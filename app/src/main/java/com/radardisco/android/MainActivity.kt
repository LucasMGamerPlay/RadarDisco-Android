package com.radardisco.android

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radardisco.android.core.storage.SystemFileActions
import com.radardisco.android.model.ScanStatus
import com.radardisco.android.ui.components.AiAdvisorPanel
import com.radardisco.android.ui.components.AiSettingsDialog
import com.radardisco.android.ui.components.CollapsedScanSummaryBar
import com.radardisco.android.ui.components.DeleteConfirmDialog
import com.radardisco.android.ui.components.DriveSelectorSection
import com.radardisco.android.ui.components.FolderExplorerList
import com.radardisco.android.ui.components.InsightsPanel
import com.radardisco.android.ui.components.ItemActionSheet
import com.radardisco.android.ui.components.LiveStatusBar
import com.radardisco.android.ui.components.PermissionGateCard
import com.radardisco.android.ui.components.TopHeaderBar
import com.radardisco.android.ui.components.TreemapCanvas
import com.radardisco.android.ui.components.UpdateBanner
import com.radardisco.android.ui.theme.RadarColors
import com.radardisco.android.ui.theme.RadarDiscoTheme
import com.radardisco.android.ui.viewmodel.RadarDiscoUiState
import com.radardisco.android.ui.viewmodel.RadarDiscoViewModel
import com.radardisco.android.ui.viewmodel.RadarTab

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        setContent {
            RadarDiscoTheme {
                RadarDiscoRootScreen()
            }
        }
    }
}

