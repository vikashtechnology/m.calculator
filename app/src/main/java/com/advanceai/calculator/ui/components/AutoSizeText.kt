package com.advanceai.calculator.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Text that adapts to the box it is given: the font grows while the text still
 * fits and shrinks as soon as it does not, never leaving [minSize] below or
 * [maxSize] above.
 *
 * The calculator readout uses this so a short answer shows up large and a long
 * one comes back smaller but complete: the box keeps its size, the text does
 * the moving.
 */
@Composable
fun AutoSizeText(
    text: String,
    minSize: TextUnit,
    maxSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontFamily: FontFamily = FontFamily.Default,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = 1
) {
    if (text.isEmpty()) return
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier) {
        Text(
            text = text,
            style = LocalTextStyle.current.copy(
                fontSize = rememberFittingFontSize(
                    text = text,
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                    min = minSize,
                    max = maxSize,
                    maxLines = maxLines,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight,
                    measurer = measurer
                ),
                fontFamily = fontFamily,
                fontWeight = fontWeight
            ),
            color = color,
            maxLines = maxLines,
            // Only reachable if even the smallest size cannot fit the text.
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * The largest font size between [min] and [max] at which [text] still fits a
 * [maxWidth] by [maxHeight] box in at most [maxLines] lines.
 *
 * The text is measured with an unbounded line count, so the answer is based on
 * the height the text really needs rather than on a clipped layout: a long
 * number that would need five lines is rejected at three.
 */
@Composable
fun rememberFittingFontSize(
    text: String,
    maxWidth: Dp,
    maxHeight: Dp,
    min: TextUnit,
    max: TextUnit,
    maxLines: Int,
    fontFamily: FontFamily,
    fontWeight: FontWeight,
    measurer: TextMeasurer = rememberTextMeasurer()
): TextUnit {
    val density = LocalDensity.current
    val widthPx = if (maxWidth == Dp.Infinity) {
        Int.MAX_VALUE
    } else {
        with(density) { maxWidth.roundToPx() }.coerceAtLeast(0)
    }
    val heightPx = if (maxHeight == Dp.Infinity) {
        Int.MAX_VALUE
    } else {
        with(density) { maxHeight.roundToPx() }.coerceAtLeast(0)
    }
    return remember(text, widthPx, heightPx, min, max, maxLines, fontFamily, fontWeight) {
        fittingFontSize(text, widthPx, heightPx, min, max, maxLines, fontFamily, fontWeight, measurer)
    }
}

private fun fittingFontSize(
    text: String,
    widthPx: Int,
    heightPx: Int,
    min: TextUnit,
    max: TextUnit,
    maxLines: Int,
    fontFamily: FontFamily,
    fontWeight: FontWeight,
    measurer: TextMeasurer
): TextUnit {
    if (text.isEmpty() || widthPx <= 0) return max

    fun fits(sizeSp: Float): Boolean {
        val layout = measurer.measure(
            text = AnnotatedString(text),
            style = TextStyle(
                fontSize = sizeSp.sp,
                fontFamily = fontFamily,
                fontWeight = fontWeight
            ),
            overflow = TextOverflow.Clip,
            softWrap = true,
            maxLines = Int.MAX_VALUE,
            constraints = Constraints(maxWidth = widthPx)
        )
        return layout.lineCount <= maxLines && layout.size.height <= heightPx
    }

    // Bounded, monotone search: a bigger font never fits when a smaller one did
    // not, so the first size that fits is found by halving the range. Eight
    // steps land within a tenth of a point across the ranges the app uses.
    if (fits(max.value)) return max
    var low = min.value
    var high = max.value
    var best = low
    repeat(8) {
        val mid = (low + high) / 2f
        if (fits(mid)) {
            best = mid
            low = mid
        } else {
            high = mid
        }
    }
    return best.sp
}
