package com.example.core

import kotlin.random.Random

enum class BlackChannelBroadcastStyle(
    val senderTitle: String,
    val labelAr: String
) {
    SYSTEM_WARNING("النظام الأمني الرسمي", "تحذير نظام رسمي"),
    SECURITY_QUESTION("تحقيق النظام الرسمي", "سؤال تحقيق أمني")
}

object BlackChannelConfig {
    const val MAX_USES = 2
    const val ONLINE_DURATION_MS = 120_000L // دقيقتان
    const val OFFLINE_DURATION_ROUNDS = 2   // جولتان

    val presetPayloads: List<Pair<BlackChannelBroadcastStyle, String>> = listOf(
        BlackChannelBroadcastStyle.SYSTEM_WARNING to
            "تحذير أمني عاجل: رُصد تسلل بيانات غير مصرح به في النصف الأول من قائمة العملاء — تحقّقوا فوراً!",
        BlackChannelBroadcastStyle.SECURITY_QUESTION to
            "سؤال تحقيق أمني: على جميع العملاء تأكيد سبب تأخّر التبليغ في القطاع المذكور قبل الجولة القادمة.",
        BlackChannelBroadcastStyle.SYSTEM_WARNING to
            "تنبيه مصادقة: تطابق بصمات ضعيف في الغرفة — يُشتبه بتزوير أحد البلاغات الأخيرة!",
        BlackChannelBroadcastStyle.SECURITY_QUESTION to
            "استجواب نظام رسمي: من العميل الذي غيّر موقعه الميداني في البلاغ قبل الأخير دون إشعار؟"
    )
}

object SmartIdentityHintGenerator {

    /**
     * Generates a mysterious yet 100% factually accurate hint about the player
     * who gave an incorrect answer during a System Audit, without revealing their name.
     */
    fun generateHint(
        offenderId: String,
        orderedRoster: List<Pair<String, String>>,
        random: Random = Random.Default
    ): String {
        if (orderedRoster.isEmpty()) {
            return "المشتبه به متخفٍّ بين الحاضرين حالياً."
        }
        val idx = orderedRoster.indexOfFirst { it.first == offenderId }
        if (idx < 0) {
            return "المشتبه به يتواجد ضمن القائمة النشطة للعملاء."
        }

        val total = orderedRoster.size
        val offenderName = orderedRoster[idx].second.trim()
        val hints = mutableListOf<String>()

        if (total >= 2) {
            val mid = total / 2
            if (idx < mid) {
                hints += "المشتبه به يقع ضمن النصف الأول من قائمة اللاعبين."
            } else {
                hints += "المشتبه به يقع ضمن النصف الأخير من قائمة اللاعبين."
            }
        }

        val seatNum = idx + 1
        if (seatNum % 2 == 1) {
            hints += "المشتبه به يحمل ترتيباً فردياً في قائمة العملاء (المقعد 1، 3، 5...)."
        } else {
            hints += "المشتبه به يحمل ترتيباً زوجياً في قائمة العملاء (المقعد 2، 4، 6...)."
        }

        if (total >= 3) {
            if (idx == 0 || idx == total - 1) {
                hints += "المشتبه به يتمركز في أحد طرفي قائمة اللاعبين (الأول أو الأخير)."
            } else {
                hints += "المشتبه به يتواجد في وسط القائمة تقريباً وليس على الأطراف."
            }
        }

        if (offenderName.isNotEmpty()) {
            if (offenderName.length <= 5) {
                hints += "الاسم الحركي للمشتبه به قصير نسبياً (5 أحرف أو أقل)."
            } else {
                hints += "الاسم الحركي للمشتبه به طويل نسبياً (أكثر من 5 أحرف)."
            }
        }

        return hints[random.nextInt(hints.size)]
    }

    fun formatWrongAnswerAlert(
        offenderId: String,
        orderedRoster: List<Pair<String, String>>,
        random: Random = Random.Default
    ): String {
        val hint = generateHint(offenderId, orderedRoster, random)
        return "تنبيه أمني: قام أحد العملاء بإجابة خاطئة!\nتلميح ذكي: $hint"
    }

    fun formatSuccessInvestigationAlert(): String {
        return "نجح التحقيق الأمني: أجاب جميع اللاعبين على سؤال النظام بشكل صحيح دون رصد أي تناقض أو كشف أسرار."
    }
}
