package com.advanceai.calculator.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.advanceai.calculator.engine.Formatter
import com.advanceai.calculator.engine.UnitCategory
import com.advanceai.calculator.engine.Units
import com.advanceai.calculator.ui.CalculatorViewModel

@Composable
fun ConverterScreen(
    viewModel: CalculatorViewModel,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = viewModel.converter
    val clipboard = LocalClipboardManager.current
    val units = Units.byCategory(state.category)
    val from = Units.find(state.fromId)
    val to = Units.find(state.toId)
    val result = viewModel.converterResult()

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
            UnitCategory.entries.forEach { category ->
                FilterChip(
                    selected = state.category == category,
                    onClick = { viewModel.setConverterCategory(category) },
                    label = { Text(category.label) }
                )
            }
        }

        OutlinedTextField(
            value = state.input,
            onValueChange = { value ->
                viewModel.updateConverter { it.copy(input = value.filter { c -> c.isDigit() || c == '.' || c == '-' }) }
            },
            label = { Text("Value") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            UnitPicker(
                label = "From",
                options = units.map { it.id to "${it.name} (${it.symbol})" },
                selectedId = state.fromId,
                onSelect = { id -> viewModel.updateConverter { it.copy(fromId = id) } },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.swapConverterUnits() }) {
                Icon(Icons.Filled.SwapVert, contentDescription = "Swap units")
            }
            UnitPicker(
                label = "To",
                options = units.map { it.id to "${it.name} (${it.symbol})" },
                selectedId = state.toId,
                onSelect = { id -> viewModel.updateConverter { it.copy(toId = id) } },
                modifier = Modifier.weight(1f)
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clickable {
                    if (result.isNotEmpty()) {
                        clipboard.setText(AnnotatedString("$result ${to?.symbol ?: ""}"))
                        showMessage("Copied $result ${to?.symbol ?: ""}")
                    }
                }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "${state.input} ${from?.symbol ?: ""}  =",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                )
                Text(
                    text = "${result.ifEmpty { "—" }} ${to?.symbol ?: ""}",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 30.sp
                    )
                )
                Text(
                    text = "Tap to copy",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.freeText,
                onValueChange = { value -> viewModel.updateConverter { it.copy(freeText = value) } },
                label = { Text("Quick conversion, e.g. 12 km to mi") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { viewModel.solveFreeTextConversion() }) { Text("Convert") }
        }

        Text(
            text = "All ${units.size} ${state.category.label.lowercase()} units for ${state.input} ${from?.symbol ?: ""}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
        ) {
            items(units) { unit ->
                val value = state.input.trim().toDoubleOrNull()
                val converted = if (value != null && from != null) {
                    Formatter.number(Units.convert(value, from, unit))
                } else {
                    "—"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = unit.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$converted ${unit.symbol}",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        color = if (unit.id == state.toId) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/** Compact single-select list rendered as a scrollable row of chips. */
@Composable
private fun UnitPicker(
    label: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { (id, name) ->
                FilterChip(
                    selected = id == selectedId,
                    onClick = { onSelect(id) },
                    label = {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    },
                    modifier = Modifier.width(150.dp)
                )
            }
        }
    }
}
