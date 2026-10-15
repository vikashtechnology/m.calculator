package com.advanceai.calculator.ui

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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.advanceai.calculator.ads.AdBanner
import com.advanceai.calculator.ads.Ads
import com.advanceai.calculator.ads.activityOrNull
import com.advanceai.calculator.ui.components.AiHeroMenuRow
import com.advanceai.calculator.ui.theme.appChipColors
import com.advanceai.calculator.ui.screens.AiChatScreen
import com.advanceai.calculator.ui.screens.ConverterScreen
import com.advanceai.calculator.ui.screens.GraphScreen
import com.advanceai.calculator.ui.screens.MatrixScreen
import com.advanceai.calculator.ui.screens.PadScreen
import com.advanceai.calculator.ui.screens.SolverScreen
import com.advanceai.calculator.data.AppThemeMode
import com.advanceai.calculator.data.Telemetry
import com.advanceai.calculator.ui.screens.EverydayTool
import com.advanceai.calculator.ui.screens.StatsScreen
import com.advanceai.calculator.ui.screens.ToolsScreen
import kotlinx.coroutines.launch

enum class AppTab(val label: String, val icon: ImageVector) {
    CALC("Calc", Icons.Filled.Calculate),
    GRAPH("Graph", Icons.AutoMirrored.Filled.ShowChart),
    SOLVE("Solve", Icons.Filled.Functions),
    MATRIX("Matrix", Icons.Filled.GridOn),
    UNITS("Units", Icons.Filled.SwapHoriz),
    STATS("Stats", Icons.Filled.BarChart)
}

/** How many tiles each line of the drop-down holds: 6 tabs become 3 lines of 2. */
private const val ItemsPerLine = 2

/** Everyday calculators are three to a line so the menu stays compact. */
private const val ToolsPerLine = 3

/**
 * Roughly what the header takes. It is only used to decide whether an ad slot
 * fits, so a few dp either way changes nothing.
 */
private val TopBarHeight = 56.dp

/**
 * Room the calculator pad needs before an ad may take a slice of the screen:
 * about 250dp for the readout and the page chips, plus about 220dp so five
 * rows of keys stay comfortably tappable, plus the banner slot itself. Screens
 * that cannot offer this (small phones, split screen) simply show no ad.
 */
private val MinContentHeightForAds = 560.dp

/**
 * The banner slot: one 320×50 ad plus the gap above it. The slot is reserved
 * for the whole life of the screen, whether or not an ad turns up, so the keys
 * above it never change size when an ad loads.
 */
private val AdSlotHeight = 50.dp
private val AdSlotGap = 6.dp

