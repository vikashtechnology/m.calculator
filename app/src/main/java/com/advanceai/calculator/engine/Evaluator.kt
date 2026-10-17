package com.advanceai.calculator.engine

/** Everything the evaluator needs to know about the outside world. */
class MathContext(
    var angleMode: AngleMode = AngleMode.DEG,
    /**
     * User variables (`A`–`F`, `x`, `n`, …). Keys are stored lower-case and the
     * values are full engine values, so a variable can hold a matrix too.
     */
    val variables: MutableMap<String, Value> = LinkedHashMap(),
    var ans: Value = Num.ZERO,
    /** When true the display renders complex results in polar form. */
    var polarMode: Boolean = false,
    /** How results are printed: digits shown, and optional Indian grouping. */
    var display: DisplayOptions = DisplayOptions.DEFAULT
) {
    fun valueOf(name: String): Value? = variables[name.lowercase()]

    fun setVariable(name: String, value: Double) {
        variables[name.lowercase()] = Num(value)
    }

    fun setValue(name: String, value: Value) {
        variables[name.lowercase()] = value
    }

    fun copy(): MathContext = MathContext(angleMode, LinkedHashMap(variables), ans, polarMode, display)
}

/** Result of evaluating an expression. */
sealed interface EngineResult {
    data class Ok(val value: Value, val text: String) : EngineResult
    data class Err(val message: String, val position: Int = -1) : EngineResult
}

/** Evaluates a parsed expression tree against a [MathContext]. */
object Evaluator {

    private val constants: Map<String, Double> = mapOf(
        "pi" to Math.PI,
        "e" to Math.E,
        "tau" to 2 * Math.PI,
        "phi" to (1 + Math.sqrt(5.0)) / 2,
        "inf" to Double.POSITIVE_INFINITY,
        "infinity" to Double.POSITIVE_INFINITY,
        "nan" to Double.NaN,
        "c" to 299_792_458.0,
        "g" to 9.80665
    )

    fun eval(expr: Expr, ctx: MathContext): Value = when (expr) {
        is NumLit -> Num(expr.value)
        is VarRef -> lookup(expr, ctx)
        is UnaryExpr -> when (expr.op) {
            TokType.MINUS -> negate(eval(expr.operand, ctx))
            else -> eval(expr.operand, ctx)
        }
        is BinaryExpr -> binary(
            expr.op,
            eval(expr.left, ctx),
            eval(expr.right, ctx),
            expr.pos
        )
        is FactorialExpr -> Num(Factorials.factorial(realOrNull(eval(expr.operand, ctx), "!")))
        is PercentExpr -> percent(eval(expr.operand, ctx))
        is CallExpr -> FunctionLibrary.call(expr.name, expr.args.map { eval(it, ctx) }, ctx, expr.pos)
        is MatrixLitExpr -> matrixLiteral(expr, ctx)
    }

    private fun lookup(ref: VarRef, ctx: MathContext): Value {
        val name = ref.name.lowercase()
        constants[name]?.let { return Num(it) }
        if (name == "ans") return ctx.ans
        if (name == "i" || name == "j") return Num.I
        ctx.valueOf(name)?.let { return it }
        if (name.length == 1) return Num.ZERO // A–F style variable slots start at zero
        throw MathError("Unknown variable '${ref.name}'", ref.pos)
    }

    private fun matrixLiteral(expr: MatrixLitExpr, ctx: MathContext): Value {
        val rows = expr.rows.map { row ->
            row.map { cell ->
                val v = eval(cell, ctx)
                (v as? Num)?.takeIf { it.isRealScalar }?.re
                    ?: throw MathError("Matrix entries must be real numbers", expr.pos)
            }
        }
        return Mat.ofRows(rows)
    }

    private fun percent(v: Value): Value = when (v) {
        is Num -> ComplexMath.div(v, Num(100.0))
        is Mat -> MatrixMath.scale(v, 0.01)
    }

    private fun negate(v: Value): Value = when (v) {
        is Num -> ComplexMath.neg(v)
        is Mat -> MatrixMath.scale(v, -1.0)
    }

    fun realOrNull(v: Value, opName: String): Double = when {
        v is Num && v.isRealScalar -> v.re
        v is Num -> throw MathError("$opName needs a real number, got a complex one")
        else -> throw MathError("$opName needs a number, got a matrix")
    }

    private fun binary(op: String, a: Value, b: Value, pos: Int): Value = when {
        a is Num && b is Num -> numericBinary(op, a, b)
        a is Mat && b is Mat -> matrixBinary(op, a, b)
        a is Mat && b is Num -> scalarOnMatrix(op, a, b, matrixOnLeft = true)
        a is Num && b is Mat -> scalarOnMatrix(op, b, a, matrixOnLeft = false)
        else -> throw MathError("Unsupported operands", pos)
    }

