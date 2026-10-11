package com.advanceai.calculator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.advanceai.calculator.engine.Formatter
import com.advanceai.calculator.engine.StatsSummary
import com.advanceai.calculator.ui.CalculatorViewModel

@Composable
fun StatsScreen(
    viewModel: CalculatorViewModel,
    modifier: Modifier = Modifier
) {
    val state = viewModel.stats

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Statistics",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )
        OutlinedTextField(
            value = state.data,
            onValueChange = { viewModel.updateStats(it) },
            label = { Text("Data — commas, spaces or new lines") },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 110.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { viewModel.computeStats() }, modifier = Modifier.weight(1f)) {
                Text("Analyse")
            }
            TextButton(onClick = { viewModel.updateStats("") }) { Text("Clear") }
        }

        state.error?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(message, modifier = Modifier.padding(12.dp))
            }
        }

        state.summary?.let { summary ->
            StatsTable(summary, Modifier.padding(top = 14.dp, bottom = 24.dp))
        }
    }
}

@Composable
private fun StatsTable(summary: StatsSummary, modifier: Modifier = Modifier) {
    val rows = listOf(
        "Count" to summary.count.toString(),
        "Sum" to Formatter.number(summary.sum),
        "Mean" to Formatter.number(summary.mean),
        "Median" to Formatter.number(summary.median),
        "Minimum" to Formatter.number(summary.min),
        "Maximum" to Formatter.number(summary.max),
        "Range" to Formatter.number(summary.range),
        "Q1" to Formatter.number(summary.q1),
        "Q3" to Formatter.number(summary.q3),
        "Std dev (sample)" to Formatter.number(summary.stdevSample),
        "Std dev (population)" to Formatter.number(summary.stdevPopulation),
        "Variance (sample)" to Formatter.number(summary.varianceSample)
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            }
        }
    }
}
