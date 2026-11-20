package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.AppSettings
import com.example.core.OwnerSignature
import com.example.core.RareCardType
import com.example.core.Role
import com.example.ui.components.NonGameplayOwnerFooter
import com.example.ui.components.OwnerSignatureCrestCard
import com.example.ui.components.TacticalBackground
import com.example.ui.theme.*

@Composable
fun RulesScreen(onBack: () -> Unit) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp).testTag("rules_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = ElectricCyan)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("دليل العمليات والقواعد", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            }

            Image(
                painter = painterResource(id = R.drawable.img_help),
                contentDescription = "دليل المساعدة",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, OrgBlue, RoundedCornerShape(18.dp))
            )

            RuleSectionCard(
                title = "1. الفكرة وتوزيع الأسرار",
                body = "يجتمع 4 إلى 8 لاعبين داخل غرفة عمليات سرية. يمتلك النظام كوداً من 4 خانات يوزّع على أعضاء المنظمة (كل عضو يرى خانة أو خانتين فقط، ولا أحد يملك الكود كاملاً). أما المتجسس فيرى الخانات مغلقة [?] ويحاول اختراقها عبر الألغاز."
            )
            RuleSectionCard(
                title = "2. القناة الأولى: البلاغات التكتيكية (20 قالباً)",
                body = "يمنع الكلام العشوائي في الشاشة وتُبنى الرسائل من 20 قالباً في 4 فئات (6 خيارات عشوائية لكل فراغ مع زر تحديث). كل كلمة تُرسل تُحذف لباقي الجولة منعاً للتكرار! كل رسالة تمنح المتجسس شحنة طاقة (+1)، وتظهر مشفرة له إلا إذا كان يتنصّت على مرسلها."
            )
            RuleSectionCard(
                title = "3. القناة الثانية: النظام الآلي (كل 60 ثانية / كل جولة)",
                body = "يرسل النظام تلميحات ذكية للمتجسس (عن خوارزمية التشفير أو حذف خيارات خاطئة)، ومساعدات للمنظمة: كاشف النشاط، درع تغيير التردد (يبدّل قيمة خانتك مرة واحدة)، واستجواب النظام السريع (سؤال موحد عن بلاغ سابق يكشف المتجسس إن أخطأ)."
            )
            RuleSectionCard(
                title = "4. الألغاز الستة والبطاقات النادرة",
                body = "يواجه المتجسس 6 أنواع ألغاز (فك الشفرة، مصفوفات المنطق، شبكة الاستنتاج، الذاكرة السريعة، توصيل الترددات، والخوارزمية المتسلسلة). الفشل يغلق المحطة مؤقتاً. عند الدقيقة 07:00 و03:00 (الجولتان 3 و6 أوفلاين) تُوزّع بطاقات نادرة مثل الفخ الخداعي، تجميد النظام، الاختراق القسري، وتزوير الهوية."
            )
            RuleSectionCard(
                title = "5. التحقيق الطارئ وشروط الفوز",
                body = "لكل لاعب طلب تحقيق طارئ مرة واحدة: يتوقف الوقت 45 ثانية للنقاش الوجاهي ثم تصويت سري (مع خيار تخطي). إدانة المتجسس تمنح الفوز للمنظمة. استبعاد بريء يخرجه من الجولة ويكشف للمتجسس خانة كاملة مجاناً! إذا جمع المتجسس الخانات الأربع وأدخلها خلال 10 ثوانٍ يفوز."
            )

            Text("معرض البطاقات التكتيكية وبطاقات اللاعبين:", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CardGalleryItem(R.drawable.img_card_back, "ظهر البطاقة", "تخفي دورك السري عن الآخرين", ElectricCyan)
                CardGalleryItem(R.drawable.img_role_org, "عضو المنظمة", "يحمل 1-2 خانة من الكود", OrgBlue)
                CardGalleryItem(R.drawable.img_role_spy, "المتجسس", "يخترق الألغاز ويتنصت سراً", SpyCrimson)
                CardGalleryItem(R.drawable.img_card_info, "معلومات", "بلاغات القوالب التكتيكية", ElectricCyan)
                CardGalleryItem(R.drawable.img_card_wiretap, "التنصت الموجّه", "مؤقت 60ث حصري للمتجسس", AmberAlert)
                CardGalleryItem(R.drawable.img_card_puzzle, "لغز التحدي", "6 ألغاز لفتح خانات الكود", VioletRare)
                CardGalleryItem(R.drawable.img_card_ability, "القدرات", "درع تغيير التردد والمستشعرات", EmeraldSuccess)
                CardGalleryItem(R.drawable.img_card_rare, "أداة نادرة", "توزّع في المحطات الحاسمة", VioletRare)
            }

            Surface(
                color = PanelSurface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VioletRare.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("البطاقات النادرة الست في اللعبة:", style = MaterialTheme.typography.titleMedium, color = VioletRare)
                    RareCardType.entries.forEach { card ->
                        val teamLabel = if (card.forRole == Role.SPY) "المتجسس" else "المنظمة"
                        val teamColor = if (card.forRole == Role.SPY) SpyCrimson else ElectricCyan
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("• ${card.arabicName} ($teamLabel)", style = MaterialTheme.typography.labelLarge, color = teamColor)
                            Text(card.arabicDescription, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }

            Text(
                text = "المطورون وحقوق الملكية · Credits",
                style = MaterialTheme.typography.titleMedium,
                color = ElectricCyan
            )
            OwnerSignatureCrestCard()
            Surface(
                color = PanelSurface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7D633A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rules_credits_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = OwnerSignature.OFFICIAL_ROLE_AR,
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
                            color = Color(0xFFF3CA7A)
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
                            color = Color(0xFFC89B4F)
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
        }
    }
}