@Composable
fun RadarDiscoRootScreen(
    viewModel: RadarDiscoViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var lastToastError by remember { mutableStateOf(false) }

    val safTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, flags)
            }
            viewModel.startSafScan(treeUri)
        }
    }

    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshEnvironment()
    }

    val requestStorageAccess: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            SystemFileActions.openAllFilesPermissionSettings(context)
        } else {
            legacyStoragePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshEnvironment()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.toastEvents.collect { toast ->
            lastToastError = toast.isError
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(toast.text)
        }
    }

    // Back handler navega para a pasta pai dentro do scanner antes de sair
    val canNavigateUpInScan = uiState.snapshot.breadcrumbs.size > 1 &&
        (uiState.activeTab == RadarTab.LIST || uiState.activeTab == RadarTab.TREEMAP)
    BackHandler(enabled = canNavigateUpInScan) {
        val crumbs = uiState.snapshot.breadcrumbs
        if (crumbs.size >= 2) {
            viewModel.focusFolder(crumbs[crumbs.size - 2].path)
        }
    }

    val hasScannedData = uiState.snapshot.status != ScanStatus.IDLE &&
        (uiState.snapshot.items.isNotEmpty() || uiState.snapshot.stats.scannedFiles > 0)
    val canCollapseHeader = uiState.snapshot.status != ScanStatus.IDLE && uiState.snapshot.status != ScanStatus.SCANNING
    val activeVolume = remember(uiState.volumes, uiState.snapshot.rootPath) {
        uiState.volumes.firstOrNull {
            it.mountPath.trimEnd('/') == uiState.snapshot.rootPath.trimEnd('/')
        }
    }
    val appsTotalBytes = remember(uiState.appStorageEntries) {
        uiState.appStorageEntries.sumOf { it.totalBytes }
    }

    Scaffold(
        containerColor = RadarColors.BgRoot,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (lastToastError) RadarColors.Rose950 else RadarColors.BgDialog,
                    contentColor = if (lastToastError) RadarColors.Rose200 else RadarColors.TextPrimary
                )
            }
        },
        bottomBar = {
            RadarBottomNavigation(
                activeTab = uiState.activeTab,
                onSelectTab = viewModel::setActiveTab
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            TopHeaderBar(
                updateState = uiState.updateState,
                onCheckUpdates = { viewModel.checkForUpdates(manual = true) },
                onOpenAiSettings = viewModel::openAiSettings
            )

            UpdateBanner(
                updateState = uiState.updateState,
                dismissed = uiState.updateBannerDismissed,
                onOpenDownload = viewModel::openUpdateLink,
                onDismiss = viewModel::dismissUpdateBanner
            )

            if (!uiState.hasAllFilesPermission) {
                PermissionGateCard(
                    onRequestAllFilesAccess = requestStorageAccess,
                    onPickSafFolder = { safTreeLauncher.launch(null) }
                )
            }

            AnimatedVisibility(visible = !uiState.headerCollapsed || !canCollapseHeader) {
                Column {
                    DriveSelectorSection(
                        volumes = uiState.volumes,
                        quickTargets = uiState.quickTargets,
                        activeRootPath = uiState.snapshot.rootPath,
                        scanStatus = uiState.snapshot.status,
                        hasScannedData = hasScannedData,
                        aiAnalyzing = uiState.aiAnalyzing,
                        loadingVolumes = uiState.loadingVolumes,
                        onSelectPath = viewModel::startPathScan,
                        onPickSafFolder = { safTreeLauncher.launch(null) },
                        onAnalyzeWithAi = viewModel::runAiAnalysis,
                        onOpenAiSettings = viewModel::openAiSettings,
                        onRefreshVolumes = viewModel::refreshEnvironment,
                        onCollapse = if (canCollapseHeader) {
                            { viewModel.setHeaderCollapsed(true) }
                        } else {
                            null
                        }
                    )

                    LiveStatusBar(
                        snapshot = uiState.snapshot,
                        activeVolume = activeVolume,
                        hasUsageStatsPermission = uiState.hasUsageStatsPermission,
                        appsTotalBytes = appsTotalBytes,
                        onPause = viewModel::pauseScan,
                        onResume = viewModel::resumeScan,
                        onCancel = viewModel::cancelScan,
                        onRescan = viewModel::rescanCurrent,
                        onCollapse = if (canCollapseHeader) {
                            { viewModel.setHeaderCollapsed(true) }
                        } else {
                            null
                        },
                        onOpenAppsTab = viewModel::openAppsInsights
                    )
                }
            }

            AnimatedVisibility(visible = uiState.headerCollapsed && canCollapseHeader) {
                CollapsedScanSummaryBar(
                    snapshot = uiState.snapshot,
                    onExpand = { viewModel.setHeaderCollapsed(false) },
                    onRescan = viewModel::rescanCurrent
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                RadarMainContent(
                    uiState = uiState,
                    hasScannedData = hasScannedData,
                    viewModel = viewModel,
                    onRequestUsageStatsPermission = {
                        SystemFileActions.openUsageAccessSettings(context)
                    }
                )
            }
        }
    }

    ItemActionSheet(
        target = uiState.actionSheetTarget,
        onDismiss = viewModel::closeActionSheet,
        onFocusFolder = viewModel::focusFolder,
        onOpenInFiles = viewModel::openInSystemFiles,
        onSearchWeb = viewModel::searchOnWeb,
        onCopyPath = viewModel::copyPath,
        onRequestDelete = viewModel::requestDelete
    )

    DeleteConfirmDialog(
        target = uiState.deleteTarget,
        busy = uiState.deleteBusy,
        onDismiss = viewModel::dismissDeleteDialog,
        onConfirm = viewModel::confirmDelete
    )

    if (uiState.aiSettingsOpen) {
        AiSettingsDialog(
            initialSettings = uiState.aiSettings,
            hasScannedData = hasScannedData,
            onDismiss = viewModel::closeAiSettings,
            onSave = viewModel::saveAiSettings,
            onFetchModels = viewModel::fetchCloudModels
        )
    }
}

