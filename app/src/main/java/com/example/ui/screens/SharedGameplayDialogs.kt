package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.core.*
import com.example.ui.components.NonGameplayOwnerFooter
import com.example.ui.components.TacticalBackground
import com.example.ui.components.TacticalCardThumb
import com.example.ui.theme.*

@Composable
fun MessageComposerDialog(
    players: List<PlayerState>,
    currentSenderId: String,
    usedWords: Set<String>,
    hasDecoyTrapCard: Boolean,
    hasSpoofCard: Boolean,
    initialUseDecoyTrap: Boolean = false,
    initialUseSpoof: Boolean = false,
    onDismiss: () -> Unit,
    onSend: (template: MessageTemplate, selectedWords: List<String>, useDecoyTrap: Boolean, spoofAsPlayerId: String?) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(MessageCategory.FIELD_ORDERS) }
    val categoryTemplates = remember(selectedCategory) {
        TemplatesAndWordBank.templates.filter { it.category == selectedCategory }
    }
    var selectedTemplate by remember(categoryTemplates) { mutableStateOf(categoryTemplates.first()) }
    var shuffleSeed by remember { mutableIntStateOf(0) }
    val chosenWords = remember(selectedTemplate) { mutableStateMapOf<Int, String>() }

    val otherAlivePlayers = remember(players, currentSenderId) {
        players.filter { it.alive && it.id != currentSenderId }
    }
    var useDecoyTrap by remember(hasDecoyTrapCard, initialUseDecoyTrap) {
        mutableStateOf(hasDecoyTrapCard && initialUseDecoyTrap)
    }
    var useSpoof by remember(hasSpoofCard, initialUseSpoof, otherAlivePlayers) {
        mutableStateOf(hasSpoofCard && initialUseSpoof && otherAlivePlayers.isNotEmpty())
    }
    var spoofTargetId by remember(otherAlivePlayers) {
        mutableStateOf(otherAlivePlayers.firstOrNull()?.id)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = DeepBunker,
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, ElectricCyan.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TacticalCardThumb(
                            drawableRes = R.drawable.img_card_info,
                            contentDescription = "بطاقة البلاغات التكتيكية",
                            borderColor = ElectricCyan
                        )
                        Text("إرسال بلاغ تكتيكي", style = MaterialTheme.typography.titleLarge, color = ElectricCyan)
                    }
                    TextButton(onClick = onDismiss) { Text("إلغاء", color = TextSecondary) }
                }

                // Category tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MessageCategory.entries.forEach { cat ->
                        val active = cat == selectedCategory
                        Surface(
                            color = if (active) ElectricCyan else PanelSurface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedCategory = cat }
                                .testTag("cat_${cat.name}")
                        ) {
                            Text(
                                text = cat.arabicTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) MidnightOps else TextSecondary,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // Template selector chips (5 templates in category)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categoryTemplates.forEach { tmpl ->
                        val active = tmpl.id == selectedTemplate.id
                        Surface(
                            color = if (active) Color(0xFF0A2A4A) else PanelSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (active) ElectricCyan else BorderGlow
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTemplate = tmpl }
                                .testTag("template_${tmpl.id}")
                        ) {
                            val preview = tmpl.render(List(tmpl.slots.size) { i ->
                                chosenWords[i] ?: "[${tmpl.slots[i].label}]"
                            })
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (active) TextPrimary else TextSecondary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                // Shuffle button + Rare card toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { shuffleSeed++ },
                        modifier = Modifier.testTag("shuffle_words_button")
                    ) {
                        Icon(Icons.Default.Casino, contentDescription = null, tint = ElectricCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تحديث الخيارات (6)", color = ElectricCyan)
                    }

                    if (hasDecoyTrapCard) {
                        FilterChip(
                            selected = useDecoyTrap,
                            onClick = { useDecoyTrap = !useDecoyTrap },
                            label = { Text("تفعيل الفخ الخداعي", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    if (hasSpoofCard && otherAlivePlayers.isNotEmpty()) {
                        FilterChip(
                            selected = useSpoof,
                            onClick = { useSpoof = !useSpoof },
                            label = { Text("تزوير الهوية", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                if (useSpoof && otherAlivePlayers.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "اختر العميل الذي سيظهر البلاغ باسمه (${otherAlivePlayers.size} مرشحين):",
                            style = MaterialTheme.typography.labelSmall,
                            color = VioletRare
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            otherAlivePlayers.forEach { target ->
                                FilterChip(
                                    selected = spoofTargetId == target.id,
                                    onClick = { spoofTargetId = target.id },
                                    label = { Text(target.name, maxLines = 1) },
                                    modifier = Modifier.testTag("spoof_target_${target.id}")
                                )
                            }
                        }
                    }
                }

                // Slot word pickers (6 random unused words per slot)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(selectedTemplate.slots.indices.toList()) { slotIdx ->
                        val kind = selectedTemplate.slots[slotIdx]
                        val otherSelected = chosenWords.filterKeys { it != slotIdx }.values.toSet()
                        val options = remember(selectedTemplate.id, slotIdx, shuffleSeed, usedWords, otherSelected.size) {
                            TemplatesAndWordBank.pickSixOptions(
                                kind = kind,
                                usedWords = usedWords,
                                currentlySelectedInMessage = otherSelected,
                                players = players,
                                currentSenderId = currentSenderId
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "اختر [${kind.label}]:",
                                style = MaterialTheme.typography.titleMedium,
                                color = AmberAlert
                            )
                            // 2 rows of 3 options
                            for (rowStart in options.indices step 3) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    for (i in rowStart until minOf(rowStart + 3, options.size)) {
                                        val word = options[i]
                                        val isPicked = chosenWords[slotIdx] == word
                                        Surface(
                                            color = if (isPicked) ElectricCyan else PanelSurface,
                                            shape = RoundedCornerShape(10.dp),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isPicked) ElectricCyan else BorderGlow
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .clickable { chosenWords[slotIdx] = word }
                                                .testTag("word_opt_${slotIdx}_$i")
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(6.dp)) {
                                                Text(
                                                    text = word,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (isPicked) MidnightOps else TextPrimary,
                                                    fontWeight = if (isPicked) FontWeight.Bold else FontWeight.Normal,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 2
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val allSlotsFilled = selectedTemplate.slots.indices.all { !chosenWords[it].isNullOrBlank() }
                Button(
                    onClick = {
                        val list = selectedTemplate.slots.indices.map { chosenWords[it] ?: "" }
                        onSend(
                            selectedTemplate,
                            list,
                            useDecoyTrap,
                            if (useSpoof) spoofTargetId else null
                        )
                    },
                    enabled = allSlotsFilled,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightOps),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("send_template_message_button")
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إرسال البلاغ فوراً", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun FinalCodeEntryDialog(
    secondsLeft: Int,
    onDismiss: () -> Unit,
    onSubmitCode: (List<String>) -> Unit
) {
    val entered = remember { mutableStateListOf<String>() }
    val keys = remember { "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toList() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = DeepBunker,
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, SpyCrimson)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("إدخال الكود وإبطال العملية", style = MaterialTheme.typography.titleLarge, color = SpyCrimson)
                Text(
                    text = "المهلة المتبقية: ${secondsLeft} ثوانٍ",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = AmberAlert
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 0..3) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MidnightOps)
                                .border(1.5.dp, SpyCrimson, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = entered.getOrElse(i) { "_" },
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }

                // Compact alphanumeric keypad (excluding 0/O/1/I)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (row in keys.chunked(6)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { ch ->
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PanelSurface)
                                        .border(1.dp, BorderGlow, RoundedCornerShape(8.dp))
                                        .clickable(enabled = entered.size < 4) {
                                            entered.add(ch.toString())
                                        }
                                        .testTag("keypad_$ch"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = ch.toString(),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { if (entered.isNotEmpty()) entered.removeAt(entered.lastIndex) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("مسح", color = TextSecondary)
                    }
                    Button(
                        onClick = { onSubmitCode(entered.toList()) },
                        enabled = entered.size == 4,
                        colors = ButtonDefaults.buttonColors(containerColor = SpyCrimson, contentColor = TextPrimary),
                        modifier = Modifier.weight(2f).testTag("confirm_final_code_button")
                    ) {
                        Text("تأكيد الإبطال!")
                    }
                }
            }
        }
    }
}

@Composable
fun GameResultScreen(
    winnerTeam: WinTeam,
    winReason: WinReason,
    codeSlots: List<String>,
    onOpenStatistics: () -> Unit,
    onPlayAgain: () -> Unit,
    onGoHome: () -> Unit,
    playAgainLabel: String = "جولة جديدة"
) {
    val imageRes = when (winReason) {
        WinReason.SPY_CODE_CRACKED, WinReason.TOO_FEW_ORG -> R.drawable.img_spy_victory
        WinReason.SPY_ARRESTED -> R.drawable.img_spy_arrested
        WinReason.TIME_OUT -> R.drawable.img_times_up
    }
    val accent = if (winnerTeam == WinTeam.SPY) SpyCrimson else ElectricCyan

    TacticalBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = winReason.arabicTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(2.dp, accent, RoundedCornerShape(22.dp))
            )

            Text(
                text = winReason.arabicTitle,
                style = MaterialTheme.typography.displayLarge,
                color = accent,
                textAlign = TextAlign.Center
            )
            Text(
                text = winReason.arabicSubtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Surface(
                color = PanelSurface,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("الكود السري للجولة", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = codeSlots.joinToString("  -  "),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = ElectricCyan
                    )
                }
            }

            Button(
                onClick = onOpenStatistics,
                colors = ButtonDefaults.buttonColors(containerColor = AmberAlert, contentColor = MidnightOps),
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("open_statistics_button")
            ) {
                Icon(Icons.Default.BarChart, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("عرض الإحصائيات وكشف السجل الكامل", style = MaterialTheme.typography.titleMedium)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onGoHome,
                    modifier = Modifier.weight(1f).height(50.dp).testTag("result_home_button")
                ) {
                    Icon(Icons.Default.Home, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("الرئيسية")
                }
                Button(
                    onClick = onPlayAgain,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightOps),
                    modifier = Modifier.weight(1f).height(50.dp).testTag("result_play_again_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(playAgainLabel, maxLines = 1)
                }
            }

            NonGameplayOwnerFooter()
        }
    }
}

@Composable
fun MatchStatisticsScreen(
    players: List<PlayerState>,
    codeSlots: List<String>,
    cipherType: CipherType,
    cipherShift: Int,
    messages: List<GameMessage>,
    battleLogs: List<BattleLogEntry> = emptyList(),
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    TacticalBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(18.dp)
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("تقرير الاستخبارات وسجل المعركة", style = MaterialTheme.typography.titleLarge, color = ElectricCyan)
                TextButton(onClick = onBack, modifier = Modifier.testTag("stats_back_button")) {
                    Text("النتيجة", color = TextPrimary)
                }
            }

            // Summary card: Spy identity & cipher
            val spies = players.filter { it.role == Role.SPY }
            val fastestSolver = players.filter { it.puzzlesSolved > 0 && it.fastestPuzzleMs > 0L }
                .minByOrNull { it.fastestPuzzleMs }

            Surface(
                color = PanelSurface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SpyCrimson.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "هوية المتجسس: ${spies.joinToString("، ") { it.name }}",
                        style = MaterialTheme.typography.titleMedium,
                        color = SpyCrimson
                    )
                    Text(
                        text = "خوارزمية التشفير: ${cipherType.arabicName} (الإزاحة: $cipherShift)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "الكود السري: ${codeSlots.joinToString(" - ")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElectricCyan
                    )
                    if (fastestSolver != null) {
                        val sec = String.format("%.1f", fastestSolver.fastestPuzzleMs / 1000.0)
                        Text(
                            text = "أسرع من حل الألغاز: ${fastestSolver.name} ($sec ثانية، حل ${fastestSolver.puzzlesSolved} ألغاز)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EmeraldSuccess
                        )
                    }
                }
            }

            // Players breakdown (interactive-style Player Outcome Cards)
            Text("كروت اللاعبين وكشف الأدوار:", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                players.forEach { p ->
                    PlayerStatChip(p)
                }
            }

            // Post-game reveal tabs: Secret Battle Log (unlocked ONLY after round ends) vs Decrypted Messages
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = if (selectedTab == 0) AmberAlert else PanelSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedTab == 0) AmberAlert else BorderGlow),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clickable { selectedTab = 0 }
                        .testTag("tab_secret_battle_log")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (selectedTab == 0) MidnightOps else AmberAlert,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "سجل المعركة السري (${battleLogs.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selectedTab == 0) MidnightOps else TextPrimary,
                            maxLines = 1
                        )
                    }
                }
                Surface(
                    color = if (selectedTab == 1) ElectricCyan else PanelSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedTab == 1) ElectricCyan else BorderGlow),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clickable { selectedTab = 1 }
                        .testTag("tab_decrypted_messages")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = if (selectedTab == 1) MidnightOps else ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "سجل البلاغات (${messages.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selectedTab == 1) MidnightOps else TextPrimary,
                            maxLines = 1
                        )
                    }
                }
            }

            if (selectedTab == 0) {
                if (battleLogs.isEmpty()) {
                    Surface(
                        color = PanelSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "لا توجد أحداث مسجلة في هذه الجولة.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(battleLogs, key = { it.id }) { entry ->
                            val badgeColor = when (entry.eventType) {
                                BattleEventType.WIRETAP -> AmberAlert
                                BattleEventType.PUZZLE_ATTACK -> SpyCrimson
                                BattleEventType.ABILITY -> VioletRare
                                BattleEventType.INVESTIGATION, BattleEventType.VOTE -> ElectricCyan
                                BattleEventType.PENALTY -> Color(0xFFFF6D00)
                                BattleEventType.ROUND_START, BattleEventType.ROUND_END -> EmeraldSuccess
                                BattleEventType.MESSAGE -> OrgBlue
                            }
                            Surface(
                                color = PanelSurface,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.55f))
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${entry.eventType.arabicLabel} · ${entry.actorName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = badgeColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (entry.timestampLabel.isNotBlank()) entry.timestampLabel else "جولة ${entry.roundNumber}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(entry.summary, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        val spoofNote = if (msg.trueSenderName != msg.senderName && msg.channel == MessageChannel.PLAYER) {
                            " (مزوّر — المرسل الحقيقي: ${msg.trueSenderName})"
                        } else ""
                        Surface(
                            color = PanelSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(
                                    text = "${msg.senderName}$spoofNote · ${msg.timestampLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (msg.channel == MessageChannel.ALERT) SpyCrimson else ElectricCyan
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(msg.plainText, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            }
                        }
                    }
                }
            }

            NonGameplayOwnerFooter()
        }
    }
}

