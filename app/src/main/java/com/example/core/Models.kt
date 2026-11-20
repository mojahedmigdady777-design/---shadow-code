package com.example.core

import java.util.UUID

enum class Role {
    ORG,
    SPY
}

enum class CipherType(val arabicName: String, val englishName: String) {
    CAESAR_FORWARD("قيصر بإزاحة للأمام", "Caesar Forward (+n)"),
    CAESAR_BACKWARD("قيصر بإزاحة عكسية", "Caesar Backward (-n)"),
    REVERSE("عكس ترتيب الأحرف", "Reverse Letters"),
    SWAP_PAIRS("تبديل المواضع الزوجية بالفردية", "Swap Adjacent Pairs")
}

enum class PuzzleType(val arabicTitle: String, val englishTitle: String) {
    CIPHER_DECODING("فك الشفرات الكلاسيكية", "Cipher Decoding"),
    LOGIC_MATRIX("مصفوفات المنطق والأرقام", "Logic & Math Matrix"),
    DEDUCTION_GRID("شبكات الاستنتاج المنطقي", "Deduction Grid"),
    MEMORY_FLASH("الذاكرة والتركيز السريع", "Memory Flash"),
    CIRCUIT_ROUTING("توصيل الترددات والأسلاك", "Circuit Routing"),
    ALGORITHM_SEQUENCE("الخوارزمية المتسلسلة", "Algorithm Sequence")
}

enum class MessageCategory(val arabicTitle: String) {
    FIELD_ORDERS("أوامر ميدانية"),
    STATUS_ALERTS("تقارير الحالة والإنذار"),
    CAMOUFLAGE_ACCUSATION("التمويه والاتهام"),
    KEYS_SECTORS("المفاتيح والقطاعات")
}

enum class MessageChannel {
    PLAYER,
    SYSTEM_ORG,
    SYSTEM_SPY,
    ALERT
}

enum class RareCardType(
    val arabicName: String,
    val arabicDescription: String,
    val forRole: Role
) {
    DECOY_TRAP(
        "الفخ الخداعي",
        "أرسل رسالة ملغومة؛ إن تنصّت المتجسس عليك أو شحن لغزه بها يتعطل وتنخفض شحناته.",
        Role.ORG
    ),
    SYSTEM_FREEZE(
        "تجميد النظام",
        "يجمّد محطة اختراق المتجسس ويمنعه من حل الألغاز في دوره القادم / 45 ثانية.",
        Role.ORG
    ),
    DEEP_RADAR_SCAN(
        "مسح الترددات",
        "يكشف عدد الخانات المخترقة حالياً وحالة نشاط المتجسس بدقة.",
        Role.ORG
    ),
    ZERO_DAY_EXPLOIT(
        "الاختراق القسري",
        "يكشف خانة كاملة من الكود السري فوراً وبإنذار مؤجّل.",
        Role.SPY
    ),
    IDENTITY_SPOOF(
        "تزوير الهوية",
        "أرسل بلاغاً تكتيكياً يظهر في السجل وكأنه مرسل من عميل آخر تختاره.",
        Role.SPY
    ),
    ALARM_JAMMER(
        "تشويش الإنذار",
        "يمنع وصول تحذير فتح الخانة القادم إلى شاشة المنظمة.",
        Role.SPY
    )
}

enum class WinTeam {
    ORG,
    SPY
}

enum class WinReason(val arabicTitle: String, val arabicSubtitle: String) {
    SPY_CODE_CRACKED("انتصار المتجسس", "تم كشف الخانات الأربع وإبطال العملية بنجاح!"),
    SPY_ARRESTED("انتصار المنظمة بالاعتقال", "تم كشف المتجسس واعتقاله في التصويت السري."),
    TIME_OUT("انتصار المنظمة بالصمود", "صمدت المنظمة حتى انتهاء المهلة دون اختراق الكود."),
    TOO_FEW_ORG("انتصار المتجسس", "تقلّص عدد الأعضاء الفاعلين إلى اثنين أو أقل دون اعتقال المتجسس.")
}

data class PlayerState(
    val id: String,
    val name: String,
    val role: Role,
    val assignedSlotIndices: List<Int> = emptyList(),
    val alive: Boolean = true,
    val usedEmergency: Boolean = false,
    val usedSlotShield: Boolean = false,
    val slotShieldGranted: Boolean = false,
    val rareCards: List<RareCardType> = emptyList(),
    val puzzlesSolved: Int = 0,
    val puzzlesAttempted: Int = 0,
    val fastestPuzzleMs: Long = 0L
)

data class GameMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val trueSenderName: String = senderName,
    val channel: MessageChannel,
    val targetPlayerId: String? = null,
    val plainText: String,
    val cipherText: String,
    val isDecoyTrap: Boolean = false,
    val decoyTriggered: Boolean = false,
    val roundNumber: Int = 1,
    val timestampLabel: String = ""
)

data class SystemAuditPrompt(
    val id: String = UUID.randomUUID().toString(),
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val senderId: String = "",
    val wrongReported: Boolean = false
)

enum class BattleEventType(val arabicLabel: String) {
    ROUND_START("بدء الجولة"),
    MESSAGE("بلاغ تكتيكي"),
    WIRETAP("تنصت موجّه"),
    PUZZLE_ATTACK("هجوم اختراق"),
    ABILITY("استخدام قدرة"),
    INVESTIGATION("تحقيق طارئ"),
    VOTE("تصويت سري"),
    PENALTY("رقابة وانضباط"),
    ROUND_END("نهاية الجولة")
}

data class BattleLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val roundNumber: Int = 1,
    val actorName: String,
    val eventType: BattleEventType,
    val summary: String,
    val timestampLabel: String = ""
)

