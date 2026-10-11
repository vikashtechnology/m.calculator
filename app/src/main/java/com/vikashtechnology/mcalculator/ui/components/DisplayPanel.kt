package com.vikashtechnology.mcalculator.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikashtechnology.mcalculator.ui.CalcUiState

/**
 * The calculator display: status chips, the (editable) expression, a live
 * preview of the result, and the answer with its alternate forms.
 */
@Composable
fun DisplayPanel(
    state: CalcUiState,
    onExpressionChanged: (String, Int) -> Unit,
    onCursorChanged: (Int) -> Unit,
    onCycleAngle: () -> Unit,
    onTogglePolar: () -> Unit,
    onHistory: () -> Unit,
    onCopy: () -> Unit,
    onClearEntry: () -> Unit,
    onHint: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val scroll = rememberScrollState()

    var fieldValue by remember {
        mutableStateOf(TextFieldValue(state.expression, TextRange(state.cursor.coerceIn(0, state.expression.length))))
    }
    LaunchedEffect(state.expression, state.cursor) {
        val target = state.cursor.coerceIn(0, state.expression.length)
        if (fieldValue.text != state.expression || fieldValue.selection.end != target) {
            fieldValue = TextFieldValue(state.expression, TextRange(target))
        }
    }
    LaunchedEffect(state.cursor, state.expression) {
        scroll.animateScrollTo(scroll.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // --- status chips + actions -----------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusChip(
                text = state.angleMode.label,
                active = true,
                onClick = onCycleAngle
            )
            if (state.second) {
                StatusChip(text = "INV", active = true, onClick = { })
            }
            StatusChip(
                text = if (state.polar) "POLAR" else "RECT",
                active = state.polar,
                onClick = onTogglePolar
            )
            if (state.memory != 0.0) {
                StatusChip(text = "M", active = true, onClick = { })
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onHint) {
                Icon(
                    imageVector = Icons.Filled.Lightbulb,
                    contentDescription = "What can this calculator do?",
                    tint = colors.onSurfaceVariant
                )
            }
            IconButton(onClick = onHistory) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "History",
                    tint = colors.onSurfaceVariant
                )
            }
        }

        // --- expression ------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp, max = 132.dp)
        ) {
            if (state.expression.isEmpty()) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 28.sp
                    ),
                    color = colors.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(vertical = 8.dp)
                )
            }
            BasicTextField(
                value = fieldValue,
                onValueChange = {
                    fieldValue = it
                    if (it.text.length < state.expression.length) {
                        // Deletions from the system keyboard behave like ⌫.
                        onExpressionChanged(it.text, it.selection.end)
                    } else {
                        onExpressionChanged(it.text, it.selection.end)
                    }
                },
                textStyle = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface
                ),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboard?.hide()
                        onExpressionChanged(state.expression, state.expression.length)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
                    .horizontalScroll(scroll)
                    .padding(vertical = 8.dp)
            )
        }

        // --- live preview ----------------------------------------------------
        val preview = state.preview
        if (preview != null && state.error == null) {
            Text(
                text = "= $preview",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp
                ),
                color = colors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )
        }

        // --- error -----------------------------------------------------------
        state.error?.let { message ->
            Surface(
                color = colors.errorContainer,
                contentColor = colors.onErrorContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // --- result ----------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (state.resultText.isNotEmpty()) {
                    Text(
                        text = state.resultText,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colors.primary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.fractionHint?.let { hint ->
                        Text(
                            text = "≈ $hint",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            if (state.resultText.isNotEmpty() || state.expression.isNotEmpty()) {
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy result",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onClearEntry) {
                    Icon(
                        imageVector = Icons.Outlined.Backspace,
                        contentDescription = "Clear entry",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (active) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (active) colors.onPrimaryContainer else colors.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
