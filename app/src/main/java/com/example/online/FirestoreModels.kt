package com.example.online

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CloudContentPackDoc(
    @DocumentId val id: String = "",
    val packId: String = "",
    val version: Long = 1L,
    val entries: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserProfileDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val gamesPlayed: Long = 0L,
    val winsAsOrg: Long = 0L,
    val winsAsSpy: Long = 0L,
    val deserterCount: Long = 0L,
    val afkPenaltyUntilEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OnlineRoomDoc(
    @DocumentId val id: String = "",
    val roomCode: String = "",
    val roomName: String = "غرفة عمليات سرية",
    val publicRoom: Boolean = true,
    val maxPlayers: Long = 8L,
    val roundNumber: Long = 1L,
    val hostId: String = "",
    val status: String = "WAITING",
    val memberUids: List<String> = emptyList(),
    val memberNames: List<String> = emptyList(),
    val readyUids: List<String> = emptyList(),
    val twoSpiesEnabled: Boolean = false,
    val cipherType: String = "CAESAR_FORWARD",
    val cipherShift: Long = 3L,
    val codeSlots: List<String> = listOf("7", "B", "4", "X"),
    val spyUids: List<String> = emptyList(),
    val usedWords: List<String> = emptyList(),
    val usedPuzzleIds: List<String> = emptyList(),
    val usedSpyReportIds: List<String> = emptyList(),
    val spyCharges: Long = 0L,
    val spyRevealedCount: Long = 0L,
    val spyInPuzzle: Boolean = false,
    val spyLastHackEpochMs: Long = 0L,
    val spyLockoutUntilEpochMs: Long = 0L,
    val spyFrozenUntilEpochMs: Long = 0L,
    val jammerActive: Boolean = false,
    val delayedAlertAtEpochMs: Long = 0L,
    val delayedAlertSlot: Long = 0L,
    val roundStartEpochMs: Long = 0L,
    val durationSeconds: Long = 600L,
    val pausedAtEpochMs: Long = 0L,
    val accumulatedPauseMs: Long = 0L,
    val investigationTriggerUid: String = "",
    val investigationEndsEpochMs: Long = 0L,
    val auditId: String = "",
    val auditQuestion: String = "",
    val auditOptions: List<String> = emptyList(),
    val auditCorrectIndex: Long = -1L,
    val auditEndsEpochMs: Long = 0L,
    val auditWrongReported: Boolean = false,
    val auditSenderUid: String = "",
    val auditSuccessReported: Boolean = false,
    val rareMilestone7Granted: Boolean = false,
    val rareMilestone3Granted: Boolean = false,
    val lastSystemMinuteTriggered: Long = 0L,
    val winnerTeam: String = "",
    val winReason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PublicPlayerDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val roomId: String = "",
    val hostId: String = "",
    val memberUids: List<String> = emptyList(),
    val displayName: String = "",
    val seatIndex: Long = 0L,
    val alive: Boolean = true,
    val usedEmergency: Boolean = false,
    val usedSlotShield: Boolean = false,
    val hasVoted: Boolean = false,
    val voteTargetUid: String = "",
    val auditAnsweredId: String = "",
    val puzzlesSolved: Long = 0L,
    val fastestPuzzleMs: Long = 0L,
    val lastActiveEpochMs: Long = 0L,
    val afkWarning: Boolean = false,
    val deserted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PrivateRoleDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val roomId: String = "",
    val hostId: String = "",
    val role: String = "ORG",
    val assignedSlots: List<Long> = emptyList(),
    val allySpyUids: List<String> = emptyList(),
    val allySpyNames: List<String> = emptyList(),
    val wiretapTargetUid: String = "",
    val wiretapTargetName: String = "",
    val wiretapExpiresEpochMs: Long = 0L,
    val wiretapCycleStartedEpochMs: Long = 0L,
    val wiretapMinute: Long = 0L,
    val rareCards: List<String> = emptyList(),
    val decoyTrapArmed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SpyNetworkDoc(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val hostId: String = "",
    val spyUids: List<String> = emptyList(),
    val spy1Uid: String = "",
    val spy1TargetUid: String = "",
    val spy1TargetName: String = "",
    val spy1ExpiresEpochMs: Long = 0L,
    val spy2Uid: String = "",
    val spy2TargetUid: String = "",
    val spy2TargetName: String = "",
    val spy2ExpiresEpochMs: Long = 0L,
    val blackChannelOpenedAtEpochMs: Long = 0L,
    val blackChannelExpiresEpochMs: Long = 0L,
    val blackChannelUsesCount: Long = 0L,
    val blackChannelClosed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class RoomMessageDoc(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val senderUid: String = "",
    val senderDisplayName: String = "",
    val spoofedByUid: String = "",
    val channel: String = "PLAYER",
    val targetUid: String = "",
    val plainText: String = "",
    val cipherText: String = "",
    val decoyTrap: Boolean = false,
    val decoyTriggered: Boolean = false,
    val createdAtEpochMs: Long = 0L,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BattleLogDoc(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val roundNumber: Long = 1L,
    val actorUid: String = "",
    val actorName: String = "",
    val eventType: String = "MESSAGE",
    val summaryAr: String = "",
    val createdAtEpochMs: Long = 0L,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)
