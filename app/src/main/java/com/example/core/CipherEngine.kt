package com.example.core

import kotlin.random.Random

object CipherEngine {

    // Exact 28 Arabic letters specified in the game design
    val ARABIC_ALPHABET = listOf(
        'ا', 'ب', 'ت', 'ث', 'ج', 'ح', 'خ', 'د', 'ذ', 'ر', 'ز', 'س', 'ش', 'ص',
        'ض', 'ط', 'ظ', 'ع', 'غ', 'ف', 'ق', 'ك', 'ل', 'م', 'ن', 'ه', 'و', 'ي'
    )

    // Exclude ambiguous characters 0/O and 1/I
    private val CODE_CHARSET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toList()

    fun generateSecretCode(random: Random = Random.Default): List<String> {
        return List(4) { CODE_CHARSET[random.nextInt(CODE_CHARSET.size)].toString() }
    }

    fun generateSingleCodeChar(excluding: String? = null, random: Random = Random.Default): String {
        val pool = if (excluding != null) CODE_CHARSET.filter { it.toString() != excluding } else CODE_CHARSET
        return pool[random.nextInt(pool.size)].toString()
    }

    fun pickCipherConfig(random: Random = Random.Default): Pair<CipherType, Int> {
        val type = CipherType.entries[random.nextInt(CipherType.entries.size)]
        val shift = random.nextInt(2, 7) // 2..6
        return type to shift
    }

    private fun normalizeArabicChar(ch: Char): Char = when (ch) {
        'أ', 'إ', 'آ', 'ى' -> 'ا'
        'ة' -> 'ه'
        'ئ', 'ؤ' -> 'ي'
        else -> ch
    }

    fun encrypt(plain: String, type: CipherType, shift: Int): String {
        return when (type) {
            CipherType.CAESAR_FORWARD -> shiftArabic(plain, shift)
            CipherType.CAESAR_BACKWARD -> shiftArabic(plain, -shift)
            CipherType.REVERSE -> plain.split(" ").joinToString(" ") { word -> word.reversed() }
            CipherType.SWAP_PAIRS -> plain.split(" ").joinToString(" ") { word -> swapAdjacent(word) }
        }
    }

    private fun shiftArabic(text: String, delta: Int): String {
        val size = ARABIC_ALPHABET.size
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val normalized = normalizeArabicChar(ch)
            val idx = ARABIC_ALPHABET.indexOf(normalized)
            if (idx >= 0) {
                val newIdx = ((idx + delta) % size + size) % size
                sb.append(ARABIC_ALPHABET[newIdx])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun swapAdjacent(word: String): String {
        val chars = word.toCharArray()
        var i = 0
        while (i + 1 < chars.size) {
            val tmp = chars[i]
            chars[i] = chars[i + 1]
            chars[i + 1] = tmp
            i += 2
        }
        return String(chars)
    }

    fun generateSpyHint(
        cipherType: CipherType,
        cipherShift: Int,
        codeSlots: List<String>,
        revealedCount: Int,
        random: Random = Random.Default
    ): String {
        val hints = mutableListOf<String>()
        when (cipherType) {
            CipherType.CAESAR_FORWARD -> {
                val parity = if (cipherShift % 2 == 0) "زوجي" else "فردي"
                hints += "تلميح التشفير: النظام يعتمد على الإزاحة للأمام بمقدار رقم $parity."
                hints += "تلميح التشفير: الإزاحة تقع بين ${maxOf(1, cipherShift - 1)} و ${cipherShift + 1} خطوات للأمام."
            }
            CipherType.CAESAR_BACKWARD -> {
                val parity = if (cipherShift % 2 == 0) "زوجي" else "فردي"
                hints += "تلميح التشفير: النظام يعتمد على الإزاحة العكسية بمقدار رقم $parity."
                hints += "تلميح التشفير: الأحرف تتحرك للخلف في الأبجدية بمقدار ${cipherShift} خطوات تقريباً."
            }
            CipherType.REVERSE -> {
                hints += "تلميح التشفير: الكلمات مقلوبة الترتيب داخل الرسائل الملتقطة."
            }
            CipherType.SWAP_PAIRS -> {
                hints += "تلميح التشفير: يتم تبديل الحروف المتجاورة في كل كلمة."
            }
        }
        if (revealedCount < 4) {
            val idx = revealedCount
            val ch = codeSlots[idx].firstOrNull() ?: 'A'
            val kind = if (ch.isDigit()) "رقماً (من 2 إلى 9)" else "حرفاً لاتينياً (ليس رقماً)"
            hints += "تلميح الكود: الخانة رقم ${idx + 1} هي $kind."
        }
        return hints[random.nextInt(hints.size)]
    }
}
