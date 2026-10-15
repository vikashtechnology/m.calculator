package com.advanceai.calculator.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Golden-value tests for the everyday calculators. Every expected number was
 * computed independently (Python) instead of being copied from the app.
 */
class EverydayTest {

    private fun r(value: Double, digits: Int = 2): Double {
        val factor = Math.pow(10.0, digits.toDouble())
        return Math.round(value * factor) / factor
    }

    @Test
    fun gstExclusive() {
        val result = Everyday.gst(1000.0, 18.0, inclusive = false)
        assertEquals(1000.0, result.base, 1e-9)
        assertEquals(180.0, result.gst, 1e-9)
        assertEquals(1180.0, result.total, 1e-9)
        assertEquals(90.0, result.cgst, 1e-9)
        assertEquals(90.0, result.sgst, 1e-9)
    }

    @Test
    fun gstInclusive() {
        val result = Everyday.gst(1180.0, 18.0, inclusive = true)
        assertEquals(1000.0, result.base, 1e-6)
        assertEquals(180.0, result.gst, 1e-6)
        assertEquals(1180.0, result.total, 1e-6)
    }

    @Test
    fun gstRatesUsedInIndia() {
        // 5% on 500 is 25, 12% on 500 is 60, 28% on 500 is 140.
        assertEquals(25.0, Everyday.gst(500.0, 5.0, false).gst, 1e-9)
        assertEquals(60.0, Everyday.gst(500.0, 12.0, false).gst, 1e-9)
        assertEquals(140.0, Everyday.gst(500.0, 28.0, false).gst, 1e-9)
    }

    @Test
    fun emiMatchesTheStandardFormula() {
        val result = Everyday.emi(500_000.0, 9.0, 60)
        assertEquals("10379.1776132", Formatter.number(result.emi))
        assertEquals("622750.656791", Formatter.number(result.totalPayment))
        assertEquals("122750.656791", Formatter.number(result.totalInterest))
        assertEquals(24.55, r(result.interestPercent), 0.01)
    }

    @Test
    fun emiEdgeCases() {
        // Zero interest: the principal is simply split across the months.
        assertEquals(10_000.0, Everyday.emi(100_000.0, 0.0, 10).emi, 1e-9)
        // A 20 year home loan.
        assertEquals("10413.8788004", Formatter.number(Everyday.emi(1_200_000.0, 8.5, 240).emi))
    }

    @Test
    fun amortisationRowsAddUp() {
        val loan = Everyday.emi(500_000.0, 9.0, 60)
        val rows = Everyday.amortisation(loan, limit = 12)
        assertEquals(12, rows.size)
        // Each row must split the EMI into interest plus principal.
        rows.forEach { row ->
            assertEquals(loan.emi, row.interest + row.principalPaid, 1e-6)
        }
        // The balance must shrink every month.
        rows.zipWithNext().forEach { (first, second) ->
            assertTrue(second.balance < first.balance)
        }
    }

    @Test
    fun bmiCategories() {
        val healthy = Everyday.bmi(70.0, 175.0)
        assertEquals("22.8571428571", Formatter.number(healthy.bmi))
        assertEquals("Normal weight", healthy.category)
        assertEquals(56.66, r(healthy.healthyMinKg, 2), 0.01)
        assertEquals(76.26, r(healthy.healthyMaxKg, 2), 0.01)

        assertEquals("Obese (class I)", Everyday.bmi(100.0, 170.0).category)
        assertEquals("Underweight", Everyday.bmi(45.0, 160.0).category)
    }

    @Test
    fun simpleAndCompoundInterest() {
        val result = Everyday.interest(10_000.0, 10.0, 2.0, 1)
        assertEquals(2000.0, result.simpleInterest, 1e-9)
        assertEquals(12_000.0, result.simpleTotal, 1e-9)
        assertEquals(2100.0, result.compoundInterest, 1e-9)
        assertEquals(12_100.0, result.compoundTotal, 1e-9)

        val quarterly = Everyday.interest(10_000.0, 10.0, 2.0, 4)
        assertEquals("2184.0289751", Formatter.number(quarterly.compoundInterest))
    }

    @Test
    fun percentageHelpers() {
        val result = Everyday.percent(15.0, 200.0)
        assertEquals(30.0, result.percentOf, 1e-9)
        assertEquals(7.5, result.whatPercent, 1e-9)
        assertEquals(1233.3333333333, result.changePercent, 1e-6)
        assertEquals(230.0, result.increase, 1e-9)
        assertEquals(170.0, result.decrease, 1e-9)
    }

