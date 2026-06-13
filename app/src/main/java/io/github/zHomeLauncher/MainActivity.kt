package io.github.zHomeLauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import io.github.zHomeLauncher.recognition.InkRecognitionManager
import io.github.zHomeLauncher.ui.LauncherRoot
import io.github.zHomeLauncher.ui.FavoriteAppsSetupScreen
import io.github.zHomeLauncher.ui.theme.DefaultTheme
import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private val inkManager = InkRecognitionManager()
    private val prefs by lazy { getSharedPreferences("launcher_prefs", MODE_PRIVATE) }

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

        setContent {
            DefaultTheme(darkTheme = true, dynamicColor = false) {
                val state by viewModel.state.collectAsState()
                var showUsageIntro by remember {
                    mutableStateOf(
                        !hasUsageAccess() && !hasSeenUsageIntro()
                    )
                }
                var showFavoriteAppsSetup by remember {
                    mutableStateOf(
                        !showUsageIntro && !hasSeenFavoriteAppsIntro()
                    )
                }

                LaunchedEffect(showUsageIntro) {
                    if (!showUsageIntro) {
                        showFavoriteAppsSetup = !hasSeenFavoriteAppsIntro()
                    }
                }

                if (showUsageIntro) {
                    BackHandler(enabled = true) {
                        // Consume back press to prevent native back gesture flicker
                    }
                    UsageAccessIntroScreen(
                        onContinue = {
                            markUsageIntroSeen()
                            requestUsageAccess()
                            showUsageIntro = false
                        },
                        onSkip = {
                            markUsageIntroSeen()
                            showUsageIntro = false
                        }
                    )
                } else if (showFavoriteAppsSetup) {
                    BackHandler(enabled = true) {
                        // Consume back press to prevent native back gesture flicker
                    }
                    FavoriteAppsSetupScreen(
                        allApps = state.allApps,
                        onDone = { selected ->
                            viewModel.pinApps(selected)
                            markFavoriteAppsIntroSeen()
                            showFavoriteAppsSetup = false
                        }
                    )
                } else {
                    BackHandler(enabled = true) {
                        if (state.showAllApps) {
                            viewModel.setShowAllApps(false)
                        } else {
                            // Consume back press on main home screen to prevent native back gesture transition/flicker
                        }
                    }
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
                        onSwapPinnedSlots = { from, to -> viewModel.swapPinnedSlots(from, to) },
                        onSettingsClick = { openPhoneSettings() }
                    )
                }
            }
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
        ContextCompat.registerReceiver(
            this,
            packageChangeReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onResume() {
        super.onResume()
        // Catch any install/uninstall changes that happened while the launcher
        // was backgrounded or if the package broadcast was missed by the system.
        viewModel.refreshApps()
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

    private fun openPhoneSettings() {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }

    private fun hasSeenUsageIntro(): Boolean {
        return prefs.getBoolean(KEY_USAGE_INTRO_SEEN, false)
    }

    private fun markUsageIntroSeen() {
        prefs.edit().putBoolean(KEY_USAGE_INTRO_SEEN, true).apply()
    }

    private fun hasSeenFavoriteAppsIntro(): Boolean {
        return prefs.getBoolean(KEY_FAVORITE_APPS_INTRO_SEEN, false)
    }

    private fun markFavoriteAppsIntroSeen() {
        prefs.edit().putBoolean(KEY_FAVORITE_APPS_INTRO_SEEN, true).apply()
    }

    companion object {
        private const val KEY_USAGE_INTRO_SEEN = "usage_intro_seen_v1"
        private const val KEY_FAVORITE_APPS_INTRO_SEEN = "favorite_apps_intro_seen_v1"
    }
}

@Composable
private fun UsageAccessIntroScreen(
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Welcome to Dash Launcher",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "We use Usage Access to sort apps by what you open most, so your launcher stays fast and useful.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "You can continue to grant access now, or skip for now and enter the launcher shell.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue to permission")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip for now")
            }
        }
    }
}