@Composable
private fun CardGalleryItem(
    drawableRes: Int,
    title: String,
    subtitle: String,
    accent: Color
) {
    Surface(
        color = PanelSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
        modifier = Modifier.width(126.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Text(title, style = MaterialTheme.typography.labelLarge, color = accent, textAlign = TextAlign.Center, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun RuleSectionCard(title: String, body: String) {
    Surface(
        color = PanelSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
            Spacer(modifier = Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        }
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onToggleSound: (Boolean) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onToggleEnglish: (Boolean) -> Unit,
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
                    modifier = Modifier.size(48.dp).testTag("settings_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = ElectricCyan)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("الإعدادات · Settings", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            }

            Image(
                painter = painterResource(id = R.drawable.img_settings),
                contentDescription = "إعدادات التطبيق",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, BorderGlow, RoundedCornerShape(18.dp))
            )

            Surface(
                color = PanelSurface,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingSwitchRow(
                        title = "التنبيهات الصوتية التكتيكية",
                        subtitle = "نغمة خفيفة عند اختراق الخانات والإنذارات (محلية بالكامل)",
                        checked = settings.soundEnabled,
                        testTag = "setting_sound_switch",
                        onCheckedChange = onToggleSound
                    )
                    HorizontalDivider(color = BorderGlow)
                    SettingSwitchRow(
                        title = "الاهتزاز واللمس التفاعلي",
                        subtitle = "اهتزاز خفيف عند نجاح أو فشل الاختراق والتصويت",
                        checked = settings.hapticsEnabled,
                        testTag = "setting_haptics_switch",
                        onCheckedChange = onToggleHaptics
                    )
                    HorizontalDivider(color = BorderGlow)
                    SettingSwitchRow(
                        title = "العناوين الثنائية (عربي + English)",
                        subtitle = "إظهار المسميات الإنجليزية المساعدة بجانب العربية",
                        checked = settings.showEnglishSubtitles,
                        testTag = "setting_bilingual_switch",
                        onCheckedChange = onToggleEnglish
                    )
                }
            }

            NonGameplayOwnerFooter()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
