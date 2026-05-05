package com.ram.orai.orais

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import java.awt.Desktop
import java.awt.Image
import java.awt.Graphics2D
import java.awt.Color as AwtColor
import java.awt.Font
import java.awt.image.BufferedImage
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import javax.swing.JOptionPane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.bytedeco.opencv.global.opencv_videoio
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_videoio.VideoWriter
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.global.opencv_core
import java.util.concurrent.atomic.AtomicReference
import androidx.compose.ui.graphics.toPixelMap

actual suspend fun loadThumbnail(path: String, isVideo: Boolean): ImageBitmap? {
    return withContext(Dispatchers.IO) {
        try {
            if (isVideo) {
                // For videos, return null (will show icon instead)
                null
            } else {
                val file = File(path)
                if (!file.exists()) return@withContext null
                
                val image = ImageIO.read(file) ?: return@withContext null
                
                // Create thumbnail (max 200x200)
                val maxSize = 200
                val width = image.width
                val height = image.height
                
                val (thumbWidth, thumbHeight) = if (width > height) {
                    if (width > maxSize) {
                        Pair(maxSize, (height * maxSize / width).coerceAtLeast(1))
                    } else {
                        Pair(width, height)
                    }
                } else {
                    if (height > maxSize) {
                        Pair((width * maxSize / height).coerceAtLeast(1), maxSize)
                    } else {
                        Pair(width, height)
                    }
                }
                
                val thumbnail = BufferedImage(thumbWidth, thumbHeight, BufferedImage.TYPE_INT_RGB)
                val g = thumbnail.createGraphics()
                g.drawImage(image.getScaledInstance(thumbWidth, thumbHeight, Image.SCALE_SMOOTH), 0, 0, null)
                g.dispose()
                
                thumbnail.toComposeImageBitmap()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

actual suspend fun loadFullImage(path: String): ImageBitmap? {
    return withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) return@withContext null
            
            val image = ImageIO.read(file) ?: return@withContext null
            
            // Load full-size image
            val bufferedImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
            val g = bufferedImage.createGraphics()
            g.drawImage(image, 0, 0, null)
            g.dispose()
            
            bufferedImage.toComposeImageBitmap()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

actual suspend fun shareFile(path: String) {
    withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.exists() && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file.parentFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

actual suspend fun shareMedia(
    filePath: String,
    mimeType: String,
    title: String
) {
    withContext(Dispatchers.IO) {
        val file = File(filePath)
        
        // Validate file exists
        if (!file.exists()) {
            throw java.io.FileNotFoundException(
                "Media file not found: $filePath\n" +
                "Please ensure the file exists and is accessible."
            )
        }
        
        if (!file.canRead()) {
            throw SecurityException(
                "Cannot read media file: $filePath\n" +
                "Please check file permissions."
            )
        }
        
        if (!Desktop.isDesktopSupported()) {
            throw UnsupportedOperationException(
                "Desktop operations not supported on this system.\n" +
                "Please use a desktop environment."
            )
        }
        
        val desktop = Desktop.getDesktop()
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        
        try {
            // Step 1: Open file folder
            val parentDir = file.parentFile
            if (parentDir != null && parentDir.exists()) {
                desktop.open(parentDir)
                println("Folder opened: ${parentDir.absolutePath}")
            } else {
                throw java.io.FileNotFoundException("Parent directory not found")
            }
            
            // Step 2: Copy absolute file path to clipboard
            val absolutePath = file.absolutePath
            val pathSelection = StringSelection(absolutePath)
            clipboard.setContents(pathSelection, null)
            println("File path copied to clipboard: $absolutePath")
            
            // Step 3: Open WhatsApp Web
            val whatsappWebUrl = java.net.URI("https://web.whatsapp.com")
            desktop.browse(whatsappWebUrl)
            println("WhatsApp Web opened in browser")
            
            // Step 4: Show user-friendly message dialog
            SwingUtilities.invokeLater {
                val message = """
                    File ready to share.
                    
                    Folder opened.
                    Path copied to clipboard.
                    
                    Next steps:
                    1. Paste or drag the file into WhatsApp Web
                    2. Select your patient contact
                    3. Send the message
                    
                    File: ${file.name}
                    Path: $absolutePath
                """.trimIndent()
                
                JOptionPane.showMessageDialog(
                    null,
                    message,
                    "Share with Patient - WhatsApp Web",
                    JOptionPane.INFORMATION_MESSAGE
                )
            }
            
        } catch (e: java.io.IOException) {
            throw Exception(
                "Failed to open folder or browser: ${e.message}\n" +
                "Please manually navigate to: ${file.parent}\n" +
                "And open: https://web.whatsapp.com",
                e
            )
        } catch (e: Exception) {
            throw Exception(
                "Failed to prepare file for sharing: ${e.message}\n" +
                "File: ${file.name}\n" +
                "Please try again or contact support.",
                e
            )
        }
    }
}

actual suspend fun shareToWhatsApp(path: String) {
    withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) {
                println("File does not exist: $path")
                return@withContext
            }
            
            // Try WhatsApp desktop app first
            val whatsappUri = java.net.URI("whatsapp://send?text=" + java.net.URLEncoder.encode("Check this out!", "UTF-8"))
            try {
                Desktop.getDesktop().browse(whatsappUri)
                // Give it a moment, then try web version
                kotlinx.coroutines.delay(500)
            } catch (e: Exception) {
                // Fallback to web WhatsApp
            }
            
            // Use web WhatsApp as fallback
            val webWhatsAppUrl = "https://web.whatsapp.com/send?text=" + 
                java.net.URLEncoder.encode("Check this out!", "UTF-8")
            Desktop.getDesktop().browse(java.net.URI(webWhatsAppUrl))
            
            // Also copy file path to clipboard for manual sharing
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val fileUrl = file.toURI().toString()
            val selection = java.awt.datatransfer.StringSelection(fileUrl)
            clipboard.setContents(selection, null)
            
            println("File path copied to clipboard. Please attach the file manually in WhatsApp.")
        } catch (e: Exception) {
            println("Error sharing to WhatsApp: ${e.message}")
            e.printStackTrace()
        }
    }
}

actual suspend fun shareToEmail(path: String, filename: String) {
    withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) {
                println("File does not exist: $path")
                return@withContext
            }
            
            // Create mailto URI with attachment (file:// URI)
            val fileUri = file.toURI()
            val subject = java.net.URLEncoder.encode("Shared: $filename", "UTF-8")
            val body = java.net.URLEncoder.encode("Please find attached: $filename", "UTF-8")
            
            // Try to open default email client
            val mailtoUri = java.net.URI("mailto:?subject=$subject&body=$body")
            Desktop.getDesktop().mail(mailtoUri)
            
            // Also copy file path to clipboard
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val fileUrl = fileUri.toString()
            val selection = java.awt.datatransfer.StringSelection(fileUrl)
            clipboard.setContents(selection, null)
            
            println("Email client opened. File path copied to clipboard. Please attach the file manually.")
        } catch (e: Exception) {
            println("Error sharing to email: ${e.message}")
            e.printStackTrace()
        }
    }
}

