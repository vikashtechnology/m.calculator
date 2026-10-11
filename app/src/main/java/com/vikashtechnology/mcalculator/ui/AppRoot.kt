package com.vikashtechnology.mcalculator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

/** Width of the left navigation sidebar. */
private val SidebarWidth = 104.dp

/** How many items each line of the sidebar holds: 6 tabs become 3 lines of 2. */
private const val ItemsPerLine = 2

@Composable
fun AppRoot(viewModel: CalculatorViewModel) {
    var tab by remember { mutableStateOf(AppTab.CALC) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    BackHandler(enabled = tab != AppTab.CALC) { tab = AppTab.CALC }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            AppSidebar(
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier
                    .fillMaxHeight()
                    .width(SidebarWidth)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Start + WindowInsetsSides.Vertical
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.End + WindowInsetsSides.Vertical
                        )
                    )
            ) {
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
            }
        }
    }
}

/** Brand tile plus the tabs laid out as [ItemsPerLine] items per line. */
@Composable
private fun AppSidebar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandMark()
            Spacer(modifier = Modifier.height(14.dp))

            AppTab.entries.chunked(ItemsPerLine).forEach { line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    line.forEach { entry ->
                        SidebarItem(
                            tab = entry,
                            selected = entry == selected,
                            onClick = { onSelect(entry) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "v1.0",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun BrandMark() {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color = colors.primary, shape = RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "M",
            color = colors.onPrimary,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        )
    }
}

/** One tappable tab tile: icon above a short label. */
@Composable
private fun SidebarItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh.copy(alpha = 0.55f),
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        shape = RoundedCornerShape(13.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                modifier = Modifier.size(21.dp)
            )
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
