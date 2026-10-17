package com.advanceai.calculator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.advanceai.calculator.BuildConfig
import com.advanceai.calculator.ads.Ads
import com.advanceai.calculator.data.AppSettings
import com.advanceai.calculator.engine.DigitGrouping
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

            SettingTitle(
                "Digit grouping",
                "Separators in long numbers. The Indian style reads in lakhs and " +
                    "crores: 12,34,567."
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GROUPING_CHOICES.forEach { (choice, label) ->
                    FilterChip(
                        selected = settings.grouping == choice,
                        onClick = { onChange(settings.copy(grouping = choice)) },
                        label = { Text(label) },
                        colors = appChipColors()
                    )
                }
            }

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

            // --- ads ----------------------------------------------------------
            // The ad diagnostics (status line, unit ids, no-fill notes) are for
            // us, not for the person using the calculator: a release build says
            // nothing about AdMob at all. They stay in a debug build so a
            // banner can still be checked on the phone.
            if (BuildConfig.DEBUG) {
                SettingTitle(
                    "Ad diagnostics (debug build only)",
                    "Not shown to anyone using a release build."
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = Ads.status,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Units in this build: ${Ads.unitSummary}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = "These only change what you read on screen — the maths behind " +
                    "them is always exact. Angle mode and the theme are in the menu.",
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

/** The three grouping styles, with the label each chip shows. */
private val GROUPING_CHOICES = listOf(
    DigitGrouping.NONE to "1234567",
    DigitGrouping.WESTERN to "1,234,567",
    DigitGrouping.INDIAN to "12,34,567"
)

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
