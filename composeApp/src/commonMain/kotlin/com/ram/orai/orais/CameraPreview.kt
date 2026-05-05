package com.ram.orai.orais

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale

@Composable
fun CameraPreview(
    cameraManager: CameraController, 
    modifier: Modifier = Modifier,
    flipHorizontal: Boolean = false
) {
    var currentFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    var frameCount by remember { mutableStateOf(0) }

    LaunchedEffect(cameraManager) {
        // Don't call startPreview here - it's called in App.kt
        // Just collect frames
        println("CameraPreview: Starting to collect frames...")
        
        cameraManager.cameraFrames.collect { frame ->
            currentFrame = frame.imageBitmap
            frameCount++
            // Debug: Log first few frames to verify UI is receiving them
            if (frameCount <= 3 || frameCount % 60 == 0) {
                println("UI received frame $frameCount: ${frame.imageBitmap.width}x${frame.imageBitmap.height}")
            }
        }
    }

    Box(modifier = modifier) {
        if (currentFrame != null) {
            Image(
                bitmap = currentFrame!!,
                contentDescription = "Camera preview",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Flip horizontally if requested (mirror effect)
                        scaleX = if (flipHorizontal) -1f else 1f
                    },
                contentScale = ContentScale.Fit  // Fit to screen (no cropping) - matches JVM behavior
            )
        }
    }
}
