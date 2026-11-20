package com.example.online

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed interface OnlineUiStatus {
    data object Idle : OnlineUiStatus
    data object Loading : OnlineUiStatus
    data class Error(val message: String) : OnlineUiStatus
}

data class SharedWiretapUi(
    val targetUid: String,
    val targetName: String,
    val secondsLeft: Int,
    val isFromAlly: Boolean
)

data class OnlineGameUiState(
    val status: OnlineUiStatus = OnlineUiStatus.Idle,
    val isConnected: Boolean = true,
    val userProfile: UserProfileDoc? = null,
    val afkCooldownSeconds: Int = 0,
    val myRooms: List<OnlineRoomDoc> = emptyList(),
    val publicRooms: List<OnlineRoomDoc> = emptyList(),
    val activeRoomId: String? = null,
    val room: OnlineRoomDoc? = null,
    val publicPlayers: List<PublicPlayerDoc> = emptyList(),
    val privateRole: PrivateRoleDoc? = null,
    val spyNetwork: SpyNetworkDoc? = null,
    val sharedWiretapTargets: List<SharedWiretapUi> = emptyList(),
    val messages: List<RoomMessageDoc> = emptyList(),
    // Strictly empty during active play; populated ONLY when room.status == "FINISHED"
    val battleLogs: List<BattleLogDoc> = emptyList(),

    // Local real-time UI timers
    val secondsRemaining: Int = 600,
    val messageCooldownSeconds: Int = 0,
    val lockoutSecondsLeft: Int = 0,
    val frozenSecondsLeft: Int = 0,
    val discussionSecondsLeft: Int = 0,
    val auditSecondsLeft: Int = 0,
    val wiretapSecondsLeft: Int = 0,
    val wiretapCycleSecondsLeft: Int = 60,
    val blackChannelSecondsLeft: Int = 0,

    // Spy puzzle state
    val activePuzzle: ActivePuzzle? = null,
    val puzzleStartMs: Long = 0L,
    val puzzleSecondsLeft: Int = 0,
    val lastPuzzleType: PuzzleType? = null,
    val eliminateTwoWrongNextCipher: Boolean = false,

    // Final code keypad
    val showFinalCodeDialog: Boolean = false,
    val finalCodeSecondsLeft: Int = 10,

    // Screens & Alerts
    val showStatisticsScreen: Boolean = false,
    val bannerMessage: String? = null,
    val radarScanPopup: String? = null,
    val showWiretapPrompt: Boolean = false
) {
    val activeWiretapTargetUids: Set<String>
        get() = sharedWiretapTargets.map { it.targetUid }.toSet()

    val isBlackChannelReady: Boolean
        get() {
            val net = spyNetwork ?: return false
            return !net.blackChannelClosed && net.blackChannelOpenedAtEpochMs == 0L
        }

    val isBlackChannelOpen: Boolean
        get() {
            val net = spyNetwork ?: return false
            return !net.blackChannelClosed &&
                net.blackChannelOpenedAtEpochMs > 0L &&
                blackChannelSecondsLeft > 0 &&
                net.blackChannelUsesCount < BlackChannelConfig.MAX_USES
        }

    val isBlackChannelPermanentlyClosed: Boolean
        get() {
            val net = spyNetwork ?: return false
            return net.blackChannelClosed ||
                net.blackChannelUsesCount >= BlackChannelConfig.MAX_USES ||
                (net.blackChannelOpenedAtEpochMs > 0L && blackChannelSecondsLeft == 0)
        }

    val blackChannelUsesRemaining: Int
        get() {
            val used = spyNetwork?.blackChannelUsesCount?.toInt() ?: 0
            return (BlackChannelConfig.MAX_USES - used).coerceAtLeast(0)
        }
}

