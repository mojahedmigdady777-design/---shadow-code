const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const HOST_UID = "host_alice";
const PLAYER2_UID = "player_bob";
const SPY1_UID = "spy_charlie";
const SPY2_UID = "spy_dana";
const ORG_UID = "org_frank";
const OUTSIDER_UID = "outsider_eve";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validRoomPayload(hostId = HOST_UID) {
  const now = new Date(Date.now() - 2000);
  return {
    roomCode: "SHD482",
    roomName: "غرفة النخبة",
    publicRoom: true,
    maxPlayers: 8,
    roundNumber: 1,
    hostId,
    status: "WAITING",
    memberUids: [hostId],
    memberNames: ["أليس"],
    readyUids: [],
    twoSpiesEnabled: false,
    cipherType: "CAESAR_FORWARD",
    cipherShift: 3,
    codeSlots: ["7", "B", "4", "X"],
    spyUids: [],
    usedWords: [],
    usedPuzzleIds: [],
    usedSpyReportIds: [],
    spyCharges: 0,
    spyRevealedCount: 0,
    spyInPuzzle: false,
    spyLastHackEpochMs: 0,
    spyLockoutUntilEpochMs: 0,
    spyFrozenUntilEpochMs: 0,
    jammerActive: false,
    delayedAlertAtEpochMs: 0,
    delayedAlertSlot: 0,
    roundStartEpochMs: 0,
    durationSeconds: 600,
    pausedAtEpochMs: 0,
    accumulatedPauseMs: 0,
    investigationTriggerUid: "",
    investigationEndsEpochMs: 0,
    auditId: "",
    auditQuestion: "",
    auditOptions: [],
    auditCorrectIndex: -1,
    auditEndsEpochMs: 0,
    auditWrongReported: false,
    auditSenderUid: "",
    auditSuccessReported: false,
    rareMilestone7Granted: false,
    rareMilestone3Granted: false,
    lastSystemMinuteTriggered: 0,
    winnerTeam: "",
    winReason: "",
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated access is denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("gameContent").doc("words_v1").get());
  await assertFails(unauthDb.collection("users").doc(HOST_UID).get());
  await assertFails(unauthDb.collection("rooms").where("status", "==", "WAITING").get());
  await assertFails(unauthDb.collection("rooms").doc("room_1").set(validRoomPayload()));
});

