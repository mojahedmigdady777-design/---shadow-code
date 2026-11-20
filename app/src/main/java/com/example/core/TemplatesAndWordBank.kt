package com.example.core

import kotlin.random.Random

enum class SlotKind(val label: String) {
    ACTION("فعل أمني"),
    LOCATION("الموقع"),
    DURATION("المدة"),
    THREAT("مستوى الخطر"),
    REASON("السبب"),
    SUSPICION("الصفة المريبة"),
    AGENT_NAME("اسم العميل"),
    SECTOR("القطاع")
}

data class MessageTemplate(
    val id: String,
    val category: MessageCategory,
    val parts: List<String>, // size = slots.size + 1
    val slots: List<SlotKind>
) {
    fun render(selectedWords: List<String>): String {
        val sb = StringBuilder()
        for (i in slots.indices) {
            sb.append(parts[i])
            sb.append(selectedWords.getOrElse(i) { "[${slots[i].label}]" })
        }
        sb.append(parts.last())
        return sb.toString().trim().replace("  ", " ")
    }
}

object TemplatesAndWordBank {

    val templates: List<MessageTemplate> = listOf(
        // 1. Field Orders (5)
        MessageTemplate(
            id = "FO1",
            category = MessageCategory.FIELD_ORDERS,
            parts = listOf("", " في ", " لمدة ", ""),
            slots = listOf(SlotKind.ACTION, SlotKind.LOCATION, SlotKind.DURATION)
        ),
        MessageTemplate(
            id = "FO2",
            category = MessageCategory.FIELD_ORDERS,
            parts = listOf("أمر عاجل: ", " داخل ", " خلال ", ""),
            slots = listOf(SlotKind.ACTION, SlotKind.SECTOR, SlotKind.DURATION)
        ),
        MessageTemplate(
            id = "FO3",
            category = MessageCategory.FIELD_ORDERS,
            parts = listOf("توجيه ميداني لـ ", ": تنفيذ ", " في ", ""),
            slots = listOf(SlotKind.AGENT_NAME, SlotKind.ACTION, SlotKind.LOCATION)
        ),
        MessageTemplate(
            id = "FO4",
            category = MessageCategory.FIELD_ORDERS,
            parts = listOf("بدء ", " حول ", " وتأمين ", ""),
            slots = listOf(SlotKind.ACTION, SlotKind.LOCATION, SlotKind.SECTOR)
        ),
        MessageTemplate(
            id = "FO5",
            category = MessageCategory.FIELD_ORDERS,
            parts = listOf("أولوية قصوى: ", " في ", " قبل انتهاء ", ""),
            slots = listOf(SlotKind.ACTION, SlotKind.SECTOR, SlotKind.DURATION)
        ),

        // 2. Status & Alert Reports (5)
        MessageTemplate(
            id = "SA1",
            category = MessageCategory.STATUS_ALERTS,
            parts = listOf("", " في ", " بسبب ", ""),
            slots = listOf(SlotKind.THREAT, SlotKind.LOCATION, SlotKind.REASON)
        ),
        MessageTemplate(
            id = "SA2",
            category = MessageCategory.STATUS_ALERTS,
            parts = listOf("رصد ", " داخل ", " نتيجة ", ""),
            slots = listOf(SlotKind.THREAT, SlotKind.SECTOR, SlotKind.REASON)
        ),
        MessageTemplate(
            id = "SA3",
            category = MessageCategory.STATUS_ALERTS,
            parts = listOf("بلاغ حالة: ", " منذ ", " عند ", ""),
            slots = listOf(SlotKind.THREAT, SlotKind.DURATION, SlotKind.LOCATION)
        ),
        MessageTemplate(
            id = "SA4",
            category = MessageCategory.STATUS_ALERTS,
            parts = listOf("تحذير استخباراتي في ", ": ", " بسبب ", ""),
            slots = listOf(SlotKind.SECTOR, SlotKind.THREAT, SlotKind.REASON)
        ),
        MessageTemplate(
            id = "SA5",
            category = MessageCategory.STATUS_ALERTS,
            parts = listOf("استقرار مؤقت في ", " بعد ", " رغم ", ""),
            slots = listOf(SlotKind.LOCATION, SlotKind.ACTION, SlotKind.REASON)
        ),

        // 3. Camouflage & Accusation (5) - Gender-neutral phrasing
        MessageTemplate(
            id = "CA1",
            category = MessageCategory.CAMOUFLAGE_ACCUSATION,
            parts = listOf("تمت ملاحظة ", " لدى ", " في ", ""),
            slots = listOf(SlotKind.SUSPICION, SlotKind.AGENT_NAME, SlotKind.LOCATION)
        ),
        MessageTemplate(
            id = "CA2",
            category = MessageCategory.CAMOUFLAGE_ACCUSATION,
            parts = listOf("تصرّف ", " بـ ", " قرب ", ""),
            slots = listOf(SlotKind.AGENT_NAME, SlotKind.SUSPICION, SlotKind.SECTOR)
        ),
        MessageTemplate(
            id = "CA3",
            category = MessageCategory.CAMOUFLAGE_ACCUSATION,
            parts = listOf("مؤشر ", " حول ", " منذ ", ""),
            slots = listOf(SlotKind.SUSPICION, SlotKind.AGENT_NAME, SlotKind.DURATION)
        ),
        MessageTemplate(
            id = "CA4",
            category = MessageCategory.CAMOUFLAGE_ACCUSATION,
            parts = listOf("تحفّظ أمني على ", " بسبب ", " عند ", ""),
            slots = listOf(SlotKind.AGENT_NAME, SlotKind.SUSPICION, SlotKind.LOCATION)
        ),
        MessageTemplate(
            id = "CA5",
            category = MessageCategory.CAMOUFLAGE_ACCUSATION,
            parts = listOf("رصد تواصل غير مبرّر لـ ", " مع ظهور ", " داخل ", ""),
            slots = listOf(SlotKind.AGENT_NAME, SlotKind.SUSPICION, SlotKind.SECTOR)
        ),

        // 4. Keys & Sectors (5)
        MessageTemplate(
            id = "KS1",
            category = MessageCategory.KEYS_SECTORS,
            parts = listOf("مفتاح ", " تم نقله إلى ", " لمدة ", ""),
            slots = listOf(SlotKind.SECTOR, SlotKind.LOCATION, SlotKind.DURATION)
        ),
        MessageTemplate(
            id = "KS2",
            category = MessageCategory.KEYS_SECTORS,
            parts = listOf("تشفير ", " محفوظ في ", " بإشراف ", ""),
            slots = listOf(SlotKind.SECTOR, SlotKind.LOCATION, SlotKind.AGENT_NAME)
        ),
        MessageTemplate(
            id = "KS3",
            category = MessageCategory.KEYS_SECTORS,
            parts = listOf("فحص بوابة ", " كشف ", " حول ", ""),
            slots = listOf(SlotKind.SECTOR, SlotKind.REASON, SlotKind.LOCATION)
        ),
        MessageTemplate(
            id = "KS4",
            category = MessageCategory.KEYS_SECTORS,
            parts = listOf("إعادة تدوير شفرة ", " نحو ", " قبل ", ""),
            slots = listOf(SlotKind.SECTOR, SlotKind.LOCATION, SlotKind.DURATION)
        ),
        MessageTemplate(
            id = "KS5",
            category = MessageCategory.KEYS_SECTORS,
            parts = listOf("عزل ", " عن ", " بعد رصد ", ""),
            slots = listOf(SlotKind.SECTOR, SlotKind.LOCATION, SlotKind.THREAT)
        )
    )