@Composable
fun AppRoot(
    viewModel: CalculatorViewModel,
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    onThemeModeChange: (AppThemeMode) -> Unit = {},
    /** Text handed over by another app through the share sheet, if any. */
    sharedText: String? = null,
    onSharedHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var tab by remember { mutableStateOf(AppTab.CALC) }
    var menuOpen by remember { mutableStateOf(false) }
    var aiOpen by remember { mutableStateOf(false) }
    var toolOpen by remember { mutableStateOf<EverydayTool?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    // A shared sentence is a question for Ask AI; a shared sum is for the pad.
    var aiSeed by remember { mutableStateOf<String?>(null) }
    // Anonymous usage counts feed the admin dashboard; the switch can turn
    // them off, and nothing is sent while it is off.
    var statsOn by remember { mutableStateOf(Telemetry.isEnabled(context)) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // Something arrived from the share sheet: the pad takes it when it is a
    // calculation, Ask AI takes it otherwise.
    LaunchedEffect(sharedText) {
        val text = sharedText ?: return@LaunchedEffect
        onSharedHandled()
        if (viewModel.acceptShared(text)) {
            tab = AppTab.CALC
            toolOpen = null
            showMessage("Calculated from what you shared")
        } else {
            aiSeed = text.trim()
            tab = AppTab.CALC
            toolOpen = null
            aiOpen = true
        }
    }

    // Back closes the drop-down first, then returns to the calculator.
    // While Ask AI is open its own screen owns the back gesture.
    BackHandler(enabled = menuOpen || toolOpen != null || (!aiOpen && tab != AppTab.CALC)) {
        when {
            menuOpen -> menuOpen = false
            toolOpen != null -> toolOpen = null
            else -> tab = AppTab.CALC
        }
    }

    val activity = remember(context) { context.activityOrNull() }
    // Ads need an activity for the consent form and the ad itself; without one
    // (a preview, for instance) the app runs exactly as it did before.
    val adsPossible = activity != null && !aiOpen

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // The ad slot only opens when the screen has room to spare: the
            // calculator pad needs its readout, page chips and five tappable
            // rows of keys, and the threshold covers all of that plus the
            // banner. Short screens and split-screen windows simply get no ad,
            // so the keypad can never be squeezed into something untappable.
            val showAdSlot =
                adsPossible && maxHeight >= TopBarHeight + MinContentHeightForAds

            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Ask AI is a full-screen sub-page with its own header, so the app
                // bar steps aside while it is open.
                if (!aiOpen) {
                    // Explicitly typed so the compiler sees a plain lambda.
                    val closeTool: (() -> Unit)? = if (toolOpen == null) {
                        null
                    } else {
                        { toolOpen = null }
                    }
                    TopBar(
                        title = toolOpen?.title ?: tab.label,
                        onMenuClick = { menuOpen = !menuOpen },
                        onBack = closeTool
                    )
                }

                // Everything above the ad slot: the tool that is open, and the
                // snackbar that belongs to it.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (aiOpen) {
                        // Ask AI is a chat: type, photograph or attach, and the
                        // answer comes back as a message. No memory by design.
                        AiChatScreen(
                            onClose = {
                                aiOpen = false
                                aiSeed = null
                            },
                            showMessage = showMessage,
                            initialQuestion = aiSeed
                        )
                    } else if (toolOpen != null) {
                        ToolsScreen(
                            tool = toolOpen!!,
                            onSelectTool = { toolOpen = it }
                        )
                    } else {
                        when (tab) {
                            AppTab.CALC -> PadScreen(
                                viewModel = viewModel,
                                showMessage = showMessage,
                                onOpenAi = { question ->
                                    aiSeed = question
                                    aiOpen = true
                                }
                            )
                            AppTab.GRAPH -> GraphScreen(viewModel = viewModel, showMessage = showMessage)
                            AppTab.SOLVE -> SolverScreen(viewModel = viewModel)
                            AppTab.MATRIX -> MatrixScreen(viewModel = viewModel)
                            AppTab.UNITS -> ConverterScreen(viewModel = viewModel, showMessage = showMessage)
                            AppTab.STATS -> StatsScreen(viewModel = viewModel)
                        }
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                    )
                }

                // The AdMob banner, pinned under everything else. Its height is
                // reserved from the first frame — the keys are laid out with
                // this space already gone — so an ad appearing can never resize
                // the keypad. If no ad arrives the slot simply stays empty.
                if (showAdSlot) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AdSlotGap)
                            .height(AdSlotHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        AdBanner()
                    }
                }
            }

            // Dim the app while the drop-down is open; tapping dismisses it. This
            // sits over the bar and the ad slot as well, so a tap meant for the
            // menu can never land on the ad underneath it.
            if (menuOpen && !aiOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.scrim.copy(alpha = 0.45f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { menuOpen = false }
                )
            }

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
                    onOpenAi = {
                        menuOpen = false
                        tab = AppTab.CALC
                        aiOpen = true
                    },
                    onOpenTool = { tool ->
                        menuOpen = false
                        toolOpen = tool
                    },
                    statsOn = statsOn,
                    onStatsChange = { enabled ->
                        statsOn = enabled
                        Telemetry.setEnabled(context, enabled)
                    },
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    // Google only asks for this entry point where the privacy
                    // rules require one; until then the row is not shown at all.
                    onPrivacyOptions = if (activity != null && Ads.privacyOptionsRequired) {
                        { Ads.showPrivacyOptions(activity) }
                    } else {
                        null
                    },
                    onOpenSettings = {
                        menuOpen = false
                        settingsOpen = true
                    },
                    onClose = { menuOpen = false }
                )
            }

            // Above the menu so the choices are visible while they are changed.
            if (settingsOpen) {
                SettingsSheet(
                    settings = viewModel.settings,
                    onChange = { viewModel.updateSettings(it) },
                    onDismiss = { settingsOpen = false }
                )
            }
        }
    }
}

