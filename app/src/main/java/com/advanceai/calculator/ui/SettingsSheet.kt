package com.advanceai.calculator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.advanceai.calculator.data.AppSettings
import com.advanceai.calculator.engine.DisplayOptions
import com.advanceai.calculator.ui.theme.appChipColors

/**
 * The app's own settings: how results are printed, and what the keys do when
 * they are pressed. Everything here is remembered across launches.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)

            SettingTitle(
                "Digits shown",
                "How many digits a result is rounded to before it is displayed. " +
                    "The engine always works at full precision."
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DisplayOptions.DIGIT_CHOICES.forEach { choice ->
                    FilterChip(
                        selected = settings.digits == choice,
                        onClick = { onChange(settings.copy(digits = choice)) },
                        label = { Text(choice.toString()) },
                        colors = appChipColors()
                    )
                }
            }

            SettingSwitch(
                title = "Indian digit grouping",
                detail = "Group digits the Indian way: 12,34,567 instead of 1,234,567. " +
                    "Useful for rupees, lakhs and crores.",
                checked = settings.indianGrouping,
                onCheckedChange = { onChange(settings.copy(indianGrouping = it)) }
            )

            SettingSwitch(
                title = "Vibrate on key press",
                detail = "A short knock each time a key on the pad is pressed.",
                checked = settings.haptics,
                onCheckedChange = { onChange(settings.copy(haptics = it)) }
            )

            SettingSwitch(
                title = "Key click sound",
                detail = "Plays the phone's own click sound on each key press.",
                checked = settings.keySound,
                onCheckedChange = { onChange(settings.copy(keySound = it)) }
            )

            Text(
                text = "Results always keep the full 12-digit precision internally; " +
                    "these settings only change what you read on screen. Angle mode, " +
                    "memory and the theme are in the menu.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun SettingTitle(title: String, detail: String) {
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
