@file:OptIn(ExperimentalFoundationApi::class)

package io.github.dashLauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.dashLauncher.data.AppInfo

@Composable
fun AppList(
    apps: List<AppInfo>,
    modifier: Modifier = Modifier,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo) -> Unit
) {
    // Hardcoded to start from bottom to top for better one-handed usability.
    // reverseLayout = true ensures the first items (most relevant) appear at the bottom.
    // verticalArrangement = Arrangement.Bottom keeps the list anchored to the bottom.
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.Bottom,
        reverseLayout = true
    ) {
        items(apps) { app ->
            AppRow(app = app, onClick = onAppClick, onLongClick = onAppLongClick)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: AppInfo,
    onClick: (String) -> Unit,
    onLongClick: (AppInfo) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick(app.packageName) },
                onLongClick = { onLongClick(app) }
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
            contentDescription = app.label,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// =============================================================================
// DRAG-TO-REORDER: PinnedAppsSection
//
// Interaction model:
//   1. Long-press a filled slot  → drag begins; slot dims to 40% opacity.
//   2. Drag over another slot    → target slot shows a white highlight ring.
//   3. Release over a slot       → swap the two slots via onSwapSlots(from, to).
//   4. Release outside all slots → drag is cancelled; nothing changes.
//
// How hit-testing works:
//   Each PinnedAppSlot records its position and size into slotRects[] via
//   onGloballyPositioned. During drag, the current pointer offset is compared
//   against every rect to find which slot the finger is over. This avoids
//   complex nested pointer-input scopes and works correctly with the Row layout.
//
// State that drives visuals (local to this composable, not in ViewModel):
//   dragFromIndex  — index of the slot being dragged (-1 = no drag active)
//   dragOverIndex  — index of the slot currently under the finger (-1 = none)
//
// WARNING: do NOT move dragFromIndex / dragOverIndex into LauncherState.
// They are purely transient visual state; persisting them would cause the
// drag ghost to survive configuration changes.
// =============================================================================
@Composable
fun PinnedAppsSection(
    pinnedApps: List<AppInfo?>,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    onAppClick: (String) -> Unit,
    onSlotLongClick: (Int) -> Unit,
    onRemoveClick: (Int) -> Unit,
    onSwapSlots: (fromIndex: Int, toIndex: Int) -> Unit,
    // Callbacks so LauncherRoot can suspend DrawingOverlay for the duration of a drag,
    // preventing the overlay from consuming drag move events after the 8dp slop.
    onDragStarted: () -> Unit = {},
    onDragEnded: () -> Unit = {}
) {
    val columns = 4
    val rows = 1
    val totalSlots = columns * rows

    // Track drag state locally — these never need to survive recomposition
    var dragFromIndex by remember { mutableIntStateOf(-1) }
    var dragOverIndex by remember { mutableIntStateOf(-1) }

    // slotRects stores each slot's bounding box in *root* (window) coordinates.
    // positionInRoot() is used so all slots share the same coordinate space
    // regardless of nesting depth — required for correct cross-slot hit-testing.
    val slotRects = remember { Array(totalSlots) { androidx.compose.ui.geometry.Rect.Zero } }

    Column(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .pointerInput(pinnedApps) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { start ->
                        val hitIndex = slotRects.indexOfFirst { rect ->
                            rect != androidx.compose.ui.geometry.Rect.Zero &&
                                start.x in rect.left..rect.right &&
                                start.y in rect.top..rect.bottom
                        }
                        if (hitIndex != -1 && pinnedApps.getOrNull(hitIndex) != null) {
                            dragFromIndex = hitIndex
                            dragOverIndex = hitIndex
                            onDragStarted()
                        }
                    },
                    onDrag = { change, _ ->
                        if (dragFromIndex == -1) return@detectDragGesturesAfterLongPress
                        val rootPos = change.position
                        val hit = slotRects.indexOfFirst { rect ->
                            rect != androidx.compose.ui.geometry.Rect.Zero &&
                                rootPos.x in rect.left..rect.right &&
                                rootPos.y in rect.top..rect.bottom
                        }
                        dragOverIndex = hit
                    },
                    onDragEnd = {
                        if (dragFromIndex != -1 &&
                            dragOverIndex != -1 &&
                            dragOverIndex != dragFromIndex
                        ) {
                            onSwapSlots(dragFromIndex, dragOverIndex)
                        }
                        dragFromIndex = -1
                        dragOverIndex = -1
                        onDragEnded()
                    },
                    onDragCancel = {
                        dragFromIndex = -1
                        dragOverIndex = -1
                        onDragEnded()
                    }
                )
            }
    ) {
        for (r in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (c in 0 until columns) {
                    val index = r * columns + c
                    if (index < pinnedApps.size) {
                        val isDragSource = dragFromIndex == index
                        val isDragTarget = dragOverIndex == index && dragFromIndex != index

                        Box(
                            modifier = Modifier
                                .width(64.dp)
                                // Record this slot's bounding rect in root coordinates.
                                // positionInParent() matches the coordinate space used
                                // by the parent pointerInput detector.
                                .onGloballyPositioned { coords ->
                                    val pos = coords.positionInParent()
                                    slotRects[index] = androidx.compose.ui.geometry.Rect(
                                        left = pos.x,
                                        top = pos.y,
                                        right = pos.x + coords.size.width,
                                        bottom = pos.y + coords.size.height
                                    )
                                }
                        ) {
                            PinnedAppSlot(
                                app = pinnedApps[index],
                                isEditMode = isEditMode,
                                isDragSource = isDragSource,
                                isDragTarget = isDragTarget,
                                onAppClick = onAppClick,
                                onLongClick = { onSlotLongClick(index) },
                                onRemoveClick = { onRemoveClick(index) }
                            )
                        }
                    }
                }
            }
            if (r < rows - 1) Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@ExperimentalFoundationApi
@Composable
fun PinnedAppSlot(
    app: AppInfo?,
    isEditMode: Boolean = false,
    // Drag-to-reorder visual state:
    // isDragSource — this slot is being dragged; dims icon to signal it's in motion
    // isDragTarget — finger is currently hovering over this slot; shows drop ring
    isDragSource: Boolean = false,
    isDragTarget: Boolean = false,
    onAppClick: (String) -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    // Opacity: dim the source slot during drag so the user sees it as "picked up"
    val contentAlpha = if (isDragSource) 0.35f else 1f

    Box(
        modifier = Modifier.width(64.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        // Drop-target highlight ring: drawn behind the slot content so it doesn't
        // clip the icon. Only visible while isDragTarget is true.
        if (isDragTarget) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { app?.let { onAppClick(it.packageName) } },
                    onLongClick = onLongClick
                )
                .padding(4.dp)
        ) {
            if (app != null) {
                Image(
                    bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer { alpha = contentAlpha }
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                        .padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = app?.label ?: "",
                color = Color.White.copy(alpha = 0.7f * contentAlpha),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        if (app != null && isEditMode) {
            Surface(
                modifier = Modifier
                    .size(20.dp)
                    .offset(x = 4.dp, y = (-4).dp),
                shape = CircleShape,
                color = Color.Red,
                onClick = onRemoveClick
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = Color.White,
                    modifier = Modifier.padding(2.dp)
                )
            }
        }
    }
}

@Composable
fun AppIcon(app: AppInfo, onAppClick: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onAppClick(app.packageName) }
            .padding(4.dp)
    ) {
        Image(
            bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
            contentDescription = app.label,
            modifier = Modifier.size(52.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
