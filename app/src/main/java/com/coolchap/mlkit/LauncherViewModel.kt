package io.github.not-a-dev-singh

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.not-a-dev-singh.data.AppInfo
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
    val isEditMode: Boolean = false,
    val draggingApp: AppInfo? = null
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

        if (filteredApps.size == 1 && totalQuery.length >= 2) {
            val app = filteredApps[0]
            if (app.label.equals(totalQuery, ignoreCase = true) || totalQuery.length >= 3) {
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
        if (committedText.isNotEmpty()) {
            committedText = committedText.dropLast(1)
        } else if (_state.value.recognizedText.isNotEmpty()) {
            committedText = ""
        }
        
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

    fun setShowAllApps(show: Boolean) {
        _state.update { it.copy(showAllApps = show) }
    }

    fun setModelReady(ready: Boolean) {
        _state.update { it.copy(isModelReady = ready) }
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