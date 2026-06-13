package io.github.zHomeLauncher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Branding component that displays the app icon, name, and tagline.
 * Can be used in splash screen or onboarding flow.
 */
@Composable
fun AppBranding(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App icon (vector drawable reference in Compose)
            // Note: To use the vector drawable, you'd typically wrap it with Image or Icon
            // For now, showing the branding text layout
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // App name "zHome"
            Text(
                text = "zHome",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 2.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Tagline "DRAW TO LAUNCH"
            Text(
                text = "DRAW TO LAUNCH",
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 3.sp
            )
        }
    }
}

/**
 * Compact branding card for display in top bar or info section.
 */
@Composable
fun AppBrandingCompact(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "zHome",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Text(
            text = "DRAW TO LAUNCH",
            fontSize = 9.sp,
            fontWeight = FontWeight.Light,
            color = Color.White.copy(alpha = 0.6f),
            letterSpacing = 2.sp
        )
    }
}