@Composable
private fun PlayerStatChip(player: PlayerState, modifier: Modifier = Modifier) {
    val isSpy = player.role == Role.SPY
    val roleColor = if (isSpy) SpyCrimson else OrgBlue
    val imageRes = if (isSpy) R.drawable.img_role_spy else R.drawable.img_role_org
    val subDetail = when {
        isSpy && player.puzzlesSolved > 0 -> "حل ${player.puzzlesSolved} ألغاز"
        !isSpy && player.assignedSlotIndices.isNotEmpty() ->
            "خانة: ${player.assignedSlotIndices.joinToString("، ") { "${it + 1}" }}"
        !player.alive -> "مستبعد"
        else -> if (isSpy) "عميل متسلل" else "عميل ميداني"
    }
    Surface(
        color = PanelSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, roleColor.copy(alpha = 0.7f)),
        modifier = modifier.widthIn(min = 128.dp, max = 160.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = if (isSpy) "متجسس" else "عضو المنظمة",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, roleColor, RoundedCornerShape(8.dp))
            )
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(player.name, style = MaterialTheme.typography.labelLarge, color = TextPrimary, maxLines = 1)
                Text(
                    text = if (isSpy) "متجسس" else "منظمة",
                    style = MaterialTheme.typography.labelSmall,
                    color = roleColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subDetail,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (!player.alive) AmberAlert else TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}
