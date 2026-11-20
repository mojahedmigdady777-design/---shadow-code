package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.*
import com.example.offline.OfflineGameViewModel
import com.example.offline.OfflineStage
import com.example.offline.OfflineUiState
import com.example.ui.components.BlackChannelComposerDialog
import com.example.ui.components.BlackChannelSpySection
import com.example.ui.components.PlayerSecretRoleCardDialog
import com.example.ui.components.RareCardHandRow
import com.example.ui.components.RareCardInspectDialog
import com.example.ui.components.RosterPlayerUi
import com.example.ui.components.SecretCodeSlotsBar
import com.example.ui.components.SlotShieldSelectorSection
import com.example.ui.components.TacticalBackground
import com.example.ui.components.TacticalCardThumb
import com.example.ui.components.TacticalPlayerRosterStrip
import com.example.ui.theme.*

@Composable
fun OfflineModeHost(
    viewModel: OfflineGameViewModel,
    feedback: TacticalFeedback,
    onExitToHome: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showQuitConfirm by remember { mutableStateOf(false) }

    // Sound cue when an intrusion alert arrives
    LaunchedEffect(state.messages.size) {
        val last = state.messages.lastOrNull()
        if (last?.channel == MessageChannel.ALERT) {
            feedback.playAlertChime()
        }
    }

    if (state.stage != OfflineStage.SETUP && state.stage != OfflineStage.RESULT && state.stage != OfflineStage.STATISTICS) {
        BackHandler { showQuitConfirm = true }
    }

    when (state.stage) {
        OfflineStage.SETUP -> {
            OfflineSetupScreen(
                state = state,
                onSetPlayerCount = viewModel::setPlayerCount,
                onUpdatePlayerName = viewModel::updatePlayerName,
                onToggleTwoSpies = viewModel::toggleTwoSpies,
                onStartGame = viewModel::startNewOfflineGame,
                onBack = onExitToHome
            )
        }
        OfflineStage.ROLE_HANDOFF -> {
            OfflineRoleRevealScreen(
                state = state,
                isRevealed = false,
                onReveal = viewModel::revealRoleCard,
                onConfirmAndPass = viewModel::confirmRoleSeenAndAdvance
            )
        }
        OfflineStage.ROLE_REVEAL -> {
            OfflineRoleRevealScreen(
                state = state,
                isRevealed = true,
                onReveal = viewModel::revealRoleCard,
                onConfirmAndPass = viewModel::confirmRoleSeenAndAdvance
            )
        }
        OfflineStage.TURN_HANDOFF -> {
            TurnHandoffShieldScreen(
                playerName = state.currentPlayer?.name ?: "",
                roundNumber = state.roundNumber,
                maxRounds = state.maxRounds,
                banner = state.bannerNotification,
                onOpenTurn = viewModel::openPlayerTurnScreen
            )
        }
        OfflineStage.PLAYER_TURN -> {
            OfflinePlayerTurnScreen(
                state = state,
                viewModel = viewModel,
                feedback = feedback
            )
        }
        OfflineStage.PUZZLE_ACTIVE -> {
            val puzzle = state.activePuzzle
            if (puzzle != null) {
                PuzzleHostScreen(
                    puzzle = puzzle,
                    secondsLeft = null, // Turn-based offline mode
                    onPuzzleCompleted = { solved ->
                        if (solved) feedback.playSuccessChime() else feedback.playFailBuzz()
                        viewModel.submitPuzzleResult(solved)
                    }
                )
            }
        }
        OfflineStage.EMERGENCY_DISCUSSION -> {
            EmergencyDiscussionScreen(
                secondsLeft = state.discussionSecondsLeft,
                banner = state.bannerNotification,
                canAdvance = true,
                onProceedToVote = viewModel::beginSecretVoting
            )
        }
        OfflineStage.VOTING_HANDOFF -> {
            val voterId = state.votingPlayerQueue.getOrNull(state.currentVoterIndex)
            val voterName = state.players.firstOrNull { it.id == voterId }?.name ?: ""
            TurnHandoffShieldScreen(
                playerName = voterName,
                roundNumber = state.roundNumber,
                maxRounds = state.maxRounds,
                banner = "تصويت سري — مرّر الجهاز إلى ($voterName) للإدلاء بصوته.",
                buttonLabel = "فتح ورقة التصويت السرية",
                onOpenTurn = viewModel::openVoterBallot
            )
        }
        OfflineStage.VOTING_TURN -> {
            val voterId = state.votingPlayerQueue.getOrNull(state.currentVoterIndex)
            val voterName = state.players.firstOrNull { it.id == voterId }?.name ?: ""
            SecretVotingScreen(
                voterName = voterName,
                candidates = state.players.filter { it.alive && it.id != voterId },
                onVote = viewModel::castSecretVote
            )
        }
        OfflineStage.RESULT -> {
            GameResultScreen(
                winnerTeam = state.winnerTeam ?: WinTeam.ORG,
                winReason = state.winReason ?: WinReason.TIME_OUT,
                codeSlots = state.codeSlots,
                onOpenStatistics = viewModel::openStatisticsScreen,
                onPlayAgain = viewModel::rematchPreservingNoRepeatPools,
                onGoHome = {
                    viewModel.resetToSetup()
                    onExitToHome()
                },
                playAgainLabel = "جولة جديدة (بدون تكرار)"
            )
        }
        OfflineStage.STATISTICS -> {
            MatchStatisticsScreen(
                players = state.players,
                codeSlots = state.codeSlots,
                cipherType = state.cipherType,
                cipherShift = state.cipherShift,
                messages = state.messages,
                battleLogs = state.battleLogs,
                onBack = viewModel::backToResultScreen
            )
        }
    }

    if (showQuitConfirm) {
        AlertDialog(
            onDismissRequest = { showQuitConfirm = false },
            containerColor = PanelSurface,
            title = { Text("إنهاء الجولة الحالية؟", color = TextPrimary) },
            text = { Text("سيتم إلغاء التقدم الحالي والعودة للشاشة الرئيسية.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showQuitConfirm = false
                        viewModel.resetToSetup()
                        onExitToHome()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpyCrimson, contentColor = TextPrimary)
                ) {
                    Text("إنهاء والعودة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuitConfirm = false }) {
                    Text("استمرار اللعب", color = ElectricCyan)
                }
            }
        )
    }
}

