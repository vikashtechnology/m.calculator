package com.advanceai.calculator.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * What the ad slot shows when there is no ad to show.
 *
 * The slot keeps its height whether an ad fills or not (that is what stops the
 * keypad jumping), so the empty strip is worth using: a tip about the app rides
 * there, changing every few seconds, and tapping it opens the help sheet. It is
 * deliberately plain text on a flat surface — it must never look like an ad.
 */
@Composable
fun AdSlotStrip(
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var index by remember { mutableIntStateOf(0) }

    // A slow ticker, so a tip the user has already read makes way for another
    // one while they are still on the screen.
    LaunchedEffect(Unit) {
        while (true) {
            delay(9_000)
            index = (index + 1) % Tips.size
        }
    }

    Surface(
        onClick = onOpenHelp,
        color = colors.surfaceContainer,
        contentColor = colors.onSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lightbulb,
                contentDescription = null,
                tint = colors.tertiary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = Tips[index],
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Open the help",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** Short enough to fit two lines in a 50dp strip. */
private val Tips = listOf(
    "Long press any key to find out what it does.",
    "INV switches sin to sin⁻¹, x² to √, and more — tap the chip above the keys.",
    "Tap the plot in Graph to trace the value at that x.",
    "Long press a letter key (A–F, x) to store the current answer in it.",
    "The sparkle in the readout explains the sum on screen, step by step.",
    "Share a sum from any app to this one and it is worked out for you.",
    "Settings can group digits the Indian way: 12,34,567.",
    "Slide the keypad pages: Basic, Func, Const, Matrix."
)
