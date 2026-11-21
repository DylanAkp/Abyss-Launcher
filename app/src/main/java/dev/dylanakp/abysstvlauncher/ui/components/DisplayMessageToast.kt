package dev.dylanakp.abysstvlauncher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Server Message Toast Component (Jellyfin DisplayMessage)
 */
@Composable
fun DisplayMessageToast(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (message.isEmpty()) return
    
    var isShowing by remember { mutableStateOf(true) }
    
    // Auto-dismiss after 5 seconds
    LaunchedEffect(message) {
        delay(5000L)
        isShowing = false
    }
    
    LaunchedEffect(onDismiss) {
        onDismiss()
    }
    
    AnimatedVisibility(
        visible = isShowing,
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)),
        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = 200))
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .background(Color(0xD91A1B26))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Warning icon
                Text("⚠", fontSize = 20.sp, color = Color.White.copy(alpha = 0.8f))
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Message content
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    fontSize = 15.sp,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Dismiss button
                Text("✕", fontSize = 14.sp, color = Color.Gray)
            }
        }
    }
}