    @Test
    fun successiveDiscounts() {
        val result = Everyday.discount(2000.0, 10.0, 5.0)
        assertEquals(200.0, result.discountAmount, 1e-9)
        assertEquals(90.0, result.extraAmount, 1e-9)
        assertEquals(1710.0, result.payable, 1e-9)
        assertEquals(14.5, result.effectiveDiscountPercent, 1e-9)
    }

    @Test
    fun profitAndMarginAreDifferentNumbers() {
        val result = Everyday.margin(80.0, 100.0)
        assertEquals(20.0, result.profit, 1e-9)
        assertEquals(25.0, result.profitPercent, 1e-9) // profit on cost
        assertEquals(20.0, result.marginPercent, 1e-9)  // profit on selling price
        assertEquals(1.25, result.markup, 1e-9)
    }

    @Test
    fun incomeTaxNewRegimeFy202526() {
        // Salaried at 12 lakh: standard deduction 75,000 then the 87A rebate
        // wipes out the whole liability.
        val twelveLakh = Everyday.incomeTax(1_200_000.0, Everyday.TaxRegime.NEW)
        assertEquals(1_125_000.0, twelveLakh.taxableIncome, 1e-9)
        assertEquals(52_500.0, twelveLakh.slabTax, 1e-9)
        assertEquals(52_500.0, twelveLakh.rebate, 1e-9)
        assertEquals(0.0, twelveLakh.totalTax, 1e-9)

        // 13 lakh: marginal relief keeps the tax at the income above 12 lakh.
        val thirteenLakh = Everyday.incomeTax(1_300_000.0, Everyday.TaxRegime.NEW)
        assertEquals(63_750.0, thirteenLakh.slabTax, 1e-9)
        assertEquals(0.0, thirteenLakh.rebate, 1e-9)
        assertEquals(38_750.0, thirteenLakh.marginalRelief, 1e-9)
        assertEquals(1_000.0, thirteenLakh.cess, 1e-9)
        assertEquals(26_000.0, thirteenLakh.totalTax, 1e-9)

        // 20 lakh: full slabs plus 4% cess.
        val twentyLakh = Everyday.incomeTax(2_000_000.0, Everyday.TaxRegime.NEW)
        assertEquals(185_000.0, twentyLakh.slabTax, 1e-9)
        assertEquals(7_400.0, twentyLakh.cess, 1e-9)
        assertEquals(192_400.0, twentyLakh.totalTax, 1e-9)
        assertEquals(9.62, r(twentyLakh.effectiveRatePercent), 0.01)

        // Under 12 lakh nothing is payable at all.
        assertEquals(0.0, Everyday.incomeTax(1_000_000.0, Everyday.TaxRegime.NEW).totalTax, 1e-9)
        assertEquals(0.0, Everyday.incomeTax(500_000.0, Everyday.TaxRegime.NEW).totalTax, 1e-9)
    }

    @Test
    fun incomeTaxOldRegime() {
        val sixLakh = Everyday.incomeTax(
            600_000.0,
            Everyday.TaxRegime.OLD,
            salaried = true,
            otherDeductions = 0.0
        )
        assertEquals(550_000.0, sixLakh.taxableIncome, 1e-9)
        assertEquals(22_500.0, sixLakh.slabTax, 1e-9)
        assertEquals(0.0, sixLakh.rebate, 1e-9) // above the 5 lakh rebate limit
        assertEquals(23_400.0, sixLakh.totalTax, 1e-9)

        // 80C style deductions pull the income under the old-regime rebate.
        val withDeductions = Everyday.incomeTax(600_000.0, Everyday.TaxRegime.OLD, true, 150_000.0)
        assertEquals(400_000.0, withDeductions.taxableIncome, 1e-9)
        assertEquals(0.0, withDeductions.totalTax, 1e-9)
    }

    @Test
    fun quadraticRoots() {
        val twoRoots = Everyday.quadratic(1.0, -3.0, 2.0)
        assertTrue(twoRoots.realRoots)
        assertEquals(1.0, twoRoots.discriminant, 1e-9)
        assertEquals(2.0, twoRoots.root1.re, 1e-9)
        assertEquals(1.0, twoRoots.root2.re, 1e-9)
        assertEquals(1.5, twoRoots.vertexX, 1e-9)
        assertEquals(-0.25, twoRoots.vertexY, 1e-9)
        assertEquals(3.0, twoRoots.sumOfRoots, 1e-9)
        assertEquals(2.0, twoRoots.productOfRoots, 1e-9)
    }

