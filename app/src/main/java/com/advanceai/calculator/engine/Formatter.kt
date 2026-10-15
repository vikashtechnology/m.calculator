package com.advanceai.calculator.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Turns values into the short, readable strings the display shows.
 *
 * Results are rounded to [SIGNIFICANT_DIGITS] significant digits before printing
 * so that binary floating point noise (0.30000000000000004) never reaches the user.
 */
object Formatter {

    const val SIGNIFICANT_DIGITS = 12
    private const val MAX_SCI_DIGITS = 10
    private const val LARGE_THRESHOLD = 1e15
    private const val SMALL_THRESHOLD = 1e-9

    fun number(
        x: Double,
        polarAngleMode: AngleMode? = null,
        display: DisplayOptions = DisplayOptions.DEFAULT
    ): String {
        if (x.isNaN()) return "Undefined"
        if (x.isInfinite()) return if (x > 0) "∞" else "-∞"
        if (x == 0.0) return "0"

        val ax = Math.abs(x)
        if (ax >= LARGE_THRESHOLD || ax < SMALL_THRESHOLD) {
            return scientific(x)
        }
        val digits = display.significantDigits.coerceIn(1, 15)
        val rounded = BigDecimal(x).round(MathContext(digits, RoundingMode.HALF_UP))
        val plain = rounded.stripTrailingZeros()
        val text = if (plain.scale() <= 0) {
            plain.toBigInteger().toString()
        } else {
            plain.toPlainString()
        }
        val plainText = if (text == "-0") "0" else text
        return group(plainText, display.indianGrouping)
    }

    /** True for the digits-and-separators text the grouping helper accepts. */
    private val GROUPABLE = Regex("^[+-]?\\d+(\\.\\d+)?$")

    /**
     * Adds thousands separators — either the Western 1,234,567 or the Indian
     * 12,34,567, where only the last three digits form a group of three and the
     * rest pair up.
     */
    fun group(text: String, indian: Boolean = false): String {
        if (!GROUPABLE.matches(text)) return text
        val negative = text.startsWith("-")
        val body = text.removePrefix("-").removePrefix("+")
        val dot = body.indexOf('.')
        val whole = if (dot < 0) body else body.substring(0, dot)
        val rest = if (dot < 0) "" else body.substring(dot)
        if (whole.length <= 3) return text
        val tail = whole.takeLast(3)
        val head = whole.dropLast(3)
        val groupedHead = if (indian) {
            head.reversed().chunked(2).joinToString(",").reversed()
        } else {
            head.reversed().chunked(3).joinToString(",").reversed()
        }
        return (if (negative) "-" else "") + groupedHead + "," + tail + rest
    }

    private fun scientific(x: Double): String {
        val rounded = BigDecimal(x).round(MathContext(MAX_SCI_DIGITS, RoundingMode.HALF_UP))
        val text = String.format("%.9E", rounded.toDouble())
        val (mantissaRaw, exponentRaw) = text.split("E")
        var mantissa = mantissaRaw.trimEnd('0').trimEnd('.')
        if (mantissa.isEmpty()) mantissa = "0"
        val exponent = exponentRaw.toInt()
        return "${mantissa}e${exponent}"
    }

    fun value(
        v: Value,
        polarAngleMode: AngleMode? = null,
        display: DisplayOptions = DisplayOptions.DEFAULT
    ): String = when (v) {
        is Num -> if (v.isRealScalar || isNegligible(v.im, v.re)) {
            number(v.re, display = display)
        } else {
            complex(v, polarAngleMode, display)
        }
        is Mat -> v.displayString()
    }

    /**
     * True when [part] is only floating point dust next to [other], e.g. the
     * 1.2e-16 real part that shows up when expanding (1+i)².
     */
    private fun isNegligible(part: Double, other: Double): Boolean =
        Math.abs(part) <= 1e-13 * Math.max(1.0, Math.abs(other))

    private fun complex(
        v: Num,
        polarAngleMode: AngleMode?,
        display: DisplayOptions = DisplayOptions.DEFAULT
    ): String {
        if (polarAngleMode != null) {
            val angle = polarAngleMode.fromRadians(v.arg)
            return "${number(v.abs, display = display)} ∠ ${number(angle, display = display)}°"
        }
        val realPart = if (isNegligible(v.re, v.im)) 0.0 else v.re
        val imagPart = if (isNegligible(v.im, v.re)) 0.0 else v.im
        val re = number(realPart, display = display)
        val imAbs = number(Math.abs(imagPart), display = display)
        return when {
            realPart == 0.0 -> if (imagPart < 0) "-${imAbs}i" else "${imAbs}i"
            imagPart < 0 -> "$re - ${imAbs}i"
            else -> "$re + ${imAbs}i"
        }
    }

    /**
     * Looks for a small, exact-looking fraction equal to (or very close to) [x].
     * Used to show a "≈ 1/3" hint under decimal results.
     */
    fun fractionHint(x: Double, maxDenominator: Int = 10_000): String? {
        if (x.isNaN() || x.isInfinite()) return null
        if (Math.abs(x) >= 1e6) return null
        if (Math.rint(x) == x) return null
        // Already a clean decimal (2.5, 0.375): a fraction hint adds no value.
        if (BigDecimal(x).stripTrailingZeros().scale() <= 6) return null

        val tolerance = Math.abs(x) * 1e-11 + 1e-13
        val negative = x < 0
        val target = Math.abs(x)
        // Search the best rational approximation up to maxDenominator.
        var bestN = 0L
        var bestD = 1L
        var bestError = Double.MAX_VALUE
        for (d in 1..maxDenominator) {
            val n = Math.round(target * d)
            val error = Math.abs(target - n.toDouble() / d)
            if (error < bestError) {
                bestError = error
                bestN = n
                bestD = d.toLong()
            }
            if (bestError <= tolerance && n > 0) break
        }
        if (bestError > tolerance) return null
        if (bestN == 0L) return null
        val g = gcd(bestN, bestD)
        var num = bestN / g
        var den = bestD / g
        if (den == 1L) return null
        if (negative) num = -num
        // Ignore the trivial false positives that are "close" for the wrong reason.
        if (Math.abs(num.toDouble() / den - x) > tolerance) return null
        return if (Math.abs(num) >= den && den != 1L) {
            val whole = if (num >= 0) num / den else -((-num) / den)
            val rem = Math.abs(num - whole * den)
            if (whole != 0L && rem != 0L) "$whole $rem/$den" else "$num/$den"
        } else {
            "$num/$den"
        }
    }

    private fun gcd(a: Long, b: Long): Long {
        var x = a
        var y = b
        while (y != 0L) {
            val t = x % y
            x = y
            y = t
        }
        return if (x == 0L) 1L else x
    }

    /** Formats a value for the history list, keeping it on one line. */
    fun compact(v: Value, display: DisplayOptions = DisplayOptions.DEFAULT): String = when (v) {
        is Num -> if (v.isRealScalar) {
            number(v.re, display = display)
        } else {
            complex(v, null, display).replace(" ", "")
        }
        is Mat -> v.displayString()
    }
}