    private val actionsBank: List<String> = listOf(
        "تأمين محيط", "إغلاق بوابات", "تفتيش دقيق", "مراقبة صامتة", "مسح إلكتروني",
        "تفعيل حواجز", "تشويش ترددي", "تغيير مفاتيح", "فحص بصمات", "تطويق أمني",
        "قطع اتصالات", "حجر احترازي", "تمشيط شامل", "مراجعة سجلات", "تشفير فوري",
        "حراسة مشددة", "تبديل ورديات", "تعليق دخول", "رصد حراري", "تتبع إشارة",
        "عزل خوادم", "تحديث جدار الحماية", "إلغاء تصاريح", "فحص كابلات", "تثبيت مستشعرات",
        "تمويه مرئي", "إخلاء جزئي", "تدقيق هويات", "حماية مركزية", "دورية سريعة",
        "استعادة نسخة", "تصفية قنوات", "إقفال طوارئ", "مراقبة مداخل", "تحصين مخازن",
        "فصل طاقة احتياطية", "تأكيد صلاحيات", "إسكات أجهزة", "توجيه كاميرات", "حجب ترددات",
        "اختبار اختراق", "مسح مغناطيسي", "إعادة ضبط بوابة", "تفعيل صافرات صامتة", "ملاحقة أثر",
        "تبديل قنوات", "فرز بيانات", "توثيق حركة", "تجميد حسابات", "رفع جاهزية",
        "كشف تنصت", "فحص طرود", "تحليل طيفي", "تضليل راداري", "تأمين ممر",
        "إغلاق نفق", "حماية مولد", "ضبط توقيت", "تشغيل مرشحات", "تنقية هواء",
        "قفل مصاعد", "تعطيل لاسلكي", "تأمين خزنة", "نقل ملفات", "مسح ليلي",
        "ترحيل بيانات", "فحص أجهزة", "تغيير كلمات سر", "مزامنة ساعات", "حصر متواجدين",
        "مراقبة أسطح", "تدعيم أبواب", "كشف معادن", "تثبيت كود", "تقليص صلاحيات",
        "إغلاق نوافذ", "فحص خزائن", "تبديل بطاقات", "مراجعة كاميرات", "إخفاء مسارات",
        "تشغيل مولد", "تفكيك فخ", "اختبار إنذار", "إعادة توجيه", "تعزيز حراسة",
        "فحص هوائيات", "تأمين سطح", "ضبط تردد", "مراقبة شبكة", "قطع تغذية",
        "تشفير صوتي", "تأمين أرشيف", "حراسة مزدوجة", "تنظيف ذاكرة", "إغلاق صمامات",
        "تفتيش سيارات", "تفعيل رادار", "مراقبة طارئة", "إيقاف مصاعد", "تثبيت دفاعات"
    )