    @Test
    fun quadraticWithComplexRoots() {
        val complex = Everyday.quadratic(1.0, 2.0, 5.0)
        assertFalse(complex.realRoots)
        assertEquals(-16.0, complex.discriminant, 1e-9)
        assertEquals(-1.0, complex.root1.re, 1e-9)
        assertEquals(2.0, complex.root1.im, 1e-9)
        assertEquals(-2.0, complex.root2.im, 1e-9)
    }

    @Test
    fun primesAndFactors() {
        assertTrue(Everyday.isPrime(97))
        assertFalse(Everyday.isPrime(1))
        assertFalse(Everyday.isPrime(100))
        assertTrue(Everyday.isPrime(7919))
        assertEquals(listOf(2L, 2L, 2L, 3L, 3L, 5L), Everyday.primeFactors(360))
        assertEquals("2^3 × 3^2 × 5", Everyday.factorString(Everyday.primeFactors(360)))
        assertEquals("13", Everyday.factorString(Everyday.primeFactors(13)))
    }

    @Test
    fun baseConversion() {
        assertEquals("FF", Everyday.baseConvert("255", 10, 16))
        assertEquals("255", Everyday.baseConvert("11111111", 2, 10))
        assertEquals("1FF", Everyday.baseConvert("777", 8, 16))
        assertEquals("11111111", Everyday.baseConvert("FF", 16, 2))
        assertEquals("Z", Everyday.baseConvert("35", 10, 36))
        // Large values must stay exact, which is why BigInteger is used.
        assertEquals("1000000000000000000", Everyday.baseConvert("1000000000000000000", 10, 10))
    }

    @Test
    fun ageCalculation() {
        val result = Everyday.age(LocalDate.of(1998, 8, 15), LocalDate.of(2026, 10, 11))
        assertEquals(28, result.years)
        assertEquals(1, result.months)
        assertEquals(26, result.days)
        assertEquals(10_284L, result.totalDays)
        assertTrue(result.nextBirthdayInDays in 300..310)
    }

    @Test
    fun daysBetweenDatesCrossesLeapDay() {
        // 2024-02-28 to 2024-03-01 includes the leap day: two days.
        val span = Everyday.daysBetween(LocalDate.of(2024, 2, 28), LocalDate.of(2024, 3, 1))
        assertEquals(2L, span.totalDays)
        assertEquals(0, span.years)
        assertEquals(0, span.months)
        assertEquals(2, span.days)
        assertTrue(span.forward)
    }

    @Test
    fun daysBetweenDatesCountsBackwards() {
        val span = Everyday.daysBetween(LocalDate.of(2026, 10, 11), LocalDate.of(1998, 8, 15))
        assertEquals(10_284L, span.totalDays)
        assertEquals(28, span.years)
        assertEquals(1, span.months)
        assertEquals(26, span.days)
        assertFalse(span.forward)
        // Counted either way round, the distance is the same.
        assertEquals(
            Everyday.daysBetween(LocalDate.of(1998, 8, 15), LocalDate.of(2026, 10, 11)).totalDays,
            span.totalDays
        )
    }

    @Test
    fun shiftingDatesHandlesMonthEnds() {
        // 31 January plus one month has to land on the end of February.
        assertEquals(
            LocalDate.of(2024, 2, 29),
            Everyday.shiftDate(LocalDate.of(2024, 1, 31), months = 1).date
        )
        assertEquals(
            LocalDate.of(2026, 10, 1),
            Everyday.shiftDate(LocalDate.of(2026, 10, 11), days = -10).date
        )
        val shifted = Everyday.shiftDate(LocalDate.of(2026, 10, 15), weeks = 2)
        assertEquals(LocalDate.of(2026, 10, 29), shifted.date)
        assertEquals("Thursday", shifted.dayOfWeek)
        assertEquals(31, shifted.daysInMonth)
    }

    @Test
    fun dateParsing() {
        assertEquals(LocalDate.of(1998, 8, 15), Everyday.parseDate("15/08/1998"))
        assertEquals(LocalDate.of(1998, 8, 15), Everyday.parseDate("15-08-1998"))
        assertEquals(LocalDate.of(1998, 8, 15), Everyday.parseDate("1998-08-15"))
        assertEquals(LocalDate.of(2000, 1, 31), Everyday.parseDate("31.01.2000"))
        try {
            Everyday.parseDate("nonsense")
            throw AssertionError("expected a MathError")
        } catch (expected: MathError) {
            assertTrue(expected.message!!.contains("date like"))
        }
    }
}
