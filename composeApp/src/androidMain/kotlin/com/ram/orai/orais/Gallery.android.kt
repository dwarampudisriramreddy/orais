package com.ram.orai.orais

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.provider.MediaStore
import androidx.camera.video.Recording
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import android.widget.VideoView
import android.view.ViewGroup
import android.widget.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference

// Get Android context and lifecycle owner (will be set from MainActivity)
private var appContext: Context? = null
private var activityContext: Context? = null
private var appLifecycleOwner: androidx.lifecycle.LifecycleOwner? = null

fun setAppContext(context: Context) {
    appContext = context.applicationContext
    activityContext = context // Store Activity context for starting activities
}

fun setAppLifecycleOwner(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
    appLifecycleOwner = lifecycleOwner
}

// Export functions to get context and lifecycle owner
fun getAppContext(): Context? = appContext
fun getAppLifecycleOwner(): androidx.lifecycle.LifecycleOwner? = appLifecycleOwner

actual suspend fun loadThumbnail(path: String, isVideo: Boolean): ImageBitmap? {
    return withContext(Dispatchers.IO) {
        try {
            val ctx = appContext ?: return@withContext null
            val file = File(path)
            if (!file.exists()) return@withContext null
            
            if (isVideo) {
                // Extract thumbnail from video using MediaMetadataRetriever
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(path)
                    val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    retriever.release()
                    
                    if (bitmap != null) {
                        // Scale down to thumbnail size (max 200x200)
                        val maxSize = 200
                        val width = bitmap.width
                        val height = bitmap.height
                        
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
                        
                        val thumbnail = Bitmap.createScaledBitmap(bitmap, thumbWidth, thumbHeight, true)
                        bitmap.recycle()
                        thumbnail.asImageBitmap()
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    retriever.release()
                    null
                }
            } else {
                // Load image thumbnail
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(path, options)
                
                val maxSize = 200
                val width = options.outWidth
                val height = options.outHeight
                
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
                
                // Calculate sample size
                options.inSampleSize = calculateInSampleSize(options, thumbWidth, thumbHeight)
                options.inJustDecodeBounds = false
                
                val bitmap = BitmapFactory.decodeFile(path, options)
                if (bitmap != null) {
                    val thumbnail = Bitmap.createScaledBitmap(bitmap, thumbWidth, thumbHeight, true)
                    if (bitmap != thumbnail) {
                        bitmap.recycle()
                    }
                    thumbnail.asImageBitmap()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1
    
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        
        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }
    
    return inSampleSize
}

actual suspend fun loadFullImage(path: String): ImageBitmap? {
    return withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            println("Attempting to load image from: $path")
            println("File exists: ${file.exists()}")
            println("File can read: ${file.canRead()}")
            val fileSize = if (file.exists()) file.length() else 0
            println("File size: $fileSize bytes")
            
            if (!file.exists()) {
                println("ERROR: File does not exist: $path")
                return@withContext null
            }
            if (!file.canRead()) {
                println("ERROR: File cannot be read: $path")
                return@withContext null
            }
            
            // Check if file is empty or too small
            if (fileSize == 0L) {
                println("ERROR: File is empty (0 bytes)")
                return@withContext null
            }
            if (fileSize < 100) {
                println("ERROR: File is too small (${fileSize} bytes) - likely corrupted")
                return@withContext null
            }
            
            // Verify file header to check if it's a valid image
            val isValidImage = verifyImageFileHeader(file)
            if (!isValidImage) {
                println("ERROR: File does not appear to be a valid image (invalid header)")
                // Still try to decode in case it's a valid format BitmapFactory supports
            }
            
            // First, get image dimensions to avoid loading huge images into memory
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            // Try decoding bounds - if this fails, the file might be corrupted
            val decoded = BitmapFactory.decodeFile(path, options)
            if (decoded != null) {
                decoded.recycle() // Free the bitmap if it was decoded
            }
            
            val imageWidth = options.outWidth
            val imageHeight = options.outHeight
            val mimeType = options.outMimeType
            
            println("Image info - Width: $imageWidth, Height: $imageHeight, MIME: $mimeType")
            
            if (imageWidth <= 0 || imageHeight <= 0) {
                println("ERROR: Invalid image dimensions: ${imageWidth}x${imageHeight}")
                println("ERROR: This usually means the file is corrupted, incomplete, or not a valid image format")
                // Try loading via input stream as fallback
                return@withContext tryLoadViaInputStream(file)
            }
            
            println("Loading image: $path (${imageWidth}x${imageHeight}, $mimeType)")
            
            // Calculate sample size to avoid OutOfMemoryError
            // For large images, be more aggressive with downsampling
            // Target max dimension based on image size to prevent OOM
            val totalPixels = imageWidth * imageHeight
            val maxPixels = when {
                totalPixels > 50_000_000 -> 1_000_000 // Very large images: max 1MP
                totalPixels > 20_000_000 -> 2_000_000 // Large images: max 2MP
                totalPixels > 10_000_000 -> 4_000_000 // Medium-large: max 4MP
                else -> 8_000_000 // Smaller images: max 8MP (2048x2048)
            }
            
            val maxDimension = when {
                totalPixels > 50_000_000 -> 1000 // Very large: 1000px max
                totalPixels > 20_000_000 -> 1400 // Large: 1400px max
                totalPixels > 10_000_000 -> 2000 // Medium-large: 2000px max
                else -> 2048 // Normal: 2048px max
            }
            
            // Calculate sample size (must be power of 2 for BitmapFactory)
            val widthRatio = (imageWidth.toFloat() / maxDimension).toInt()
            val heightRatio = (imageHeight.toFloat() / maxDimension).toInt()
            var sampleSize = maxOf(widthRatio, heightRatio).coerceAtLeast(1)
            
            // Round up to nearest power of 2 (BitmapFactory requirement)
            sampleSize = when {
                sampleSize <= 1 -> 1
                sampleSize <= 2 -> 2
                sampleSize <= 4 -> 4
                sampleSize <= 8 -> 8
                sampleSize <= 16 -> 16
                else -> 32 // Cap at 32x downsampling
            }
            
            // Verify the resulting size won't be too large
            val resultWidth = imageWidth / sampleSize
            val resultHeight = imageHeight / sampleSize
            val resultPixels = resultWidth * resultHeight
            
            println("Original: ${imageWidth}x${imageHeight} (${totalPixels / 1_000_000}MP)")
            println("Target max: ${maxDimension}px, Max pixels: ${maxPixels / 1_000_000}MP")
            println("Using sample size: $sampleSize")
            println("Result size: ${resultWidth}x${resultHeight} (${resultPixels / 1_000_000}MP)")
            
            // If still too large, increase sample size
            if (resultPixels > maxPixels) {
                val newSampleSize = kotlin.math.sqrt((totalPixels.toFloat() / maxPixels)).toInt()
                val roundedSampleSize = when {
                    newSampleSize <= 1 -> 1
                    newSampleSize <= 2 -> 2
                    newSampleSize <= 4 -> 4
                    newSampleSize <= 8 -> 8
                    newSampleSize <= 16 -> 16
                    else -> 32
                }
                if (roundedSampleSize > sampleSize) {
                    sampleSize = roundedSampleSize
                    println("Adjusted sample size to $sampleSize to fit memory constraints")
                }
            }
            
            // Now decode with appropriate sample size
            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            // Use RGB_565 for very large images to save memory (half the memory of ARGB_8888)
            options.inPreferredConfig = if (resultPixels > 10_000_000) {
                Bitmap.Config.RGB_565
            } else {
                Bitmap.Config.ARGB_8888
            }
            options.inDither = false
            options.inScaled = false
            options.inPurgeable = true // Allow system to reclaim memory
            options.inInputShareable = true
            options.inMutable = false // Don't need mutable bitmap for preview
            
            println("Decoding with config: ${options.inPreferredConfig}")
            
            var bitmap = BitmapFactory.decodeFile(path, options)
            if (bitmap != null) {
                println("Successfully loaded image: ${bitmap.width}x${bitmap.height}")
                bitmap.asImageBitmap()
            } else {
                println("ERROR: BitmapFactory.decodeFile returned null with sample size $sampleSize")
                println("ERROR: Trying with more aggressive downsampling...")
                
                // Try with larger sample size (more aggressive downsampling)
                val aggressiveSampleSize = when {
                    sampleSize >= 32 -> 32 // Already at max
                    sampleSize >= 16 -> 32
                    sampleSize >= 8 -> 16
                    sampleSize >= 4 -> 8
                    sampleSize >= 2 -> 4
                    else -> 2
                }
                
                if (aggressiveSampleSize > sampleSize) {
                    options.inSampleSize = aggressiveSampleSize
                    options.inPreferredConfig = Bitmap.Config.RGB_565 // Use RGB_565 for aggressive downsampling
                    val newResultWidth = imageWidth / aggressiveSampleSize
                    val newResultHeight = imageHeight / aggressiveSampleSize
                    println("Retrying with sample size: $aggressiveSampleSize (${newResultWidth}x${newResultHeight})")
                    
                    bitmap = BitmapFactory.decodeFile(path, options)
                    if (bitmap != null) {
                        println("✅ Successfully loaded with aggressive downsampling: ${bitmap.width}x${bitmap.height}")
                        bitmap.asImageBitmap()
                    } else {
                        println("ERROR: Still failed with aggressive downsampling")
                        println("ERROR: Possible causes:")
                        println("  - File is corrupted or incomplete")
                        println("  - File format not supported by BitmapFactory")
                        println("  - Insufficient memory even with downsampling")
                        println("  - File is locked or being written to")
                        // Try loading via input stream as fallback
                        tryLoadViaInputStream(file)
                    }
                } else {
                    println("ERROR: Already at maximum downsampling, trying input stream fallback")
                    println("ERROR: Possible causes:")
                    println("  - File is corrupted or incomplete")
                    println("  - File format not supported by BitmapFactory")
                    println("  - Insufficient memory (try smaller sample size)")
                    println("  - File is locked or being written to")
                    // Try loading via input stream as fallback
                    tryLoadViaInputStream(file)
                }
            }
        } catch (e: OutOfMemoryError) {
            println("ERROR: Out of memory loading image from $path: ${e.message}")
            System.gc() // Try to free memory
            null
        } catch (e: Exception) {
            println("ERROR: Exception loading full image from $path: ${e.message}")
            println("ERROR: Exception type: ${e.javaClass.simpleName}")
            e.printStackTrace()
            // Try loading via input stream as fallback
            try {
                tryLoadViaInputStream(File(path))
            } catch (e2: Exception) {
                println("ERROR: Fallback also failed: ${e2.message}")
                println("ERROR: Fallback exception type: ${e2.javaClass.simpleName}")
                e2.printStackTrace()
                null
            }
        }
    }
}

private fun verifyImageFileHeader(file: File): Boolean {
    return try {
        file.inputStream().use { inputStream ->
            val header = ByteArray(8)
            val bytesRead = inputStream.read(header)
            if (bytesRead < 8) {
                println("WARNING: File header too short (only $bytesRead bytes)")
                return false
            }
            
            // Check for PNG signature: 89 50 4E 47 0D 0A 1A 0A
            val isPng = header[0] == 0x89.toByte() && 
                       header[1] == 0x50.toByte() && 
                       header[2] == 0x4E.toByte() && 
                       header[3] == 0x47.toByte()
            
            // Check for JPEG signature: FF D8 FF
            val isJpeg = header[0] == 0xFF.toByte() && 
                        header[1] == 0xD8.toByte() && 
                        header[2] == 0xFF.toByte()
            
            // Check for GIF signature: GIF87a or GIF89a
            val isGif = (header[0] == 0x47.toByte() && header[1] == 0x49.toByte() && 
                        header[2] == 0x46.toByte() && header[3] == 0x38.toByte() &&
                        (header[4] == 0x37.toByte() || header[4] == 0x39.toByte()) &&
                        header[5] == 0x61.toByte())
            
            val isValid = isPng || isJpeg || isGif
            if (isValid) {
                val format = when {
                    isPng -> "PNG"
                    isJpeg -> "JPEG"
                    isGif -> "GIF"
                    else -> "Unknown"
                }
                println("File header verified: $format format")
            } else {
                println("WARNING: File header does not match known image formats")
                println("Header bytes: ${header.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }}")
            }
            isValid
        }
    } catch (e: Exception) {
        println("WARNING: Could not verify file header: ${e.message}")
        false
    }
}

private fun tryLoadViaInputStream(file: File): ImageBitmap? {
    return try {
        println("Trying to load image via input stream: ${file.absolutePath}")
        
        // First pass: get dimensions
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        
        var dimensionsRead = false
        file.inputStream().use { inputStream ->
            val result = BitmapFactory.decodeStream(inputStream, null, options)
            dimensionsRead = (options.outWidth > 0 && options.outHeight > 0)
            if (result != null) {
                result.recycle()
            }
        }
        
        val imageWidth = options.outWidth
        val imageHeight = options.outHeight
        val mimeType = options.outMimeType
        
        println("Input stream decode - Width: $imageWidth, Height: $imageHeight, MIME: $mimeType")
        
        if (!dimensionsRead || imageWidth <= 0 || imageHeight <= 0) {
            println("ERROR: Invalid dimensions from input stream: ${imageWidth}x${imageHeight}")
            println("ERROR: File appears to be corrupted or not a valid image")
            return null
        }
        
        // Calculate sample size - same logic as main decode
        val totalPixels = imageWidth * imageHeight
        val maxPixels = when {
            totalPixels > 50_000_000 -> 1_000_000
            totalPixels > 20_000_000 -> 2_000_000
            totalPixels > 10_000_000 -> 4_000_000
            else -> 8_000_000
        }
        
        val maxDimension = when {
            totalPixels > 50_000_000 -> 1000
            totalPixels > 20_000_000 -> 1400
            totalPixels > 10_000_000 -> 2000
            else -> 2048
        }
        
        val widthRatio = (imageWidth.toFloat() / maxDimension).toInt()
        val heightRatio = (imageHeight.toFloat() / maxDimension).toInt()
        var sampleSize = maxOf(widthRatio, heightRatio).coerceAtLeast(1)
        
        // Round up to nearest power of 2
        sampleSize = when {
            sampleSize <= 1 -> 1
            sampleSize <= 2 -> 2
            sampleSize <= 4 -> 4
            sampleSize <= 8 -> 8
            sampleSize <= 16 -> 16
            else -> 32
        }
        
        val resultWidth = imageWidth / sampleSize
        val resultHeight = imageHeight / sampleSize
        val resultPixels = resultWidth * resultHeight
        
        if (resultPixels > maxPixels) {
            val newSampleSize = kotlin.math.sqrt((totalPixels.toFloat() / maxPixels)).toInt()
            val roundedSampleSize = when {
                newSampleSize <= 1 -> 1
                newSampleSize <= 2 -> 2
                newSampleSize <= 4 -> 4
                newSampleSize <= 8 -> 8
                newSampleSize <= 16 -> 16
                else -> 32
            }
            if (roundedSampleSize > sampleSize) {
                sampleSize = roundedSampleSize
            }
        }
        
        println("Input stream - Original: ${imageWidth}x${imageHeight} (${totalPixels / 1_000_000}MP)")
        println("Input stream - Using sample size: $sampleSize")
        println("Input stream - Result: ${resultWidth}x${resultHeight} (${resultPixels / 1_000_000}MP)")
        
        // Second pass: decode with sample size
        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize
        // Use RGB_565 for large images to save memory
        options.inPreferredConfig = if (resultPixels > 10_000_000) {
            Bitmap.Config.RGB_565
        } else {
            Bitmap.Config.ARGB_8888
        }
        options.inPurgeable = true
        options.inInputShareable = true
        options.inMutable = false
        
        println("Input stream - Decoding with config: ${options.inPreferredConfig}")
        
        // Try with buffered input stream for better reliability
        file.inputStream().buffered().use { bufferedStream ->
            val bitmap = BitmapFactory.decodeStream(bufferedStream, null, options)
            if (bitmap != null) {
                println("✅ Successfully loaded image via input stream: ${bitmap.width}x${bitmap.height}")
                bitmap.asImageBitmap()
            } else {
                println("ERROR: BitmapFactory.decodeStream returned null")
                println("ERROR: Even input stream method failed - file is likely corrupted")
                null
            }
        }
    } catch (e: OutOfMemoryError) {
        println("ERROR: Out of memory in input stream fallback: ${e.message}")
        System.gc()
        null
    } catch (e: Exception) {
        println("ERROR: Exception loading via input stream: ${e.message}")
        println("ERROR: Exception type: ${e.javaClass.simpleName}")
        e.printStackTrace()
        null
    }
}

actual suspend fun shareFile(path: String) {
    println("DEBUG: shareFile() called with path: $path")
    withContext(Dispatchers.Main) {
        try {
            println("DEBUG: shareFile() - on Main thread")
            val context = appContext ?: run {
                println("ERROR: appContext is null, cannot share file")
                return@withContext
            }
            println("DEBUG: shareFile() - appContext obtained: ${context.packageName}")
            val activityCtx = if (activityContext is android.app.Activity) {
                println("DEBUG: Using Activity context")
                activityContext as android.app.Activity
            } else {
                println("WARNING: activityContext is null or not Activity, using Application context")
                context
            }
            println("DEBUG: shareFile() - using context: ${activityCtx.javaClass.simpleName}")
            println("DEBUG: shareFile() - context is Activity: ${activityCtx is android.app.Activity}")
            val file = File(path)
            println("DEBUG: shareFile() - File object created: ${file.absolutePath}")
            if (!file.exists()) {
                println("ERROR: File does not exist: $path")
                println("ERROR: File absolute path: ${file.absolutePath}")
                return@withContext
            }
            println("DEBUG: shareFile() - File exists, size: ${file.length()} bytes")
            
            val authority = "${context.packageName}.fileprovider"
            println("DEBUG: shareFile() - FileProvider authority: $authority")
            val uri = FileProvider.getUriForFile(context, authority, file)
            println("DEBUG: shareFile() - URI created: $uri")
            
            var mimeType = context.contentResolver.getType(uri)
            println("DEBUG: shareFile() - MIME type from resolver: $mimeType")
            
            // Fallback MIME type if resolver returns null
            if (mimeType == null) {
                mimeType = when {
                    file.extension.equals("mp4", ignoreCase = true) -> "video/mp4"
                    file.extension.equals("jpg", ignoreCase = true) || file.extension.equals("jpeg", ignoreCase = true) -> "image/jpeg"
                    file.extension.equals("png", ignoreCase = true) -> "image/png"
                    else -> "*/*"
                }
                println("DEBUG: shareFile() - Using fallback MIME type: $mimeType")
            }
            
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            println("DEBUG: shareFile() - ShareIntent created with type: $mimeType")
            println("DEBUG: shareFile() - ShareIntent created")
            
            val chooser = Intent.createChooser(shareIntent, "Share via")
            println("DEBUG: shareFile() - Chooser intent created")
            
            // Add FLAG_ACTIVITY_NEW_TASK only if not using Activity context
            if (activityCtx !is android.app.Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                println("DEBUG: Added FLAG_ACTIVITY_NEW_TASK (not using Activity context)")
            } else {
                println("DEBUG: Using Activity context, no need for FLAG_ACTIVITY_NEW_TASK")
            }
            
            // Check if chooser can be resolved
            val resolveInfo = activityCtx.packageManager.resolveActivity(chooser, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            if (resolveInfo == null) {
                println("ERROR: No app can handle share intent")
                println("ERROR: ShareIntent action: ${shareIntent.action}, type: ${shareIntent.type}, URI: $uri")
                return@withContext
            }
            println("DEBUG: Chooser can be resolved by: ${resolveInfo.activityInfo.packageName}")
            
            println("DEBUG: shareFile() - Starting activity with context: ${activityCtx.javaClass.simpleName}")
            try {
                activityCtx.startActivity(chooser)
                println("DEBUG: Share intent started successfully for: $path")
            } catch (e: android.content.ActivityNotFoundException) {
                println("ERROR: ActivityNotFoundException - No app can handle this share")
                println("ERROR: ShareIntent action: ${shareIntent.action}, type: ${shareIntent.type}")
                e.printStackTrace()
                throw e
            } catch (e: Exception) {
                println("ERROR: Failed to start share activity: ${e.message}")
                println("ERROR: Exception type: ${e.javaClass.name}")
                e.printStackTrace()
                throw e
            }
        } catch (e: Exception) {
            println("ERROR sharing file: ${e.message}")
            println("ERROR: Exception type: ${e.javaClass.name}")
            e.printStackTrace()
        }
    }
}

actual suspend fun shareMedia(
    filePath: String,
    mimeType: String,
    title: String
) {
    withContext(Dispatchers.Main) {
        // Validate Activity context is available
        val activityCtx = activityContext as? android.app.Activity
            ?: throw IllegalStateException(
                "Activity context not available. Cannot share media. " +
                "Please ensure MainActivity has called setAppContext(this)."
            )
        
        val appCtx = appContext
            ?: throw IllegalStateException("Application context not available")
        
        // Validate file exists
        val file = File(filePath)
        println("DEBUG: shareMedia - File path: ${file.absolutePath}")
        println("DEBUG: shareMedia - File exists: ${file.exists()}")
        println("DEBUG: File canonical path: ${file.canonicalPath}")
        
        if (!file.exists()) {
            throw java.io.FileNotFoundException(
                "Media file not found: $filePath\n" +
                "Absolute path: ${file.absolutePath}\n" +
                "Please ensure the file exists and is accessible."
            )
        }
        
        if (!file.canRead()) {
            throw SecurityException(
                "Cannot read media file: $filePath\n" +
                "Please check file permissions."
            )
        }
        
        try {
            // Create FileProvider URI
            val authority = "${appCtx.packageName}.fileprovider"
            println("DEBUG: shareMedia - FileProvider authority: $authority")
            println("DEBUG: shareMedia - Attempting to get URI for file: ${file.absolutePath}")
            
            val uri = try {
                FileProvider.getUriForFile(appCtx, authority, file)
            } catch (e: IllegalArgumentException) {
                println("ERROR: FileProvider.getUriForFile failed: ${e.message}")
                println("ERROR: File path: ${file.absolutePath}")
                println("ERROR: File exists: ${file.exists()}")
                println("ERROR: File canRead: ${file.canRead()}")
                throw Exception(
                    "FileProvider configuration error: ${e.message}\n" +
                    "File path: ${file.absolutePath}\n" +
                    "Please ensure file_paths.xml includes the directory containing this file.",
                    e
                )
            }
            
            // Determine MIME type (use parameter if provided, otherwise detect)
            val finalMimeType = mimeType.ifBlank {
                appCtx.contentResolver.getType(uri) ?: when {
                    file.extension.equals("mp4", ignoreCase = true) -> "video/mp4"
                    file.extension.equals("jpg", ignoreCase = true) ||
                    file.extension.equals("jpeg", ignoreCase = true) -> "image/jpeg"
                    file.extension.equals("png", ignoreCase = true) -> "image/png"
                    else -> "*/*"
                }
            }
            
            // Create share intent
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = finalMimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            // Create chooser with user-friendly title
            val chooserTitle = title.ifBlank { "Select WhatsApp or any app to share the file." }
            val chooser = Intent.createChooser(shareIntent, chooserTitle)
            
            // Verify at least one app can handle the intent
            val resolveInfo = activityCtx.packageManager.resolveActivity(
                chooser,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )
            
            if (resolveInfo == null) {
                throw android.content.ActivityNotFoundException(
                    "No apps available to share media.\n" +
                    "Please install WhatsApp, Gmail, or another sharing app."
                )
            }
            
            // Start share activity (using Activity context - no FLAG_ACTIVITY_NEW_TASK needed)
            activityCtx.startActivity(chooser)
            
            println("Share dialog opened successfully for: ${file.name}")
            
        } catch (e: android.content.ActivityNotFoundException) {
            throw Exception(
                "Cannot share media: No apps available.\n" +
                "Please install WhatsApp, Gmail, Messages, or another sharing app.",
                e
            )
        } catch (e: java.io.FileNotFoundException) {
            throw e // Re-throw as-is
        } catch (e: SecurityException) {
            throw e // Re-throw as-is
        } catch (e: Exception) {
            throw Exception(
                "Failed to share media: ${e.message}\n" +
                "File: ${file.name}\n" +
                "Please try again or contact support.",
                e
            )
        }
    }
}

actual suspend fun shareToWhatsApp(path: String) {
    withContext(Dispatchers.Main) {
        try {
            val ctx = appContext ?: return@withContext
            val file = File(path)
            if (!file.exists()) return@withContext
            
            val uri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (file.extension.equals("mp4", true)) "video/*" else "image/*"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            if (intent.resolveActivity(ctx.packageManager) != null) {
                ctx.startActivity(intent)
            } else {
                // WhatsApp not installed, use generic share
                shareFile(path)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

actual suspend fun shareToEmail(path: String, filename: String) {
    withContext(Dispatchers.Main) {
        try {
            val ctx = appContext ?: return@withContext
            val file = File(path)
            if (!file.exists()) return@withContext
            
            val uri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (file.extension.equals("mp4", true)) "video/*" else "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Shared: $filename")
                putExtra(Intent.EXTRA_TEXT, "Please find attached: $filename")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooser = Intent.createChooser(intent, "Send email")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

actual suspend fun shareToTelegram(path: String) {
    withContext(Dispatchers.Main) {
        try {
            val ctx = appContext ?: return@withContext
            val file = File(path)
            if (!file.exists()) return@withContext
            
            val uri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (file.extension.equals("mp4", true)) "video/*" else "image/*"
                setPackage("org.telegram.messenger")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            if (intent.resolveActivity(ctx.packageManager) != null) {
                ctx.startActivity(intent)
            } else {
                // Telegram not installed, use generic share
                shareFile(path)
            }
        } catch (e: Exception) {
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
private val recordingPathRef = AtomicReference<String?>(null)
private val activeRecordingRef = AtomicReference<Recording?>(null)

actual suspend fun startVideoRecording(cameraManager: CameraController, fileManager: FileManager, filename: String, flipHorizontal: Boolean): String? {
    return withContext(Dispatchers.Main) {
        try {
            val ctx = appContext ?: return@withContext null
            val mediaDir = fileManager.getMediaDirectory()
            val videoFile = File(mediaDir, filename)
            val parentDir = videoFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs()
            }
            
            val videoPath = videoFile.absolutePath
            
            // Cast to CameraManagerImpl to access video recording methods
            val androidCameraManager = cameraManager as? com.ram.orai.orais.CameraManagerImpl
            if (androidCameraManager == null) {
                println("ERROR: CameraManager is not CameraManagerImpl, cannot record video")
                return@withContext null
            }
            
            // Start actual video recording using CameraX VideoCapture
            val recording = androidCameraManager.startVideoRecording(videoFile)
            if (recording != null) {
                recordingPathRef.set(videoPath)
                activeRecordingRef.set(recording)
                println("✅ Video recording started: $videoPath (flip=$flipHorizontal)")
                videoPath
            } else {
                println("ERROR: Failed to start video recording")
                null
            }
        } catch (e: Exception) {
            println("ERROR: Exception starting video recording: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}

actual suspend fun stopVideoRecording(cameraManager: CameraController): String? {
    return withContext(Dispatchers.Main) {
        try {
            val videoPath = recordingPathRef.getAndSet(null)
            val recording = activeRecordingRef.getAndSet(null)
            
            if (recording != null && videoPath != null) {
                // Cast to CameraManagerImpl to access video recording methods
                val androidCameraManager = cameraManager as? com.ram.orai.orais.CameraManagerImpl
                if (androidCameraManager != null) {
                    androidCameraManager.stopVideoRecording()
                } else {
                    // Fallback: stop recording directly
                    try {
                        recording.stop()
                    } catch (e: Exception) {
                        println("ERROR: Failed to stop recording: ${e.message}")
                    }
                }
                
                // Wait for file to be finalized (CameraX needs time to finalize the video)
                println("Waiting for video file to be finalized...")
                var attempts = 0
                val maxAttempts = 10
                var fileReady = false
                
                while (attempts < maxAttempts && !fileReady) {
                    delay(500)
                    attempts++
                    val file = File(videoPath)
                    if (file.exists() && file.length() > 0) {
                        // Check if file size is stable (not still being written)
                        val size1 = file.length()
                        delay(200)
                        val size2 = file.length()
                        if (size1 == size2 && size1 > 0) {
                            fileReady = true
                            println("✅ Video file ready: $videoPath (${file.length()} bytes) after ${attempts * 500}ms")
                        } else {
                            println("Video file still being written: $size1 -> $size2 bytes")
                        }
                    } else {
                        println("Waiting for video file... (attempt $attempts/$maxAttempts)")
                    }
                }
                
                val file = File(videoPath)
                if (file.exists() && file.length() > 0) {
                    println("✅ Video recording stopped: $videoPath (${file.length()} bytes)")
                    videoPath
                } else {
                    println("WARNING: Video file does not exist or is empty after waiting: $videoPath")
                    null
                }
            } else {
                println("WARNING: No active recording to stop")
                null
            }
        } catch (e: Exception) {
            println("ERROR: Exception stopping video recording: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}

actual fun playVideo(path: String) {
    // This function is kept for compatibility but not used
    // Video playback is now handled by BuiltInVideoPlayer
}

actual suspend fun loadVideoFrames(videoPath: String, onFrame: (ImageBitmap) -> Unit) {
    withContext(Dispatchers.IO) {
        var retriever: MediaMetadataRetriever? = null
        try {
            val file = File(videoPath)
            println("Loading video: $videoPath")
            println("File exists: ${file.exists()}")
            println("File size: ${file.length()} bytes")
            
            if (!file.exists()) {
                println("ERROR: Video file does not exist: $videoPath")
                throw Exception("Video file does not exist")
            }
            
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoPath)
            
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 30f
            
            println("Video duration: ${duration}ms, Frame rate: $frameRate")
            
            val frameDelay = if (frameRate > 0) (1000.0 / frameRate).toLong().coerceAtLeast(33) else 33L
            var frameNumber = 0
            var currentTime = 0L
            
            while (currentTime < duration) {
                try {
                    val bitmap = retriever.getFrameAtTime(currentTime * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    
                    if (bitmap != null) {
                        val imageBitmap = bitmap.asImageBitmap()
                        
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
                        
                        currentTime += frameDelay
                    } else {
                        break
                    }
                } catch (e: Exception) {
                    println("Error processing frame $frameNumber: ${e.message}")
                    e.printStackTrace()
                    break
                }
            }
            
            println("Video playback finished. Total frames: $frameNumber")
        } catch (e: Exception) {
            println("ERROR loading video: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            try {
                retriever?.release()
            } catch (e: Exception) {
                println("Error cleaning up video resources: ${e.message}")
            }
        }
    }
}

@Composable
actual fun VideoPlayerView(videoPath: String, modifier: Modifier) {
    val ctx = appContext ?: return
    var currentPath by remember { mutableStateOf<String?>(null) }
    
    AndroidView(
        factory = { context ->
            VideoView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                // Set video path
                val file = File(videoPath)
                if (file.exists()) {
                    setVideoPath(videoPath)
                    currentPath = videoPath
                } else {
                    // Try to get URI from MediaStore if file path doesn't exist
                    try {
                        val uri = android.net.Uri.parse(videoPath)
                        setVideoURI(uri)
                        currentPath = videoPath
                    } catch (e: Exception) {
                        println("ERROR: Failed to set video path: ${e.message}")
                    }
                }
                
                // Add media controller for playback controls
                val mediaController = MediaController(context)
                mediaController.setAnchorView(this)
                setMediaController(mediaController)
                
                // Auto-start playback
                start()
                
                println("VideoView initialized for: $videoPath")
            }
        },
        modifier = modifier,
        update = { videoView ->
            // Update video view if path changes
            if (currentPath != videoPath) {
                val file = File(videoPath)
                if (file.exists()) {
                    videoView.setVideoPath(videoPath)
                    videoView.start()
                    currentPath = videoPath
                } else {
                    try {
                        val uri = android.net.Uri.parse(videoPath)
                        videoView.setVideoURI(uri)
                        videoView.start()
                        currentPath = videoPath
                    } catch (e: Exception) {
                        println("ERROR: Failed to update video path: ${e.message}")
                    }
                }
            }
        }
    )
}

actual fun flipImageBitmapHorizontally(bitmap: ImageBitmap): ImageBitmap {
    return try {
        // Convert ImageBitmap to Android Bitmap
        val width = bitmap.width
        val height = bitmap.height
        
        // Create a mutable bitmap
        val androidBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(androidBitmap)
        
        // Draw the original bitmap
        val paint = android.graphics.Paint()
        canvas.drawBitmap(bitmap.asAndroidBitmap(), 0f, 0f, paint)
        
        // Flip horizontally using Matrix
        val matrix = Matrix().apply {
            postScale(-1f, 1f, width / 2f, height / 2f)
        }
        
        val flippedBitmap = Bitmap.createBitmap(androidBitmap, 0, 0, width, height, matrix, true)
        androidBitmap.recycle()
        
        flippedBitmap.asImageBitmap()
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
        // Convert ImageData to Android Bitmap using the same method as FileManager
        // This ensures consistency and avoids color conversion issues
        // Explicitly use sRGB color space to avoid color shifts
        val androidBitmap = Bitmap.createBitmap(
            width, 
            height, 
            Bitmap.Config.ARGB_8888,
            true, // hasAlpha
            ColorSpace.get(ColorSpace.Named.SRGB) // Use sRGB color space
        )
        val bytes = imageData.bytes
        var idx = 0
        
        // Copy pixels from ImageData to Android Bitmap (ARGB format)
        // Use the exact same method as FileManager.convertImageDataToBitmap
        for (y in 0 until height) {
            for (x in 0 until width) {
                val a = bytes[idx++].toInt() and 0xFF
                val r = bytes[idx++].toInt() and 0xFF
                val g = bytes[idx++].toInt() and 0xFF
                val b = bytes[idx++].toInt() and 0xFF
                val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
                androidBitmap.setPixel(x, y, argb)
            }
        }
        
        val canvas = Canvas(androidBitmap)
        
        // Create paint objects for drawing
        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        
        val fillPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        val textPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        
        val bgPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            alpha = 200 // Semi-transparent black background
        }
        
        // Draw each detection
        detections.forEach { tooth ->
            val box = tooth.boundingBox
            
            // Map normalized coordinates (0-1) to pixel coordinates
            val normalizedLeft = if (flipHorizontal) 1f - box.right else box.left
            val normalizedRight = if (flipHorizontal) 1f - box.left else box.right
            
            val left = normalizedLeft * width
            val top = box.top * height
            val right = normalizedRight * width
            val bottom = box.bottom * height
            
            // Determine box color
            // Yellow for FDI teeth, Green for "Normal" condition only, Red for other conditions
            val boxColor = when {
                tooth.toothNumber > 0 -> android.graphics.Color.YELLOW // FDI teeth in yellow
                tooth.condition == "Normal" -> android.graphics.Color.GREEN // Normal condition in green
                tooth.condition != null -> android.graphics.Color.RED // Other conditions in red
                else -> android.graphics.Color.GREEN // Default to green
            }
            
            boxPaint.color = boxColor
            fillPaint.color = boxColor
            fillPaint.alpha = 50 // Semi-transparent fill
            
            // Draw filled background
            canvas.drawRect(left, top, right, bottom, fillPaint)
            
            // Draw box border
            canvas.drawRect(left, top, right, bottom, boxPaint)
            
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
                val textBounds = android.graphics.Rect()
                textPaint.getTextBounds(labelText, 0, labelText.length, textBounds)
                val textX = left + 8f
                val textY = top - 8f
                
                canvas.drawRect(
                    textX - 4f,
                    textY - textBounds.height() - 4f,
                    textX + textBounds.width() + 4f,
                    textY + 4f,
                    bgPaint
                )
                
                // Draw text
                canvas.drawText(labelText, textX, textY, textPaint)
            }
        }
        
        // Convert Android Bitmap back to ImageData (ARGB format)
        // Use getPixel() to read pixels one by one to avoid any color space conversion issues
        val resultBytes = ByteArray(width * height * 4)
        var resultIdx = 0
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = androidBitmap.getPixel(x, y)
                resultBytes[resultIdx++] = ((argb shr 24) and 0xFF).toByte() // Alpha
                resultBytes[resultIdx++] = ((argb shr 16) and 0xFF).toByte() // Red
                resultBytes[resultIdx++] = ((argb shr 8) and 0xFF).toByte()  // Green
                resultBytes[resultIdx++] = (argb and 0xFF).toByte()         // Blue
            }
        }
        
        // Clean up Android bitmap
        if (!androidBitmap.isRecycled) {
            androidBitmap.recycle()
        }
        
        println("Successfully drew ${detections.size} detection boxes on ImageData: ${width}x${height}")
        
        ImageData(width, height, imageData.rotationDegrees, resultBytes)
    } catch (e: Exception) {
        println("Error drawing detections on ImageData: ${e.message}")
        e.printStackTrace()
        null // Return null on error, will use original ImageData
    }
}

// Helper extension to convert ImageBitmap to Android Bitmap
private fun ImageBitmap.asAndroidBitmap(): Bitmap {
    // This is a simplified conversion - in production, you'd use proper pixel access
    val width = this.width
    val height = this.height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    
    // Copy pixels from ImageBitmap to Bitmap
    val pixelMap = this.toPixelMap()
    for (y in 0 until height) {
        for (x in 0 until width) {
            val color = pixelMap[x, y]
            val argb = (
                ((color.alpha * 255f).toInt() and 0xFF) shl 24 or
                ((color.red * 255f).toInt() and 0xFF) shl 16 or
                ((color.green * 255f).toInt() and 0xFF) shl 8 or
                ((color.blue * 255f).toInt() and 0xFF)
            )
            bitmap.setPixel(x, y, argb)
        }
    }
    
    return bitmap
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

