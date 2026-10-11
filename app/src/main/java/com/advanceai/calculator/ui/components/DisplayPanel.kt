package com.advanceai.calculator.ui.components

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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import com.advanceai.calculator.ui.CalcUiState

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
    onOpenAi: () -> Unit,
    onCopy: () -> Unit,
    onClearEntry: () -> Unit,
    onHint: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

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
            // Ask AI lives here now, beside the history button, instead of
            // taking a card of its own above the keypad.
            IconButton(onClick = onOpenAi) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = "Ask AI",
                    tint = colors.primary
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
                    color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(vertical = 8.dp)
                )
            }
            // Read-only on purpose: the app is driven by its own keypad, so the
            // soft keyboard never covers the display. `showKeyboardOnFocus` is
            // false as well so focusing the field cannot summon the IME either.
            BasicTextField(
                value = fieldValue,
                onValueChange = { /* only the keypad edits the expression */ },
                readOnly = true,
                textStyle = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface
                ),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
                onTextLayout = { layout -> textLayout = layout },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
                    .horizontalScroll(scroll)
                    .padding(vertical = 8.dp)
                    .pointerInput(state.expression) {
                        detectTapGestures { offset ->
                            // Tapping the text moves the caret to that character.
                            textLayout?.let { layout ->
                                val position = layout.getOffsetForPosition(
                                    Offset(offset.x + scroll.value, offset.y)
                                )
                                onCursorChanged(position)
                            }
                        }
                    }
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
                color = colors.onSurfaceVariant,
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
                shadowElevation = 2.dp,
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
