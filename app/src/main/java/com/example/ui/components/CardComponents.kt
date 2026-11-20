package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.core.CipherType
import com.example.core.RareCardType
import com.example.core.Role
import com.example.ui.theme.*

data class RosterPlayerUi(
    val id: String,
    val name: String,
    val seatNumber: Int,
    val alive: Boolean = true,
    val isCurrentTurnOrMe: Boolean = false,
    val isHost: Boolean = false,
    val usedEmergency: Boolean = false,
    val afkWarning: Boolean = false,
    val deserted: Boolean = false,
    val isAllySpy: Boolean = false
)

@Composable
fun TacticalCardThumb(
    drawableRes: Int,
    contentDescription: String,
    borderColor: Color = ElectricCyan,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = drawableRes),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(width = 36.dp, height = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
    )
}

@Composable
fun TacticalPlayerRosterStrip(
    players: List<RosterPlayerUi>,
    title: String = "كروت العملاء في الغرفة",
    modifier: Modifier = Modifier
) {
    if (players.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "$title (${players.count { it.alive }}/${players.size} نشط)",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            players.forEach { p ->
                val borderCol = when {
                    p.deserted || !p.alive -> SpyCrimson.copy(alpha = 0.55f)
                    p.isAllySpy -> SpyCrimson
                    p.afkWarning -> AmberAlert
                    p.isCurrentTurnOrMe -> ElectricCyan
                    else -> BorderGlow
                }
                val bgCol = when {
                    p.deserted || !p.alive -> Color(0xFF210E14)
                    p.isAllySpy -> Color(0xFF2C0D18)
                    p.isCurrentTurnOrMe -> Color(0xFF0A243D)
                    else -> PanelSurface
                }
                val statusLabel = when {
                    p.deserted -> "منسحب"
                    !p.alive -> "مستبعد"
                    p.isAllySpy -> "متجسس زميل"
                    p.afkWarning -> "خامل ⚠"
                    p.isCurrentTurnOrMe -> "أنت / نشط"
                    else -> "في الميدان"
                }
                val statusColor = when {
                    p.deserted || !p.alive -> SpyCrimson
                    p.isAllySpy -> SpyCrimson
                    p.afkWarning -> AmberAlert
                    p.isCurrentTurnOrMe -> ElectricCyan
                    else -> EmeraldSuccess
                }

                Surface(
                    color = bgCol,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, borderCol),
                    modifier = Modifier
                        .widthIn(min = 108.dp, max = 148.dp)
                        .testTag("player_card_${p.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(statusColor.copy(alpha = 0.18f))
                                .border(1.dp, statusColor.copy(alpha = 0.7f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!p.alive || p.deserted) {
                                Icon(
                                    imageVector = Icons.Default.PersonOff,
                                    contentDescription = statusLabel,
                                    tint = SpyCrimson,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Text(
                                    text = "${p.seatNumber}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = statusColor
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = p.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (p.alive && !p.deserted) TextPrimary else TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (p.isHost) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "مضيف الغرفة",
                                        tint = AmberAlert,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            Text(
                                text = statusLabel + if (p.usedEmergency && p.alive) " · ⚑" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RareCardHandRow(
    cards: List<RareCardType>,
    testTagPrefix: String,
    onCardClick: (RareCardType) -> Unit,
    modifier: Modifier = Modifier
) {
    if (cards.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = VioletRare,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "بطاقاتك التكتيكية النادرة (${cards.size}):",
                style = MaterialTheme.typography.labelLarge,
                color = VioletRare
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            cards.forEachIndexed { index, card ->
                val accent = if (card.forRole == Role.SPY) SpyCrimson else ElectricCyan
                Surface(
                    color = Color(0xFF1F1136),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.3.dp, VioletRare),
                    modifier = Modifier
                        .widthIn(min = 195.dp, max = 255.dp)
                        .heightIn(min = 68.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onCardClick(card) }
                        .testTag("${testTagPrefix}${card.name}")
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TacticalCardThumb(
                            drawableRes = R.drawable.img_card_rare,
                            contentDescription = card.arabicName,
                            borderColor = accent
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = card.arabicName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = VioletRare,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "#${index + 1}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent
                                )
                            }
                            Text(
                                text = card.arabicDescription,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "اضغط للمعاينة والتفعيل",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RareCardInspectDialog(
    card: RareCardType,
    canActivate: Boolean,
    disabledReason: String? = null,
    onConfirmActivate: () -> Unit,
    onDismiss: () -> Unit
) {
    val isComposerCard = card == RareCardType.DECOY_TRAP || card == RareCardType.IDENTITY_SPOOF
    val accent = if (card.forRole == Role.SPY) SpyCrimson else ElectricCyan

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = DeepBunker,
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, VioletRare),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = accent.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (card.forRole == Role.SPY) "بطاقة متجسس نادرة" else "بطاقة منظمة نادرة",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondary)
                    }
                }

                Image(
                    painter = painterResource(id = R.drawable.img_card_rare),
                    contentDescription = card.arabicName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 136.dp, height = 180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, VioletRare, RoundedCornerShape(16.dp))
                )

                Text(
                    text = card.arabicName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = VioletRare,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = card.arabicDescription,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                if (!canActivate && !disabledReason.isNullOrBlank()) {
                    Surface(
                        color = Color(0xFF331018),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SpyCrimson.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = disabledReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = AmberAlert,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("إلغاء", color = TextSecondary)
                    }
                    Button(
                        onClick = onConfirmActivate,
                        enabled = canActivate,
                        colors = ButtonDefaults.buttonColors(containerColor = VioletRare, contentColor = TextPrimary),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp)
                            .testTag("confirm_activate_rare_card_button")
                    ) {
                        Text(
                            text = if (isComposerCard) "إرفاق بالبلاغ" else "تفعيل البطاقة الآن",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SlotShieldSelectorSection(
    assignedSlots: List<Int>,
    testTagPrefix: String,
    onActivateShield: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (assignedSlots.isEmpty()) return
    Surface(
        color = Color(0xFF0A2A24),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, EmeraldSuccess.copy(alpha = 0.7f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TacticalCardThumb(
                    drawableRes = R.drawable.img_card_ability,
                    contentDescription = "بطاقة درع تغيير التردد",
                    borderColor = EmeraldSuccess
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "درع تغيير التردد (متاح لمرة واحدة)",
                        style = MaterialTheme.typography.titleMedium,
                        color = EmeraldSuccess
                    )
                    Text(
                        text = "يبدّل قيمة خانتك فوراً ويبطل كشف المتجسس لها.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                assignedSlots.forEachIndexed { index, slotIdx ->
                    val tag = if (index == 0) testTagPrefix else "${testTagPrefix}_$slotIdx"
                    Button(
                        onClick = { onActivateShield(slotIdx) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = MidnightOps),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag(tag)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حماية الخانة ${slotIdx + 1}", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerSecretRoleCardDialog(
    playerName: String,
    role: Role,
    codeSlots: List<String>,
    assignedSlotIndices: List<Int>,
    cipherType: CipherType,
    revealedIndices: Set<Int> = emptySet(),
    allySpyNames: List<String> = emptyList(),
    initialFlipped: Boolean = false,
    onDismiss: () -> Unit
) {
    var flipped by remember { mutableStateOf(initialFlipped) }
    val isSpy = role == Role.SPY
    val accent = if (isSpy) SpyCrimson else OrgBlue
    val imageRes = if (isSpy) R.drawable.img_role_spy else R.drawable.img_role_org

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = DeepBunker,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, accent.copy(alpha = 0.7f)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Badge, contentDescription = null, tint = ElectricCyan)
                        Text("بطاقة العميل السرية · $playerName", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondary)
                    }
                }

                FlippableRoleCard(
                    flipped = flipped,
                    onFlipClick = { flipped = !flipped },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(370.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(PanelSurface)
                            .border(2.dp, accent, RoundedCornerShape(22.dp))
                    ) {
                        Image(
                            painter = painterResource(id = imageRes),
                            contentDescription = if (isSpy) "بطاقة المتجسس" else "بطاقة عضو المنظمة",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color(0xE6050814))
                                .padding(14.dp),
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
                                    text = "اكشف الخانات الأربع عبر محطة الاختراق والتنصت الموجّه، وأدخل الكود دون أن يكتشفوك.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center
                                )
                                if (allySpyNames.isNotEmpty()) {
                                    Surface(
                                        color = SpyCrimson.copy(alpha = 0.22f),
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, SpyCrimson.copy(alpha = 0.8f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "شريكك المتجسس: ${allySpyNames.joinToString("، ")} · تتشاركان الخانات المكشوفة والتنصت",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextPrimary,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                                SecretCodeSlotsBar(
                                    codeSlots = codeSlots,
                                    revealedIndices = revealedIndices,
                                    isSpy = true
                                )
                            } else {
                                if (assignedSlotIndices.isEmpty()) {
                                    Text(
                                        text = "عميل ميداني — لا تحمل خانة كود. راقب البلاغات واكشف المتجسس!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary,
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    Text(
                                        text = "خانتك السرية في الكود (احفظها ولا تكشفها علناً):",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary,
                                        textAlign = TextAlign.Center
                                    )
                                    SecretCodeSlotsBar(
                                        codeSlots = codeSlots,
                                        revealedIndices = emptySet(),
                                        isSpy = false,
                                        assignedIndices = assignedSlotIndices
                                    )
                                }
                                Text(
                                    text = "خوارزمية الجولة: ${cipherType.arabicName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { flipped = !flipped },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("toggle_role_card_flip_button")
                    ) {
                        Icon(
                            imageVector = if (flipped) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = ElectricCyan
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (flipped) "إخفاء البطاقة" else "كشف البطاقة", color = ElectricCyan)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightOps),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("close_role_card_dialog_button")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("متابعة المهمة")
                    }
                }
            }
        }
    }
}
