package com.ram.orai.orais

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.SharedFlow

data class CameraFrame(val imageBitmap: ImageBitmap, val timestamp: Long)

interface CameraManager {
    fun startPreview()
    fun stopPreview()
    fun switchCamera() // For devices with multiple cameras
    fun captureImage(): CameraFrame? // Returns a single frame
    val cameraFrames: SharedFlow<CameraFrame> // Stream of frames for live preview
}

expect class CameraManagerImpl(context: Any?, lifecycleOwner: Any?) : CameraController

expect fun getCameraManager(context: Any?, lifecycleOwner: Any?): CameraController
