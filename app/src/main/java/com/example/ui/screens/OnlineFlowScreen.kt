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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.core.*
import com.example.online.*
import com.example.ui.components.BlackChannelComposerDialog
import com.example.ui.components.BlackChannelSpySection
import com.example.ui.components.NonGameplayOwnerFooter
import com.example.ui.components.PlayerSecretRoleCardDialog
import com.example.ui.components.RareCardHandRow
import com.example.ui.components.RareCardInspectDialog
import com.example.ui.components.ReconnectingBanner
import com.example.ui.components.RosterPlayerUi
import com.example.ui.components.SecretCodeSlotsBar
import com.example.ui.components.SlotShieldSelectorSection
import com.example.ui.components.TacticalBackground
import com.example.ui.components.TacticalCardThumb
import com.example.ui.components.TacticalPlayerRosterStrip
import com.example.ui.components.formatTimer
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseUser

@Composable
fun OnlineModeHost(
    feedback: TacticalFeedback,
    onSwitchToOffline: () -> Unit,
    onExitToHome: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by OnlineAuthManager.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = OnlineAuthManager.currentUser())
    var autoSignInChecked by remember { mutableStateOf(currentUser != null) }
    var authError by remember { mutableStateOf<String?>(null) }
    var isConnected by remember { mutableStateOf(ConnectivityHelper.hasInternetConnection(context)) }

    LaunchedEffect(Unit) {
        ConnectivityHelper.observeConnectivity(context).collect { connected ->
            isConnected = connected
        }
    }

    LaunchedEffect(Unit) {
        if (currentUser == null && isConnected) {
            OnlineAuthManager.attemptAutoSignIn(
                context = context,
                scope = scope,
                onAuthSuccess = { autoSignInChecked = true },
                onUnauthenticated = { autoSignInChecked = true }
            )
        } else {
            autoSignInChecked = true
        }
    }

    if (!isConnected && currentUser == null) {
        BackHandler(onBack = onExitToHome)
        OnlineOfflineFallbackScreen(
            onPlayOffline = onSwitchToOffline,
            onBack = onExitToHome
        )
        return
    }

    val user: FirebaseUser? = currentUser
    if (user == null) {
        BackHandler(onBack = onExitToHome)
        OnlineAuthScreen(
            isLoadingAuto = !autoSignInChecked,
            errorMessage = authError,
            onSignInClick = {
                authError = null
                OnlineAuthManager.onGoogleSignInClicked(
                    context = context,
                    scope = scope,
                    onAuthSuccess = { authError = null },
                    onAuthError = { err -> authError = err },
                    onAuthCancelled = { authError = null }
                )
            },
            onBack = onExitToHome
        )
        return
    }

    key(user.uid) {
        val appContext = context.applicationContext
        val onlineViewModel: OnlineGameViewModel = viewModel(
            key = "online_${user.uid}",
            factory = viewModelFactory {
                initializer {
                    OnlineGameViewModel(
                        repository = OnlineRoomRepository(appContext),
                        currentUserId = user.uid,
                        appContext = appContext
                    )
                }
            }
        )

        LaunchedEffect(user.uid) {
            onlineViewModel.ensureProfile(user.displayName ?: "عميل ميداني")
        }

        val state by onlineViewModel.uiState.collectAsStateWithLifecycle()
        var showDesertConfirm by remember { mutableStateOf(false) }

        LaunchedEffect(state.messages.size) {
            val last = state.messages.lastOrNull()
            if (last?.channel == "ALERT") {
                feedback.playAlertChime()
            }
        }

        val room = state.room
        if (state.activeRoomId == null || room == null) {
            BackHandler(onBack = onExitToHome)
            OnlineLobbyScreen(
                userName = user.displayName ?: "عميل ميداني",
                currentUserId = user.uid,
                state = state,
                onCreateRoom = { alias, rName, isPub, maxP, dur ->
                    onlineViewModel.createRoom(alias, rName, isPub, maxP, dur)
                },
                onJoinRoom = { code, alias -> onlineViewModel.joinRoom(code, alias) },
                onJoinPublicRoom = { roomId, alias -> onlineViewModel.joinPublicRoom(roomId, alias) },
                onSelectRoom = { roomId -> onlineViewModel.attachToRoom(roomId) },
                onSignOut = {
                    OnlineAuthManager.signOut(context, scope) {
                        onExitToHome()
                    }
                },
                onBack = onExitToHome,
                onDismissError = onlineViewModel::clearError
            )
        } else {
            val isLiveMatch = room.status in listOf("PLAYING", "INVESTIGATION", "VOTING")
            BackHandler {
                if (isLiveMatch) {
                    showDesertConfirm = true
                } else {
                    onlineViewModel.leaveOrCloseRoom()
                }
            }

            when (room.status) {
                "WAITING" -> {
                    OnlineWaitingRoomScreen(
                        room = room,
                        currentUserId = user.uid,
                        isConnected = state.isConnected,
                        status = state.status,
                        onToggleReady = onlineViewModel::toggleReady,
                        onKickPlayer = onlineViewModel::kickWaitingPlayer,
                        onUpdateConfig = onlineViewModel::updateWaitingRoomConfig,
                        onToggleTwoSpies = onlineViewModel::toggleTwoSpies,
                        onStartMatch = onlineViewModel::startMatch,
                        onLeaveRoom = onlineViewModel::leaveOrCloseRoom,
                        onDismissError = onlineViewModel::clearError
                    )
                }
                "PLAYING" -> {
                    val activePuzzle = state.activePuzzle
                    if (activePuzzle != null) {
                        PuzzleHostScreen(
                            puzzle = activePuzzle,
                            secondsLeft = state.puzzleSecondsLeft,
                            onPuzzleCompleted = { solved ->
                                if (solved) feedback.playSuccessChime() else feedback.playFailBuzz()
                                onlineViewModel.submitOnlinePuzzleResult(solved)
                            }
                        )
                    } else {
                        OnlineLiveMatchScreen(
                            room = room,
                            state = state,
                            currentUserId = user.uid,
                            viewModel = onlineViewModel,
                            feedback = feedback,
                            onLeaveRoom = { showDesertConfirm = true }
                        )
                    }
                }
                "INVESTIGATION" -> {
                    EmergencyDiscussionScreen(
                        secondsLeft = state.discussionSecondsLeft,
                        banner = state.bannerMessage ?: "توقف المؤقت لمدة 45 ثانية للمناقشة الوجاهية.",
                        canAdvance = (room.hostId == user.uid),
                        onProceedToVote = onlineViewModel::skipDiscussionToVote
                    )
                }
                "VOTING" -> {
                    val me = state.publicPlayers.firstOrNull { it.userId == user.uid }
                    if (me == null || !me.alive || me.hasVoted) {
                        val aliveList = state.publicPlayers.filter { it.alive }
                        val votedCount = aliveList.count { it.hasVoted }
                        OnlineVoteWaitingScreen(
                            isAlive = me?.alive ?: false,
                            votedCount = votedCount,
                            totalAlive = aliveList.size,
                            isConnected = state.isConnected
                        )
                    } else {
                        val candidates = state.publicPlayers
                            .filter { it.alive && it.userId != user.uid }
                            .map { p ->
                                PlayerState(
                                    id = p.userId,
                                    name = p.displayName,
                                    role = Role.ORG
                                )
                            }
                        SecretVotingScreen(
                            voterName = me.displayName,
                            candidates = candidates,
                            onVote = { target ->
                                feedback.tapHaptic()
                                onlineViewModel.castOnlineVote(target)
                            }
                        )
                    }
                }
                "FINISHED" -> {
                    val winner = if (room.winnerTeam == "SPY") WinTeam.SPY else WinTeam.ORG
                    val reason = enumValues<WinReason>().firstOrNull { it.name == room.winReason }
                        ?: WinReason.TIME_OUT
                    val cipherType = enumValues<CipherType>().firstOrNull { it.name == room.cipherType }
                        ?: CipherType.CAESAR_FORWARD

                    if (state.showStatisticsScreen) {
                        val mappedPlayers = state.publicPlayers.map { p ->
                            PlayerState(
                                id = p.userId,
                                name = p.displayName,
                                role = if (p.userId in room.spyUids) Role.SPY else Role.ORG,
                                alive = p.alive,
                                puzzlesSolved = p.puzzlesSolved.toInt(),
                                fastestPuzzleMs = p.fastestPuzzleMs
                            )
                        }
                        val mappedMessages = state.messages.map { m ->
                            val trueSenderName = if (m.spoofedByUid.isNotBlank()) {
                                state.publicPlayers.firstOrNull { it.userId == m.spoofedByUid }?.displayName
                                    ?: m.senderDisplayName
                            } else {
                                m.senderDisplayName
                            }
                            GameMessage(
                                id = m.id,
                                senderId = m.senderUid,
                                senderName = m.senderDisplayName,
                                trueSenderName = trueSenderName,
                                channel = enumValues<MessageChannel>().firstOrNull { it.name == m.channel }
                                    ?: MessageChannel.PLAYER,
                                targetPlayerId = m.targetUid.takeIf { it.isNotBlank() },
                                plainText = m.plainText,
                                cipherText = m.cipherText,
                                isDecoyTrap = m.decoyTrap,
                                decoyTriggered = m.decoyTriggered
                            )
                        }
                        val mappedBattleLogs = state.battleLogs.map { log ->
                            BattleLogEntry(
                                id = log.id,
                                roundNumber = log.roundNumber.toInt(),
                                actorName = log.actorName,
                                eventType = enumValues<BattleEventType>().firstOrNull { it.name == log.eventType }
                                    ?: BattleEventType.MESSAGE,
                                summary = log.summaryAr,
                                timestampLabel = "جولة ${log.roundNumber}"
                            )
                        }
                        MatchStatisticsScreen(
                            players = mappedPlayers,
                            codeSlots = room.codeSlots,
                            cipherType = cipherType,
                            cipherShift = room.cipherShift.toInt(),
                            messages = mappedMessages,
                            battleLogs = mappedBattleLogs,
                            onBack = onlineViewModel::backToResult
                        )
                    } else {
                        val isHost = room.hostId == user.uid
                        GameResultScreen(
                            winnerTeam = winner,
                            winReason = reason,
                            codeSlots = room.codeSlots,
                            onOpenStatistics = onlineViewModel::openStatistics,
                            onPlayAgain = {
                                if (isHost) {
                                    onlineViewModel.prepareRematch()
                                } else {
                                    onlineViewModel.leaveOrCloseRoom()
                                }
                            },
                            onGoHome = {
                                onlineViewModel.leaveOrCloseRoom()
                                onExitToHome()
                            },
                            playAgainLabel = if (isHost) "جولة جديدة (بدون تكرار)" else "مغادرة الغرفة"
                        )
                    }
                }
                else -> {
                    OnlineWaitingRoomScreen(
                        room = room,
                        currentUserId = user.uid,
                        isConnected = state.isConnected,
                        status = state.status,
                        onToggleReady = onlineViewModel::toggleReady,
                        onKickPlayer = onlineViewModel::kickWaitingPlayer,
                        onUpdateConfig = onlineViewModel::updateWaitingRoomConfig,
                        onToggleTwoSpies = onlineViewModel::toggleTwoSpies,
                        onStartMatch = onlineViewModel::startMatch,
                        onLeaveRoom = onlineViewModel::leaveOrCloseRoom,
                        onDismissError = onlineViewModel::clearError
                    )
                }
            }

            if (showDesertConfirm) {
                AlertDialog(
                    onDismissRequest = { showDesertConfirm = false },
                    containerColor = PanelSurface,
                    title = { Text("تحذير الانسحاب المبكر", color = SpyCrimson) },
                    text = {
                        Text(
                            "مغادرة الغرفة أثناء سير الجولة تُسجَّل كـ (انسحاب) وتفرض حظر دخول مؤقت لمدة دقيقتين في نظام الانضباط. هل تريد المتابعة؟",
                            color = TextSecondary
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showDesertConfirm = false
                                onlineViewModel.desertActiveMatch()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SpyCrimson, contentColor = TextPrimary),
                            modifier = Modifier.testTag("confirm_desert_match_button")
                        ) {
                            Text("تأكيد الانسحاب")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDesertConfirm = false }) {
                            Text("البقاء في المهمة", color = ElectricCyan)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun OnlineOfflineFallbackScreen(
    onPlayOffline: () -> Unit,
    onBack: () -> Unit
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
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = "لا يوجد اتصال بالإنترنت",
                tint = AmberAlert,
                modifier = Modifier.size(68.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "لا يوجد اتصال بالإنترنت · No internet connection",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "يتطلب وضع الأونلاين اتصالاً بالإنترنت للمزامنة عبر الخادم. يمكنك اللعب الآن عبر وضع الجهاز الواحد (أوفلاين) بدون إنترنت.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onPlayOffline,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightOps),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("online_no_net_play_offline_button")
            ) {
                Icon(Icons.Default.Groups, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("العب أوفلاين · Play offline", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag("online_no_net_back_button")
            ) {
                Text("العودة للرئيسية", color = TextSecondary)
            }
            Spacer(modifier = Modifier.height(18.dp))
            NonGameplayOwnerFooter()
        }
    }
}

@Composable
private fun OnlineAuthScreen(
    isLoadingAuto: Boolean,
    errorMessage: String?,
    onSignInClick: () -> Unit,
    onBack: () -> Unit
) {
    TacticalBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(22.dp)
                .widthIn(max = 540.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("auth_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = ElectricCyan)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Image(
                painter = painterResource(id = R.drawable.img_loading),
                contentDescription = "تسجيل الدخول لغرفة العمليات",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.5.dp, ElectricCyan, RoundedCornerShape(20.dp))
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "بوابة الأونلاين السرية",
                style = MaterialTheme.typography.headlineMedium,
                color = ElectricCyan,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "سجّل الدخول بحساب Google للانضمام إلى غرف العمليات الجماعية ومزامنة المؤقت والأدوار لحظياً.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(22.dp))

            if (isLoadingAuto) {
                CircularProgressIndicator(color = ElectricCyan)
                Spacer(modifier = Modifier.height(12.dp))
                Text("جاري التحقق من الجلسة...", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            } else {
                Button(
                    onClick = onSignInClick,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightOps),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("google_sign_in_button")
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign in with Google · تسجيل الدخول عبر Google", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = Color(0xFF3A0E18),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SpyCrimson)
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            NonGameplayOwnerFooter()
        }
    }
}

@Composable
private fun OnlineLobbyScreen(
    userName: String,
    currentUserId: String,
    state: OnlineGameUiState,
    onCreateRoom: (alias: String, roomName: String, publicRoom: Boolean, maxPlayers: Int, durationSec: Long) -> Unit,
    onJoinRoom: (String, String) -> Unit,
    onJoinPublicRoom: (String, String) -> Unit,
    onSelectRoom: (String) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    onDismissError: () -> Unit
) {
    var alias by remember(userName) { mutableStateOf(userName.take(20)) }
    var joinCode by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedLobbyTab by remember { mutableIntStateOf(0) } // 0 = Public Rooms, 1 = My Rooms

    val availablePublicRooms = remember(state.publicRooms, currentUserId) {
        state.publicRooms.filter { currentUserId !in it.memberUids }
    }
    val canEnterRoom = state.status !is OnlineUiStatus.Loading && state.isConnected && state.afkCooldownSeconds == 0

    TacticalBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            ReconnectingBanner(visible = !state.isConnected)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .widthIn(max = 600.dp)
                    .align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(48.dp).testTag("lobby_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = ElectricCyan)
                        }
                        Column {
                            Text("نظام الغرف الأونلاين", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                            state.userProfile?.let { profile ->
                                Text(
                                    text = "المباريات: ${profile.gamesPlayed} · انتصارات: ${profile.winsAsOrg + profile.winsAsSpy} · انسحابات: ${profile.deserterCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.size(48.dp).testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "تسجيل الخروج", tint = SpyCrimson)
                    }
                }

                if (state.afkCooldownSeconds > 0) {
                    Surface(
                        color = Color(0xFF3A160B),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF6D00))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = AmberAlert)
                            Text(
                                text = "عقوبة الانسحاب المبكر نشطة: يمكنك إنشاء أو دخول غرفة جديدة خلال ${formatTimer(state.afkCooldownSeconds)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it.take(24) },
                    label = { Text("اسمك الحركي في الغرفة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("online_alias_input")
                )

                Button(
                    onClick = { showCreateDialog = true },
                    enabled = canEnterRoom,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightOps),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("create_room_button")
                ) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إنشاء غرفة عمليات مخصصة", style = MaterialTheme.typography.titleMedium)
                }

                Surface(
                    color = PanelSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("الانضمام السريع بكود الغرفة (6 رموز):", style = MaterialTheme.typography.titleMedium, color = AmberAlert)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = joinCode,
                                onValueChange = { joinCode = it.uppercase().take(6) },
                                placeholder = { Text("مثال: SHD482") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("join_room_code_input")
                            )
                            Button(
                                onClick = { onJoinRoom(joinCode, alias) },
                                enabled = joinCode.trim().length == 6 && canEnterRoom,
                                colors = ButtonDefaults.buttonColors(containerColor = OrgBlue, contentColor = TextPrimary),
                                modifier = Modifier.height(52.dp).testTag("join_room_button")
                            ) {
                                Text("انضمام")
                            }
                        }
                    }
                }

                if (state.status is OnlineUiStatus.Loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ElectricCyan)
                }

                if (state.status is OnlineUiStatus.Error) {
                    Surface(
                        color = Color(0xFF3A0E18),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SpyCrimson)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(state.status.message, color = TextPrimary, modifier = Modifier.weight(1f))
                            TextButton(onClick = onDismissError) { Text("حسناً", color = ElectricCyan) }
                        }
                    }
                }

                // Lobby Tabs: Public Rooms Browser vs My Active Rooms
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = if (selectedLobbyTab == 0) ElectricCyan else PanelSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedLobbyTab == 0) ElectricCyan else BorderGlow),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clickable { selectedLobbyTab = 0 }
                            .testTag("tab_public_rooms")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "الغرف العامة (${availablePublicRooms.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selectedLobbyTab == 0) MidnightOps else TextPrimary
                            )
                        }
                    }
                    Surface(
                        color = if (selectedLobbyTab == 1) ElectricCyan else PanelSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedLobbyTab == 1) ElectricCyan else BorderGlow),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clickable { selectedLobbyTab = 1 }
                            .testTag("tab_my_rooms")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "غرفي النشطة (${state.myRooms.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selectedLobbyTab == 1) MidnightOps else TextPrimary
                            )
                        }
                    }
                }

                if (selectedLobbyTab == 0) {
                    if (availablePublicRooms.isEmpty()) {
                        Surface(
                            color = PanelSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "لا توجد غرف عامة متاحة الآن — يمكنك إنشاء غرفة عامة لينضم إليها اللاعبون مباشرة.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availablePublicRooms, key = { it.id }) { roomItem ->
                                val isFull = roomItem.memberUids.size >= roomItem.maxPlayers.toInt()
                                Surface(
                                    color = PanelSurface,
                                    shape = RoundedCornerShape(14.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = roomItem.roomName,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = ElectricCyan,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "#${roomItem.roomCode} · ${roomItem.memberUids.size}/${roomItem.maxPlayers} لاعبين · ${roomItem.durationSeconds / 60}د · جولة ${roomItem.roundNumber}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }
                                        Button(
                                            onClick = { onJoinPublicRoom(roomItem.id, alias) },
                                            enabled = !isFull && canEnterRoom,
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = MidnightOps),
                                            modifier = Modifier.height(48.dp).testTag("join_public_${roomItem.roomCode}")
                                        ) {
                                            Text(if (isFull) "ممتلئة" else "انضمام")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (state.myRooms.isEmpty()) {
                        Text(
                            text = "لا توجد غرف حالياً — أنشئ غرفة جديدة أو انضم لغرفة عامة.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.myRooms, key = { it.id }) { roomItem ->
                                Surface(
                                    color = PanelSurface,
                                    shape = RoundedCornerShape(14.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectRoom(roomItem.id) }
                                        .testTag("room_item_${roomItem.roomCode}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${roomItem.roomName} (#${roomItem.roomCode})",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = ElectricCyan
                                            )
                                            Text(
                                                text = "${if (roomItem.publicRoom) "عامة" else "خاصة"} · ${roomItem.memberUids.size}/${roomItem.maxPlayers} لاعبين · جولة ${roomItem.roundNumber} · ${roomItem.status}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextSecondary
                                            )
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = "دخول", tint = ElectricCyan)
                                    }
                                }
                            }
                        }
                    }
                }

                NonGameplayOwnerFooter()
            }
        }
    }

    if (showCreateDialog) {
        CreateRoomDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { rName, isPub, maxP, dur ->
                showCreateDialog = false
                onCreateRoom(alias, rName, isPub, maxP, dur)
            }
        )
    }
}

