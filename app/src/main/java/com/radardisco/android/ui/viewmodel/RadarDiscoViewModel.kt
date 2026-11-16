package com.radardisco.android.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.radardisco.android.BuildConfig
import com.radardisco.android.core.ai.AiSettingsRepository
import com.radardisco.android.core.ai.CloudAiService
import com.radardisco.android.core.scanner.DiskScanEngine
import com.radardisco.android.core.scanner.FolderClassifier
import com.radardisco.android.core.scanner.JavaIoScanFileSystem
import com.radardisco.android.core.scanner.SafScanFileSystem
import com.radardisco.android.core.storage.AppStorageStatsService
import com.radardisco.android.core.storage.FileDeleteService
import com.radardisco.android.core.storage.StorageVolumeService
import com.radardisco.android.core.storage.SystemFileActions
import com.radardisco.android.core.update.GitHubUpdateChecker
import com.radardisco.android.model.AiAnalysisReport
import com.radardisco.android.model.AiApiProvider
import com.radardisco.android.model.AiSettings
import com.radardisco.android.model.AppStorageEntry
import com.radardisco.android.model.ItemType
import com.radardisco.android.model.QuickScanTarget
import com.radardisco.android.model.ScanSnapshot
import com.radardisco.android.model.ScanSourceType
import com.radardisco.android.model.ScanStatus
import com.radardisco.android.model.StorageVolumeInfo
import com.radardisco.android.model.UpdateState
import com.radardisco.android.model.UpdateStatus
import com.radardisco.android.ui.theme.RadarFormatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RadarTab(val label: String) {
    LIST("Lista"),
    TREEMAP("Mapa"),
    INSIGHTS("Top & Apps"),
    AI("Consultor IA")
}

data class ItemActionTarget(
    val name: String,
    val path: String,
    val size: Long,
    val type: ItemType
)

data class ToastMessage(
    val isError: Boolean,
    val text: String
)

data class RadarDiscoUiState(
    val hasAllFilesPermission: Boolean = false,
    val hasUsageStatsPermission: Boolean = false,
    val volumes: List<StorageVolumeInfo> = emptyList(),
    val quickTargets: List<QuickScanTarget> = emptyList(),
    val loadingVolumes: Boolean = false,
    val appStorageEntries: List<AppStorageEntry> = emptyList(),
    val loadingApps: Boolean = false,
    val snapshot: ScanSnapshot = ScanSnapshot(),
    val activeTab: RadarTab = RadarTab.LIST,
    val insightsSubTab: Int = 0,
    val headerCollapsed: Boolean = false,
    val selectedPath: String? = null,
    val aiSettings: AiSettings = AiSettings(),
    val aiSettingsOpen: Boolean = false,
    val aiReport: AiAnalysisReport? = null,
    val aiAnalyzing: Boolean = false,
    val aiError: String? = null,
    val deleteTarget: ItemActionTarget? = null,
    val deleteBusy: Boolean = false,
    val actionSheetTarget: ItemActionTarget? = null,
    val updateState: UpdateState = UpdateState(currentVersion = BuildConfig.VERSION_NAME),
    val updateBannerDismissed: Boolean = false
)

class RadarDiscoViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val aiRepository = AiSettingsRepository(appContext)
    private val cloudAiService = CloudAiService()
    private val updateChecker = GitHubUpdateChecker()

    private val _uiState = MutableStateFlow(RadarDiscoUiState())
    val uiState: StateFlow<RadarDiscoUiState> = _uiState.asStateFlow()

    private val _toastEvents = MutableSharedFlow<ToastMessage>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<ToastMessage> = _toastEvents.asSharedFlow()

    private var activeEngine: DiskScanEngine? = null
    private var activeScanJob: Job? = null

    init {
        refreshEnvironment()
        viewModelScope.launch {
            val savedAi = runCatching { aiRepository.load() }.getOrDefault(AiSettings())
            _uiState.update { it.copy(aiSettings = savedAi) }
        }
        viewModelScope.launch {
            val update = updateChecker.checkLatestRelease(BuildConfig.VERSION_NAME, manual = false)
            _uiState.update { it.copy(updateState = update) }
        }
    }

    fun refreshEnvironment() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingVolumes = true, loadingApps = true) }
            val hasFiles = StorageVolumeService.hasAllFilesPermission(appContext)
            val hasUsage = AppStorageStatsService.hasUsageStatsPermission(appContext)
            val volumes = withContext(Dispatchers.IO) {
                StorageVolumeService.listStorageVolumes(appContext)
            }
            val primaryMount = volumes.firstOrNull { !it.isRemovable }?.mountPath ?: volumes.firstOrNull()?.mountPath
            val quick = withContext(Dispatchers.IO) {
                StorageVolumeService.listQuickTargets(primaryMount)
            }
            val apps = if (hasUsage) {
                withContext(Dispatchers.IO) {
                    AppStorageStatsService.queryInstalledAppStorage(appContext)
                }
            } else {
                emptyList()
            }

            _uiState.update {
                it.copy(
                    hasAllFilesPermission = hasFiles,
                    hasUsageStatsPermission = hasUsage,
                    volumes = volumes,
                    quickTargets = quick,
                    loadingVolumes = false,
                    appStorageEntries = apps,
                    loadingApps = false
                )
            }
        }
    }

    fun setActiveTab(tab: RadarTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setInsightsSubTab(subTab: Int) {
        _uiState.update { it.copy(insightsSubTab = subTab.coerceIn(0, 2)) }
    }

    fun openAppsInsights() {
        _uiState.update { it.copy(activeTab = RadarTab.INSIGHTS, insightsSubTab = 2) }
    }

    fun toggleHeaderCollapsed() {
        _uiState.update { it.copy(headerCollapsed = !it.headerCollapsed) }
    }

    fun setHeaderCollapsed(collapsed: Boolean) {
        _uiState.update { it.copy(headerCollapsed = collapsed) }
    }

    fun selectPath(path: String?) {
        _uiState.update { it.copy(selectedPath = path) }
    }

    fun startPathScan(rootPath: String) {
        if (!StorageVolumeService.hasAllFilesPermission(appContext)) {
            emitToast(true, "Conceda Acesso a Todos os Arquivos ou use Escolher Pasta (SAF).")
            return
        }
        val engine = DiskScanEngine(
            rawRootPath = rootPath,
            fileSystem = JavaIoScanFileSystem(),
            concurrency = 10
        )
        launchScan(engine)
    }

    fun startSafScan(treeUri: Uri) {
        val rootDocUri = SafScanFileSystem.treeRootDocumentUriString(treeUri)
        val safFs = SafScanFileSystem(appContext, treeUri)
        val engine = DiskScanEngine(
            rawRootPath = rootDocUri,
            fileSystem = safFs,
            concurrency = 6
        )
        launchScan(engine)
    }

    private fun launchScan(engine: DiskScanEngine) {
        activeEngine?.cancel()
        activeScanJob?.cancel()
        activeEngine = engine

        _uiState.update { state ->
            state.copy(
                selectedPath = null,
                headerCollapsed = false,
                aiReport = null,
                aiError = null,
                activeTab = if (state.activeTab == RadarTab.AI) RadarTab.LIST else state.activeTab,
                snapshot = engine.getSnapshot()
            )
        }

        activeScanJob = viewModelScope.launch {
            try {
                engine.run { snap ->
                    _uiState.update { it.copy(snapshot = snap) }
                }
                _uiState.update { it.copy(snapshot = engine.getSnapshot()) }
            } catch (t: Throwable) {
                if (t !is kotlinx.coroutines.CancellationException) {
                    emitToast(true, t.message ?: "Erro ao escanear o armazenamento.")
                }
            }
        }
    }

    fun pauseScan() {
        val engine = activeEngine ?: return
        engine.pause()
        _uiState.update { it.copy(snapshot = engine.getSnapshot()) }
    }

    fun resumeScan() {
        val engine = activeEngine ?: return
        engine.resume()
        _uiState.update { it.copy(snapshot = engine.getSnapshot()) }
    }

    fun cancelScan() {
        val engine = activeEngine ?: return
        engine.cancel()
        _uiState.update { it.copy(snapshot = engine.getSnapshot()) }
    }

    fun rescanCurrent() {
        val snap = _uiState.value.snapshot
        if (snap.rootPath.isBlank()) return
        if (snap.sourceType == ScanSourceType.SAF_TREE) {
            val treeUri = runCatching { Uri.parse(snap.rootPath) }.getOrNull()
            // Se o rootPath for document URI da árvore, reescaneia usando o treeUri original ou pede nova seleção
            if (treeUri != null) {
                emitToast(false, "Selecione a pasta novamente para reiniciar o escaneamento SAF.")
            }
        } else {
            startPathScan(snap.rootPath)
        }
    }

    fun focusFolder(targetPath: String) {
        val engine = activeEngine ?: return
        engine.setFocusPath(targetPath)
        _uiState.update { state ->
            state.copy(
                selectedPath = null,
                snapshot = engine.getSnapshot(),
                activeTab = if (state.activeTab == RadarTab.INSIGHTS || state.activeTab == RadarTab.AI) {
                    RadarTab.LIST
                } else {
                    state.activeTab
                }
            )
        }
    }

    fun openActionSheet(item: ItemActionTarget) {
        _uiState.update { it.copy(actionSheetTarget = item) }
    }

    fun closeActionSheet() {
        _uiState.update { it.copy(actionSheetTarget = null) }
    }

    fun copyPath(path: String) {
        SystemFileActions.copyToClipboard(appContext, "Caminho RadarDisco", path)
        emitToast(false, "Caminho copiado para a área de transferência!")
    }

    fun searchOnWeb(name: String, path: String, type: ItemType) {
        val url = FolderClassifier.buildSmartWebSearchUrl(name, path, type)
        if (!SystemFileActions.openUrlInBrowser(appContext, url)) {
            emitToast(true, "Nenhum navegador disponível para abrir a pesquisa.")
        }
    }

    fun openInSystemFiles(path: String, type: ItemType) {
        if (!SystemFileActions.openInSystemFiles(appContext, path, type)) {
            emitToast(true, "Não foi possível abrir este item no gerenciador de arquivos do sistema.")
        }
    }

    fun openAppStorageSettings(packageName: String) {
        SystemFileActions.openAppSettings(appContext, packageName)
    }

    fun requestDelete(target: ItemActionTarget) {
        _uiState.update { it.copy(deleteTarget = target, actionSheetTarget = null) }
    }

    fun dismissDeleteDialog() {
        if (_uiState.value.deleteBusy) return
        _uiState.update { it.copy(deleteTarget = null) }
    }

    fun confirmDelete() {
        val target = _uiState.value.deleteTarget ?: return
        if (_uiState.value.deleteBusy) return

        viewModelScope.launch {
            _uiState.update { it.copy(deleteBusy = true) }
            val currentSnap = _uiState.value.snapshot
            val volumeRoots = _uiState.value.volumes.map { it.mountPath }.toSet()

            val result = withContext(Dispatchers.IO) {
                if (currentSnap.sourceType == ScanSourceType.SAF_TREE || target.path.startsWith("content://")) {
                    FileDeleteService.executeSafDelete(appContext, target.path, currentSnap.rootPath)
                } else {
                    FileDeleteService.executeDirectDelete(
                        rawPath = target.path,
                        volumeRoots = volumeRoots,
                        protectedRoot = currentSnap.rootPath
                    )
                }
            }

            if (result.ok) {
                activeEngine?.removeNodeByPath(target.path)
                val nextSnap = activeEngine?.getSnapshot() ?: currentSnap

                _uiState.update { state ->
                    val updatedReport = state.aiReport?.let { rep ->
                        rep.copy(
                            recommendations = rep.recommendations.filterNot {
                                it.path.equals(target.path, ignoreCase = true)
                            }
                        )
                    }
                    state.copy(
                        snapshot = nextSnap,
                        aiReport = updatedReport,
                        deleteTarget = null,
                        deleteBusy = false
                    )
                }

                val msg = result.message ?: "\"${target.name}\" excluído (${RadarFormatters.formatBytes(target.size)} liberados)!"
                emitToast(false, msg)
                refreshEnvironment()
            } else {
                _uiState.update { it.copy(deleteBusy = false) }
                emitToast(true, result.error ?: "Falha ao excluir o item selecionado.")
            }
        }
    }

    fun openAiSettings() {
        _uiState.update { it.copy(aiSettingsOpen = true) }
    }

    fun closeAiSettings() {
        _uiState.update { it.copy(aiSettingsOpen = false) }
    }

    fun saveAiSettings(next: AiSettings, runAfterSave: Boolean) {
        viewModelScope.launch {
            val savedResult = withContext(Dispatchers.IO) { aiRepository.save(next) }
            savedResult.onSuccess { saved ->
                _uiState.update { it.copy(aiSettings = saved, aiSettingsOpen = false) }
                emitToast(false, "Configurações da API de IA salvas!")
                val snap = _uiState.value.snapshot
                if (runAfterSave && snap.stats.scannedFiles > 0 && snap.status != ScanStatus.SCANNING) {
                    runAiAnalysis()
                }
            }.onFailure { err ->
                emitToast(true, err.message ?: "Erro ao salvar configurações da API.")
            }
        }
    }

    suspend fun fetchCloudModels(
        provider: AiApiProvider,
        baseUrl: String,
        apiKey: String
    ): Result<List<String>> {
        return cloudAiService.fetchCloudModels(provider, baseUrl, apiKey)
    }

    fun runAiAnalysis() {
        val state = _uiState.value
        val snap = state.snapshot
        if (snap.status == ScanStatus.SCANNING || (snap.stats.scannedFiles == 0 && snap.items.isEmpty())) {
            emitToast(true, "Conclua o escaneamento antes de consultar a IA.")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activeTab = RadarTab.AI,
                    aiAnalyzing = true,
                    aiError = null
                )
            }

            val response = cloudAiService.analyzeSnapshot(
                snapshot = snap,
                settings = _uiState.value.aiSettings,
                appStats = _uiState.value.appStorageEntries
            )

            if (response.ok && response.report != null) {
                _uiState.update {
                    it.copy(
                        aiAnalyzing = false,
                        aiReport = response.report,
                        aiError = null
                    )
                }
                emitToast(false, "Análise da IA concluída!")
            } else {
                _uiState.update {
                    it.copy(
                        aiAnalyzing = false,
                        aiError = response.error ?: "Não foi possível analisar com a API de IA.",
                        aiSettingsOpen = response.needsSetup
                    )
                }
            }
        }
    }

    fun checkForUpdates(manual: Boolean = true) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    updateBannerDismissed = false,
                    updateState = it.updateState.copy(status = UpdateStatus.CHECKING, manualCheck = manual)
                )
            }
            val result = updateChecker.checkLatestRelease(BuildConfig.VERSION_NAME, manual = manual)
            _uiState.update { it.copy(updateState = result) }
            if (manual) {
                when (result.status) {
                    UpdateStatus.NOT_AVAILABLE ->
                        emitToast(false, "Você já está na versão mais recente (v${result.currentVersion})!")
                    UpdateStatus.ERROR ->
                        emitToast(true, result.error ?: "Erro ao consultar atualizações.")
                    else -> Unit
                }
            }
        }
    }

    fun dismissUpdateBanner() {
        _uiState.update { it.copy(updateBannerDismissed = true) }
    }

    fun openUpdateLink() {
        val u = _uiState.value.updateState
        val url = u.apkDownloadUrl ?: u.releaseUrl ?: return
        SystemFileActions.openUrlInBrowser(appContext, url)
    }

    private fun emitToast(isError: Boolean, text: String) {
        _toastEvents.tryEmit(ToastMessage(isError = isError, text = text))
    }

    override fun onCleared() {
        activeEngine?.cancel()
        super.onCleared()
    }
}
