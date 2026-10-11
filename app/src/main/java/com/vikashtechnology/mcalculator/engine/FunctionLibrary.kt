package com.vikashtechnology.mcalculator.engine

import kotlin.math.abs
import kotlin.math.cosh
import kotlin.math.log2
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * The built-in function library. Names are matched lower-case and every entry
 * validates its own argument count and types so errors read well on screen.
 */
object FunctionLibrary {

    fun call(name: String, args: List<Value>, ctx: MathContext, pos: Int): Value {
        return try {
            dispatch(name, args, ctx)
        } catch (e: MathError) {
            if (e.position >= 0) throw e
            throw MathError(e.message ?: "Math error", pos)
        }
    }

    private fun dispatch(name: String, args: List<Value>, ctx: MathContext): Value = when (name) {
        // --- trigonometry (angle mode aware) ---
        "sin" -> Num(trigOut(ComplexMath.sin(angleIn(one(args, name), ctx), ctx), ctx))
        "cos" -> Num(trigOut(ComplexMath.cos(angleIn(one(args, name), ctx), ctx), ctx))
        "tan" -> Num(trigOut(ComplexMath.tan(angleIn(one(args, name), ctx), ctx), ctx))
        "csc" -> Num(trigOut(recip(ComplexMath.sin(angleIn(one(args, name), ctx), ctx)), ctx))
        "sec" -> Num(trigOut(recip(ComplexMath.cos(angleIn(one(args, name), ctx), ctx)), ctx))
        "cot" -> Num(trigOut(recip(ComplexMath.tan(angleIn(one(args, name), ctx), ctx)), ctx))

        "asin", "arcsin" -> Num(angleOut(ComplexMath.asin(unit(args, name)), ctx))
        "acos", "arccos" -> Num(angleOut(ComplexMath.acos(unit(args, name)), ctx))
        "atan", "arctan" -> Num(angleOut(ComplexMath.atan(unit(args, name)), ctx))
        "asec" -> Num(angleOut(ComplexMath.acos(recip(unit(args, name))), ctx))
        "acsc" -> Num(angleOut(ComplexMath.asin(recip(unit(args, name))), ctx))
        "acot" -> Num(angleOut(ComplexMath.atan(recip(unit(args, name))), ctx))

        "sinh" -> Num(trigOut(ComplexMath.sinh(realComplex(one(args, name), name)), ctx))
        "cosh" -> Num(trigOut(ComplexMath.cosh(realComplex(one(args, name), name)), ctx))
        "tanh" -> Num(trigOut(ComplexMath.tanh(realComplex(one(args, name), name)), ctx))
        "csch" -> Num(trigOut(recip(ComplexMath.sinh(realComplex(one(args, name), name))), ctx))
        "sech" -> Num(trigOut(recip(ComplexMath.cosh(realComplex(one(args, name), name))), ctx))
        "coth" -> Num(trigOut(recip(ComplexMath.tanh(realComplex(one(args, name), name))), ctx))

        "asinh" -> Num(ComplexMath.asinh(one(args, name)))
        "acosh" -> Num(ComplexMath.acosh(one(args, name)))
        "atanh" -> Num(ComplexMath.atanh(one(args, name)))

        "atan2" -> {
            need(args, 2, name)
            val y = real(args[0], name)
            val x = real(args[1], name)
            Num(ctx.angleMode.fromRadians(Math.atan2(y, x)))
        }

        // --- exponentials and logarithms ---
        "exp" -> Num(ComplexMath.exp(one(args, name)))
        "exp2" -> Num(ComplexMath.pow(Num.TWO, one(args, name)))
        "exp10" -> Num(ComplexMath.pow(Num(10.0), one(args, name)))
        "ln" -> Num(ComplexMath.log(positiveCheck(one(args, name), name)))
        "log" -> when (args.size) {
            1 -> Num(ComplexMath.log10(positiveCheck(args[0], name)))
            2 -> {
                val value = positiveCheck(args[0], name)
                val base = positiveCheck(args[1], name)
                Num(ComplexMath.div(ComplexMath.log(value), ComplexMath.log(base)))
            }
            else -> throw MathError("log expects 1 or 2 arguments")
        }
        "log2" -> Num(ComplexMath.div(ComplexMath.log(positiveCheck(one(args, name), name)), Num(log2(2.0))))
        "log10" -> Num(ComplexMath.log10(positiveCheck(one(args, name), name)))

        // --- powers and roots ---
        "sqrt" -> Num(ComplexMath.sqrt(one(args, name)))
        "cbrt" -> Num(one(args, name).let { if (it.isRealScalar) Num(Math.cbrt(it.re)) else ComplexMath.pow(it, Num(1.0 / 3.0)) })
        "nthroot", "root" -> {
            need(args, 2, name)
            val x = one(listOf(args[0]), name)
            val n = real(args[1], name)
            if (n == 0.0) throw MathError("Root of degree zero is undefined")
            if (x.im == 0.0 && x.re < 0 && Math.rint(n) == n && n % 2.0 != 0.0) {
                Num(-Math.pow(-x.re, 1.0 / n))
            } else {
                Num(ComplexMath.pow(x, Num(1.0 / n)))
            }
        }
        "pow" -> {
            need(args, 2, name)
            Num(ComplexMath.pow(one(listOf(args[0]), name), one(listOf(args[1]), name)))
        }
        "hypot" -> {
            val list = realsFrom(args, name)
            Num(sqrt(list.sumOf { it * it }))
        }

        // --- rounding and sign ---
        "abs" -> when (val v = one(args, name)) {
            is Num -> Num(v.abs)
            is Mat -> Num(MatrixMath.frobeniusNorm(v))
        }
        "sign", "sgn" -> Num(Math.signum(real(one(args, name), name)))
        "floor" -> Num(Math.floor(real(one(args, name), name)))
        "ceil" -> Num(Math.ceil(real(one(args, name), name)))
        "trunc" -> Num(truncate(real(one(args, name), name)))
        "frac" -> Num(real(one(args, name), name).let { it - truncate(it) })
        "round" -> when (args.size) {
            1 -> Num(roundTo(real(args[0], name), 0))
            2 -> Num(roundTo(real(args[0], name), real(args[1], name).toInt()))
            else -> throw MathError("round expects 1 or 2 arguments")
        }

        // --- combinatorics and number theory ---
        "fact", "factorial" -> Num(Factorials.factorial(real(one(args, name), name)))
        "gamma" -> Num(Factorials.gamma(real(one(args, name), name)))
        "lgamma", "loggamma" -> Num(Factorials.logGamma(real(one(args, name), name)))
        "ncr", "c", "comb" -> {
            need(args, 2, name)
            Num(Factorials.nCr(real(args[0], name), real(args[1], name)))
        }
        "npr", "p", "perm" -> {
            need(args, 2, name)
            Num(Factorials.nPr(real(args[0], name), real(args[1], name)))
        }
        "gcd" -> {
            val list = realsFrom(args, name)
            if (list.isEmpty()) throw MathError("gcd expects at least one argument")
            Num(list.reduce { a, b -> Factorials.gcd(a, b) })
        }
        "lcm" -> {
            val list = realsFrom(args, name)
            if (list.isEmpty()) throw MathError("lcm expects at least one argument")
            Num(list.reduce { a, b -> Factorials.lcm(a, b) })
        }
        "mod" -> {
            need(args, 2, name)
            val a = real(args[0], name)
            val b = real(args[1], name)
            if (b == 0.0) throw MathError("mod by zero")
            Num(a - b * Math.floor(a / b))
        }

        // --- aggregates ---
        "min" -> Num(realsFrom(args, name).min())
        "max" -> Num(realsFrom(args, name).max())
        "sum" -> Num(realsFrom(args, name).sum())
        "mean", "avg" -> {
            val list = realsFrom(args, name)
            Num(list.sum() / list.size)
        }
        "median" -> Num(median(realsFrom(args, name)))
        "stdev", "stddev" -> Num(sqrt(variance(realsFrom(args, name), sample = true)))
        "variance", "var" -> Num(variance(realsFrom(args, name), sample = true))
        "count" -> Num(realsFrom(args, name).size.toDouble())
        "product", "prod" -> Num(realsFrom(args, name).fold(1.0) { acc, v -> acc * v })

        // --- angle conversion ---
        "deg" -> Num(Math.toDegrees(real(one(args, name), name)))
        "rad" -> Num(Math.toRadians(real(one(args, name), name)))

        // --- complex helpers ---
        "re" -> Num(complexOf(one(args, name), name).re)
        "im" -> Num(complexOf(one(args, name), name).im)
        "conj", "conjugate" -> Num(ComplexMath.conj(complexOf(one(args, name), name)))
        "arg" -> Num(ctx.angleMode.fromRadians(complexOf(one(args, name), name).arg))
        "polar" -> {
            val z = complexOf(one(args, name), name)
            Mat.of(z.abs, ctx.angleMode.fromRadians(z.arg))
        }

        // --- matrices ---
        "det" -> Num(MatrixMath.determinant(matrix(one(args, name), name)))
        "inv", "inverse" -> MatrixMath.inverse(matrix(one(args, name), name))
        "transpose", "transp" -> MatrixMath.transpose(matrix(one(args, name), name))
        "trace" -> Num(MatrixMath.trace(matrix(one(args, name), name)))
        "rank" -> Num(MatrixMath.rank(matrix(one(args, name), name)).toDouble())
        "identity", "eye" -> {
            val n = real(one(args, name), name)
            if (Math.rint(n) != n || n < 1 || n > 64) throw MathError("identity expects a whole number from 1 to 64")
            Mat.identity(n.toInt())
        }
        "dims", "dim" -> {
            val m = matrix(one(args, name), name)
            Mat.of(m.rows.toDouble(), m.cols.toDouble())
        }
        "dot" -> {
            need(args, 2, name)
            Num(MatrixMath.dot(matrix(args[0], name), matrix(args[1], name)))
        }
        "cross" -> {
            need(args, 2, name)
            MatrixMath.cross(matrix(args[0], name), matrix(args[1], name))
        }
        "normalize", "unit" -> {
            val m = matrix(one(args, name), name)
            val n = MatrixMath.frobeniusNorm(m)
            if (n == 0.0) throw MathError("Cannot normalize a zero vector")
            MatrixMath.scale(m, 1.0 / n)
        }
        "solve" -> {
            need(args, 2, name)
            MatrixMath.solve(matrix(args[0], name), matrix(args[1], name))
        }
        "rand" -> {
            need(args, 0, name)
            Num(Math.random())
        }

        // --- misc ---
        "fib" -> {
            val n = real(one(args, name), name)
            if (Math.rint(n) != n || n < 0) throw MathError("fib expects a whole, non-negative number")
            Num(fibonacci(n.toInt()).toDouble())
        }
        "hyp" -> {
            val list = realsFrom(args, name)
            Num(sqrt(list.sumOf { it * it }))
        }
        else -> throw MathError("Unknown function '$name'")
    }