    private val locationsBank: List<String> = listOf(
        "القبو الشرقي", "غرفة الخوادم", "الممر السري", "برج المراقبة", "المختبر المركزي",
        "بوابة الشحن", "مخزن الذخيرة", "قاعة الاجتماعات", "نفق الصيانة", "السطح الشمالي",
        "مركز القيادة", "مكتب الأرشيف", "محطة الطاقة", "مرآب الطوارئ", "المصعد المعزول",
        "جناح الاتصالات", "غرفة التشفير", "الساحة الخلفية", "منصة الهبوط", "المستودع الغربي",
        "خزانة الملفات", "نقطة التفتيش", "الممر الزجاجي", "غرفة التبريد", "برج الإرسال",
        "محور البيانات", "المدخل الرئيسي", "القطاع السفلي", "قاعة الرادار", "مكتب التحليل",
        "حظيرة الطائرات", "المعمل الكيميائي", "غرفة التحكم", "الرصيف البحري", "بوابة الطوارئ",
        "نفق التهوية", "صالة الاستقبال", "الجناح الطبي", "مجمع المولدات", "برج التبريد",
        "قاعة الخرائط", "مخزن المعدات", "غرفة الاستجواب", "السور الجنوبي", "منطقة العزل",
        "المكتبة السرية", "سلم الهروب", "نقطة المراقبة ب", "القبو الأوسط", "ممر الكابلات",
        "محطة التحويل", "بوابة الكراج", "غرفة الأقمار", "الجناح الإداري", "مركز البث",
        "الخزنة المحصنة", "الرصيف الثالث", "قاعة العمليات", "مكتب الأمن", "صالة التدريب",
        "الممر الضيق", "مخزن الوقود", "غرفة المراقبة 4", "برج الإشارة", "القبو الجنوبي",
        "ساحة التحميل", "معمل البصريات", "نقطة الحراسة", "الدهليز القديم", "محطة الضخ",
        "غرفة المحولات", "منصة الإطلاق", "المستودع المبرد", "مركز الطوارئ", "ممر الخدمة",
        "قاعة المؤتمرات", "بوابة المختبر", "الجناح السري", "غرفة الطباعة", "برج الاتصالات",
        "المدرج الغربي", "حاجز التفتيش", "قاعة الصيانة", "مخزن البطاريات", "نفق الطوارئ",
        "غرفة الاختبار", "مركز الحوسبة", "السور الشمالي", "بوابة الأرشيف", "السطح الشرقي",
        "غرفة القنوات", "مكتب التخطيط", "الممر المحصن", "منطقة الفرز", "مركز التوزيع",
        "الخندق التقني", "صالة الخوادم", "المعبر الآمن", "غرفة التسجيل", "محطة الرصد"
    )

