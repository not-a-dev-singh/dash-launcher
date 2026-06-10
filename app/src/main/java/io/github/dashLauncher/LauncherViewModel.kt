package io.github.dashLauncher

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.dashLauncher.data.AppInfo
import io.github.dashLauncher.data.AppRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LauncherState(
    val recentApps: List<AppInfo> = emptyList(),
    val pinnedApps: List<AppInfo?> = emptyList(),
    val allApps: List<AppInfo> = emptyList(),
    val suggestions: List<AppInfo> = emptyList(),
    val recognizedText: String = "",
    val showAllApps: Boolean = false,
    val isModelReady: Boolean = false,
    val modelDownloadStatus: ModelDownloadStatus = ModelDownloadStatus(),
    val isEditMode: Boolean = false,
    val draggingApp: AppInfo? = null
)

data class ModelDownloadStatus(
    val isDownloading: Boolean = false,
    val isReady: Boolean = false,
    val message: String = "",
    val etaText: String? = null,
    val errorText: String? = null
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AppRepository(application)
    private val _state = MutableStateFlow(LauncherState())
    val state: StateFlow<LauncherState> = _state

    private var committedText: String = ""
    private var loadAppsJob: Job? = null

    init {
        loadApps()
    }

    private fun loadApps() {
        loadAppsJob?.cancel()
        loadAppsJob = viewModelScope.launch {
            applyAppSections(buildAppSections())
        }
    }

    private suspend fun buildAppSections(): AppSections {
        // TODO: move this snapshot-building logic into a dedicated helper or
        // use-case class if refresh logic grows further.
        // TODO: if app-state derivation expands, split persistence cleanup from
        // UI section building so each step stays easy to scan.
        val allApps = repo.getAllApps()
        val installedPackages = allApps.map { it.packageName }.toSet()
        val rawPinnedPackages = repo.getPinnedPackages()
        val pinnedPackages = sanitizePinnedPackages(rawPinnedPackages, installedPackages)
        if (pinnedPackages != rawPinnedPackages) {
            repo.savePinnedPackages(pinnedPackages)
        }

        val currentQuery = _state.value.recognizedText.trim()

        return AppSections(
            allApps = allApps,
            pinnedApps = buildPinnedApps(allApps, pinnedPackages),
            recentApps = buildRecentApps(allApps, pinnedPackages),
            suggestions = buildSuggestions(currentQuery, allApps)
        )
    }

    private fun applyAppSections(sections: AppSections) {
        _state.update {
            it.copy(
                allApps = sections.allApps,
                recentApps = sections.recentApps,
                pinnedApps = sections.pinnedApps,
                suggestions = sections.suggestions
            )
        }
    }

    private fun buildPinnedApps(
        allApps: List<AppInfo>,
        pinnedPackages: List<String>
    ): List<AppInfo?> {
        return pinnedPackages.map { pkg ->
            if (pkg.isEmpty()) null else allApps.find { it.packageName == pkg }
        }
    }

    private fun buildRecentApps(
        allApps: List<AppInfo>,
        pinnedPackages: List<String>
    ): List<AppInfo> {
        return allApps.filter { app -> !pinnedPackages.contains(app.packageName) }.take(20)
    }

    private fun buildSuggestions(query: String, allApps: List<AppInfo>): List<AppInfo> {
        return if (query.isNotEmpty()) filterApps(query, allApps) else emptyList()
    }

    private fun sanitizePinnedPackages(
        pinnedPackages: List<String>,
        installedPackages: Set<String>
    ): List<String> {
        return pinnedPackages.map { pkg ->
            if (pkg.isNotEmpty() && pkg in installedPackages) pkg else ""
        }
    }

    fun onRecognizedResults(results: List<String>) {
        if (results.isEmpty()) return

        // TODO: if recognition behavior grows, split state update and
        // auto-launch decision into separate helpers for clarity.
        val activeResult = results.first().trim()
        val totalQuery = (committedText + activeResult).trim()

        val filteredApps = filterApps(totalQuery)

        _state.update { 
            it.copy(
                recognizedText = totalQuery, 
                suggestions = filteredApps
            ) 
        }

        // only auto-launch on an exact label match; avoids firing mid-word when
        // the recognizer returns an imprecise partial result at 3+ characters
        if (filteredApps.size == 1) {
            val app = filteredApps[0]
            if (app.label.equals(totalQuery, ignoreCase = true)) {
                launchApp(app.packageName)
            }
        }
    }

    private fun filterApps(query: String, sourceApps: List<AppInfo> = _state.value.allApps): List<AppInfo> {
        if (query.isEmpty()) return emptyList()
        
        // TODO: move matching/ranking logic into a dedicated search helper if
        // we add more scoring rules, fuzzy match, or recency weighting.
        val filteredApps = mutableListOf<AppInfo>()
        val seenPackages = mutableSetOf<String>()

        val prefixMatches = sourceApps.filter {
            it.label.startsWith(query, ignoreCase = true) && !seenPackages.contains(it.packageName)
        }
        filteredApps.addAll(prefixMatches)
        seenPackages.addAll(prefixMatches.map { it.packageName })

        val containsMatches = sourceApps.filter {
            it.label.contains(query, ignoreCase = true) && !seenPackages.contains(it.packageName)
        }
        filteredApps.addAll(containsMatches)
        seenPackages.addAll(containsMatches.map { it.packageName })
        
        return filteredApps.take(15)
    }

    fun backspace() {
        // Always derive the new query from the full visible text (recognizedText).
        // The old impl used committedText as the base: when committedText was still
        // empty (active scribble not yet committed by the idle timer), the else branch
        // was a no-op and recognizedText was then overwritten with "" — wiping
        // everything in a single backspace swipe instead of removing one character.
        val current = _state.value.recognizedText
        if (current.isEmpty()) return
        committedText = current.dropLast(1)
        val suggestions = filterApps(committedText)
        _state.update {
            it.copy(
                recognizedText = committedText,
                suggestions = suggestions
            )
        }
    }

    fun commitActiveScribble() {
        committedText = _state.value.recognizedText
    }

    fun clearScribble() {
        committedText = ""
        _state.update { it.copy(recognizedText = "", suggestions = emptyList()) }
    }

    fun refreshApps() {
        // TODO: if more refresh triggers appear, route them through a single
        // mutation/reload helper to reduce repeated load calls.
        loadApps()
    }

    fun swapPinnedSlots(fromIndex: Int, toIndex: Int) {
        repo.swapPinnedSlots(fromIndex, toIndex)
        loadApps()
    }

    fun setShowAllApps(show: Boolean) {
        _state.update { it.copy(showAllApps = show) }
    }

    fun setModelReady(ready: Boolean) {
        _state.update { it.copy(isModelReady = ready) }
    }

    fun setModelDownloadStatus(status: ModelDownloadStatus) {
        _state.update {
            it.copy(
                isModelReady = status.isReady,
                modelDownloadStatus = status
            )
        }
    }

    fun setEditMode(enabled: Boolean) {
        _state.update { it.copy(isEditMode = enabled) }
    }

    fun setDraggingApp(app: AppInfo?) {
        _state.update { it.copy(draggingApp = app) }
    }

    fun pinApp(packageName: String, slotIndex: Int) {
        // TODO: consider a small "mutate then reload" helper if more actions
        // start needing the same repo-write + refresh pattern.
        repo.pinApp(packageName, slotIndex)
        loadApps()
    }

    fun unpinApp(slotIndex: Int) {
        repo.unpinApp(slotIndex)
        loadApps()
    }

    fun launchApp(packageName: String) {
        clearScribble()
        val intent = getApplication<Application>().packageManager
            .getLaunchIntentForPackage(packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        intent?.let { getApplication<Application>().startActivity(it) }
    }

    private data class AppSections(
        val allApps: List<AppInfo>,
        val pinnedApps: List<AppInfo?>,
        val recentApps: List<AppInfo>,
        val suggestions: List<AppInfo>
    )
}
