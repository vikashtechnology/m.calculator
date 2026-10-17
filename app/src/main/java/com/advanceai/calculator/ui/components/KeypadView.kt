package com.advanceai.calculator.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.advanceai.calculator.ui.CalcKey
import com.advanceai.calculator.ui.Keypads
import com.advanceai.calculator.ui.KeypadPage
import com.advanceai.calculator.ui.KeyStyle

/*
 * Keypad geometry. These are shared with the pad screen, which asks for a
 * keypad of exactly [keypadHeightFor] and gives the rest of the height to the
 * readout — that is what keeps every key the same size whatever page is open,
 * whatever the screen is, and whether or not an ad is showing.
 */
val KeypadGap = 6.dp

/** The size a key is meant to be; keys only ever shrink if the screen cannot fit them. */
val KeypadIdealKeyHeight = 54.dp

/** The smallest a key may get before the keypad is allowed to overflow instead. */
val KeypadMinKeyHeight = 38.dp

/** Total height of a keypad with [rows] rows of keys [keyHeight] tall. */
fun keypadHeightFor(rows: Int, keyHeight: Dp): Dp =
    keyHeight * rows + KeypadGap * (rows - 1).coerceAtLeast(0)

/**
 * Renders a full keypad page with keys of a fixed [keyHeight], centred in the
 * space it is given. The caller hands out a height measured for the busiest
 * page, so a page with fewer rows keeps its keys the same size and the readout
 * above the keypad does not move.
 */
@Composable
fun Keypad(
    page: KeypadPage,
    second: Boolean,
    onKey: (CalcKey) -> Unit,
    onLongPressKey: (CalcKey) -> Unit,
    keyHeight: Dp = KeypadIdealKeyHeight,
    /** The knock (and optional click) a press makes; see rememberKeyFeedback. */
    feedback: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val rows = Keypads.keysFor(page)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(KeypadGap, Alignment.CenterVertically)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(keyHeight),
                horizontalArrangement = Arrangement.spacedBy(KeypadGap)
            ) {
                row.forEach { key ->
                    KeyButton(
                        key = key,
                        second = second,
                        onClick = {
                            feedback()
                            onKey(key)
                        },
                        onLongClick = {
                            feedback()
                            onLongPressKey(key)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
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

    // Every pair here is (background, text-on-that-background) from the scheme,
    // so the labels stay readable in both themes.
    val (container, content) = when (key.style) {
        KeyStyle.DIGIT -> colors.surfaceContainerHigh to colors.onSurface
        KeyStyle.OPERATOR -> colors.primaryContainer to colors.onPrimaryContainer
        KeyStyle.FUNCTION -> colors.surfaceContainer to colors.secondary
        KeyStyle.CONSTANT -> colors.surfaceContainer to colors.tertiary
        KeyStyle.ACCENT -> colors.primary to colors.onPrimary
        KeyStyle.DANGER -> colors.errorContainer to colors.onErrorContainer
    }
    val activeContainer = if (useAlt && key.style == KeyStyle.FUNCTION) colors.secondaryContainer else container
    val activeContent = if (useAlt && key.style == KeyStyle.FUNCTION) colors.onSecondaryContainer else content

    // --- the press animation -------------------------------------------
    // A key sinks into the pad while a finger is on it and springs back when
    // let go, the way a physical key behaves. `pressed` is set from the gesture
    // itself (not from the click), so it tracks a finger that slides off, a
    // long press, and a cancelled touch alike.
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        // Down is quick and linear; up overshoots very slightly, which is the
        // "click" the eye reads even before the digit appears.
        animationSpec = if (pressed) {
            tween(durationMillis = 60)
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
        },
        label = "keyPressScale"
    )
    val pressLift by animateDpAsState(
        targetValue = if (pressed) 1.5.dp else 0.dp,
        animationSpec = tween(durationMillis = if (pressed) 60 else 140),
        label = "keyPressLift"
    )
    val flatKey = key.style == KeyStyle.FUNCTION
    val pressShadow by animateDpAsState(
        targetValue = if (pressed || flatKey) 0.dp else 2.dp,
        animationSpec = tween(durationMillis = 90),
        label = "keyPressShadow"
    )

    Surface(
        color = activeContainer,
        contentColor = activeContent,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = if (flatKey) 0.dp else 2.dp,
        // A soft shadow makes the keys read as physical buttons in both themes;
        // it flattens as the key is pushed in.
        shadowElevation = pressShadow,
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .offset(y = pressLift)
            .pointerInput(key.id, second) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        try {
                            tryAwaitRelease()
                        } finally {
                            pressed = false
                        }
                    },
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
                // Sits in the corner of a function key. `onSurfaceVariant` is the
                // readable-on-any-surface role; the key's own tint at 55% was not.
                Text(
                    text = key.altLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 4.dp)
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
