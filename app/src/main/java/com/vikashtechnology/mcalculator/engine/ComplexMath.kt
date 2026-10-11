package com.vikashtechnology.mcalculator.engine

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * Complex-number helpers. Real numbers use the same [Num] type with a zero
 * imaginary part, so most call sites only deal with one type.
 */
object ComplexMath {

    fun add(a: Num, b: Num): Num = Num(a.re + b.re, a.im + b.im)

    fun sub(a: Num, b: Num): Num = Num(a.re - b.re, a.im - b.im)

    fun mul(a: Num, b: Num): Num =
        Num(a.re * b.re - a.im * b.im, a.re * b.im + a.im * b.re)

    fun div(a: Num, b: Num): Num {
        if (b.re == 0.0 && b.im == 0.0) throw MathError("Division by zero")
        if (Math.abs(b.re) >= Math.abs(b.im)) {
            val r = b.im / b.re
            val den = b.re + b.im * r
            return Num((a.re + a.im * r) / den, (a.im - a.re * r) / den)
        }
        val r = b.re / b.im
        val den = b.re * r + b.im
        return Num((a.re * r + a.im) / den, (a.im * r - a.re) / den)
        // The scaled form above avoids overflow when one part dominates.
    }

    fun neg(a: Num): Num = Num(-a.re, -a.im)

    fun abs(a: Num): Num = Num(a.abs)

    fun conj(a: Num): Num = Num(a.re, -a.im)

    fun exp(a: Num): Num {
        val r = exp(a.re)
        return Num(r * cos(a.im), r * sin(a.im))
    }

    fun log(a: Num): Num {
        if (a.re == 0.0 && a.im == 0.0) throw MathError("log(0) is undefined")
        return Num(ln(a.abs), a.arg)
    }

    fun log10(a: Num): Num {
        val l = log(a)
        val inv = 1.0 / ln(10.0)
        return Num(l.re * inv, l.im * inv)
    }

    fun sqrt(a: Num): Num {
        if (a.im == 0.0) {
            return if (a.re >= 0.0) Num(kotlin.math.sqrt(a.re))
            else Num(0.0, kotlin.math.sqrt(-a.re))
        }
        val r = kotlin.math.sqrt(a.abs)
        val theta = a.arg / 2.0
        return Num(r * cos(theta), r * sin(theta))
    }

    fun pow(a: Num, b: Num): Num {
        if (b.re == 0.0 && b.im == 0.0) return Num.ONE
        if (a.re == 0.0 && a.im == 0.0) {
            return if (b.re > 0.0) Num.ZERO else throw MathError("0 raised to a non-positive power")
        }
        // Exact real result for a real base raised to an integer power.
        if (a.im == 0.0 && b.im == 0.0 && Math.rint(b.re) == b.re && Math.abs(b.re) <= 1_000_000) {
            return Num(Math.pow(a.re, b.re))
        }
        // Real base with a real exponent keeps the real branch when valid.
        if (a.im == 0.0 && b.im == 0.0 && a.re > 0.0) {
            return Num(Math.pow(a.re, b.re))
        }
        if (a.im == 0.0 && b.im == 0.0 && a.re < 0.0) {
            // Negative base, non-integer real exponent: principal complex root.
            return exp(mul(b, log(Num(a.re, 0.0))))
        }
        return exp(mul(b, log(a)))
    }

    fun sin(a: Num): Num =
        if (a.im == 0.0) Num(sin(a.re))
        else Num(sin(a.re) * kotlin.math.cosh(a.im), cos(a.re) * kotlin.math.sinh(a.im))

    fun cos(a: Num): Num =
        if (a.im == 0.0) Num(cos(a.re))
        else Num(cos(a.re) * kotlin.math.cosh(a.im), -sin(a.re) * kotlin.math.sinh(a.im))

    fun tan(a: Num): Num = div(sin(a), cos(a))

    fun sinh(a: Num): Num =
        if (a.im == 0.0) Num(kotlin.math.sinh(a.re))
        else Num(kotlin.math.sinh(a.re) * cos(a.im), kotlin.math.cosh(a.re) * sin(a.im))

    fun cosh(a: Num): Num =
        if (a.im == 0.0) Num(kotlin.math.cosh(a.re))
        else Num(kotlin.math.cosh(a.re) * cos(a.im), kotlin.math.sinh(a.re) * sin(a.im))

    fun tanh(a: Num): Num = div(sinh(a), cosh(a))

    fun asin(a: Num): Num {
        if (a.im == 0.0 && a.re >= -1.0 && a.re <= 1.0) return Num(Math.asin(a.re))
        // asin(z) = -i * ln(i z + sqrt(1 - z^2))
        val iz = mul(Num.I, a)
        val root = sqrt(sub(Num.ONE, mul(a, a)))
        return mul(Num(0.0, -1.0), log(add(iz, root)))
    }

    fun acos(a: Num): Num {
        if (a.im == 0.0 && a.re >= -1.0 && a.re <= 1.0) return Num(Math.acos(a.re))
        // acos(z) = -i * ln(z + i sqrt(1 - z^2))
        val root = sqrt(sub(Num.ONE, mul(a, a)))
        return mul(Num(0.0, -1.0), log(add(a, mul(Num.I, root))))
    }

    fun atan(a: Num): Num {
        if (a.im == 0.0) return Num(Math.atan(a.re))
        // atan(z) = (i/2) * ln((i + z) / (i - z))
        val i = Num.I
        return mul(Num(0.0, 0.5), log(div(add(i, a), sub(i, a))))
    }

    fun asinh(a: Num): Num = log(add(a, sqrt(add(mul(a, a), Num.ONE))))

    fun acosh(a: Num): Num = log(add(a, sqrt(sub(mul(a, a), Num.ONE))))

    fun atanh(a: Num): Num =
        if (a.im == 0.0 && Math.abs(a.re) < 1.0) Num(0.5 * ln((1 + a.re) / (1 - a.re)))
        else mul(Num.HALF, log(div(add(Num.ONE, a), sub(Num.ONE, a))))

    /** Round very small imaginary parts created by floating point noise to zero. */
    fun cleanIfNearlyReal(a: Num, tolerance: Double = 1e-13): Num {
        if (a.im != 0.0 && Math.abs(a.im) <= tolerance * Math.max(1.0, Math.abs(a.re))) {
            return Num(a.re, 0.0)
        }
        return a
    }
}
