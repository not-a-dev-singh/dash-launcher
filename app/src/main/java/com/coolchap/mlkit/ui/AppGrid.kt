@file:OptIn(ExperimentalFoundationApi::class)

package io.github.not-a-dev-singh.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.not-a-dev-singh.data.AppInfo

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

@Composable
fun PinnedAppsSection(
    pinnedApps: List<AppInfo?>,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    onAppClick: (String) -> Unit,
    onSlotLongClick: (Int) -> Unit,
    onRemoveClick: (Int) -> Unit
) {
    val columns = 5
    val rows = 2
    
    Column(modifier = modifier.padding(horizontal = 8.dp)) {
        for (r in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (c in 0 until columns) {
                    val index = r * columns + c
                    if (index < pinnedApps.size) {
                        PinnedAppSlot(
                            app = pinnedApps[index],
                            isEditMode = isEditMode,
                            onAppClick = onAppClick,
                            onLongClick = { onSlotLongClick(index) },
                            onRemoveClick = { onRemoveClick(index) }
                        )
                    }
                }
            }
            if (r == 0) Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@ExperimentalFoundationApi
@Composable
fun PinnedAppSlot(
    app: AppInfo?,
    isEditMode: Boolean = false,
    onAppClick: (String) -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Box(
        modifier = Modifier.width(64.dp),
        contentAlignment = Alignment.TopEnd
    ) {
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
                    modifier = Modifier.size(48.dp)
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
                color = Color.White.copy(alpha = 0.7f),
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
