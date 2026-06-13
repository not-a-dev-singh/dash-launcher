package io.github.zHomeLauncher.ui

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.ui.draw.blur
import io.github.zHomeLauncher.LauncherState
import io.github.zHomeLauncher.data.AppInfo
import io.github.zHomeLauncher.recognition.InkRecognitionManager

private val LauncherHorizontalInset = 24.dp

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
                modifier = Modifier
                    .fillMaxSize()
                    .blur(if (state.showAllApps) 16.dp else 0.dp),
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

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = LauncherHorizontalInset, vertical = 8.dp),
                    color = Color.White.copy(alpha = 0.10f),
                    thickness = 1.dp
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
                                .padding(16.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.12f), MaterialTheme.shapes.medium),
                            color = Color.Black.copy(alpha = 0.75f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                "Tap a slot below to pin ${appToPin?.label}",
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
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
                            .padding(top = 4.dp, bottom = 4.dp)
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
    modelDownloadStatus: io.github.zHomeLauncher.ModelDownloadStatus,
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
                DateHeader()
            }
        }

        extraContent?.invoke(this)
    }
}

@Composable
private fun DateHeader() {
    val currentDate = remember { java.time.LocalDate.now() }
    val formatter = remember {
        java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d", java.util.Locale.getDefault())
    }
    val formattedDate = currentDate.format(formatter)

    Box(
        modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp, vertical = 15.dp),
        // .border(
        //     width = 1.dp,
        //     color = Color.White,
        //     shape = RoundedCornerShape(0.dp)
        // ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formattedDate,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.25.sp,
            // style = TextStyle(
                // textDecoration = TextDecoration.Underline
            )
    }
}

@Composable
private fun SwipeUpHint(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "SwipeUpHintTransition")

    // Animate alpha from 0.2f to 0.6f for a gentle breathing effect
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlphaAnimation"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp), // Low-profile height to reduce vertical footprint
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "S W I P E - U P",
            color = Color.White.copy(alpha = alpha),
            fontSize = 10.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 6.sp // Wide letter spacing for premium typography
        )
    }
}

@Composable
private fun ModelDownloadBanner(status: io.github.zHomeLauncher.ModelDownloadStatus) {
    val subtitle = when {
        status.errorText != null -> status.errorText
        !status.etaText.isNullOrEmpty() -> status.etaText
        else -> "Waiting for model readiness"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(1.dp, Color.White.copy(alpha = 0.08f), MaterialTheme.shapes.medium),
        color = Color.White.copy(alpha = 0.10f),
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
