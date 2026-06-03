package io.github.dashLauncher.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dashLauncher.LauncherState
import io.github.dashLauncher.data.AppInfo
import io.github.dashLauncher.recognition.InkRecognitionManager

@Composable
fun LauncherRoot(
    state: LauncherState,
    inkManager: InkRecognitionManager,
    onSwipeUp: () -> Unit,
    onBackspace: () -> Unit,
    onDismissAllApps: () -> Unit,
    onAppClick: (String) -> Unit,
    onClearScribble: () -> Unit,
    onPinApp: (String, Int) -> Unit,
    onUnpinApp: (Int) -> Unit,
    onCommitActiveScribble: () -> Unit,
    onSetEditMode: (Boolean) -> Unit,
    // Drag-to-reorder callback: called with (fromIndex, toIndex) when the user
    // drops a pinned slot onto another. Routes to LauncherViewModel.swapPinnedSlots().
    onSwapPinnedSlots: (Int, Int) -> Unit
) {
    var appToPin by remember { mutableStateOf<AppInfo?>(null) }
    // True while the user is dragging a pinned slot. DrawingOverlay reads this
    // to skip event interception so detectDragGesturesAfterLongPress in
    // PinnedAppsSection can receive move events unobstructed.
    var isPinDragActive by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Drawing overlay — handles scribble gestures
        DrawingOverlay(
            modifier = Modifier.fillMaxSize(),
            inkManager = inkManager,
            recognizedText = state.recognizedText,
            onSwipeUp = onSwipeUp,
            onBackspace = onBackspace,
            onCommitActiveScribble = onCommitActiveScribble,
            isInputSuspended = { isPinDragActive }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Bottom
            ) {
                // Fixed-height top bar zone (56 dp).
                // Provides status-bar clearance and a reserved slot for future title/settings.
                // When a scribble is active it shows the recognized text in-place;
                // the rest of the layout never shifts because the box height is constant.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    if (state.recognizedText.isNotEmpty()) {
                        RecognizedTextBar(
                            text = state.recognizedText,
                            onClear = {
                                onClearScribble()
                                appToPin = null
                                onSetEditMode(false)
                            }
                        )
                    } else if (!state.modelDownloadStatus.isReady) {
                        ModelDownloadBanner(status = state.modelDownloadStatus)
                    }
                }

                // Main App List (Suggestions or Recent)
                Box(modifier = Modifier.weight(1f)) {
                    val displayApps = if (state.suggestions.isNotEmpty()) {
                        state.suggestions
                    } else {
                        state.recentApps
                    }

                    AppList(
                        apps = displayApps,
                        modifier = Modifier.fillMaxSize(),
                        onAppClick = { pkg ->
                            if (appToPin != null) {
                                appToPin = null
                            } else {
                                onAppClick(pkg)
                            }
                        },
                        onAppLongClick = { app ->
                            appToPin = app
                            onSetEditMode(true)
                        }
                    )
                    
                    if (appToPin != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                "Tap a slot below to pin ${appToPin?.label}",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Pinned Apps Section (Fixed slots)
                PinnedAppsSection(
                    pinnedApps = state.pinnedApps,
                    isEditMode = state.isEditMode || appToPin != null,
                    onAppClick = { pkg ->
                        if (appToPin != null) {
                            val index = state.pinnedApps.indexOfFirst { it?.packageName == pkg }
                            if (index != -1) {
                                onPinApp(appToPin!!.packageName, index)
                                appToPin = null
                                onSetEditMode(false)
                            }
                        } else {
                            onAppClick(pkg)
                        }
                    },
                    onSlotLongClick = { index ->
                        if (appToPin != null) {
                            onPinApp(appToPin!!.packageName, index)
                            appToPin = null
                            onSetEditMode(false)
                        } else {
                            // Long press on pinned app enters edit mode
                            onSetEditMode(true)
                        }
                    },
                    onRemoveClick = { index ->
                        onUnpinApp(index)
                    },
                    // Drag-to-reorder: only active when no pin-assignment flow is in progress
                    // to avoid conflicting with the appToPin tap-to-select interaction.
                    onSwapSlots = { from, to ->
                        if (appToPin == null) onSwapPinnedSlots(from, to)
                    },
                    onDragStarted = { isPinDragActive = true },
                    onDragEnded = { isPinDragActive = false }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // All apps overlay — uses AllAppsScreen which owns the inline Pin/Unpin buttons
        AnimatedVisibility(
            visible = state.showAllApps,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            val pinnedPackages = state.pinnedApps.mapNotNull { it?.packageName }
            AllAppsScreen(
                apps = state.allApps,
                pinnedPackages = pinnedPackages,
                onAppClick = { pkg ->
                    onAppClick(pkg)
                    onDismissAllApps()
                },
                onDismiss = onDismissAllApps,
                onPinApp = { pkg ->
                    // reuse the same appToPin flow: user taps a slot on the home screen
                    appToPin = state.allApps.find { it.packageName == pkg }
                    onSetEditMode(true)
                    onDismissAllApps()
                },
                onUnpinApp = { pkg ->
                    // resolve slot index here so AllAppsScreen stays package-agnostic
                    val index = state.pinnedApps.indexOfFirst { it?.packageName == pkg }
                    if (index != -1) onUnpinApp(index)
                }
            )
        }
    }
}

@Composable
private fun ModelDownloadBanner(status: io.github.dashLauncher.ModelDownloadStatus) {
    val subtitle = when {
        status.errorText != null -> status.errorText
        !status.etaText.isNullOrEmpty() -> status.etaText
        else -> "Waiting for model readiness"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = Color.White.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = status.message.ifEmpty { "Preparing handwriting model" },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 11.sp
            )
            if (status.isDownloading) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.18f)
                )
            }
        }
    }
}