@Composable
private fun CreateRoomDialog(
    onDismiss: () -> Unit,
    onConfirm: (roomName: String, publicRoom: Boolean, maxPlayers: Int, durationSec: Long) -> Unit
) {
    var roomName by remember { mutableStateOf("غرفة عمليات سرية") }
    var publicRoom by remember { mutableStateOf(true) }
    var maxPlayers by remember { mutableIntStateOf(8) }
    var durationMinutes by remember { mutableIntStateOf(10) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelSurface,
        title = { Text("إعداد غرفة العمليات", color = ElectricCyan) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it.take(36) },
                    label = { Text("اسم الغرفة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_room_name_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("غرفة عامة (تظهر في المتصفح)", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Text(
                            text = if (publicRoom) "متاحة للانضمام المباشر أو بالكود" else "خاصة — الدخول بالكود السري فقط",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = publicRoom,
                        onCheckedChange = { publicRoom = it },
                        modifier = Modifier.testTag("create_room_public_switch")
                    )
                }

                Text("الحد الأقصى للاعبين: $maxPlayers", style = MaterialTheme.typography.labelLarge, color = AmberAlert)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(4, 5, 6, 7, 8).forEach { count ->
                        FilterChip(
                            selected = maxPlayers == count,
                            onClick = { maxPlayers = count },
                            label = { Text("$count") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text("مدة الجولة: $durationMinutes دقائق", style = MaterialTheme.typography.labelLarge, color = AmberAlert)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(6, 8, 10, 12).forEach { mins ->
                        FilterChip(
                            selected = durationMinutes == mins,
                            onClick = { durationMinutes = mins },
                            label = { Text("${mins}د") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(roomName, publicRoom, maxPlayers, durationMinutes * 60L) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightOps),
                modifier = Modifier.testTag("confirm_create_room_button")
            ) {
                Text("إنشاء الغرفة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء", color = TextSecondary) }
        }
    )
}

@Composable
private fun OnlineWaitingRoomScreen(
    room: OnlineRoomDoc,
    currentUserId: String,
    isConnected: Boolean,
    status: OnlineUiStatus,
    onToggleReady: () -> Unit,
    onKickPlayer: (String) -> Unit,
    onUpdateConfig: (Boolean, Int, Long) -> Unit,
    onToggleTwoSpies: (Boolean) -> Unit,
    onStartMatch: () -> Unit,
    onLeaveRoom: () -> Unit,
    onDismissError: () -> Unit
) {
    val isHost = room.hostId == currentUserId
    val amReady = currentUserId in room.readyUids

    TacticalBackground {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ReconnectingBanner(visible = !isConnected)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
                    .widthIn(max = 580.dp)
                    .align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(room.roomName, style = MaterialTheme.typography.headlineMedium, color = TextPrimary, maxLines = 1)
                        Text(
                            text = "${if (room.publicRoom) "غرفة عامة" else "غرفة خاصة"} · جولة ${room.roundNumber} · ${room.durationSeconds / 60} دقائق",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberAlert
                        )
                    }
                    TextButton(
                        onClick = onLeaveRoom,
                        modifier = Modifier.testTag("leave_waiting_room_button")
                    ) {
                        Text(if (isHost) "إغلاق الغرفة" else "مغادرة", color = SpyCrimson)
                    }
                }

                Surface(
                    color = PanelSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, ElectricCyan),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("كود الانضمام للغرفة", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = room.roomCode,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            letterSpacing = 6.sp,
                            color = ElectricCyan
                        )
                        Text(
                            text = "شارك الكود مع أصدقائك (4 إلى ${room.maxPlayers} لاعبين)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                if (status is OnlineUiStatus.Error) {
                    Surface(
                        color = Color(0xFF3A0E18),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SpyCrimson)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(status.message, color = TextPrimary, modifier = Modifier.weight(1f))
                            TextButton(onClick = onDismissError) { Text("حسناً", color = ElectricCyan) }
                        }
                    }
                }

                Text("العملاء المنضمون (${room.memberNames.size}/${room.maxPlayers}):", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(room.memberNames.indices.toList()) { idx ->
                        val name = room.memberNames[idx]
                        val uid = room.memberUids.getOrElse(idx) { "" }
                        val ready = uid in room.readyUids
                        val isMe = uid == currentUserId
                        val isPlayerHost = uid == room.hostId
                        val statusColor = if (isPlayerHost) AmberAlert else if (ready) EmeraldSuccess else TextSecondary
                        Surface(
                            color = if (isMe) Color(0xFF0A243D) else PanelSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.2.dp,
                                if (ready) EmeraldSuccess.copy(alpha = 0.75f) else if (isMe) ElectricCyan.copy(alpha = 0.7f) else BorderGlow
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(statusColor.copy(alpha = 0.16f))
                                            .border(1.dp, statusColor.copy(alpha = 0.7f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${idx + 1}",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = if (isMe) "$name (أنت)" else name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = if (isPlayerHost) "المضيف · جاهز" else if (ready) "جاهز ✓" else "في الانتظار...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = statusColor
                                        )
                                    }
                                }
                                if (isHost && uid != currentUserId) {
                                    IconButton(
                                        onClick = { onKickPlayer(uid) },
                                        modifier = Modifier.size(48.dp).testTag("kick_player_$uid")
                                    ) {
                                        Icon(Icons.Default.PersonRemove, contentDescription = "طرد اللاعب", tint = SpyCrimson)
                                    }
                                }
                            }
                        }
                    }
                }

                if (isHost) {
                    Surface(
                        color = PanelSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("غرفة عامة للجميع", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                Switch(
                                    checked = room.publicRoom,
                                    onCheckedChange = { onUpdateConfig(it, room.maxPlayers.toInt(), room.durationSeconds) }
                                )
                            }
                            if (room.memberUids.size >= 7) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("تفعيل متجسسَين اثنين", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                    Switch(
                                        checked = room.twoSpiesEnabled,
                                        onCheckedChange = onToggleTwoSpies,
                                        modifier = Modifier.testTag("online_two_spies_switch")
                                    )
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onToggleReady,
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, if (amReady) EmeraldSuccess else ElectricCyan),
                        modifier = Modifier.weight(1f).height(54.dp).testTag("toggle_ready_button")
                    ) {
                        Text(
                            text = if (amReady) "جاهز ✓" else "تأكيد الجاهزية",
                            color = if (amReady) EmeraldSuccess else ElectricCyan
                        )
                    }

                    if (isHost) {
                        val canStart = room.memberUids.size in 4..8 && status !is OnlineUiStatus.Loading
                        Button(
                            onClick = onStartMatch,
                            enabled = canStart,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightOps),
                            modifier = Modifier.weight(1.4f).height(54.dp).testTag("online_start_match_button")
                        ) {
                            Text(
                                text = if (room.memberUids.size < 4) "تحتاج ${4 - room.memberUids.size} لاعبين" else "بدء المهمة الآن!",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                NonGameplayOwnerFooter()
            }
        }
    }
}

