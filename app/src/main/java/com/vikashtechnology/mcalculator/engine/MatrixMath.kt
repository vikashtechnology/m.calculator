package com.vikashtechnology.mcalculator.engine

import kotlin.math.abs
import kotlin.math.max

/** Real-matrix algebra: arithmetic, determinant, inverse, and friends. */
object MatrixMath {

    fun fromValue(value: Value, opName: String): Mat =
        value as? Mat ?: throw MathError("$opName expects a matrix, e.g. [1, 2; 3, 4]")

    fun add(a: Mat, b: Mat): Mat {
        requireSameShape(a, b, "add")
        return Mat(a.rows, a.cols, DoubleArray(a.data.size) { a.data[it] + b.data[it] })
    }

    fun sub(a: Mat, b: Mat): Mat {
        requireSameShape(a, b, "subtract")
        return Mat(a.rows, a.cols, DoubleArray(a.data.size) { a.data[it] - b.data[it] })
    }

    fun scale(a: Mat, s: Double): Mat = a.map { it * s }

    fun mul(a: Mat, b: Mat): Mat {
        if (a.cols != b.rows) {
            throw MathError("Cannot multiply a ${a.rows}x${a.cols} matrix by a ${b.rows}x${b.cols} matrix")
        }
        val out = DoubleArray(a.rows * b.cols)
        for (i in 0 until a.rows) {
            for (k in 0 until a.cols) {
                val aik = a[i, k]
                if (aik == 0.0) continue
                for (j in 0 until b.cols) {
                    out[i * b.cols + j] += aik * b[k, j]
                }
            }
        }
        return Mat(a.rows, b.cols, out)
    }

    fun hadamard(a: Mat, b: Mat): Mat {
        requireSameShape(a, b, "multiply element-wise")
        return Mat(a.rows, a.cols, DoubleArray(a.data.size) { a.data[it] * b.data[it] })
    }

    fun transpose(a: Mat): Mat {
        val out = DoubleArray(a.data.size)
        for (i in 0 until a.rows) for (j in 0 until a.cols) out[j * a.rows + i] = a[i, j]
        return Mat(a.cols, a.rows, out)
    }

    fun trace(a: Mat): Double {
        requireSquare(a, "trace")
        var s = 0.0
        for (i in 0 until a.rows) s += a[i, i]
        return s
    }

    fun determinant(a: Mat): Double {
        requireSquare(a, "det")
        val n = a.rows
        if (n == 1) return a[0, 0]
        if (n == 2) return a[0, 0] * a[1, 1] - a[0, 1] * a[1, 0]
        // LU decomposition with partial pivoting on a copy.
        val m = a.data.copyOf()
        var det = 1.0
        for (col in 0 until n) {
            var pivot = col
            var best = abs(m[col * n + col])
            for (r in col + 1 until n) {
                val v = abs(m[r * n + col])
                if (v > best) {
                    best = v
                    pivot = r
                }
            }
            if (best == 0.0) return 0.0
            if (pivot != col) {
                for (c in 0 until n) {
                    val t = m[col * n + c]
                    m[col * n + c] = m[pivot * n + c]
                    m[pivot * n + c] = t
                }
                det = -det
            }
            val diag = m[col * n + col]
            det *= diag
            for (r in col + 1 until n) {
                val factor = m[r * n + col] / diag
                if (factor == 0.0) continue
                for (c in col until n) {
                    m[r * n + c] -= factor * m[col * n + c]
                }
            }
        }
        return det
    }

