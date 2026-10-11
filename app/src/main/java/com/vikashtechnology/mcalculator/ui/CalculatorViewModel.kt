package com.vikashtechnology.mcalculator.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.vikashtechnology.mcalculator.data.HistoryEntry
import com.vikashtechnology.mcalculator.data.Prefs
import com.vikashtechnology.mcalculator.engine.AngleMode
import com.vikashtechnology.mcalculator.engine.CalculatorEngine
import com.vikashtechnology.mcalculator.engine.Calculus
import com.vikashtechnology.mcalculator.engine.EngineResult
import com.vikashtechnology.mcalculator.engine.Formatter
import com.vikashtechnology.mcalculator.engine.MathContext
import com.vikashtechnology.mcalculator.engine.Num
import com.vikashtechnology.mcalculator.engine.StatsSummary
import com.vikashtechnology.mcalculator.engine.UnitCategory
import com.vikashtechnology.mcalculator.engine.Units
import com.vikashtechnology.mcalculator.engine.Value

/** State of the main calculator pad. */
data class CalcUiState(
    val expression: String = "",
    val cursor: Int = 0,
    val resultText: String = "",
    val fractionHint: String? = null,
    val preview: String? = null,
    val error: String? = null,
    val page: KeypadPage = KeypadPage.BASIC,
    val second: Boolean = false,
    val angleMode: AngleMode = AngleMode.DEG,
    val polar: Boolean = false,
    val memory: Double = 0.0,
    val variables: Map<String, Double> = emptyMap(),
    val history: List<HistoryEntry> = emptyList(),
    val justEvaluated: Boolean = false,
    val ans: Value = Num.ZERO
)

/** One plotted function. */
data class GraphFunction(
    val id: Int,
    val expression: String,
    val colorIndex: Int,
    val visible: Boolean = true
)

data class GraphState(
    val functions: List<GraphFunction> = listOf(
        GraphFunction(1, "sin(x)", 0),
        GraphFunction(2, "x^2/4 - 2", 1)
    ),
    val xMin: Double = -10.0,
    val xMax: Double = 10.0,
    val yMin: Double = -6.0,
    val yMax: Double = 6.0,
    val traceX: Double? = null
)

enum class MatrixOp(val label: String) {
    ADD("A + B"),
    SUBTRACT("A − B"),
    MULTIPLY("A × B"),
    DETERMINANT("det(A)"),
    INVERSE("inv(A)"),
    TRANSPOSE("trn(A)"),
    RANK("rank(A)"),
    TRACE("trace(A)"),
    SOLVE("solve(A, B)")
}

data class MatrixState(
    val rowsA: Int = 2,
    val colsA: Int = 2,
    val rowsB: Int = 2,
    val colsB: Int = 2,
    val dataA: List<String> = listOf("1", "2", "3", "4"),
    val dataB: List<String> = listOf("5", "6", "7", "8"),
    val operation: MatrixOp = MatrixOp.MULTIPLY,
    val editingA: Boolean = true,
    val selectedCell: Int = -1,
    val result: String = "",
    val error: String? = null
)

data class ConverterState(
    val category: UnitCategory = UnitCategory.LENGTH,
    val fromId: String = "m",
    val toId: String = "ft",
    val input: String = "1",
    val freeText: String = ""
)

data class StatsState(
    val data: String = "2, 4, 4, 4, 5, 5, 7, 9",
    val summary: StatsSummary? = null,
    val error: String? = null
)

/** State of the solver/calculus screen. */
data class SolverState(
    val mode: SolverMode = SolverMode.SOLVE,
    val expression: String = "x^2 - 4",
    val variable: String = "x",
    val point: String = "1",
    val lower: String = "0",
    val upper: String = "3",
    val guess: String = "1",
    val result: String = "",
    val detail: String = "",
    val error: String? = null
)

class CalculatorViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)

    var ui by mutableStateOf(
        CalcUiState(
            angleMode = prefs.angleMode,
            polar = prefs.polarMode,
            memory = prefs.memory,
            variables = prefs.loadVariables(),
            history = prefs.loadHistory(),
            expression = prefs.lastExpression,
            resultText = prefs.lastResult
        )
    )
        private set

    var graph by mutableStateOf(GraphState())
        private set

    var matrix by mutableStateOf(MatrixState())
        private set

    var converter by mutableStateOf(ConverterState())
        private set

    var stats by mutableStateOf(StatsState())
        private set

    var solver by mutableStateOf(SolverState())
        private set

    // ---- pad ---------------------------------------------------------------

    fun onKey(key: CalcKey) {
        val action = if (ui.second && key.altAction != null) key.altAction else key.action
        val wasSecond = ui.second
        if (action !is KeyAction.ToggleSecond) {
            if (wasSecond) ui = ui.copy(second = false)
        }
        when (action) {
            is KeyAction.Insert -> insert(action.text)
            KeyAction.Backspace -> backspace()
            KeyAction.Clear -> clearAll()
            KeyAction.Evaluate -> evaluate()
            KeyAction.ToggleSign -> toggleSign()
            KeyAction.CycleAngle -> cycleAngleMode()
            KeyAction.ToggleSecond -> ui = ui.copy(second = !ui.second)
            KeyAction.TogglePolar -> setPolar(!ui.polar)
            is KeyAction.Memory -> memoryAction(action.op)
            is KeyAction.RecallVariable -> insert(action.name.lowercase())
            is KeyAction.AssignVariable -> {
                assignVariable(action.name)
            }
            KeyAction.OpenHistory -> Unit // handled by the UI
        }
    }

    fun onLongPress(key: CalcKey) {
        val action = key.longPressAction ?: return
        when (action) {
            is KeyAction.Memory -> memoryAction(action.op)
            is KeyAction.AssignVariable -> assignVariable(action.name)
            KeyAction.Clear -> clearAll()
            else -> Unit
        }
    }

    fun insert(text: String) {
        if (text.isEmpty()) return
        var expression = ui.expression
        var cursor = ui.cursor
        var justEvaluated = ui.justEvaluated

        val startsWithOperator = text.length == 1 && isOperatorChar(text[0])
        if (justEvaluated) {
            if (startsWithOperator && text[0] != '!' && text[0] != '%') {
                // Continue the calculation from the previous answer.
                expression = "ans"
                cursor = 3
            } else {
                expression = ""
                cursor = 0
            }
            justEvaluated = false
        }

        if (!isInsertAllowed(expression, cursor, text)) return

        // Replace a trailing operator instead of stacking a second one.
        if (text.length == 1 && isOperatorChar(text[0]) && text[0] != '-' && cursor == expression.length) {
            val last = expression.lastOrNull()
            if (last != null && isOperatorChar(last) && last != '(') {
                expression = expression.dropLast(1)
                cursor -= 1
            }
        }

        // Insert a visible × where two tokens would merge (")3", "π2", "e(").
        val needsTimes = needsImplicitMultiplication(expression, cursor, text)
        val toInsert = if (needsTimes) "×$text" else text
        val safeCursor = cursor.coerceIn(0, expression.length)

        // Leading operators that cannot start an expression are dropped.
        expression = expression.substring(0, safeCursor) + toInsert + expression.substring(safeCursor)
        cursor = safeCursor + toInsert.length

        ui = ui.copy(
            expression = expression,
            cursor = cursor,
            justEvaluated = false,
            error = null,
            resultText = "",
            fractionHint = null
        )
        updatePreview()
    }

    fun onExpressionChanged(text: String, cursor: Int) {
        ui = ui.copy(
            expression = text,
            cursor = cursor.coerceIn(0, text.length),
            resultText = "",
            fractionHint = null,
            error = null,
            justEvaluated = false
        )
        updatePreview()
    }

    fun backspace() {
        var text = ui.expression
        var cursor = ui.cursor
        if (cursor <= 0 || text.isEmpty()) return
        val upTo = text.substring(0, cursor)
        val tokenMatch = Regex("([A-Za-z]+)\\($").find(upTo)
        if (tokenMatch != null) {
            text = text.removeRange(tokenMatch.range.first, cursor)
            cursor = tokenMatch.range.first
        } else {
            text = text.removeRange(cursor - 1, cursor)
            cursor -= 1
        }
        ui = ui.copy(
            expression = text,
            cursor = cursor,
            resultText = "",
            fractionHint = null,
            error = null,
            justEvaluated = false
        )
        updatePreview()
    }

    fun clearAll() {
        ui = ui.copy(
            expression = "",
            cursor = 0,
            resultText = "",
            fractionHint = null,
            preview = null,
            error = null,
            justEvaluated = false
        )
    }

    fun setCursor(cursor: Int) {
        ui = ui.copy(cursor = cursor.coerceIn(0, ui.expression.length))
    }

    fun setPage(page: KeypadPage) {
        ui = ui.copy(page = page, second = false)
    }

    fun cycleAngleMode() {
        val next = when (ui.angleMode) {
            AngleMode.DEG -> AngleMode.RAD
            AngleMode.RAD -> AngleMode.GRAD
            AngleMode.GRAD -> AngleMode.DEG
        }
        setAngleMode(next)
    }

    fun setAngleMode(mode: AngleMode) {
        ui = ui.copy(angleMode = mode)
        prefs.angleMode = mode
        updatePreview()
    }

    fun setPolar(polar: Boolean) {
        ui = ui.copy(polar = polar)
        prefs.polarMode = polar
        if (ui.resultText.isNotEmpty() || ui.expression.isNotBlank()) evaluate()
    }

    fun toggleSign() {
        val text = ui.expression
        val cursor = ui.cursor
        var start = cursor
        while (start > 0 && (text[start - 1].isDigit() || text[start - 1] == '.')) start--
        if (start == cursor) {
            insert("-")
            return
        }
        val before = start - 1
        val isUnaryMinus = before >= 0 && text[before] == '-' &&
            (before == 0 || isOperatorChar(text[before - 1]) || text[before - 1] == '(')
        val newText: String
        val newCursor: Int
        if (isUnaryMinus) {
            newText = text.removeRange(before, start)
            newCursor = cursor - 1
        } else {
            newText = text.substring(0, start) + "-" + text.substring(start)
            newCursor = cursor + 1
        }
        ui = ui.copy(expression = newText, cursor = newCursor, justEvaluated = false, error = null)
        updatePreview()
    }

    fun evaluate() {
        val source = autoClose(ui.expression).trim()
        if (source.isEmpty()) {
            clearAll()
            return
        }
        val ctx = buildContext()
        when (val result = CalculatorEngine.evaluate(source, ctx)) {
            is EngineResult.Ok -> {
                val formatted = result.text
                val hint = fractionHintFor(result.value)
                val entry = HistoryEntry(ui.expression, formatted, System.currentTimeMillis())
                val history = (listOf(entry) + ui.history).take(200)
                ui = ui.copy(
                    expression = autoClose(ui.expression),
                    cursor = autoClose(ui.expression).length,
                    resultText = formatted,
                    fractionHint = hint,
                    preview = null,
                    error = null,
                    history = history,
                    justEvaluated = true,
                    ans = result.value
                )
                prefs.saveHistory(history)
                prefs.lastExpression = ui.expression
                prefs.lastResult = formatted
            }
            is EngineResult.Err -> {
                ui = ui.copy(
                    resultText = "",
                    fractionHint = null,
                    preview = null,
                    error = result.message,
                    justEvaluated = false
                )
            }
        }
    }

    fun useHistory(entry: HistoryEntry) {
        ui = ui.copy(
            expression = entry.expression,
            cursor = entry.expression.length,
            resultText = "",
            fractionHint = null,
            error = null,
            justEvaluated = false
        )
        updatePreview()
    }

    fun insertResultOf(entry: HistoryEntry) {
        insert(entry.result.replace(" ", ""))
    }

    fun clearHistory() {
        ui = ui.copy(history = emptyList())
        prefs.clearHistory()
    }

    fun memoryAction(op: MemoryOp) {
        val current = realValueOf(ui.ans)
        when (op) {
            MemoryOp.STORE -> setMemory(current)
            MemoryOp.ADD -> setMemory(ui.memory + current)
            MemoryOp.SUBTRACT -> setMemory(ui.memory - current)
            MemoryOp.CLEAR -> setMemory(0.0)
            MemoryOp.RECALL -> insert("m")
        }
    }

    private fun setMemory(value: Double) {
        ui = ui.copy(memory = value)
        prefs.memory = value
    }

    fun assignVariable(name: String) {
        val value = realValueOf(ui.ans)
        val variables = LinkedHashMap(ui.variables)
        variables[name.lowercase()] = value
        ui = ui.copy(variables = variables)
        prefs.saveVariables(variables)
    }

    fun clearVariable(name: String) {
        val variables = LinkedHashMap(ui.variables)
        variables.remove(name.lowercase())
        ui = ui.copy(variables = variables)
        prefs.saveVariables(variables)
    }

    private fun updatePreview() {
        val text = autoClose(ui.expression).trim()
        if (text.isEmpty() || ui.justEvaluated) {
            ui = ui.copy(preview = null)
            return
        }
        val ctx = buildContext()
        val preview = when (val result = CalculatorEngine.evaluate(text, ctx)) {
            is EngineResult.Ok -> result.text.takeIf { it != ui.expression.trim() }
            is EngineResult.Err -> null
        }
        ui = ui.copy(preview = preview)
    }

    private fun buildContext(): MathContext {
        val ctx = MathContext(
            angleMode = ui.angleMode,
            variables = variablesAsValues(),
            ans = ui.ans,
            polarMode = ui.polar
        )
        if (ui.memory != 0.0) ctx.setVariable("m", ui.memory)
        return ctx
    }

    private fun variablesAsValues(): MutableMap<String, Value> {
        val map = LinkedHashMap<String, Value>()
        ui.variables.forEach { (key, value) -> map[key] = Num(value) }
        return map
    }

    private fun fractionHintFor(value: Value): String? {
        val real = (value as? Num)?.takeIf { it.isRealScalar } ?: return null
        return Formatter.fractionHint(real.re)
    }

    private fun realValueOf(value: Value): Double = when (value) {
        is Num -> if (value.isRealScalar) value.re else value.abs
        else -> 0.0
    }

    private fun autoClose(text: String): String {
        var balance = 0
        text.forEach { c ->
            if (c == '(') balance++
            if (c == ')') balance--
        }
        return if (balance > 0) text + ")".repeat(balance) else text
    }

    private fun isOperatorChar(c: Char): Boolean = c in "+-*/^%×÷!"

    private fun isInsertAllowed(expression: String, cursor: Int, text: String): Boolean {
        if (expression.isNotEmpty() || cursor > 0) {
            if (text == ")") {
                val before = expression.substring(0, cursor)
                val balance = before.count { it == '(' } - before.count { it == ')' }
                return balance > 0
            }
            return true
        }
        // Empty expression: only characters that can start one are accepted.
        val c = text[0]
        return when {
            text == "∞" -> true
            c.isDigit() || c == '.' || c == '(' || c == '[' -> true
            c.isLetter() -> true
            c == 'π' || c == 'φ' || c == 'τ' -> true
            c == '-' || c == '+' -> true
            else -> false
        }
    }

    private fun needsImplicitMultiplication(expression: String, cursor: Int, text: String): Boolean {
        if (cursor != expression.length || expression.isEmpty()) return false
        val prev = expression.last()
        val first = text.first()
        val prevIsValueEnd = prev == ')' || prev.isLetter() || prev.isDigit()
        if (!prevIsValueEnd) return false
        val nextStartsValue = first.isDigit() || first == '(' || first.isLetter() || first == 'π' || first == '.'
        if (!nextStartsValue) return false
        if (prev.isDigit() && (first.isDigit() || first == '.')) return false
        if (prev == ')' && first == ')') return false
        // Numbers after a number are a single number; letters after '(' start a function.
        return true
    }

    // ---- graph -------------------------------------------------------------

    fun updateGraphFunction(id: Int, expression: String) {
        graph = graph.copy(
            functions = graph.functions.map { if (it.id == id) it.copy(expression = expression) else it }
        )
    }

    fun toggleGraphFunction(id: Int) {
        graph = graph.copy(
            functions = graph.functions.map { if (it.id == id) it.copy(visible = !it.visible) else it }
        )
    }

    fun addGraphFunction() {
        val id = (graph.functions.maxOfOrNull { it.id } ?: 0) + 1
        graph = graph.copy(
            functions = graph.functions + GraphFunction(id, "", graph.functions.size % 5)
        )
    }

    fun removeGraphFunction(id: Int) {
        if (graph.functions.size <= 1) return
        graph = graph.copy(functions = graph.functions.filterNot { it.id == id })
    }

    fun setGraphWindow(xMin: Double, xMax: Double, yMin: Double, yMax: Double) {
        graph = graph.copy(xMin = xMin, xMax = xMax, yMin = yMin, yMax = yMax)
    }

    fun panGraph(dxPixels: Float, dyPixels: Float, widthPx: Float, heightPx: Float) {
        val g = graph
        val dx = (g.xMax - g.xMin) * dxPixels / widthPx
        val dy = (g.yMax - g.yMin) * dyPixels / heightPx
        graph = g.copy(
            xMin = g.xMin - dx, xMax = g.xMax - dx,
            yMin = g.yMin + dy, yMax = g.yMax + dy
        )
    }

    fun zoomGraph(factor: Float, focusX: Double, focusY: Double) {
        val g = graph
        val newXMin = focusX + (g.xMin - focusX) / factor
        val newXMax = focusX + (g.xMax - focusX) / factor
        val newYMin = focusY + (g.yMin - focusY) / factor
        val newYMax = focusY + (g.yMax - focusY) / factor
        graph = g.copy(xMin = newXMin, xMax = newXMax, yMin = newYMin, yMax = newYMax)
    }

    fun resetGraphWindow() {
        graph = graph.copy(xMin = -10.0, xMax = 10.0, yMin = -6.0, yMax = 6.0)
    }

    fun setTraceX(x: Double?) {
        graph = graph.copy(traceX = x)
    }

    // ---- solver ------------------------------------------------------------

    fun updateSolver(transform: (SolverState) -> SolverState) {
        solver = transform(solver)
        solver = solver.copy(result = "", detail = "", error = null)
    }

    fun runSolver(mode: SolverMode = solver.mode) {
        val s = solver
        val ctx = buildContext()
        try {
            val expr = CalculatorEngine.parse(s.expression)
            when (mode) {
                SolverMode.SOLVE -> {
                    val guess = s.guess.toDoubleOrNull() ?: 1.0
                    val roots = Calculus.solve(expr, s.variable, -100.0, 100.0, guess, ctx)
                    solver = if (roots.isEmpty()) {
                        s.copy(error = "No real solution found", result = "", detail = "")
                    } else {
                        s.copy(
                            result = roots.joinToString(", ") { Formatter.number(it) },
                            detail = "Found ${roots.size} solution${if (roots.size == 1) "" else "s"}",
                            error = null
                        )
                    }
                }
                SolverMode.DERIVATIVE -> {
                    val at = s.point.toDoubleOrNull() ?: return
                    val value = Calculus.derivative(expr, s.variable, at, ctx)
                    val second = Calculus.secondDerivative(expr, s.variable, at, ctx)
                    solver = s.copy(
                        result = Formatter.number(value),
                        detail = "at ${s.variable} = ${s.point}   •   second derivative ${Formatter.number(second)}",
                        error = null
                    )
                }
                SolverMode.INTEGRAL -> {
                    val a = s.lower.toDoubleOrNull() ?: return
                    val b = s.upper.toDoubleOrNull() ?: return
                    val value = Calculus.integrate(expr, s.variable, a, b, ctx)
                    solver = s.copy(
                        result = Formatter.number(value),
                        detail = "∫ from ${s.lower} to ${s.upper}",
                        error = null
                    )
                }
                SolverMode.SUM -> {
                    val a = s.lower.toDoubleOrNull() ?: return
                    val b = s.upper.toDoubleOrNull() ?: return
                    val value = Calculus.series(expr, s.variable, a, b, ctx)
                    solver = s.copy(
                        result = Formatter.number(value),
                        detail = "Σ from ${s.lower} to ${s.upper}",
                        error = null
                    )
                }
                SolverMode.PRODUCT -> {
                    val a = s.lower.toDoubleOrNull() ?: return
                    val b = s.upper.toDoubleOrNull() ?: return
                    val value = Calculus.series(expr, s.variable, a, b, ctx, product = true)
                    solver = s.copy(
                        result = Formatter.number(value),
                        detail = "Π from ${s.lower} to ${s.upper}",
                        error = null
                    )
                }
            }
        } catch (e: Exception) {
            solver = s.copy(error = e.message ?: "Could not evaluate", result = "", detail = "")
        }
    }

    // ---- matrix ------------------------------------------------------------

    fun updateMatrix(transform: (MatrixState) -> MatrixState) {
        matrix = transform(matrix)
    }

    fun setMatrixSize(rowsA: Int, colsA: Int, rowsB: Int, colsB: Int) {
        matrix = matrix.copy(
            rowsA = rowsA, colsA = colsA, rowsB = rowsB, colsB = colsB,
            dataA = resize(matrix.dataA, matrix.rowsA, matrix.colsA, rowsA, colsA),
            dataB = resize(matrix.dataB, matrix.rowsB, matrix.colsB, rowsB, colsB),
            selectedCell = -1,
            result = "",
            error = null
        )
    }

    private fun resize(data: List<String>, oldRows: Int, oldCols: Int, rows: Int, cols: Int): List<String> {
        val out = MutableList(rows * cols) { "0" }
        for (r in 0 until minOf(oldRows, rows)) {
            for (c in 0 until minOf(oldCols, cols)) {
                out[r * cols + c] = data.getOrElse(r * oldCols + c) { "0" }
            }
        }
        return out
    }

    fun setMatrixCell(editingA: Boolean, index: Int, value: String) {
        matrix = if (editingA) {
            matrix.copy(
                dataA = matrix.dataA.toMutableList().also { it[index] = value },
                selectedCell = index,
                result = "",
                error = null
            )
        } else {
            matrix.copy(
                dataB = matrix.dataB.toMutableList().also { it[index] = value },
                selectedCell = index,
                result = "",
                error = null
            )
        }
    }

    fun computeMatrix() {
        val m = matrix
        try {
            val a = buildMatrix(m.dataA, m.rowsA, m.colsA, "A")
            val b = buildMatrix(m.dataB, m.rowsB, m.colsB, "B")
            val ctx = buildContext()
            val expression = when (m.operation) {
                MatrixOp.ADD -> "a+b"
                MatrixOp.SUBTRACT -> "a-b"
                MatrixOp.MULTIPLY -> "a*b"
                MatrixOp.DETERMINANT -> "det(a)"
                MatrixOp.INVERSE -> "inv(a)"
                MatrixOp.TRANSPOSE -> "transpose(a)"
                MatrixOp.RANK -> "rank(a)"
                MatrixOp.TRACE -> "trace(a)"
                MatrixOp.SOLVE -> "solve(a,b)"
            }
            ctx.setValue("a", a)
            ctx.setValue("b", b)
            val result = CalculatorEngine.evaluate(expression, ctx)
            matrix = when (result) {
                is EngineResult.Ok -> m.copy(result = result.text, error = null)
                is EngineResult.Err -> m.copy(result = "", error = result.message)
            }
        } catch (e: Exception) {
            matrix = m.copy(result = "", error = e.message ?: "Could not compute")
        }
    }

    private fun buildMatrix(cells: List<String>, rows: Int, cols: Int, name: String): Value {
        val values = DoubleArray(rows * cols)
        for (i in 0 until rows * cols) {
            val text = cells.getOrElse(i) { "0" }.trim()
            val parsed = text.toDoubleOrNull()
                ?: throw IllegalArgumentException("Matrix $name: '${text.ifEmpty { "empty" }}' is not a number")
            values[i] = parsed
        }
        return com.vikashtechnology.mcalculator.engine.Mat(rows, cols, values)
    }

    // ---- converter ---------------------------------------------------------

    fun updateConverter(transform: (ConverterState) -> ConverterState) {
        converter = transform(converter)
    }

    fun setConverterCategory(category: UnitCategory) {
        val units = Units.byCategory(category)
        converter = converter.copy(
            category = category,
            fromId = units.firstOrNull()?.id ?: converter.fromId,
            toId = units.getOrNull(1)?.id ?: units.firstOrNull()?.id ?: converter.toId
        )
    }

    fun swapConverterUnits() {
        converter = converter.copy(fromId = converter.toId, toId = converter.fromId)
    }

    fun converterResult(): String {
        val from = Units.find(converter.fromId) ?: return ""
        val to = Units.find(converter.toId) ?: return ""
        val value = converter.input.trim().toDoubleOrNull() ?: return ""
        return Formatter.number(Units.convert(value, from, to))
    }

    fun solveFreeTextConversion() {
        val request = Units.parseRequest(converter.freeText.trim())
        converter = if (request == null) {
            converter.copy(input = "", toId = converter.toId)
        } else {
            converter.copy(
                category = request.from.category,
                fromId = request.from.id,
                toId = request.to.id,
                input = Formatter.number(request.value)
            )
        }
    }

    // ---- statistics --------------------------------------------------------

    fun updateStats(data: String) {
        stats = stats.copy(data = data)
    }

    fun computeStats() {
        val numbers = stats.data
            .split(Regex("[,\\s]+"))
            .mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() }?.toDoubleOrNull() }
        stats = if (numbers.isEmpty()) {
            stats.copy(summary = null, error = "Enter numbers separated by commas or spaces")
        } else {
            runCatching { Calculus.describe(numbers.toDoubleArray()) }
                .fold(
                    onSuccess = { stats.copy(summary = it, error = null) },
                    onFailure = { stats.copy(summary = null, error = it.message ?: "Could not analyse") }
                )
        }
    }

    /** Converts a finished computation into a value usable by other tools. */
    private fun MathContext.unusedHelper() = Unit
}

enum class SolverMode(val label: String, val symbol: String) {
    SOLVE("Solve", "x="),
    DERIVATIVE("Derivative", "d/dx"),
    INTEGRAL("Integral", "∫"),
    SUM("Sum", "Σ"),
    PRODUCT("Product", "Π")
}
