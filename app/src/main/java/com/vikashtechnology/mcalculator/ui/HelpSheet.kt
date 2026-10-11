package com.vikashtechnology.mcalculator.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Built-in reference: syntax, functions and shortcuts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text("How to use M Calculator", style = MaterialTheme.typography.titleLarge)
            Section(
                "Typing",
                listOf(
                    "2π, 3(4+5), 2sin(30) — multiplication can be left out.",
                    "50% is 0.5, so 50%+2 gives 2.5.",
                    "10%3 and mod(10,3) are both 1 (remainder).",
                    "5! is a factorial, 0.5! uses the gamma function.",
                    "Unbalanced brackets are closed automatically when you press =.",
                    "Tap DEG/RAD/GRAD in the display to change the angle unit.",
                    "Long press AC to clear everything, long press a key for its description."
                )
            )
            Section(
                "Functions",
                listOf(
                    "Trig: sin cos tan csc sec cot asin acos atan atan2 (angle mode aware)",
                    "Hyperbolic: sinh cosh tanh csch sech coth asinh acosh atanh",
                    "Logs & powers: ln log log2 exp sqrt cbrt nthroot x^y hypot",
                    "Rounding: floor ceil round(x, digits) trunc frac abs sign",
                    "Combinatorics: ncr npr fact gamma fib gcd lcm mod",
                    "Statistics: mean median stdev variance min max sum product count",
                    "Complex: i, re im conj arg polar, sqrt, ln, sin, cos, tan of complex numbers",
                    "Calculus screen: solve, derivative, integral, Σ and Π series"
                )
            )
            Section(
                "Matrices",
                listOf(
                    "[1, 2; 3, 4] is a 2×2 matrix; commas separate columns, ; starts a row.",
                    "det inv transpose trace rank dot cross solve identity dims normalize",
                    "Arithmetic works: [1,2;3,4] + [1,1;1,1], 2*[1,2;3,4], A^3, A*B.",
                    "The Matrix tab lets you type them cell by cell."
                )
            )
            Section(
                "Answers, memory and variables",
                listOf(
                    "Ans is the previous result and can be used in the next expression.",
                    "M is memory: MS stores, M+ / M− change it, MR recalls, long press MS clears.",
                    "A–F and x are variables. Long press to store the current result, tap to use it.",
                    "History (the clock icon) keeps the last 200 calculations — tap to reuse."
                )
            )
            Section(
                "Ask AI",
                listOf(
                    "Tap the Ask AI card at the top of the pad, or the Ask AI row in the menu.",
                    "Sign in with a Puter account the first time; 'Continue as guest' makes a temporary one.",
                    "Upload a photo, a PDF or a text file of a question and press Solve this question.",
                    "It only solves the question in the file you upload. This screen needs internet;",
                    "everything else in the app works offline."
                )
            )
            Section(
                "Units and statistics",
                listOf(
                    "Convert tab: pick a category, type a value, switch units instantly.",
                    "Type things like '12 km to mi' in the free-text box for a quick conversion.",
                    "Stats tab: paste numbers separated by commas or spaces for a full summary."
                )
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                Text("Got it")
            }
        }
    }
}

@Composable
private fun Section(title: String, lines: List<String>) {
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        lines.forEach { line ->
            Text(
                text = "• $line",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
