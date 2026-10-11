package com.vikashtechnology.mcalculator.engine

/**
 * The built-in function library. Names are matched lower-case and every entry
 * validates its own argument count and types so errors read well on screen.
 */
object FunctionLibrary {

    // ---- argument helpers --------------------------------------------------

    /** Exactly one argument, as a number (complex allowed). */
    private fun num1(args: List<Value>, name: String): Num {
        expectCount(args, 1, name)
        return args[0] as? Num ?: throw MathError("$name expects a number, not a matrix")
    }

    /** Exactly one argument, as a real number. */
    private fun real1(args: List<Value>, name: String): Double = realOf(num1(args, name), name)

    /** Exactly one argument, as any value (used by matrix/aggregate functions). */
    private fun any1(args: List<Value>, name: String): Value {
        expectCount(args, 1, name)
        return args[0]
    }

    private fun expectCount(args: List<Value>, n: Int, name: String) {
        if (args.size != n) {
            throw MathError("$name expects $n argument${if (n == 1) "" else "s"} but got ${args.size}")
        }
    }

    private fun realOf(value: Num, name: String): Double =
        if (value.isRealScalar) value.re else throw MathError("$name expects a real number")

    private fun realAt(args: List<Value>, index: Int, name: String): Double {
        val value = args[index]
        val num = value as? Num ?: throw MathError("$name expects numbers")
        return realOf(num, name)
    }

    private fun matrix1(args: List<Value>, name: String): Mat =
        any1(args, name) as? Mat
            ?: throw MathError("$name expects a matrix such as [1, 2; 3, 4]")

    /** Flattens numeric arguments and vectors into one list of reals. */
    private fun reals(args: List<Value>, name: String): List<Double> {
        if (args.isEmpty()) throw MathError("$name expects at least one argument")
        val out = ArrayList<Double>()
        args.forEach { arg ->
            when (arg) {
                is Num -> out.add(realOf(arg, name))
                is Mat -> {
                    if (!arg.isVector()) throw MathError("$name accepts vectors, not 2-D matrices")
                    arg.data.forEach { out.add(it) }
                }
            }
        }
        return out
    }

    // ---- dispatch ----------------------------------------------------------

    fun call(name: String, args: List<Value>, ctx: MathContext, pos: Int): Value {
        return try {
            dispatch(name, args, ctx)
        } catch (e: MathError) {
            if (e.position >= 0) throw e
            throw MathError(e.message ?: "Math error", pos)
        }
    }

