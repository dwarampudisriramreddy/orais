package com.ram.orai.orais

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.material3.Text

actual suspend fun loadThumbnail(path: String, isVideo: Boolean): ImageBitmap? {
    return null
}

actual suspend fun loadFullImage(path: String): ImageBitmap? {
    return null
}

actual fun checkFileStatus(path: String): FileStatusCheck {
    return FileStatusCheck(
        exists = false,
        canRead = false,
        debugMessages = listOf("File status check not implemented for web")
    )
}

actual suspend fun shareFile(path: String) {
    // Web: Not implemented
}

actual suspend fun shareToWhatsApp(path: String) {
    // Web: Not implemented
}

actual suspend fun shareToEmail(path: String, filename: String) {
    // Web: Not implemented
}

actual suspend fun shareToTelegram(path: String) {
    // Web: Not implemented
}

actual suspend fun renameFile(oldPath: String, newName: String, fileManager: FileManager) {
    // Web: Not implemented
}

actual suspend fun startVideoRecording(cameraManager: CameraController, fileManager: FileManager, filename: String, flipHorizontal: Boolean): String? {
    return null
}

actual suspend fun stopVideoRecording(cameraManager: CameraController): String? {
    return null
}

actual fun playVideo(path: String) {
    // Web: Not implemented
}

actual suspend fun loadVideoFrames(videoPath: String, onFrame: (ImageBitmap) -> Unit) {
    // Web: Not implemented
}

@Composable
actual fun VideoPlayerView(videoPath: String, modifier: Modifier) {
    // Web: Video playback not implemented
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Video playback not available on Web",
            color = Color.White
        )
    }
}

actual fun flipImageBitmapHorizontally(bitmap: ImageBitmap): ImageBitmap {
    return bitmap
}





