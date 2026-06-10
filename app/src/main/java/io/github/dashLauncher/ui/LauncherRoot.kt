package io.github.dashLauncher.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dashLauncher.LauncherState
import io.github.dashLauncher.data.AppInfo
import io.github.dashLauncher.recognition.InkRecognitionManager

private val LauncherHorizontalInset = 12.dp

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
    onSwapPinnedSlots: (Int, Int) -> Unit,
    onSettingsClick: (() -> Unit)? = null,
    topBarContent: (@Composable BoxScope.() -> Unit)? = null
) {
    var appToPin by remember { mutableStateOf<AppInfo?>(null) }
    // True while the user is dragging a pinned slot. DrawingOverlay reads this
    // to skip event interception so detectDragGesturesAfterLongPress in
    // PinnedAppsSection can receive move events unobstructed.
    var isPinDragActive by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                LauncherTopBar(
                    recognizedText = state.recognizedText,
                    modelDownloadStatus = state.modelDownloadStatus,
                    onClearScribble = {
                        onClearScribble()
                        appToPin = null
                        onSetEditMode(false)
                    },
                    modifier = Modifier.padding(start = LauncherHorizontalInset, end = LauncherHorizontalInset, top = 4.dp),
                    extraContent = topBarContent
                )

                // Main App List (Suggestions or Recent)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = LauncherHorizontalInset)
                ) {
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LauncherHorizontalInset),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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

                    SwipeUpHint(
                        modifier = Modifier
                            .padding(top = 10.dp, bottom = 10.dp)
                    )
                }
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
                },
                onSettingsClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun LauncherTopBar(
    recognizedText: String,
    modelDownloadStatus: io.github.dashLauncher.ModelDownloadStatus,
    onClearScribble: () -> Unit,
    modifier: Modifier = Modifier,
    extraContent: (@Composable BoxScope.() -> Unit)? = null
) {
    // Fixed-height top bar zone (56 dp).
    // Provides status-bar clearance and a reserved slot for future sections.
    // The default content stays stable, but callers can inject additional UI
    // later (clock, music controls, settings, etc.) without changing layout.
    // TODO: define named top-bar sections instead of a single generic slot so
    // future widgets can plug in without coupling to one shared content lambda.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        when {
            recognizedText.isNotEmpty() -> {
                RecognizedTextBar(
                    text = recognizedText,
                    onClear = onClearScribble
                )
            }
            !modelDownloadStatus.isReady -> {
                ModelDownloadBanner(status = modelDownloadStatus)
            }
            else -> {
                ReservedTopBarHint()
            }
        }

        extraContent?.invoke(this)
    }
}

@Composable
private fun ReservedTopBarHint() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 4.dp)
                .background(Color.White.copy(alpha = 0.10f), RectangleShape)
        )
    }
}

@Composable
private fun SwipeUpHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(68.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = "Swipe up for all apps",
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(16.dp)
        )
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.16f))
        )
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