    private fun dispatch(name: String, args: List<Value>, ctx: MathContext): Value = when (name) {

        // --- trigonometry (angle mode aware) --------------------------------
        "sin" -> Num(outAngle(ComplexMath.sin(intoAngle(num1(args, name), ctx), ctx), ctx))
        "cos" -> Num(outAngle(ComplexMath.cos(intoAngle(num1(args, name), ctx), ctx), ctx))
        "tan" -> Num(outAngle(ComplexMath.tan(intoAngle(num1(args, name), ctx), ctx), ctx))
        "csc" -> Num(outAngle(reciprocal(ComplexMath.sin(intoAngle(num1(args, name), ctx), ctx)), ctx))
        "sec" -> Num(outAngle(reciprocal(ComplexMath.cos(intoAngle(num1(args, name), ctx), ctx)), ctx))
        "cot" -> Num(outAngle(reciprocal(ComplexMath.tan(intoAngle(num1(args, name), ctx), ctx)), ctx))

        "asin", "arcsin" -> Num(fromAngle(ComplexMath.asin(num1(args, name)), ctx))
        "acos", "arccos" -> Num(fromAngle(ComplexMath.acos(num1(args, name)), ctx))
        "atan", "arctan" -> Num(fromAngle(ComplexMath.atan(num1(args, name)), ctx))
        "asec" -> Num(fromAngle(ComplexMath.acos(reciprocal(num1(args, name))), ctx))
        "acsc" -> Num(fromAngle(ComplexMath.asin(reciprocal(num1(args, name))), ctx))
        "acot" -> Num(fromAngle(ComplexMath.atan(reciprocal(num1(args, name))), ctx))

        "sinh" -> Num(outAngle(ComplexMath.sinh(num1(args, name)), ctx))
        "cosh" -> Num(outAngle(ComplexMath.cosh(num1(args, name)), ctx))
        "tanh" -> Num(outAngle(ComplexMath.tanh(num1(args, name)), ctx))
        "csch" -> Num(outAngle(reciprocal(ComplexMath.sinh(num1(args, name))), ctx))
        "sech" -> Num(outAngle(reciprocal(ComplexMath.cosh(num1(args, name))), ctx))
        "coth" -> Num(outAngle(reciprocal(ComplexMath.tanh(num1(args, name))), ctx))

        "asinh" -> Num(ComplexMath.asinh(num1(args, name)))
        "acosh" -> Num(ComplexMath.acosh(num1(args, name)))
        "atanh" -> Num(ComplexMath.atanh(num1(args, name)))

        "atan2" -> {
            expectCount(args, 2, name)
            val y = realAt(args, 0, name)
            val x = realAt(args, 1, name)
            Num(ctx.angleMode.fromRadians(Math.atan2(y, x)))
        }

        // --- exponentials and logarithms ------------------------------------
        "exp" -> Num(ComplexMath.exp(num1(args, name)))
        "exp2" -> Num(ComplexMath.pow(Num.TWO, num1(args, name)))
        "exp10" -> Num(ComplexMath.pow(Num(10.0), num1(args, name)))
        "ln" -> Num(ComplexMath.log(nonZero(num1(args, name), name)))
        "log" -> when (args.size) {
            1 -> Num(ComplexMath.log10(nonZero(num1(args, name), name)))
            2 -> {
                val value = nonZero(args[0] as? Num ?: throw MathError("log expects numbers"), name)
                val base = nonZero(args[1] as? Num ?: throw MathError("log expects numbers"), name)
                Num(ComplexMath.div(ComplexMath.log(value), ComplexMath.log(base)))
            }
            else -> throw MathError("log expects 1 or 2 arguments")
        }
        "log2" -> Num(
            ComplexMath.div(
                ComplexMath.log(nonZero(num1(args, name), name)),
                Num(Math.log(2.0))
            )
        )
        "log10" -> Num(ComplexMath.log10(nonZero(num1(args, name), name)))

        // --- powers and roots -----------------------------------------------
        "sqrt" -> Num(ComplexMath.sqrt(num1(args, name)))
        "cbrt" -> {
            val v = num1(args, name)
            Num(if (v.isRealScalar) Num(Math.cbrt(v.re)) else ComplexMath.pow(v, Num(1.0 / 3.0)))
        }
        "nthroot", "root" -> {
            expectCount(args, 2, name)
            val x = args[0] as? Num ?: throw MathError("$name expects numbers")
            val n = realAt(args, 1, name)
            if (n == 0.0) throw MathError("Root of degree zero is undefined")
            Num(
                if (x.im == 0.0 && x.re < 0 && Math.rint(n) == n && n % 2.0 != 0.0) {
                    Num(-Math.pow(-x.re, 1.0 / n))
                } else {
                    ComplexMath.pow(x, Num(1.0 / n))
                }
            )
        }
        "pow" -> {
            expectCount(args, 2, name)
            Num(ComplexMath.pow(num1(listOf(args[0]), name), num1(listOf(args[1]), name)))
        }
        "hypot", "hyp" -> {
            val values = reals(args, name)
            Num(Math.sqrt(values.sumOf { it * it }))
        }

        // --- rounding and sign ----------------------------------------------
        "abs" -> when (val v = any1(args, name)) {
            is Num -> Num(v.abs)
            is Mat -> Num(MatrixMath.frobeniusNorm(v))
        }
        "sign", "sgn" -> Num(Math.signum(real1(args, name)))
        "floor" -> Num(Math.floor(real1(args, name)))
        "ceil" -> Num(Math.ceil(real1(args, name)))
        "trunc" -> Num(truncate(real1(args, name)))
        "frac" -> Num(real1(args, name).let { it - truncate(it) })
        "round" -> when (args.size) {
            1 -> Num(roundTo(real1(args, name), 0))
            2 -> Num(roundTo(realAt(args, 0, name), realAt(args, 1, name).toInt()))
            else -> throw MathError("round expects 1 or 2 arguments")
        }

        // --- combinatorics and number theory --------------------------------
        "fact", "factorial" -> Num(Factorials.factorial(real1(args, name)))
        "gamma" -> Num(Factorials.gamma(real1(args, name)))
        "lgamma", "loggamma" -> Num(Factorials.logGamma(real1(args, name)))
        "ncr", "comb" -> {
            expectCount(args, 2, name)
            Num(Factorials.nCr(realAt(args, 0, name), realAt(args, 1, name)))
        }
        "npr", "perm" -> {
            expectCount(args, 2, name)
            Num(Factorials.nPr(realAt(args, 0, name), realAt(args, 1, name)))
        }
        "gcd" -> Num(reals(args, name).reduce { a, b -> Factorials.gcd(a, b) })
        "lcm" -> Num(reals(args, name).reduce { a, b -> Factorials.lcm(a, b) })
        "mod" -> {
            expectCount(args, 2, name)
            val a = realAt(args, 0, name)
            val b = realAt(args, 1, name)
            if (b == 0.0) throw MathError("mod by zero")
            Num(a - b * Math.floor(a / b))
        }

        // --- aggregates ------------------------------------------------------
        "min" -> Num(reals(args, name).min())
        "max" -> Num(reals(args, name).max())
        "sum" -> Num(reals(args, name).sum())
        "product", "prod" -> Num(reals(args, name).fold(1.0) { acc, v -> acc * v })
        "mean", "avg" -> reals(args, name).let { Num(it.sum() / it.size) }
        "median" -> Num(median(reals(args, name)))
        "stdev", "stddev" -> Num(Math.sqrt(variance(reals(args, name))))
        "variance", "var" -> Num(variance(reals(args, name)))
        "count" -> Num(reals(args, name).size.toDouble())

        // --- angle conversion -------------------------------------------------
        "deg" -> Num(Math.toDegrees(real1(args, name)))
        "rad" -> Num(Math.toRadians(real1(args, name)))

        // --- complex helpers --------------------------------------------------
        "re" -> Num(num1(args, name).re)
        "im" -> Num(num1(args, name).im)
        "conj", "conjugate" -> Num(ComplexMath.conj(num1(args, name)))
        "arg" -> Num(ctx.angleMode.fromRadians(num1(args, name).arg))
        "polar" -> {
            val z = num1(args, name)
            Mat.of(z.abs, ctx.angleMode.fromRadians(z.arg))
        }

        // --- matrices ---------------------------------------------------------
        "det" -> Num(MatrixMath.determinant(matrix1(args, name)))
        "inv", "inverse" -> MatrixMath.inverse(matrix1(args, name))
        "transpose", "transp" -> MatrixMath.transpose(matrix1(args, name))
        "trace" -> Num(MatrixMath.trace(matrix1(args, name)))
        "rank" -> Num(MatrixMath.rank(matrix1(args, name)).toDouble())
        "identity", "eye" -> {
            val n = real1(args, name)
            if (Math.rint(n) != n || n < 1 || n > 64) {
                throw MathError("identity expects a whole number from 1 to 64")
            }
            Mat.identity(n.toInt())
        }
        "dims", "dim" -> {
            val m = matrix1(args, name)
            Mat.of(m.rows.toDouble(), m.cols.toDouble())
        }
        "dot" -> {
            expectCount(args, 2, name)
            val a = args[0] as? Mat ?: throw MathError("dot expects two vectors")
            val b = args[1] as? Mat ?: throw MathError("dot expects two vectors")
            Num(MatrixMath.dot(a, b))
        }
        "cross" -> {
            expectCount(args, 2, name)
            val a = args[0] as? Mat ?: throw MathError("cross expects two vectors")
            val b = args[1] as? Mat ?: throw MathError("cross expects two vectors")
            MatrixMath.cross(a, b)
        }
        "normalize", "unit" -> {
            val m = matrix1(args, name)
            val norm = MatrixMath.frobeniusNorm(m)
            if (norm == 0.0) throw MathError("Cannot normalize a zero vector")
            MatrixMath.scale(m, 1.0 / norm)
        }
        "solve" -> {
            expectCount(args, 2, name)
            val a = args[0] as? Mat ?: throw MathError("solve expects two matrices")
            val b = args[1] as? Mat ?: throw MathError("solve expects two matrices")
            MatrixMath.solve(a, b)
        }

        // --- misc -------------------------------------------------------------
        "fib" -> {
            val n = real1(args, name)
            if (Math.rint(n) != n || n < 0) throw MathError("fib expects a whole, non-negative number")
            Num(fibonacci(n.toInt()).toDouble())
        }
        "rand" -> {
            expectCount(args, 0, name)
            Num(Math.random())
        }

        else -> throw MathError("Unknown function '$name'")
    }

