package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.online.ConnectivityHelper
import com.example.ui.components.HomeOwnerSignatureFooter
import com.example.ui.components.OwnerCreditsDialog
import com.example.ui.components.TacticalBackground
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    onPlayOffline: () -> Unit,
    onPlayOnline: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var showNoInternetDialog by remember { mutableStateOf(false) }
    var showCreditsDialog by remember { mutableStateOf(false) }

    TacticalBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top bar with Rules & Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "شفرة الظل",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SHADOW CODE",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onOpenRules,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PanelSurface)
                            .border(1.dp, BorderGlow, RoundedCornerShape(12.dp))
                            .testTag("home_rules_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "قواعد اللعبة",
                            tint = ElectricCyan
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PanelSurface)
                            .border(1.dp, BorderGlow, RoundedCornerShape(12.dp))
                            .testTag("home_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "الإعدادات",
                            tint = TextSecondary
                        )
                    }
                }
            }

            // Hero Splash Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.5.dp, ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_splash_banner),
                    contentDescription = "غلاف لعبة شفرة الظل",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xDD050814))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "غرفة العمليات السرية · 4 إلى 8 لاعبين",
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricCyan
                    )
                    Text(
                        text = "فريق يحمي كوداً من 4 خانات، ومتجسس يخترق النظام قبل نفاد الوقت.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }

            // Offline Single-Device Mode Card
            ModeOptionCard(
                title = "وضع الجهاز الواحد (أوفلاين)",
                subtitle = "Offline · Pass & Play",
                description = "يتناوب الأصدقاء على هاتف واحد بالأدوار (10 جولات) — يعمل بدون إنترنت تماماً وبسرعة فائقة.",
                badgeText = "بدون إنترنت · 4-8 لاعبين",
                accentColor = ElectricCyan,
                icon = {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(30.dp)
                    )
                },
                testTag = "play_offline_button",
                onClick = onPlayOffline
            )

            // Online Multiplayer Mode Card
            ModeOptionCard(
                title = "وضع اللعب الجماعي (أونلاين)",
                subtitle = "Online · Real-Time 10:00",
                description = "كل لاعب بهاتفه مع مؤقت لحظي 10 دقائق، تنصّت موجّه، غرف خاصة بكود انضمام، ومزامنة فورية.",
                badgeText = "يتطلب إنترنت · Firebase",
                accentColor = OrgBlue,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = OrgBlue,
                        modifier = Modifier.size(30.dp)
                    )
                },
                testTag = "play_online_button",
                onClick = {
                    if (ConnectivityHelper.hasInternetConnection(context)) {
                        onPlayOnline()
                    } else {
                        showNoInternetDialog = true
                    }
                }
            )

            // Quick Role Cards Showcase
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = PanelSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OrgBlue.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_role_org),
                            contentDescription = "عضو المنظمة",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("المنظمة", style = MaterialTheme.typography.titleMedium, color = OrgBlue)
                            Text("احمِ خانتك واكشف الدخيل", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    color = PanelSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SpyCrimson.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_role_spy),
                            contentDescription = "المتجسس",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("المتجسس", style = MaterialTheme.typography.titleMedium, color = SpyCrimson)
                            Text("اخترق الألغاز وأبطل الشيفرة", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                }
            }

            // Permanent Official Owner Signature Footer
            HomeOwnerSignatureFooter(
                onOpenCredits = { showCreditsDialog = true }
            )
        }
    }

    if (showNoInternetDialog) {
        AlertDialog(
            onDismissRequest = { showNoInternetDialog = false },
            containerColor = PanelSurface,
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "لا يوجد اتصال",
                    tint = AmberAlert,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "لا يوجد اتصال بالإنترنت · No internet connection",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "وضع الأونلاين يتطلب اتصالاً بالإنترنت. يمكنك اللعب فوراً مع أصدقائك على نفس الجهاز عبر الوضع الأوفلاين دون إنترنت.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNoInternetDialog = false
                        onPlayOffline()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightOps),
                    modifier = Modifier.testTag("offline_fallback_button")
                ) {
                    Icon(Icons.Default.Groups, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("العب أوفلاين · Play offline")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNoInternetDialog = false },
                    modifier = Modifier.testTag("dismiss_no_internet_button")
                ) {
                    Text("إغلاق", color = TextSecondary)
                }
            }
        )
    }

    if (showCreditsDialog) {
        OwnerCreditsDialog(
            onDismiss = { showCreditsDialog = false }
        )
    }
}

@Composable
private fun ModeOptionCard(
    title: String,
    subtitle: String,
    description: String,
    badgeText: String,
    accentColor: Color,
    icon: @Composable () -> Unit,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        color = PanelSurface,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, accentColor.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor
                    )
                }
            }
        }
    }
}