    private val durationsBank: List<String> = listOf(
        "دقيقة واحدة", "دقيقتين", "ثلاث دقائق", "خمس دقائق", "عشر دقائق",
        "ربع ساعة", "نصف ساعة", "ستين ثانية", "تسعين ثانية", "أربعين ثانية",
        "عشرين ثانية", "خمس عشرة ثانية", "الوردية الحالية", "الفترة المسائية", "ساعة كاملة",
        "لحظات قليلة", "نهاية الدقيقة", "جولة واحدة", "جولتين", "منتصف الليل",
        "الفجر", "العد التنازلي", "المرحلة الأولى", "المرحلة الثانية", "الإنذار القادم",
        "زمن التبديل", "فترة الصمت", "نوبة الحراسة", "وقت الفحص", "زمن الاستجابة",
        "أربع دقائق", "ست دقائق", "سبع دقائق", "ثماني دقائق", "تسع دقائق",
        "اثنتي عشرة دقيقة", "خمس وأربعين ثانية", "ثلاثين ثانية", "عشر ثوانٍ", "خمس ثوانٍ",
        "ساعتين", "ثلاث ساعات", "نصف يوم", "نهاية العملية", "بداية الجولة",
        "وقت الإخلاء", "مدة التشفير", "فترة التهدئة", "لحظة الحسم", "النافذة الزمنية",
        "دورة التحديث", "نصف دقيقة", "دقيقة ونصف", "دقيقتين ونصف", "أربعين دقيقة",
        "خمسين دقيقة", "الربع الأخير", "الشوط الأول", "الدور القادم", "ثلاث جولات",
        "ساعة الصفر", "ما بعد الصفر", "حتى إشعار آخر", "فوراً وبلا تأخير", "قبل دقيقة",
        "أثناء الفحص", "خلال التبديل", "طوال الدقيقة", "إلى نهاية المهمة", "لحظة التوقف",
        "خمس ساعات", "ست ساعات", "الليل كله", "ساعات الذروة", "فترة السكون",
        "أسبوع كامل", "يومين", "نهاية اليوم", "خمس عشرة دقيقة", "عشرين دقيقة",
        "خمساً وعشرين دقيقة", "ثلاثين دقيقة", "مئة ثانية", "مئتي ثانية", "ثلاثمئة ثانية",
        "أربع ساعات", "سبع ساعات", "ثماني ساعات", "تسع ساعات", "عشر ساعات",
        "أثناء التصويت", "قبل المسح", "بعد التجميد", "خلال المراقبة", "لحين التأكيد",
        "منذ قليل", "منذ بدء المهمة", "منذ دقيقتين", "منذ خمس دقائق", "منذ عشر دقائق"
    )

