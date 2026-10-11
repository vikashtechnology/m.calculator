package com.advanceai.calculator.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.advanceai.calculator.ui.CalcKey
import com.advanceai.calculator.ui.CalculatorViewModel
import com.advanceai.calculator.ui.components.AiHeroCard
import com.advanceai.calculator.ui.components.DisplayPanel
import com.advanceai.calculator.ui.components.Keypad
import com.advanceai.calculator.ui.HelpSheet
import com.advanceai.calculator.ui.HistorySheet
import com.advanceai.calculator.ui.KeyAction
import com.advanceai.calculator.ui.KeypadPage

@Composable
fun PadScreen(
    viewModel: CalculatorViewModel,
    showMessage: (String) -> Unit,
    onOpenAi: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = viewModel.ui
    val clipboard = LocalClipboardManager.current
    var showHistory by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // Hero section: the AI entry point sits at the very top of the pad.
        AiHeroCard(onOpenAi = onOpenAi)

        DisplayPanel(
            state = state,
            onExpressionChanged = { text, cursor -> viewModel.onExpressionChanged(text, cursor) },
            onCursorChanged = { viewModel.setCursor(it) },
            onCycleAngle = { viewModel.cycleAngleMode() },
            onTogglePolar = { viewModel.setPolar(!state.polar) },
            onHistory = { showHistory = true },
            onCopy = {
                val text = state.resultText.ifEmpty { state.expression }
                if (text.isNotEmpty()) {
                    clipboard.setText(AnnotatedString(text))
                    showMessage("Copied $text")
                }
            },
            onClearEntry = { viewModel.clearAll() },
            onHint = { showHelp = true }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeypadPage.entries.forEach { page ->
                FilterChip(
                    selected = state.page == page,
                    onClick = { viewModel.setPage(page) },
                    label = { Text(page.label) },
                    colors = FilterChipDefaults.filterChipColors()
                )
            }
            FilterChip(
                selected = state.second,
                onClick = { viewModel.onKey(CalcKey("inv", "INV", KeyAction.ToggleSecond)) },
                label = { Text("INV") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Functions,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }

        Keypad(
            page = state.page,
            second = state.second,
            onKey = { key -> viewModel.onKey(key) },
            onLongPressKey = { key ->
                if (key.longPressAction != null) {
                    viewModel.onLongPress(key)
                    key.longPressLabel?.let { showMessage("${key.label}: $it") }
                } else if (!key.description.isNullOrEmpty()) {
                    showMessage(key.description)
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }

    if (showHistory) {
        HistorySheet(
            history = state.history,
            onUseExpression = { entry ->
                viewModel.useHistory(entry)
                showHistory = false
            },
            onUseResult = { entry ->
                viewModel.insertResultOf(entry)
                showHistory = false
            },
            onClear = { viewModel.clearHistory() },
            onDismiss = { showHistory = false },
            showMessage = showMessage
        )
    }

    if (showHelp) {
        HelpSheet(onDismiss = { showHelp = false })
    }
}