actual suspend fun shareToTelegram(path: String) {
    withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) {
                println("File does not exist: $path")
                return@withContext
            }
            
            // Try Telegram desktop app first
            val telegramUri = java.net.URI("tg://msg?text=" + java.net.URLEncoder.encode("Check this out!", "UTF-8"))
            try {
                Desktop.getDesktop().browse(telegramUri)
                kotlinx.coroutines.delay(500)
            } catch (e: Exception) {
                // Fallback to web Telegram
            }
            
            // Use web Telegram as fallback
            val webTelegramUrl = "https://web.telegram.org/"
            Desktop.getDesktop().browse(java.net.URI(webTelegramUrl))
            
            // Also copy file path to clipboard
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val fileUrl = file.toURI().toString()
            val selection = java.awt.datatransfer.StringSelection(fileUrl)
            clipboard.setContents(selection, null)
            
            println("Telegram opened. File path copied to clipboard. Please attach the file manually.")
        } catch (e: Exception) {
            println("Error sharing to Telegram: ${e.message}")
            e.printStackTrace()
        }
    }
}

actual suspend fun renameFile(oldPath: String, newName: String, fileManager: FileManager) {
    withContext(Dispatchers.IO) {
        try {
            val oldFile = File(oldPath)
            if (!oldFile.exists()) return@withContext
            
            val parentDir = oldFile.parentFile
            val newFile = File(parentDir, newName)
            
            if (newFile.exists()) {
                println("File with name $newName already exists")
                return@withContext
            }
            
            oldFile.renameTo(newFile)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// Video recording state
private val videoWriterRef = AtomicReference<VideoWriter?>(null)
private val recordingPathRef = AtomicReference<String?>(null)
private val recordingFramesRef = AtomicReference<MutableList<Mat>>(null)
private val recordingFlipRef = AtomicReference<Boolean>(false)

actual suspend fun startVideoRecording(cameraManager: CameraController, fileManager: FileManager, filename: String, flipHorizontal: Boolean): String? {
    return withContext(Dispatchers.IO) {
        try {
            val mediaDir = fileManager.getMediaDirectory()
            val videoPath = File(mediaDir, filename).absolutePath
            
            // Get a frame to determine dimensions
            val testFrame = cameraManager.captureImage()
            if (testFrame == null) {
                println("Cannot start recording: no camera frame available")
                return@withContext null
            }
            
            val width = testFrame.imageBitmap.width
            val height = testFrame.imageBitmap.height
            
            // Create VideoWriter (using XVID codec)
            // Note: XVID is more compatible than MP4V on Windows
            // fourcc is created by bitwise OR of character codes
            val fourcc = (('X'.code and 0xFF) shl 0) or 
                        (('V'.code and 0xFF) shl 8) or 
                        (('I'.code and 0xFF) shl 16) or 
                        (('D'.code and 0xFF) shl 24)
            val fps = 30.0
            val size = org.bytedeco.opencv.opencv_core.Size(width, height)
            
            val videoWriter = VideoWriter(videoPath, fourcc, fps, size, true)
            
            if (!videoWriter.isOpened) {
                println("Failed to open video writer for recording")
                videoWriter.release()
                return@withContext null
            }
            
            videoWriterRef.set(videoWriter)
            recordingPathRef.set(videoPath)
            recordingFramesRef.set(mutableListOf())
            recordingFlipRef.set(flipHorizontal)
            
            println("Video recording started: $videoPath ($width x $height @ $fps fps, flip=$flipHorizontal)")
            videoPath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

actual suspend fun stopVideoRecording(cameraManager: CameraController): String? {
    return withContext(Dispatchers.IO) {
        try {
            val videoWriter = videoWriterRef.getAndSet(null)
            val videoPath = recordingPathRef.getAndSet(null)
            val frames = recordingFramesRef.getAndSet(null)
            
            if (videoWriter == null || videoPath == null) {
                println("No active recording to stop")
                return@withContext null
            }
            
            // Write any remaining frames
            frames?.forEach { frame ->
                try {
                    videoWriter.write(frame)
                    frame.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            videoWriter.release()
            println("Video recording stopped: $videoPath")
            
            // Verify file was created
            val file = File(videoPath)
            if (file.exists() && file.length() > 0) {
                videoPath
            } else {
                println("Warning: Video file may be empty or not created")
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

// Helper function to record a frame (called from camera capture loop)
fun recordFrame(mat: Mat) {
    val videoWriter = videoWriterRef.get()
    val shouldFlip = recordingFlipRef.get()
    
    if (videoWriter != null && videoWriter.isOpened) {
        try {
            // Clone the mat to avoid issues with frame reuse
            var frameToWrite = mat.clone()
            
            // Flip horizontally if needed
            if (shouldFlip) {
                val flippedMat = org.bytedeco.opencv.opencv_core.Mat()
                opencv_core.flip(frameToWrite, flippedMat, 1) // 1 = horizontal flip
                frameToWrite.close()
                frameToWrite = flippedMat
            }
            
            videoWriter.write(frameToWrite)
            frameToWrite.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

actual fun playVideo(path: String) {
    // This function is kept for compatibility but not used
    // Video playback is now handled by BuiltInVideoPlayer
}

actual suspend fun loadVideoFrames(videoPath: String, onFrame: (ImageBitmap) -> Unit) {
    withContext(Dispatchers.IO) {
        var videoCapture: org.bytedeco.opencv.opencv_videoio.VideoCapture? = null
        var mat: org.bytedeco.opencv.opencv_core.Mat? = null
        
        try {
            val file = File(videoPath)
            println("Loading video: $videoPath")
            println("File exists: ${file.exists()}")
            println("File size: ${file.length()} bytes")
            
            if (!file.exists()) {
                println("ERROR: Video file does not exist: $videoPath")
                throw Exception("Video file does not exist")
            }
            
            // Use OpenCV VideoCapture to read video frames
            videoCapture = org.bytedeco.opencv.opencv_videoio.VideoCapture(videoPath)
            
            if (!videoCapture.isOpened) {
                println("ERROR: Failed to open video: $videoPath")
                videoCapture.release()
                throw Exception("Failed to open video file")
            }
            
            val fps = videoCapture.get(opencv_videoio.CAP_PROP_FPS)
            val frameCount = videoCapture.get(opencv_videoio.CAP_PROP_FRAME_COUNT)
            val width = videoCapture.get(opencv_videoio.CAP_PROP_FRAME_WIDTH).toInt()
            val height = videoCapture.get(opencv_videoio.CAP_PROP_FRAME_HEIGHT).toInt()
            
            println("Video info: ${width}x${height}, FPS: $fps, Frames: $frameCount")
            
            val frameDelay = if (fps > 0) (1000.0 / fps).toLong().coerceAtLeast(33) else 33L
            
            mat = org.bytedeco.opencv.opencv_core.Mat()
            var frameNumber = 0
            
            while (videoCapture.read(mat) && !mat.empty()) {
                try {
                    // Convert Mat to BufferedImage
                    val rgbMat = org.bytedeco.opencv.opencv_core.Mat()
                    opencv_imgproc.cvtColor(mat, rgbMat, opencv_imgproc.COLOR_BGR2RGB)
                    
                    val frameWidth = rgbMat.cols()
                    val frameHeight = rgbMat.rows()
                    
                    if (frameWidth > 0 && frameHeight > 0) {
                        val bufferedImage = BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_RGB)
                        
                        val matData = ByteArray(frameWidth * frameHeight * 3)
                        rgbMat.data().get(matData)
                        
                        var srcIdx = 0
                        for (y in 0 until frameHeight) {
                            for (x in 0 until frameWidth) {
                                val r = (matData[srcIdx++].toInt() and 0xFF)
                                val g = (matData[srcIdx++].toInt() and 0xFF)
                                val b = (matData[srcIdx++].toInt() and 0xFF)
                                val rgb = (r shl 16) or (g shl 8) or b
                                bufferedImage.setRGB(x, y, rgb)
                            }
                        }
                        
                        rgbMat.close()
                        
                        // Convert to ImageBitmap and emit on main thread
                        val imageBitmap = bufferedImage.toComposeImageBitmap()
                        
                        // Switch to main dispatcher for UI update
                        withContext(Dispatchers.Main) {
                            onFrame(imageBitmap)
                        }
                        
                        frameNumber++
                        if (frameNumber % 30 == 0) {
                            println("Processed $frameNumber frames")
                        }
                        
                        // Control playback speed
                        delay(frameDelay)
                    }
                } catch (e: Exception) {
                    println("Error processing frame $frameNumber: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            println("Video playback finished. Total frames: $frameNumber")
        } catch (e: Exception) {
            println("ERROR loading video: ${e.message}")
            e.printStackTrace()
            throw e // Re-throw to trigger error handling in UI
        } finally {
            try {
                mat?.close()
                videoCapture?.release()
            } catch (e: Exception) {
                println("Error cleaning up video resources: ${e.message}")
            }
        }
    }
}

@Composable
actual fun VideoPlayerView(videoPath: String, modifier: Modifier) {
    // JVM: Video playback not implemented
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Video playback not available on JVM",
            color = Color.White
        )
    }
}

actual fun flipImageBitmapHorizontally(bitmap: ImageBitmap): ImageBitmap {
    return try {
        val width = bitmap.width
        val height = bitmap.height
        
        // Convert ImageBitmap to BufferedImage
        val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val pixelMap = bitmap.toPixelMap()
        
        // Copy pixels to BufferedImage
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = pixelMap[x, y]
                val argb = (
                    ((color.alpha * 255f).toInt() and 0xFF) shl 24 or
                    ((color.red * 255f).toInt() and 0xFF) shl 16 or
                    ((color.green * 255f).toInt() and 0xFF) shl 8 or
                    ((color.blue * 255f).toInt() and 0xFF)
                )
                bufferedImage.setRGB(x, y, argb)
            }
        }
        
        // Flip horizontally using AffineTransform
        val flippedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = flippedImage.createGraphics()
        val transform = java.awt.geom.AffineTransform()
        transform.scale(-1.0, 1.0)
        transform.translate(-width.toDouble(), 0.0)
        g.transform(transform)
        g.drawImage(bufferedImage, 0, 0, null)
        g.dispose()
        
        // Convert back to ImageBitmap
        flippedImage.toComposeImageBitmap()
    } catch (e: Exception) {
        println("Error flipping image: ${e.message}")
        e.printStackTrace()
        bitmap // Return original if flip fails
    }
}

actual fun drawDetectionsOnImageDataPlatform(
    imageData: ImageData,
    detections: List<ToothDetection>,
    flipHorizontal: Boolean,
    width: Int,
    height: Int
): ImageData? {
    return try {
        // Convert ImageData to BufferedImage
        val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val bytes = imageData.bytes
        var idx = 0
        
        // Copy pixels from ImageData to BufferedImage (ARGB format)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val a = bytes[idx++].toInt() and 0xFF
                val r = bytes[idx++].toInt() and 0xFF
                val g = bytes[idx++].toInt() and 0xFF
                val b = bytes[idx++].toInt() and 0xFF
                val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
                bufferedImage.setRGB(x, y, argb)
            }
        }
        
        // Create Graphics2D for drawing
        val g = bufferedImage.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        
        // Draw each detection
        detections.forEach { tooth ->
            val box = tooth.boundingBox
            
            // Map normalized coordinates (0-1) to pixel coordinates
            val normalizedLeft = if (flipHorizontal) 1f - box.right else box.left
            val normalizedRight = if (flipHorizontal) 1f - box.left else box.right
            
            val left = (normalizedLeft * width).toInt()
            val top = (box.top * height).toInt()
            val right = (normalizedRight * width).toInt()
            val bottom = (box.bottom * height).toInt()
            
            val boxWidth = right - left
            val boxHeight = bottom - top
            
            // Determine box color
            // Yellow for FDI teeth, Green for "Normal" condition only, Red for other conditions
            val boxColor = when {
                tooth.toothNumber > 0 -> AwtColor.YELLOW // FDI teeth in yellow
                tooth.condition == "Normal" -> AwtColor.GREEN // Normal condition in green
                tooth.condition != null -> AwtColor.RED // Other conditions in red
                else -> AwtColor.GREEN // Default to green
            }
            
            // Draw filled semi-transparent background
            g.color = AwtColor(boxColor.red, boxColor.green, boxColor.blue, 50)
            g.fillRect(left, top, boxWidth, boxHeight)
            
            // Draw box border
            g.color = boxColor
            g.stroke = java.awt.BasicStroke(3f)
            g.drawRect(left, top, boxWidth, boxHeight)
            
            // Draw label text
            val labelText = buildString {
                if (tooth.toothNumber > 0) {
                    append("#${tooth.toothNumber}")
                }
                tooth.condition?.let { condition ->
                    if (tooth.toothNumber > 0) append("\n")
                    append(condition)
                    tooth.conditionConfidence?.let { conf ->
                        append(" ${(conf * 100).toInt()}%")
                    }
                } ?: run {
                    if (tooth.toothNumber <= 0) {
                        append("Condition")
                    }
                }
            }
            
            if (labelText.isNotBlank()) {
                // Draw background for text
                g.font = Font(Font.SANS_SERIF, Font.BOLD, 16)
                val metrics = g.fontMetrics
                val textWidth = metrics.stringWidth(labelText)
                val textHeight = metrics.height
                
                val textX = left + 8
                val textY = top - 8
                
                g.color = AwtColor(0, 0, 0, 200) // Semi-transparent black
                g.fillRect(textX - 4, textY - textHeight - 4, textWidth + 8, textHeight + 8)
                
                // Draw text
                g.color = AwtColor.WHITE
                g.drawString(labelText, textX, textY)
            }
        }
        
        g.dispose()
        
        // Convert back to ImageData (ARGB format)
        val resultBytes = ByteArray(width * height * 4)
        var resultIdx = 0
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = bufferedImage.getRGB(x, y)
                resultBytes[resultIdx++] = ((argb shr 24) and 0xFF).toByte() // Alpha
                resultBytes[resultIdx++] = ((argb shr 16) and 0xFF).toByte() // Red
                resultBytes[resultIdx++] = ((argb shr 8) and 0xFF).toByte()  // Green
                resultBytes[resultIdx++] = (argb and 0xFF).toByte()         // Blue
            }
        }
        
        println("Successfully drew ${detections.size} detection boxes on ImageData: ${width}x${height}")
        
        ImageData(width, height, imageData.rotationDegrees, resultBytes)
    } catch (e: Exception) {
        println("Error drawing detections on ImageData: ${e.message}")
        e.printStackTrace()
        null // Return null on error, will use original ImageData
    }
}

actual fun checkFileStatus(path: String): FileStatusCheck {
    val debugMessages = mutableListOf<String>()
    val file = File(path)
    
    debugMessages.add("📂 Checking file: $path")
    
    val exists = file.exists()
    debugMessages.add(if (exists) "✅ File exists" else "❌ File does NOT exist")
    
    if (exists) {
        debugMessages.add("📊 File size: ${file.length()} bytes")
        debugMessages.add("📁 Is file: ${file.isFile}")
        debugMessages.add("📁 Is directory: ${file.isDirectory}")
        debugMessages.add("🔐 Can read: ${file.canRead()}")
        debugMessages.add("✍️ Can write: ${file.canWrite()}")
        debugMessages.add("🔒 Is absolute: ${file.isAbsolute}")
        debugMessages.add("📅 Last modified: ${java.util.Date(file.lastModified())}")
        
        if (file.isFile) {
            val extension = file.extension.lowercase()
            debugMessages.add("📄 Extension: $extension")
            debugMessages.add("📄 MIME type: ${when(extension) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "gif" -> "image/gif"
                "bmp" -> "image/bmp"
                "webp" -> "image/webp"
                else -> "unknown"
            }}")
        }
    } else {
        debugMessages.add("💡 File path may be incorrect or file was deleted")
        val parent = file.parentFile
        if (parent != null) {
            debugMessages.add("📁 Parent directory exists: ${parent.exists()}")
            if (parent.exists()) {
                debugMessages.add("📁 Parent can read: ${parent.canRead()}")
            }
        }
    }
    
    return FileStatusCheck(
        exists = exists,
        canRead = exists && file.canRead(),
        debugMessages = debugMessages
    )
}

