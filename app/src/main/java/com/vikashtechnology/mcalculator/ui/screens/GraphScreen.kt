package com.vikashtechnology.mcalculator.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikashtechnology.mcalculator.engine.CalculatorEngine
import com.vikashtechnology.mcalculator.engine.Calculus
import com.vikashtechnology.mcalculator.engine.Evaluator
import com.vikashtechnology.mcalculator.engine.Expr
import com.vikashtechnology.mcalculator.engine.Formatter
import com.vikashtechnology.mcalculator.engine.MathContext
import com.vikashtechnology.mcalculator.engine.MathError
import com.vikashtechnology.mcalculator.ui.CalculatorViewModel
import com.vikashtechnology.mcalculator.ui.GraphState
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private val curveColors = listOf(
    Color(0xFF7C6CFF),
    Color(0xFF35C2F0),
    Color(0xFF3FD08B),
    Color(0xFFF0A93B),
    Color(0xFFE05FA8)
)

@Composable
fun GraphScreen(
    viewModel: CalculatorViewModel,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val graph = viewModel.graph
    val textMeasurer = rememberTextMeasurer()
    val parsed = remember(graph.functions) {
        graph.functions.map { fn -> runCatching { CalculatorEngine.parse(fn.expression) }.getOrNull() }
    }
    val ctx = MathContext(viewModel.ui.angleMode, LinkedHashMap(), viewModel.ui.ans, false)

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.addGraphFunction() }) {
                Icon(Icons.Filled.Add, contentDescription = "Add a function")
            }
            IconButton(onClick = {
                viewModel.zoomGraph(1.5f, (graph.xMin + graph.xMax) / 2, (graph.yMin + graph.yMax) / 2)
            }) {
                Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in")
            }
            IconButton(onClick = {
                viewModel.zoomGraph(1f / 1.5f, (graph.xMin + graph.xMax) / 2, (graph.yMin + graph.yMax) / 2)
            }) {
                Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out")
            }
            IconButton(onClick = {
                viewModel.resetGraphWindow()
                viewModel.setTraceX(null)
            }) {
                Icon(Icons.Filled.CenterFocusStrong, contentDescription = "Reset the view")
            }
            Text(
                text = "x ∈ [${Formatter.number(graph.xMin)}, ${Formatter.number(graph.xMax)}]",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(graph) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            if (pan != Offset.Zero) {
                                viewModel.panGraph(pan.x, pan.y, size.width.toFloat(), size.height.toFloat())
                            }
                            if (zoom != 1f && size.width > 0 && size.height > 0) {
                                val focusX = graph.xMin + centroid.x / size.width * (graph.xMax - graph.xMin)
                                val focusY = graph.yMax - centroid.y / size.height * (graph.yMax - graph.yMin)
                                viewModel.zoomGraph(zoom, focusX, focusY)
                            }
                        }
                    }
                    .pointerInput(graph) {
                        detectTapGestures { offset ->
                            val x = graph.xMin + offset.x / size.width * (graph.xMax - graph.xMin)
                            viewModel.setTraceX(x)
                        }
                    }
            ) {
                drawPlot(graph, parsed, ctx, textMeasurer)
            }

            val traceX = graph.traceX
            if (traceX != null) {
                TraceChip(graph, parsed, ctx, traceX)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 190.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            graph.functions.forEachIndexed { index, fn ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                color = if (fn.visible) curveColors[fn.colorIndex % curveColors.size]
                                else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                    )
                    OutlinedTextField(
                        value = fn.expression,
                        onValueChange = { viewModel.updateGraphFunction(fn.id, it) },
                        label = { Text("y${index + 1} = f(x)") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                    IconButton(onClick = { viewModel.toggleGraphFunction(fn.id) }) {
                        Icon(
                            imageVector = if (fn.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "Show or hide",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.removeGraphFunction(fn.id) }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Text(
                text = "Drag to pan, pinch to zoom, tap the plot to trace a value.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
    }
}

@Composable
private fun BoxScope.TraceChip(
    graph: GraphState,
    parsed: List<Expr?>,
    ctx: MathContext,
    x: Double
) {
    val values = graph.functions.mapIndexedNotNull { index, fn ->
        if (!fn.visible) return@mapIndexedNotNull null
        val expr = parsed.getOrNull(index) ?: return@mapIndexedNotNull null
        val previous = ctx.valueOf("x")
        ctx.setVariable("x", x)
        val traced = try {
            val result = Evaluator.eval(expr, ctx)
            Formatter.value(result)
        } catch (e: MathError) {
            null
        } finally {
            if (previous == null) ctx.variables.remove("x") else ctx.setValue("x", previous)
        } ?: return@mapIndexedNotNull null
        index to traced
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = "x = ${Formatter.number(x)}",
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace)
            )
            values.forEach { (index, text) ->
                Text(
                    text = "y${index + 1} = $text",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = curveColors[index % curveColors.size]
                )
            }
        }
    }
}

private fun DrawScope.drawPlot(
    graph: GraphState,
    parsed: List<Expr?>,
    ctx: MathContext,
    textMeasurer: TextMeasurer
) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return
    val xSpan = graph.xMax - graph.xMin
    val ySpan = graph.yMax - graph.yMin
    if (xSpan <= 0 || ySpan <= 0) return

    fun xToPx(x: Double): Float = ((x - graph.xMin) / xSpan * width).toFloat()
    fun yToPx(y: Double): Float = ((graph.yMax - y) / ySpan * height).toFloat()

    val gridColor = Color(0x22FFFFFF)
    val axisColor = Color(0x88FFFFFF)
    val labelStyle = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0x99AEB4CC))

    // --- grid ---------------------------------------------------------------
    val xStep = niceStep(xSpan / 8)
    val yStep = niceStep(ySpan / 5)

    var gx = ceil(graph.xMin / xStep) * xStep
    while (gx <= graph.xMax) {
        val px = xToPx(gx)
        drawLine(gridColor, Offset(px, 0f), Offset(px, height), strokeWidth = 1f)
        gx += xStep
    }
    var gy = ceil(graph.yMin / yStep) * yStep
    while (gy <= graph.yMax) {
        val py = yToPx(gy)
        drawLine(gridColor, Offset(0f, py), Offset(width, py), strokeWidth = 1f)
        gy += yStep
    }

    // --- axes ---------------------------------------------------------------
    val axisY = yToPx(0.0).coerceIn(0f, height)
    val axisX = xToPx(0.0).coerceIn(0f, width)
    drawLine(axisColor, Offset(0f, axisY), Offset(width, axisY), strokeWidth = 2f)
    drawLine(axisColor, Offset(axisX, 0f), Offset(axisX, height), strokeWidth = 2f)

    // --- tick labels --------------------------------------------------------
    gx = ceil(graph.xMin / xStep) * xStep
    while (gx <= graph.xMax) {
        if (abs(gx) > xStep / 1000) {
            val layout = textMeasurer.measure(Formatter.number(gx), labelStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    (xToPx(gx) + 3f).coerceIn(2f, width - layout.size.width - 2f),
                    (axisY + 4f).coerceIn(0f, height - layout.size.height)
                )
            )
        }
        gx += xStep
    }
    gy = ceil(graph.yMin / yStep) * yStep
    while (gy <= graph.yMax) {
        if (abs(gy) > yStep / 1000) {
            val layout = textMeasurer.measure(Formatter.number(gy), labelStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    (axisX + 4f).coerceIn(2f, width - layout.size.width - 2f),
                    (yToPx(gy) + 3f).coerceIn(0f, height - layout.size.height)
                )
            )
        }
        gy += yStep
    }

    // --- curves -------------------------------------------------------------
    val samples = 700
    graph.functions.forEachIndexed { index, fn ->
        if (!fn.visible) return@forEachIndexed
        val expr = parsed.getOrNull(index) ?: return@forEachIndexed
        val ys = runCatching {
            Calculus.samples(expr, "x", graph.xMin, graph.xMax, samples, ctx)
        }.getOrNull() ?: return@forEachIndexed

        val path = Path()
        var started = false
        var previousY = Double.NaN
        val breakThreshold = ySpan * 3
        for (i in 0 until samples) {
            val y = ys[i]
            val x = graph.xMin + xSpan * i / (samples - 1)
            val offScreen = !y.isFinite()
            val jump = !previousY.isNaN() && abs(y - previousY) > breakThreshold
            if (offScreen || jump) {
                started = false
                previousY = y
                continue
            }
            val px = xToPx(x)
            val py = yToPx(y).coerceIn(-height, 2 * height)
            if (!started) {
                path.moveTo(px, py)
                started = true
            } else {
                path.lineTo(px, py)
            }
            previousY = y
        }
        drawPath(
            path = path,
            color = curveColors[fn.colorIndex % curveColors.size],
            style = Stroke(width = 3f)
        )
    }

    // --- trace marker -------------------------------------------------------
    val traceX = graph.traceX
    if (traceX != null && traceX in graph.xMin..graph.xMax) {
        val px = xToPx(traceX)
        drawLine(Color(0x66FFFFFF), Offset(px, 0f), Offset(px, height), strokeWidth = 2f)
    }
}

/** Picks a 1/2/5-style step so grid labels stay readable. */
private fun niceStep(raw: Double): Double {
    if (raw <= 0 || raw.isNaN() || raw.isInfinite()) return 1.0
    val exponent = floor(log10(raw))
    val base = raw / 10.0.pow(exponent)
    val nice = when {
        base <= 1.0 -> 1.0
        base <= 2.0 -> 2.0
        base <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * 10.0.pow(exponent)
}