    private val threatsBank: List<String> = listOf(
        "اختراق صامت", "إنذار أحمر", "خطر متوسط", "تهديد وشيك", "نشاط مريب",
        "خلل أمني", "تسريب محتمل", "ارتفاع حرارة", "انقطاع مفاجئ", "تشويش قوي",
        "محاولة دخول", "تحذير أصفر", "تجاوز صلاحية", "تلاعب بالبيانات", "نبض غير طبيعي",
        "تسلل ليلي", "ضغط مرتفع", "إشارة مجهولة", "اهتزاز أرضي", "فقدان تواصل",
        "استهداف مباشر", "خرق جدار", "ثغرة حرجة", "تكرار محاولات", "حالة تأهب",
        "طوارئ قصوى", "خطر منخفض", "تنبيه برتقالي", "توقف حساسات", "انحراف مسار",
        "شيفرة مكسورة", "تزوير توقيع", "اختفاء ملف", "اضطراب شبكة", "تهديد داخلي",
        "خطر كيميائي", "وميض ليزري", "صوت غير مألوف", "حركة خفية", "فشل مصادقة",
        "فتح غير مصرح", "زيادة أحمال", "تعطل نظام", "إنذار حريق كاذب", "تشوش راداري",
        "انتحال هوية", "تحايل تقني", "انخفاض جهد", "سحب بيانات", "حقن أوامر",
        "تحذير مبكر", "مستوى حرج", "إغلاق ذاتي", "تهديد سيبراني", "خرق بصري",
        "فجوة زمنية", "تناقض سجلات", "نشاط ليلي", "انفلات أمني", "تذبذب طيفي",
        "اختراق مادي", "نسخ غير قانوني", "محاولة تفكيك", "تعطيل كاميرا", "قطع لاسلكي",
        "التقاط تردد", "مسح سري", "تجسس صوتي", "تغيير تردد", "إشارة تحذير",
        "توقف تبريد", "ازدحام بيانات", "تسلل إلكتروني", "ثغرة برمجية", "فتح خزنة",
        "تبديل بطاقة", "مؤشر خطر", "خلل ميكانيكي", "رصد حراري", "كسر قفل",
        "إنذار محيطي", "حالة اشتباه", "فقدان حزمة", "تشابه توقيعات", "توقف نبضة",
        "تحرك صامت", "انعكاس إشارة", "عطل حساس", "خطر متصاعد", "تهديد صامت",
        "اضطراب تشفير", "كشف موقع", "ارتباك مهام", "خطر غير محدد", "نفاد طاقة",
        "إعادة تشغيل", "تجاوز بوابة", "اندفاع تيار", "تسرّب ضوء", "بلاغ طارئ"
    )

    private val reasonsBank: List<String> = listOf(
        "تعارض المفاتيح", "فتح غير مصرح", "عطل كهربائي", "ازدحام الشبكة", "بطاقة مفقودة",
        "خطأ بشري", "محاولة تخمين", "فشل التزامن", "كابل مقطوع", "جهاز غريب",
        "تأخر الاستجابة", "نسخة قديمة", "رمز منتهي", "تداخل موجات", "سوء تقدير",
        "باب مفتوح", "إشارة ضعيفة", "حساس معطل", "فقدان الإشارة", "تحميل زائد",
        "بصمة مرفوضة", "انتهاء الصلاحية", "مهمة متضاربة", "تشغيل يدوي", "ظلام مفاجئ",
        "عدم التبليغ", "سرعة مفرطة", "مسار خاطئ", "رسالة متأخرة", "صمت غريب",
        "قفل مكسور", "شحن منخفض", "مراقبة مفقودة", "تبديل متأخر", "كلمة مرور مكشوفة",
        "ارتفاع الرطوبة", "اهتزاز الباب", "فشل التشخيص", "ملف تالف", "اتصال خارجي",
        "جدار معطل", "بروتوكول قديم", "تسرب غاز", "شرارة كهربائية", "نسيان الإغلاق",
        "تكرار الدخول", "استبدال جهاز", "تعديل غير مسجل", "فقدان التغطية", "عدم التحقق",
        "ازدواجية الأوامر", "نقص المعدات", "تضارب المعلومات", "ضغط العمل", "إعادة التوجيه",
        "اختلاف التوقيت", "فشل النسخ", "امتلاء الذاكرة", "حجب الرؤية", "ضوضاء عالية",
        "تغيير الوردية", "اختصار الإجراء", "تأخر الصيانة", "بطء المعالجة", "ثغرة مفتوحة",
        "عدم المطابقة", "فصل السلك", "خطأ الترتيب", "إهمال التشفير", "تشغيل تلقائي",
        "عطل المروحة", "انهيار الاتصال", "شبهة تلاعب", "اختلال التوازن", "ضعف البطارية",
        "تفعيل خاطئ", "غياب المشرف", "حركة غير متوقعة", "انقطاع التغذية", "فشل التبريد",
        "تراكم البيانات", "صيانة طارئة", "إزالة الختم", "بلاغ ناقص", "التقاط ضوضاء",
        "سوء الفهم", "تعديل الصلاحيات", "حظر مؤقت", "استثناء أمني", "انتظار طويل",
        "فحص متكرر", "انتحال صلاحية", "كسر الحماية", "عدم الاتساق", "تمويه فاشل",
        "تبديد الشكوك", "ارتداد الموجة", "تشقق العازل", "انغلاق المصعد", "تضليل متعمد"
    )

