package com.vikashtechnology.mcalculator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikashtechnology.mcalculator.ui.screens.ConverterScreen
import com.vikashtechnology.mcalculator.ui.screens.GraphScreen
import com.vikashtechnology.mcalculator.ui.screens.MatrixScreen
import com.vikashtechnology.mcalculator.ui.screens.PadScreen
import com.vikashtechnology.mcalculator.ui.screens.SolverScreen
import com.vikashtechnology.mcalculator.ui.screens.StatsScreen
import kotlinx.coroutines.launch

enum class AppTab(val label: String, val icon: ImageVector) {
    CALC("Calc", Icons.Filled.Calculate),
    GRAPH("Graph", Icons.Filled.ShowChart),
    SOLVE("Solve", Icons.Filled.Functions),
    MATRIX("Matrix", Icons.Filled.GridOn),
    UNITS("Units", Icons.Filled.SwapHoriz),
    STATS("Stats", Icons.Filled.BarChart)
}

/** How many tiles each line of the drop-down holds: 6 tabs become 3 lines of 2. */
private const val ItemsPerLine = 2

@Composable
fun AppRoot(viewModel: CalculatorViewModel) {
    var tab by remember { mutableStateOf(AppTab.CALC) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // Back closes the drop-down first, then returns to the calculator.
    BackHandler(enabled = menuOpen || tab != AppTab.CALC) {
        if (menuOpen) menuOpen = false else tab = AppTab.CALC
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            TopBar(
                title = tab.label,
                onMenuClick = { menuOpen = !menuOpen }
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when (tab) {
                    AppTab.CALC -> PadScreen(viewModel = viewModel, showMessage = showMessage)
                    AppTab.GRAPH -> GraphScreen(viewModel = viewModel, showMessage = showMessage)
                    AppTab.SOLVE -> SolverScreen(viewModel = viewModel)
                    AppTab.MATRIX -> MatrixScreen(viewModel = viewModel)
                    AppTab.UNITS -> ConverterScreen(viewModel = viewModel, showMessage = showMessage)
                    AppTab.STATS -> StatsScreen(viewModel = viewModel)
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                )

                // Dim the tool while the drop-down is open; tapping dismisses it.
                if (menuOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { menuOpen = false }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopStart
        ) {
            AnimatedVisibility(
                visible = menuOpen,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                DropDownMenuPanel(
                    selected = tab,
                    onSelect = { entry ->
                        tab = entry
                        menuOpen = false
                    },
                    onClose = { menuOpen = false }
                )
            }
        }
    }
}

/** Compact header with the menu button; the drop-down replaces a bottom tab bar. */
@Composable
private fun TopBar(
    title: String,
    onMenuClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Open the tools menu",
                tint = colors.primary
            )
        }
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(color = colors.primary, shape = RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "M",
                color = colors.onPrimary,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** The panel itself: brand row plus [ItemsPerLine] tiles per line. */
@Composable
private fun DropDownMenuPanel(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    onClose: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "M Calculator",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "tap a tool to switch",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close the menu",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AppTab.entries.chunked(ItemsPerLine).forEach { line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    line.forEach { entry ->
                        MenuTile(
                            tab = entry,
                            selected = entry == selected,
                            onClick = { onSelect(entry) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Keep the last line aligned when it holds fewer items.
                    repeat(ItemsPerLine - line.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** One row-shaped entry: icon beside a label, highlighted when active. */
@Composable
private fun MenuTile(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        color = if (selected) colors.primaryContainer else colors.surfaceContainer,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = tab.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
