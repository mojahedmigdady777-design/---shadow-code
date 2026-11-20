package com.example.online

import android.content.Context
import com.example.R
import com.example.core.BattleEventType
import com.example.core.CipherEngine
import com.example.core.CipherType
import com.example.core.CloudContentCatalog
import com.example.core.RareCardType
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.random.Random

class OnlineRoomRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun generateRoomCode(): String {
        val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        return (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    /**
     * Syncs the massive Cloud Content Catalog (/gameContent) from Firestore.
     * Seeds any missing packs on first access so all rooms pull from Firebase cloud packs.
     */
    suspend fun syncCloudContentPacks(): Result<Unit> {
        val path = "gameContent"
        return try {
            requireUserId()
            syncSinglePack(
                packId = "words_v1",
                expectedEntries = { CloudContentCatalog.serializeWordsPackEntries() },
                applyEntries = { CloudContentCatalog.applyCloudWordsPack(it) }
            )
            syncSinglePack(
                packId = "puzzles_v1",
                expectedEntries = { CloudContentCatalog.serializePuzzlesPackEntries() },
                applyEntries = { CloudContentCatalog.applyCloudPuzzlesPack(it) }
            )
            syncSinglePack(
                packId = "spy_reports_v1",
                expectedEntries = { CloudContentCatalog.serializeSpyReportsPackEntries() },
                applyEntries = { CloudContentCatalog.applyCloudSpyReportsPack(it) }
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    private suspend fun syncSinglePack(
        packId: String,
        expectedEntries: () -> List<String>,
        applyEntries: (List<String>) -> Unit
    ) {
        val docRef = db.collection("gameContent").document(packId)
        val snap = docRef.get().await()
        if (snap.exists()) {
            val doc = snap.toObject(CloudContentPackDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            if (doc != null && doc.entries.isNotEmpty()) {
                applyEntries(doc.entries)
                return
            }
        }
        val entries = expectedEntries().take(1500)
        docRef.set(
            mapOf(
                "packId" to packId,
                "version" to 1L,
                "entries" to entries,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        applyEntries(entries)
    }

    suspend fun ensureUserProfile(displayName: String): Result<UserProfileDoc> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(
                    UserProfileDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (existing != null) {
                    if (displayName.isNotBlank() && displayName != existing.displayName) {
                        docRef.update(
                            mapOf(
                                "displayName" to displayName.take(30),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                        return Result.success(existing.copy(displayName = displayName.take(30)))
                    }
                    return Result.success(existing)
                }
            }
            val safeName = displayName.trim().ifEmpty { "عميل ميداني" }.take(30)
            val payload = mapOf(
                "userId" to uid,
                "displayName" to safeName,
                "gamesPlayed" to 0L,
                "winsAsOrg" to 0L,
                "winsAsSpy" to 0L,
                "deserterCount" to 0L,
                "afkPenaltyUntilEpochMs" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(UserProfileDoc(id = uid, userId = uid, displayName = safeName))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun recordMatchOutcomeOnProfile(won: Boolean, asSpy: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "users/$uid"
        return try {
            val updates = mutableMapOf<String, Any>(
                "gamesPlayed" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (won) {
                if (asSpy) updates["winsAsSpy"] = FieldValue.increment(1L)
                else updates["winsAsOrg"] = FieldValue.increment(1L)
            }
            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfileDoc?> = flow {
        require(userId.isNotBlank()) { "Blank userId" }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            UserProfileDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeMyRooms(userId: String): Flow<List<OnlineRoomDoc>> = flow {
        require(userId.isNotBlank()) { "Blank userId" }
        val path = "rooms"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        OnlineRoomDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePublicWaitingRooms(userId: String): Flow<List<OnlineRoomDoc>> = flow {
        require(userId.isNotBlank()) { "Blank userId" }
        val path = "rooms"
        emitAll(
            db.collection(path)
                .whereEqualTo("status", "WAITING")
                .whereEqualTo("publicRoom", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        OnlineRoomDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeRoom(roomId: String): Flow<OnlineRoomDoc?> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomId"
        emitAll(
            db.collection("rooms").document(roomId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            OnlineRoomDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observePublicPlayers(roomId: String, userId: String): Flow<List<PublicPlayerDoc>> = flow {
        require(userId.isNotBlank()) { "Blank userId" }
        val path = "rooms/$roomId/publicPlayers"
        emitAll(
            db.collection("rooms").document(roomId).collection("publicPlayers")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        PublicPlayerDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.seatIndex }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePrivateRole(roomId: String, userId: String): Flow<PrivateRoleDoc?> = flow {
        require(userId.isNotBlank()) { "Blank userId" }
        val path = "rooms/$roomId/privateRoles/$userId"
        emitAll(
            db.collection("rooms").document(roomId)
                .collection("privateRoles").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            PrivateRoleDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeSpyNetwork(roomId: String, userId: String): Flow<SpyNetworkDoc?> = flow {
        require(userId.isNotBlank()) { "Blank userId" }
        val verifiedUid = requireUserId()
        val path = "rooms/$roomId/spyNetwork/shared"
        emitAll(
            db.collection("rooms").document(roomId)
                .collection("spyNetwork").document("shared")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            SpyNetworkDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == verifiedUid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(roomId: String, userId: String): Flow<List<RoomMessageDoc>> = flow {
        require(userId.isNotBlank()) { "Blank userId" }
        val path = "rooms/$roomId/messages"
        emitAll(
            db.collection("rooms").document(roomId).collection("messages")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        RoomMessageDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAtEpochMs }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Strictly attaches ONLY when room status == FINISHED (enforced by firestore.rules too).
     */
    fun observeBattleLogs(roomId: String, userId: String): Flow<List<BattleLogDoc>> = flow {
        require(userId.isNotBlank()) { "Blank userId" }
        val path = "rooms/$roomId/battleLogs"
        emitAll(
            db.collection("rooms").document(roomId).collection("battleLogs")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        BattleLogDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAtEpochMs }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createRoom(
        hostName: String,
        roomName: String = "غرفة عمليات سرية",
        publicRoom: Boolean = true,
        maxPlayers: Int = 8,
        durationSeconds: Long = 600L
    ): Result<String> {
        val roomId = "room_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "rooms/$roomId"
        return try {
            val uid = requireUserId()
            val safeName = hostName.trim().ifEmpty { "القائد" }.take(30)
            val safeRoomName = roomName.trim().ifEmpty { "غرفة عمليات سرية" }.take(40)
            val clampedMax = maxPlayers.coerceIn(4, 8).toLong()
            val clampedDuration = durationSeconds.coerceIn(360L, 720L)
            val code = generateRoomCode()
            val payload = mapOf(
                "roomCode" to code,
                "roomName" to safeRoomName,
                "publicRoom" to publicRoom,
                "maxPlayers" to clampedMax,
                "roundNumber" to 1L,
                "hostId" to uid,
                "status" to "WAITING",
                "memberUids" to listOf(uid),
                "memberNames" to listOf(safeName),
                "readyUids" to listOf(uid),
                "twoSpiesEnabled" to false,
                "cipherType" to CipherType.CAESAR_FORWARD.name,
                "cipherShift" to 3L,
                "codeSlots" to CipherEngine.generateSecretCode(),
                "spyUids" to emptyList<String>(),
                "usedWords" to emptyList<String>(),
                "usedPuzzleIds" to emptyList<String>(),
                "usedSpyReportIds" to emptyList<String>(),
                "spyCharges" to 0L,
                "spyRevealedCount" to 0L,
                "spyInPuzzle" to false,
                "spyLastHackEpochMs" to 0L,
                "spyLockoutUntilEpochMs" to 0L,
                "spyFrozenUntilEpochMs" to 0L,
                "jammerActive" to false,
                "delayedAlertAtEpochMs" to 0L,
                "delayedAlertSlot" to 0L,
                "roundStartEpochMs" to 0L,
                "durationSeconds" to clampedDuration,
                "pausedAtEpochMs" to 0L,
                "accumulatedPauseMs" to 0L,
                "investigationTriggerUid" to "",
                "investigationEndsEpochMs" to 0L,
                "auditId" to "",
                "auditQuestion" to "",
                "auditOptions" to emptyList<String>(),
                "auditCorrectIndex" to -1L,
                "auditEndsEpochMs" to 0L,
                "auditWrongReported" to false,
                "auditSenderUid" to "",
                "auditSuccessReported" to false,
                "rareMilestone7Granted" to false,
                "rareMilestone3Granted" to false,
                "lastSystemMinuteTriggered" to 0L,
                "winnerTeam" to "",
                "winReason" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("rooms").document(roomId).set(payload).await()
            Result.success(roomId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun joinRoomByCode(roomCode: String, playerName: String): Result<String> {
        val normalizedCode = roomCode.trim().uppercase()
        val path = "rooms"
        return try {
            val uid = requireUserId()
            require(normalizedCode.length == 6) { "كود الغرفة يجب أن يتكون من 6 أحرف/أرقام" }
            val querySnap = db.collection("rooms")
                .whereEqualTo("status", "WAITING")
                .whereEqualTo("roomCode", normalizedCode)
                .get()
                .await()
            val roomDoc = querySnap.documents.firstOrNull()
                ?: return Result.failure(IllegalStateException("لم يتم العثور على غرفة متاحة بهذا الكود"))
            joinRoomDocInternal(roomDoc, uid, playerName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun joinRoomById(roomId: String, playerName: String): Result<String> {
        val path = "rooms/$roomId"
        return try {
            val uid = requireUserId()
            val roomDoc = db.collection("rooms").document(roomId).get().await()
            if (!roomDoc.exists()) {
                return Result.failure(IllegalStateException("الغرفة لم تعد موجودة"))
            }
            joinRoomDocInternal(roomDoc, uid, playerName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private suspend fun joinRoomDocInternal(
        roomDoc: DocumentSnapshot,
        uid: String,
        playerName: String
    ): Result<String> {
        val room = roomDoc.toObject(
            OnlineRoomDoc::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ) ?: return Result.failure(IllegalStateException("بيانات الغرفة غير صالحة"))

        if (uid in room.memberUids) {
            return Result.success(roomDoc.id)
        }
        if (room.status != "WAITING") {
            return Result.failure(IllegalStateException("المباراة قد بدأت بالفعل في هذه الغرفة"))
        }
        if (room.memberUids.size >= room.maxPlayers.toInt().coerceIn(4, 8)) {
            return Result.failure(IllegalStateException("الغرفة ممتلئة حالياً"))
        }
        val safeName = playerName.trim().ifEmpty { "عميل" }.take(30)
        val newUids = room.memberUids + uid
        val newNames = room.memberNames + safeName
        db.collection("rooms").document(roomDoc.id).update(
            mapOf(
                "memberUids" to newUids,
                "memberNames" to newNames,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        return Result.success(roomDoc.id)
    }

    suspend fun toggleReady(room: OnlineRoomDoc): Result<Unit> {
        val path = "rooms/${room.id}"
        return try {
            val uid = requireUserId()
            val newReady = if (uid in room.readyUids) {
                room.readyUids - uid
            } else {
                (room.readyUids + uid).distinct()
            }
            db.collection("rooms").document(room.id).update(
                mapOf(
                    "readyUids" to newReady,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun kickWaitingPlayer(room: OnlineRoomDoc, targetUid: String): Result<Unit> {
        val path = "rooms/${room.id}"
        return try {
            val uid = requireUserId()
            require(room.hostId == uid && targetUid != uid) { "المضيف فقط يمكنه استبعاد لاعب في صالة الانتظار" }
            val idx = room.memberUids.indexOf(targetUid)
            if (idx >= 0) {
                val newUids = room.memberUids.filterIndexed { i, _ -> i != idx }
                val newNames = room.memberNames.filterIndexed { i, _ -> i != idx }
                val newReady = room.readyUids.filter { it != targetUid }
                db.collection("rooms").document(room.id).update(
                    mapOf(
                        "memberUids" to newUids,
                        "memberNames" to newNames,
                        "readyUids" to newReady,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateWaitingRoomConfig(
        roomId: String,
        publicRoom: Boolean,
        maxPlayers: Int,
        durationSeconds: Long
    ): Result<Unit> {
        val path = "rooms/$roomId"
        return try {
            requireUserId()
            db.collection("rooms").document(roomId).update(
                mapOf(
                    "publicRoom" to publicRoom,
                    "maxPlayers" to maxPlayers.coerceIn(4, 8).toLong(),
                    "durationSeconds" to durationSeconds.coerceIn(360L, 720L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun leaveWaitingRoom(room: OnlineRoomDoc): Result<Unit> {
        val path = "rooms/${room.id}"
        return try {
            val uid = requireUserId()
            if (room.hostId == uid) {
                db.collection("rooms").document(room.id).delete().await()
                return Result.success(Unit)
            }
            val idx = room.memberUids.indexOf(uid)
            if (idx >= 0) {
                val newUids = room.memberUids.filterIndexed { i, _ -> i != idx }
                val newNames = room.memberNames.filterIndexed { i, _ -> i != idx }
                val newReady = room.readyUids.filter { it != uid }
                db.collection("rooms").document(room.id).update(
                    mapOf(
                        "memberUids" to newUids,
                        "memberNames" to newNames,
                        "readyUids" to newReady,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun applyDeserterPenalty(room: OnlineRoomDoc, actorName: String): Result<Unit> {
        val path = "rooms/${room.id}"
        return try {
            val uid = requireUserId()
            val now = System.currentTimeMillis()
            val penaltyUntil = now + 120_000L // 2-minute matchmaking penalty
            db.collection("rooms").document(room.id)
                .collection("publicPlayers").document(uid)
                .update(
                    mapOf(
                        "alive" to false,
                        "deserted" to true,
                        "afkWarning" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

            db.collection("users").document(uid).update(
                mapOf(
                    "deserterCount" to FieldValue.increment(1L),
                    "afkPenaltyUntilEpochMs" to penaltyUntil,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            logBattleEvent(
                room = room,
                actorUid = uid,
                actorName = actorName,
                eventType = BattleEventType.PENALTY,
                summaryAr = "انسحب ($actorName) من الغرفة أثناء سير الجولة، فتم تطبيق عقوبة الانسحاب تلقائياً."
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleTwoSpies(roomId: String, enabled: Boolean): Result<Unit> {
        val path = "rooms/$roomId"
        return try {
            requireUserId()
            db.collection("rooms").document(roomId).update(
                mapOf(
                    "twoSpiesEnabled" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun prepareRematchInSameRoom(room: OnlineRoomDoc): Result<Unit> {
        val path = "rooms/${room.id}"
        return try {
            val uid = requireUserId()
            require(room.hostId == uid) { "المضيف فقط يمكنه بدء جولة جديدة في الغرفة" }
            db.collection("rooms").document(room.id).update(
                mapOf(
                    "status" to "WAITING",
                    "roundNumber" to (room.roundNumber + 1L),
                    "spyUids" to emptyList<String>(),
                    "spyCharges" to 0L,
                    "spyRevealedCount" to 0L,
                    "spyInPuzzle" to false,
                    "spyLastHackEpochMs" to 0L,
                    "spyLockoutUntilEpochMs" to 0L,
                    "spyFrozenUntilEpochMs" to 0L,
                    "jammerActive" to false,
                    "delayedAlertAtEpochMs" to 0L,
                    "delayedAlertSlot" to 0L,
                    "roundStartEpochMs" to 0L,
                    "pausedAtEpochMs" to 0L,
                    "accumulatedPauseMs" to 0L,
                    "investigationTriggerUid" to "",
                    "investigationEndsEpochMs" to 0L,
                    "auditId" to "",
                    "auditQuestion" to "",
                    "auditOptions" to emptyList<String>(),
                    "auditCorrectIndex" to -1L,
                    "auditEndsEpochMs" to 0L,
                    "auditWrongReported" to false,
                    "auditSenderUid" to "",
                    "auditSuccessReported" to false,
                    "rareMilestone7Granted" to false,
                    "rareMilestone3Granted" to false,
                    "lastSystemMinuteTriggered" to 0L,
                    "winnerTeam" to "",
                    "winReason" to "",
                    "readyUids" to listOf(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun startOnlineGame(room: OnlineRoomDoc): Result<Unit> {
        val path = "rooms/${room.id}"
        return try {
            val uid = requireUserId()
            require(room.hostId == uid) { "المضيف فقط يمكنه بدء الجولة" }
            require(room.memberUids.size in 4..8) { "يجب وجود 4 إلى 8 لاعبين لبدء المهمة" }

            // Refresh cloud packs before dealing match roles/puzzles
            runCatching { syncCloudContentPacks() }

            val members = room.memberUids
            val spyCount = if (members.size >= 7 && room.twoSpiesEnabled) 2 else 1
            val spyUids = members.shuffled().take(spyCount)
            val orgUids = members.filter { it !in spyUids }

            val slotAssignments = mutableMapOf<String, MutableList<Long>>()
            for (slotIdx in 0..3) {
                val holderUid = orgUids[slotIdx % orgUids.size]
                slotAssignments.getOrPut(holderUid) { mutableListOf() }.add(slotIdx.toLong())
            }

            val codeSlots = CipherEngine.generateSecretCode()
            val (cipherType, cipherShift) = CipherEngine.pickCipherConfig()
            val startEpoch = System.currentTimeMillis()

            val roomRef = db.collection("rooms").document(room.id)

            // Check existing subdocs so rematch safely updates without touching createdAt
            val existingPubIds = roomRef.collection("publicPlayers").get().await().documents.map { it.id }.toSet()
            val existingPrivIds = roomRef.collection("privateRoles").get().await().documents.map { it.id }.toSet()
            val existingSpyNet = roomRef.collection("spyNetwork").document("shared").get().await().exists()

            val batch = db.batch()
            batch.update(
                roomRef,
                mapOf(
                    "status" to "PLAYING",
                    "cipherType" to cipherType.name,
                    "cipherShift" to cipherShift.toLong(),
                    "codeSlots" to codeSlots,
                    "spyUids" to spyUids,
                    "roundStartEpochMs" to startEpoch,
                    "durationSeconds" to room.durationSeconds.coerceIn(360L, 720L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            members.forEachIndexed { index, memberUid ->
                val name = room.memberNames.getOrElse(index) { "عميل ${index + 1}" }
                val pubRef = roomRef.collection("publicPlayers").document(memberUid)
                if (memberUid in existingPubIds) {
                    batch.update(
                        pubRef,
                        mapOf(
                            "memberUids" to members,
                            "displayName" to name,
                            "seatIndex" to index.toLong(),
                            "alive" to true,
                            "usedEmergency" to false,
                            "usedSlotShield" to false,
                            "hasVoted" to false,
                            "voteTargetUid" to "",
                            "auditAnsweredId" to "",
                            "puzzlesSolved" to 0L,
                            "fastestPuzzleMs" to 0L,
                            "lastActiveEpochMs" to startEpoch,
                            "afkWarning" to false,
                            "deserted" to false,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    batch.set(
                        pubRef,
                        mapOf(
                            "userId" to memberUid,
                            "roomId" to room.id,
                            "hostId" to room.hostId,
                            "memberUids" to members,
                            "displayName" to name,
                            "seatIndex" to index.toLong(),
                            "alive" to true,
                            "usedEmergency" to false,
                            "usedSlotShield" to false,
                            "hasVoted" to false,
                            "voteTargetUid" to "",
                            "auditAnsweredId" to "",
                            "puzzlesSolved" to 0L,
                            "fastestPuzzleMs" to 0L,
                            "lastActiveEpochMs" to startEpoch,
                            "afkWarning" to false,
                            "deserted" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                val privRef = roomRef.collection("privateRoles").document(memberUid)
                val isSpy = memberUid in spyUids
                val allySpyUids = if (isSpy) spyUids.filter { it != memberUid } else emptyList()
                val allySpyNames = allySpyUids.map { sUid ->
                    val sIdx = members.indexOf(sUid)
                    room.memberNames.getOrElse(sIdx) { "متجسس" }
                }
                if (memberUid in existingPrivIds) {
                    batch.update(
                        privRef,
                        mapOf(
                            "role" to if (isSpy) "SPY" else "ORG",
                            "assignedSlots" to if (isSpy) emptyList<Long>() else (slotAssignments[memberUid] ?: emptyList()),
                            "allySpyUids" to allySpyUids,
                            "allySpyNames" to allySpyNames,
                            "wiretapTargetUid" to "",
                            "wiretapTargetName" to "",
                            "wiretapExpiresEpochMs" to 0L,
                            "wiretapCycleStartedEpochMs" to startEpoch,
                            "wiretapMinute" to 0L,
                            "rareCards" to emptyList<String>(),
                            "decoyTrapArmed" to false,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    batch.set(
                        privRef,
                        mapOf(
                            "userId" to memberUid,
                            "roomId" to room.id,
                            "hostId" to room.hostId,
                            "role" to if (isSpy) "SPY" else "ORG",
                            "assignedSlots" to if (isSpy) emptyList<Long>() else (slotAssignments[memberUid] ?: emptyList()),
                            "allySpyUids" to allySpyUids,
                            "allySpyNames" to allySpyNames,
                            "wiretapTargetUid" to "",
                            "wiretapTargetName" to "",
                            "wiretapExpiresEpochMs" to 0L,
                            "wiretapCycleStartedEpochMs" to startEpoch,
                            "wiretapMinute" to 0L,
                            "rareCards" to emptyList<String>(),
                            "decoyTrapArmed" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            // Shared Two-Spy coordination document (strictly readable by Spies only)
            val spy1Uid = spyUids.getOrElse(0) { "" }
            val spy2Uid = spyUids.getOrElse(1) { "" }
            val spyNetRef = roomRef.collection("spyNetwork").document("shared")
            if (existingSpyNet) {
                batch.update(
                    spyNetRef,
                    mapOf(
                        "spyUids" to spyUids,
                        "spy1Uid" to spy1Uid,
                        "spy1TargetUid" to "",
                        "spy1TargetName" to "",
                        "spy1ExpiresEpochMs" to 0L,
                        "spy2Uid" to spy2Uid,
                        "spy2TargetUid" to "",
                        "spy2TargetName" to "",
                        "spy2ExpiresEpochMs" to 0L,
                        "blackChannelOpenedAtEpochMs" to 0L,
                        "blackChannelExpiresEpochMs" to 0L,
                        "blackChannelUsesCount" to 0L,
                        "blackChannelClosed" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    spyNetRef,
                    mapOf(
                        "roomId" to room.id,
                        "hostId" to room.hostId,
                        "spyUids" to spyUids,
                        "spy1Uid" to spy1Uid,
                        "spy1TargetUid" to "",
                        "spy1TargetName" to "",
                        "spy1ExpiresEpochMs" to 0L,
                        "spy2Uid" to spy2Uid,
                        "spy2TargetUid" to "",
                        "spy2TargetName" to "",
                        "spy2ExpiresEpochMs" to 0L,
                        "blackChannelOpenedAtEpochMs" to 0L,
                        "blackChannelExpiresEpochMs" to 0L,
                        "blackChannelUsesCount" to 0L,
                        "blackChannelClosed" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            // Write initial secret Battle Log entry (unreadable until FINISHED)
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val spyNames = spyUids.map { sUid ->
                val idx = members.indexOf(sUid)
                room.memberNames.getOrElse(idx) { "متجسس" }
            }.joinToString("، ")
            batch.set(
                roomRef.collection("battleLogs").document(logId),
                mapOf(
                    "roomId" to room.id,
                    "roundNumber" to room.roundNumber,
                    "actorUid" to uid,
                    "actorName" to "النظام",
                    "eventType" to BattleEventType.ROUND_START.name,
                    "summaryAr" to "انطلقت الجولة رقم ${room.roundNumber} (${room.roomName}). المتجسس: $spyNames | التشفير: ${cipherType.arabicName}.",
                    "createdAtEpochMs" to startEpoch,
                    "memberUids" to members,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun logBattleEvent(
        room: OnlineRoomDoc,
        actorUid: String,
        actorName: String,
        eventType: BattleEventType,
        summaryAr: String
    ): Result<Unit> {
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val path = "rooms/${room.id}/battleLogs/$logId"
        return try {
            requireUserId()
            if (room.status == "FINISHED") return Result.success(Unit)
            db.collection("rooms").document(room.id)
                .collection("battleLogs").document(logId)
                .set(
                    mapOf(
                        "roomId" to room.id,
                        "roundNumber" to room.roundNumber,
                        "actorUid" to actorUid.take(128),
                        "actorName" to actorName.ifBlank { "عميل" }.take(40),
                        "eventType" to eventType.name,
                        "summaryAr" to summaryAr.take(400),
                        "createdAtEpochMs" to System.currentTimeMillis(),
                        "memberUids" to room.memberUids,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendOnlineMessage(
        room: OnlineRoomDoc,
        senderDisplayName: String,
        spoofedByUid: String,
        plainText: String,
        cipherText: String,
        newlyUsedWords: List<String>,
        isDecoyTrap: Boolean,
        decoyTriggered: Boolean,
        halveChargesOnTrigger: Boolean,
        autoAudit: com.example.core.SystemAuditPrompt? = null
    ): Result<Unit> {
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val path = "rooms/${room.id}/messages/$msgId"
        return try {
            val uid = requireUserId()
            val roomRef = db.collection("rooms").document(room.id)
            val batch = db.batch()
            val now = System.currentTimeMillis()

            batch.set(
                roomRef.collection("messages").document(msgId),
                mapOf(
                    "roomId" to room.id,
                    "senderUid" to if (spoofedByUid.isNotBlank()) spoofedByUid else uid,
                    "senderDisplayName" to senderDisplayName.take(40),
                    "spoofedByUid" to if (spoofedByUid.isNotBlank()) uid else "",
                    "channel" to "PLAYER",
                    "targetUid" to "",
                    "plainText" to plainText.take(500),
                    "cipherText" to cipherText.take(500),
                    "decoyTrap" to isDecoyTrap,
                    "decoyTriggered" to decoyTriggered,
                    "createdAtEpochMs" to now,
                    "memberUids" to room.memberUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            val updatedWords = (room.usedWords + newlyUsedWords).distinct().take(1000)
            val roomUpdates = mutableMapOf<String, Any>(
                "usedWords" to updatedWords,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (halveChargesOnTrigger) {
                roomUpdates["spyCharges"] = maxOf(0L, room.spyCharges / 2L)
                roomUpdates["spyLockoutUntilEpochMs"] = now + 30_000L
            } else {
                roomUpdates["spyCharges"] = FieldValue.increment(1L)
            }
            if (autoAudit != null) {
                roomUpdates["auditId"] = autoAudit.id
                roomUpdates["auditQuestion"] = autoAudit.question.take(300)
                roomUpdates["auditOptions"] = autoAudit.options
                roomUpdates["auditCorrectIndex"] = autoAudit.correctIndex.toLong()
                roomUpdates["auditEndsEpochMs"] = now + 20_000L
                roomUpdates["auditWrongReported"] = false
                roomUpdates["auditSenderUid"] = uid
                roomUpdates["auditSuccessReported"] = false
            }
            batch.update(roomRef, roomUpdates)

            // Record secret battle log for spoofed/decoy or regular tactical reports
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val eventType = if (spoofedByUid.isNotBlank() || isDecoyTrap) {
                BattleEventType.ABILITY
            } else {
                BattleEventType.MESSAGE
            }
            val logSummary = when {
                spoofedByUid.isNotBlank() -> "تم استخدام بطاقة تزوير الهوية لإرسال بلاغ باسم ($senderDisplayName): «$plainText»"
                isDecoyTrap -> "زرع ($senderDisplayName) فخاً خداعياً داخل البلاغ: «$plainText»"
                else -> "أرسل ($senderDisplayName) بلاغاً تكتيكياً: «$plainText»"
            }
            batch.set(
                roomRef.collection("battleLogs").document(logId),
                mapOf(
                    "roomId" to room.id,
                    "roundNumber" to room.roundNumber,
                    "actorUid" to uid,
                    "actorName" to senderDisplayName.take(40),
                    "eventType" to eventType.name,
                    "summaryAr" to logSummary.take(400),
                    "createdAtEpochMs" to now,
                    "memberUids" to room.memberUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun postSystemMessage(
        room: OnlineRoomDoc,
        channel: String,
        targetUid: String,
        senderName: String,
        plainText: String,
        cipherText: String
    ): Result<Unit> {
        val msgId = "sys_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val path = "rooms/${room.id}/messages/$msgId"
        return try {
            val uid = requireUserId()
            db.collection("rooms").document(room.id).collection("messages").document(msgId).set(
                mapOf(
                    "roomId" to room.id,
                    "senderUid" to uid,
                    "senderDisplayName" to senderName.take(40),
                    "spoofedByUid" to "",
                    "channel" to channel,
                    "targetUid" to targetUid,
                    "plainText" to plainText.take(500),
                    "cipherText" to cipherText.take(500),
                    "decoyTrap" to false,
                    "decoyTriggered" to false,
                    "createdAtEpochMs" to System.currentTimeMillis(),
                    "memberUids" to room.memberUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateRoomFields(roomId: String, updates: Map<String, Any>): Result<Unit> {
        val path = "rooms/$roomId"
        return try {
            requireUserId()
            val payload = updates.toMutableMap()
            payload["updatedAt"] = FieldValue.serverTimestamp()
            db.collection("rooms").document(roomId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePublicPlayer(roomId: String, targetUid: String, updates: Map<String, Any>): Result<Unit> {
        val path = "rooms/$roomId/publicPlayers/$targetUid"
        return try {
            requireUserId()
            val payload = updates.toMutableMap()
            payload["updatedAt"] = FieldValue.serverTimestamp()
            db.collection("rooms").document(roomId)
                .collection("publicPlayers").document(targetUid)
                .update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendHeartbeat(roomId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        return updatePublicPlayer(
            roomId,
            uid,
            mapOf("lastActiveEpochMs" to System.currentTimeMillis(), "afkWarning" to false)
        )
    }

    suspend fun updatePrivateRole(roomId: String, updates: Map<String, Any>): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "rooms/$roomId/privateRoles/${uid ?: "unknown"}"
        return try {
            val verifiedUid = requireUserId()
            val payload = updates.toMutableMap()
            payload["updatedAt"] = FieldValue.serverTimestamp()
            db.collection("rooms").document(roomId)
                .collection("privateRoles").document(verifiedUid)
                .update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSpyNetworkWiretap(
        roomId: String,
        spyNet: SpyNetworkDoc,
        spyUid: String,
        targetUid: String,
        targetName: String,
        expiresEpochMs: Long
    ): Result<Unit> {
        val path = "rooms/$roomId/spyNetwork/shared"
        return try {
            val verifiedUid = requireUserId()
            if (verifiedUid != spyUid) return Result.failure(IllegalStateException("Unauthorized spy update"))
            val updates = when (spyUid) {
                spyNet.spy1Uid -> mapOf(
                    "spy1TargetUid" to targetUid.take(128),
                    "spy1TargetName" to targetName.take(40),
                    "spy1ExpiresEpochMs" to expiresEpochMs.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                spyNet.spy2Uid -> mapOf(
                    "spy2TargetUid" to targetUid.take(128),
                    "spy2TargetName" to targetName.take(40),
                    "spy2ExpiresEpochMs" to expiresEpochMs.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                else -> return Result.success(Unit)
            }
            db.collection("rooms").document(roomId)
                .collection("spyNetwork").document("shared")
                .update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun revealNextCodeSlotTransaction(
        roomId: String,
        extraFields: Map<String, Any> = emptyMap(),
        setDelayedAlertToNewCount: Boolean = false
    ): Result<Long> {
        val path = "rooms/$roomId"
        return try {
            requireUserId()
            val roomRef = db.collection("rooms").document(roomId)
            val newCount = db.runTransaction { tx ->
                val snap = tx.get(roomRef)
                val currentCount = (snap.getLong("spyRevealedCount") ?: 0L).coerceIn(0L, 4L)
                val nextCount = (currentCount + 1L).coerceAtMost(4L)
                val payload = extraFields.toMutableMap()
                payload["spyRevealedCount"] = nextCount
                if (setDelayedAlertToNewCount) {
                    payload["delayedAlertSlot"] = nextCount
                }
                payload["updatedAt"] = FieldValue.serverTimestamp()
                tx.update(roomRef, payload)
                nextCount
            }.await()
            Result.success(newCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun grantRareCardToSelf(roomId: String, card: RareCardType, currentCards: List<String>): Result<Unit> {
        return updatePrivateRole(roomId, mapOf("rareCards" to (currentCards + card.name).take(6)))
    }

    suspend fun markDecoyTriggered(roomId: String, messageId: String): Result<Unit> {
        val path = "rooms/$roomId/messages/$messageId"
        return try {
            requireUserId()
            db.collection("rooms").document(roomId)
                .collection("messages").document(messageId)
                .update(mapOf("decoyTriggered" to true))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun tryCompleteAuditSuccessTransaction(roomId: String, expectedAuditId: String): Result<Boolean> {
        val path = "rooms/$roomId"
        return try {
            requireUserId()
            val roomRef = db.collection("rooms").document(roomId)
            val won = db.runTransaction { tx ->
                val snap = tx.get(roomRef)
                val currentId = snap.getString("auditId") ?: ""
                val wrongReported = snap.getBoolean("auditWrongReported") ?: false
                val successReported = snap.getBoolean("auditSuccessReported") ?: false
                if (currentId != expectedAuditId || currentId.isBlank() || wrongReported || successReported) {
                    false
                } else {
                    tx.update(
                        roomRef,
                        mapOf(
                            "auditSuccessReported" to true,
                            "auditEndsEpochMs" to 0L,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    true
                }
            }.await()
            Result.success(won)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun openBlackChannel(roomId: String): Result<Unit> {
        val path = "rooms/$roomId/spyNetwork/shared"
        return try {
            val verifiedUid = requireUserId()
            val now = System.currentTimeMillis()
            val expires = now + com.example.core.BlackChannelConfig.ONLINE_DURATION_MS
            val spyNetRef = db.collection("rooms").document(roomId)
                .collection("spyNetwork").document("shared")
            db.runTransaction { tx ->
                val snap = tx.get(spyNetRef)
                val spyUids = (snap.get("spyUids") as? List<*>)?.filterIsInstance<String>().orEmpty()
                require(verifiedUid in spyUids) { "القناة السوداء متاحة للمتجسس فقط" }
                val closed = snap.getBoolean("blackChannelClosed") ?: false
                val openedAt = snap.getLong("blackChannelOpenedAtEpochMs") ?: 0L
                if (!closed && openedAt == 0L) {
                    tx.update(
                        spyNetRef,
                        mapOf(
                            "blackChannelOpenedAtEpochMs" to now,
                            "blackChannelExpiresEpochMs" to expires,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun closeBlackChannelIfExpired(roomId: String): Result<Unit> {
        val path = "rooms/$roomId/spyNetwork/shared"
        return try {
            requireUserId()
            db.collection("rooms").document(roomId)
                .collection("spyNetwork").document("shared")
                .update(
                    mapOf(
                        "blackChannelClosed" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendBlackChannelMessage(
        room: OnlineRoomDoc,
        senderRealName: String,
        text: String,
        style: com.example.core.BlackChannelBroadcastStyle
    ): Result<Boolean> {
        val path = "rooms/${room.id}/spyNetwork/shared"
        return try {
            val verifiedUid = requireUserId()
            val clean = text.trim().take(220)
            require(clean.isNotEmpty()) { "النص فارغ" }
            val now = System.currentTimeMillis()
            val spyNetRef = db.collection("rooms").document(room.id)
                .collection("spyNetwork").document("shared")

            val sentAndNowClosed = db.runTransaction { tx ->
                val snap = tx.get(spyNetRef)
                val spyUids = (snap.get("spyUids") as? List<*>)?.filterIsInstance<String>().orEmpty()
                require(verifiedUid in spyUids) { "القناة السوداء متاحة للمتجسس فقط" }
                val closed = snap.getBoolean("blackChannelClosed") ?: false
                val openedAt = snap.getLong("blackChannelOpenedAtEpochMs") ?: 0L
                val expiresAt = snap.getLong("blackChannelExpiresEpochMs") ?: 0L
                val uses = snap.getLong("blackChannelUsesCount") ?: 0L
                require(!closed && openedAt > 0L && now < expiresAt && uses < com.example.core.BlackChannelConfig.MAX_USES) {
                    "القناة السوداء مغلقة أو استُنفدت محاولاتها"
                }
                val nextUses = uses + 1L
                val willClose = nextUses >= com.example.core.BlackChannelConfig.MAX_USES.toLong()
                tx.update(
                    spyNetRef,
                    mapOf(
                        "blackChannelUsesCount" to nextUses,
                        "blackChannelClosed" to willClose,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                willClose
            }.await()

            postSystemMessage(
                room = room,
                channel = "ALERT",
                targetUid = "",
                senderName = style.senderTitle,
                plainText = clean,
                cipherText = clean
            )

            logBattleEvent(
                room = room,
                actorUid = verifiedUid,
                actorName = senderRealName,
                eventType = BattleEventType.ABILITY,
                summaryAr = "بث المتجسس ($senderRealName) عبر [القناة السوداء] بصيغة (${style.labelAr}): «$clean»"
            )
            Result.success(sentAndNowClosed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
