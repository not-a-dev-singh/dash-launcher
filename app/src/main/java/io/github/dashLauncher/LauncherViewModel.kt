package io.github.dashLauncher

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.dashLauncher.data.AppInfo
import io.github.dashLauncher.data.AppRepository
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

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            val all = repo.getAllApps()
            val pinnedPkgs = repo.getPinnedPackages()
            val pinned = pinnedPkgs.map { pkg -> 
                if (pkg.isEmpty()) null else all.find { it.packageName == pkg } 
            }
            val recent = all.filter { app -> !pinnedPkgs.contains(app.packageName) }.take(20)
            
            _state.update { it.copy(allApps = all, recentApps = recent, pinnedApps = pinned) }
        }
    }

    fun onRecognizedResults(results: List<String>) {
        if (results.isEmpty()) return

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

    private fun filterApps(query: String): List<AppInfo> {
        if (query.isEmpty()) return emptyList()
        
        val allApps = _state.value.allApps
        val filteredApps = mutableListOf<AppInfo>()
        val seenPackages = mutableSetOf<String>()

        val prefixMatches = allApps.filter {
            it.label.startsWith(query, ignoreCase = true) && !seenPackages.contains(it.packageName)
        }
        filteredApps.addAll(prefixMatches)
        seenPackages.addAll(prefixMatches.map { it.packageName })

        val containsMatches = allApps.filter {
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
}
