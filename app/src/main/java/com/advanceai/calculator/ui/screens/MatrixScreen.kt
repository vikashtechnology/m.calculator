package com.advanceai.calculator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.advanceai.calculator.ui.CalculatorViewModel
import com.advanceai.calculator.ui.MatrixOp
import com.advanceai.calculator.ui.MatrixState

@Composable
fun MatrixScreen(
    viewModel: CalculatorViewModel,
    modifier: Modifier = Modifier
) {
    val state = viewModel.matrix

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MatrixOp.entries.forEach { op ->
                FilterChip(
                    selected = state.operation == op,
                    onClick = { viewModel.updateMatrix { it.copy(operation = op, result = "", error = null) } },
                    label = { Text(op.label) }
                )
            }
        }

        SizeControls(state, viewModel)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.editingA,
                onClick = { viewModel.updateMatrix { it.copy(editingA = true, selectedCell = -1) } },
                label = { Text("Matrix A (${state.rowsA}×${state.colsA})") }
            )
            FilterChip(
                selected = !state.editingA,
                onClick = { viewModel.updateMatrix { it.copy(editingA = false, selectedCell = -1) } },
                label = { Text("Matrix B (${state.rowsB}×${state.colsB})") }
            )
        }

        MatrixGrid(state, viewModel)

        CellEditor(state, viewModel)

        Button(
            onClick = { viewModel.computeMatrix() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Text("Compute ${state.operation.label}")
        }

        if (state.error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (state.result.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(
                    text = state.result,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(14.dp),
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            text = "Tap a cell to edit it. Matrices are also available on the pad: [1, 2; 3, 4].",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )
    }
}

@Composable
private fun SizeControls(state: MatrixState, viewModel: CalculatorViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("A", style = MaterialTheme.typography.labelLarge)
        Stepper("rows", state.rowsA) { viewModel.setMatrixSize(it, state.colsA, state.rowsB, state.colsB) }
        Stepper("cols", state.colsA) { viewModel.setMatrixSize(state.rowsA, it, state.rowsB, state.colsB) }
        Text("B", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
        Stepper("rows", state.rowsB) { viewModel.setMatrixSize(state.rowsA, state.colsA, it, state.colsB) }
        Stepper("cols", state.colsB) { viewModel.setMatrixSize(state.rowsA, state.colsA, state.rowsB, it) }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { if (value > 1) onChange(value - 1) }) { Text("−") }
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(18.dp)
        )
        TextButton(onClick = { if (value < 6) onChange(value + 1) }) { Text("+") }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MatrixGrid(state: MatrixState, viewModel: CalculatorViewModel) {
    val rows = if (state.editingA) state.rowsA else state.rowsB
    val cols = if (state.editingA) state.colsA else state.colsB
    val data = if (state.editingA) state.dataA else state.dataB

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (r in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in 0 until cols) {
                    val index = r * cols + c
                    val selected = state.selectedCell == index
                    Box(
                        modifier = Modifier
                            .size(width = 62.dp, height = 44.dp)
                            .background(
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                viewModel.updateMatrix { it.copy(selectedCell = index) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = data.getOrElse(index) { "0" },
                            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CellEditor(state: MatrixState, viewModel: CalculatorViewModel) {
    val rows = if (state.editingA) state.rowsA else state.rowsB
    val cols = if (state.editingA) state.colsA else state.colsB
    val index = state.selectedCell
    val gridName = if (state.editingA) "A" else "B"
    val label = if (index >= 0 && index < rows * cols) {
        val r = index / cols
        val c = index % cols
        "$gridName[${r + 1}, ${c + 1}]"
    } else {
        "Select a cell"
    }
    OutlinedTextField(
        value = if (index >= 0) {
            (if (state.editingA) state.dataA else state.dataB).getOrElse(index) { "0" }
        } else {
            ""
        },
        onValueChange = { value ->
            val clean = value.replace(',', '.').filter { it.isDigit() || it == '.' || it == '-' }
            if (index >= 0) viewModel.setMatrixCell(state.editingA, index, clean)
        },
        enabled = index >= 0,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    )
}
