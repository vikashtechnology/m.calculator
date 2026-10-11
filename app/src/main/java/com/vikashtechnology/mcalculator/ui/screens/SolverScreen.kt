package com.vikashtechnology.mcalculator.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.vikashtechnology.mcalculator.ui.CalculatorViewModel
import com.vikashtechnology.mcalculator.ui.SolverMode

@Composable
fun SolverScreen(
    viewModel: CalculatorViewModel,
    modifier: Modifier = Modifier
) {
    val state = viewModel.solver
    val mode = state.mode

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
            SolverMode.entries.forEach { entry ->
                FilterChip(
                    selected = entry == mode,
                    onClick = {
                        viewModel.updateSolver {
                            it.copy(
                                mode = entry,
                                variable = if (entry == SolverMode.SUM || entry == SolverMode.PRODUCT) "k" else "x"
                            )
                        }
                    },
                    label = { Text("${entry.symbol}  ${entry.label}") }
                )
            }
        }

        OutlinedTextField(
            value = state.expression,
            onValueChange = { value -> viewModel.updateSolver { it.copy(expression = value) } },
            label = { Text("Expression in ${state.variable}") },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (mode) {
                SolverMode.SOLVE -> {
                    NumberField(
                        value = state.guess,
                        label = "Start at",
                        onValueChange = { value -> viewModel.updateSolver { it.copy(guess = value) } }
                    )
                }
                SolverMode.DERIVATIVE -> {
                    NumberField(
                        value = state.point,
                        label = "at x =",
                        onValueChange = { value -> viewModel.updateSolver { it.copy(point = value) } }
                    )
                }
                SolverMode.INTEGRAL, SolverMode.SUM, SolverMode.PRODUCT -> {
                    NumberField(
                        value = state.lower,
                        label = "from",
                        onValueChange = { value -> viewModel.updateSolver { it.copy(lower = value) } }
                    )
                    NumberField(
                        value = state.upper,
                        label = "to",
                        onValueChange = { value -> viewModel.updateSolver { it.copy(upper = value) } }
                    )
                }
            }
        }

        Button(
            onClick = { viewModel.runSolver(mode) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
        ) {
            Text(mode.label)
        }

        if (state.error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
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
                    .padding(top = 14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = state.result,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 26.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.detail.isNotEmpty()) {
                        Text(
                            text = state.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        Text(
            text = descriptionFor(mode),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun NumberField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.width(130.dp)
    )
}

private fun descriptionFor(mode: SolverMode): String = when (mode) {
    SolverMode.SOLVE ->
        "Finds the real values of x where the expression equals zero, searched between −100 and 100. Write it as f(x) = 0, for example x^2 - 4 or sin(x) - 0.5."
    SolverMode.DERIVATIVE ->
        "Numerical derivative at a point using a five point formula, plus the second derivative for reference."
    SolverMode.INTEGRAL ->
        "Definite integral with Simpson's rule (double checked with a finer grid). Handles negative limits by flipping the sign."
    SolverMode.SUM ->
        "Adds up the expression for every integer k between the two limits, e.g. k^2 from 1 to 10."
    SolverMode.PRODUCT ->
        "Multiplies the expression for every integer k between the two limits, e.g. k from 1 to 5 gives 120."
}
