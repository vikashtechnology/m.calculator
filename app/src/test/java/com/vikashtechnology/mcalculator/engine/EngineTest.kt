package com.vikashtechnology.mcalculator.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden-value tests for the math engine. Expected strings come from
 * `tools/reference.py`, which applies the same rounding rules documented in
 * [Formatter] to values computed with Python's math module.
 */
class EngineTest {

    private fun eval(input: String, angle: AngleMode = AngleMode.DEG): String {
        val ctx = MathContext(angle)
        return when (val result = CalculatorEngine.evaluate(input, ctx)) {
            is EngineResult.Ok -> result.text
            is EngineResult.Err -> "ERROR: ${result.message}"
        }
    }

    private fun check(expr: String, expected: String, angle: AngleMode = AngleMode.DEG) {
        assertEquals("for '$expr'", expected, eval(expr, angle))
    }

    @Test
    fun operatorPrecedenceAndImplicitMultiplication() {
        check("2+3*4", "14")
        check("2+3(4+1)", "17")
        check("(1+2)(3+4)", "21")
        check("-2^2", "-4")
        check("2^3^2", "512")
        check("2^-3", "0.125")
        check("10/4", "2.5")
        check("2π", "6.28318530718")
        check("3(4)", "12")
        check("2sin(30)", "1")
    }

    @Test
    fun floatingPointNoiseIsRoundedAway() {
        check("0.1+0.2", "0.3")
        check("(0.1+0.2)*3", "0.9")
        check("1/3", "0.333333333333")
        check("2/3", "0.666666666667")
        check("sqrt(9)-3", "0")
    }

    @Test
    fun rootsAndPowers() {
        check("sqrt(2)", "1.41421356237")
        check("sqrt(10)", "3.16227766017")
        check("cbrt(27)", "3")
        check("nthroot(27,3)", "3")
        check("5^0.5", "2.2360679775")
        check("7^7", "823543")
        check("10^16", "1e16")
    }

    @Test
    fun trigonometryRespectsAngleMode() {
        check("sin(30)", "0.5", AngleMode.DEG)
        check("cos(60)+0.5", "1", AngleMode.DEG)
        check("tan(45)", "1", AngleMode.DEG)
        check("asin(0.5)", "30", AngleMode.DEG)
        check("atan2(1,1)", "45", AngleMode.DEG)
        check("sin(pi/2)", "1", AngleMode.RAD)
        check("cos(pi)", "-1", AngleMode.RAD)
        check("tan(180)", "0", AngleMode.DEG)
        check("sin(100)", "1", AngleMode.GRAD)
        check("sinh(0)", "0")
        check("cosh(0)", "1")
        check("acosh(1)", "0")
    }

    @Test
    fun logarithmsAndExponentials() {
        check("ln(e^3)", "3")
        check("log(1000)", "3")
        check("log(8,2)", "3")
        check("exp(1)", "2.71828182846")
        check("ln(10)", "2.30258509299")
        check("log2(1024)", "10")
    }

    @Test
    fun combinatoricsAndNumberTheory() {
        check("5!", "120")
        check("0.5!", "0.886226925453")
        check("gamma(5)", "24")
        check("ncr(5,2)", "10")
        check("npr(5,2)", "20")
        check("gcd(12,18)", "6")
        check("lcm(4,6)", "12")
        check("fib(10)", "55")
        check("100!/98!", "9900")
    }

    @Test
    fun percentAndModulo() {
        check("50%", "0.5")
        check("50%+2", "2.5")
        check("10%3", "1")
        check("mod(10,3)", "1")
        check("mod(-1,3)", "2")
    }

    @Test
    fun roundingHelpers() {
        check("round(2.567,2)", "2.57")
        check("round(pi,4)", "3.1416")
        check("floor(-2.5)", "-3")
        check("ceil(-2.5)", "-2")
        check("floor(7.999)", "7")
        check("abs(-7)", "7")
        check("sign(-3)", "-1")
        check("trunc(-2.7)", "-2")
        check("frac(-2.25)", "-0.25")
    }

    @Test
    fun aggregates() {
        check("mean(1,2,3,4)", "2.5")
        check("median(1,2,3)", "2")
        check("stdev(2,4,4,4,5,5,7,9)", "2.1380899353")
        check("sum(1,2,3)", "6")
        check("product(1,2,3,4)", "24")
        check("min(3,1,2)", "1")
        check("max(3,1,2)", "3")
        check("hypot(3,4)", "5")
    }

    @Test
    fun constants() {
        check("pi", "3.14159265359")
        check("tau", "6.28318530718")
        check("phi", "1.61803398875")
        check("2e3", "2000")
        check("1.5e-3", "0.0015")
        check("1e-12", "1e-12")
        check("1e16", "1e16")
    }

    @Test
    fun angleConversionHelpers() {
        check("deg(pi)", "180")
        check("rad(180)", "3.14159265359")
    }

