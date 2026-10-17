package com.advanceai.calculator.engine

/** How digits are separated when a result is printed. */
enum class DigitGrouping {
    /** 1234567 — what the app has always shown, and the default. */
    NONE,

    /** 1,234,567 — threes all the way. */
    WESTERN,

    /** 12,34,567 — the last three digits, then pairs (lakhs and crores). */
    INDIAN
}

/**
 * How results are printed. Kept separate from the maths on purpose: these
 * settings change what the user reads, never what the engine computes with.
 *
 * [significantDigits] is how many digits a result is rounded to before it is
 * shown (12 by default, the most that still reads as exact for everyday sums).
 */
data class DisplayOptions(
    val significantDigits: Int = Formatter.SIGNIFICANT_DIGITS,
    val grouping: DigitGrouping = DigitGrouping.NONE
) {
    /** True when the Indian style is selected. */
    val indianGrouping: Boolean get() = grouping == DigitGrouping.INDIAN

    companion object {
        /** The choices offered in Settings. */
        val DIGIT_CHOICES = listOf(8, 10, 12, 15)

        /** Twelve digits, no separators: what the app has always shown. */
        val DEFAULT = DisplayOptions()
    }
}