@Composable
private fun OnlineLiveMatchScreen(
    room: OnlineRoomDoc,
    state: OnlineGameUiState,
    currentUserId: String,
    viewModel: OnlineGameViewModel,
    feedback: TacticalFeedback,
    onLeaveRoom: () -> Unit
) {
    val priv = state.privateRole
    val me = state.publicPlayers.firstOrNull { it.userId == currentUserId }
    val isSpy = priv?.role == "SPY"
    var showComposer by remember { mutableStateOf(false) }
    var preArmDecoy by remember { mutableStateOf(false) }
    var preArmSpoof by remember { mutableStateOf(false) }
    var inspectedRareCard by remember { mutableStateOf<RareCardType?>(null) }
    var showManualWiretap by remember { mutableStateOf(false) }
    var seenRoleCardKey by rememberSaveable { mutableStateOf("") }
    var showRoleCardDialog by remember { mutableStateOf(false) }
    var showBlackChannelDialog by remember { mutableStateOf(false) }

    val roundRoleKey = "${room.id}_${room.roundNumber}_${room.roundStartEpochMs}"
    val showAutoRoleReveal = priv != null && room.roundStartEpochMs > 0L && seenRoleCardKey != roundRoleKey

    val allySpyUids = remember(priv?.allySpyUids, isSpy) {
        if (isSpy) priv?.allySpyUids.orEmpty().toSet() else emptySet()
    }
    val allySpyNames = remember(priv?.allySpyNames, isSpy) {
        if (isSpy) priv?.allySpyNames.orEmpty() else emptyList()
    }
    val activeWiretapUids = remember(state.sharedWiretapTargets, isSpy) {
        if (isSpy) state.activeWiretapTargetUids else emptySet()
    }
    val wiretapActive = isSpy && activeWiretapUids.isNotEmpty()
    val targetCandidates = remember(state.publicPlayers, currentUserId, allySpyUids) {
        state.publicPlayers
            .filter { it.alive && it.userId != currentUserId && it.userId !in allySpyUids }
            .ifEmpty { state.publicPlayers.filter { it.alive && it.userId != currentUserId } }
    }

    TacticalBackground {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ReconnectingBanner(visible = !state.isConnected)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
                    .widthIn(max = 600.dp)
                    .align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header: Timer + Role + Secret Card + Emergency + Leave
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatTimer(state.secondsRemaining),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = if (state.secondsRemaining <= 60) SpyCrimson else ElectricCyan
                        )
                        Text(
                            text = "${room.roomName} · ${if (isSpy) "دورك: المتجسس" else "دورك: عضو المنظمة"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSpy) SpyCrimson else OrgBlue,
                            maxLines = 1
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showRoleCardDialog = true },
                            enabled = priv != null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PanelSurface)
                                .border(1.dp, ElectricCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .testTag("online_view_role_card_button")
                        ) {
                            Icon(Icons.Default.Badge, contentDescription = "بطاقة دوري السرية", tint = ElectricCyan)
                        }
                        Button(
                            onClick = viewModel::triggerOnlineInvestigation,
                            enabled = me != null && me.alive && !me.usedEmergency,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberAlert, contentColor = MidnightOps),
                            modifier = Modifier.height(48.dp).testTag("online_emergency_button")
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (me?.usedEmergency == true) "استُنفد" else "تحقيق")
                        }
                        IconButton(
                            onClick = onLeaveRoom,
                            modifier = Modifier.size(48.dp).testTag("online_leave_match_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "انسحاب", tint = TextSecondary)
                        }
                    }
                }

                // Code Slots Bar
                SecretCodeSlotsBar(
                    codeSlots = room.codeSlots,
                    revealedIndices = (0 until room.spyRevealedCount.toInt()).toSet(),
                    isSpy = isSpy,
                    assignedIndices = priv?.assignedSlots?.map { it.toInt() } ?: emptyList()
                )

                // Player Cards Strip (all room players + AFK / Deserted / Eliminated status; ally spy badge shown ONLY to Spies)
                TacticalPlayerRosterStrip(
                    players = state.publicPlayers
                        .sortedBy { it.seatIndex }
                        .map { p ->
                            RosterPlayerUi(
                                id = p.userId,
                                name = p.displayName,
                                seatNumber = p.seatIndex.toInt() + 1,
                                alive = p.alive,
                                isCurrentTurnOrMe = (p.userId == currentUserId),
                                isHost = (p.userId == room.hostId),
                                usedEmergency = p.usedEmergency,
                                afkWarning = p.afkWarning,
                                deserted = p.deserted,
                                isAllySpy = (p.userId in allySpyUids)
                            )
                        }
                )

                // Banner Notification
                state.bannerMessage?.let { msg ->
                    Surface(
                        color = Color(0xFF10243E),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(msg, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::dismissBanner) { Text("إخفاء", color = ElectricCyan) }
                        }
                    }
                }

                // System Audit Prompt (excludes report sender; remaining alive players verify)
                if (room.auditId.isNotBlank() && state.auditSecondsLeft > 0 && me != null && me.alive &&
                    me.userId != room.auditSenderUid && me.auditAnsweredId != room.auditId
                ) {
                    Surface(
                        color = Color(0xFF2B1C08),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, AmberAlert),
                        modifier = Modifier.testTag("online_audit_card")
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("سؤال النظام التلقائي: ${room.auditQuestion}", style = MaterialTheme.typography.titleMedium, color = AmberAlert, modifier = Modifier.weight(1f))
                                Text("${state.auditSecondsLeft}s", fontFamily = JetBrainsMonoFontFamily, color = SpyCrimson)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                room.auditOptions.forEachIndexed { idx, opt ->
                                    OutlinedButton(
                                        onClick = { viewModel.answerOnlineAudit(idx) },
                                        modifier = Modifier.weight(1f).testTag("online_audit_opt_$idx")
                                    ) {
                                        Text(opt, style = MaterialTheme.typography.labelSmall, color = TextPrimary, maxLines = 2)
                                    }
                                }
                            }
                        }
                    }
                } else if (room.auditId.isNotBlank() && state.auditSecondsLeft > 0 && me != null &&
                    me.userId == room.auditSenderUid && !room.auditSuccessReported && !room.auditWrongReported
                ) {
                    Surface(
                        color = Color(0xFF1B1910),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AmberAlert.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "طرح النظام سؤال تحقق تلقائياً على بقية اللاعبين (${state.auditSecondsLeft}ث)...",
                            style = MaterialTheme.typography.bodySmall,
                            color = AmberAlert,
                            modifier = Modifier.fillMaxWidth().padding(10.dp)
                        )
                    }
                }

                // Spy Hacking Station + 1-Minute Targeted Eavesdropping OR Org Slot Shield
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
                                    Text("محطة الاختراق · الشحنات: ${room.spyCharges}", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                }
                                TextButton(
                                    onClick = { showManualWiretap = true },
                                    modifier = Modifier.testTag("online_wiretap_button")
                                ) {
                                    Icon(Icons.Default.Hearing, contentDescription = null, tint = AmberAlert, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (wiretapActive) formatTimer(state.wiretapSecondsLeft) else "تنصت (01:00)",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = AmberAlert
                                    )
                                }
                            }

                            if (room.spyRevealedCount >= 4L) {
                                Button(
                                    onClick = viewModel::openOnlineFinalCodeEntry,
                                    colors = ButtonDefaults.buttonColors(containerColor = SpyCrimson, contentColor = TextPrimary),
                                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("online_spy_final_code_button")
                                ) {
                                    Text("إدخال الكود وإبطال العملية (10 ثوانٍ)!", style = MaterialTheme.typography.titleMedium)
                                }
                            } else {
                                val canHack = room.spyCharges > 0L && state.lockoutSecondsLeft == 0 && state.frozenSecondsLeft == 0 && (me?.alive == true)
                                val hackText = when {
                                    state.frozenSecondsLeft > 0 -> "تجميد النظام (${state.frozenSecondsLeft}ث)"
                                    state.lockoutSecondsLeft > 0 -> "مغلق مؤقتاً (${state.lockoutSecondsLeft}ث)"
                                    room.spyCharges <= 0L -> "لا توجد شحنات (تزداد مع البلاغات)"
                                    else -> "بدء الاختراق (-1 شحنة)"
                                }
                                Button(
                                    onClick = viewModel::startOnlineSpyPuzzle,
                                    enabled = canHack,
                                    colors = ButtonDefaults.buttonColors(containerColor = SpyCrimson, contentColor = TextPrimary),
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("online_start_puzzle_button")
                                ) {
                                    Text(hackText, style = MaterialTheme.typography.titleMedium)
                                }
                            }

                            if (allySpyNames.isNotEmpty()) {
                                Surface(
                                    color = Color(0xFF330F1B),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SpyCrimson.copy(alpha = 0.65f)),
                                    modifier = Modifier.fillMaxWidth().testTag("online_ally_spy_banner")
                                ) {
                                    Text(
                                        text = "زميلك المتجسس: ${allySpyNames.joinToString("، ")} · الخانات المكشوفة مشتركة (${room.spyRevealedCount}/4)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // Interactive 1-Minute Targeted Eavesdropping Bar (Spy Exclusive & Shared Between Two Spies)
                            val activeSummary = state.sharedWiretapTargets.joinToString("، ") { item ->
                                if (item.isFromAlly) "${item.targetName} (زميلك · ${item.secondsLeft}ث)" else "${item.targetName} (${item.secondsLeft}ث)"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (wiretapActive) {
                                        "التنصت الموجّه: $activeSummary"
                                    } else {
                                        "التنصت الموجّه (مؤقت 1:00) — اختر لاعباً:"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = AmberAlert,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (state.wiretapSecondsLeft > 0) formatTimer(state.wiretapSecondsLeft) else formatTimer(state.wiretapCycleSecondsLeft),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = if (wiretapActive) EmeraldSuccess else AmberAlert
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                targetCandidates.forEach { target ->
                                    val sharedEntry = state.sharedWiretapTargets.firstOrNull { it.targetUid == target.userId }
                                    val isCurrentTarget = sharedEntry != null
                                    FilterChip(
                                        selected = isCurrentTarget,
                                        onClick = { viewModel.selectWiretapTarget(target.userId) },
                                        label = {
                                            Text(
                                                text = if (sharedEntry?.isFromAlly == true) "${target.displayName} (زميلك)" else target.displayName,
                                                maxLines = 1,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        modifier = Modifier.testTag("spy_inline_wiretap_${target.userId}")
                                    )
                                }
                            }

                            if (wiretapActive) {
                                val interceptedTargetMsgs = state.messages
                                    .filter { it.channel == "PLAYER" && it.senderUid in activeWiretapUids }
                                    .takeLast(4)
                                Surface(
                                    color = Color(0xFF1F1506),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberAlert.copy(alpha = 0.7f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "قناة التنصت المشتركة ($activeSummary):",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AmberAlert
                                        )
                                        if (interceptedTargetMsgs.isEmpty()) {
                                            Text(
                                                text = "بانتظار إرسال الهدف لأي بلاغ الآن لقراءته فورياً بدون تشفير...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        } else {
                                            interceptedTargetMsgs.forEach { m ->
                                                Text(
                                                    text = "• ${m.senderDisplayName}: ${m.plainText}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = TextPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Spy-exclusive Black Channel ("القناة السوداء") — strictly hidden from Org
                            val blackWindowLabel = if (state.isBlackChannelOpen) {
                                formatTimer(state.blackChannelSecondsLeft)
                            } else {
                                "دقيقتان (02:00)"
                            }
                            BlackChannelSpySection(
                                isReadyToOpen = state.isBlackChannelReady,
                                isOpen = state.isBlackChannelOpen,
                                isPermanentlyClosed = state.isBlackChannelPermanentlyClosed,
                                usesRemaining = state.blackChannelUsesRemaining,
                                windowLabel = blackWindowLabel,
                                testTagPrefix = "online_black_channel",
                                onOpenChannel = viewModel::openOnlineBlackChannel,
                                onOpenComposer = { showBlackChannelDialog = true }
                            )
                        }
                    }
                } else if (me != null && me.alive && !me.usedSlotShield && priv != null && priv.assignedSlots.isNotEmpty()) {
                    SlotShieldSelectorSection(
                        assignedSlots = priv.assignedSlots.map { it.toInt() },
                        testTagPrefix = "online_slot_shield_button",
                        onActivateShield = viewModel::activateOnlineSlotShield
                    )
                }

                // Rare Cards Row with card artwork & inspect preview
                val cardEnums = priv?.rareCards.orEmpty().mapNotNull { name ->
                    enumValues<RareCardType>().firstOrNull { it.name == name }
                }
                RareCardHandRow(
                    cards = cardEnums,
                    testTagPrefix = "online_rare_",
                    onCardClick = { card -> inspectedRareCard = card }
                )

                // Dual-Channel Message Feed (Secret Battle Log is strictly NOT accessible or shown here!)
                Text("البلاغات وسجل النظام:", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val visibleMsgs = state.messages.filter { m ->
                        when (m.channel) {
                            "SYSTEM_SPY" -> isSpy
                            "SYSTEM_ORG" -> !isSpy && (m.targetUid.isBlank() || m.targetUid == currentUserId)
                            else -> true
                        }
                    }.reversed()

                    items(visibleMsgs, key = { it.id }) { msg ->
                        val shouldDecrypt = !isSpy ||
                            msg.channel != "PLAYER" ||
                            msg.senderUid in activeWiretapUids ||
                            msg.senderUid == currentUserId ||
                            msg.spoofedByUid == currentUserId
                        val body = if (shouldDecrypt) msg.plainText else msg.cipherText
                        val cardColor = when (msg.channel) {
                            "ALERT" -> Color(0xFF2F0D18)
                            "SYSTEM_ORG" -> Color(0xFF0B253A)
                            "SYSTEM_SPY" -> Color(0xFF291436)
                            else -> PanelSurface
                        }
                        Surface(
                            color = cardColor,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = msg.senderDisplayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (shouldDecrypt) ElectricCyan else SpyCrimson
                                    )
                                    if (!shouldDecrypt) {
                                        Text("مشفّر", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(body, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                            }
                        }
                    }
                }

                // Bottom Action: Send Template Message (with 30s cooldown)
                Button(
                    onClick = {
                        feedback.tapHaptic()
                        preArmDecoy = false
                        preArmSpoof = false
                        showComposer = true
                    },
                    enabled = me != null && me.alive && state.messageCooldownSeconds == 0,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightOps),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("online_open_composer_button")
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            me?.alive == false -> "تم استبعادك (وضع المشاهدة)"
                            state.messageCooldownSeconds > 0 -> "إرسال البلاغ القادم خلال ${state.messageCooldownSeconds}ث"
                            else -> "إرسال بلاغ تكتيكي"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }

    if ((showAutoRoleReveal || showRoleCardDialog) && priv != null) {
        val cipherType = enumValues<CipherType>().firstOrNull { it.name == room.cipherType } ?: CipherType.CAESAR_FORWARD
        PlayerSecretRoleCardDialog(
            playerName = me?.displayName ?: "عميل",
            role = if (priv.role == "SPY") Role.SPY else Role.ORG,
            codeSlots = room.codeSlots,
            assignedSlotIndices = priv.assignedSlots.map { it.toInt() },
            cipherType = cipherType,
            revealedIndices = if (isSpy) (0 until room.spyRevealedCount.toInt()).toSet() else emptySet(),
            allySpyNames = allySpyNames,
            initialFlipped = showRoleCardDialog && !showAutoRoleReveal,
            onDismiss = {
                seenRoleCardKey = roundRoleKey
                showRoleCardDialog = false
            }
        )
    }

    inspectedRareCard?.let { card ->
        val aliveMe = me?.alive == true
        val otherAlive = state.publicPlayers.any { it.alive && it.userId != currentUserId }
        val (canActivate, reason) = when {
            !aliveMe -> false to "تم استبعادك — لا يمكنك تفعيل البطاقات."
            card == RareCardType.DECOY_TRAP -> (state.messageCooldownSeconds == 0) to
                "انتظر انتهاء مهلة الإرسال (${state.messageCooldownSeconds}ث) لإرفاق الفخ ببلاغك."
            card == RareCardType.IDENTITY_SPOOF -> (state.messageCooldownSeconds == 0 && otherAlive) to
                if (!otherAlive) "لا يوجد لاعب نشط لتزوير هويته." else "انتظر انتهاء مهلة الإرسال (${state.messageCooldownSeconds}ث) لتزوير الهوية."
            card == RareCardType.ZERO_DAY_EXPLOIT -> (room.spyRevealedCount < 4L) to
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
                    viewModel.useOnlineRareCard(card)
                }
            },
            onDismiss = { inspectedRareCard = null }
        )
    }

    if (showComposer) {
        val mappedPlayers = state.publicPlayers.map {
            PlayerState(id = it.userId, name = it.displayName, role = Role.ORG, alive = it.alive)
        }
        val rareSet = priv?.rareCards.orEmpty()
        MessageComposerDialog(
            players = mappedPlayers,
            currentSenderId = currentUserId,
            usedWords = room.usedWords.toSet(),
            hasDecoyTrapCard = RareCardType.DECOY_TRAP.name in rareSet,
            hasSpoofCard = RareCardType.IDENTITY_SPOOF.name in rareSet,
            initialUseDecoyTrap = preArmDecoy,
            initialUseSpoof = preArmSpoof,
            onDismiss = { showComposer = false },
            onSend = { tmpl, words, decoy, spoofId ->
                showComposer = false
                preArmDecoy = false
                preArmSpoof = false
                viewModel.sendOnlineTemplateMessage(tmpl, words, decoy, spoofId)
            }
        )
    }

    if (state.showWiretapPrompt || showManualWiretap) {
        val targets = targetCandidates
            .map { PlayerState(id = it.userId, name = it.displayName, role = Role.ORG) }
        WiretapSelectionDialog(
            players = targets,
            currentTargetId = priv?.wiretapTargetUid?.takeIf { it.isNotBlank() && state.wiretapSecondsLeft > 0 },
            onSelect = { targetUid ->
                showManualWiretap = false
                viewModel.selectWiretapTarget(targetUid)
            },
            onDismiss = {
                showManualWiretap = false
                viewModel.dismissWiretapPrompt()
            }
        )
    }

    if (state.showFinalCodeDialog) {
        FinalCodeEntryDialog(
            secondsLeft = state.finalCodeSecondsLeft,
            onDismiss = viewModel::dismissOnlineFinalCodeDialog,
            onSubmitCode = viewModel::submitOnlineFinalCode
        )
    }

    if (isSpy && showBlackChannelDialog && state.isBlackChannelOpen) {
        BlackChannelComposerDialog(
            usesRemaining = state.blackChannelUsesRemaining,
            windowLabel = formatTimer(state.blackChannelSecondsLeft),
            onDismiss = { showBlackChannelDialog = false },
            onSend = { text, style ->
                showBlackChannelDialog = false
                viewModel.sendOnlineBlackChannelMessage(text, style)
            }
        )
    }
}

@Composable
private fun OnlineVoteWaitingScreen(
    isAlive: Boolean,
    votedCount: Int,
    totalAlive: Int,
    isConnected: Boolean
) {
    TacticalBackground {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ReconnectingBanner(visible = !isConnected)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .widthIn(max = 520.dp)
                    .align(Alignment.CenterHorizontally),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = ElectricCyan)
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = if (isAlive) "تم تسجيل صوتك السري" else "أنت في وضع المشاهدة",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "في انتظار بقية اللاعبين... ($votedCount / $totalAlive)",
                    style = MaterialTheme.typography.titleMedium,
                    color = AmberAlert
                )
            }
        }
    }
}