test("Cloud gameContent packs & user profile discipline tracking", async () => {
  const aliceDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const bobDb = testEnv.authenticatedContext(PLAYER2_UID).firestore();
  const now = new Date(Date.now() - 2000);

  // Cloud content pack write/read
  await assertSucceeds(
    aliceDb.collection("gameContent").doc("words_v1").set({
      packId: "words_v1",
      version: 1,
      entries: ["ACTION|عزل المسار", "LOCATION|مرفأ الشحن"],
      updatedAt: now,
    })
  );
  await assertSucceeds(bobDb.collection("gameContent").doc("words_v1").get());

  // User profile with deserter/AFK tracking
  await assertSucceeds(
    aliceDb.collection("users").doc(HOST_UID).set({
      userId: HOST_UID,
      displayName: "صقر",
      gamesPlayed: 0,
      winsAsOrg: 0,
      winsAsSpy: 0,
      deserterCount: 0,
      afkPenaltyUntilEpochMs: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(HOST_UID).update({
      deserterCount: 1,
      afkPenaltyUntilEpochMs: Date.now() + 120000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(bobDb.collection("users").doc(HOST_UID).get());
  await assertFails(bobDb.collection("users").doc(HOST_UID).update({ displayName: "Hack" }));
});

test("Room lifecycle, secret battleLogs lockdown during match, reveal on FINISHED, and rematch preservation", async () => {
  const aliceDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const bobDb = testEnv.authenticatedContext(PLAYER2_UID).firestore();
  const eveDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  // 1. Create room & public query
  await assertSucceeds(aliceDb.collection("rooms").doc("room_1").set(validRoomPayload()));
  await assertFails(bobDb.collection("rooms").get());
  await assertSucceeds(
    bobDb.collection("rooms").where("status", "==", "WAITING").where("publicRoom", "==", true).get()
  );

  // 2. Bob joins waiting room
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_1").update({
      memberUids: [HOST_UID, PLAYER2_UID],
      memberNames: ["أليس", "بوب"],
      readyUids: [PLAYER2_UID],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // 3. Host starts game and seeds publicPlayers + privateRoles
  const now = new Date(Date.now() - 1000);
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_1").update({
      status: "PLAYING",
      spyUids: [PLAYER2_UID],
      roundStartEpochMs: Date.now(),
      updatedAt: now,
    })
  );

  const members = [HOST_UID, PLAYER2_UID];
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_1").collection("publicPlayers").doc(HOST_UID).set({
      userId: HOST_UID,
      roomId: "room_1",
      hostId: HOST_UID,
      memberUids: members,
      displayName: "أليس",
      seatIndex: 0,
      alive: true,
      usedEmergency: false,
      usedSlotShield: false,
      hasVoted: false,
      voteTargetUid: "",
      auditAnsweredId: "",
      puzzlesSolved: 0,
      fastestPuzzleMs: 0,
      lastActiveEpochMs: Date.now(),
      afkWarning: false,
      deserted: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_1").collection("privateRoles").doc(PLAYER2_UID).set({
      userId: PLAYER2_UID,
      roomId: "room_1",
      hostId: HOST_UID,
      role: "SPY",
      assignedSlots: [],
      allySpyUids: [],
      allySpyNames: [],
      wiretapTargetUid: "",
      wiretapTargetName: "",
      wiretapExpiresEpochMs: 0,
      wiretapCycleStartedEpochMs: 0,
      wiretapMinute: 0,
      rareCards: [],
      decoyTrapArmed: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // 4. Private role isolation
  await assertSucceeds(bobDb.collection("rooms").doc("room_1").collection("privateRoles").doc(PLAYER2_UID).get());
  await assertFails(aliceDb.collection("rooms").doc("room_1").collection("privateRoles").doc(PLAYER2_UID).get());
  await assertFails(eveDb.collection("rooms").doc("room_1").collection("privateRoles").doc(PLAYER2_UID).get());

  // 5. Secret Battle Log: writable during match, STRICTLY UNREADABLE during match!
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_1").collection("battleLogs").doc("log_1").set({
      roomId: "room_1",
      roundNumber: 1,
      actorUid: PLAYER2_UID,
      actorName: "بوب",
      eventType: "WIRETAP",
      summaryAr: "فعّل بوب التنصت الموجّه على أليس لمدة 60 ثانية.",
      createdAtEpochMs: Date.now(),
      memberUids: members,
      createdAt: now,
    })
  );

  // Even room members & host CANNOT read battleLogs while status == PLAYING!
  await assertFails(
    aliceDb.collection("rooms").doc("room_1").collection("battleLogs").where("memberUids", "array-contains", HOST_UID).get()
  );
  await assertFails(
    bobDb.collection("rooms").doc("room_1").collection("battleLogs").doc("log_1").get()
  );

  // 6. Finish match -> battleLogs now readable by room members ONLY; winner tampering rejected; Rematch allowed
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_1").update({
      status: "FINISHED",
      winnerTeam: "ORG",
      winReason: "SPY_ARRESTED",
      usedPuzzleIds: ["CIPHER:الشيفرة"],
      usedSpyReportIds: ["SPY_RPT_01"],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Now Alice & Bob CAN read battleLogs, Eve STILL CANNOT!
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_1").collection("battleLogs").where("memberUids", "array-contains", HOST_UID).get()
  );
  await assertFails(
    eveDb.collection("rooms").doc("room_1").collection("battleLogs").doc("log_1").get()
  );

  // Terminal tampering fails
  await assertFails(
    aliceDb.collection("rooms").doc("room_1").update({
      winnerTeam: "SPY",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Host Rematch in same room succeeds while incrementing roundNumber and preserving used pools
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_1").update({
      status: "WAITING",
      roundNumber: 2,
      spyUids: [],
      spyCharges: 0,
      spyRevealedCount: 0,
      winnerTeam: "",
      winReason: "",
      readyUids: [],
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Two-Spy Coordination: Zero-Trust spyNetwork isolation, per-spy wiretap scoping, and allySpy immutability", async () => {
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const spy1Db = testEnv.authenticatedContext(SPY1_UID).firestore();
  const spy2Db = testEnv.authenticatedContext(SPY2_UID).firestore();
  const orgDb = testEnv.authenticatedContext(ORG_UID).firestore();
  const eveDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  const now = new Date(Date.now() - 2000);
  const members = [HOST_UID, SPY1_UID, SPY2_UID, ORG_UID];

  await assertSucceeds(
    hostDb.collection("rooms").doc("room_2spies").set({
      ...validRoomPayload(HOST_UID),
      twoSpiesEnabled: true,
      memberUids: members,
      memberNames: ["أليس", "تشارلي", "دانا", "فرانك"],
      readyUids: members,
      status: "WAITING",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    hostDb.collection("rooms").doc("room_2spies").update({
      status: "PLAYING",
      spyUids: [SPY1_UID, SPY2_UID],
      roundStartEpochMs: Date.now(),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Host seeds privateRoles with mutual allySpyUids / allySpyNames
  await assertSucceeds(
    hostDb.collection("rooms").doc("room_2spies").collection("privateRoles").doc(SPY1_UID).set({
      userId: SPY1_UID,
      roomId: "room_2spies",
      hostId: HOST_UID,
      role: "SPY",
      assignedSlots: [],
      allySpyUids: [SPY2_UID],
      allySpyNames: ["دانا"],
      wiretapTargetUid: "",
      wiretapTargetName: "",
      wiretapExpiresEpochMs: 0,
      wiretapCycleStartedEpochMs: 0,
      wiretapMinute: 0,
      rareCards: [],
      decoyTrapArmed: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Owner cannot mutate allySpyUids or role
  await assertFails(
    spy1Db.collection("rooms").doc("room_2spies").collection("privateRoles").doc(SPY1_UID).update({
      allySpyUids: [ORG_UID],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Host creates spyNetwork/shared document
  await assertSucceeds(
    hostDb.collection("rooms").doc("room_2spies").collection("spyNetwork").doc("shared").set({
      roomId: "room_2spies",
      hostId: HOST_UID,
      spyUids: [SPY1_UID, SPY2_UID],
      spy1Uid: SPY1_UID,
      spy1TargetUid: "",
      spy1TargetName: "",
      spy1ExpiresEpochMs: 0,
      spy2Uid: SPY2_UID,
      spy2TargetUid: "",
      spy2TargetName: "",
      spy2ExpiresEpochMs: 0,
      blackChannelOpenedAtEpochMs: 0,
      blackChannelExpiresEpochMs: 0,
      blackChannelUsesCount: 0,
      blackChannelClosed: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Only Spy 1 and Spy 2 can get spyNetwork/shared; Org members (even Host) and Outsiders are denied; list is always denied
  await assertSucceeds(spy1Db.collection("rooms").doc("room_2spies").collection("spyNetwork").doc("shared").get());
  await assertSucceeds(spy2Db.collection("rooms").doc("room_2spies").collection("spyNetwork").doc("shared").get());
  await assertFails(hostDb.collection("rooms").doc("room_2spies").collection("spyNetwork").doc("shared").get());
  await assertFails(orgDb.collection("rooms").doc("room_2spies").collection("spyNetwork").doc("shared").get());
  await assertFails(eveDb.collection("rooms").doc("room_2spies").collection("spyNetwork").doc("shared").get());
  await assertFails(spy1Db.collection("rooms").doc("room_2spies").collection("spyNetwork").get());

  // Spy 1 can update ONLY spy1 wiretap fields; cannot touch spy2 wiretap fields
  await assertSucceeds(
    spy1Db.collection("rooms").doc("room_2spies").collection("spyNetwork").doc("shared").update({
      spy1TargetUid: ORG_UID,
      spy1TargetName: "فرانك",
      spy1ExpiresEpochMs: Date.now() + 60000,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    spy1Db.collection("rooms").doc("room_2spies").collection("spyNetwork").doc("shared").update({
      spy2TargetUid: ORG_UID,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Spy 2 can update ONLY spy2 wiretap fields; cannot touch spy1 wiretap fields
  await assertSucceeds(
    spy2Db.collection("rooms").doc("room_2spies").collection("spyNetwork").doc("shared").update({
      spy2TargetUid: HOST_UID,
      spy2TargetName: "أليس",
      spy2ExpiresEpochMs: Date.now() + 60000,
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertFails(
    spy2Db.collection("rooms").doc("room_2spies").collection("spyNetwork").doc("shared").update({
      spy1TargetUid: HOST_UID,
      updatedAt: new Date(Date.now() - 250),
    })
  );

  // Org player & outsider cannot mutate spyNetwork/shared
  await assertFails(
    orgDb.collection("rooms").doc("room_2spies").collection("spyNetwork").doc("shared").update({
      spy1TargetUid: "",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Spy 1 opens Black Channel for 2 minutes and sends up to 2 fake system messages
  await assertSucceeds(
    spy1Db.collection("rooms").doc("room_2spies").collection("spyNetwork").doc("shared").update({
      blackChannelOpenedAtEpochMs: Date.now(),
      blackChannelExpiresEpochMs: Date.now() + 120000,
      updatedAt: new Date(Date.now() - 180),
    })
  );
  // Org cannot open or tamper with Black Channel
  await assertFails(
    orgDb.collection("rooms").doc("room_2spies").collection("spyNetwork").doc("shared").update({
      blackChannelClosed: true,
      updatedAt: new Date(Date.now() - 170),
    })
  );
  // Use 1
  await assertSucceeds(
    spy1Db.collection("rooms").doc("room_2spies").collection("spyNetwork").doc("shared").update({
      blackChannelUsesCount: 1,
      blackChannelClosed: false,
      updatedAt: new Date(Date.now() - 160),
    })
  );
  // Decrementing usesCount is forbidden
  await assertFails(
    spy1Db.collection("rooms").doc("room_2spies").collection("spyNetwork").doc("shared").update({
      blackChannelUsesCount: 0,
      updatedAt: new Date(Date.now() - 155),
    })
  );
  // Exceeding MAX_USES (3 > 2) is forbidden
  await assertFails(
    spy2Db.collection("rooms").doc("room_2spies").collection("spyNetwork").doc("shared").update({
      blackChannelUsesCount: 3,
      updatedAt: new Date(Date.now() - 150),
    })
  );
  // Use 2 permanently closes the Black Channel
  await assertSucceeds(
    spy2Db.collection("rooms").doc("room_2spies").collection("spyNetwork").doc("shared").update({
      blackChannelUsesCount: 2,
      blackChannelClosed: true,
      updatedAt: new Date(Date.now() - 140),
    })
  );
  // Once blackChannelClosed == true, spies CANNOT reopen it!
  await assertFails(
    spy1Db.collection("rooms").doc("room_2spies").collection("spyNetwork").doc("shared").update({
      blackChannelClosed: false,
      updatedAt: new Date(Date.now() - 130),
    })
  );

  // Host can reset spyNetwork/shared for a new game/rematch
  await assertSucceeds(
    hostDb.collection("rooms").doc("room_2spies").collection("spyNetwork").doc("shared").update({
      spyUids: [SPY1_UID],
      spy1Uid: SPY1_UID,
      spy1TargetUid: "",
      spy1TargetName: "",
      spy1ExpiresEpochMs: 0,
      spy2Uid: "",
      spy2TargetUid: "",
      spy2TargetName: "",
      spy2ExpiresEpochMs: 0,
      blackChannelOpenedAtEpochMs: 0,
      blackChannelExpiresEpochMs: 0,
      blackChannelUsesCount: 0,
      blackChannelClosed: false,
      updatedAt: new Date(Date.now() - 100),
    })
  );
});