    // ---- helpers -----------------------------------------------------------

    private fun one(args: List<Value>, name: String): Value {
        need(args, 1, name)
        return args[0]
    }

    private fun unit(args: List<Value>, name: String): Num = complexOf(one(args, name), name)

    private fun need(args: List<Value>, n: Int, name: String) {
        if (args.size != n) {
            throw MathError("$name expects $n argument${if (n == 1) "" else "s"} but got ${args.size}")
        }
    }

    fun complexOf(v: Value, name: String): Num =
        v as? Num ?: throw MathError("$name expects a number, not a matrix")

    fun real(v: Value, name: String): Double =
        (v as? Num)?.takeIf { it.isRealScalar }?.re
            ?: throw MathError("$name expects a real number")

    private fun matrix(v: Value, name: String): Mat =
        v as? Mat ?: throw MathError("$name expects a matrix such as [1, 2; 3, 4]")

    private fun realsFrom(args: List<Value>, name: String): List<Double> {
        if (args.isEmpty()) throw MathError("$name expects at least one argument")
        return args.map { arg ->
            when (arg) {
                is Num -> real(arg, name)
                is Mat -> {
                    if (!arg.isVector()) throw MathError("$name accepts vectors, not 2-D matrices")
                    arg.data.toList()
                }
            }
        }.flatten()
    }