class OnlineGameViewModel(
    private val repository: OnlineRoomRepository,
    private val currentUserId: String,
    appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnlineGameUiState())
    val uiState: StateFlow<OnlineGameUiState> = _uiState.asStateFlow()

    private var roomListenersJob: Job? = null
    private var spyNetworkJob: Job? = null
    private var spyNetworkKey: String? = null
    private var battleLogsJob: Job? = null
    private var tickerJob: Job? = null
    private var puzzleJob: Job? = null
    private var finalCodeJob: Job? = null

    private var battleLogsLoadedKey: String? = null
    private var outcomeRecordedKey: String? = null
    private var rare7GrantedKey: String? = null
    private var rare3GrantedKey: String? = null
    private val triggeredDecoyMsgIds = mutableSetOf<String>()
    private var lastExpiredWiretapEpochMs: Long = 0L
    private var lastHeartbeatEpochMs: Long = 0L
    private var votingStartedAtEpochMs: Long = 0L
    private var lastTalliedRoundKey: String? = null
    private val afkWarnedKeys = mutableSetOf<String>()
    private var lastAutoSkipVotingKey: String? = null

    init {
        viewModelScope.launch {
            ConnectivityHelper.observeConnectivity(appContext).collect { connected ->
                _uiState.update { it.copy(isConnected = connected) }
                if (connected) {
                    launch { repository.syncCloudContentPacks() }
                }
            }
        }
        viewModelScope.launch {
            repository.syncCloudContentPacks()
        }
        viewModelScope.launch {
            repository.observeUserProfile(currentUserId)
                .catch { }
                .collect { profile ->
                    val cooldown = if (profile != null && profile.afkPenaltyUntilEpochMs > System.currentTimeMillis()) {
                        ((profile.afkPenaltyUntilEpochMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
                    } else 0
                    _uiState.update { it.copy(userProfile = profile, afkCooldownSeconds = cooldown) }
                }
        }
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                val profile = _uiState.value.userProfile
                val cooldown = if (profile != null && profile.afkPenaltyUntilEpochMs > System.currentTimeMillis()) {
                    ((profile.afkPenaltyUntilEpochMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
                } else 0
                if (cooldown != _uiState.value.afkCooldownSeconds) {
                    _uiState.update { it.copy(afkCooldownSeconds = cooldown) }
                }
            }
        }
        viewModelScope.launch {
            repository.observeMyRooms(currentUserId)
                .catch { error ->
                    val root = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull() ?: error
                    _uiState.update { it.copy(status = OnlineUiStatus.Error(root.message ?: "خطأ في الاتصال بالغرف")) }
                }
                .collect { rooms ->
                    _uiState.update { it.copy(myRooms = rooms) }
                }
        }
        viewModelScope.launch {
            repository.observePublicWaitingRooms(currentUserId)
                .catch { }
                .collect { rooms ->
                    _uiState.update { it.copy(publicRooms = rooms) }
                }
        }
    }

    fun ensureProfile(displayName: String) {
        viewModelScope.launch {
            repository.ensureUserProfile(displayName).onSuccess { profile ->
                val cooldown = if (profile.afkPenaltyUntilEpochMs > System.currentTimeMillis()) {
                    ((profile.afkPenaltyUntilEpochMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
                } else 0
                _uiState.update { it.copy(userProfile = profile, afkCooldownSeconds = cooldown) }
            }
        }
    }

    fun createRoom(
        hostName: String,
        roomName: String = "غرفة عمليات سرية",
        publicRoom: Boolean = true,
        maxPlayers: Int = 8,
        durationSeconds: Long = 600L
    ) {
        val cooldown = _uiState.value.afkCooldownSeconds
        if (cooldown > 0) {
            _uiState.update {
                it.copy(status = OnlineUiStatus.Error("عليك عقوبة انسحاب نشطة! انتظر ${cooldown} ثانية قبل إنشاء غرفة."))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(status = OnlineUiStatus.Loading) }
            val result = repository.createRoom(
                hostName = hostName,
                roomName = roomName,
                publicRoom = publicRoom,
                maxPlayers = maxPlayers,
                durationSeconds = durationSeconds
            )
            result.fold(
                onSuccess = { roomId ->
                    _uiState.update { it.copy(status = OnlineUiStatus.Idle) }
                    attachToRoom(roomId)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(status = OnlineUiStatus.Error(err.message ?: "تعذر إنشاء الغرفة")) }
                }
            )
        }
    }

    fun joinRoom(roomCode: String, playerName: String) {
        val cooldown = _uiState.value.afkCooldownSeconds
        if (cooldown > 0) {
            _uiState.update {
                it.copy(status = OnlineUiStatus.Error("عليك عقوبة انسحاب نشطة! انتظر ${cooldown} ثانية قبل الانضمام."))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(status = OnlineUiStatus.Loading) }
            val result = repository.joinRoomByCode(roomCode, playerName)
            result.fold(
                onSuccess = { roomId ->
                    _uiState.update { it.copy(status = OnlineUiStatus.Idle) }
                    attachToRoom(roomId)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(status = OnlineUiStatus.Error(err.message ?: "تعذر الانضمام للغرفة")) }
                }
            )
        }
    }

    fun joinPublicRoom(roomId: String, playerName: String) {
        val cooldown = _uiState.value.afkCooldownSeconds
        if (cooldown > 0) {
            _uiState.update {
                it.copy(status = OnlineUiStatus.Error("عليك عقوبة انسحاب نشطة! انتظر ${cooldown} ثانية قبل الانضمام."))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(status = OnlineUiStatus.Loading) }
            val result = repository.joinRoomById(roomId, playerName)
            result.fold(
                onSuccess = { id ->
                    _uiState.update { it.copy(status = OnlineUiStatus.Idle) }
                    attachToRoom(id)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(status = OnlineUiStatus.Error(err.message ?: "تعذر الانضمام للغرفة")) }
                }
            )
        }
    }

    fun attachToRoom(roomId: String) {
        if (_uiState.value.activeRoomId == roomId && roomListenersJob != null) return
        roomListenersJob?.cancel()
        spyNetworkJob?.cancel()
        spyNetworkJob = null
        spyNetworkKey = null
        battleLogsJob?.cancel()
        battleLogsJob = null
        battleLogsLoadedKey = null
        rare7GrantedKey = null
        rare3GrantedKey = null
        triggeredDecoyMsgIds.clear()
        tickerJob?.cancel()
        lastExpiredWiretapEpochMs = 0L
        votingStartedAtEpochMs = 0L

        _uiState.update {
            it.copy(
                activeRoomId = roomId,
                spyNetwork = null,
                sharedWiretapTargets = emptyList(),
                showStatisticsScreen = false,
                battleLogs = emptyList(),
                bannerMessage = null,
                radarScanPopup = null,
                activePuzzle = null,
                showFinalCodeDialog = false
            )
        }

        roomListenersJob = viewModelScope.launch {
            launch {
                repository.observeRoom(roomId)
                    .catch { err ->
                        _uiState.update { it.copy(status = OnlineUiStatus.Error(err.message ?: "فقدان الاتصال بالغرفة")) }
                    }
                    .collect { room ->
                        _uiState.update { it.copy(room = room) }
                        if (room != null) {
                            handleRoomStateEffects(room)
                        }
                    }
            }
            launch {
                repository.observePublicPlayers(roomId, currentUserId)
                    .catch { }
                    .collect { list ->
                        _uiState.update { it.copy(publicPlayers = list) }
                        val room = _uiState.value.room
                        if (room != null && room.status == "VOTING") {
                            val alivePlayers = list.filter { it.alive }
                            val roundKey = "${room.id}_${room.roundNumber}_${room.pausedAtEpochMs}"
                            if (alivePlayers.isNotEmpty() && alivePlayers.all { it.hasVoted } &&
                                room.hostId == currentUserId && lastTalliedRoundKey != roundKey
                            ) {
                                lastTalliedRoundKey = roundKey
                                launch { tallyOnlineVotes(room, alivePlayers) }
                            }
                        }
                    }
            }
            launch {
                repository.observePrivateRole(roomId, currentUserId)
                    .catch { }
                    .collect { priv ->
                        if (priv?.role == "SPY") {
                            if (spyNetworkKey != roomId) {
                                spyNetworkKey = roomId
                                spyNetworkJob?.cancel()
                                spyNetworkJob = viewModelScope.launch {
                                    repository.observeSpyNetwork(roomId, currentUserId)
                                        .catch { }
                                        .collect { net ->
                                            val now = System.currentTimeMillis()
                                            _uiState.update { st ->
                                                val shared = if (st.room?.status == "PLAYING") {
                                                    computeSharedWiretaps(st.privateRole, net, now)
                                                } else emptyList()
                                                st.copy(spyNetwork = net, sharedWiretapTargets = shared)
                                            }
                                        }
                                }
                            }
                        } else {
                            if (spyNetworkKey != null) {
                                spyNetworkKey = null
                                spyNetworkJob?.cancel()
                                spyNetworkJob = null
                            }
                        }

                        _uiState.update { st ->
                            val nextNet = if (priv?.role == "SPY") st.spyNetwork else null
                            val shared = if (st.room?.status == "PLAYING") {
                                computeSharedWiretaps(priv, nextNet, System.currentTimeMillis())
                            } else emptyList()
                            st.copy(
                                privateRole = priv,
                                spyNetwork = nextNet,
                                sharedWiretapTargets = shared
                            )
                        }
                        checkWiretapDecoyTrap(_uiState.value.messages)
                    }
            }
            launch {
                repository.observeMessages(roomId, currentUserId)
                    .catch { }
                    .collect { msgs ->
                        _uiState.update { it.copy(messages = msgs) }
                        checkWiretapDecoyTrap(msgs)
                    }
            }
        }

        startLiveTicker()
    }

    private fun handleRoomStateEffects(room: OnlineRoomDoc) {
        val roundKey = "${room.id}_${room.roundNumber}"

        // Track when VOTING started for AFK 45s auto-skip protection
        if (room.status == "VOTING") {
            if (votingStartedAtEpochMs == 0L) {
                votingStartedAtEpochMs = System.currentTimeMillis()
            }
        } else {
            votingStartedAtEpochMs = 0L
        }

        // Strict Battle Log security boundary: NEVER read or display battleLogs until FINISHED!
        if (room.status == "FINISHED") {
            if (battleLogsLoadedKey != roundKey) {
                battleLogsLoadedKey = roundKey
                battleLogsJob?.cancel()
                battleLogsJob = viewModelScope.launch {
                    repository.observeBattleLogs(room.id, currentUserId)
                        .catch { }
                        .collect { logs ->
                            _uiState.update { state ->
                                // Double-check room is still FINISHED
                                if (state.room?.status == "FINISHED") {
                                    state.copy(battleLogs = logs)
                                } else {
                                    state.copy(battleLogs = emptyList())
                                }
                            }
                        }
                }
            }

            if (room.winnerTeam.isNotBlank() && outcomeRecordedKey != roundKey) {
                outcomeRecordedKey = roundKey
                val isSpy = _uiState.value.privateRole?.role == "SPY" || currentUserId in room.spyUids
                val won = (room.winnerTeam == "SPY" && isSpy) || (room.winnerTeam == "ORG" && !isSpy)
                viewModelScope.launch { repository.recordMatchOutcomeOnProfile(won = won, asSpy = isSpy) }
            }
        } else {
            if (battleLogsLoadedKey != null || _uiState.value.battleLogs.isNotEmpty()) {
                battleLogsJob?.cancel()
                battleLogsJob = null
                battleLogsLoadedKey = null
                _uiState.update { it.copy(battleLogs = emptyList(), showStatisticsScreen = false) }
            }
        }

        val priv = _uiState.value.privateRole
        val meAlive = _uiState.value.publicPlayers.firstOrNull { it.userId == currentUserId }?.alive ?: true
        // Grant Rare Cards automatically at 70% remaining and 30% remaining (aligned with 7:00 and 3:00 on 10m match)
        if (priv != null && room.status == "PLAYING" && meAlive) {
            val remaining = computeRemainingSeconds(room)
            val milestone7Threshold = (room.durationSeconds * 0.70).toInt()
            val milestone3Threshold = (room.durationSeconds * 0.30).toInt()
            val spyPool = listOf(RareCardType.ZERO_DAY_EXPLOIT, RareCardType.IDENTITY_SPOOF, RareCardType.ALARM_JAMMER)
            val orgPool = listOf(RareCardType.DECOY_TRAP, RareCardType.SYSTEM_FREEZE, RareCardType.DEEP_RADAR_SCAN)

            if (remaining <= milestone7Threshold && !room.rareMilestone7Granted && room.hostId == currentUserId) {
                viewModelScope.launch {
                    repository.updateRoomFields(room.id, mapOf("rareMilestone7Granted" to true))
                    repository.postSystemMessage(
                        room,
                        "ALERT",
                        "",
                        "النظام",
                        "محطة الإمداد الأولى — تم إتاحة بطاقة نادرة تكتيكية في صف بطاقاتك!",
                        "توزيع بطاقات نادرة"
                    )
                }
            }
            if (remaining <= milestone3Threshold && !room.rareMilestone3Granted && room.hostId == currentUserId) {
                viewModelScope.launch {
                    repository.updateRoomFields(room.id, mapOf("rareMilestone3Granted" to true))
                    repository.postSystemMessage(
                        room,
                        "ALERT",
                        "",
                        "النظام",
                        "محطة المرحلة الحاسمة — تم إتاحة بطاقة نادرة جديدة في صف بطاقاتك!",
                        "بطاقة نادرة للمرحلة الأخيرة"
                    )
                }
            }

            if (remaining <= milestone7Threshold && room.rareMilestone7Granted && rare7GrantedKey != roundKey) {
                rare7GrantedKey = roundKey
                val pool = if (priv.role == "SPY") spyPool else orgPool
                val card = pool.filter { it.name !in priv.rareCards }.ifEmpty { pool }.random()
                viewModelScope.launch { repository.grantRareCardToSelf(room.id, card, priv.rareCards) }
            } else if (remaining <= milestone3Threshold && room.rareMilestone3Granted && rare3GrantedKey != roundKey) {
                rare3GrantedKey = roundKey
                val pool = if (priv.role == "SPY") spyPool else orgPool
                val card = pool.filter { it.name !in priv.rareCards }.ifEmpty { pool }.random()
                viewModelScope.launch { repository.grantRareCardToSelf(room.id, card, priv.rareCards) }
            }
        }

        // Check voting completion when all alive players have voted
        val alivePlayers = _uiState.value.publicPlayers.filter { it.alive }
        val voteRoundKey = "${room.id}_${room.roundNumber}_${room.pausedAtEpochMs}"
        if (room.status == "VOTING" && alivePlayers.isNotEmpty() && alivePlayers.all { it.hasVoted } &&
            room.hostId == currentUserId && lastTalliedRoundKey != voteRoundKey
        ) {
            lastTalliedRoundKey = voteRoundKey
            viewModelScope.launch { tallyOnlineVotes(room, alivePlayers) }
        }
    }

    private fun computeRemainingSeconds(room: OnlineRoomDoc): Int {
        if (room.roundStartEpochMs <= 0L) return room.durationSeconds.toInt()
        val now = System.currentTimeMillis()
        val currentPause = if (room.pausedAtEpochMs > 0L) (now - room.pausedAtEpochMs) else 0L
        val elapsedMs = (now - room.roundStartEpochMs) - room.accumulatedPauseMs - currentPause
        return (room.durationSeconds - (elapsedMs / 1000L)).toInt().coerceIn(0, room.durationSeconds.toInt())
    }

    private fun startLiveTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val state = _uiState.value
                val room = state.room ?: continue
                val now = System.currentTimeMillis()

                val remSec = computeRemainingSeconds(room)
                val cooldown = (state.messageCooldownSeconds - 1).coerceAtLeast(0)
                val lockout = ((room.spyLockoutUntilEpochMs - now) / 1000L).toInt().coerceAtLeast(0)
                val frozen = ((room.spyFrozenUntilEpochMs - now) / 1000L).toInt().coerceAtLeast(0)
                val disc = if (room.status == "INVESTIGATION") {
                    ((room.investigationEndsEpochMs - now) / 1000L).toInt().coerceAtLeast(0)
                } else 0
                val auditRem = if (room.auditEndsEpochMs > now) {
                    ((room.auditEndsEpochMs - now) / 1000L).toInt().coerceAtLeast(0)
                } else 0

                // Targeted Eavesdropping 1-Minute Timer management (own + shared ally wiretaps)
                val priv = state.privateRole
                val isSpy = priv?.role == "SPY"
                var wiretapSecLeft = 0
                var wiretapCycleLeft = 60
                var expiredJustNow = false

                if (isSpy && priv != null && room.status == "PLAYING") {
                    if (priv.wiretapTargetUid.isNotBlank() && priv.wiretapExpiresEpochMs > 0L) {
                        if (now < priv.wiretapExpiresEpochMs) {
                            wiretapSecLeft = ((priv.wiretapExpiresEpochMs - now) / 1000L).toInt().coerceAtLeast(1)
                            wiretapCycleLeft = wiretapSecLeft
                        } else if (lastExpiredWiretapEpochMs != priv.wiretapExpiresEpochMs) {
                            lastExpiredWiretapEpochMs = priv.wiretapExpiresEpochMs
                            expiredJustNow = true
                            wiretapSecLeft = 0
                            wiretapCycleLeft = 60
                            // Reset target and restart a fresh 1-minute eavesdropping cycle
                            repository.updatePrivateRole(
                                room.id,
                                mapOf(
                                    "wiretapTargetUid" to "",
                                    "wiretapTargetName" to "",
                                    "wiretapExpiresEpochMs" to 0L,
                                    "wiretapCycleStartedEpochMs" to now
                                )
                            )
                            state.spyNetwork?.let { net ->
                                repository.updateSpyNetworkWiretap(
                                    roomId = room.id,
                                    spyNet = net,
                                    spyUid = currentUserId,
                                    targetUid = "",
                                    targetName = "",
                                    expiresEpochMs = 0L
                                )
                            }
                        }
                    } else if (priv.wiretapCycleStartedEpochMs > 0L) {
                        val elapsedSinceCycle = ((now - priv.wiretapCycleStartedEpochMs) / 1000L).toInt().coerceAtLeast(0)
                        wiretapCycleLeft = (60 - (elapsedSinceCycle % 60)).coerceIn(1, 60)
                    }
                }

                val sharedTargets = if (isSpy && room.status == "PLAYING") {
                    computeSharedWiretaps(priv, state.spyNetwork, now)
                } else {
                    emptyList()
                }

                // Spy-exclusive Black Channel 2-minute window countdown & auto-lock
                val net = state.spyNetwork
                var blackChannelLeft = 0
                if (isSpy && net != null && room.status == "PLAYING") {
                    if (!net.blackChannelClosed && net.blackChannelOpenedAtEpochMs > 0L) {
                        if (now < net.blackChannelExpiresEpochMs) {
                            blackChannelLeft = ((net.blackChannelExpiresEpochMs - now) / 1000L).toInt().coerceAtLeast(1)
                        } else {
                            blackChannelLeft = 0
                            launch { repository.closeBlackChannelIfExpired(room.id) }
                        }
                    }
                }

                // Heartbeat every 20s for AFK monitoring
                val me = state.publicPlayers.firstOrNull { it.userId == currentUserId }
                if (me != null && me.alive && room.status in listOf("PLAYING", "INVESTIGATION", "VOTING") &&
                    now - lastHeartbeatEpochMs >= 20_000L
                ) {
                    lastHeartbeatEpochMs = now
                    launch { repository.sendHeartbeat(room.id) }
                }

                _uiState.update {
                    it.copy(
                        secondsRemaining = remSec,
                        messageCooldownSeconds = cooldown,
                        lockoutSecondsLeft = lockout,
                        frozenSecondsLeft = frozen,
                        discussionSecondsLeft = disc,
                        auditSecondsLeft = auditRem,
                        wiretapSecondsLeft = wiretapSecLeft,
                        wiretapCycleSecondsLeft = wiretapCycleLeft,
                        blackChannelSecondsLeft = blackChannelLeft,
                        sharedWiretapTargets = sharedTargets,
                        showWiretapPrompt = if (expiredJustNow) true else it.showWiretapPrompt,
                        bannerMessage = if (expiredJustNow) {
                            "انتهت دقيقة التنصت المحددة! عاد مؤقت التنصت لدقيقة جديدة — اختر هدفك التالي."
                        } else it.bannerMessage
                    )
                }

                // Host coordinates 60s system events, delayed zero-day alerts, AFK watchdog, timeout, and investigation->voting
                if (room.hostId == currentUserId && room.status != "WAITING" && room.status != "FINISHED") {
                    if (room.status == "PLAYING" && remSec == 0) {
                        repository.logBattleEvent(
                            room = room,
                            actorUid = currentUserId,
                            actorName = "النظام",
                            eventType = BattleEventType.ROUND_END,
                            summaryAr = "انتهى الوقت الرسمي للجولة! انتصرت المنظمة بصمودها في حماية الكود."
                        )
                        repository.updateRoomFields(
                            room.id,
                            mapOf(
                                "status" to "FINISHED",
                                "winnerTeam" to "ORG",
                                "winReason" to "TIME_OUT"
                            )
                        )
                    } else if (room.status == "INVESTIGATION" && disc == 0) {
                        votingStartedAtEpochMs = now
                        repository.updateRoomFields(room.id, mapOf("status" to "VOTING"))
                    } else if (room.status == "VOTING" && votingStartedAtEpochMs > 0L && now - votingStartedAtEpochMs >= 45_000L) {
                        val voteTimeoutKey = "${room.id}_${room.roundNumber}_${votingStartedAtEpochMs}"
                        if (lastAutoSkipVotingKey != voteTimeoutKey) {
                            lastAutoSkipVotingKey = voteTimeoutKey
                            val nonVoters = state.publicPlayers.filter { it.alive && !it.hasVoted }
                            nonVoters.forEach { afkPlayer ->
                                repository.updatePublicPlayer(
                                    room.id,
                                    afkPlayer.userId,
                                    mapOf("hasVoted" to true, "voteTargetUid" to "SKIP", "afkWarning" to true)
                                )
                                repository.logBattleEvent(
                                    room = room,
                                    actorUid = afkPlayer.userId,
                                    actorName = afkPlayer.displayName,
                                    eventType = BattleEventType.PENALTY,
                                    summaryAr = "تخطّى النظام صوت (${afkPlayer.displayName}) تلقائياً بعد انتهاء مهلة التصويت (45ث)."
                                )
                            }
                        }
                    } else if (room.status == "PLAYING") {
                        // AFK Watchdog: warn if no heartbeat for >90s
                        state.publicPlayers.filter { it.alive && !it.afkWarning && !it.deserted }.forEach { player ->
                            val warnKey = "${room.id}_${room.roundNumber}_${player.userId}"
                            if (player.lastActiveEpochMs > 0L && now - player.lastActiveEpochMs > 90_000L && warnKey !in afkWarnedKeys) {
                                afkWarnedKeys += warnKey
                                repository.updatePublicPlayer(room.id, player.userId, mapOf("afkWarning" to true))
                                repository.logBattleEvent(
                                    room = room,
                                    actorUid = player.userId,
                                    actorName = player.displayName,
                                    eventType = BattleEventType.PENALTY,
                                    summaryAr = "رصد النظام خمول اللاعب (${player.displayName}) لأكثر من 90 ثانية."
                                )
                            }
                        }

                        // Delayed zero-day alert check
                        if (room.delayedAlertAtEpochMs in 1..now && room.delayedAlertSlot > 0) {
                            val slotNum = room.delayedAlertSlot
                            repository.updateRoomFields(
                                room.id,
                                mapOf("delayedAlertAtEpochMs" to 0L, "delayedAlertSlot" to 0L)
                            )
                            repository.postSystemMessage(
                                room,
                                "ALERT",
                                "",
                                "تحذير أمني",
                                "تحذير أمني: تم اختراق الخانة رقم $slotNum من الكود!",
                                "تحذير أمني"
                            )
                        }
                        // Automated Channel 2 every 60s
                        val elapsedMinutes = ((room.durationSeconds - remSec) / 60L).coerceAtLeast(0L)
                        if (elapsedMinutes > room.lastSystemMinuteTriggered && elapsedMinutes > 0) {
                            triggerMinuteSystemEvents(room, elapsedMinutes)
                        }

                        // Host check: ensure active audit success alert fires even if the last remaining eligible player disconnects or drops
                        if (room.auditId.isNotBlank() && !room.auditWrongReported && !room.auditSuccessReported) {
                            val eligibleAlive = state.publicPlayers.filter {
                                it.alive && it.userId != room.auditSenderUid
                            }
                            if (eligibleAlive.isNotEmpty() && eligibleAlive.all { it.auditAnsweredId == room.auditId }) {
                                val won = repository.tryCompleteAuditSuccessTransaction(room.id, room.auditId).getOrDefault(false)
                                if (won) {
                                    val okText = SmartIdentityHintGenerator.formatSuccessInvestigationAlert()
                                    repository.postSystemMessage(
                                        room,
                                        "ALERT",
                                        "",
                                        "النظام الأمني",
                                        okText,
                                        okText
                                    )
                                    repository.logBattleEvent(
                                        room = room,
                                        actorUid = currentUserId,
                                        actorName = "النظام",
                                        eventType = BattleEventType.INVESTIGATION,
                                        summaryAr = "أجاب جميع اللاعبين بشكل صحيح على سؤال النظام التلقائي."
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun triggerMinuteSystemEvents(room: OnlineRoomDoc, minuteIndex: Long) {
        val (reportId, reportText) = TemplatesAndWordBank.pickNextSpyReport(room.usedSpyReportIds.toSet())
        val updatedReportIds = (room.usedSpyReportIds + reportId).distinct().take(500)

        repository.updateRoomFields(
            room.id,
            mapOf(
                "lastSystemMinuteTriggered" to minuteIndex,
                "usedSpyReportIds" to updatedReportIds
            )
        )

        val cipherType = enumValues<CipherType>().firstOrNull { it.name == room.cipherType } ?: CipherType.CAESAR_FORWARD
        val spyHint = CipherEngine.generateSpyHint(
            cipherType,
            room.cipherShift.toInt(),
            room.codeSlots,
            room.spyRevealedCount.toInt()
        )
        val combinedSpyIntel = "$reportText\n$spyHint"
        repository.postSystemMessage(room, "SYSTEM_SPY", "", "مساعد الاختراق", combinedSpyIntel, combinedSpyIntel)

        // Org assist
        val players = _uiState.value.publicPlayers.filter { it.alive }
        val orgCandidates = players.filter { it.userId !in room.spyUids }
        when (Random.nextInt(3)) {
            0 -> {
                if (System.currentTimeMillis() - room.spyLastHackEpochMs <= 60_000L && room.spyLastHackEpochMs > 0) {
                    val target = orgCandidates.randomOrNull()?.userId ?: ""
                    repository.postSystemMessage(
                        room,
                        "SYSTEM_ORG",
                        target,
                        "كاشف النشاط",
                        "كاشف النشاط: أحد اللاعبين تفاعل مع وحدة فك التشفير وحل لغزاً في آخر دقيقة!",
                        "نشاط مشفر"
                    )
                }
            }
            1 -> {
                val target = orgCandidates.firstOrNull { !it.usedSlotShield }
                if (target != null) {
                    repository.postSystemMessage(
                        room,
                        "SYSTEM_ORG",
                        target.userId,
                        "درع التردد",
                        "تنبيه للعميل (${target.displayName}): لديك صلاحية تفعيل درع تغيير التردد الآن!",
                        "درع أمني"
                    )
                }
            }
            else -> {
                if (room.auditId.isBlank() || System.currentTimeMillis() > room.auditEndsEpochMs) {
                    val coreMsgs = _uiState.value.messages.map {
                        GameMessage(
                            id = it.id,
                            senderId = it.senderUid,
                            senderName = it.senderDisplayName,
                            channel = enumValues<MessageChannel>().firstOrNull { c -> c.name == it.channel } ?: MessageChannel.PLAYER,
                            plainText = it.plainText,
                            cipherText = it.cipherText
                        )
                    }
                    val audit = TemplatesAndWordBank.buildAuditPrompt(coreMsgs)
                    if (audit != null) {
                        repository.updateRoomFields(
                            room.id,
                            mapOf(
                                "auditId" to audit.id,
                                "auditQuestion" to audit.question,
                                "auditOptions" to audit.options,
                                "auditCorrectIndex" to audit.correctIndex.toLong(),
                                "auditEndsEpochMs" to (System.currentTimeMillis() + 20_000L),
                                "auditWrongReported" to false,
                                "auditSenderUid" to "",
                                "auditSuccessReported" to false
                            )
                        )
                    }
                }
            }
        }
    }

    fun toggleReady() {
        val room = _uiState.value.room ?: return
        viewModelScope.launch {
            repository.toggleReady(room)
        }
    }

    fun kickWaitingPlayer(targetUid: String) {
        val room = _uiState.value.room ?: return
        viewModelScope.launch {
            repository.kickWaitingPlayer(room, targetUid)
        }
    }

    fun updateWaitingRoomConfig(publicRoom: Boolean, maxPlayers: Int, durationSeconds: Long) {
        val room = _uiState.value.room ?: return
        if (room.hostId != currentUserId || room.status != "WAITING") return
        viewModelScope.launch {
            repository.updateWaitingRoomConfig(room.id, publicRoom, maxPlayers, durationSeconds)
        }
    }

    fun prepareRematch() {
        val room = _uiState.value.room ?: return
        if (room.hostId != currentUserId || room.status != "FINISHED") return
        viewModelScope.launch {
            _uiState.update { it.copy(status = OnlineUiStatus.Loading) }
            val res = repository.prepareRematchInSameRoom(room)
            res.fold(
                onSuccess = {
                    _uiState.update { it.copy(status = OnlineUiStatus.Idle, showStatisticsScreen = false) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(status = OnlineUiStatus.Error(err.message ?: "تعذر تحضير الجولة الجديدة")) }
                }
            )
        }
    }

    fun desertActiveMatch() {
        val state = _uiState.value
        val room = state.room ?: run {
            leaveOrCloseRoom()
            return
        }
        val me = state.publicPlayers.firstOrNull { it.userId == currentUserId }
        val myName = me?.displayName ?: state.userProfile?.displayName ?: "عميل"
        val aliveOthers = state.publicPlayers.filter { it.alive && it.userId != currentUserId }

        viewModelScope.launch {
            repository.applyDeserterPenalty(room, myName)
            // Resolve game if desertion ends spy threat or drops org below threshold
            val remainingAliveSpies = aliveOthers.count { it.userId in room.spyUids }
            if (currentUserId in room.spyUids && remainingAliveSpies == 0) {
                repository.logBattleEvent(
                    room = room,
                    actorUid = currentUserId,
                    actorName = "النظام",
                    eventType = BattleEventType.ROUND_END,
                    summaryAr = "انسحب آخر متجسس ($myName) من الغرفة — انتصار تلقائي للمنظمة!"
                )
                repository.updateRoomFields(
                    room.id,
                    mapOf("status" to "FINISHED", "winnerTeam" to "ORG", "winReason" to "SPY_ARRESTED")
                )
            } else if (aliveOthers.size <= 2 && remainingAliveSpies > 0) {
                repository.logBattleEvent(
                    room = room,
                    actorUid = currentUserId,
                    actorName = "النظام",
                    eventType = BattleEventType.ROUND_END,
                    summaryAr = "تقلّص عدد اللاعبين بعد انسحاب ($myName) — انتصر المتجسس تلقائياً!"
                )
                repository.updateRoomFields(
                    room.id,
                    mapOf("status" to "FINISHED", "winnerTeam" to "SPY", "winReason" to "TOO_FEW_ORG")
                )
            }
            leaveOrCloseRoom()
        }
    }

    fun leaveOrCloseRoom() {
        val room = _uiState.value.room
        roomListenersJob?.cancel()
        roomListenersJob = null
        spyNetworkJob?.cancel()
        spyNetworkJob = null
        spyNetworkKey = null
        battleLogsJob?.cancel()
        battleLogsJob = null
        battleLogsLoadedKey = null
        tickerJob?.cancel()
        tickerJob = null
        puzzleJob?.cancel()
        finalCodeJob?.cancel()
        if (room != null && room.status == "WAITING") {
            viewModelScope.launch { repository.leaveWaitingRoom(room) }
        }
        _uiState.update {
            it.copy(
                activeRoomId = null,
                room = null,
                privateRole = null,
                spyNetwork = null,
                sharedWiretapTargets = emptyList(),
                battleLogs = emptyList(),
                showStatisticsScreen = false
            )
        }
    }

    fun toggleTwoSpies(enabled: Boolean) {
        val room = _uiState.value.room ?: return
        viewModelScope.launch { repository.toggleTwoSpies(room.id, enabled) }
    }

    fun startMatch() {
        val room = _uiState.value.room ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(status = OnlineUiStatus.Loading) }
            val res = repository.startOnlineGame(room)
            res.fold(
                onSuccess = { _uiState.update { it.copy(status = OnlineUiStatus.Idle) } },
                onFailure = { err -> _uiState.update { it.copy(status = OnlineUiStatus.Error(err.message ?: "تعذر بدء الجولة")) } }
            )
        }
    }

    fun sendOnlineTemplateMessage(
        template: MessageTemplate,
        selectedWords: List<String>,
        useDecoyTrap: Boolean = false,
        spoofAsPlayerId: String? = null
    ) {
        val state = _uiState.value
        val room = state.room ?: return
        if (state.messageCooldownSeconds > 0) return
        val me = state.publicPlayers.firstOrNull { it.userId == currentUserId } ?: return
        if (!me.alive) return

        val cipherType = enumValues<CipherType>().firstOrNull { it.name == room.cipherType } ?: CipherType.CAESAR_FORWARD
        val plain = template.render(selectedWords)
        val ciphered = CipherEngine.encrypt(plain, cipherType, room.cipherShift.toInt())

        val newlyUsed = mutableListOf<String>()
        template.slots.forEachIndexed { idx, kind ->
            if (kind != SlotKind.AGENT_NAME) {
                selectedWords.getOrNull(idx)?.let { newlyUsed += it }
            }
        }

        val priv = state.privateRole
        val canUseDecoy = useDecoyTrap && priv != null && RareCardType.DECOY_TRAP.name in priv.rareCards
        val canUseSpoof = spoofAsPlayerId != null && priv != null && RareCardType.IDENTITY_SPOOF.name in priv.rareCards
        val spoofTarget = if (canUseSpoof) {
            state.publicPlayers.firstOrNull { it.userId == spoofAsPlayerId }
        } else null

        val autoAudit = TemplatesAndWordBank.buildAuditPromptFromReport(
            template = template,
            selectedWords = selectedWords,
            senderId = currentUserId
        )

        viewModelScope.launch {
            repository.sendOnlineMessage(
                room = room,
                senderDisplayName = spoofTarget?.displayName ?: me.displayName,
                spoofedByUid = spoofTarget?.userId ?: "",
                plainText = plain,
                cipherText = ciphered,
                newlyUsedWords = newlyUsed,
                isDecoyTrap = canUseDecoy,
                decoyTriggered = false,
                halveChargesOnTrigger = false,
                autoAudit = autoAudit
            )
            repository.sendHeartbeat(room.id)
            if (priv != null && (canUseDecoy || spoofTarget != null)) {
                var remainingCards = priv.rareCards
                if (canUseDecoy) remainingCards = remainingCards - RareCardType.DECOY_TRAP.name
                if (spoofTarget != null) remainingCards = remainingCards - RareCardType.IDENTITY_SPOOF.name
                repository.updatePrivateRole(room.id, mapOf("rareCards" to remainingCards))
            }
            _uiState.update {
                it.copy(
                    messageCooldownSeconds = 30,
                    bannerMessage = if (autoAudit != null) {
                        "تم إرسال البلاغ وطُرح سؤال النظام التلقائي على بقية اللاعبين!"
                    } else it.bannerMessage
                )
            }
        }
    }

    fun openOnlineBlackChannel() {
        val state = _uiState.value
        val room = state.room ?: return
        val priv = state.privateRole ?: return
        val me = state.publicPlayers.firstOrNull { it.userId == currentUserId }
        if (priv.role != "SPY" || me?.alive != true) return
        if (!state.isBlackChannelReady) return

        viewModelScope.launch {
            repository.openBlackChannel(room.id).fold(
                onSuccess = {
                    repository.logBattleEvent(
                        room = room,
                        actorUid = currentUserId,
                        actorName = me.displayName,
                        eventType = BattleEventType.ABILITY,
                        summaryAr = "فتح المتجسس (${me.displayName}) [القناة السوداء السرية] لمدة دقيقتين."
                    )
                    _uiState.update {
                        it.copy(bannerMessage = "تم فتح القناة السوداء السرية! متاحة لمدة دقيقتين وبحد أقصى رسالتين.")
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(bannerMessage = err.message ?: "تعذر فتح القناة السوداء") }
                }
            )
        }
    }

    fun sendOnlineBlackChannelMessage(text: String, style: BlackChannelBroadcastStyle) {
        val state = _uiState.value
        val room = state.room ?: return
        val priv = state.privateRole ?: return
        val me = state.publicPlayers.firstOrNull { it.userId == currentUserId }
        if (priv.role != "SPY" || me?.alive != true || !state.isBlackChannelOpen) return

        viewModelScope.launch {
            repository.sendBlackChannelMessage(
                room = room,
                senderRealName = me.displayName,
                text = text,
                style = style
            ).fold(
                onSuccess = { nowClosed ->
                    _uiState.update {
                        it.copy(
                            bannerMessage = if (nowClosed) {
                                "تم بث رسالة القناة السوداء وأُغلقت نهائياً بعد استنفاد الحد الأقصى."
                            } else {
                                "تم بث رسالة القناة السوداء للجميع بصيغة رسمية (متبقٍ رسالة واحدة)."
                            }
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(bannerMessage = err.message ?: "تعذر إرسال رسالة القناة السوداء") }
                }
            )
        }
    }

    private fun checkWiretapDecoyTrap(msgs: List<RoomMessageDoc>) {
        val state = _uiState.value
        val room = state.room ?: return
        val priv = state.privateRole ?: return
        if (room.status != "PLAYING" || priv.role != "SPY") return
        val now = System.currentTimeMillis()
        if (priv.wiretapTargetUid.isBlank() || priv.wiretapExpiresEpochMs <= now) return

        val trappedMsg = msgs.lastOrNull { m ->
            m.channel == "PLAYER" &&
                m.decoyTrap &&
                !m.decoyTriggered &&
                m.senderUid == priv.wiretapTargetUid &&
                m.id !in triggeredDecoyMsgIds
        } ?: return

        triggeredDecoyMsgIds += trappedMsg.id
        val meName = state.publicPlayers.firstOrNull { it.userId == currentUserId }?.displayName ?: "المتجسس"
        viewModelScope.launch {
            repository.markDecoyTriggered(room.id, trappedMsg.id)
            repository.updateRoomFields(
                room.id,
                mapOf(
                    "spyCharges" to (room.spyCharges / 2L),
                    "spyLockoutUntilEpochMs" to (System.currentTimeMillis() + 30_000L)
                )
            )
            repository.logBattleEvent(
                room = room,
                actorUid = currentUserId,
                actorName = meName,
                eventType = BattleEventType.ABILITY,
                summaryAr = "انفجر الفخ الخداعي لـ (${trappedMsg.senderDisplayName}) أثناء تنصّت المتجسس ($meName) وتم تعطيل محطته 30 ثانية!"
            )
            _uiState.update {
                it.copy(bannerMessage = "انفجر الفخ الخداعي أثناء التنصت! توقفت محطة الاختراق 30 ثانية وخسرت نصف شحناتك!")
            }
        }
    }

    private fun computeSharedWiretaps(
        priv: PrivateRoleDoc?,
        spyNet: SpyNetworkDoc?,
        now: Long
    ): List<SharedWiretapUi> {
        if (priv == null || priv.role != "SPY") return emptyList()
        val byTarget = linkedMapOf<String, SharedWiretapUi>()
        if (priv.wiretapTargetUid.isNotBlank() && priv.wiretapExpiresEpochMs > now) {
            val sec = ((priv.wiretapExpiresEpochMs - now) / 1000L).toInt().coerceAtLeast(1)
            byTarget[priv.wiretapTargetUid] = SharedWiretapUi(
                targetUid = priv.wiretapTargetUid,
                targetName = priv.wiretapTargetName,
                secondsLeft = sec,
                isFromAlly = false
            )
        }
        if (spyNet != null && currentUserId in spyNet.spyUids) {
            val entries = listOf(
                Triple(spyNet.spy1Uid, spyNet.spy1TargetUid, spyNet.spy1TargetName to spyNet.spy1ExpiresEpochMs),
                Triple(spyNet.spy2Uid, spyNet.spy2TargetUid, spyNet.spy2TargetName to spyNet.spy2ExpiresEpochMs)
            )
            for ((spyUid, targetUid, nameAndExp) in entries) {
                val (targetName, expMs) = nameAndExp
                if (spyUid.isNotBlank() && targetUid.isNotBlank() && expMs > now) {
                    val isFromAlly = spyUid != currentUserId
                    val sec = ((expMs - now) / 1000L).toInt().coerceAtLeast(1)
                    val existing = byTarget[targetUid]
                    if (existing == null) {
                        byTarget[targetUid] = SharedWiretapUi(
                            targetUid = targetUid,
                            targetName = targetName,
                            secondsLeft = sec,
                            isFromAlly = isFromAlly
                        )
                    } else if (!isFromAlly) {
                        byTarget[targetUid] = existing.copy(
                            secondsLeft = maxOf(existing.secondsLeft, sec),
                            isFromAlly = false
                        )
                    }
                }
            }
        }
        return byTarget.values.toList()
    }

    fun selectWiretapTarget(targetUid: String) {
        val state = _uiState.value
        val room = state.room ?: return
        val priv = state.privateRole ?: return
        val me = state.publicPlayers.firstOrNull { it.userId == currentUserId }
        if (priv.role != "SPY" || me?.alive != true) return
        val targetPlayer = state.publicPlayers.firstOrNull { it.userId == targetUid } ?: return
        val meName = me.displayName
        val now = System.currentTimeMillis()
        val expires = now + 60_000L
        val minuteBucket = (state.secondsRemaining / 60).toLong()
        val hasAllySpy = priv.allySpyUids.isNotEmpty()

        viewModelScope.launch {
            repository.updatePrivateRole(
                room.id,
                mapOf(
                    "wiretapTargetUid" to targetUid,
                    "wiretapTargetName" to targetPlayer.displayName,
                    "wiretapExpiresEpochMs" to expires,
                    "wiretapCycleStartedEpochMs" to now,
                    "wiretapMinute" to minuteBucket
                )
            )
            state.spyNetwork?.let { net ->
                repository.updateSpyNetworkWiretap(
                    roomId = room.id,
                    spyNet = net,
                    spyUid = currentUserId,
                    targetUid = targetUid,
                    targetName = targetPlayer.displayName,
                    expiresEpochMs = expires
                )
            }
            repository.logBattleEvent(
                room = room,
                actorUid = currentUserId,
                actorName = meName,
                eventType = BattleEventType.WIRETAP,
                summaryAr = "فعّل المتجسس ($meName) التنصت الموجّه حصرياً على (${targetPlayer.displayName}) لمدة 60 ثانية."
            )
            val updatedPriv = priv.copy(
                wiretapTargetUid = targetUid,
                wiretapTargetName = targetPlayer.displayName,
                wiretapExpiresEpochMs = expires,
                wiretapCycleStartedEpochMs = now,
                wiretapMinute = minuteBucket
            )
            _uiState.update {
                it.copy(
                    showWiretapPrompt = false,
                    wiretapSecondsLeft = 60,
                    wiretapCycleSecondsLeft = 60,
                    sharedWiretapTargets = computeSharedWiretaps(updatedPriv, it.spyNetwork, now),
                    bannerMessage = if (hasAllySpy) {
                        "تم تفعيل التنصت الموجّه على (${targetPlayer.displayName}) لمدة 60 ثانية — سيقرأ زميلك المتجسس بلاغاته واضحة أيضاً."
                    } else {
                        "تم تفعيل التنصت الموجّه على (${targetPlayer.displayName}) حصرياً لمدة 60 ثانية."
                    }
                )
            }
        }
    }

    fun dismissWiretapPrompt() {
        _uiState.update { it.copy(showWiretapPrompt = false) }
    }

    fun activateOnlineSlotShield(slotIndex: Int) {
        val state = _uiState.value
        val room = state.room ?: return
        val me = state.publicPlayers.firstOrNull { it.userId == currentUserId } ?: return
        val priv = state.privateRole ?: return
        if (!me.alive || me.usedSlotShield || slotIndex.toLong() !in priv.assignedSlots) return

        val oldChar = room.codeSlots.getOrNull(slotIndex) ?: return
        val newChar = CipherEngine.generateSingleCodeChar(excluding = oldChar)
        val updatedCode = room.codeSlots.toMutableList().also { it[slotIndex] = newChar }
        val newRevealedCount = if (slotIndex.toLong() < room.spyRevealedCount) {
            (room.spyRevealedCount - 1L).coerceAtLeast(0L)
        } else {
            room.spyRevealedCount
        }

        viewModelScope.launch {
            repository.updateRoomFields(
                room.id,
                mapOf(
                    "codeSlots" to updatedCode,
                    "spyRevealedCount" to newRevealedCount
                )
            )
            repository.updatePublicPlayer(room.id, currentUserId, mapOf("usedSlotShield" to true))
            repository.postSystemMessage(
                room,
                "SYSTEM_ORG",
                "",
                "درع التردد",
                "تم تفعيل درع تغيير التردد على الخانة رقم ${slotIndex + 1}!",
                "تفعيل درع أمني"
            )
            repository.logBattleEvent(
                room = room,
                actorUid = currentUserId,
                actorName = me.displayName,
                eventType = BattleEventType.ABILITY,
                summaryAr = "فعّل (${me.displayName}) درع تغيير التردد على الخانة رقم ${slotIndex + 1} (من $oldChar إلى $newChar)."
            )
            _uiState.update { it.copy(bannerMessage = "تغيّرت قيمة الخانة رقم ${slotIndex + 1} إلى ($newChar)!") }
        }
    }

    fun useOnlineRareCard(card: RareCardType) {
        val state = _uiState.value
        val room = state.room ?: return
        val priv = state.privateRole ?: return
        val me = state.publicPlayers.firstOrNull { it.userId == currentUserId }
        if (me?.alive != true) return
        if (card.name !in priv.rareCards) return
        if (card == RareCardType.ZERO_DAY_EXPLOIT && room.spyRevealedCount >= 4L) {
            _uiState.update { it.copy(bannerMessage = "جميع الخانات الأربع مكشوفة بالفعل!") }
            return
        }
        val meName = me.displayName

        viewModelScope.launch {
            val newCards = priv.rareCards - card.name
            when (card) {
                RareCardType.SYSTEM_FREEZE -> {
                    repository.updateRoomFields(room.id, mapOf("spyFrozenUntilEpochMs" to (System.currentTimeMillis() + 45_000L)))
                    repository.updatePrivateRole(room.id, mapOf("rareCards" to newCards))
                    repository.logBattleEvent(
                        room = room,
                        actorUid = currentUserId,
                        actorName = meName,
                        eventType = BattleEventType.ABILITY,
                        summaryAr = "استخدم ($meName) بطاقة [تجميد النظام] لتعطيل ألغاز المتجسس لمدة 45 ثانية."
                    )
                    _uiState.update { it.copy(bannerMessage = "تم تجميد ألغاز المتجسس لمدة 45 ثانية!") }
                }
                RareCardType.DEEP_RADAR_SCAN -> {
                    repository.updatePrivateRole(room.id, mapOf("rareCards" to newCards))
                    val inPuzzleText = if (room.spyInPuzzle) "هو داخل لغز نشط الآن!" else "لا يحل لغزاً حالياً."
                    val msg = "مسح الترددات: المتجسس فتح ${room.spyRevealedCount} من 4 خانات، و$inPuzzleText"
                    repository.logBattleEvent(
                        room = room,
                        actorUid = currentUserId,
                        actorName = meName,
                        eventType = BattleEventType.ABILITY,
                        summaryAr = "استخدم ($meName) بطاقة [مسح الترددات العميق]."
                    )
                    _uiState.update { it.copy(radarScanPopup = msg, bannerMessage = msg) }
                }
                RareCardType.ZERO_DAY_EXPLOIT -> {
                    val revealRes = repository.revealNextCodeSlotTransaction(
                        roomId = room.id,
                        extraFields = mapOf("delayedAlertAtEpochMs" to (System.currentTimeMillis() + 30_000L)),
                        setDelayedAlertToNewCount = true
                    )
                    val nextCount = revealRes.getOrElse { (room.spyRevealedCount + 1L).coerceAtMost(4L) }
                    repository.updatePrivateRole(room.id, mapOf("rareCards" to newCards))
                    repository.logBattleEvent(
                        room = room,
                        actorUid = currentUserId,
                        actorName = meName,
                        eventType = BattleEventType.ABILITY,
                        summaryAr = "نفّذ المتجسس ($meName) [الاختراق القسري] وكشف الخانة رقم $nextCount بإنذار متأخر."
                    )
                    _uiState.update { it.copy(bannerMessage = "اختراق قسري ناجح للخانة رقم $nextCount (يتأخر الإنذار 30 ثانية)!") }
                }
                RareCardType.ALARM_JAMMER -> {
                    repository.updateRoomFields(room.id, mapOf("jammerActive" to true))
                    repository.updatePrivateRole(room.id, mapOf("rareCards" to newCards))
                    repository.logBattleEvent(
                        room = room,
                        actorUid = currentUserId,
                        actorName = meName,
                        eventType = BattleEventType.ABILITY,
                        summaryAr = "فعّل المتجسس ($meName) بطاقة [تشويش الإنذار]."
                    )
                    _uiState.update { it.copy(bannerMessage = "تم تفعيل تشويش الإنذار لاختراقك القادم!") }
                }
                RareCardType.DECOY_TRAP, RareCardType.IDENTITY_SPOOF -> Unit
            }
        }
    }

    fun answerOnlineAudit(chosenIndex: Int) {
        val state = _uiState.value
        val room = state.room ?: return
        val me = state.publicPlayers.firstOrNull { it.userId == currentUserId } ?: return
        if (!me.alive || room.auditId.isBlank() || me.auditAnsweredId == room.auditId) return
        if (room.auditSenderUid == currentUserId) return

        val isCorrect = chosenIndex.toLong() == room.auditCorrectIndex
        viewModelScope.launch {
            repository.updatePublicPlayer(room.id, currentUserId, mapOf("auditAnsweredId" to room.auditId))
            repository.sendHeartbeat(room.id)
            if (!isCorrect) {
                if (!room.auditWrongReported) {
                    repository.updateRoomFields(room.id, mapOf("auditWrongReported" to true))
                    val orderedRoster = state.publicPlayers.sortedBy { it.seatIndex }.map { it.userId to it.displayName }
                    val alertText = SmartIdentityHintGenerator.formatWrongAnswerAlert(
                        offenderId = currentUserId,
                        orderedRoster = orderedRoster
                    )
                    repository.postSystemMessage(
                        room,
                        "ALERT",
                        "",
                        "النظام الأمني",
                        alertText,
                        alertText
                    )
                    repository.logBattleEvent(
                        room = room,
                        actorUid = currentUserId,
                        actorName = me.displayName,
                        eventType = BattleEventType.ABILITY,
                        summaryAr = "أخطأ (${me.displayName}) في سؤال النظام التلقائي فأرسل النظام تلميحه الذكي للجميع."
                    )
                }
                _uiState.update { it.copy(bannerMessage = "إجابة خاطئة! تم إرسال تنبيه أمني وتلميح ذكي للجميع.") }
            } else {
                val eligibleOthers = state.publicPlayers.filter {
                    it.alive && it.userId != room.auditSenderUid && it.userId != currentUserId
                }
                val allEligibleAnswered = eligibleOthers.all { it.auditAnsweredId == room.auditId }
                if (allEligibleAnswered && !room.auditWrongReported && !room.auditSuccessReported) {
                    val won = repository.tryCompleteAuditSuccessTransaction(room.id, room.auditId).getOrDefault(false)
                    if (won) {
                        val okText = SmartIdentityHintGenerator.formatSuccessInvestigationAlert()
                        repository.postSystemMessage(
                            room,
                            "ALERT",
                            "",
                            "النظام الأمني",
                            okText,
                            okText
                        )
                        repository.logBattleEvent(
                            room = room,
                            actorUid = currentUserId,
                            actorName = "النظام",
                            eventType = BattleEventType.INVESTIGATION,
                            summaryAr = "أجاب جميع اللاعبين بشكل صحيح على سؤال النظام التلقائي."
                        )
                    }
                }
            }
        }
    }

    fun startOnlineSpyPuzzle() {
        val state = _uiState.value
        val room = state.room ?: return
        if (state.privateRole?.role != "SPY") return
        if (room.spyCharges <= 0L || state.lockoutSecondsLeft > 0 || state.frozenSecondsLeft > 0) return
        val meName = state.publicPlayers.firstOrNull { it.userId == currentUserId }?.displayName ?: "المتجسس"

        // Check if an armed decoy message catches Spy on activation
        val armedDecoy = state.messages.lastOrNull { it.decoyTrap && !it.decoyTriggered }
        if (armedDecoy != null) {
            viewModelScope.launch {
                repository.markDecoyTriggered(room.id, armedDecoy.id)
                repository.updateRoomFields(
                    room.id,
                    mapOf(
                        "spyCharges" to (room.spyCharges / 2L),
                        "spyLockoutUntilEpochMs" to (System.currentTimeMillis() + 30_000L)
                    )
                )
                repository.logBattleEvent(
                    room = room,
                    actorUid = currentUserId,
                    actorName = meName,
                    eventType = BattleEventType.ABILITY,
                    summaryAr = "انفجر الفخ الخداعي لـ (${armedDecoy.senderDisplayName}) بوجه المتجسس ($meName) وعطّل محطته 30 ثانية!"
                )
                _uiState.update { it.copy(bannerMessage = "فخ خداعي! شُحنت محطتك برسالة ملغومة وتوقفت 30 ثانية!") }
            }
            return
        }

        val cipherType = enumValues<CipherType>().firstOrNull { it.name == room.cipherType } ?: CipherType.CAESAR_FORWARD
        val puzzle = PuzzleEngine.generateNextPuzzle(
            lastPuzzleType = state.lastPuzzleType,
            cipherType = cipherType,
            cipherShift = room.cipherShift.toInt(),
            eliminateTwoWrongInCipher = state.eliminateTwoWrongNextCipher,
            usedPuzzleIds = room.usedPuzzleIds.toSet()
        )
        val updatedUsedPuzzleIds = (room.usedPuzzleIds + puzzle.puzzleKey).distinct().take(500)

        viewModelScope.launch {
            repository.updateRoomFields(
                room.id,
                mapOf(
                    "spyCharges" to maxOf(0L, room.spyCharges - 1L),
                    "spyInPuzzle" to true,
                    "usedPuzzleIds" to updatedUsedPuzzleIds
                )
            )
            repository.logBattleEvent(
                room = room,
                actorUid = currentUserId,
                actorName = meName,
                eventType = BattleEventType.PUZZLE_ATTACK,
                summaryAr = "بدأ المتجسس ($meName) هجوم اختراق عبر لغز [${puzzle.type.arabicTitle}]."
            )
        }

        puzzleJob?.cancel()
        _uiState.update {
            it.copy(
                activePuzzle = puzzle,
                puzzleStartMs = System.currentTimeMillis(),
                puzzleSecondsLeft = puzzle.timeLimitSeconds,
                lastPuzzleType = puzzle.type,
                eliminateTwoWrongNextCipher = false
            )
        }

        puzzleJob = viewModelScope.launch {
            while (_uiState.value.puzzleSecondsLeft > 0 && _uiState.value.activePuzzle != null) {
                delay(1000L)
                _uiState.update { it.copy(puzzleSecondsLeft = (it.puzzleSecondsLeft - 1).coerceAtLeast(0)) }
            }
            if (_uiState.value.activePuzzle != null && _uiState.value.puzzleSecondsLeft == 0) {
                submitOnlinePuzzleResult(false)
            }
        }
    }

    fun submitOnlinePuzzleResult(solved: Boolean) {
        puzzleJob?.cancel()
        val state = _uiState.value
        val room = state.room ?: return
        val me = state.publicPlayers.firstOrNull { it.userId == currentUserId }
        val meName = me?.displayName ?: "المتجسس"
        val elapsedMs = (System.currentTimeMillis() - state.puzzleStartMs).coerceAtLeast(500L)

        _uiState.update { it.copy(activePuzzle = null) }

        viewModelScope.launch {
            if (solved) {
                val jammed = room.jammerActive
                val revealRes = repository.revealNextCodeSlotTransaction(
                    roomId = room.id,
                    extraFields = mapOf(
                        "spyInPuzzle" to false,
                        "spyLastHackEpochMs" to System.currentTimeMillis(),
                        "jammerActive" to false
                    )
                )
                val nextCount = revealRes.getOrElse { (room.spyRevealedCount + 1L).coerceAtMost(4L) }
                if (me != null) {
                    val best = if (me.fastestPuzzleMs == 0L) elapsedMs else minOf(me.fastestPuzzleMs, elapsedMs)
                    repository.updatePublicPlayer(
                        room.id,
                        currentUserId,
                        mapOf("puzzlesSolved" to (me.puzzlesSolved + 1L), "fastestPuzzleMs" to best)
                    )
                }
                if (!jammed) {
                    repository.postSystemMessage(
                        room,
                        "ALERT",
                        "",
                        "تحذير أمني",
                        "تحذير أمني: تم اختراق الخانة رقم $nextCount من الكود!",
                        "تحذير أمني"
                    )
                }
                repository.logBattleEvent(
                    room = room,
                    actorUid = currentUserId,
                    actorName = meName,
                    eventType = BattleEventType.PUZZLE_ATTACK,
                    summaryAr = "نجح المتجسس ($meName) في فك اللغز وكشف الخانة رقم $nextCount${if (jammed) " (مع تشويش الإنذار)" else ""}."
                )
                _uiState.update { it.copy(bannerMessage = "تم اختراق الخانة رقم $nextCount!") }
            } else {
                repository.updateRoomFields(
                    room.id,
                    mapOf(
                        "spyInPuzzle" to false,
                        "spyLockoutUntilEpochMs" to (System.currentTimeMillis() + 20_000L)
                    )
                )
                repository.postSystemMessage(
                    room,
                    "ALERT",
                    "",
                    "نظام الدفاع",
                    "محاولة اختراق فاشلة تم صدّها.",
                    "محاولة اختراق فاشلة"
                )
                repository.logBattleEvent(
                    room = room,
                    actorUid = currentUserId,
                    actorName = meName,
                    eventType = BattleEventType.PUZZLE_ATTACK,
                    summaryAr = "فشل المتجسس ($meName) في هجوم الاختراق وتم قفل محطته 20 ثانية."
                )
                _uiState.update { it.copy(bannerMessage = "فشل اللغز! قُفلت المحطة لمدة 20 ثانية.") }
            }
        }
    }

    fun openOnlineFinalCodeEntry() {
        finalCodeJob?.cancel()
        _uiState.update { it.copy(showFinalCodeDialog = true, finalCodeSecondsLeft = 10) }
        finalCodeJob = viewModelScope.launch {
            while (_uiState.value.finalCodeSecondsLeft > 0 && _uiState.value.showFinalCodeDialog) {
                delay(1000L)
                _uiState.update { it.copy(finalCodeSecondsLeft = (it.finalCodeSecondsLeft - 1).coerceAtLeast(0)) }
            }
            if (_uiState.value.showFinalCodeDialog && _uiState.value.finalCodeSecondsLeft == 0) {
                _uiState.update { it.copy(showFinalCodeDialog = false, bannerMessage = "انتهت مهلة الـ 10 ثوانٍ لإدخال الكود!") }
            }
        }
    }

    fun dismissOnlineFinalCodeDialog() {
        finalCodeJob?.cancel()
        _uiState.update { it.copy(showFinalCodeDialog = false) }
    }

    fun submitOnlineFinalCode(entered: List<String>) {
        finalCodeJob?.cancel()
        val room = _uiState.value.room ?: return
        val meName = _uiState.value.publicPlayers.firstOrNull { it.userId == currentUserId }?.displayName ?: "المتجسس"
        val matches = entered.size == 4 && entered.indices.all {
            entered[it].equals(room.codeSlots.getOrElse(it) { "" }, ignoreCase = true)
        }
        _uiState.update { it.copy(showFinalCodeDialog = false) }
        viewModelScope.launch {
            if (matches) {
                repository.logBattleEvent(
                    room = room,
                    actorUid = currentUserId,
                    actorName = meName,
                    eventType = BattleEventType.ROUND_END,
                    summaryAr = "أدخل المتجسس ($meName) الكود السري (${room.codeSlots.joinToString("-")}) بنجاح وأبطل العملية!"
                )
                repository.updateRoomFields(
                    room.id,
                    mapOf(
                        "status" to "FINISHED",
                        "winnerTeam" to "SPY",
                        "winReason" to "SPY_CODE_CRACKED"
                    )
                )
            } else {
                repository.logBattleEvent(
                    room = room,
                    actorUid = currentUserId,
                    actorName = meName,
                    eventType = BattleEventType.PUZZLE_ATTACK,
                    summaryAr = "أدخل المتجسس ($meName) كوداً خاطئاً (${entered.joinToString("-")}) وتم صده 20 ثانية."
                )
                repository.updateRoomFields(
                    room.id,
                    mapOf("spyLockoutUntilEpochMs" to (System.currentTimeMillis() + 20_000L))
                )
                _uiState.update { it.copy(bannerMessage = "الكود غير صحيح!") }
            }
        }
    }

    fun triggerOnlineInvestigation() {
        val state = _uiState.value
        val room = state.room ?: return
        val me = state.publicPlayers.firstOrNull { it.userId == currentUserId } ?: return
        if (me.usedEmergency || !me.alive || room.status != "PLAYING") return

        puzzleJob?.cancel()
        finalCodeJob?.cancel()
        _uiState.update { it.copy(activePuzzle = null, showFinalCodeDialog = false) }

        val now = System.currentTimeMillis()
        viewModelScope.launch {
            repository.updatePublicPlayer(room.id, currentUserId, mapOf("usedEmergency" to true))
            state.publicPlayers.filter { it.alive }.forEach { p ->
                repository.updatePublicPlayer(room.id, p.userId, mapOf("hasVoted" to false, "voteTargetUid" to ""))
            }
            repository.logBattleEvent(
                room = room,
                actorUid = currentUserId,
                actorName = me.displayName,
                eventType = BattleEventType.INVESTIGATION,
                summaryAr = "دعا (${me.displayName}) إلى تحقيق طارئ وإيقاف المؤقت 45 ثانية!"
            )
            repository.updateRoomFields(
                room.id,
                mapOf(
                    "status" to "INVESTIGATION",
                    "pausedAtEpochMs" to now,
                    "investigationTriggerUid" to currentUserId,
                    "investigationEndsEpochMs" to (now + 45_000L)
                )
            )
            repository.postSystemMessage(
                room,
                "ALERT",
                "",
                "تحقيق طارئ",
                "طلب ${me.displayName} تحقيقاً طارئاً! توقف المؤقت لمدة 45 ثانية للنقاش.",
                "تحقيق طارئ"
            )
        }
    }

    fun skipDiscussionToVote() {
        val room = _uiState.value.room ?: return
        if (room.hostId != currentUserId || room.status != "INVESTIGATION") return
        viewModelScope.launch {
            votingStartedAtEpochMs = System.currentTimeMillis()
            repository.updateRoomFields(room.id, mapOf("status" to "VOTING"))
        }
    }

    fun castOnlineVote(targetUidOrSkip: String) {
        val state = _uiState.value
        val room = state.room ?: return
        val me = state.publicPlayers.firstOrNull { it.userId == currentUserId } ?: return
        if (!me.alive || me.hasVoted) return

        val targetName = if (targetUidOrSkip == "SKIP") {
            "تخطي التصويت"
        } else {
            state.publicPlayers.firstOrNull { it.userId == targetUidOrSkip }?.displayName ?: targetUidOrSkip
        }

        viewModelScope.launch {
            repository.updatePublicPlayer(
                room.id,
                currentUserId,
                mapOf(
                    "hasVoted" to true,
                    "voteTargetUid" to targetUidOrSkip,
                    "lastActiveEpochMs" to System.currentTimeMillis()
                )
            )
            repository.logBattleEvent(
                room = room,
                actorUid = currentUserId,
                actorName = me.displayName,
                eventType = BattleEventType.VOTE,
                summaryAr = "صوّت (${me.displayName}) سراً: [$targetName]."
            )
        }
    }

    private suspend fun tallyOnlineVotes(room: OnlineRoomDoc, alivePlayers: List<PublicPlayerDoc>) {
        val counts = mutableMapOf<String, Int>()
        alivePlayers.forEach { p ->
            val v = p.voteTargetUid
            counts[v] = (counts[v] ?: 0) + 1
        }
        val skipVotes = counts["SKIP"] ?: 0
        val playerVotes = counts.filterKeys { it != "SKIP" && it.isNotBlank() }
        val top = playerVotes.maxByOrNull { it.value }
        val hasMajority = top != null &&
            top.value > alivePlayers.size / 2 &&
            top.value > skipVotes &&
            playerVotes.values.count { it == top.value } == 1

        val now = System.currentTimeMillis()
        val extraPause = if (room.pausedAtEpochMs > 0L) (now - room.pausedAtEpochMs) else 0L
        val baseResumeUpdates = mapOf(
            "status" to "PLAYING",
            "pausedAtEpochMs" to 0L,
            "accumulatedPauseMs" to (room.accumulatedPauseMs + extraPause)
        )

        if (!hasMajority || top == null) {
            repository.logBattleEvent(
                room = room,
                actorUid = currentUserId,
                actorName = "النظام",
                eventType = BattleEventType.VOTE,
                summaryAr = "نتيجة التصويت: تعادل أو تخطّي ($skipVotes تخطي) — استمر اللعب دون استبعاد."
            )
            repository.updateRoomFields(room.id, baseResumeUpdates)
            repository.postSystemMessage(
                room,
                "ALERT",
                "",
                "نتيجة التصويت",
                "تعادل أو تخطّي التصويت — تستأنف العملية دون استبعاد.",
                "استئناف العملية"
            )
            return
        }

        val targetUid = top.key
        val targetPlayer = alivePlayers.firstOrNull { it.userId == targetUid } ?: return
        if (targetUid in room.spyUids) {
            val remainingSpies = room.spyUids.count { spyId ->
                spyId != targetUid && alivePlayers.any { it.userId == spyId }
            }
            if (remainingSpies == 0) {
                repository.logBattleEvent(
                    room = room,
                    actorUid = currentUserId,
                    actorName = "النظام",
                    eventType = BattleEventType.ROUND_END,
                    summaryAr = "اعتُقل المتجسس (${targetPlayer.displayName}) بأغلبية الأصوات! انتصرت المنظمة."
                )
                repository.updateRoomFields(
                    room.id,
                    mapOf(
                        "status" to "FINISHED",
                        "winnerTeam" to "ORG",
                        "winReason" to "SPY_ARRESTED"
                    )
                )
            } else {
                repository.logBattleEvent(
                    room = room,
                    actorUid = currentUserId,
                    actorName = "النظام",
                    eventType = BattleEventType.VOTE,
                    summaryAr = "اعتُقل المتجسس (${targetPlayer.displayName})، وما زال المتجسس الثاني حراً."
                )
                repository.updatePublicPlayer(room.id, targetUid, mapOf("alive" to false))
                repository.updateRoomFields(room.id, baseResumeUpdates)
                repository.postSystemMessage(
                    room,
                    "ALERT",
                    "",
                    "اعتقال",
                    "تم اعتقال (${targetPlayer.displayName}) وكان أحد المتجسسين، وما زال المتجسس الثاني حراً!",
                    "اعتقال متجسس"
                )
            }
        } else {
            repository.updatePublicPlayer(room.id, targetUid, mapOf("alive" to false))
            val newRevealed = (room.spyRevealedCount + 1L).coerceAtMost(4L)
            val remainingAlive = alivePlayers.size - 1
            if (remainingAlive <= 2) {
                repository.logBattleEvent(
                    room = room,
                    actorUid = currentUserId,
                    actorName = "النظام",
                    eventType = BattleEventType.ROUND_END,
                    summaryAr = "استُبعد البريء (${targetPlayer.displayName}) وتقلّص عدد الأحياء لاثنين فقط — انتصر المتجسس!"
                )
                repository.updateRoomFields(
                    room.id,
                    mapOf(
                        "spyRevealedCount" to newRevealed,
                        "status" to "FINISHED",
                        "winnerTeam" to "SPY",
                        "winReason" to "TOO_FEW_ORG"
                    )
                )
            } else {
                repository.logBattleEvent(
                    room = room,
                    actorUid = currentUserId,
                    actorName = "النظام",
                    eventType = BattleEventType.VOTE,
                    summaryAr = "استُبعد البريء (${targetPlayer.displayName}) وانكشفت خانة كاملة للمتجسس (رقم $newRevealed)."
                )
                repository.updateRoomFields(
                    room.id,
                    baseResumeUpdates + ("spyRevealedCount" to newRevealed)
                )
                repository.postSystemMessage(
                    room,
                    "ALERT",
                    "",
                    "عقوبة التصويت",
                    "تم استبعاد البريء (${targetPlayer.displayName})! وكعقوبة انكشفت خانة كاملة للمتجسس.",
                    "استبعاد بريء"
                )
            }
        }
    }

    fun openStatistics() {
        _uiState.update { it.copy(showStatisticsScreen = true) }
    }

    fun backToResult() {
        _uiState.update { it.copy(showStatisticsScreen = false) }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(bannerMessage = null, radarScanPopup = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(status = OnlineUiStatus.Idle) }
    }

    override fun onCleared() {
        super.onCleared()
        roomListenersJob?.cancel()
        spyNetworkJob?.cancel()
        battleLogsJob?.cancel()
        tickerJob?.cancel()
        puzzleJob?.cancel()
        finalCodeJob?.cancel()
    }
}
