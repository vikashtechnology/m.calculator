package com.vikashtechnology.mcalculator.engine

/**
 * Error thrown for user-visible math problems (domain errors, shape errors, ...).
 * The message is written to be shown directly in the calculator display.
 */
class MathError(message: String, val position: Int = -1) : Exception(message)

/** Angle interpretation used by the trigonometric functions. */
enum class AngleMode(val label: String) {
    DEG("DEG"),
    RAD("RAD"),
    GRAD("GRAD");

    fun toRadians(x: Double): Double = when (this) {
        DEG -> Math.toRadians(x)
        RAD -> x
        GRAD -> x * Math.PI / 200.0
    }

    fun fromRadians(x: Double): Double = when (this) {
        DEG -> Math.toDegrees(x)
        RAD -> x
        GRAD -> x * 200.0 / Math.PI
    }
}

/** Everything the calculator can compute with. */
sealed interface Value {
    /** True when this value is a plain real number (no imaginary part, not a matrix). */
    val isRealScalar: Boolean
}

/**
 * A complex number. Real numbers are simply [Num] with a zero imaginary part,
 * which keeps arithmetic uniform across the whole engine.
 */
data class Num(val re: Double, val im: Double = 0.0) : Value {
    override val isRealScalar: Boolean get() = im == 0.0 || im == -0.0

    val abs: Double
        get() = if (isRealScalar) Math.abs(re) else Math.hypot(re, im)

    val arg: Double
        get() = Math.atan2(im, re)

    companion object {
        val ZERO = Num(0.0)
        val ONE = Num(1.0)
        val TWO = Num(2.0)
        val HALF = Num(0.5)
        val PI = Num(Math.PI)
        val E = Num(Math.E)
        val I = Num(0.0, 1.0)
        val NAN = Num(Double.NaN)

        fun of(x: Double): Num = Num(x)
        fun of(x: Int): Num = Num(x.toDouble())
        fun complex(re: Double, im: Double): Num = Num(re, im)
    }
}

/** A dense real matrix. Row-major, immutable. */
class Mat(val rows: Int, val cols: Int, val data: DoubleArray) : Value {
    override val isRealScalar: Boolean get() = false

    init {
        require(data.size == rows * cols) {
            "Matrix data size ${data.size} does not match $rows x $cols"
        }
    }

    operator fun get(r: Int, c: Int): Double = data[r * cols + c]

    fun row(r: Int): DoubleArray = DoubleArray(cols) { c -> this[r, c] }

    fun isSquare(): Boolean = rows == cols

    fun isVector(): Boolean = rows == 1 || cols == 1

    fun copy(): Mat = Mat(rows, cols, data.copyOf())

    fun map(f: (Double) -> Double): Mat = Mat(rows, cols, DoubleArray(data.size) { f(data[it]) })

    fun displayString(): String =
        (0 until rows).joinToString("; ") { r ->
            "[" + (0 until cols).joinToString(", ") { c -> Formatter.number(this[r, c]) } + "]"
        }.let { "[$it]" }

    companion object {
        fun of(vararg values: Double): Mat =
            Mat(1, values.size, values.copyOf())

        fun column(vararg values: Double): Mat =
            Mat(values.size, 1, values.copyOf())

        fun identity(n: Int): Mat {
            val d = DoubleArray(n * n)
            for (i in 0 until n) d[i * n + i] = 1.0
            return Mat(n, n, d)
        }

        fun zeros(rows: Int, cols: Int): Mat = Mat(rows, cols, DoubleArray(rows * cols))

        fun ofRows(rows: List<List<Double>>): Mat {
            if (rows.isEmpty()) throw MathError("A matrix needs at least one row")
            val cols = rows[0].size
            if (cols == 0) throw MathError("A matrix needs at least one column")
            rows.forEach {
                if (it.size != cols) throw MathError("All matrix rows must have the same length")
            }
            val d = DoubleArray(rows.size * cols)
            var k = 0
            rows.forEach { row -> row.forEach { v -> d[k++] = v } }
            return Mat(rows.size, cols, d)
        }
    }
}