    // ---- shared helpers ------------------------------------------------------

    /** Applies the angle mode to a real argument, leaving complex values alone. */
    private fun intoAngle(v: Num, ctx: MathContext): Num =
        if (v.isRealScalar) Num(ctx.angleMode.toRadians(v.re)) else v

    /** Converts a real result back to the display angle unit. */
    private fun fromAngle(v: Num, ctx: MathContext): Num =
        if (v.isRealScalar) Num(ctx.angleMode.fromRadians(v.re)) else v

    /** Snaps the tiny floating point noise that would make sin(180°) read non-zero. */
    private fun outAngle(v: Num, ctx: MathContext): Num {
        val cleaned = ComplexMath.cleanIfNearlyReal(v)
        if (cleaned.isRealScalar && Math.abs(cleaned.re) < 1e-12) return Num(0.0)
        return cleaned
    }

    private fun reciprocal(v: Num): Num {
        if (v.re == 0.0 && v.im == 0.0) throw MathError("Division by zero")
        return ComplexMath.div(Num.ONE, v)
    }

    private fun nonZero(v: Num, name: String): Num {
        if (v.re == 0.0 && v.im == 0.0) throw MathError("$name(0) is undefined")
        return v
    }

    private fun truncate(x: Double): Double = if (x >= 0) Math.floor(x) else Math.ceil(x)

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

    private fun variance(values: List<Double>): Double {
        if (values.size < 2) throw MathError("variance expects at least two arguments")
        val mean = values.sum() / values.size
        return values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
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
