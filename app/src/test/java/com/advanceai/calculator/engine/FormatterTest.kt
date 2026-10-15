package com.advanceai.calculator.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the display settings: how many digits a result shows and the two
 * digit-grouping styles. Every expected string was checked by hand, and the
 * defaults here are what the app has always printed (no separators), so an
 * expression that used to read 9900 must still read 9900.
 */
class FormatterTest {

    private val indian = DisplayOptions(grouping = DigitGrouping.INDIAN)
    private val western = DisplayOptions(grouping = DigitGrouping.WESTERN)

    @Test
    fun nothingIsGroupedByDefault() {
        assertEquals("9900", Formatter.number(9900.0))
        assertEquals("1234567", Formatter.number(1234567.0))
        assertEquals("1234567", Formatter.number(1234567.0, display = DisplayOptions()))
    }

    @Test
    fun digitsShownRoundsTheDisplayOnly() {
        val third = 1.0 / 3.0
        assertEquals("0.333333333333", Formatter.number(third))
        assertEquals("0.3333", Formatter.number(third, display = DisplayOptions(significantDigits = 4)))
        assertEquals(
            "0.333333333333333",
            Formatter.number(third, display = DisplayOptions(significantDigits = 15))
        )
    }

    @Test
    fun westernGroupingUsesThrees() {
        assertEquals("1,234,567", Formatter.number(1234567.0, display = western))
        assertEquals("1,234,567", Formatter.group("1234567"))
        assertEquals("-1,234,567", Formatter.group("-1234567"))
        assertEquals("12,345.678", Formatter.group("12345.678"))
        // Short numbers are left alone.
        assertEquals("999", Formatter.group("999"))
    }

    @Test
    fun indianGroupingUsesTheLastThreeThenPairs() {
        assertEquals("12,34,567", Formatter.number(1234567.0, display = indian))
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
        assertEquals("0.3333", (CalculatorEngine.evaluate("1/3", ctx) as EngineResult.Ok).text)
    }

    @Test
    fun theEngineGroupsOnlyWhenAsked() {
        val plain = CalculatorEngine.evaluate("100!/98!", MathContext()) as EngineResult.Ok
        assertEquals("9900", plain.text)

        val grouped = CalculatorEngine.evaluate(
            "100!/98!",
            MathContext(display = DisplayOptions(grouping = DigitGrouping.WESTERN))
        ) as EngineResult.Ok
        assertEquals("9,900", grouped.text)

        val lakhs = CalculatorEngine.evaluate(
            "500000*1",
            MathContext(display = DisplayOptions(grouping = DigitGrouping.INDIAN))
        ) as EngineResult.Ok
        assertEquals("5,00,000", lakhs.text)
    }
}