    private val suspicionsBank: List<String> = listOf(
        "تردداً غريباً", "تأخراً في الرد", "محاولة تشتيت", "نظرات متوترة", "تغييراً في الأسلوب",
        "تجنباً للأسئلة", "إصراراً مبالغاً", "صمتاً طويلاً", "أسئلة متكررة", "ارتباكاً واضحاً",
        "تجولاً بلا سبب", "مراقبة الهواتف", "دفاعاً متسرعاً", "اتهامات عشوائية", "تناقضاً في الكلام",
        "كتماناً مريباً", "انسحاباً مفاجئاً", "تغييراً في الموقع", "حماساً زائداً", "بروداً غير طبيعي",
        "تجاهلاً للتنبيه", "التفافاً حول الإجابة", "تركيزاً على الشاشة", "مسحاً للمحيط", "إشارة خفية",
        "رفضاً للتعاون", "تأخيراً متعمداً", "تلعثماً ملحوظاً", "تكراراً للعبارات", "حركة متوترة",
        "مقاطعة للنقاش", "محاولة تصويت مبكر", "تغييراً للموضوع", "تردداً في الاختيار", "ثقة مصطنعة",
        "مغالطة في التوقيت", "شكاً في الجميع", "دعم غير مبرر", "استعجالاً لإنهاء الدور", "نسياناً غريباً",
        "غموضاً في البلاغ", "استفساراً عن الخانات", "إخفاءً للشاشة", "تظاهراً بالانشغال", "مبالغة في التبرير",
        "انتظاراً مريباً", "مخالفة للخطة", "تبديلاً في الرأي", "رفضاً للفحص", "توتر الأصابع",
        "ابتسامة متكلفة", "تشتت الانتباه", "تسرعاً في الاتهام", "تغييراً في النبرة", "تحفظاً شديداً",
        "اهتماماً بالمتجسس", "تلميحات غامضة", "إرباكاً للفريق", "تهرباً من Audit", "تأخراً في التصويت",
        "مسايرة مفرطة", "انعزالاً جانبياً", "مراجعة متكررة", "قراءة بطيئة", "حرصاً زائداً",
        "استبعاداً بلا دليل", "تقليداً للآخرين", "إنكاراً متسرعاً", "تفسيراً معقداً", "تشويشاً كلامياً",
        "عدم وضوح", "تردداً أمام اللغز", "قفزاً للاستنتاج", "مراقبة الساعات", "تشابه أعذار",
        "إخفاقاً في التذكر", "تفادياً للنظر", "قلقاً متزايداً", "انفعالاً مفاجئاً", "هدوءاً مقلقاً",
        "تعليقاً ساخرًا", "تمسكاً بالتفاصيل", "تبديداً للوقت", "خوفاً من التحقيق", "تشكيكاً في النظام",
        "تحايلاً على القالب", "تباطؤاً مقصوداً", "اختباراً للآخرين", "صدمة عند التنبيه", "تبريراً متكرراً",
        "انحيازاً غريباً", "تهويناً للخطر", "تضخيماً للخطأ", "تمويهاً لغوياً", "ارتجالاً ضعيفاً",
        "حساسية مفرطة", "اندفاعاً خاطئاً", "انتقاداً مستمراً", "غياباً عن التركيز", "سلوكاً غير معتاد"
    )