@Composable
private fun RadarMainContent(
    uiState: RadarDiscoUiState,
    hasScannedData: Boolean,
    viewModel: RadarDiscoViewModel,
    onRequestUsageStatsPermission: () -> Unit
) {
    when (uiState.activeTab) {
        RadarTab.INSIGHTS -> {
            InsightsPanel(
                snapshot = uiState.snapshot,
                appStorageEntries = uiState.appStorageEntries,
                hasUsageStatsPermission = uiState.hasUsageStatsPermission,
                loadingApps = uiState.loadingApps,
                selectedSubTab = uiState.insightsSubTab,
                onSelectSubTab = viewModel::setInsightsSubTab,
                onOpenActions = viewModel::openActionSheet,
                onRequestUsageStatsPermission = onRequestUsageStatsPermission,
                onOpenAppSettings = viewModel::openAppStorageSettings
            )
        }

        RadarTab.AI -> {
            AiAdvisorPanel(
                aiSettings = uiState.aiSettings,
                report = uiState.aiReport,
                analyzing = uiState.aiAnalyzing,
                error = uiState.aiError,
                hasScannedData = hasScannedData,
                onRunAnalysis = viewModel::runAiAnalysis,
                onOpenSettings = viewModel::openAiSettings,
                onFocusFolder = viewModel::focusFolder,
                onSearchWeb = viewModel::searchOnWeb,
                onRequestDelete = viewModel::requestDelete
            )
        }

        else -> {
            if (uiState.snapshot.status == ScanStatus.IDLE) {
                EmptyScanHero()
            } else {
                when (uiState.activeTab) {
                    RadarTab.LIST -> {
                        FolderExplorerList(
                            snapshot = uiState.snapshot,
                            selectedPath = uiState.selectedPath,
                            onSelectPath = viewModel::selectPath,
                            onFocusFolder = viewModel::focusFolder,
                            onOpenActions = viewModel::openActionSheet
                        )
                    }

                    RadarTab.TREEMAP -> {
                        TreemapCanvas(
                            snapshot = uiState.snapshot,
                            selectedPath = uiState.selectedPath,
                            onSelectPath = viewModel::selectPath,
                            onFocusFolder = viewModel::focusFolder,
                            onOpenActions = viewModel::openActionSheet
                        )
                    }

                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun EmptyScanHero() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_radardisco_logo),
            contentDescription = "RadarDisco",
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, RadarColors.BorderMuted, RoundedCornerShape(16.dp))
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Pronto para encontrar o que consome seu espaço",
            color = RadarColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Selecione um volume de armazenamento, toque em um atalho (Downloads, DCIM, WhatsApp, Android/media) ou escolha qualquer pasta pelo seletor do Android acima.",
            color = RadarColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(RadarColors.BgSurfaceAlt)
                .border(1.dp, RadarColors.Fuchsia500.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = RadarColors.Fuchsia400,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Consultor IA na nuvem via API (Gemini, OpenAI, OpenRouter, Groq)",
                color = RadarColors.Fuchsia200,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RadarBottomNavigation(
    activeTab: RadarTab,
    onSelectTab: (RadarTab) -> Unit
) {
    NavigationBar(
        containerColor = RadarColors.BgHeader,
        contentColor = RadarColors.TextSecondary,
        modifier = Modifier.navigationBarsPadding()
    ) {
        RadarTab.entries.forEach { tab ->
            val icon = when (tab) {
                RadarTab.LIST -> Icons.AutoMirrored.Rounded.FormatListBulleted
                RadarTab.TREEMAP -> Icons.Rounded.GridView
                RadarTab.INSIGHTS -> Icons.Rounded.Insights
                RadarTab.AI -> Icons.Rounded.AutoAwesome
            }
            val selectedColor = if (tab == RadarTab.AI) RadarColors.Fuchsia400 else RadarColors.Sky400
            NavigationBarItem(
                selected = activeTab == tab,
                onClick = { onSelectTab(tab) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(20.dp)
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = selectedColor,
                    selectedTextColor = selectedColor,
                    unselectedIconColor = RadarColors.TextMuted,
                    unselectedTextColor = RadarColors.TextMuted,
                    indicatorColor = selectedColor.copy(alpha = 0.14f)
                )
            )
        }
    }
}