    private fun numericBinary(op: String, a: Num, b: Num): Value = when (op) {
        "+" -> ComplexMath.add(a, b)
        "-" -> ComplexMath.sub(a, b)
        "*" -> ComplexMath.mul(a, b)
        "/" -> ComplexMath.div(a, b)
        "^" -> ComplexMath.pow(a, b)
        "%" -> {
            val x = realOrNull(a, "mod")
            val y = realOrNull(b, "mod")
            if (y == 0.0) throw MathError("Division by zero in mod")
            Num(x - y * Math.floor(x / y))
        }
        else -> throw MathError("Unknown operator '$op'")
    }

    private fun matrixBinary(op: String, a: Mat, b: Mat): Value = when (op) {
        "+" -> MatrixMath.add(a, b)
        "-" -> MatrixMath.sub(a, b)
        "*" -> MatrixMath.mul(a, b)
        "^" -> {
            if (b.rows != 1 || b.cols != 1) throw MathError("Matrix power needs a single integer exponent")
            val e = b[0, 0]
            if (Math.rint(e) != e || e < 0 || e > 64) throw MathError("Matrix power expects an integer from 0 to 64")
            var result = Mat.identity(a.rows)
            if (!a.isSquare()) throw MathError("Matrix power requires a square matrix")
            repeat(e.toInt()) { result = MatrixMath.mul(result, a) }
            result
        }
        else -> throw MathError("Operator '$op' is not defined for two matrices")
    }

    private fun scalarOnMatrix(op: String, mat: Mat, scalar: Num, matrixOnLeft: Boolean): Value {
        val s = realOrNull(scalar, "matrix arithmetic")
        return when (op) {
            "*" -> MatrixMath.scale(mat, s)
            "/" -> {
                if (!matrixOnLeft) throw MathError("Cannot divide a number by a matrix")
                if (s == 0.0) throw MathError("Division by zero")
                MatrixMath.scale(mat, 1.0 / s)
            }
            "+" -> mat.map { it + s }
            "-" -> if (matrixOnLeft) mat.map { it - s } else mat.map { s - it }
            else -> throw MathError("Operator '$op' is not defined between a matrix and a number")
        }
    }
}

/** Factorial, gamma and combinatorial helpers. */
object Factorials {

    fun factorial(x: Double): Double {
        if (x.isNaN()) return Double.NaN
        if (x < 0 && Math.rint(x) == x) throw MathError("Factorial of a negative integer is undefined")
        if (x > 170.5) return Double.POSITIVE_INFINITY
        if (Math.rint(x) == x) {
            var result = 1.0
            var i = 2.0
            while (i <= x) {
                result *= i
                i += 1.0
            }
            return result
        }
        return gamma(x + 1.0)
    }

    /** Lanczos approximation of the gamma function. */
    fun gamma(x: Double): Double {
        if (x.isNaN()) return Double.NaN
        if (x < 0.5) {
            // Reflection formula keeps the poles usable.
            val s = Math.sin(Math.PI * x)
            if (s == 0.0) throw MathError("Gamma is undefined at this value")
            return Math.PI / (s * gamma(1.0 - x))
        }
        val g = 7.0
        val coefficients = doubleArrayOf(
            0.99999999999980993, 676.5203681218851, -1259.1392167224028,
            771.32342877765313, -176.61502916214059, 12.507343278686905,
            -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7
        )
        val z = x - 1.0
        var a = coefficients[0]
        val t = z + g + 0.5
        for (i in 1 until coefficients.size) {
            a += coefficients[i] / (z + i)
        }
        return Math.sqrt(2 * Math.PI) * Math.pow(t, z + 0.5) * Math.exp(-t) * a
    }

    fun logGamma(x: Double): Double {
        if (x <= 0 && Math.rint(x) == x) throw MathError("log gamma is undefined here")
        return Math.log(Math.abs(gamma(x)))
    }

    fun nCr(n: Double, r: Double): Double {
        if (Math.rint(n) == n && Math.rint(r) == r && n >= 0 && r >= 0 && n <= 2000) {
            val k = Math.min(r, n - r).toInt()
            var result = 1.0
            for (i in 1..k) {
                result = result * (n - k + i) / i
            }
            return Math.round(result).toDouble()
        }
        return Math.exp(logGamma(n + 1) - logGamma(r + 1) - logGamma(n - r + 1))
    }

    fun nPr(n: Double, r: Double): Double {
        if (Math.rint(n) == n && Math.rint(r) == r && n >= 0 && r >= 0 && n <= 2000) {
            var result = 1.0
            for (i in 0 until r.toInt()) result *= (n - i)
            return Math.round(result).toDouble()
        }
        return Math.exp(logGamma(n + 1) - logGamma(n - r + 1))
    }

    fun gcd(a: Double, b: Double): Double {
        var x = Math.abs(a)
        var y = Math.abs(b)
        if (Math.rint(x) != x || Math.rint(y) != y) throw MathError("gcd expects whole numbers")
        while (y > 0) {
            val t = x % y
            x = y
            y = t
        }
        return x
    }

    fun lcm(a: Double, b: Double): Double {
        val g = gcd(a, b)
        if (g == 0.0) return 0.0
        return Math.abs(a * b) / g
    }
}
