package io.github.dashLauncher.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    companion object {
        private const val PINNED_SLOT_COUNT = 4
        private const val PINNED_PACKAGES_KEY = "pinned_apps_v2"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    suspend fun getAllApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val usageStats = getUsageStats()
        val pinned = getPinnedPackages()
        
        pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { ri -> 
                val pkg = ri.activityInfo.packageName
                AppInfo(
                    packageName = pkg, 
                    label = ri.loadLabel(pm).toString(), 
                    icon = ri.loadIcon(pm), 
                    lastUsed = usageStats[pkg] ?: 0L,
                    isPinned = pinned.contains(pkg)
                ) 
            }
            .sortedByDescending { it.lastUsed }
    }

    private fun getUsageStats(): Map<String, Long> {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - 1000L * 60 * 60 * 24 * 30 // last 30 days
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
                ?.associate { it.packageName to it.lastTimeUsed }
                ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun getPinnedPackages(): List<String> {
        val raw = prefs.getString(PINNED_PACKAGES_KEY, "") ?: ""
        val normalized = normalizePinnedPackages(raw)
        if (raw != normalized.joinToString(",")) {
            savePinnedPackages(normalized)
        }
        return normalized
    }

    fun savePinnedPackages(packages: List<String>) {
        val normalized = normalizePinnedPackages(packages)
        prefs.edit().putString(PINNED_PACKAGES_KEY, normalized.joinToString(",")).apply()
    }
    
    fun pinApp(packageName: String, index: Int) {
        val current = getPinnedPackages().toMutableList()
        if (index in current.indices) {
            // Remove from old position if exists
            val oldIndex = current.indexOf(packageName)
            if (oldIndex != -1) current[oldIndex] = ""
            
            current[index] = packageName
            savePinnedPackages(current)
        }
    }
    
    fun unpinApp(index: Int) {
        val current = getPinnedPackages().toMutableList()
        if (index in current.indices) {
            current[index] = ""
            savePinnedPackages(current)
        }
    }

    // -------------------------------------------------------------------------
    // DRAG-TO-REORDER: swap two pinned slots by index.
    // Swap semantics: fromIndex and toIndex exchange their package names.
    // Either slot may be empty (""). If from == to, the call is a no-op.
    // Called by LauncherViewModel.swapPinnedSlots() after a drag-drop gesture.
    // -------------------------------------------------------------------------
    fun swapPinnedSlots(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val current = getPinnedPackages().toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val temp = current[fromIndex]
        current[fromIndex] = current[toIndex]
        current[toIndex] = temp
        savePinnedPackages(current)
    }

    private fun normalizePinnedPackages(raw: String): List<String> {
        if (raw.isEmpty()) return emptyPinnedSlots()
        return normalizePinnedPackages(raw.split(",", limit = PINNED_SLOT_COUNT + 1))
    }

    private fun normalizePinnedPackages(packages: List<String>): List<String> {
        return packages
            .take(PINNED_SLOT_COUNT)
            .let { slots ->
                slots + List((PINNED_SLOT_COUNT - slots.size).coerceAtLeast(0)) { "" }
            }
    }

    private fun emptyPinnedSlots(): List<String> = List(PINNED_SLOT_COUNT) { "" }
}
