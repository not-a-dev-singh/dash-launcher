package io.github.dashLauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.dashLauncher.recognition.InkRecognitionManager
import io.github.dashLauncher.ui.LauncherRoot
import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private val inkManager = InkRecognitionManager()

    // -------------------------------------------------------------------------
    // APP INSTALL / UNINSTALL LISTENER
    // Listens for three system broadcasts that mean the installed-app list has
    // changed and our in-memory state is stale:
    //   PACKAGE_ADDED   — a new app was installed
    //   PACKAGE_REMOVED — an app was uninstalled
    //   PACKAGE_REPLACED — an app was updated (icon or label may have changed)
    // All three simply call viewModel.refreshApps(), which re-runs the full
    // loadApps() pipeline so allApps, recentApps, and pinnedApps stay current.
    //
    // Lifecycle: registered in onStart / unregistered in onStop so it is only
    // active while the launcher is in the foreground. Using onStart/onStop (not
    // onCreate/onDestroy) avoids receiving stale broadcasts after the Activity
    // has been backgrounded.
    // -------------------------------------------------------------------------
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Guard: ignore broadcasts unrelated to package changes
            val action = intent.action ?: return
            if (action in listOf(
                    Intent.ACTION_PACKAGE_ADDED,
                    Intent.ACTION_PACKAGE_REMOVED,
                    Intent.ACTION_PACKAGE_REPLACED
                )
            ) {
                viewModel.refreshApps()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inkManager.onModelReady = { viewModel.setModelReady(true) }
        inkManager.onResults = { results -> viewModel.onRecognizedResults(results) }
        inkManager.onDownloadStatusChanged = { status -> viewModel.setModelDownloadStatus(status) }
        inkManager.initialize("en-US")

        if (!hasUsageAccess()) {
            requestUsageAccess()
        }
        
        setContent {
            val state by viewModel.state.collectAsState()
            LauncherRoot(
                state = state,
                inkManager = inkManager,
                onSwipeUp = { viewModel.setShowAllApps(true) },
                onBackspace = { viewModel.backspace() },
                onDismissAllApps = { viewModel.setShowAllApps(false) },
                onAppClick = { viewModel.launchApp(it) },
                onClearScribble = { viewModel.clearScribble() },
                onPinApp = { pkg, index -> viewModel.pinApp(pkg, index) },
                onUnpinApp = { index -> viewModel.unpinApp(index) },
                onCommitActiveScribble = { viewModel.commitActiveScribble() },
                onSetEditMode = { viewModel.setEditMode(it) },
                onSwapPinnedSlots = { from, to -> viewModel.swapPinnedSlots(from, to) }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        // Register for package-change broadcasts. DATA_SCHEME is required because
        // PACKAGE_ADDED/REMOVED carry a data URI with the affected package name.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageChangeReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(packageChangeReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        inkManager.close()
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsageAccess() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }
}
