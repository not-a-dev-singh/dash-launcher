package io.github.not-a-dev-singh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.not-a-dev-singh.recognition.InkRecognitionManager
import io.github.not-a-dev-singh.ui.LauncherRoot
import android.app.AppOpsManager
import android.content.Intent
import android.provider.Settings

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private val inkManager = InkRecognitionManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inkManager.onModelReady = { viewModel.setModelReady(true) }
        inkManager.onResults = { results -> viewModel.onRecognizedResults(results) }
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
                onSetEditMode = { viewModel.setEditMode(it) }
            )
        }
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
