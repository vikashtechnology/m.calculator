package com.vikashtechnology.mcalculator.ui

/** Pages of the on-screen keypad. */
enum class KeypadPage(val label: String) {
    BASIC("Basic"),
    FUNC("Func"),
    CONST("Const"),
    MATRIX("Matrix")
}

enum class KeyStyle { DIGIT, OPERATOR, FUNCTION, ACCENT, DANGER, CONSTANT }

enum class MemoryOp { STORE, ADD, SUBTRACT, RECALL, CLEAR }

/** What happens when a key is pressed. */
sealed interface KeyAction {
    data class Insert(val text: String) : KeyAction
    data object Backspace : KeyAction
    data object Clear : KeyAction
    data object Evaluate : KeyAction
    data object ToggleSign : KeyAction
    data object CycleAngle : KeyAction
    data object ToggleSecond : KeyAction
    data class Memory(val op: MemoryOp) : KeyAction
    data class RecallVariable(val name: String) : KeyAction
    data class AssignVariable(val name: String) : KeyAction
    data object OpenHistory : KeyAction
    data object TogglePolar : KeyAction
}

data class CalcKey(
    val id: String,
    val label: String,
    val action: KeyAction,
    val style: KeyStyle = KeyStyle.DIGIT,
    /** Shown instead of [label] when the INV/2nd toggle is active. */
    val altLabel: String? = null,
    /** Used instead of [action] when the INV/2nd toggle is active. */
    val altAction: KeyAction? = null,
    /** Long press behaviour, e.g. storing a variable. */
    val longPressAction: KeyAction? = null,
    val longPressLabel: String? = null,
    val description: String? = null
)

object Keypads {

    private fun digit(d: String) = CalcKey("d$d", d, KeyAction.Insert(d), KeyStyle.DIGIT)

    private fun fn(
        id: String,
        label: String,
        insert: String,
        description: String,
        altLabel: String? = null,
        altInsert: String? = null,
        longPressLabel: String? = null,
        longPressAction: KeyAction? = null
    ) = CalcKey(
        id = id,
        label = label,
        action = KeyAction.Insert(insert),
        style = KeyStyle.FUNCTION,
        altLabel = altLabel,
        altAction = altInsert?.let { KeyAction.Insert(it) },
        longPressAction = longPressAction,
        longPressLabel = longPressLabel,
        description = description
    )

    private fun variable(name: String) = CalcKey(
        id = "var$name",
        label = name,
        action = KeyAction.RecallVariable(name),
        style = KeyStyle.CONSTANT,
        longPressAction = KeyAction.AssignVariable(name),
        longPressLabel = "store result in $name",
        description = "Variable $name — tap to use it, long press to store the current result"
    )

    val basic: List<List<CalcKey>> = listOf(
        listOf(
            CalcKey("ac", "AC", KeyAction.Clear, KeyStyle.DANGER, description = "Clear everything"),
            CalcKey(
                "back", "⌫", KeyAction.Backspace, KeyStyle.FUNCTION,
                longPressAction = KeyAction.Clear, longPressLabel = "clear all",
                description = "Delete the last entry"
            ),
            CalcKey("open", "(", KeyAction.Insert("("), KeyStyle.OPERATOR),
            CalcKey("close", ")", KeyAction.Insert(")"), KeyStyle.OPERATOR)
        ),
        listOf(digit("7"), digit("8"), digit("9"), operator("÷", "÷")),
        listOf(digit("4"), digit("5"), digit("6"), operator("×", "×")),
        listOf(digit("1"), digit("2"), digit("3"), operator("−", "-")),
        listOf(
            CalcKey("sign", "±", KeyAction.ToggleSign, KeyStyle.FUNCTION),
            digit("0"),
            CalcKey("dot", ".", KeyAction.Insert("."), KeyStyle.DIGIT),
            operator("+", "+")
        ),
        listOf(CalcKey("eq", "=", KeyAction.Evaluate, KeyStyle.ACCENT, description = "Calculate"))
    )

    val func: List<List<CalcKey>> = listOf(
        listOf(
            fn("sin", "sin", "sin(", "Sine", "sin⁻¹", "asin("),
            fn("cos", "cos", "cos(", "Cosine", "cos⁻¹", "acos("),
            fn("tan", "tan", "tan(", "Tangent", "tan⁻¹", "atan("),
            fn("ln", "ln", "ln(", "Natural logarithm (base e)", "eˣ", "exp("),
            fn("log", "log", "log(", "Logarithm base 10", "log₂", "log2(")
        ),
        listOf(
            fn("sinh", "sinh", "sinh(", "Hyperbolic sine", "sinh⁻¹", "asinh("),
            fn("cosh", "cosh", "cosh(", "Hyperbolic cosine", "cosh⁻¹", "acosh("),
            fn("tanh", "tanh", "tanh(", "Hyperbolic tangent", "tanh⁻¹", "atanh("),
            fn("exp", "eˣ", "exp(", "e raised to the power x"),
            fn("pow", "xʸ", "^", "Power", "ʸ√x", "nthroot(")
        ),
        listOf(
            fn("sq", "x²", "^2", "Square"),
            fn("cube", "x³", "^3", "Cube"),
            fn("sqrt", "√", "sqrt(", "Square root"),
            fn("cbrt", "∛", "cbrt(", "Cube root"),
            fn("abs", "|x|", "abs(", "Absolute value / modulus")
        ),
        listOf(
            fn("inv", "1/x", "1/", "Reciprocal"),
            fn("fact", "n!", "!", "Factorial"),
            fn("ncr", "nCr", "ncr(", "Combinations: n choose r"),
            fn("npr", "nPr", "npr(", "Permutations"),
            fn("pct", "%", "%", "Percent")
        ),
        listOf(
            variable("x"),
            CalcKey("pi", "π", KeyAction.Insert("π"), KeyStyle.CONSTANT, description = "Pi = 3.14159…"),
            CalcKey("e", "e", KeyAction.Insert("e"), KeyStyle.CONSTANT, description = "Euler's number = 2.71828…"),
            fn("modf", "mod", "mod(", "Remainder after division"),
            CalcKey("ans", "Ans", KeyAction.Insert("ans"), KeyStyle.CONSTANT, description = "Previous answer")
        ),
        listOf(
            CalcKey("thousands", ",", KeyAction.Insert(","), KeyStyle.FUNCTION,
                description = "Separates function arguments and matrix rows"),
            fn("atan2", "atan2", "atan2(", "Angle from x/y coordinates, two arguments"),
            fn("hypot", "hypot", "hypot(", "Length of the hypotenuse"),
            fn("deg", "→deg", "deg(", "Convert radians to degrees"),
            fn("rad", "→rad", "rad(", "Convert degrees to radians")
        )
    )

