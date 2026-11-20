package com.example.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class OfflineStage {
    SETUP,
    ROLE_HANDOFF,
    ROLE_REVEAL,
    TURN_HANDOFF,
    PLAYER_TURN,
    PUZZLE_ACTIVE,
    EMERGENCY_DISCUSSION,
    VOTING_HANDOFF,
    VOTING_TURN,
    RESULT,
    STATISTICS
}

data class OfflineUiState(
    val stage: OfflineStage = OfflineStage.SETUP,
    val setupNames: List<String> = listOf("العميل 1", "العميل 2", "العميل 3", "العميل 4"),
    val twoSpiesEnabled: Boolean = false,
    val gameSessionNumber: Int = 1,

    val roundNumber: Int = 1,
    val maxRounds: Int = 10,
    val players: List<PlayerState> = emptyList(),
    val currentPlayerIndex: Int = 0,
    val codeSlots: List<String> = emptyList(),
    val cipherType: CipherType = CipherType.CAESAR_FORWARD,
    val cipherShift: Int = 3,

    // Spy state
    val spyCharges: Int = 0,
    val revealedSlotIndices: Set<Int> = emptySet(),
    val spyLockoutThisTurn: Boolean = false,
    val spyFrozenNextTurn: Boolean = false,
    val spyFrozenThisTurn: Boolean = false,
    val alarmJammerActive: Boolean = false,
    val delayedAlertPendingRound: Int? = null,
    val delayedAlertSlotNumber: Int? = null,
    val wiretapNextRoundBySpyId: Map<String, String> = emptyMap(),
    val wiretapActiveBySpyId: Map<String, String> = emptyMap(),
    val decoyTrapsArmedByPlayerIds: Set<String> = emptySet(),
    val eliminateTwoWrongHintForSpy: Boolean = false,
    val lastPuzzleType: PuzzleType? = null,
    val spySolvedPuzzleRecently: Boolean = false,

    // No-repeat & Secret Battle Log (never shown until RESULT/STATISTICS)
    val usedWords: Set<String> = emptySet(),
    val usedPuzzleIds: Set<String> = emptySet(),
    val usedSpyReportIds: Set<String> = emptySet(),
    val battleLogs: List<BattleLogEntry> = emptyList(),

    // Turn state
    val messageSentThisTurn: Boolean = false,
    val messages: List<GameMessage> = emptyList(),
    // System Audit & Smart Hints
    val activeAudit: SystemAuditPrompt? = null,
    val auditEligiblePlayerIds: Set<String> = emptySet(),
    val auditAnsweredPlayerIds: Set<String> = emptySet(),
    val auditSuccessReported: Boolean = false,
    val bannerNotification: String? = null,
    val radarScanResult: String? = null,

    // Spy-exclusive Secret Black Channel ("القناة السوداء")
    val blackChannelOpenedRound: Int? = null,
    val blackChannelClosesAfterRound: Int? = null,
    val blackChannelUsesCount: Int = 0,
    val blackChannelClosed: Boolean = false,

    // Puzzle state
    val activePuzzle: ActivePuzzle? = null,
    val puzzleStartMs: Long = 0L,

    // Investigation & Voting
    val discussionSecondsLeft: Int = 45,
    val returnStageAfterVote: OfflineStage = OfflineStage.TURN_HANDOFF,
    val votingPlayerQueue: List<String> = emptyList(),
    val currentVoterIndex: Int = 0,
    val votesCast: Map<String, String> = emptyMap(), // voterId -> targetId or "SKIP"
    val lastVoteSummary: String? = null,

    // Final code entry
    val showFinalCodeDialog: Boolean = false,
    val finalCodeSecondsLeft: Int = 10,

    // Outcome
    val winnerTeam: WinTeam? = null,
    val winReason: WinReason? = null
) {
    val currentPlayer: PlayerState?
        get() = players.getOrNull(currentPlayerIndex)

    val allActiveWiretapTargetIds: Set<String>
        get() = wiretapActiveBySpyId.values.toSet()

    val isBlackChannelOpen: Boolean
        get() = !blackChannelClosed &&
            blackChannelOpenedRound != null &&
            blackChannelClosesAfterRound != null &&
            roundNumber <= blackChannelClosesAfterRound &&
            blackChannelUsesCount < BlackChannelConfig.MAX_USES

    val blackChannelRemainingUses: Int
        get() = (BlackChannelConfig.MAX_USES - blackChannelUsesCount).coerceAtLeast(0)

    val blackChannelRoundsRemaining: Int
        get() = if (isBlackChannelOpen && blackChannelClosesAfterRound != null) {
            (blackChannelClosesAfterRound - roundNumber + 1).coerceAtLeast(0)
        } else 0

    fun allySpiesFor(playerId: String): List<PlayerState> {
        val self = players.firstOrNull { it.id == playerId } ?: return emptyList()
        if (self.role != Role.SPY) return emptyList()
        return players.filter { it.role == Role.SPY && it.id != playerId }
    }
}

class OfflineGameViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(OfflineUiState())
    val uiState: StateFlow<OfflineUiState> = _uiState.asStateFlow()

    private var discussionJob: Job? = null
    private var finalCodeJob: Job? = null
    private var roundTurnOrder: List<Int> = emptyList()
    private var turnOrderCursor: Int = 0

    fun setPlayerCount(count: Int) {
        val clamped = count.coerceIn(4, 8)
        _uiState.update { state ->
            val current = state.setupNames.toMutableList()
            while (current.size < clamped) {
                current.add("العميل ${current.size + 1}")
            }
            val trimmed = current.take(clamped)
            state.copy(
                setupNames = trimmed,
                twoSpiesEnabled = if (clamped >= 7) state.twoSpiesEnabled else false
            )
        }
    }

    fun updatePlayerName(index: Int, name: String) {
        _uiState.update { state ->
            val updated = state.setupNames.toMutableList()
            if (index in updated.indices) {
                updated[index] = name.take(20)
            }
            state.copy(setupNames = updated)
        }
    }

    fun toggleTwoSpies(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(twoSpiesEnabled = if (state.setupNames.size >= 7) enabled else false)
        }
    }

    fun startNewOfflineGame() {
        startOfflineMatchInternal(preserveNoRepeatPools = false)
    }

    fun rematchPreservingNoRepeatPools() {
        startOfflineMatchInternal(preserveNoRepeatPools = true)
    }

    private fun startOfflineMatchInternal(preserveNoRepeatPools: Boolean) {
        discussionJob?.cancel()
        finalCodeJob?.cancel()

        val state = _uiState.value
        val names = state.setupNames.mapIndexed { idx, n ->
            n.trim().ifEmpty { "العميل ${idx + 1}" }
        }
        val spyCount = if (names.size >= 7 && state.twoSpiesEnabled) 2 else 1
        val spyIndices = names.indices.shuffled().take(spyCount).toSet()
        val orgIndices = names.indices.filter { it !in spyIndices }

        // Deal 4 code slots round-robin among Organization members
        val slotAssignments = mutableMapOf<Int, MutableList<Int>>()
        for (slotIdx in 0..3) {
            val holderIdx = orgIndices[slotIdx % orgIndices.size]
            slotAssignments.getOrPut(holderIdx) { mutableListOf() }.add(slotIdx)
        }

        val players = names.mapIndexed { idx, name ->
            PlayerState(
                id = "p_$idx",
                name = name,
                role = if (idx in spyIndices) Role.SPY else Role.ORG,
                assignedSlotIndices = slotAssignments[idx]?.toList() ?: emptyList()
            )
        }

        val code = CipherEngine.generateSecretCode()
        val (cipherType, cipherShift) = CipherEngine.pickCipherConfig()

        roundTurnOrder = players.indices.toList()
        turnOrderCursor = 0

        val sessionNum = if (preserveNoRepeatPools) state.gameSessionNumber + 1 else 1
        val spyNames = players.filter { it.role == Role.SPY }.joinToString("، ") { it.name }
        val startLog = BattleLogEntry(
            roundNumber = 1,
            actorName = "النظام",
            eventType = BattleEventType.ROUND_START,
            summary = "بدأت المباراة رقم $sessionNum. المتجسس: $spyNames | التشفير: ${cipherType.arabicName}.",
            timestampLabel = "دورة 1"
        )

        _uiState.value = OfflineUiState(
            stage = OfflineStage.ROLE_HANDOFF,
            setupNames = names,
            twoSpiesEnabled = state.twoSpiesEnabled,
            gameSessionNumber = sessionNum,
            roundNumber = 1,
            players = players,
            currentPlayerIndex = 0,
            codeSlots = code,
            cipherType = cipherType,
            cipherShift = cipherShift,
            usedWords = if (preserveNoRepeatPools) state.usedWords else emptySet(),
            usedPuzzleIds = if (preserveNoRepeatPools) state.usedPuzzleIds else emptySet(),
            usedSpyReportIds = if (preserveNoRepeatPools) state.usedSpyReportIds else emptySet(),
            battleLogs = listOf(startLog)
        )
    }

    fun revealRoleCard() {
        _uiState.update { it.copy(stage = OfflineStage.ROLE_REVEAL) }
    }

    fun confirmRoleSeenAndAdvance() {
        _uiState.update { state ->
            val nextIdx = state.currentPlayerIndex + 1
            if (nextIdx < state.players.size) {
                state.copy(
                    stage = OfflineStage.ROLE_HANDOFF,
                    currentPlayerIndex = nextIdx
                )
            } else {
                turnOrderCursor = 0
                val firstAliveIdx = findNextAliveIndexFromCursor(state.players, 0)
                state.copy(
                    stage = OfflineStage.TURN_HANDOFF,
                    currentPlayerIndex = firstAliveIdx,
                    roundNumber = 1,
                    bannerNotification = "بدأت المهمة! الجولة 1 من ${state.maxRounds}"
                )
            }
        }
    }

    private fun findNextAliveIndexFromCursor(players: List<PlayerState>, startCursor: Int): Int {
        for (offset in players.indices) {
            val idx = (startCursor + offset) % players.size
            if (players[idx].alive) return idx
        }
        return 0
    }

    fun openPlayerTurnScreen() {
        _uiState.update { state ->
            val player = state.currentPlayer ?: return@update state
            val frozenNow = player.role == Role.SPY && state.spyFrozenNextTurn
            state.copy(
                stage = OfflineStage.PLAYER_TURN,
                messageSentThisTurn = false,
                spyLockoutThisTurn = false,
                spyFrozenThisTurn = frozenNow,
                spyFrozenNextTurn = if (frozenNow) false else state.spyFrozenNextTurn,
                radarScanResult = null
            )
        }
    }

    fun sendTemplateMessage(
        template: MessageTemplate,
        selectedWords: List<String>,
        useDecoyTrap: Boolean = false,
        spoofAsPlayerId: String? = null
    ) {
        val state = _uiState.value
        val sender = state.currentPlayer ?: return
        if (state.messageSentThisTurn || !sender.alive) return

        val plain = template.render(selectedWords)
        val ciphered = CipherEngine.encrypt(plain, state.cipherType, state.cipherShift)

        // Strict no-repeat: remove non-agent-name words from future options
        val newlyUsed = mutableSetOf<String>()
        template.slots.forEachIndexed { i, kind ->
            if (kind != SlotKind.AGENT_NAME) {
                selectedWords.getOrNull(i)?.let { newlyUsed += it }
            }
        }

        val canUseDecoy = useDecoyTrap && RareCardType.DECOY_TRAP in sender.rareCards
        val validSpoofId = spoofAsPlayerId?.takeIf { RareCardType.IDENTITY_SPOOF in sender.rareCards }

        val displayedSender = if (validSpoofId != null) {
            state.players.firstOrNull { it.id == validSpoofId } ?: sender
        } else sender

        val msg = GameMessage(
            senderId = displayedSender.id,
            senderName = displayedSender.name,
            trueSenderName = sender.name,
            channel = MessageChannel.PLAYER,
            plainText = plain,
            cipherText = ciphered,
            isDecoyTrap = canUseDecoy,
            roundNumber = state.roundNumber,
            timestampLabel = "جولة ${state.roundNumber}"
        )

        val updatedPlayers = state.players.map { p ->
            if (p.id == sender.id) {
                var cards = p.rareCards
                if (canUseDecoy) cards = cards - RareCardType.DECOY_TRAP
                if (validSpoofId != null) cards = cards - RareCardType.IDENTITY_SPOOF
                p.copy(rareCards = cards)
            } else p
        }

        var newCharges = state.spyCharges + 1
        var banner: String? = "تم إرسال البلاغ (+1 شحنة طاقة لمحطة الاختراق)."
        var decoyTriggered = false
        var finalMsg = msg

        if (canUseDecoy && sender.id in state.allActiveWiretapTargetIds) {
            newCharges = maxOf(0, state.spyCharges / 2)
            decoyTriggered = true
            finalMsg = msg.copy(decoyTriggered = true)
            banner = "انفجر الفخ الخداعي! المتجسس كان يتنصت على ${sender.name} وخسر نصف شحناته!"
        }

        val newArmedSet = if (canUseDecoy && !decoyTriggered) {
            state.decoyTrapsArmedByPlayerIds + sender.id
        } else state.decoyTrapsArmedByPlayerIds

        val logEntry = BattleLogEntry(
            roundNumber = state.roundNumber,
            actorName = sender.name,
            eventType = if (validSpoofId != null || canUseDecoy) BattleEventType.ABILITY else BattleEventType.MESSAGE,
            summary = when {
                validSpoofId != null -> "زوّر (${sender.name}) هوية البلاغ ليظهر باسم (${displayedSender.name}): «$plain»"
                canUseDecoy -> "زرع (${sender.name}) فخاً خداعياً داخل البلاغ: «$plain»"
                else -> "أرسل (${sender.name}) بلاغاً تكتيكياً: «$plain»"
            },
            timestampLabel = "دورة ${state.roundNumber}"
        )

        val autoAudit = TemplatesAndWordBank.buildAuditPromptFromReport(
            template = template,
            selectedWords = selectedWords,
            senderId = sender.id
        )
        val eligibleAuditTargets = if (autoAudit != null) {
            state.players.filter { it.alive && it.id != sender.id }.map { it.id }.toSet()
        } else state.auditEligiblePlayerIds

        _uiState.update {
            it.copy(
                players = updatedPlayers,
                usedWords = it.usedWords + newlyUsed,
                messages = it.messages + finalMsg,
                battleLogs = it.battleLogs + logEntry,
                spyCharges = newCharges,
                messageSentThisTurn = true,
                decoyTrapsArmedByPlayerIds = newArmedSet,
                activeAudit = autoAudit ?: it.activeAudit,
                auditEligiblePlayerIds = if (autoAudit != null) eligibleAuditTargets else it.auditEligiblePlayerIds,
                auditAnsweredPlayerIds = if (autoAudit != null) emptySet() else it.auditAnsweredPlayerIds,
                auditSuccessReported = if (autoAudit != null) false else it.auditSuccessReported,
                bannerNotification = if (autoAudit != null) {
                    "$banner\nطرح النظام سؤال تحقق تلقائياً لبقية اللاعبين حول هذا البلاغ!"
                } else banner
            )
        }
    }

    fun openBlackChannel() {
        _uiState.update { state ->
            val actor = state.currentPlayer ?: return@update state
            if (actor.role != Role.SPY || !actor.alive) return@update state
            if (state.blackChannelClosed || state.blackChannelOpenedRound != null) return@update state

            val closesAfter = minOf(state.maxRounds, state.roundNumber + BlackChannelConfig.OFFLINE_DURATION_ROUNDS - 1)
            val log = BattleLogEntry(
                roundNumber = state.roundNumber,
                actorName = actor.name,
                eventType = BattleEventType.ABILITY,
                summary = "فتح المتجسس (${actor.name}) [القناة السوداء السرية] حتى نهاية الجولة $closesAfter.",
                timestampLabel = "دورة ${state.roundNumber}"
            )
            state.copy(
                blackChannelOpenedRound = state.roundNumber,
                blackChannelClosesAfterRound = closesAfter,
                battleLogs = state.battleLogs + log,
                bannerNotification = "تم تفعيل القناة السوداء السرية (متاحة حتى نهاية الجولة $closesAfter بحد أقصى رسالتين)."
            )
        }
    }

    fun sendBlackChannelMessage(text: String, style: BlackChannelBroadcastStyle) {
        val clean = text.trim().take(220)
        if (clean.isEmpty()) return
        _uiState.update { state ->
            val actor = state.currentPlayer ?: return@update state
            if (actor.role != Role.SPY || !actor.alive || !state.isBlackChannelOpen) return@update state

            val nextUses = state.blackChannelUsesCount + 1
            val nowClosed = nextUses >= BlackChannelConfig.MAX_USES
            val spoofMsg = GameMessage(
                senderId = "SYSTEM_OFFICIAL",
                senderName = style.senderTitle,
                trueSenderName = actor.name,
                channel = MessageChannel.ALERT,
                plainText = clean,
                cipherText = clean,
                roundNumber = state.roundNumber,
                timestampLabel = "إشعار رسمي · جولة ${state.roundNumber}"
            )
            val log = BattleLogEntry(
                roundNumber = state.roundNumber,
                actorName = actor.name,
                eventType = BattleEventType.ABILITY,
                summary = "بث المتجسس (${actor.name}) عبر [القناة السوداء] بصيغة (${style.labelAr}): «$clean»",
                timestampLabel = "دورة ${state.roundNumber}"
            )
            state.copy(
                messages = state.messages + spoofMsg,
                battleLogs = state.battleLogs + log,
                blackChannelUsesCount = nextUses,
                blackChannelClosed = nowClosed,
                bannerNotification = if (nowClosed) {
                    "تم إرسال رسالة القناة السوداء وأُغلقت القناة نهائياً بعد استنفاد الحد الأقصى."
                } else {
                    "تم بث رسالة القناة السوداء بصيغة رسمية للجميع (متبقٍ ${BlackChannelConfig.MAX_USES - nextUses} استخدام)."
                }
            )
        }
    }

    fun selectWiretapTarget(targetPlayerId: String) {
        _uiState.update { state ->
            val actor = state.currentPlayer ?: return@update state
            if (actor.role != Role.SPY || !actor.alive) return@update state
            val actorName = actor.name
            val targetName = state.players.firstOrNull { it.id == targetPlayerId }?.name ?: targetPlayerId
            val hasAllySpy = state.allySpiesFor(actor.id).any { it.alive }
            val log = BattleLogEntry(
                roundNumber = state.roundNumber,
                actorName = actorName,
                eventType = BattleEventType.WIRETAP,
                summary = "وجّه المتجسس ($actorName) جهاز التنصت نحو ($targetName).",
                timestampLabel = "دورة ${state.roundNumber}"
            )
            state.copy(
                wiretapActiveBySpyId = state.wiretapActiveBySpyId + (actor.id to targetPlayerId),
                wiretapNextRoundBySpyId = state.wiretapNextRoundBySpyId + (actor.id to targetPlayerId),
                battleLogs = state.battleLogs + log,
                bannerNotification = if (hasAllySpy) {
                    "تم تفعيل التنصت الموجّه على ($targetName) — سيقرأ زميلك المتجسس رسائله واضحة أيضاً."
                } else {
                    "تم تفعيل التنصت الموجّه على ($targetName) لقراءة بلاغاته واضحة."
                }
            )
        }
    }

    fun activateSlotShield(slotIndex: Int) {
        _uiState.update { state ->
            val player = state.currentPlayer ?: return@update state
            if (!player.slotShieldGranted || player.usedSlotShield) return@update state
            if (slotIndex !in player.assignedSlotIndices) return@update state

            val oldChar = state.codeSlots.getOrNull(slotIndex) ?: return@update state
            val newChar = CipherEngine.generateSingleCodeChar(excluding = oldChar)
            val newCode = state.codeSlots.toMutableList().also { it[slotIndex] = newChar }
            val updatedRevealed = state.revealedSlotIndices - slotIndex

            val updatedPlayers = state.players.map {
                if (it.id == player.id) it.copy(usedSlotShield = true) else it
            }

            val shieldMsg = GameMessage(
                senderId = "SYSTEM",
                senderName = "النظام الأمني",
                channel = MessageChannel.SYSTEM_ORG,
                plainText = "تم تفعيل درع تغيير التردد على الخانة رقم ${slotIndex + 1}.",
                cipherText = "تفعيل درع أمني",
                roundNumber = state.roundNumber,
                timestampLabel = "جولة ${state.roundNumber}"
            )
            val log = BattleLogEntry(
                roundNumber = state.roundNumber,
                actorName = player.name,
                eventType = BattleEventType.ABILITY,
                summary = "فعّل (${player.name}) درع تغيير التردد للخانة رقم ${slotIndex + 1} ($oldChar -> $newChar).",
                timestampLabel = "دورة ${state.roundNumber}"
            )

            state.copy(
                players = updatedPlayers,
                codeSlots = newCode,
                revealedSlotIndices = updatedRevealed,
                messages = state.messages + shieldMsg,
                battleLogs = state.battleLogs + log,
                bannerNotification = "تم تغيير قيمة الخانة رقم ${slotIndex + 1} إلى ($newChar)!"
            )
        }
    }

    fun useRareCard(card: RareCardType) {
        _uiState.update { state ->
            val player = state.currentPlayer ?: return@update state
            if (card !in player.rareCards) return@update state

            when (card) {
                RareCardType.SYSTEM_FREEZE -> {
                    val updated = state.players.map {
                        if (it.id == player.id) it.copy(rareCards = it.rareCards - card) else it
                    }
                    val log = BattleLogEntry(
                        roundNumber = state.roundNumber,
                        actorName = player.name,
                        eventType = BattleEventType.ABILITY,
                        summary = "استخدم (${player.name}) بطاقة [تجميد النظام] على المتجسس.",
                        timestampLabel = "دورة ${state.roundNumber}"
                    )
                    state.copy(
                        players = updated,
                        spyFrozenNextTurn = true,
                        battleLogs = state.battleLogs + log,
                        bannerNotification = "تم تفعيل تجميد النظام! لن يتمكن المتجسس من حل الألغاز في دوره القادم."
                    )
                }
                RareCardType.DEEP_RADAR_SCAN -> {
                    val updated = state.players.map {
                        if (it.id == player.id) it.copy(rareCards = it.rareCards - card) else it
                    }
                    val report = "نتيجة مسح الترددات: المتجسس كشف ${state.revealedSlotIndices.size} من 4 خانات، ولديه ${state.spyCharges} شحنات طاقة."
                    val log = BattleLogEntry(
                        roundNumber = state.roundNumber,
                        actorName = player.name,
                        eventType = BattleEventType.ABILITY,
                        summary = "نفّذ (${player.name}) [مسح الترددات العميق].",
                        timestampLabel = "دورة ${state.roundNumber}"
                    )
                    state.copy(
                        players = updated,
                        radarScanResult = report,
                        battleLogs = state.battleLogs + log,
                        bannerNotification = report
                    )
                }
                RareCardType.ZERO_DAY_EXPLOIT -> {
                    val lockedIdx = (0..3).firstOrNull { it !in state.revealedSlotIndices }
                    if (lockedIdx == null) return@update state
                    val updated = state.players.map {
                        if (it.id == player.id) it.copy(rareCards = it.rareCards - card) else it
                    }
                    val log = BattleLogEntry(
                        roundNumber = state.roundNumber,
                        actorName = player.name,
                        eventType = BattleEventType.ABILITY,
                        summary = "نفّذ المتجسس (${player.name}) [الاختراق القسري] وفتح الخانة رقم ${lockedIdx + 1} سراً.",
                        timestampLabel = "دورة ${state.roundNumber}"
                    )
                    state.copy(
                        players = updated,
                        revealedSlotIndices = state.revealedSlotIndices + lockedIdx,
                        delayedAlertPendingRound = state.roundNumber + 1,
                        delayedAlertSlotNumber = lockedIdx + 1,
                        battleLogs = state.battleLogs + log,
                        bannerNotification = "اختراق قسري ناجح! فُتحت الخانة رقم ${lockedIdx + 1} سراً (يتأخر الإنذار جولة كاملة)."
                    )
                }
                RareCardType.ALARM_JAMMER -> {
                    val updated = state.players.map {
                        if (it.id == player.id) it.copy(rareCards = it.rareCards - card) else it
                    }
                    val log = BattleLogEntry(
                        roundNumber = state.roundNumber,
                        actorName = player.name,
                        eventType = BattleEventType.ABILITY,
                        summary = "فعّل المتجسس (${player.name}) بطاقة [تشويش الإنذار].",
                        timestampLabel = "دورة ${state.roundNumber}"
                    )
                    state.copy(
                        players = updated,
                        alarmJammerActive = true,
                        battleLogs = state.battleLogs + log,
                        bannerNotification = "تشويش الإنذار نشط: اختراقك القادم لن يرسل تحذيراً للمنظمة."
                    )
                }
                RareCardType.DECOY_TRAP, RareCardType.IDENTITY_SPOOF -> state
            }
        }
    }

    fun answerSystemAudit(chosenIndex: Int) {
        _uiState.update { state ->
            val player = state.currentPlayer ?: return@update state
            val audit = state.activeAudit ?: return@update state
            if (player.id == audit.senderId) return@update state
            if (player.id in state.auditAnsweredPlayerIds) return@update state

            val isCorrect = chosenIndex == audit.correctIndex
            val newAnswered = state.auditAnsweredPlayerIds + player.id
            var newMessages = state.messages
            var newLogs = state.battleLogs
            var updatedAudit = audit
            var note = "تم تسجيل إجابتك الصحيحة في سؤال النظام."
            var successReported = state.auditSuccessReported

            if (!isCorrect) {
                updatedAudit = audit.copy(wrongReported = true)
                val alertText = SmartIdentityHintGenerator.formatWrongAnswerAlert(
                    offenderId = player.id,
                    orderedRoster = state.players.map { it.id to it.name }
                )
                newMessages = newMessages + GameMessage(
                    senderId = "SYSTEM",
                    senderName = "النظام الأمني",
                    channel = MessageChannel.ALERT,
                    plainText = alertText,
                    cipherText = alertText,
                    roundNumber = state.roundNumber,
                    timestampLabel = "جولة ${state.roundNumber}"
                )
                newLogs = newLogs + BattleLogEntry(
                    roundNumber = state.roundNumber,
                    actorName = player.name,
                    eventType = BattleEventType.ABILITY,
                    summary = "أخطأ (${player.name}) في سؤال النظام التلقائي فأصدر النظام تلميحه الذكي.",
                    timestampLabel = "دورة ${state.roundNumber}"
                )
                note = "إجابة خاطئة! أرسل النظام تنبيهاً أمنياً وتلميحاً ذكياً للجميع."
            }

            val allEligibleAnswered = state.auditEligiblePlayerIds.isNotEmpty() &&
                state.auditEligiblePlayerIds.all { it in newAnswered }

            var nextAudit: SystemAuditPrompt? = updatedAudit
            if (allEligibleAnswered) {
                if (!updatedAudit.wrongReported && !successReported) {
                    val successText = SmartIdentityHintGenerator.formatSuccessInvestigationAlert()
                    newMessages = newMessages + GameMessage(
                        senderId = "SYSTEM",
                        senderName = "النظام الأمني",
                        channel = MessageChannel.ALERT,
                        plainText = successText,
                        cipherText = successText,
                        roundNumber = state.roundNumber,
                        timestampLabel = "جولة ${state.roundNumber}"
                    )
                    newLogs = newLogs + BattleLogEntry(
                        roundNumber = state.roundNumber,
                        actorName = "النظام",
                        eventType = BattleEventType.INVESTIGATION,
                        summary = "أجاب جميع اللاعبين بشكل صحيح على سؤال النظام التلقائي.",
                        timestampLabel = "دورة ${state.roundNumber}"
                    )
                    successReported = true
                    note = "نجح التحقيق! أجاب الجميع بشكل صحيح."
                }
                nextAudit = null
            }

            state.copy(
                activeAudit = nextAudit,
                auditAnsweredPlayerIds = newAnswered,
                auditSuccessReported = successReported,
                messages = newMessages,
                battleLogs = newLogs,
                bannerNotification = note
            )
        }
    }

    fun startSpyPuzzle() {
        _uiState.update { state ->
            val player = state.currentPlayer ?: return@update state
            if (player.role != Role.SPY || state.spyCharges <= 0) return@update state
            if (state.spyLockoutThisTurn || state.spyFrozenThisTurn) return@update state

            if (state.decoyTrapsArmedByPlayerIds.isNotEmpty()) {
                val halved = state.spyCharges / 2
                val log = BattleLogEntry(
                    roundNumber = state.roundNumber,
                    actorName = player.name,
                    eventType = BattleEventType.ABILITY,
                    summary = "انفجر الفخ الخداعي في وجه المتجسس (${player.name}) عند محاولة الاختراق!",
                    timestampLabel = "دورة ${state.roundNumber}"
                )
                return@update state.copy(
                    spyCharges = halved,
                    spyLockoutThisTurn = true,
                    decoyTrapsArmedByPlayerIds = emptySet(),
                    battleLogs = state.battleLogs + log,
                    bannerNotification = "فخ خداعي! شحنت محطة الاختراق برسالة ملغومة: توقفت المحطة وخسرت نصف شحناتك!"
                )
            }

            val puzzle = PuzzleEngine.generateNextPuzzle(
                lastPuzzleType = state.lastPuzzleType,
                cipherType = state.cipherType,
                cipherShift = state.cipherShift,
                eliminateTwoWrongInCipher = state.eliminateTwoWrongHintForSpy,
                usedPuzzleIds = state.usedPuzzleIds
            )

            val updatedPlayers = state.players.map {
                if (it.id == player.id) it.copy(puzzlesAttempted = it.puzzlesAttempted + 1) else it
            }
            val log = BattleLogEntry(
                roundNumber = state.roundNumber,
                actorName = player.name,
                eventType = BattleEventType.PUZZLE_ATTACK,
                summary = "بدأ المتجسس (${player.name}) هجوم اختراق عبر لغز [${puzzle.type.arabicTitle}].",
                timestampLabel = "دورة ${state.roundNumber}"
            )

            state.copy(
                stage = OfflineStage.PUZZLE_ACTIVE,
                players = updatedPlayers,
                spyCharges = state.spyCharges - 1,
                lastPuzzleType = puzzle.type,
                eliminateTwoWrongHintForSpy = false,
                usedPuzzleIds = state.usedPuzzleIds + puzzle.puzzleKey,
                battleLogs = state.battleLogs + log,
                activePuzzle = puzzle,
                puzzleStartMs = System.currentTimeMillis()
            )
        }
    }

    fun submitPuzzleResult(solved: Boolean) {
        _uiState.update { state ->
            val player = state.currentPlayer ?: return@update state
            val elapsedMs = (System.currentTimeMillis() - state.puzzleStartMs).coerceAtLeast(500L)

            if (solved) {
                val nextLocked = (0..3).firstOrNull { it !in state.revealedSlotIndices }
                val newRevealed = if (nextLocked != null) {
                    state.revealedSlotIndices + nextLocked
                } else state.revealedSlotIndices

                val updatedPlayers = state.players.map {
                    if (it.id == player.id) {
                        val best = if (it.fastestPuzzleMs == 0L) elapsedMs else minOf(it.fastestPuzzleMs, elapsedMs)
                        it.copy(puzzlesSolved = it.puzzlesSolved + 1, fastestPuzzleMs = best)
                    } else it
                }

                val jammed = state.alarmJammerActive
                val newMessages = if (!jammed && nextLocked != null) {
                    state.messages + GameMessage(
                        senderId = "SYSTEM",
                        senderName = "تحذير أمني",
                        channel = MessageChannel.ALERT,
                        plainText = "تحذير أمني: تم اختراق الخانة رقم ${nextLocked + 1} من الكود!",
                        cipherText = "تحذير أمني: اختراق خانة",
                        roundNumber = state.roundNumber,
                        timestampLabel = "جولة ${state.roundNumber}"
                    )
                } else state.messages

                val log = BattleLogEntry(
                    roundNumber = state.roundNumber,
                    actorName = player.name,
                    eventType = BattleEventType.PUZZLE_ATTACK,
                    summary = "نجح المتجسس (${player.name}) وكشف الخانة رقم ${(nextLocked ?: 3) + 1}${if (jammed) " (مع تشويش الإنذار)" else ""}.",
                    timestampLabel = "دورة ${state.roundNumber}"
                )

                state.copy(
                    stage = OfflineStage.PLAYER_TURN,
                    players = updatedPlayers,
                    revealedSlotIndices = newRevealed,
                    alarmJammerActive = false,
                    spySolvedPuzzleRecently = true,
                    activePuzzle = null,
                    messages = newMessages,
                    battleLogs = state.battleLogs + log,
                    bannerNotification = if (nextLocked != null) {
                        "تم فك قفل الخانة رقم ${nextLocked + 1}: (${state.codeSlots[nextLocked]})"
                    } else "جميع الخانات الأربع مفتوحة بالفعل!"
                )
            } else {
                val failMsg = GameMessage(
                    senderId = "SYSTEM",
                    senderName = "نظام الدفاع",
                    channel = MessageChannel.ALERT,
                    plainText = "محاولة اختراق فاشلة تم صدّها.",
                    cipherText = "محاولة اختراق فاشلة",
                    roundNumber = state.roundNumber,
                    timestampLabel = "جولة ${state.roundNumber}"
                )
                val log = BattleLogEntry(
                    roundNumber = state.roundNumber,
                    actorName = player.name,
                    eventType = BattleEventType.PUZZLE_ATTACK,
                    summary = "فشل المتجسس (${player.name}) في هجوم الاختراق وأُغلقت محطته حتى نهاية دوره.",
                    timestampLabel = "دورة ${state.roundNumber}"
                )
                state.copy(
                    stage = OfflineStage.PLAYER_TURN,
                    spyLockoutThisTurn = true,
                    activePuzzle = null,
                    messages = state.messages + failMsg,
                    battleLogs = state.battleLogs + log,
                    bannerNotification = "فشل الاختراق! أُغلقت محطة الاختراق حتى نهاية دورك."
                )
            }
        }
    }

    fun openFinalCodeEntry() {
        finalCodeJob?.cancel()
        _uiState.update { it.copy(showFinalCodeDialog = true, finalCodeSecondsLeft = 10) }
        finalCodeJob = viewModelScope.launch {
            while (_uiState.value.finalCodeSecondsLeft > 0 && _uiState.value.showFinalCodeDialog) {
                delay(1000L)
                _uiState.update { it.copy(finalCodeSecondsLeft = (it.finalCodeSecondsLeft - 1).coerceAtLeast(0)) }
            }
            if (_uiState.value.showFinalCodeDialog && _uiState.value.finalCodeSecondsLeft == 0) {
                _uiState.update {
                    it.copy(
                        showFinalCodeDialog = false,
                        bannerNotification = "انتهت مهلة الـ 10 ثوانٍ لإدخال الكود!"
                    )
                }
            }
        }
    }

    fun dismissFinalCodeDialog() {
        finalCodeJob?.cancel()
        _uiState.update { it.copy(showFinalCodeDialog = false) }
    }

    fun submitFinalCode(entered: List<String>) {
        finalCodeJob?.cancel()
        val state = _uiState.value
        val actorName = state.currentPlayer?.name ?: "المتجسس"
        val matches = entered.size == 4 && entered.indices.all {
            entered[it].equals(state.codeSlots[it], ignoreCase = true)
        }
        if (matches) {
            val log = BattleLogEntry(
                roundNumber = state.roundNumber,
                actorName = actorName,
                eventType = BattleEventType.ROUND_END,
                summary = "أدخل المتجسس ($actorName) الكود السري (${state.codeSlots.joinToString("-")}) وأبطل العملية!",
                timestampLabel = "دورة ${state.roundNumber}"
            )
            _uiState.update {
                it.copy(
                    showFinalCodeDialog = false,
                    stage = OfflineStage.RESULT,
                    winnerTeam = WinTeam.SPY,
                    winReason = WinReason.SPY_CODE_CRACKED,
                    battleLogs = it.battleLogs + log
                )
            }
        } else {
            val log = BattleLogEntry(
                roundNumber = state.roundNumber,
                actorName = actorName,
                eventType = BattleEventType.PUZZLE_ATTACK,
                summary = "أدخل المتجسس ($actorName) كوداً خاطئاً (${entered.joinToString("-")}) وتم إحباط محاولته.",
                timestampLabel = "دورة ${state.roundNumber}"
            )
            _uiState.update {
                it.copy(
                    showFinalCodeDialog = false,
                    spyLockoutThisTurn = true,
                    battleLogs = it.battleLogs + log,
                    bannerNotification = "الكود المدخل غير صحيح! تم إحباط محاولة الإبطال."
                )
            }
        }
    }

    fun endTurn() {
        _uiState.update { state ->
            val currentIdx = state.currentPlayerIndex
            var nextIdx: Int? = null
            for (i in (currentIdx + 1) until state.players.size) {
                if (state.players[i].alive) {
                    nextIdx = i
                    break
                }
            }

            if (nextIdx != null) {
                state.copy(
                    stage = OfflineStage.TURN_HANDOFF,
                    currentPlayerIndex = nextIdx,
                    bannerNotification = null
                )
            } else {
                val nextRound = state.roundNumber + 1
                if (nextRound > state.maxRounds) {
                    val endLog = BattleLogEntry(
                        roundNumber = state.roundNumber,
                        actorName = "النظام",
                        eventType = BattleEventType.ROUND_END,
                        summary = "انتهت الجولات العشر دون كشف الكود! انتصرت المنظمة بالصمود.",
                        timestampLabel = "دورة ${state.roundNumber}"
                    )
                    return@update state.copy(
                        stage = OfflineStage.RESULT,
                        winnerTeam = WinTeam.ORG,
                        winReason = WinReason.TIME_OUT,
                        battleLogs = state.battleLogs + endLog
                    )
                }
                startNewOfflineRound(state, nextRound)
            }
        }
    }

    private fun startNewOfflineRound(state: OfflineUiState, newRound: Int): OfflineUiState {
        val firstAlive = state.players.indexOfFirst { it.alive }.coerceAtLeast(0)
        var players = state.players
        val newMessages = state.messages.toMutableList()

        if (state.delayedAlertPendingRound == newRound && state.delayedAlertSlotNumber != null) {
            newMessages += GameMessage(
                senderId = "SYSTEM",
                senderName = "تحذير متأخر",
                channel = MessageChannel.ALERT,
                plainText = "تحذير أمني: تم اختراق الخانة رقم ${state.delayedAlertSlotNumber} من الكود!",
                cipherText = "تحذير أمني",
                roundNumber = newRound,
                timestampLabel = "جولة $newRound"
            )
        }

        val updatedBattleLogs = state.battleLogs.toMutableList()

        if (newRound == 3 || newRound == 6) {
            val aliveOrg = players.filter { it.alive && it.role == Role.ORG }
            val aliveSpies = players.filter { it.alive && it.role == Role.SPY }
            val orgPool = listOf(RareCardType.DECOY_TRAP, RareCardType.SYSTEM_FREEZE, RareCardType.DEEP_RADAR_SCAN)
            val spyPool = listOf(RareCardType.ZERO_DAY_EXPLOIT, RareCardType.IDENTITY_SPOOF, RareCardType.ALARM_JAMMER)

            val chosenOrg = aliveOrg.randomOrNull()
            val chosenSpy = aliveSpies.randomOrNull()
            val orgCard = chosenOrg?.let { holder ->
                orgPool.filter { it !in holder.rareCards }.ifEmpty { orgPool }.random()
            }
            val spyCard = chosenSpy?.let { holder ->
                spyPool.filter { it !in holder.rareCards }.ifEmpty { spyPool }.random()
            }

            players = players.map { p ->
                when (p.id) {
                    chosenOrg?.id -> if (orgCard != null) p.copy(rareCards = p.rareCards + orgCard) else p
                    chosenSpy?.id -> if (spyCard != null) p.copy(rareCards = p.rareCards + spyCard) else p
                    else -> p
                }
            }
            if (chosenOrg != null && orgCard != null) {
                updatedBattleLogs += BattleLogEntry(
                    roundNumber = newRound,
                    actorName = "النظام",
                    eventType = BattleEventType.ABILITY,
                    summary = "منح النظام بطاقة نادرة [${orgCard.arabicName}] للعميل (${chosenOrg.name}).",
                    timestampLabel = "دورة $newRound"
                )
            }
            if (chosenSpy != null && spyCard != null) {
                updatedBattleLogs += BattleLogEntry(
                    roundNumber = newRound,
                    actorName = "النظام",
                    eventType = BattleEventType.ABILITY,
                    summary = "منح النظام بطاقة نادرة [${spyCard.arabicName}] للمتجسس (${chosenSpy.name}).",
                    timestampLabel = "دورة $newRound"
                )
            }
            newMessages += GameMessage(
                senderId = "SYSTEM",
                senderName = "توزيع تكتيكي",
                channel = MessageChannel.ALERT,
                plainText = "تم توزيع بطاقات نادرة في هذه المحطة (الجولة $newRound)! تفقد صف بطاقاتك.",
                cipherText = "توزيع بطاقات نادرة",
                roundNumber = newRound,
                timestampLabel = "جولة $newRound"
            )
        }

        // Non-repeating Spy Intelligence Report + Cipher Hint
        val (reportId, reportText) = TemplatesAndWordBank.pickNextSpyReport(state.usedSpyReportIds)
        val giveEliminateHint = Random.nextInt(3) == 0
        val spyHintText = if (giveEliminateHint) {
            "$reportText\nتلميح التشفير: تم تفعيل ميزة حذف خيارين خاطئين للغز الشفرة القادم."
        } else {
            val hint = CipherEngine.generateSpyHint(
                state.cipherType,
                state.cipherShift,
                state.codeSlots,
                state.revealedSlotIndices.size
            )
            "$reportText\n$hint"
        }
        newMessages += GameMessage(
            senderId = "SYSTEM",
            senderName = "مساعد الاختراق",
            channel = MessageChannel.SYSTEM_SPY,
            plainText = spyHintText,
            cipherText = spyHintText,
            roundNumber = newRound,
            timestampLabel = "جولة $newRound"
        )

        var newAudit: SystemAuditPrompt? = null
        val assistRoll = Random.nextInt(3)
        when (assistRoll) {
            0 -> {
                if (state.spySolvedPuzzleRecently) {
                    val recipient = players.filter { it.alive && it.role == Role.ORG }.randomOrNull()
                    newMessages += GameMessage(
                        senderId = "SYSTEM",
                        senderName = "كاشف النشاط",
                        channel = MessageChannel.SYSTEM_ORG,
                        targetPlayerId = recipient?.id,
                        plainText = "كاشف النشاط: أحد اللاعبين تفاعل مع وحدة فك التشفير وحل لغزاً في الجولة الماضية!",
                        cipherText = "نشاط مشفر",
                        roundNumber = newRound,
                        timestampLabel = "جولة $newRound"
                    )
                }
            }
            1 -> {
                val eligibleHolder = players.firstOrNull {
                    it.alive && it.role == Role.ORG && it.assignedSlotIndices.isNotEmpty() && !it.slotShieldGranted && !it.usedSlotShield
                }
                if (eligibleHolder != null) {
                    players = players.map {
                        if (it.id == eligibleHolder.id) it.copy(slotShieldGranted = true) else it
                    }
                    newMessages += GameMessage(
                        senderId = "SYSTEM",
                        senderName = "درع التردد",
                        channel = MessageChannel.SYSTEM_ORG,
                        targetPlayerId = eligibleHolder.id,
                        plainText = "منح النظام العميل (${eligibleHolder.name}) درع تغيير التردد لمرة واحدة!",
                        cipherText = "درع أمني",
                        roundNumber = newRound,
                        timestampLabel = "جولة $newRound"
                    )
                }
            }
            else -> {
                if (state.activeAudit == null) {
                    newAudit = TemplatesAndWordBank.buildAuditPrompt(newMessages)?.copy(senderId = "")
                }
            }
        }

        val aliveSpyIds = players.filter { it.alive && it.role == Role.SPY }.map { it.id }.toSet()
        val carriedWiretaps = state.wiretapNextRoundBySpyId.filterKeys { it in aliveSpyIds }

        val blackChannelExpired = state.blackChannelOpenedRound != null &&
            state.blackChannelClosesAfterRound != null &&
            newRound > state.blackChannelClosesAfterRound
        val nextBlackChannelClosed = state.blackChannelClosed || blackChannelExpired

        val finalAudit = newAudit ?: state.activeAudit
        val finalEligible = when {
            newAudit != null -> players.filter { it.alive && it.id != newAudit.senderId }.map { it.id }.toSet()
            finalAudit != null -> state.auditEligiblePlayerIds
            else -> emptySet()
        }
        val finalAnswered = if (newAudit != null) emptySet() else state.auditAnsweredPlayerIds
        val finalSuccess = if (newAudit != null) false else state.auditSuccessReported

        return state.copy(
            stage = OfflineStage.TURN_HANDOFF,
            roundNumber = newRound,
            currentPlayerIndex = firstAlive,
            players = players,
            messages = newMessages,
            battleLogs = updatedBattleLogs,
            usedSpyReportIds = state.usedSpyReportIds + reportId,
            wiretapActiveBySpyId = carriedWiretaps,
            wiretapNextRoundBySpyId = emptyMap(),
            spySolvedPuzzleRecently = false,
            eliminateTwoWrongHintForSpy = giveEliminateHint,
            delayedAlertPendingRound = null,
            delayedAlertSlotNumber = null,
            activeAudit = finalAudit,
            auditEligiblePlayerIds = finalEligible,
            auditAnsweredPlayerIds = finalAnswered,
            auditSuccessReported = finalSuccess,
            blackChannelClosed = nextBlackChannelClosed,
            bannerNotification = "بدأت الجولة $newRound من ${state.maxRounds}"
        )
    }

    fun triggerEmergencyInvestigation() {
        val state = _uiState.value
        val caller = state.currentPlayer ?: return
        if (caller.usedEmergency || !caller.alive) return

        discussionJob?.cancel()
        finalCodeJob?.cancel()

        val updatedPlayers = state.players.map {
            if (it.id == caller.id) it.copy(usedEmergency = true) else it
        }
        val log = BattleLogEntry(
            roundNumber = state.roundNumber,
            actorName = caller.name,
            eventType = BattleEventType.INVESTIGATION,
            summary = "دعا (${caller.name}) إلى تحقيق طارئ ومواجهة وجاهية!",
            timestampLabel = "دورة ${state.roundNumber}"
        )

        _uiState.value = state.copy(
            stage = OfflineStage.EMERGENCY_DISCUSSION,
            players = updatedPlayers,
            battleLogs = state.battleLogs + log,
            showFinalCodeDialog = false,
            discussionSecondsLeft = 45,
            bannerNotification = "طلب ${caller.name} تحقيقاً طارئاً! نقاش وجاهي لمدة 45 ثانية."
        )

        discussionJob = viewModelScope.launch {
            while (_uiState.value.discussionSecondsLeft > 0 && _uiState.value.stage == OfflineStage.EMERGENCY_DISCUSSION) {
                delay(1000L)
                _uiState.update { it.copy(discussionSecondsLeft = (it.discussionSecondsLeft - 1).coerceAtLeast(0)) }
            }
            if (_uiState.value.stage == OfflineStage.EMERGENCY_DISCUSSION) {
                beginSecretVoting()
            }
        }
    }

    fun beginSecretVoting() {
        discussionJob?.cancel()
        _uiState.update { state ->
            val aliveIds = state.players.filter { it.alive }.map { it.id }
            state.copy(
                stage = OfflineStage.VOTING_HANDOFF,
                votingPlayerQueue = aliveIds,
                currentVoterIndex = 0,
                votesCast = emptyMap(),
                lastVoteSummary = null
            )
        }
    }

    fun openVoterBallot() {
        _uiState.update { it.copy(stage = OfflineStage.VOTING_TURN) }
    }

    fun castSecretVote(targetIdOrSkip: String) {
        _uiState.update { state ->
            val voterId = state.votingPlayerQueue.getOrNull(state.currentVoterIndex) ?: return@update state
            val voterName = state.players.firstOrNull { it.id == voterId }?.name ?: voterId
            val targetName = if (targetIdOrSkip == "SKIP") {
                "تخطي التصويت"
            } else {
                state.players.firstOrNull { it.id == targetIdOrSkip }?.name ?: targetIdOrSkip
            }
            val voteLog = BattleLogEntry(
                roundNumber = state.roundNumber,
                actorName = voterName,
                eventType = BattleEventType.VOTE,
                summary = "صوّت ($voterName) سراً: [$targetName].",
                timestampLabel = "دورة ${state.roundNumber}"
            )

            val newVotes = state.votesCast + (voterId to targetIdOrSkip)
            val nextVoterIdx = state.currentVoterIndex + 1

            if (nextVoterIdx < state.votingPlayerQueue.size) {
                state.copy(
                    stage = OfflineStage.VOTING_HANDOFF,
                    votesCast = newVotes,
                    battleLogs = state.battleLogs + voteLog,
                    currentVoterIndex = nextVoterIdx
                )
            } else {
                tallyVotesAndResolve(state.copy(votesCast = newVotes, battleLogs = state.battleLogs + voteLog))
            }
        }
    }

    private fun tallyVotesAndResolve(state: OfflineUiState): OfflineUiState {
        val activeVoters = state.votingPlayerQueue.size
        val counts = mutableMapOf<String, Int>()
        state.votesCast.values.forEach { target ->
            counts[target] = (counts[target] ?: 0) + 1
        }
        val skipVotes = counts["SKIP"] ?: 0
        val playerVoteCounts = counts.filterKeys { it != "SKIP" }
        val topEntry = playerVoteCounts.maxByOrNull { it.value }
        val hasMajority = topEntry != null &&
            topEntry.value > activeVoters / 2 &&
            topEntry.value > skipVotes &&
            playerVoteCounts.values.count { it == topEntry.value } == 1

        if (!hasMajority || topEntry == null) {
            val resumeMsg = GameMessage(
                senderId = "SYSTEM",
                senderName = "نتيجة التصويت",
                channel = MessageChannel.ALERT,
                plainText = "انتهى التصويت بتخطٍّ أو تعادل — تستأنف العملية دون استبعاد.",
                cipherText = "انتهى التصويت دون استبعاد",
                roundNumber = state.roundNumber,
                timestampLabel = "جولة ${state.roundNumber}"
            )
            val log = BattleLogEntry(
                roundNumber = state.roundNumber,
                actorName = "النظام",
                eventType = BattleEventType.VOTE,
                summary = "نتيجة التصويت: تعادل أو تخطّي — لم يُستبعد أحد.",
                timestampLabel = "دورة ${state.roundNumber}"
            )
            return state.copy(
                stage = OfflineStage.TURN_HANDOFF,
                messages = state.messages + resumeMsg,
                battleLogs = state.battleLogs + log,
                bannerNotification = "نتيجة التصويت: تعادل أو تخطٍّ — تستأنف اللعبة."
            )
        }

        val targetPlayer = state.players.firstOrNull { it.id == topEntry.key } ?: return state
        if (targetPlayer.role == Role.SPY) {
            val otherAliveSpy = state.players.any { it.id != targetPlayer.id && it.alive && it.role == Role.SPY }
            if (!otherAliveSpy) {
                val endLog = BattleLogEntry(
                    roundNumber = state.roundNumber,
                    actorName = "النظام",
                    eventType = BattleEventType.ROUND_END,
                    summary = "تم اعتقال المتجسس (${targetPlayer.name}) بأغلبية الأصوات! انتصرت المنظمة.",
                    timestampLabel = "دورة ${state.roundNumber}"
                )
                return state.copy(
                    stage = OfflineStage.RESULT,
                    winnerTeam = WinTeam.ORG,
                    winReason = WinReason.SPY_ARRESTED,
                    battleLogs = state.battleLogs + endLog
                )
            } else {
                val updated = state.players.map {
                    if (it.id == targetPlayer.id) it.copy(alive = false) else it
                }
                val msg = GameMessage(
                    senderId = "SYSTEM",
                    senderName = "اعتقال متجسس",
                    channel = MessageChannel.ALERT,
                    plainText = "تم اعتقال (${targetPlayer.name}) وتبيّن أنه أحد المتجسسين، لكن ما زال هناك متجسس ثانٍ طليق!",
                    cipherText = "اعتقال أحد المتجسسين",
                    roundNumber = state.roundNumber,
                    timestampLabel = "جولة ${state.roundNumber}"
                )
                val log = BattleLogEntry(
                    roundNumber = state.roundNumber,
                    actorName = "النظام",
                    eventType = BattleEventType.VOTE,
                    summary = "اعتُقل المتجسس (${targetPlayer.name})، وبقي المتجسس الثاني حراً.",
                    timestampLabel = "دورة ${state.roundNumber}"
                )
                return state.copy(
                    stage = OfflineStage.TURN_HANDOFF,
                    players = updated,
                    wiretapActiveBySpyId = state.wiretapActiveBySpyId - targetPlayer.id,
                    wiretapNextRoundBySpyId = state.wiretapNextRoundBySpyId - targetPlayer.id,
                    messages = state.messages + msg,
                    battleLogs = state.battleLogs + log,
                    bannerNotification = msg.plainText
                )
            }
        }

        val updatedPlayers = state.players.map {
            if (it.id == targetPlayer.id) it.copy(alive = false) else it
        }
        val lockedIdx = (0..3).firstOrNull { it !in state.revealedSlotIndices }
        val newRevealed = if (lockedIdx != null) state.revealedSlotIndices + lockedIdx else state.revealedSlotIndices
        val aliveRemaining = updatedPlayers.count { it.alive }

        if (aliveRemaining <= 2) {
            val endLog = BattleLogEntry(
                roundNumber = state.roundNumber,
                actorName = "النظام",
                eventType = BattleEventType.ROUND_END,
                summary = "استُبعد البريء (${targetPlayer.name}) وتقلّص الأحياء لاثنين فقط — انتصر المتجسس!",
                timestampLabel = "دورة ${state.roundNumber}"
            )
            return state.copy(
                players = updatedPlayers,
                revealedSlotIndices = newRevealed,
                stage = OfflineStage.RESULT,
                winnerTeam = WinTeam.SPY,
                winReason = WinReason.TOO_FEW_ORG,
                battleLogs = state.battleLogs + endLog
            )
        }

        val penaltyMsg = GameMessage(
            senderId = "SYSTEM",
            senderName = "عقوبة التصويت الخاطئ",
            channel = MessageChannel.ALERT,
            plainText = "تم استبعاد البريء (${targetPlayer.name})! وكعقوبة انكشفت خانة كاملة للمتجسس.",
            cipherText = "استبعاد بريء وانكشاف خانة",
            roundNumber = state.roundNumber,
            timestampLabel = "جولة ${state.roundNumber}"
        )
        val voteLog = BattleLogEntry(
            roundNumber = state.roundNumber,
            actorName = "النظام",
            eventType = BattleEventType.VOTE,
            summary = "استُبعد البريء (${targetPlayer.name}) وكعقوبة انكشفت خانة رقم ${(lockedIdx ?: 3) + 1} للمتجسس.",
            timestampLabel = "دورة ${state.roundNumber}"
        )

        val currentAlive = updatedPlayers.getOrNull(state.currentPlayerIndex)?.alive == true
        val nextIdx = if (currentAlive) state.currentPlayerIndex else findNextAliveIndexFromCursor(updatedPlayers, state.currentPlayerIndex)

        return state.copy(
            stage = OfflineStage.TURN_HANDOFF,
            players = updatedPlayers,
            currentPlayerIndex = nextIdx,
            revealedSlotIndices = newRevealed,
            messages = state.messages + penaltyMsg,
            battleLogs = state.battleLogs + voteLog,
            bannerNotification = penaltyMsg.plainText
        )
    }

    fun openStatisticsScreen() {
        _uiState.update { it.copy(stage = OfflineStage.STATISTICS) }
    }

    fun backToResultScreen() {
        _uiState.update { it.copy(stage = OfflineStage.RESULT) }
    }

    fun resetToSetup() {
        discussionJob?.cancel()
        finalCodeJob?.cancel()
        _uiState.update {
            OfflineUiState(
                setupNames = it.setupNames,
                twoSpiesEnabled = it.twoSpiesEnabled
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        discussionJob?.cancel()
        finalCodeJob?.cancel()
    }
}
