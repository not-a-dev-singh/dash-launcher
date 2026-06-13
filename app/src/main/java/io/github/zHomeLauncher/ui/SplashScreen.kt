package io.github.zHomeLauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.zHomeLauncher.R

/**
 * Splash/onboarding screen showing app icon, name, and tagline.
 * Display this when the app first launches or in a splash activity.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            Spacer(modifier = Modifier.weight(1f))
            
            // App icon (vector drawable)
            Icon(
                painter = painterResource(id = R.drawable.ic_app_icon),
                contentDescription = "zHome launcher icon",
                modifier = Modifier.size(128.dp),
                tint = Color.Unspecified  // Use colors from drawable
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // App name
            Text(
                text = "zHome",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 2.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Tagline
            Text(
                text = "DRAW TO LAUNCH",
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 3.sp
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Optional: loading indicator or version info at bottom
            Text(
                text = "Loading...",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.weight(0f)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
