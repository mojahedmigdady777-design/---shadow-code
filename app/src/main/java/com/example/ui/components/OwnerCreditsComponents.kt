/**
 * شفرة الظل — Shadow Code
 * المالك والمطوّر الرسمي: مجاهد مهند مقدادي (mojahed mohanad migdady)
 */
package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.core.OwnerSignature
import com.example.ui.theme.*

private val RoyalGoldBright = Color(0xFFF3CA7A)
private val RoyalGoldMid = Color(0xFFC89B4F)
private val AntiqueBronze = Color(0xFF7D633A)
private val ObsidianMetalTop = Color(0xFF0B0E14)
private val ObsidianMetalMid = Color(0xFF171E2B)
private val ObsidianMetalBottom = Color(0xFF090C12)

@Composable
fun OwnerSignatureCrestCard(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(18.dp))
            .border(1.6.dp, AntiqueBronze, RoundedCornerShape(18.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(ObsidianMetalMid, ObsidianMetalTop, ObsidianMetalBottom)
                )
            )
            .testTag("owner_signature_crest_card"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Subtle metallic circuit schematics on dark brushed steel
            val circuitGold = RoyalGoldMid.copy(alpha = 0.22f)
            val circuitCyan = ElectricCyan.copy(alpha = 0.14f)
            drawLine(circuitGold, Offset(w * 0.08f, h * 0.28f), Offset(w * 0.24f, h * 0.28f), strokeWidth = 1.5f)
            drawLine(circuitGold, Offset(w * 0.24f, h * 0.28f), Offset(w * 0.28f, h * 0.36f), strokeWidth = 1.5f)
            drawCircle(RoyalGoldBright.copy(alpha = 0.35f), radius = 3.5f, center = Offset(w * 0.28f, h * 0.36f))

            drawLine(circuitCyan, Offset(w * 0.92f, h * 0.30f), Offset(w * 0.74f, h * 0.30f), strokeWidth = 1.5f)
            drawLine(circuitCyan, Offset(w * 0.74f, h * 0.30f), Offset(w * 0.70f, h * 0.38f), strokeWidth = 1.5f)
            drawCircle(ElectricCyan.copy(alpha = 0.35f), radius = 3.5f, center = Offset(w * 0.70f, h * 0.38f))

            drawLine(circuitGold, Offset(w * 0.12f, h * 0.72f), Offset(w * 0.30f, h * 0.72f), strokeWidth = 1.5f)
            drawLine(circuitCyan, Offset(w * 0.88f, h * 0.70f), Offset(w * 0.68f, h * 0.70f), strokeWidth = 1.5f)

            // Outer & inner bronze ornate frame
            val pad = w * 0.032f
            drawRoundRect(
                color = AntiqueBronze.copy(alpha = 0.78f),
                topLeft = Offset(pad, pad),
                size = Size(w - pad * 2f, h - pad * 2f),
                cornerRadius = CornerRadius(14f, 14f),
                style = Stroke(width = 2.4f)
            )
            drawRoundRect(
                color = RoyalGoldMid.copy(alpha = 0.25f),
                topLeft = Offset(pad + 6f, pad + 6f),
                size = Size(w - (pad + 6f) * 2f, h - (pad + 6f) * 2f),
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(width = 1.1f)
            )

            // Sculpted eagle wings flanking top-center & bottom-center keyholes
            drawWingPair(centerX = w * 0.5f, centerY = h * 0.13f, wingSpan = w * 0.26f, pointingUp = false)
            drawWingPair(centerX = w * 0.5f, centerY = h * 0.87f, wingSpan = w * 0.28f, pointingUp = true)

            // Top, bottom, left, right keyhole emblems
            drawKeyholeMedallion(Offset(w * 0.5f, h * 0.13f), radius = h * 0.095f)
            drawKeyholeMedallion(Offset(w * 0.5f, h * 0.87f), radius = h * 0.095f)
            drawKeyholeMedallion(Offset(w * 0.072f, h * 0.5f), radius = h * 0.08f)
            drawKeyholeMedallion(Offset(w * 0.928f, h * 0.5f), radius = h * 0.08f)

            // Corner shields
            val corners = listOf(
                Offset(w * 0.09f, h * 0.16f),
                Offset(w * 0.91f, h * 0.16f),
                Offset(w * 0.09f, h * 0.84f),
                Offset(w * 0.91f, h * 0.84f)
            )
            corners.forEach { pt ->
                drawKeyholeMedallion(pt, radius = h * 0.068f)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth(0.76f)
                .padding(vertical = 18.dp)
        ) {
            Text(
                text = stringResource(R.string.owner_name_ar),
                style = TextStyle(
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    lineHeight = 36.sp,
                    brush = Brush.verticalGradient(listOf(RoyalGoldBright, RoyalGoldMid))
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = OwnerSignature.OWNER_NAME_EN,
                style = TextStyle(
                    fontFamily = CinzelFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.8.sp,
                    color = RoyalGoldMid
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun DrawScope.drawWingPair(
    centerX: Float,
    centerY: Float,
    wingSpan: Float,
    pointingUp: Boolean
) {
    val dir = if (pointingUp) -1f else 1f
    for (side in listOf(-1f, 1f)) {
        for (feather in 0..3) {
            val startX = centerX + side * (wingSpan * 0.14f)
            val endX = centerX + side * (wingSpan * (0.95f - feather * 0.16f))
            val endY = centerY + dir * (feather * 5.5f) - dir * 4f
            drawLine(
                color = AntiqueBronze.copy(alpha = 0.85f - feather * 0.12f),
                start = Offset(startX, centerY),
                end = Offset(endX, endY),
                strokeWidth = 3f - feather * 0.4f,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawKeyholeMedallion(center: Offset, radius: Float) {
    drawCircle(
        color = ObsidianMetalBottom,
        radius = radius,
        center = center
    )
    drawCircle(
        color = AntiqueBronze,
        radius = radius,
        center = center,
        style = Stroke(width = 2.2f)
    )
    drawCircle(
        color = RoyalGoldMid.copy(alpha = 0.45f),
        radius = radius * 0.78f,
        center = center,
        style = Stroke(width = 1f)
    )
    // Keyhole silhouette
    val headR = radius * 0.24f
    drawCircle(
        color = RoyalGoldMid.copy(alpha = 0.82f),
        radius = headR,
        center = Offset(center.x, center.y - radius * 0.14f)
    )
    val keyholeStem = Path().apply {
        moveTo(center.x - headR * 0.55f, center.y - radius * 0.02f)
        lineTo(center.x + headR * 0.55f, center.y - radius * 0.02f)
        lineTo(center.x + headR * 0.9f, center.y + radius * 0.44f)
        lineTo(center.x - headR * 0.9f, center.y + radius * 0.44f)
        close()
    }
    drawPath(keyholeStem, color = RoyalGoldMid.copy(alpha = 0.82f))
}

@Composable
fun HomeOwnerSignatureFooter(
    onOpenCredits: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF0A0F1D),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AntiqueBronze.copy(alpha = 0.7f)),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenCredits)
            .testTag("home_owner_signature_footer")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(RoyalGoldMid.copy(alpha = 0.14f))
                        .border(1.dp, AntiqueBronze, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = stringResource(R.string.credits_section_title),
                        tint = RoyalGoldBright,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.owner_name_ar),
                        style = TextStyle(
                            fontFamily = AmiriFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = RoyalGoldBright
                        )
                    )
                    Text(
                        text = OwnerSignature.OWNER_NAME_EN,
                        style = TextStyle(
                            fontFamily = CinzelFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = RoyalGoldMid
                        )
                    )
                }
            }

            Surface(
                color = RoyalGoldMid.copy(alpha = 0.15f),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AntiqueBronze.copy(alpha = 0.8f))
            ) {
                Text(
                    text = stringResource(R.string.credits_section_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = RoyalGoldBright,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun OwnerCreditsDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = DeepBunker,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, AntiqueBronze),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 540.dp)
                .testTag("owner_credits_dialog")
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = RoyalGoldBright)
                        Text(
                            text = stringResource(R.string.credits_section_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = RoyalGoldBright
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).testTag("close_credits_dialog_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondary)
                    }
                }

                OwnerSignatureCrestCard()

                Surface(
                    color = PanelSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AntiqueBronze.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.owner_role_ar),
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = OwnerSignature.OWNER_NAME_AR,
                            style = TextStyle(
                                fontFamily = AmiriFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = RoyalGoldBright
                            ),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = OwnerSignature.OWNER_NAME_EN,
                            style = TextStyle(
                                fontFamily = CinzelFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 1.4.sp,
                                color = RoyalGoldMid
                            ),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "${OwnerSignature.PROJECT_NAME_AR} · ${OwnerSignature.PROJECT_NAME_EN} · ${OwnerSignature.OFFICIAL_ROLE_EN}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalGoldMid, contentColor = MidnightOps),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("إغلاق", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun NonGameplayOwnerFooter(
    modifier: Modifier = Modifier
) {
    var showCreditsDialog by remember { mutableStateOf(false) }

    HomeOwnerSignatureFooter(
        onOpenCredits = { showCreditsDialog = true },
        modifier = modifier
    )

    if (showCreditsDialog) {
        OwnerCreditsDialog(onDismiss = { showCreditsDialog = false })
    }
}