@Composable
fun TurnHandoffShieldScreen(
    playerName: String,
    roundNumber: Int,
    maxRounds: Int,
    banner: String?,
    buttonLabel: String = "كشف شاشتي وبدء الدور",
    onOpenTurn: () -> Unit
) {
    TacticalBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
                .widthIn(max = 520.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_card_back),
                contentDescription = "شاشة الحماية",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, ElectricCyan, RoundedCornerShape(20.dp))
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text("الجولة $roundNumber من $maxRounds", style = MaterialTheme.typography.labelLarge, color = AmberAlert)
            Spacer(modifier = Modifier.height(6.dp))
            Text("مرّر الجهاز سراً إلى:", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
            Text(playerName, style = MaterialTheme.typography.displayLarge, color = ElectricCyan, textAlign = TextAlign.Center)

            if (!banner.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = PanelSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
                ) {
                    Text(
                        text = banner,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onOpenTurn,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightOps),
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("open_turn_button")
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(buttonLabel, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun OfflinePlayerTurnScreen(
    state: OfflineUiState,
    viewModel: OfflineGameViewModel,
    feedback: TacticalFeedback
) {
    val player = state.currentPlayer ?: return
    val isSpy = player.role == Role.SPY
    val allySpies = remember(state.players, player.id, isSpy) {
        if (isSpy) state.allySpiesFor(player.id) else emptyList()
    }
    val allySpyIds = remember(allySpies) { allySpies.map { it.id }.toSet() }
    val wiretapCandidates = remember(state.players, player.id, allySpyIds) {
        state.players
            .filter { it.alive && it.id != player.id && it.id !in allySpyIds }
            .ifEmpty { state.players.filter { it.alive && it.id != player.id } }
    }
    var showComposer by remember { mutableStateOf(false) }
    var preArmDecoy by remember { mutableStateOf(false) }
    var preArmSpoof by remember { mutableStateOf(false) }
    var inspectedRareCard by remember { mutableStateOf<RareCardType?>(null) }
    var showWiretapSheet by remember { mutableStateOf(false) }
    var showRoleCardDialog by remember { mutableStateOf(false) }
    var showBlackChannelDialog by remember { mutableStateOf(false) }

    TacticalBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(14.dp)
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top bar: Player Role Badge + Round + Role Card Viewer + Emergency Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${player.name} (${if (isSpy) "المتجسس" else "المنظمة"})",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isSpy) SpyCrimson else OrgBlue,
                        maxLines = 1
                    )
                    Text(
                        text = "الجولة ${state.roundNumber} / ${state.maxRounds}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showRoleCardDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PanelSurface)
                            .border(1.dp, ElectricCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .testTag("offline_view_role_card_button")
                    ) {
                        Icon(Icons.Default.Badge, contentDescription = "بطاقة دوري السرية", tint = ElectricCyan)
                    }

                    Button(
                        onClick = viewModel::triggerEmergencyInvestigation,
                        enabled = !player.usedEmergency,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberAlert,
                            contentColor = MidnightOps
                        ),
                        modifier = Modifier.height(48.dp).testTag("emergency_investigation_button")
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (player.usedEmergency) "استُنفد" else "تحقيق طارئ")
                    }
                }
            }

            // Code Slots Bar
            SecretCodeSlotsBar(
                codeSlots = state.codeSlots,
                revealedIndices = state.revealedSlotIndices,
                isSpy = isSpy,
                assignedIndices = player.assignedSlotIndices
            )

            // Player Cards Strip (hides secret roles from Org; reveals ally spy ONLY to Spies)
            TacticalPlayerRosterStrip(
                players = state.players.mapIndexed { idx, p ->
                    RosterPlayerUi(
                        id = p.id,
                        name = p.name,
                        seatNumber = idx + 1,
                        alive = p.alive,
                        isCurrentTurnOrMe = (p.id == player.id),
                        usedEmergency = p.usedEmergency,
                        isAllySpy = (p.id in allySpyIds)
                    )
                }
            )

            // Banner notification
            state.bannerNotification?.let { msg ->
                Surface(
                    color = Color(0xFF10243E),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.fillMaxWidth().padding(10.dp)
                    )
                }
            }

            // System Audit prompt if active and unanswered by this player (sender excluded)
            val audit = state.activeAudit
            if (audit != null && player.id != audit.senderId && player.id !in state.auditAnsweredPlayerIds) {
                Surface(
                    color = Color(0xFF2B1C08),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AmberAlert),
                    modifier = Modifier.testTag("offline_audit_card")
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("سؤال النظام التلقائي:", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                        Text(audit.question, style = MaterialTheme.typography.titleMedium, color = AmberAlert)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            audit.options.forEachIndexed { idx, opt ->
                                OutlinedButton(
                                    onClick = { viewModel.answerSystemAudit(idx) },
                                    modifier = Modifier.weight(1f).testTag("audit_opt_$idx")
                                ) {
                                    Text(opt, style = MaterialTheme.typography.labelSmall, color = TextPrimary, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            } else if (audit != null && player.id == audit.senderId && state.auditEligiblePlayerIds.any { it !in state.auditAnsweredPlayerIds }) {
                Surface(
                    color = Color(0xFF1B1910),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberAlert.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "طرح النظام سؤال تحقق تلقائياً على بقية اللاعبين حول بلاغك الأخير.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AmberAlert,
                        modifier = Modifier.fillMaxWidth().padding(10.dp)
                    )
                }
            }

            // Spy Hacking Station OR Org Slot Shield
            if (isSpy) {
                Surface(
                    color = Color(0xFF230B14),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, SpyCrimson)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TacticalCardThumb(
                                    drawableRes = R.drawable.img_card_puzzle,
                                    contentDescription = "بطاقة محطة الاختراق",
                                    borderColor = SpyCrimson
                                )
                                Text("محطة الاختراق · الشحنات: ${state.spyCharges}", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            }
                            TextButton(
                                onClick = { showWiretapSheet = true },
                                modifier = Modifier.testTag("open_wiretap_button")
                            ) {
                                Icon(Icons.Default.Hearing, contentDescription = null, tint = AmberAlert, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("التنصت الموجّه", color = AmberAlert)
                            }
                        }

                        if (state.revealedSlotIndices.size >= 4) {
                            Button(
                                onClick = viewModel::openFinalCodeEntry,
                                colors = ButtonDefaults.buttonColors(containerColor = SpyCrimson, contentColor = TextPrimary),
                                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("spy_final_code_button")
                            ) {
                                Text("إدخال الكود وإبطال العملية!", style = MaterialTheme.typography.titleMedium)
                            }
                        } else {
                            val canHack = state.spyCharges > 0 && !state.spyLockoutThisTurn && !state.spyFrozenThisTurn
                            val hackText = when {
                                state.spyFrozenThisTurn -> "النظام مجمّد هذا الدور!"
                                state.spyLockoutThisTurn -> "المحطة مغلقة حتى نهاية الدور"
                                state.spyCharges == 0 -> "لا توجد شحنات طاقة (تزداد مع كل بلاغ)"
                                else -> "بدء الاختراق (-1 شحنة)"
                            }
                            Button(
                                onClick = viewModel::startSpyPuzzle,
                                enabled = canHack,
                                colors = ButtonDefaults.buttonColors(containerColor = SpyCrimson, contentColor = TextPrimary),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("start_puzzle_button")
                            ) {
                                Text(hackText, style = MaterialTheme.typography.titleMedium)
                            }
                        }

                        if (allySpies.isNotEmpty()) {
                            Surface(
                                color = Color(0xFF330F1B),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SpyCrimson.copy(alpha = 0.65f)),
                                modifier = Modifier.fillMaxWidth().testTag("offline_ally_spy_banner")
                            ) {
                                Text(
                                    text = "زميلك المتجسس: ${allySpies.joinToString("، ") { "${it.name}${if (!it.alive) " (مستبعد)" else ""}" }} · الخانات المكشوفة مشتركة (${state.revealedSlotIndices.size}/4)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Inline Targeted Eavesdropping interactive selector + exclusive shared decrypted feed
                        val activeTargetIds = state.allActiveWiretapTargetIds
                        val myTargetId = state.wiretapActiveBySpyId[player.id]
                        val activeTargetNamesSummary = state.wiretapActiveBySpyId.entries.mapNotNull { (spyId, targetId) ->
                            val targetName = state.players.firstOrNull { it.id == targetId }?.name ?: return@mapNotNull null
                            if (spyId == player.id) targetName else "$targetName (عبر زميلك)"
                        }.distinct()
                        Text(
                            text = if (activeTargetNamesSummary.isNotEmpty()) {
                                "التنصت الموجّه النشط على: ${activeTargetNamesSummary.joinToString("، ")} (مشترك للمتجسسين)"
                            } else {
                                "اختر لاعباً للتنصت الموجّه وكشف بلاغاته:"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = AmberAlert
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            wiretapCandidates.forEach { target ->
                                val selectedByAnySpy = target.id in activeTargetIds
                                val selectedByAllyOnly = selectedByAnySpy && target.id != myTargetId
                                FilterChip(
                                    selected = selectedByAnySpy,
                                    onClick = { viewModel.selectWiretapTarget(target.id) },
                                    label = {
                                        Text(
                                            text = if (selectedByAllyOnly) "${target.name} (زميلك)" else target.name,
                                            maxLines = 1,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.testTag("offline_inline_wiretap_${target.id}")
                                )
                            }
                        }
                        if (activeTargetIds.isNotEmpty()) {
                            val intercepted = state.messages
                                .filter { it.channel == MessageChannel.PLAYER && it.senderId in activeTargetIds }
                                .takeLast(4)
                            Surface(
                                color = Color(0xFF1E1406),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AmberAlert.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "بلاغات (${activeTargetNamesSummary.joinToString("، ")}) المفكوكة تشفيرها:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AmberAlert
                                    )
                                    if (intercepted.isEmpty()) {
                                        Text(
                                            text = "لا توجد رسائل مسجلة لهذه الأهداف حتى الآن.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    } else {
                                        intercepted.forEach { m ->
                                            Text(
                                                text = "• ${m.senderName}: ${m.plainText}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Spy-exclusive Black Channel ("القناة السوداء") — strictly hidden from Org
                        val isBlackReady = !state.blackChannelClosed && state.blackChannelOpenedRound == null
                        val isBlackClosed = state.blackChannelClosed ||
                            state.blackChannelUsesCount >= BlackChannelConfig.MAX_USES ||
                            (state.blackChannelClosesAfterRound != null && state.roundNumber > state.blackChannelClosesAfterRound)
                        val blackWindowText = if (state.isBlackChannelOpen) {
                            "متبقٍ ${state.blackChannelRoundsRemaining} جولة"
                        } else {
                            "جولتان"
                        }
                        BlackChannelSpySection(
                            isReadyToOpen = isBlackReady && !isBlackClosed,
                            isOpen = state.isBlackChannelOpen,
                            isPermanentlyClosed = isBlackClosed,
                            usesRemaining = state.blackChannelRemainingUses,
                            windowLabel = blackWindowText,
                            testTagPrefix = "offline_black_channel",
                            onOpenChannel = viewModel::openBlackChannel,
                            onOpenComposer = { showBlackChannelDialog = true }
                        )
                    }
                }
            } else if (player.slotShieldGranted && !player.usedSlotShield && player.assignedSlotIndices.isNotEmpty()) {
                SlotShieldSelectorSection(
                    assignedSlots = player.assignedSlotIndices,
                    testTagPrefix = "activate_slot_shield_button",
                    onActivateShield = viewModel::activateSlotShield
                )
            }

            // Rare Cards Row ("بطاقاتك") with card artwork & inspect preview
            RareCardHandRow(
                cards = player.rareCards,
                testTagPrefix = "rare_card_",
                onCardClick = { card -> inspectedRareCard = card }
            )

            // Dual-Channel Messages Feed
            Text("سجل البلاغات والنظام:", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val visibleMessages = state.messages.filter { msg ->
                    when (msg.channel) {
                        MessageChannel.SYSTEM_SPY -> isSpy
                        MessageChannel.SYSTEM_ORG -> !isSpy && (msg.targetPlayerId == null || msg.targetPlayerId == player.id)
                        else -> true
                    }
                }.reversed()

                items(visibleMessages, key = { it.id }) { msg ->
                    val shouldDecrypt = !isSpy ||
                        msg.channel != MessageChannel.PLAYER ||
                        msg.senderId in state.allActiveWiretapTargetIds ||
                        msg.trueSenderName == player.name
                    val body = if (shouldDecrypt) msg.plainText else msg.cipherText

                    val cardColor = when (msg.channel) {
                        MessageChannel.ALERT -> Color(0xFF2F0D18)
                        MessageChannel.SYSTEM_ORG -> Color(0xFF0B253A)
                        MessageChannel.SYSTEM_SPY -> Color(0xFF291436)
                        MessageChannel.PLAYER -> PanelSurface
                    }

                    Surface(
                        color = cardColor,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = msg.senderName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (shouldDecrypt) ElectricCyan else SpyCrimson
                                )
                                Text(
                                    text = if (shouldDecrypt) msg.timestampLabel else "مشفّر · ${msg.timestampLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(body, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        }
                    }
                }
            }

            // Bottom Action Bar: Send Message (max 1/turn) + End Turn
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        preArmDecoy = false
                        preArmSpoof = false
                        showComposer = true
                    },
                    enabled = !state.messageSentThisTurn,
                    colors = ButtonDefaults.buttonColors(containerColor = OrgBlue, contentColor = TextPrimary),
                    modifier = Modifier.weight(1f).height(52.dp).testTag("open_composer_button")
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (state.messageSentThisTurn) "تم الإرسال" else "إرسال بلاغ")
                }

                Button(
                    onClick = {
                        feedback.tapHaptic()
                        viewModel.endTurn()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightOps),
                    modifier = Modifier.weight(1f).height(52.dp).testTag("end_turn_button")
                ) {
                    Text("إنهاء الدور")
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    inspectedRareCard?.let { card ->
        val otherAlive = state.players.any { it.alive && it.id != player.id }
        val (canActivate, reason) = when (card) {
            RareCardType.DECOY_TRAP -> (!state.messageSentThisTurn) to
                "أرسلت بلاغك في هذا الدور بالفعل — يمكنك إرفاق الفخ في الدور القادم."
            RareCardType.IDENTITY_SPOOF -> (!state.messageSentThisTurn && otherAlive) to
                if (state.messageSentThisTurn) "أرسلت بلاغك في هذا الدور بالفعل — استخدمها مع بلاغ الدور القادم." else "لا يوجد لاعب آخر لتزوير هويته."
            RareCardType.ZERO_DAY_EXPLOIT -> (state.revealedSlotIndices.size < 4) to
                "جميع الخانات الأربع مكشوفة بالفعل!"
            else -> true to null
        }
        RareCardInspectDialog(
            card = card,
            canActivate = canActivate,
            disabledReason = if (canActivate) null else reason,
            onConfirmActivate = {
                inspectedRareCard = null
                if (card == RareCardType.DECOY_TRAP) {
                    preArmDecoy = true
                    preArmSpoof = false
                    showComposer = true
                } else if (card == RareCardType.IDENTITY_SPOOF) {
                    preArmDecoy = false
                    preArmSpoof = true
                    showComposer = true
                } else {
                    viewModel.useRareCard(card)
                }
            },
            onDismiss = { inspectedRareCard = null }
        )
    }

    if (showRoleCardDialog) {
        PlayerSecretRoleCardDialog(
            playerName = player.name,
            role = player.role,
            codeSlots = state.codeSlots,
            assignedSlotIndices = player.assignedSlotIndices,
            cipherType = state.cipherType,
            revealedIndices = if (isSpy) state.revealedSlotIndices else emptySet(),
            allySpyNames = allySpies.map { it.name },
            initialFlipped = true,
            onDismiss = { showRoleCardDialog = false }
        )
    }

    if (showComposer) {
        MessageComposerDialog(
            players = state.players,
            currentSenderId = player.id,
            usedWords = state.usedWords,
            hasDecoyTrapCard = RareCardType.DECOY_TRAP in player.rareCards,
            hasSpoofCard = RareCardType.IDENTITY_SPOOF in player.rareCards,
            initialUseDecoyTrap = preArmDecoy,
            initialUseSpoof = preArmSpoof,
            onDismiss = { showComposer = false },
            onSend = { tmpl, words, decoy, spoofId ->
                showComposer = false
                preArmDecoy = false
                preArmSpoof = false
                viewModel.sendTemplateMessage(tmpl, words, decoy, spoofId)
            }
        )
    }

    if (showWiretapSheet) {
        WiretapSelectionDialog(
            players = wiretapCandidates,
            currentTargetId = state.wiretapNextRoundBySpyId[player.id] ?: state.wiretapActiveBySpyId[player.id],
            onSelect = { targetId ->
                showWiretapSheet = false
                viewModel.selectWiretapTarget(targetId)
            },
            onDismiss = { showWiretapSheet = false }
        )
    }

    if (state.showFinalCodeDialog) {
        FinalCodeEntryDialog(
            secondsLeft = state.finalCodeSecondsLeft,
            onDismiss = viewModel::dismissFinalCodeDialog,
            onSubmitCode = viewModel::submitFinalCode
        )
    }

    if (isSpy && showBlackChannelDialog && state.isBlackChannelOpen) {
        BlackChannelComposerDialog(
            usesRemaining = state.blackChannelRemainingUses,
            windowLabel = "متبقٍ ${state.blackChannelRoundsRemaining} جولة",
            onDismiss = { showBlackChannelDialog = false },
            onSend = { text, style ->
                showBlackChannelDialog = false
                viewModel.sendBlackChannelMessage(text, style)
            }
        )
    }
}

@Composable
fun WiretapSelectionDialog(
    players: List<PlayerState>,
    currentTargetId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TacticalCardThumb(
                    drawableRes = R.drawable.img_card_wiretap,
                    contentDescription = "بطاقة التنصت الموجّه",
                    borderColor = AmberAlert
                )
                Text("التنصت الموجّه", color = AmberAlert)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("اختر عميلاً لقراءة رسائله واضحة بدون تشفير في الجولة/الدقيقة القادمة:", color = TextSecondary)
                players.forEachIndexed { idx, p ->
                    val selected = p.id == currentTargetId
                    Button(
                        onClick = { onSelect(p.id) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) AmberAlert else ElevatedCard,
                            contentColor = if (selected) MidnightOps else TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("wiretap_target_${p.id}")
                    ) {
                        Text("${idx + 1}. ${p.name}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق", color = TextSecondary) }
        }
    )
}

@Composable
fun EmergencyDiscussionScreen(
    secondsLeft: Int,
    banner: String?,
    canAdvance: Boolean,
    onProceedToVote: () -> Unit
) {
    TacticalBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(22.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_investigation),
                contentDescription = "التحقيق الطارئ",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, AmberAlert, RoundedCornerShape(20.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("تحقيق طارئ — مواجهة وجاهية", style = MaterialTheme.typography.headlineMedium, color = AmberAlert)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "00:%02d".format(secondsLeft),
                style = MaterialTheme.typography.displayLarge,
                fontFamily = JetBrainsMonoFontFamily,
                color = TextPrimary
            )
            if (!banner.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(banner, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center)
            }
            Spacer(modifier = Modifier.height(20.dp))
            if (canAdvance) {
                Button(
                    onClick = onProceedToVote,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightOps),
                    modifier = Modifier.fillMaxWidth().height(54.dp).testTag("proceed_to_vote_button")
                ) {
                    Text("الانتقال إلى التصويت السري الآن", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun SecretVotingScreen(
    voterName: String,
    candidates: List<PlayerState>,
    onVote: (String) -> Unit
) {
    TacticalBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("التصويت السري", style = MaterialTheme.typography.headlineMedium, color = AmberAlert)
            Text("الناخب: $voterName — اختر المشتبه به أو تخطَّ التصويت:", style = MaterialTheme.typography.bodyLarge, color = TextPrimary, textAlign = TextAlign.Center)

            candidates.forEachIndexed { idx, candidate ->
                Surface(
                    color = ElevatedCard,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onVote(candidate.id) }
                        .testTag("vote_candidate_${candidate.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(SpyCrimson.copy(alpha = 0.2f))
                                    .border(1.dp, SpyCrimson.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = SpyCrimson
                                )
                            }
                            Text("اتهام ${candidate.name}", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        }
                        Icon(Icons.Default.Gavel, contentDescription = null, tint = AmberAlert)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = { onVote("SKIP") },
                border = androidx.compose.foundation.BorderStroke(1.5.dp, TextSecondary),
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("vote_skip_button")
            ) {
                Text("تخطي التصويت (Skip Vote)", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
            }
        }
    }
}
