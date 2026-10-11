package com.vikashtechnology.mcalculator.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Numerical calculus, series, solving and plotting helpers. */
class CalculusTest {

    private val ctx = MathContext(AngleMode.RAD)

    @Test
    fun derivatives() {
        val parabola = CalculatorEngine.parse("x^2")
        assertEquals(6.0, Calculus.derivative(parabola, "x", 3.0, ctx), 1e-6)

        val sine = CalculatorEngine.parse("sin(x)")
        assertEquals(1.0, Calculus.derivative(sine, "x", 0.0, ctx), 1e-6)
        assertEquals(-1.0, Calculus.derivative(sine, "x", Math.PI, ctx), 1e-5) // d/dx sin = cos, cos(π) = -1

        val cubic = CalculatorEngine.parse("x^3 - 3x + 1")
        assertEquals(0.0, Calculus.derivative(cubic, "x", 1.0, ctx), 1e-6)

        assertEquals(2.0, Calculus.secondDerivative(parabola, "x", 5.0, ctx), 1e-4)
    }

    @Test
    fun integrals() {
        val xSquared = CalculatorEngine.parse("x^2")
        assertEquals(9.0, Calculus.integrate(xSquared, "x", 0.0, 3.0, ctx), 1e-6)

        val sine = CalculatorEngine.parse("sin(x)")
        assertEquals(2.0, Calculus.integrate(sine, "x", 0.0, Math.PI, ctx), 1e-6)

        val gaussian = CalculatorEngine.parse("exp(-x^2)")
        assertEquals(1.7724539, Calculus.integrate(gaussian, "x", -10.0, 10.0, ctx), 1e-4)

        // Reversing the limits flips the sign.
        assertEquals(-9.0, Calculus.integrate(xSquared, "x", 3.0, 0.0, ctx), 1e-6)
    }

    @Test
    fun series() {
        val k = CalculatorEngine.parse("k")
        assertEquals(55.0, Calculus.series(k, "k", 1.0, 10.0, ctx), 1e-9)

        val squared = CalculatorEngine.parse("k^2")
        assertEquals(385.0, Calculus.series(squared, "k", 1.0, 10.0, ctx), 1e-9)

        val factorialTerm = CalculatorEngine.parse("1/k!")
        assertEquals(Math.E - 1, Calculus.series(factorialTerm, "k", 1.0, 20.0, ctx), 1e-9)

        assertEquals(120.0, Calculus.series(k, "k", 1.0, 5.0, ctx, product = true), 1e-9)
    }

    @Test
    fun solvingFindsBothRoots() {
        val expr = CalculatorEngine.parse("x^2 - 4")
        val roots = Calculus.solve(expr, "x", -10.0, 10.0, 1.0, ctx)
        assertEquals(2, roots.size)
        assertEquals(-2.0, roots[0], 1e-6)
        assertEquals(2.0, roots[1], 1e-6)
    }

    @Test
    fun solvingTrigEquation() {
        val expr = CalculatorEngine.parse("sin(x) - 0.5")
        val roots = Calculus.solve(expr, "x", 0.0, Math.PI, 0.5, ctx)
        assertTrue(roots.isNotEmpty())
        assertEquals(Math.PI / 6, roots.first(), 1e-6)
    }

    @Test
    fun samplesMarkUndefinedPoints() {
        val logExpr = CalculatorEngine.parse("ln(x)")
        val samples = Calculus.samples(logExpr, "x", -2.0, 2.0, 11, ctx)
        assertEquals(11, samples.size)
        assertTrue(samples[0].isNaN())        // ln(-2) is outside the domain
        assertTrue(samples.last().isFinite()) // ln(2) is fine
    }

    @Test
    fun statisticsSummary() {
        val data = doubleArrayOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)
        val summary = Calculus.describe(data)
        assertEquals(8, summary.count)
        assertEquals(5.0, summary.mean, 1e-12)
        assertEquals(4.5, summary.median, 1e-12) // (4 + 5) / 2 for an even count
        assertEquals(2.0, summary.min, 1e-12)
        assertEquals(9.0, summary.max, 1e-12)
        assertEquals(2.138089935299395, summary.stdevSample, 1e-12)
    }
}