    val constPage: List<List<CalcKey>> = listOf(
        listOf(
            CalcKey("pi2", "π", KeyAction.Insert("π"), KeyStyle.CONSTANT, description = "3.14159265359"),
            CalcKey("e2", "e", KeyAction.Insert("e"), KeyStyle.CONSTANT, description = "2.71828182846"),
            CalcKey("phi", "φ", KeyAction.Insert("φ"), KeyStyle.CONSTANT, description = "Golden ratio 1.61803398875"),
            CalcKey("tau", "τ", KeyAction.Insert("τ"), KeyStyle.CONSTANT, description = "2π = 6.28318530718")
        ),
        listOf(
            CalcKey("c", "c", KeyAction.Insert("c"), KeyStyle.CONSTANT, description = "Speed of light, 299792458 m/s"),
            CalcKey("g", "g", KeyAction.Insert("g"), KeyStyle.CONSTANT, description = "Standard gravity, 9.80665 m/s²"),
            CalcKey("inf", "∞", KeyAction.Insert("∞"), KeyStyle.CONSTANT, description = "Infinity"),
            CalcKey("ans2", "Ans", KeyAction.Insert("ans"), KeyStyle.CONSTANT, description = "Previous answer")
        ),
        listOf(variable("A"), variable("B"), variable("C"), variable("D")),
        listOf(variable("E"), variable("F"), variable("M"), CalcKey("i", "i", KeyAction.Insert("i"), KeyStyle.CONSTANT, description = "Imaginary unit √−1")),
        listOf(
            CalcKey("mplus", "M+", KeyAction.Memory(MemoryOp.ADD), KeyStyle.OPERATOR, description = "Add the result to memory"),
            CalcKey("mminus", "M−", KeyAction.Memory(MemoryOp.SUBTRACT), KeyStyle.OPERATOR, description = "Subtract the result from memory"),
            CalcKey("mr", "MR", KeyAction.Memory(MemoryOp.RECALL), KeyStyle.OPERATOR, description = "Recall memory"),
            CalcKey("ms", "MS", KeyAction.Memory(MemoryOp.STORE), KeyStyle.OPERATOR, longPressAction = KeyAction.Memory(MemoryOp.CLEAR), longPressLabel = "clear memory", description = "Store the result in memory")
        )
    )

    val matrixPage: List<List<CalcKey>> = listOf(
        listOf(
            CalcKey("lb", "[", KeyAction.Insert("["), KeyStyle.OPERATOR, description = "Start a matrix: [1, 2; 3, 4]"),
            CalcKey("rb", "]", KeyAction.Insert("]"), KeyStyle.OPERATOR),
            CalcKey("semi", ";", KeyAction.Insert(";"), KeyStyle.OPERATOR, description = "Next matrix row"),
            CalcKey("comma", ",", KeyAction.Insert(","), KeyStyle.OPERATOR, description = "Next matrix column / next argument")
        ),
        listOf(
            fn("det", "det", "det(", "Determinant of a square matrix"),
            fn("inv2", "inv", "inv(", "Matrix inverse"),
            fn("trn", "trn", "transpose(", "Transpose"),
            fn("rank2", "rank", "rank(", "Rank of a matrix")
        ),
        listOf(
            fn("dot", "dot", "dot(", "Dot product of two vectors"),
            fn("cross", "cross", "cross(", "Cross product of two 3-vectors"),
            fn("solve", "solve", "solve(", "Solve A·x = b for square A"),
            fn("dims", "dims", "dims(", "Size of a matrix as [rows, cols]")
        ),
        listOf(
            fn("id", "id", "identity(", "Identity matrix, e.g. identity(3)"),
            fn("meanf", "mean", "mean(", "Average of the arguments"),
            fn("stdevf", "stdev", "stdev(", "Sample standard deviation"),
            fn("sumf", "sum", "sum(", "Total of the arguments")
        )
    )

    private fun operator(label: String, insert: String) =
        CalcKey("op$label", label, KeyAction.Insert(insert), KeyStyle.OPERATOR)

    fun keysFor(page: KeypadPage): List<List<CalcKey>> = when (page) {
        KeypadPage.BASIC -> basic
        KeypadPage.FUNC -> func
        KeypadPage.CONST -> constPage
        KeypadPage.MATRIX -> matrixPage
    }
}
