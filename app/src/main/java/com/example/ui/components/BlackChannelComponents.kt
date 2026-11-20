/**
 * شفرة الظل — Shadow Code
 * المالك والمطوّر الرسمي: مجاهد مهند مقدادي (mojahed mohanad migdady)
 */
package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.BlackChannelBroadcastStyle
import com.example.core.BlackChannelConfig
import com.example.ui.theme.*

@Composable
fun BlackChannelSpySection(
    isReadyToOpen: Boolean,
    isOpen: Boolean,
    isPermanentlyClosed: Boolean,
    usesRemaining: Int,
    windowLabel: String,
    testTagPrefix: String,
    onOpenChannel: () -> Unit,
    onOpenComposer: () -> Unit
) {
    val containerColor = when {
        isOpen -> Color(0xFF1A0712)
        isPermanentlyClosed -> Color(0xFF120C14)
        else -> Color(0xFF170B1A)
    }
    val borderColor = when {
        isOpen -> SpyCrimson
        isPermanentlyClosed -> TextMuted.copy(alpha = 0.45f)
        else -> VioletRare.copy(alpha = 0.8f)
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.2.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_section")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isOpen) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = "القناة السوداء",
                        tint = if (isOpen) SpyCrimson else if (isPermanentlyClosed) TextMuted else VioletRare,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "القناة السوداء السرية (للمتجسس فقط)",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isPermanentlyClosed) TextSecondary else TextPrimary
                    )
                }

                if (isOpen) {
                    Surface(
                        color = SpyCrimson.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, SpyCrimson.copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = "$windowLabel · متبقٍ $usesRemaining/${BlackChannelConfig.MAX_USES}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = AmberAlert,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            when {
                isPermanentlyClosed -> {
                    Text(
                        text = "أُغلقت القناة السوداء نهائياً لهذه الجولة حفاظاً على توازن المهمة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.testTag("${testTagPrefix}_closed_label")
                    )
                }
                isOpen -> {
                    Text(
                        text = "أرسل تحذيراً أو سؤال تحقيق يظهر لجميع اللاعبين كأنه صادر من النظام الرسمي للعبة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Button(
                        onClick = onOpenComposer,
                        colors = ButtonDefaults.buttonColors(containerColor = SpyCrimson, contentColor = TextPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("${testTagPrefix}_compose_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("بث رسالة نظام رسمية وهمية", style = MaterialTheme.typography.labelLarge)
                    }
                }
                isReadyToOpen -> {
                    Text(
                        text = "تُفتح لمرة واحدة فقط ($windowLabel — بحد أقصى ${BlackChannelConfig.MAX_USES} رسالة نظام وهمية).",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedButton(
                        onClick = onOpenChannel,
                        border = BorderStroke(1.2.dp, VioletRare),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("${testTagPrefix}_open_button")
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = VioletRare, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("فتح القناة السوداء السرية الآن", color = VioletRare)
                    }
                }
            }
        }
    }
}

@Composable
fun BlackChannelComposerDialog(
    usesRemaining: Int,
    windowLabel: String,
    onDismiss: () -> Unit,
    onSend: (text: String, style: BlackChannelBroadcastStyle) -> Unit
) {
    var selectedStyle by remember { mutableStateOf(BlackChannelBroadcastStyle.SYSTEM_WARNING) }
    var messageText by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = DeepBunker,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.5.dp, SpyCrimson),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .testTag("black_channel_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("القناة السوداء · بث نظام وهمي", style = MaterialTheme.typography.titleLarge, color = SpyCrimson)
                        Text(
                            text = "متبقٍ $usesRemaining استخدامات · $windowLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberAlert
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text("إلغاء", color = TextSecondary)
                    }
                }

                Text("نوع الرسالة الظاهر للمنظمة:", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlackChannelBroadcastStyle.entries.forEach { style ->
                        val active = style == selectedStyle
                        Surface(
                            color = if (active) SpyCrimson else PanelSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (active) SpyCrimson else BorderGlow),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedStyle = style }
                                .testTag("black_channel_style_${style.name}")
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = style.labelAr,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (active) TextPrimary else TextSecondary
                                )
                            }
                        }
                    }
                }

                Text("قوالب سريعة جاهزة (أو اكتب نصك الخاص):", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BlackChannelConfig.presetPayloads.forEachIndexed { idx, (presetStyle, payload) ->
                        Surface(
                            color = PanelSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderGlow),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedStyle = presetStyle
                                    messageText = payload
                                }
                                .testTag("black_channel_preset_$idx")
                        ) {
                            Text(
                                text = payload,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it.take(220) },
                    label = { Text("نص رسالة النظام الوهمية (حد أقصى 220 حرفاً)") },
                    minLines = 3,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("black_channel_message_input")
                )

                // Live preview of how Org sees it
                if (messageText.isNotBlank()) {
                    Surface(
                        color = Color(0xFF2F0D18),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SpyCrimson.copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "معاينة لدى المنظمة — المرسل: ${selectedStyle.senderTitle}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberAlert,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(messageText, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        }
                    }
                }

                Button(
                    onClick = { onSend(messageText.trim(), selectedStyle) },
                    enabled = messageText.trim().length >= 6,
                    colors = ButtonDefaults.buttonColors(containerColor = SpyCrimson, contentColor = TextPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("black_channel_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إرسال كبث نظام رسمي الآن", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