    @Test
    fun complexArithmetic() {
        check("i^2", "-1")
        check("sqrt(-4)", "2i")
        check("(1+i)^2", "2i")
        check("abs(3-4i)", "5")
        check("re(3-4i)", "3")
        check("im(3-4i)", "-4")
        check("conj(2+3i)", "2 - 3i")
        check("(1+i)*(1-i)", "2")
        check("ln(-1)", "3.14159265359i")
        check("arg(1+i)", "45")
        // Complex results stay in radians: the angle mode only applies to real arguments.
        check("asin(2)", "1.57079632679 - 1.31695789692i")
        check("sin(1+i)", "1.29845758142 + 0.634963914785i")
    }

    @Test
    fun matrixOperations() {
        check("det([1,2;3,4])", "-2")
        check("trace([1,2;3,4])", "5")
        check("inv([2,0;0,2])", "[[0.5, 0], [0, 0.5]]")
        check("[1,2;3,4]*[1,0;0,1]", "[[1, 2], [3, 4]]")
        check("transpose([1,2;3,4])", "[[1, 3], [2, 4]]")
        check("identity(3)", "[[1, 0, 0], [0, 1, 0], [0, 0, 1]]")
        check("dims([1,2,3;4,5,6])", "[[2, 3]]")
        check("dot([1,2,3],[4,5,6])", "32")
        check("rank([1,2;2,4])", "1")
        check("[1,2;3,4]+[1,1;1,1]", "[[2, 3], [4, 5]]")
        check("[1,2,3]*2", "[[2, 4, 6]]")
    }

    @Test
    fun variablesAreSharedThroughTheContext() {
        val ctx = MathContext(AngleMode.DEG)
        ctx.setVariable("a", 5.0)
        ctx.setVariable("b", 7.0)
        val result = CalculatorEngine.evaluate("a*b+1", ctx) as EngineResult.Ok
        assertEquals("36", result.text)
        // Unknown single letters start at zero so they never block an expression.
        check("z+1", "1")
    }

    @Test
    fun friendlyErrors() {
        assertTrue(eval("1/0").startsWith("ERROR: Division by zero"))
        assertTrue(eval("2+").startsWith("ERROR:"))
        assertTrue(eval("foo(2)").startsWith("ERROR: Unknown function"))
        assertTrue(eval("qqq").startsWith("ERROR: Unknown variable"))
        assertTrue(eval("(1+2").startsWith("ERROR:"))
        assertTrue(eval("ln(0)").startsWith("ERROR:"))
        assertTrue(eval("(-1)!").startsWith("ERROR: Factorial of a negative integer"))
        assertTrue(eval("det([1,2,3])").startsWith("ERROR: det requires a square matrix"))
    }

    @Test
    fun fractionHints() {
        assertEquals("1/3", Formatter.fractionHint(1.0 / 3.0))
        assertEquals("1/6", Formatter.fractionHint(1.0 / 6.0))
        assertEquals("1/7", Formatter.fractionHint(1.0 / 7.0))
        assertEquals("22/7", Formatter.fractionHint(22.0 / 7.0))

        // Clean decimals and irrational-looking numbers get no hint.
        assertEquals(null, Formatter.fractionHint(0.375))
        assertEquals(null, Formatter.fractionHint(2.5))
        assertEquals(null, Formatter.fractionHint(1.23456789))
    }

    @Test
    fun numberFormattingEdgeCases() {
        assertEquals("0", Formatter.number(0.0))
        assertEquals("0", Formatter.number(-0.0))
        assertEquals("-42", Formatter.number(-42.0))
        assertEquals("1e-12", Formatter.number(1e-12))
        assertEquals("1e16", Formatter.number(1e16))
        assertEquals("2.5e-5", Formatter.number(2.5e-5))
        assertEquals("Infinity", Formatter.number(Double.POSITIVE_INFINITY).replace("∞", "Infinity"))
        assertEquals("123456789012", Formatter.number(123456789012.0))
        // Rounding must not produce a trailing ".0".
        assertEquals("3", Formatter.number(3.0))
    }

    @Test
    fun matrixLiteralErrorsAreHelpful() {
        assertTrue(eval("[1,2;3]").startsWith("ERROR: All matrix rows must have the same length"))
        assertTrue(eval("[1,2] + [1,2;3,4]").startsWith("ERROR:"))
        assertTrue(eval("inv([1,2;2,4])").startsWith("ERROR: Matrix is singular"))
        assertTrue(eval("[1,2;3,4] * [1,2,3]").startsWith("ERROR: Cannot multiply"))
    }

    @Test
    fun unitConversion() {
        val km = Units.find("km")!!
        val mi = Units.find("mi")!!
        val cm = Units.find("in")!!
        val mm = Units.find("cm")!!
        val degC = Units.find("degC")!!
        val degF = Units.find("degF")!!
        val mph = Units.find("mph")!!
        val kph = Units.find("kph")!!

        assertEquals("0.621371192237", Formatter.number(Units.convert(1.0, km, mi), AngleMode.DEG))
        assertEquals(2.54, Units.convert(1.0, cm, mm), 1e-12)
        assertEquals(212.0, Units.convert(100.0, degC, degF), 1e-9)
        assertEquals(-40.0, Units.convert(-40.0, degC, degF), 1e-9)
        assertEquals(96.56064, Units.convert(60.0, mph, kph), 1e-9)
    }
}
