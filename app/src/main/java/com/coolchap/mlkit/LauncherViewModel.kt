package io.github.dashLauncher

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.dashLauncher.data.AppInfo
import com.yourapp.launcher.data.AppRepository
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
    val isEditMode: Boolean = false
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

    // -------------------------------------------------------------------------
    // APP INSTALL / UNINSTALL LISTENER — ViewModel entry point
    // Called by the BroadcastReceiver in MainActivity whenever a package is
    // added, removed, or replaced. Re-runs the full loadApps() pipeline so that
    // allApps, recentApps, and pinnedApps are immediately consistent with the
    // device state. Pinned slots for uninstalled apps become null automatically
    // because loadApps() re-resolves each package name against the fresh list.
    // -------------------------------------------------------------------------
    fun refreshApps() {
        loadApps()
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

    // -------------------------------------------------------------------------
    // BACKSPACE GESTURE — state layer
    // Responsibility: remove the last character from the visible query and
    // re-run filtering. Called by DrawingOverlay AFTER ink/timer cleanup.
    //
    // Key invariant: always derive the new query from recognizedText (the full
    // visible string), NOT from committedText alone.
    // Reason: committedText is only updated after the 1-second idle timer fires.
    // While the user is still scribbling, committedText may be "" even though
    // recognizedText shows "map". Using committedText as the base would make the
    // first backspace wipe the entire string in one gesture.
    //
    // WARNING: do NOT change the source of truth back to committedText.
    // -------------------------------------------------------------------------
    fun backspace() {
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
    // -------------------------------------------------------------------------

    fun commitActiveScribble() {
        committedText = _state.value.recognizedText
    }

    fun clearScribble() {
        committedText = ""
        _state.update { it.copy(recognizedText = "", suggestions = emptyList()) }
    }

    fun setShowAllApps(show: Boolean) {
        _state.update { it.copy(showAllApps = show) }
    }

    fun setModelReady(ready: Boolean) {
        _state.update { it.copy(isModelReady = ready) }
    }

    fun setEditMode(enabled: Boolean) {
        _state.update { it.copy(isEditMode = enabled) }
    }

    fun pinApp(packageName: String, slotIndex: Int) {
        repo.pinApp(packageName, slotIndex)
        loadApps()
    }

    fun unpinApp(slotIndex: Int) {
        repo.unpinApp(slotIndex)
        loadApps()
    }

    // -------------------------------------------------------------------------
    // DRAG-TO-REORDER: swap two pinned slots.
    // fromIndex: the slot the user started dragging from.
    // toIndex:   the slot the user released over.
    // Delegates to AppRepository.swapPinnedSlots() for persistence, then calls
    // loadApps() to rebuild pinnedApps state from SharedPreferences so the UI
    // reflects the new order immediately.
    // No-op if either index is out of bounds — that guard lives in the repo.
    // -------------------------------------------------------------------------
    fun swapPinnedSlots(fromIndex: Int, toIndex: Int) {
        repo.swapPinnedSlots(fromIndex, toIndex)
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