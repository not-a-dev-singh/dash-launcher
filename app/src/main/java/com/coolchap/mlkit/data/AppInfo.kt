package io.github.not-a-dev-singh.data

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val lastUsed: Long = 0L,
    val isPinned: Boolean = false
)