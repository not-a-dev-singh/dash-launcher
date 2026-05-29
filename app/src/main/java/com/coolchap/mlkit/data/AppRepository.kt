package com.yourapp.launcher.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import io.github.not-a-dev-singh.data.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

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
        val raw = prefs.getString("pinned_apps_v2", "") ?: ""
        if (raw.isEmpty()) return List(10) { "" } // Default 10 slots
        return raw.split(",")
    }

    fun savePinnedPackages(packages: List<String>) {
        prefs.edit().putString("pinned_apps_v2", packages.joinToString(",")).apply()
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
}