package com.vikashtechnology.mcalculator.engine

import kotlin.math.abs
import kotlin.math.max

/** Numerical calculus, series and equation solving. */
object Calculus {

    private const val MAX_SAMPLES = 100_000

    /** Five-point centred difference: accurate and cheap. */
    fun derivative(expr: Expr, variable: String, at: Double, ctx: MathContext): Double {
        val h = 1e-5 * max(1.0, abs(at))
        val f = { x: Double -> value(expr, variable, x, ctx) }
        val y1 = f(at - 2 * h)
        val y2 = f(at - h)
        val y3 = f(at + h)
        val y4 = f(at + 2 * h)
        val result = (-y4 + 8 * y3 - 8 * y2 + y1) / (12 * h)
        return if (abs(result) < 1e-12) 0.0 else result
    }

    /** Numerical second derivative, useful for the "d²/dx²" button. */
    fun secondDerivative(expr: Expr, variable: String, at: Double, ctx: MathContext): Double {
        val h = 1e-4 * max(1.0, abs(at))
        val f = { x: Double -> value(expr, variable, x, ctx) }
        return (f(at + h) - 2 * f(at) + f(at - h)) / (h * h)
    }

    /** Composite Simpson rule with an even number of panels. */
    fun integrate(expr: Expr, variable: String, from: Double, to: Double, ctx: MathContext): Double {
        if (from == to) return 0.0
        val (a, b, sign) = if (from < to) Triple(from, to, 1.0) else Triple(to, from, -1.0)
        val panels = 2048
        val h = (b - a) / panels
        if (h == 0.0) return 0.0
        val f = { x: Double -> value(expr, variable, x, ctx) }

        var sum = f(a) + f(b)
        for (i in 1 until panels) {
            val x = a + i * h
            sum += if (i % 2 == 0) 2 * f(x) else 4 * f(x)
        }
        val result = sum * h / 3.0
        // Refine once and compare for a sanity check on the error.
        val halfPanels = panels * 2
        val h2 = (b - a) / halfPanels
        var sum2 = f(a) + f(b)
        for (i in 1 until halfPanels) {
            val x = a + i * h2
            sum2 += if (i % 2 == 0) 2 * f(x) else 4 * f(x)
        }
        val refined = sum2 * h2 / 3.0
        val best = if (abs(refined - result) < abs(result)) refined else result
        return sign * best
    }

    /** Sum or product of an expression over an integer range. */
    fun series(
        expr: Expr,
        variable: String,
        from: Double,
        to: Double,
        ctx: MathContext,
        product: Boolean = false
    ): Double {
        val start = Math.ceil(from).toLong()
        val end = Math.floor(to).toLong()
        if (end < start) return if (product) 1.0 else 0.0
        val steps = end - start + 1
        if (steps > 1_000_000) throw MathError("Series range is too large (max 1,000,000 steps)")
        var acc = if (product) 1.0 else 0.0
        var i = start
        while (i <= end) {
            val v = value(expr, variable, i.toDouble(), ctx)
            acc = if (product) acc * v else acc + v
            i++
        }
        return acc
    }