    private fun positiveCheck(v: Num, name: String): Num {
        if (v.im == 0.0 && v.re == 0.0) throw MathError("$name(0) is undefined")
        return v
    }

    private fun realComplex(v: Value, name: String): Num = complexOf(v, name)

    private fun recip(v: Num): Num {
        if (v.re == 0.0 && v.im == 0.0) throw MathError("Division by zero")
        return ComplexMath.div(Num.ONE, v)
    }

    private fun angleIn(v: Num, ctx: MathContext): Num =
        if (v.im == 0.0) Num(ctx.angleMode.toRadians(v.re)) else v

    private fun angleOut(v: Num, ctx: MathContext): Num =
        if (v.im == 0.0) Num(ctx.angleMode.fromRadians(v.re)) else v

    /** Snaps the tiny floating point noise that makes sin(180°) fail to read as 0. */
    private fun trigOut(v: Num, ctx: MathContext): Num {
        val cleaned = ComplexMath.cleanIfNearlyReal(v)
        if (cleaned.im == 0.0 && abs(cleaned.re) < 1e-12) return Num(0.0)
        return cleaned
    }

    private fun truncate(x: Double): Double =
        if (x >= 0) Math.floor(x) else Math.ceil(x)

    private fun roundTo(x: Double, digits: Int): Double {
        if (digits < 0 || digits > 15) throw MathError("round expects 0–15 digits")
        val factor = Math.pow(10.0, digits.toDouble())
        return Math.round(x * factor) / factor
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) throw MathError("median expects at least one argument")
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    private fun variance(values: List<Double>, sample: Boolean): Double {
        if (values.size < 2) throw MathError("variance expects at least two arguments")
        val mean = values.sum() / values.size
        val sumSq = values.sumOf { (it - mean) * (it - mean) }
        return sumSq / (values.size - if (sample) 1 else 0)
    }

    private fun fibonacci(n: Int): Long {
        var a = 0L
        var b = 1L
        repeat(n) {
            val t = a + b
            a = b
            b = t
        }
        return a
    }
}