    fun inverse(a: Mat): Mat {
        requireSquare(a, "inv")
        val n = a.rows
        // Gauss-Jordan elimination with partial pivoting.
        val m = a.data.copyOf()
        val inv = DoubleArray(n * n)
        for (i in 0 until n) inv[i * n + i] = 1.0

        for (col in 0 until n) {
            var pivot = col
            var best = abs(m[col * n + col])
            for (r in col + 1 until n) {
                val v = abs(m[r * n + col])
                if (v > best) {
                    best = v
                    pivot = r
                }
            }
            if (best < 1e-300) throw MathError("Matrix is singular, it has no inverse")
            if (pivot != col) {
                for (c in 0 until n) {
                    var t = m[col * n + c]; m[col * n + c] = m[pivot * n + c]; m[pivot * n + c] = t
                    t = inv[col * n + c]; inv[col * n + c] = inv[pivot * n + c]; inv[pivot * n + c] = t
                }
            }
            val diag = m[col * n + col]
            for (c in 0 until n) {
                m[col * n + c] /= diag
                inv[col * n + c] /= diag
            }
            for (r in 0 until n) {
                if (r == col) continue
                val factor = m[r * n + col]
                if (factor == 0.0) continue
                for (c in 0 until n) {
                    m[r * n + c] -= factor * m[col * n + c]
                    inv[r * n + c] -= factor * inv[col * n + c]
                }
            }
        }
        return Mat(n, n, inv)
    }

    fun rank(a: Mat): Int {
        val rows = a.rows
        val cols = a.cols
        val m = a.data.copyOf()
        var rank = 0
        var row = 0
        for (col in 0 until cols) {
            if (row >= rows) break
            var pivot = row
            var best = abs(m[row * cols + col])
            for (r in row + 1 until rows) {
                val v = abs(m[r * cols + col])
                if (v > best) {
                    best = v
                    pivot = r
                }
            }
            if (best < 1e-12) continue
            if (pivot != row) {
                for (c in 0 until cols) {
                    val t = m[row * cols + c]
                    m[row * cols + c] = m[pivot * cols + c]
                    m[pivot * cols + c] = t
                }
            }
            val diag = m[row * cols + col]
            for (c in col until cols) m[row * cols + c] /= diag
            for (r in 0 until rows) {
                if (r == row) continue
                val factor = m[r * cols + col]
                if (factor == 0.0) continue
                for (c in col until cols) m[r * cols + c] -= factor * m[row * cols + c]
            }
            rank++
            row++
        }
        return rank
    }

    fun dot(a: Mat, b: Mat): Double {
        if (!a.isVector() || !b.isVector()) throw MathError("dot expects two vectors")
        if (a.data.size != b.data.size) throw MathError("dot expects vectors of the same length")
        var s = 0.0
        for (i in a.data.indices) s += a.data[i] * b.data[i]
        return s
    }

    fun cross(a: Mat, b: Mat): Mat {
        if (a.data.size != 3 || b.data.size != 3) {
            throw MathError("cross expects two vectors with 3 elements")
        }
        return Mat.of(
            a.data[1] * b.data[2] - a.data[2] * b.data[1],
            a.data[2] * b.data[0] - a.data[0] * b.data[2],
            a.data[0] * b.data[1] - a.data[1] * b.data[0]
        )
    }

    fun frobeniusNorm(a: Mat): Double {
        var s = 0.0
        for (v in a.data) s += v * v
        return kotlin.math.sqrt(s)
    }

    /** Solves A x = b for square A using the inverse (fine for the sizes a keypad can enter). */
    fun solve(a: Mat, b: Mat): Mat = mul(inverse(a), b)

    /** Smallest absolute value in the matrix, used to detect near-singular input. */
    fun minAbs(a: Mat): Double {
        var m = Double.MAX_VALUE
        for (v in a.data) m = minOf(m, abs(v))
        return m
    }

    fun maxAbs(a: Mat): Double {
        var m = 0.0
        for (v in a.data) m = max(m, abs(v))
        return m
    }

    private fun requireSameShape(a: Mat, b: Mat, verb: String) {
        if (a.rows != b.rows || a.cols != b.cols) {
            throw MathError(
                "Cannot $verb a ${a.rows}x${a.cols} matrix and a ${b.rows}x${b.cols} matrix"
            )
        }
    }

    private fun requireSquare(a: Mat, opName: String) {
        if (!a.isSquare()) throw MathError("$opName requires a square matrix (got ${a.rows}x${a.cols})")
    }
}
