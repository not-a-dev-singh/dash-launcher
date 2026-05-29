package io.github.not-a-dev-singh.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.not-a-dev-singh.LauncherState
import io.github.not-a-dev-singh.data.AppInfo
import io.github.not-a-dev-singh.recognition.InkRecognitionManager

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
    onSetEditMode: (Boolean) -> Unit
) {
    var appToPin by remember { mutableStateOf<AppInfo?>(null) }

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
            onCommitActiveScribble = onCommitActiveScribble
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Bottom
            ) {
                // Recognized text bar at the top
                if (state.recognizedText.isNotEmpty()) {
                    RecognizedTextBar(
                        text = state.recognizedText,
                        onClear = {
                            onClearScribble()
                            appToPin = null
                            onSetEditMode(false)
                        }
                    )
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
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // All apps overlay
        AnimatedVisibility(
            visible = state.showAllApps,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            "All Apps", 
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        TextButton(
                            onClick = onDismissAllApps,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Text("Back", color = Color.Gray)
                        }
                    }
                    AppList(
                        apps = state.allApps,
                        onAppClick = { pkg ->
                            if (appToPin != null) {
                                appToPin = null
                            } else {
                                onAppClick(pkg)
                                onDismissAllApps()
                            }
                        },
                        onAppLongClick = { app ->
                            appToPin = app
                            onSetEditMode(true)
                            onDismissAllApps()
                        }
                    )
                }
            }
        }
    }
}