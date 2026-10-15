package com.advanceai.calculator.engine

/**
 * How results are printed. Kept separate from the maths on purpose: the setting
 * changes what the user reads, never what the engine computes with.
 *
 * [significantDigits] is how many digits a result is rounded to before it is
 * shown (12 by default, the most that still reads as exact for everyday sums).
 * [indianGrouping] switches digit grouping to the Indian system, so a salary
 * reads 12,34,567 rather than 1,234,567.
 */
data class DisplayOptions(
    val significantDigits: Int = Formatter.SIGNIFICANT_DIGITS,
    val indianGrouping: Boolean = false
) {
    companion object {
        /** The choices offered in Settings. */
        val DIGIT_CHOICES = listOf(8, 10, 12, 15)

        /** Twelve digits, Western grouping: what the app has always shown. */
        val DEFAULT = DisplayOptions()
    }
}
