package com.vikashtechnology.mcalculator.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikashtechnology.mcalculator.ui.CalcKey
import com.vikashtechnology.mcalculator.ui.Keypads
import com.vikashtechnology.mcalculator.ui.KeypadPage
import com.vikashtechnology.mcalculator.ui.KeyStyle

/** Renders a full keypad page, sized to fill the space it is given. */
@Composable
fun Keypad(
    page: KeypadPage,
    second: Boolean,
    onKey: (CalcKey) -> Unit,
    onLongPressKey: (CalcKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = Keypads.keysFor(page)
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { key ->
                    KeyButton(
                        key = key,
                        second = second,
                        onClick = { onKey(key) },
                        onLongClick = { onLongPressKey(key) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    key: CalcKey,
    second: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val useAlt = second && key.altAction != null
    val label = if (useAlt) key.altLabel ?: key.label else key.label

    val (container, content, border) = when (key.style) {
        KeyStyle.DIGIT -> Triple(colors.surfaceContainerHigh, colors.onSurface, null)
        KeyStyle.OPERATOR -> Triple(colors.primaryContainer, colors.onPrimaryContainer, null)
        KeyStyle.FUNCTION -> Triple(colors.surfaceContainer, colors.secondary, null)
        KeyStyle.CONSTANT -> Triple(colors.surfaceContainer, colors.tertiary, null)
        KeyStyle.ACCENT -> Triple(colors.primary, colors.onPrimary, null)
        KeyStyle.DANGER -> Triple(colors.errorContainer, colors.onErrorContainer, null)
    }
    val activeContainer = if (useAlt && key.style == KeyStyle.FUNCTION) colors.secondaryContainer else container

    Surface(
        color = activeContainer,
        contentColor = content,
        shape = RoundedCornerShape(16.dp),
        border = border?.let { BorderStroke(1.dp, it) },
        tonalElevation = if (key.style == KeyStyle.FUNCTION) 0.dp else 1.dp,
        modifier = modifier.pointerInput(key.id, second) {
            detectTapGestures(
                onTap = { onClick() },
                onLongPress = { onLongClick() }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = if (key.style == KeyStyle.FUNCTION || key.style == KeyStyle.CONSTANT) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.titleLarge
                }.copy(
                    fontSize = labelSize(label, key.style),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1
            )
            if (!second && key.altLabel != null && key.style == KeyStyle.FUNCTION) {
                Text(
                    text = key.altLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = content.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 5.dp)
                )
            }
        }
    }
}

private fun labelSize(label: String, style: KeyStyle): androidx.compose.ui.unit.TextUnit = when {
    label.length >= 5 -> 12.sp
    label.length == 4 -> 13.sp
    label.length == 3 -> 15.sp
    style == KeyStyle.FUNCTION || style == KeyStyle.CONSTANT -> 16.sp
    else -> 21.sp
}