    private val sectorsBank: List<String> = listOf(
        "القطاع ألفا", "القطاع بيتا", "القطاع جاما", "القطاع دلتا", "القطاع إبسيلون",
        "القطاع زيتا", "القطاع سيجما", "القطاع أوميجا", "القطاع الأول", "القطاع الثاني",
        "القطاع الثالث", "القطاع الرابع", "القطاع الخامس", "القطاع السادس", "القطاع السابع",
        "القطاع الثامن", "القطاع التاسع", "القطاع العاشر", "قطاع الشيفرة", "قطاع الاتصالات",
        "قطاع الطاقة", "قطاع الدفاع", "قطاع المراقبة", "قطاع الأرشيف", "قطاع التحليل",
        "قطاع الخوادم", "قطاع الشبكات", "قطاع التشفير", "قطاع البوابات", "قطاع الإسناد",
        "قطاع الطوارئ", "قطاع العمليات", "قطاع الردع", "قطاع الاستطلاع", "قطاع المخازن",
        "قطاع الصيانة", "قطاع التوجيه", "قطاع الرادار", "قطاع الإشارة", "قطاع الحماية",
        "النطاق الشمالي", "النطاق الجنوبي", "النطاق الشرقي", "النطاق الغربي", "النطاق المركزي",
        "المحور أ-1", "المحور ب-2", "المحور ج-3", "المحور د-4", "المحور هـ-5",
        "الجناح الأزرق", "الجناح الفضي", "الجناح الأسود", "الجناح الذهبي", "الجناح الحديدي",
        "القناة الأولى", "القناة الثانية", "القناة المشفرة", "القناة الاحتياطية", "القناة الصامتة",
        "الخلية 11", "الخلية 12", "الخلية 13", "الخلية 14", "الخلية 15",
        "المجمع 07", "المجمع 08", "المجمع 09", "المجمع 10", "المجمع 20",
        "الدائرة المغلقة", "الدائرة المفتوحة", "الدائرة العليا", "الدائرة السفلى", "الدائرة الحرجة",
        "الخط الأخضر", "الخط الأحمر", "الخط الأزرق", "الخط الأصفر", "الخط الفاصل",
        "المنطقة س", "المنطقة ص", "المنطقة ع", "المنطقة ق", "المنطقة ك",
        "البرج 1", "البرج 2", "البرج 3", "البرج 4", "البرج 5",
        "الرصيف أ", "الرصيف ب", "الرصيف ج", "الرصيف د", "الرصيف هـ",
        "المركز اللوجستي", "المركز التقني", "المركز الإقليمي", "المركز الاستخباراتي", "المركز الاحتياطي"
    )

    fun getBankForSlot(
        kind: SlotKind,
        players: List<PlayerState>,
        currentSenderId: String
    ): List<String> {
        return when (kind) {
            SlotKind.ACTION -> (actionsBank + CloudContentCatalog.builtInExtraActions + CloudContentCatalog.cloudExtraActions).distinct()
            SlotKind.LOCATION -> (locationsBank + CloudContentCatalog.builtInExtraLocations + CloudContentCatalog.cloudExtraLocations).distinct()
            SlotKind.DURATION -> (durationsBank + CloudContentCatalog.builtInExtraDurations + CloudContentCatalog.cloudExtraDurations).distinct()
            SlotKind.THREAT -> (threatsBank + CloudContentCatalog.builtInExtraThreats + CloudContentCatalog.cloudExtraThreats).distinct()
            SlotKind.REASON -> (reasonsBank + CloudContentCatalog.builtInExtraReasons + CloudContentCatalog.cloudExtraReasons).distinct()
            SlotKind.SUSPICION -> (suspicionsBank + CloudContentCatalog.builtInExtraSuspicions + CloudContentCatalog.cloudExtraSuspicions).distinct()
            SlotKind.SECTOR -> (sectorsBank + CloudContentCatalog.builtInExtraSectors + CloudContentCatalog.cloudExtraSectors).distinct()
            SlotKind.AGENT_NAME -> players
                .filter { it.alive && it.id != currentSenderId }
                .map { it.name }
        }
    }

    fun pickNextSpyReport(
        usedSpyReportIds: Set<String>,
        random: Random = Random.Default
    ): Pair<String, String> {
        val all = CloudContentCatalog.allSpyReports()
        val available = all.filter { it.id !in usedSpyReportIds }
        val pool = if (available.isNotEmpty()) available else all
        val chosen = pool[random.nextInt(pool.size)]
        return chosen.id to chosen.text
    }

    fun pickSixOptions(
        kind: SlotKind,
        usedWords: Set<String>,
        currentlySelectedInMessage: Set<String>,
        players: List<PlayerState>,
        currentSenderId: String,
        random: Random = Random.Default
    ): List<String> {
        val raw = getBankForSlot(kind, players, currentSenderId)
        val filtered = if (kind == SlotKind.AGENT_NAME) {
            raw.filter { it !in currentlySelectedInMessage }
        } else {
            raw.filter { it !in usedWords && it !in currentlySelectedInMessage }
        }
        val pool = if (filtered.isNotEmpty()) filtered else raw.filter { it !in currentlySelectedInMessage }
        return pool.shuffled(random).take(6)
    }

