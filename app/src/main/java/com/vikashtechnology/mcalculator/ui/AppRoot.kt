package com.vikashtechnology.mcalculator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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

@Composable
fun AppRoot(viewModel: CalculatorViewModel) {
    var tab by remember { mutableStateOf(AppTab.CALC) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    BackHandler(enabled = tab != AppTab.CALC) { tab = AppTab.CALC }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                AppTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = {
                            Icon(
                                imageVector = entry.icon,
                                contentDescription = entry.label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(entry.label, style = MaterialTheme.typography.labelSmall)
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (tab) {
                AppTab.CALC -> PadScreen(viewModel = viewModel, showMessage = showMessage)
                AppTab.GRAPH -> GraphScreen(viewModel = viewModel, showMessage = showMessage)
                AppTab.SOLVE -> SolverScreen(viewModel = viewModel)
                AppTab.MATRIX -> MatrixScreen(viewModel = viewModel)
                AppTab.UNITS -> ConverterScreen(viewModel = viewModel, showMessage = showMessage)
                AppTab.STATS -> StatsScreen(viewModel = viewModel)
            }
        }
    }
}