    /**
     * Finds real roots of `expr = 0` in [min, max]. Newton's method is tried from
     * the user's guess first, then a bracketing scan is used as a safety net.
     */
    fun solve(
        expr: Expr,
        variable: String,
        min: Double,
        max: Double,
        guess: Double,
        ctx: MathContext,
        maxRoots: Int = 12
    ): List<Double> {
        val roots = ArrayList<Double>()
        val f = { x: Double -> value(expr, variable, x, ctx) }

        fun addRoot(x: Double) {
            if (x.isNaN() || x.isInfinite()) return
            if (x < min - 1e-9 || x > max + 1e-9) return
            if (roots.none { abs(it - x) < 1e-7 * max(1.0, abs(x)) }) roots.add(x)
        }

        // 1. Newton-Raphson from the provided guess.
        var x = guess.coerceIn(min, max)
        var converged = false
        repeat(80) {
            val fx = runCatching { f(x) }.getOrNull() ?: return@repeat
            if (abs(fx) < 1e-13) {
                converged = true
                return@repeat
            }
            val h = 1e-6 * max(1.0, abs(x))
            val dfx = (runCatching { f(x + h) }.getOrNull() ?: return@repeat) -
                (runCatching { f(x - h) }.getOrNull() ?: return@repeat)
            val slope = dfx / (2 * h)
            if (slope == 0.0 || slope.isNaN()) return@repeat
            val next = x - fx / slope
            if (next.isNaN() || next.isInfinite()) return@repeat
            x = next.coerceIn(min, max)
        }
        if (converged && abs(runCatching { f(x) }.getOrDefault(Double.NaN)) < 1e-9) addRoot(x)

        // 2. Sign-change scan across the interval.
        val samples = 2000
        val step = (max - min) / samples
        if (step > 0) {
            var prevX = min
            var prevF = runCatching { f(prevX) }.getOrNull()
            for (i in 1..samples) {
                val curX = min + i * step
                val curF = runCatching { f(curX) }.getOrNull()
                if (prevF != null && curF != null && prevF != 0.0 && curF != 0.0 &&
                    (prevF > 0) != (curF > 0)
                ) {
                    var lo = prevX
                    var hi = curX
                    repeat(120) {
                        val mid = (lo + hi) / 2
                        val fmid = runCatching { f(mid) }.getOrNull() ?: return@repeat
                        if ((f(lo) > 0) != (fmid > 0)) hi = mid else lo = mid
                    }
                    addRoot((lo + hi) / 2)
                    if (roots.size >= maxRoots) return@repeat
                }
                prevX = curX
                prevF = curF
            }
        }
        return roots.sorted()
    }

    /** Samples y = f(x) for plotting. `null` marks a point outside the domain. */
    fun samples(
        expr: Expr,
        variable: String,
        xMin: Double,
        xMax: Double,
        count: Int,
        ctx: MathContext
    ): DoubleArray {
        val n = count.coerceIn(2, 4000)
        val out = DoubleArray(n)
        for (i in 0 until n) {
            val x = xMin + (xMax - xMin) * i / (n - 1)
            out[i] = runCatching { value(expr, variable, x, ctx) }
                .getOrDefault(Double.NaN)
                .let { if (it.isInfinite()) Double.NaN else it }
        }
        return out
    }

    private fun value(expr: Expr, variable: String, x: Double, ctx: MathContext): Double {
        val previous = ctx.variables[variable]
        ctx.variables[variable] = x
        try {
            return Evaluator.realOrNull(Evaluator.eval(expr, ctx), "Expression")
        } finally {
            if (previous == null) ctx.variables.remove(variable) else ctx.variables[variable] = previous
        }
    }

    /** Descriptive statistics for the statistics tool. */
    fun describe(data: DoubleArray): StatsSummary {
        if (data.isEmpty()) throw MathError("No data points")
        val sorted = data.sortedArray()
        val n = sorted.size
        val mean = sorted.sum() / n
        val varianceSample = if (n > 1) sorted.sumOf { (it - mean) * (it - mean) } / (n - 1) else 0.0
        val variancePopulation = sorted.sumOf { (it - mean) * (it - mean) } / n
        val median = if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
        return StatsSummary(
            count = n,
            sum = sorted.sum(),
            mean = mean,
            median = median,
            min = sorted.first(),
            max = sorted.last(),
            range = sorted.last() - sorted.first(),
            stdevSample = Math.sqrt(varianceSample),
            stdevPopulation = Math.sqrt(variancePopulation),
            varianceSample = varianceSample,
            q1 = percentile(sorted, 0.25),
            q3 = percentile(sorted, 0.75)
        )
    }

    private fun percentile(sorted: DoubleArray, p: Double): Double {
        if (sorted.isEmpty()) return Double.NaN
        val index = (sorted.size - 1) * p
        val lo = Math.floor(index).toInt()
        val hi = Math.ceil(index).toInt()
        if (lo == hi) return sorted[lo]
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (index - lo)
    }
}

data class StatsSummary(
    val count: Int,
    val sum: Double,
    val mean: Double,
    val median: Double,
    val min: Double,
    val max: Double,
    val range: Double,
    val stdevSample: Double,
    val stdevPopulation: Double,
    val varianceSample: Double,
    val q1: Double,
    val q3: Double
)