    fun buildAuditPromptFromReport(
        template: MessageTemplate,
        selectedWords: List<String>,
        senderId: String,
        random: Random = Random.Default
    ): SystemAuditPrompt? {
        val preferenceOrder = listOf(
            SlotKind.LOCATION,
            SlotKind.SECTOR,
            SlotKind.THREAT,
            SlotKind.ACTION,
            SlotKind.REASON,
            SlotKind.SUSPICION,
            SlotKind.DURATION
        )
        val candidatePairs = template.slots.mapIndexedNotNull { idx, kind ->
            val word = selectedWords.getOrNull(idx)?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            if (kind == SlotKind.AGENT_NAME) null else kind to word
        }
        if (candidatePairs.isEmpty()) return null

        val chosen = preferenceOrder.firstNotNullOfOrNull { pref ->
            candidatePairs.firstOrNull { it.first == pref }
        } ?: candidatePairs.random(random)

        return createPromptForSlot(
            kind = chosen.first,
            correctWord = chosen.second,
            reportLabel = "في البلاغ الأخير",
            senderId = senderId,
            random = random
        )
    }

    fun buildAuditPrompt(
        messages: List<GameMessage>,
        random: Random = Random.Default
    ): SystemAuditPrompt? {
        val playerMessages = messages.filter { it.channel == MessageChannel.PLAYER && !it.isDecoyTrap }
        if (playerMessages.isEmpty()) return null
        val targetMsg = playerMessages.last()
        return buildPromptForMessage(targetMsg, "في البلاغ الأخير", random)
    }

    private fun buildPromptForMessage(
        msg: GameMessage,
        label: String,
        random: Random
    ): SystemAuditPrompt? {
        val searchOrder = listOf(
            SlotKind.SECTOR,
            SlotKind.LOCATION,
            SlotKind.THREAT,
            SlotKind.ACTION,
            SlotKind.REASON,
            SlotKind.SUSPICION,
            SlotKind.DURATION
        )
        for (kind in searchOrder) {
            val bank = getBankForSlot(kind, emptyList(), "")
            // Match longest phrase first to avoid partial substring collisions
            val matched = bank.sortedByDescending { it.length }.firstOrNull { msg.plainText.contains(it) }
            if (matched != null) {
                return createPromptForSlot(
                    kind = kind,
                    correctWord = matched,
                    reportLabel = label,
                    senderId = msg.senderId,
                    random = random
                )
            }
        }
        return null
    }

    private fun createPromptForSlot(
        kind: SlotKind,
        correctWord: String,
        reportLabel: String,
        senderId: String,
        random: Random
    ): SystemAuditPrompt {
        val bank = getBankForSlot(kind, emptyList(), "")
        val distractors = bank.filter { it != correctWord }.shuffled(random).take(3)
        val options = (distractors + correctWord).shuffled(random)
        val questionText = when (kind) {
            SlotKind.LOCATION -> "سؤال النظام التلقائي: ما المنطقة أو الموقع المذكور $reportLabel؟"
            SlotKind.SECTOR -> "سؤال النظام التلقائي: ما القطاع المذكور $reportLabel؟"
            SlotKind.THREAT -> "سؤال النظام التلقائي: ما مستوى الخطر المذكور $reportLabel؟"
            SlotKind.ACTION -> "سؤال النظام التلقائي: ما الإجراء الأمني المذكور $reportLabel؟"
            SlotKind.REASON -> "سؤال النظام التلقائي: ما السبب المذكور $reportLabel؟"
            SlotKind.SUSPICION -> "سؤال النظام التلقائي: ما الصفة المريبة المذكورة $reportLabel؟"
            SlotKind.DURATION -> "سؤال النظام التلقائي: ما المدة الزمنية المذكورة $reportLabel؟"
            SlotKind.AGENT_NAME -> "سؤال النظام التلقائي: ما التفصيل المذكور $reportLabel؟"
        }
        return SystemAuditPrompt(
            question = questionText,
            options = options,
            correctIndex = options.indexOf(correctWord).coerceAtLeast(0),
            senderId = senderId
        )
    }
}