/** Compact header with the menu button; the drop-down replaces a bottom tab bar. */
@Composable
private fun TopBar(
    title: String,
    onMenuClick: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to the keypad",
                    tint = colors.primary
                )
            }
        }
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
    onOpenAi: () -> Unit,
    onOpenTool: (EverydayTool) -> Unit,
    statsOn: Boolean,
    onStatsChange: (Boolean) -> Unit,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onPrivacyOptions: (() -> Unit)?,
    onOpenSettings: () -> Unit,
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
                    text = "AI Calculator",
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

            AiHeroMenuRow(onOpenAi = onOpenAi)
            Spacer(modifier = Modifier.height(8.dp))

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

            // --- everyday calculators ---------------------------------------
            Text(
                text = "Everyday calculators",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp)
            )
            EverydayTool.entries.chunked(ToolsPerLine).forEach { line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    line.forEach { tool ->
                        ToolTile(
                            tool = tool,
                            onClick = { onOpenTool(tool) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(ToolsPerLine - line.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // --- settings ----------------------------------------------------
            TextButton(onClick = onOpenSettings) {
                Text(
                    text = "Settings — digits shown, digit grouping, key feedback",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            // --- appearance --------------------------------------------------
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = mode == themeMode,
                        onClick = { onThemeModeChange(mode) },
                        label = { Text(mode.label) },
                        colors = appChipColors()
                    )
                }
            }

            // --- privacy ------------------------------------------------------
            // Only present where Google requires a way back into the consent
            // form (EEA/UK/Switzerland); nothing to show anywhere else.
            if (onPrivacyOptions != null) {
                TextButton(onClick = onPrivacyOptions) {
                    Text(
                        text = "Privacy settings",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // --- anonymous usage counts (admin dashboard) --------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Anonymous usage stats",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface
                    )
                    Text(
                        text = "Counts of app opens, calculations and AI questions. " +
                            "No expressions, answers or files leave the phone.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
                Switch(checked = statsOn, onCheckedChange = onStatsChange)
            }
        }
    }
}

/** Compact tile for an everyday calculator: icon above a short label. */
@Composable
private fun ToolTile(
    tool: EverydayTool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        color = colors.surfaceContainer,
        contentColor = colors.secondary,
        shape = RoundedCornerShape(13.dp),
        shadowElevation = 2.dp,
        tonalElevation = 1.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = toolIcon(tool),
                contentDescription = null,
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = tool.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun toolIcon(tool: EverydayTool): ImageVector = when (tool) {
    EverydayTool.GST -> Icons.Filled.Receipt
    EverydayTool.TAX -> Icons.Filled.AccountBalance
    EverydayTool.EMI -> Icons.Filled.Home
    EverydayTool.BMI -> Icons.Filled.Favorite
    EverydayTool.PERCENT -> Icons.Filled.Percent
    EverydayTool.INTEREST -> Icons.AutoMirrored.Filled.TrendingUp
    EverydayTool.PROFIT -> Icons.Filled.Payments
    EverydayTool.ALGEBRA -> Icons.Filled.Functions
    EverydayTool.BASES -> Icons.Filled.Numbers
    EverydayTool.AGE -> Icons.Filled.Cake
    EverydayTool.DATE -> Icons.Filled.DateRange
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
