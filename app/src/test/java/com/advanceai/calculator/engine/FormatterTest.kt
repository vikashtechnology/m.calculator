package com.advanceai.calculator.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the display settings: how many digits a result shows, and the two
 * digit-grouping styles. The values were checked by hand.
 */
class FormatterTest {

    private val plain = DisplayOptions()
    private val indian = DisplayOptions(indianGrouping = true)

    @Test
    fun digitsShownRoundsTheDisplayOnly() {
        val third = 1.0 / 3.0
        assertEquals("0.333333333333", Formatter.number(third, display = plain))
        assertEquals("0.3333", Formatter.number(third, display = DisplayOptions(significantDigits = 4)))
        assertEquals(
            "0.333333333333333",
            Formatter.number(third, display = DisplayOptions(significantDigits = 15))
        )
    }

    @Test
    fun westernGroupingUsesThrees() {
        assertEquals("1,234", Formatter.number(1234.0, display = indian))
        assertEquals("1,234,567", Formatter.group("1234567"))
        assertEquals("-1,234,567", Formatter.group("-1234567"))
        assertEquals("12,345.678", Formatter.group("12345.678"))
        // Short numbers are left alone.
        assertEquals("999", Formatter.group("999"))
    }

    @Test
    fun indianGroupingUsesTheLastThreeThenPairs() {
        assertEquals("12,34,567", Formatter.group("1234567", indian = true))
        assertEquals("1,23,456", Formatter.group("123456", indian = true))
        assertEquals("-12,34,567", Formatter.group("-1234567", indian = true))
        assertEquals("12,34,567.89", Formatter.group("1234567.89", indian = true))
        assertEquals("5,00,000", Formatter.group("500000", indian = true))
    }

    @Test
    fun groupingNeverTouchesOtherText() {
        assertEquals("1e15", Formatter.group("1e15"))
        assertEquals("Undefined", Formatter.group("Undefined"))
    }

    @Test
    fun theEnginePrintsWithTheDisplayOptions() {
        val ctx = MathContext(display = DisplayOptions(significantDigits = 4))
        val result = CalculatorEngine.evaluate("1/3", ctx)
        assertEquals("0.3333", (result as EngineResult.Ok).text)
    }

    @Test
    fun theEngineGroupsWhenAsked() {
        val ctx = MathContext(display = DisplayOptions(indianGrouping = true))
        val result = CalculatorEngine.evaluate("500000*1", ctx)
        assertEquals("5,00,000", (result as EngineResult.Ok).text)
    }
}
