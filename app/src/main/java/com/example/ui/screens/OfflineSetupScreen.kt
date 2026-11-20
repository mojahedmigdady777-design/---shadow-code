package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
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
import com.example.core.Role
import com.example.offline.OfflineUiState
import com.example.ui.components.FlippableRoleCard
import com.example.ui.components.NonGameplayOwnerFooter
import com.example.ui.components.SecretCodeSlotsBar
import com.example.ui.components.TacticalBackground
import com.example.ui.theme.*

@Composable
fun OfflineSetupScreen(
    state: OfflineUiState,
    onSetPlayerCount: (Int) -> Unit,
    onUpdatePlayerName: (Int, String) -> Unit,
    onToggleTwoSpies: (Boolean) -> Unit,
    onStartGame: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp).testTag("offline_setup_back")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = ElectricCyan)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("إعداد جولة الجهاز الواحد", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text("أوفلاين بالكامل · لعب بالأدوار (10 جولات)", style = MaterialTheme.typography.bodyMedium, color = ElectricCyan)
                }
            }

            // Player Count Selector (4..8)
            Surface(
                color = PanelSurface,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("عدد اللاعبين (4 إلى 8):", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (count in 4..8) {
                            val selected = state.setupNames.size == count
                            Surface(
                                color = if (selected) ElectricCyan else ElevatedCard,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clickable { onSetPlayerCount(count) }
                                    .testTag("player_count_$count")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$count",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = if (selected) MidnightOps else TextPrimary
                                    )
                                }
                            }
                        }
                    }

                    if (state.setupNames.size >= 7) {
                        HorizontalDivider(color = BorderGlow)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("خيار متجسسين اثنين", style = MaterialTheme.typography.titleMedium, color = SpyCrimson)
                                Text("متاح عند 7 أو 8 لاعبين لرفع التحدي", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                            Switch(
                                checked = state.twoSpiesEnabled,
                                onCheckedChange = onToggleTwoSpies,
                                modifier = Modifier.testTag("two_spies_switch")
                            )
                        }
                    }
                }
            }

            // Editable Player Names
            Surface(
                color = PanelSurface,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("أسماء العملاء:", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    state.setupNames.forEachIndexed { idx, name ->
                        OutlinedTextField(
                            value = name,
                            onValueChange = { onUpdatePlayerName(idx, it) },
                            label = { Text("اللاعب ${idx + 1}") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("player_name_input_$idx")
                        )
                    }
                }
            }

            Button(
                onClick = onStartGame,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightOps),
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("start_offline_match_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("توزيع الأدوار وبدء المهمة", style = MaterialTheme.typography.titleMedium)
            }

            NonGameplayOwnerFooter()
        }
    }
}

@Composable
fun OfflineRoleRevealScreen(
    state: OfflineUiState,
    isRevealed: Boolean,
    onReveal: () -> Unit,
    onConfirmAndPass: () -> Unit
) {
    val player = state.currentPlayer ?: return
    var localFlipped by remember(player.id, isRevealed) { mutableStateOf(isRevealed) }

    TacticalBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp)
                .widthIn(max = 520.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "بطاقة العميل ${state.currentPlayerIndex + 1} من ${state.players.size}",
                style = MaterialTheme.typography.labelLarge,
                color = AmberAlert
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "مرّر الجهاز سراً إلى:",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = player.name,
                style = MaterialTheme.typography.displayLarge,
                color = ElectricCyan
            )
            Spacer(modifier = Modifier.height(18.dp))

            FlippableRoleCard(
                flipped = localFlipped,
                onFlipClick = {
                    if (!isRevealed) {
                        onReveal()
                    } else {
                        localFlipped = !localFlipped
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(385.dp)
            ) {
                val isSpy = player.role == Role.SPY
                val allySpies = if (isSpy) state.allySpiesFor(player.id) else emptyList()
                val accent = if (isSpy) SpyCrimson else OrgBlue
                val imageRes = if (isSpy) R.drawable.img_role_spy else R.drawable.img_role_org

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PanelSurface)
                        .border(2.dp, accent, RoundedCornerShape(22.dp))
                ) {
                    Image(
                        painter = painterResource(id = imageRes),
                        contentDescription = if (isSpy) "أنت المتجسس" else "أنت عضو المنظمة",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color(0xE6050814))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isSpy) "أنت المتجسس (The Spy)" else "أنت عضو في المنظمة",
                            style = MaterialTheme.typography.titleLarge,
                            color = accent
                        )
                        if (isSpy) {
                            Text(
                                text = "اكشف الخانات الأربع عبر محطة الاختراق وأدخل الكود دون أن يكتشفوك.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                            if (allySpies.isNotEmpty()) {
                                Surface(
                                    color = SpyCrimson.copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SpyCrimson.copy(alpha = 0.8f)),
                                    modifier = Modifier.fillMaxWidth().testTag("offline_ally_spy_reveal_box")
                                ) {
                                    Text(
                                        text = "زميلك المتجسس: ${allySpies.joinToString("، ") { it.name }}\nتتشاركان الخانات المكشوفة ونتائج التنصت الموجّه تلقائياً",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            SecretCodeSlotsBar(
                                codeSlots = state.codeSlots,
                                revealedIndices = state.revealedSlotIndices,
                                isSpy = true
                            )
                        } else {
                            if (player.assignedSlotIndices.isEmpty()) {
                                Text(
                                    text = "عميل ميداني — لا تحمل خانة كود. راقب البلاغات واكشف المتجسس!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text(
                                    text = "تحمل من الكود السري (احفظها ولا تكشفها علناً):",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center
                                )
                                SecretCodeSlotsBar(
                                    codeSlots = state.codeSlots,
                                    revealedIndices = emptySet(),
                                    isSpy = false,
                                    assignedIndices = player.assignedSlotIndices
                                )
                            }
                            Text(
                                text = "مفتاح التشفير النشط: ${state.cipherType.arabicName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            if (isRevealed) {
                Button(
                    onClick = onConfirmAndPass,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightOps),
                    modifier = Modifier.fillMaxWidth().height(54.dp).testTag("confirm_role_seen_button")
                ) {
                    Text("أخفِ الدور ومرّر الجهاز", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                OutlinedButton(
                    onClick = onReveal,
                    modifier = Modifier.fillMaxWidth().height(54.dp).testTag("tap_reveal_role_button")
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = ElectricCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("اضغط هنا لكشف دورك السري", color = ElectricCyan)
                }
            }
        }
    }
}
