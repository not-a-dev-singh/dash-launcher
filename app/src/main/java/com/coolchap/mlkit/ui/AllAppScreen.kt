package io.github.dashLauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dashLauncher.data.AppInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

@Composable
fun AllAppsScreen(
    apps: List<AppInfo>,
    onAppClick: (String) -> Unit,
    onDismiss: () -> Unit,
    onPinApp: (String) -> Unit,
    onUnpinApp: (String) -> Unit,
    pinnedPackages: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 30) onDismiss() // swipe down to dismiss
                }
            }
            .padding(horizontal = 16.dp)  // vertical padding replaced by the 56dp header zone
    ) {
        // Fixed-height top bar zone — same 56dp as home screen so both screens share
        // the same visual rhythm; "All Apps" title sits here, leaving room for
        // a future back button or settings icon in the same row.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "All Apps",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(apps) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAppClick(app.packageName) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = app.icon.toBitmap(48, 48).asImageBitmap(),
                        contentDescription = app.label,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = app.label,
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    // Pin/unpin
                    Text(
                        text = if (pinnedPackages.contains(app.packageName)) "Unpin" else "Pin",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable {
                                if (pinnedPackages.contains(app.packageName))
                                    onUnpinApp(app.packageName)
                                else
                                    onPinApp(app.packageName)
                            }
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}