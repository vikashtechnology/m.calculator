"""Reference oracle for the Kotlin engine tests.

Computes expected results with Python's math module and applies the exact
formatting rules documented in Formatter.kt, so the values printed here can be
pasted straight into the JUnit tests (and any mismatch points at a real bug).
"""
import math
from decimal import Decimal, Context, ROUND_HALF_UP

PREC = 12
SCI = 10
LARGE = 1e15
SMALL = 1e-9

def number(x):
    if math.isnan(x): return "Undefined"
    if math.isinf(x): return "\u221e" if x > 0 else "-\u221e"
    if x == 0: return "0"
    ax = abs(x)
    c = Context(prec=SCI, rounding=ROUND_HALF_UP)
    if ax >= LARGE or ax < SMALL:
        d = c.create_decimal(Decimal(x))
        s = f"{float(d):.9E}"
        mant, exp = s.split("E")
        mant = mant.rstrip("0").rstrip(".")
        if mant == "": mant = "0"
        return f"{mant}e{int(exp)}"
    d = Context(prec=PREC, rounding=ROUND_HALF_UP).create_decimal(Decimal(x))
    s = format(d.normalize(), 'f')
    if "." in s: s = s.rstrip("0").rstrip(".")
    return "0" if s == "-0" else s

def cnum(z):
    re, im = z.real, z.imag
    if im == 0: return number(re)
    ia = number(abs(im))
    if re == 0: return ("-" + ia + "i") if im < 0 else (ia + "i")
    return f"{number(re)} - {ia}i" if im < 0 else f"{number(re)} + {ia}i"

cases = {
    "2+3*4": 14, "2+3(4+1)": 17, "(1+2)(3+4)": 21, "-2^2": -4, "2^3^2": 512,
    "10/4": 2.5, "1/3": 1/3, "2/3": 2/3, "0.1+0.2": 0.1+0.2,
    "sqrt(2)": math.sqrt(2), "sqrt(9)-3": 0.0, "cbrt(27)": 3.0,
    "sin(30)": 0.5, "cos(60)+0.5": 1.0, "tan(45)": 1.0, "asin(0.5)": 30.0,
    "atan2(1,1)": 45.0, "sin(pi/2)": 1.0, "ln(e^3)": 3.0, "log(1000)": 3.0,
    "log(8,2)": 3.0, "exp(1)": math.e, "hypot(3,4)": 5.0,
    "5!": 120.0, "0.5!": math.gamma(1.5), "gamma(5)": 24.0, "ncr(5,2)": 10.0,
    "npr(5,2)": 20.0, "gcd(12,18)": 6.0, "lcm(4,6)": 12.0, "fib(10)": 55.0,
    "mod(10,3)": 1.0, "10%3": 1.0, "50%": 0.5, "50%+2": 2.5,
    "round(2.567,2)": 2.57, "floor(-2.5)": -3.0, "ceil(-2.5)": -2.0,
    "mean(1,2,3,4)": 2.5, "median(1,2,3)": 2.0,
    "stdev(2,4,4,4,5,5,7,9)": math.sqrt(32/7),
    "nthroot(27,3)": 3.0, "5^0.5": math.sqrt(5), "acosh(1)": 0.0,
    "pi": math.pi, "tau": 2*math.pi, "phi": (1+math.sqrt(5))/2,
    "2e3": 2000.0, "1.5e-3": 0.0015, "1e-12": 1e-12, "10^16": 1e16,
    "1e16": 1e16, "sum(1,2,3)": 6.0, "product(1,2,3,4)": 24.0,
    "deg(pi)": 180.0, "rad(180)": math.pi, "floor(7.999)": 7.0,
    "round(pi,4)": 3.1416, "sqrt(10)": math.sqrt(10), "ln(10)": math.log(10),
    "7^7": 823543.0, "2^-3": 0.125, "100!/98!": 9900.0,
    "abs(-7)": 7.0, "sign(-3)": -1.0, "min(3,1,2)": 1.0, "max(3,1,2)": 3.0,
    "det([1,2;3,4])": -2.0, "trace([1,2;3,4])": 5.0,
    "dot([1,2,3],[4,5,6])": 32.0, "rank([1,2;2,4])": 1.0,
}
complex_cases = {
    "i^2": complex(-1, 0), "sqrt(-4)": complex(0, 2), "(1+i)^2": complex(0, 2),
    "abs(3-4i)": complex(5, 0), "re(3-4i)": complex(3, 0), "im(3-4i)": complex(-4, 0),
    "conj(2+3i)": complex(2, -3), "ln(-1)": complex(0, math.pi),
    "(1+i)*(1-i)": complex(2, 0),
}
extra = {
    "mean(1,2,3,4)": ("2.5", "mean"),
    "stdev(2,4,4,4,5,5,7,9)": ("2.1380899353", "sample stdev"),
    "median(1,2,3)": ("2", "median"),
    "1/7": ("0.142857142857", "repeating decimal"),
    "22/7": ("3.142857142857", ""),
}
print("// ---- expected formatted results ----")
for expr, val in cases.items():
    print(f'"{expr}" -> "{number(val)}"   // {val!r}')
print()
print("// ---- complex ----")
for expr, z in complex_cases.items():
    print(f'"{expr}" -> "{cnum(z)}"')
print()
print("// ---- matrix strings ----")
print('"inv([2,0;0,2])" -> "[[0.5, 0], [0, 0.5]]"')
print('"[1,2;3,4]*[1,0;0,1]" -> "[[1, 2], [3, 4]]"')
print('"transpose([1,2;3,4])" -> "[[1, 3], [2, 4]]"')
print('"identity(3)" -> "[[1, 0, 0], [0, 1, 0], [0, 0, 1]]"')
print('"dims([1,2,3;4,5,6])" -> "[[2, 3]]"')
print()
print("// ---- fraction hints ----")
for x in (1/3, 0.375, 1/7, 2.5, 0.0):
    print(f"{x!r} -> hint")
print()
print("// ---- unit conversions ----")
print("convert(1, km, mi) =", number(1 * 1000 / 1609.344))
print("convert(100, degC, degF) =", number((100 + 273.15) / (5/9) - 255.3722222222222))
print("convert(1, in, cm) =", number(0.0254 / 0.01))
print("convert(60, mph, kph) =", number(60 * 0.44704 * 3.6))
