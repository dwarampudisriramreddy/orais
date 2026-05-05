package com.ram.orai.orais

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

actual class FileManager {
    // TODO: Pass context via dependency injection

    actual fun saveImage(imageData: ImageData, filename: String): Boolean {
        return try {
            val ctx = getAppContext() ?: return false
            
            // ✅ CRITICAL FIX: Convert ImageData bytes to Bitmap, then encode as PNG
            // ImageData.bytes contains raw RGBA pixel data, NOT PNG-encoded data
            val bitmap = convertImageDataToBitmap(imageData) ?: run {
                println("ERROR: Failed to convert ImageData to Bitmap")
                return false
            }
            
            println("Converting Bitmap to PNG: ${bitmap.width}x${bitmap.height}")
            
            // Encode Bitmap as PNG bytes
            val pngBytes = ByteArrayOutputStream().use { outputStream ->
                val success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                if (!success) {
                    println("ERROR: Bitmap.compress() returned false")
                    return false
                }
                outputStream.flush()
                outputStream.toByteArray()
            }
            
            // Verify PNG header (89 50 4E 47 0D 0A 1A 0A)
            if (pngBytes.size >= 8) {
                val header = pngBytes.take(8)
                val isValidPng = header[0] == 0x89.toByte() && 
                                header[1] == 0x50.toByte() && 
                                header[2] == 0x4E.toByte() && 
                                header[3] == 0x47.toByte()
                if (isValidPng) {
                    println("✅ PNG header verified: Valid PNG file (${pngBytes.size} bytes)")
                } else {
                    println("WARNING: PNG header verification failed")
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (API 29+): Use MediaStore - save to external storage only
                println("📱 Using MediaStore API for Android 10+ (API ${Build.VERSION.SDK_INT})")
                println("📂 Target directory: ${Environment.DIRECTORY_PICTURES}/Orai")
                
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Orai")
                    put(MediaStore.MediaColumns.IS_PENDING, 1) // Mark as pending during write
                }
                
                println("🔄 Attempting to insert into MediaStore...")
                val uri = try {
                    ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                } catch (e: Exception) {
                    println("❌ EXCEPTION during MediaStore insert: ${e.javaClass.simpleName}")
                    println("❌ Exception message: ${e.message}")
                    e.printStackTrace()
                    null
                }
                
                if (uri != null) {
                    println("✅ MediaStore URI created: $uri")
                    
                    // Write image data to MediaStore
                    try {
                    ctx.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(pngBytes)
                            outputStream.flush()
                            println("✅ Image data written to MediaStore (${pngBytes.size} bytes)")
                        } ?: run {
                            println("❌ ERROR: Failed to open output stream for MediaStore URI")
                            return false
                        }
                    } catch (e: Exception) {
                        println("❌ ERROR writing to MediaStore: ${e.message}")
                        e.printStackTrace()
                        return false
                    }
                    
                    // Mark as not pending to make it visible in gallery
                    val updateValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    val updated = try {
                        ctx.contentResolver.update(uri, updateValues, null, null)
                    } catch (e: Exception) {
                        println("⚠️ WARNING: Failed to update IS_PENDING flag: ${e.message}")
                        0
                    }
                    
                    if (updated > 0) {
                        println("✅ Image saved to MediaStore gallery: $uri")
                        
                        // Force MediaStore refresh to make image immediately visible
                        println("🔄 Refreshing MediaStore to make image visible in gallery...")
                        try {
                            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                            val oraiDir = File(picturesDir, "Orai")
                            val mediaStoreFile = File(oraiDir, filename)
                            
                            if (mediaStoreFile.exists()) {
                                println("📂 Found MediaStore file: ${mediaStoreFile.absolutePath}")
                                println("📊 File size: ${mediaStoreFile.length()} bytes")
                                
                                // Scan the file to make it visible in gallery
                                MediaScannerConnection.scanFile(
                                    ctx,
                                    arrayOf(mediaStoreFile.absolutePath),
                                    arrayOf("image/png")
                                ) { path, scannedUri ->
                                    println("✅ MediaScanner scan completed for: $path")
                                    if (scannedUri != null) {
                                        println("✅ Scanned URI: $scannedUri")
                                    }
                                }
                            } else {
                                println("⚠️ WARNING: MediaStore file not found at expected path: ${mediaStoreFile.absolutePath}")
                            }
                        } catch (e: Exception) {
                            println("⚠️ WARNING: Failed to refresh MediaStore: ${e.message}")
                            e.printStackTrace()
                        }
                        
                        println("=".repeat(60))
                        println("✅ SUCCESS: Image saved to external storage (Pictures/Orai)!")
                        println("=".repeat(60))
                        return true
                    } else {
                        println("❌ ERROR: Failed to update IS_PENDING flag")
                        return false
                    }
                } else {
                    println("❌ ERROR: Failed to insert into MediaStore")
                    return false
                }
            } else {
                // Android 9 and below: Save to external storage (Pictures/Orai)
                println("📱 Using file system API for Android 9 and below (API ${Build.VERSION.SDK_INT})")
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val oraiDir = File(picturesDir, "Orai")
                if (!oraiDir.exists()) {
                    val created = oraiDir.mkdirs()
                    println("${if (created) "✅" else "❌"} Orai directory created: $created")
                }
                
                val file = File(oraiDir, filename)
                println("📂 Saving to external storage: ${file.absolutePath}")
                
                try {
                    FileOutputStream(file).use { fos ->
                        fos.write(pngBytes)
                        fos.flush()
                    }
                    println("✅ Image written to external storage (${pngBytes.size} bytes)")
                    println("📊 File size: ${file.length()} bytes")
                } catch (e: Exception) {
                    println("❌ ERROR writing file: ${e.message}")
                    e.printStackTrace()
                    return false
                }
                
                // Register with MediaStore for gallery visibility
                println("🔄 Registering image with MediaStore...")
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DATA, file.absolutePath)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.TITLE, filename)
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                }
                
                try {
                    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        println("✅ Image registered with MediaStore: $uri")
                        
                        // Force MediaStore refresh to make image immediately visible
                        println("🔄 Refreshing MediaStore to make image visible in gallery...")
                        try {
                            val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                            val fileUri = Uri.fromFile(file)
                            scanIntent.data = fileUri
                            ctx.sendBroadcast(scanIntent)
                            println("✅ Media scanner broadcast sent")
                            
                            // Also use MediaScannerConnection
                            MediaScannerConnection.scanFile(
                                ctx,
                                arrayOf(file.absolutePath),
                                arrayOf("image/png")
                            ) { path, scannedUri ->
                                println("✅ MediaScanner scan completed for: $path")
                                println("✅ MediaStore URI: $scannedUri")
                            }
                            println("✅ MediaStore refresh initiated")
                        } catch (e: Exception) {
                            println("⚠️ WARNING: Failed to refresh MediaStore: ${e.message}")
                            e.printStackTrace()
                        }
                        
                        println("=".repeat(60))
                        println("✅ SUCCESS: Image saved to external storage (Pictures/Orai)!")
                        println("=".repeat(60))
                        return true
                    } else {
                        println("❌ ERROR: Failed to register with MediaStore")
                        return false
                    }
                } catch (e: Exception) {
                    println("❌ ERROR registering with MediaStore: ${e.message}")
                    e.printStackTrace()
                    return false
                }
            }
        } catch (e: Exception) {
            println("ERROR: Exception saving image: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Convert ImageData (raw RGBA bytes) to Android Bitmap
     * ImageData.bytes format: ARGB (Alpha, Red, Green, Blue) per pixel
     */
    private fun convertImageDataToBitmap(imageData: ImageData): Bitmap? {
        return try {
            val width = imageData.width
            val height = imageData.height
            val bytes = imageData.bytes
            
            if (bytes.size != width * height * 4) {
                println("ERROR: ImageData size mismatch. Expected ${width * height * 4} bytes, got ${bytes.size}")
                return null
            }
            
            // Create Bitmap with ARGB_8888 format
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            var idx = 0
            
            for (y in 0 until height) {
                for (x in 0 until width) {
                    // ImageData bytes are in ARGB format
                    val a = bytes[idx++].toInt() and 0xFF
                    val r = bytes[idx++].toInt() and 0xFF
                    val g = bytes[idx++].toInt() and 0xFF
                    val b = bytes[idx++].toInt() and 0xFF
                    
                    // Combine into ARGB int
                    val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
                    bitmap.setPixel(x, y, argb)
                }
            }
            
            println("✅ Converted ImageData to Bitmap: ${bitmap.width}x${bitmap.height}")
            bitmap
        } catch (e: Exception) {
            println("ERROR: Exception converting ImageData to Bitmap: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    actual fun saveVideo(videoPath: String, filename: String): VideoSaveResult {
        println("=".repeat(60))
        println("🔍 DEBUG: Starting saveVideo()")
        println("📁 Video path: $videoPath")
        println("📝 Filename: $filename")
        println("📱 Android version: ${Build.VERSION.SDK_INT}")
        
        val errorDetails = mutableListOf<String>()
        try {
            val ctx = getAppContext()
            if (ctx == null) {
                val error = "Context is null, cannot save video"
                println("❌ ERROR: $error")
                errorDetails.add("❌ $error")
                errorDetails.add("💡 This should never happen - context should be set in MainActivity")
                return VideoSaveResult(
                    success = false,
                    errorMessage = "Failed to save video: Context unavailable",
                    errorDetails = errorDetails
                )
            }
            println("✅ Context obtained: ${ctx.packageName}")
            
            val src = File(videoPath)
            println("📂 Source file path: ${src.absolutePath}")
            println("📂 Source file exists: ${src.exists()}")
            println("📂 Source file can read: ${if (src.exists()) src.canRead() else "N/A"}")
            println("📂 Source file can write: ${if (src.exists()) src.canWrite() else "N/A"}")
            println("📂 Source file is file: ${if (src.exists()) src.isFile else "N/A"}")
            println("📂 Source file is directory: ${if (src.exists()) src.isDirectory else "N/A"}")
            
            if (!src.exists()) {
                val error = "Source video file does not exist: $videoPath"
                println("❌ ERROR: $error")
                errorDetails.add("❌ $error")
                errorDetails.add("💡 Possible causes:")
                errorDetails.add("   - File was deleted before save")
                errorDetails.add("   - File path is incorrect")
                errorDetails.add("   - File is in a different location")
                return VideoSaveResult(
                    success = false,
                    errorMessage = "Video file not found",
                    errorDetails = errorDetails,
                    filePath = videoPath
                )
            }
            
            val fileSize = src.length()
            println("📊 Source file size: $fileSize bytes (${fileSize / 1024.0 / 1024.0} MB)")
            
            if (fileSize == 0L) {
                val error = "Source video file is empty (0 bytes)"
                println("❌ ERROR: $error")
                errorDetails.add("❌ $error")
                errorDetails.add("💡 Possible causes:")
                errorDetails.add("   - Video recording failed")
                errorDetails.add("   - File was not fully written")
                errorDetails.add("   - File is corrupted")
                return VideoSaveResult(
                    success = false,
                    errorMessage = "Video file is empty",
                    errorDetails = errorDetails,
                    filePath = videoPath
                )
            }
            
            if (fileSize < 1024) {
                println("⚠️ WARNING: Source video file is very small (${fileSize} bytes)")
                println("💡 This might indicate the file is incomplete or corrupted")
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (API 29+): Use MediaStore
                println("📱 Using MediaStore API for Android 10+ (API ${Build.VERSION.SDK_INT})")
                println("📂 Target directory: ${Environment.DIRECTORY_MOVIES}/Orai")
                
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Orai")
                    put(MediaStore.MediaColumns.IS_PENDING, 1) // Mark as pending during write
                }
                
                println("📝 ContentValues created:")
                println("   - DISPLAY_NAME: ${contentValues.getAsString(MediaStore.MediaColumns.DISPLAY_NAME)}")
                println("   - MIME_TYPE: ${contentValues.getAsString(MediaStore.MediaColumns.MIME_TYPE)}")
                println("   - RELATIVE_PATH: ${contentValues.getAsString(MediaStore.MediaColumns.RELATIVE_PATH)}")
                println("   - IS_PENDING: ${contentValues.getAsInteger(MediaStore.MediaColumns.IS_PENDING)}")
                
                println("🔄 Attempting to insert into MediaStore...")
                val uri = try {
                    ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                } catch (e: Exception) {
                    println("❌ EXCEPTION during MediaStore insert: ${e.javaClass.simpleName}")
                    println("❌ Exception message: ${e.message}")
                    println("❌ Stack trace:")
                    e.printStackTrace()
                    null
                }
                
                if (uri != null) {
                    println("✅ MediaStore URI created successfully: $uri")
                    println("📋 URI scheme: ${uri.scheme}")
                    println("📋 URI authority: ${uri.authority}")
                    println("📋 URI path: ${uri.path}")
                    
                    try {
                        println("🔄 Opening output stream for MediaStore URI...")
                        val outputStream = ctx.contentResolver.openOutputStream(uri)
                        if (outputStream != null) {
                            println("✅ Output stream opened successfully")
                            try {
                                println("🔄 Opening input stream from source file...")
                        src.inputStream().use { inputStream ->
                                    println("✅ Input stream opened, starting copy...")
                                    val startTime = System.currentTimeMillis()
                                    val bytesCopied = inputStream.copyTo(outputStream)
                                    outputStream.flush()
                                    val duration = System.currentTimeMillis() - startTime
                                    println("✅ Copied $bytesCopied bytes to MediaStore in ${duration}ms")
                                    println("📊 Copy speed: ${bytesCopied / 1024.0 / duration * 1000} KB/s")
                                    
                                    if (bytesCopied != fileSize) {
                                        println("⚠️ WARNING: Bytes copied ($bytesCopied) != file size ($fileSize)")
                                    }
                                }
                            } catch (e: Exception) {
                                println("❌ EXCEPTION during file copy:")
                                println("   - Type: ${e.javaClass.simpleName}")
                                println("   - Message: ${e.message}")
                                println("   - Stack trace:")
                                e.printStackTrace()
                                throw e
                            } finally {
                                outputStream.close()
                                println("✅ Output stream closed")
                            }
                        } else {
                            println("❌ ERROR: Failed to open output stream for MediaStore URI")
                            println("💡 Possible causes:")
                            println("   - Insufficient permissions")
                            println("   - Storage is full")
                            println("   - MediaStore URI is invalid")
                            println("   - File system error")
                            
                            // Delete the MediaStore entry if write failed
                            println("🔄 Attempting to delete MediaStore entry...")
                            try {
                                val deleted = ctx.contentResolver.delete(uri, null, null)
                                println("${if (deleted > 0) "✅" else "⚠️"} MediaStore entry deleted: $deleted rows")
                            } catch (e: Exception) {
                                println("❌ WARNING: Failed to delete MediaStore entry: ${e.message}")
                                e.printStackTrace()
                            }
                            errorDetails.add("❌ Failed to open output stream for MediaStore URI")
                            errorDetails.add("💡 Possible causes:")
                            errorDetails.add("   - Insufficient permissions")
                            errorDetails.add("   - Storage is full")
                            errorDetails.add("   - MediaStore URI is invalid")
                            errorDetails.add("   - File system error")
                            return VideoSaveResult(
                                success = false,
                                errorMessage = "Failed to write video to gallery",
                                errorDetails = errorDetails,
                                filePath = videoPath,
                                mediaStoreUri = uri.toString()
                            )
                        }
                        
                        // Mark as not pending (make it visible in gallery)
                        println("🔄 Marking video as not pending (making visible in gallery)...")
                        val updateValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_PENDING, 0)
                        }
                        val updated = ctx.contentResolver.update(uri, updateValues, null, null)
                        println("${if (updated > 0) "✅" else "⚠️"} MediaStore update result: $updated rows updated")
                        
                        if (updated > 0) {
                            println("✅ Video saved to MediaStore gallery: $uri")
                            
                            // Force MediaStore refresh to make video immediately visible
                            println("🔄 Refreshing MediaStore to make video visible in gallery...")
                            try {
                                // On Android 10+, get the file path from MediaStore
                                // The file is saved to Movies/Orai directory
                                val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                                val oraiDir = File(moviesDir, "Orai")
                                val mediaStoreFile = File(oraiDir, filename)
                                
                                if (mediaStoreFile.exists()) {
                                    println("📂 Found MediaStore file: ${mediaStoreFile.absolutePath}")
                                    println("📊 File size: ${mediaStoreFile.length()} bytes")
                                    
                                    // Scan the file to make it visible in gallery
                                    MediaScannerConnection.scanFile(
                                        ctx,
                                        arrayOf(mediaStoreFile.absolutePath),
                                        arrayOf("video/mp4")
                                    ) { path, scannedUri ->
                                        println("✅ MediaScanner scan completed for: $path")
                                        if (scannedUri != null) {
                                            println("✅ Scanned URI: $scannedUri")
                                        }
                                    }
                                } else {
                                    println("⚠️ WARNING: MediaStore file not found at expected location: ${mediaStoreFile.absolutePath}")
                                    println("💡 Trying to scan source file as fallback...")
                                    // Fallback: scan the source file
                                    if (src.exists()) {
                                        MediaScannerConnection.scanFile(
                                            ctx,
                                            arrayOf(src.absolutePath),
                                            arrayOf("video/mp4")
                                        ) { path, scannedUri ->
                                            println("✅ MediaScanner scan completed (fallback): $path")
                                        }
                                    }
                                }
                                
                                // Also try to open the MediaStore URI to trigger indexing
                                try {
                                    ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                                        println("✅ Successfully opened MediaStore file descriptor")
                                        // This helps MediaStore index the file
                                    }
                                } catch (e: Exception) {
                                    println("⚠️ WARNING: Could not open MediaStore file descriptor: ${e.message}")
                                }
                                
                                println("✅ MediaStore refresh initiated")
                                println("✅ Video should now be visible in gallery app")
                                println("💡 If video doesn't appear immediately:")
                                println("   1. Close and reopen gallery app")
                                println("   2. Wait a few seconds for MediaStore to index")
                                println("   3. Check Movies/Orai folder in file manager")
                            } catch (e: Exception) {
                                println("⚠️ WARNING: Failed to refresh MediaStore: ${e.message}")
                                println("💡 Video is saved but might take a moment to appear in gallery")
                                e.printStackTrace()
                            }
                        } else {
                            println("⚠️ WARNING: MediaStore update returned 0 rows")
                            println("💡 Video might not be visible in gallery")
                            println("🔄 Attempting MediaScanner scan as fallback...")
                            try {
                                if (src.exists()) {
                                    MediaScannerConnection.scanFile(
                                        ctx,
                                        arrayOf(src.absolutePath),
                                        arrayOf("video/mp4")
                                    ) { path, scannedUri ->
                                        println("✅ MediaScanner scan completed (fallback): $path")
                                    }
                                }
                            } catch (e: Exception) {
                                println("⚠️ WARNING: MediaScanner fallback also failed: ${e.message}")
                            }
                        }
                        
                        // Also keep a copy in app directory for internal access
                        println("🔄 Saving copy to app directory...")
                    val dir = File(getMediaDirectory())
                        println("📂 App directory: ${dir.absolutePath}")
                        println("📂 App directory exists: ${dir.exists()}")
                        if (!dir.exists()) {
                            val created = dir.mkdirs()
                            println("${if (created) "✅" else "❌"} App directory created: $created")
                        }
                    val dest = File(dir, filename)
                        println("📂 Destination: ${dest.absolutePath}")
                        try {
                    src.copyTo(dest, overwrite = true)
                            println("✅ Video also saved to app directory: ${dest.absolutePath}")
                            println("📊 App directory file size: ${dest.length()} bytes")
                        } catch (e: Exception) {
                            println("⚠️ WARNING: Failed to save copy to app directory: ${e.message}")
                            e.printStackTrace()
                            // Don't fail the whole operation if app directory save fails
                        }
                        
                        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                        val oraiDir = File(moviesDir, "Orai")
                        val mediaStoreFile = File(oraiDir, filename)
                        val savedPath = if (mediaStoreFile.exists()) mediaStoreFile.absolutePath else dest.absolutePath
                        
                        println("=".repeat(60))
                        println("✅ SUCCESS: Video saved to gallery!")
                        println("=".repeat(60))
                        return VideoSaveResult(
                            success = true,
                            filePath = savedPath,
                            mediaStoreUri = uri.toString(),
                            errorDetails = if (errorDetails.isNotEmpty()) errorDetails else emptyList()
                        )
                    } catch (e: SecurityException) {
                        val error = "Security exception during video save: ${e.message}"
                        println("❌ SECURITY EXCEPTION during video save:")
                        println("   - Message: ${e.message}")
                        errorDetails.add("❌ $error")
                        errorDetails.add("💡 This usually means missing permissions")
                        errorDetails.add("💡 Required permissions:")
                        errorDetails.add("   - WRITE_EXTERNAL_STORAGE (Android 9 and below)")
                        errorDetails.add("   - No storage permission needed for Android 10+ (scoped storage)")
                        errorDetails.add("   - But MediaStore insert might still require proper app configuration")
                        e.printStackTrace()
                        
                        // Try to delete the MediaStore entry
                        try {
                            ctx.contentResolver.delete(uri, null, null)
                            println("✅ Cleaned up MediaStore entry after security exception")
                        } catch (e2: Exception) {
                            println("⚠️ WARNING: Failed to delete MediaStore entry: ${e2.message}")
                        }
                        return VideoSaveResult(
                            success = false,
                            errorMessage = "Permission denied: Cannot save video to gallery",
                            errorDetails = errorDetails,
                            filePath = videoPath,
                            mediaStoreUri = uri.toString()
                        )
                    } catch (e: Exception) {
                        val error = "Exception copying video to MediaStore: ${e.javaClass.simpleName}: ${e.message}"
                        println("❌ EXCEPTION copying video to MediaStore:")
                        println("   - Type: ${e.javaClass.simpleName}")
                        println("   - Message: ${e.message}")
                        errorDetails.add("❌ $error")
                        println("   - Stack trace:")
                        e.printStackTrace()
                        
                        // Try to delete the MediaStore entry
                        println("🔄 Attempting to clean up MediaStore entry...")
                        try {
                            val deleted = ctx.contentResolver.delete(uri, null, null)
                            println("${if (deleted > 0) "✅" else "⚠️"} MediaStore entry deleted: $deleted rows")
                        } catch (e2: Exception) {
                            println("❌ WARNING: Failed to delete MediaStore entry: ${e2.message}")
                            e2.printStackTrace()
                        }
                        return VideoSaveResult(
                            success = false,
                            errorMessage = "Failed to save video: ${e.message}",
                            errorDetails = errorDetails,
                            filePath = videoPath,
                            mediaStoreUri = uri.toString()
                        )
                    }
                } else {
                    println("❌ ERROR: Failed to create MediaStore URI (insert returned null)")
                    println("💡 Possible causes:")
                    println("   1. Missing permissions (check AndroidManifest.xml)")
                    println("   2. Invalid RELATIVE_PATH")
                    println("   3. Storage is full")
                    println("   4. MediaStore service unavailable")
                    println("   5. App not properly configured for scoped storage")
                    println("🔍 Debugging steps:")
                    println("   - Check if app has required permissions in Settings")
                    println("   - Verify RELATIVE_PATH: ${Environment.DIRECTORY_MOVIES}/Orai")
                    println("   - Check available storage space")
                    println("   - Try restarting the device")
                    errorDetails.add("❌ Failed to create MediaStore URI (insert returned null)")
                    errorDetails.add("💡 Possible causes:")
                    errorDetails.add("   1. Missing permissions (check AndroidManifest.xml)")
                    errorDetails.add("   2. Invalid RELATIVE_PATH")
                    errorDetails.add("   3. Storage is full")
                    errorDetails.add("   4. MediaStore service unavailable")
                    errorDetails.add("   5. App not properly configured for scoped storage")
                    errorDetails.add("🔍 Debugging steps:")
                    errorDetails.add("   - Check if app has required permissions in Settings")
                    errorDetails.add("   - Verify RELATIVE_PATH: ${Environment.DIRECTORY_MOVIES}/Orai")
                    errorDetails.add("   - Check available storage space")
                    errorDetails.add("   - Try restarting the device")
                    return VideoSaveResult(
                        success = false,
                        errorMessage = "Failed to save video to gallery",
                        errorDetails = errorDetails,
                        filePath = videoPath
                    )
                }
            } else {
                // Android 9 and below: Use traditional file system
                println("📱 Using file system API for Android 9 and below (API ${Build.VERSION.SDK_INT})")
                val dir = File(getMediaDirectory())
                println("📂 App directory: ${dir.absolutePath}")
                if (!dir.exists()) {
                    val created = dir.mkdirs()
                    println("${if (created) "✅" else "❌"} App directory created: $created")
                }
                val dest = File(dir, filename)
                println("📂 Destination: ${dest.absolutePath}")
                
                try {
                src.copyTo(dest, overwrite = true)
                    println("✅ Video copied to: ${dest.absolutePath}")
                    println("📊 Destination file size: ${dest.length()} bytes")
                } catch (e: Exception) {
                    val error = "Error copying file: ${e.javaClass.simpleName}: ${e.message}"
                    println("❌ ERROR copying file: ${e.message}")
                    println("   - Type: ${e.javaClass.simpleName}")
                    errorDetails.add("❌ $error")
                    e.printStackTrace()
                    return VideoSaveResult(
                        success = false,
                        errorMessage = "Failed to copy video file",
                        errorDetails = errorDetails,
                        filePath = videoPath
                    )
                }
                
                // Register with MediaStore for gallery visibility
                println("🔄 Registering video with MediaStore...")
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DATA, dest.absolutePath)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.TITLE, filename)
                    put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                }
                println("📝 ContentValues:")
                println("   - DATA: ${contentValues.getAsString(MediaStore.Video.Media.DATA)}")
                println("   - MIME_TYPE: ${contentValues.getAsString(MediaStore.Video.Media.MIME_TYPE)}")
                
                try {
                    val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        println("✅ Video registered with MediaStore: $uri")
                        
                        // Force MediaStore refresh to make video immediately visible
                        println("🔄 Refreshing MediaStore to make video visible in gallery...")
                        try {
                            // Broadcast media scanner intent
                            val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                            val fileUri = Uri.fromFile(dest)
                            scanIntent.data = fileUri
                            ctx.sendBroadcast(scanIntent)
                            println("✅ Media scanner broadcast sent")
                            
                            // Also use MediaScannerConnection
                            MediaScannerConnection.scanFile(
                                ctx,
                                arrayOf(dest.absolutePath),
                                arrayOf("video/mp4")
                            ) { path, scannedUri ->
                                println("✅ MediaScanner scan completed for: $path")
                                println("✅ MediaStore URI: $scannedUri")
                            }
                            println("✅ MediaStore refresh initiated")
                        } catch (e: Exception) {
                            println("⚠️ WARNING: Failed to refresh MediaStore: ${e.message}")
                            e.printStackTrace()
                        }
                        
                        println("=".repeat(60))
                        println("✅ SUCCESS: Video saved to gallery!")
                        println("=".repeat(60))
                        return VideoSaveResult(
                            success = true,
                            filePath = dest.absolutePath,
                            mediaStoreUri = uri.toString()
                        )
                    } else {
                        val warning = "Video saved but MediaStore registration returned null"
                        println("⚠️ WARNING: $warning")
                        errorDetails.add("⚠️ $warning")
                        errorDetails.add("💡 Video file exists but might not appear in gallery")
                        println("💡 Video file exists but might not appear in gallery")
                        println("💡 Attempting MediaScanner scan as fallback...")
                        try {
                            MediaScannerConnection.scanFile(
                                ctx,
                                arrayOf(dest.absolutePath),
                                arrayOf("video/mp4")
                            ) { path, scannedUri ->
                                println("✅ MediaScanner scan completed (fallback): $path")
                            }
                        } catch (e: Exception) {
                            println("⚠️ WARNING: MediaScanner fallback also failed: ${e.message}")
                            errorDetails.add("⚠️ MediaScanner fallback failed: ${e.message}")
                        }
                        errorDetails.add("💡 Possible causes:")
                        errorDetails.add("   - Missing WRITE_EXTERNAL_STORAGE permission")
                        errorDetails.add("   - MediaStore service unavailable")
                        errorDetails.add("   - Invalid file path")
                        println("💡 Possible causes:")
                        println("   - Missing WRITE_EXTERNAL_STORAGE permission")
                        println("   - MediaStore service unavailable")
                        println("   - Invalid file path")
                        println("=".repeat(60))
                        println("⚠️ PARTIAL SUCCESS: Video saved to file system")
                        println("=".repeat(60))
                    return VideoSaveResult(
                        success = true, // File was saved, but gallery registration failed
                        filePath = dest.absolutePath,
                        errorMessage = "Video saved but may not appear in gallery",
                        errorDetails = errorDetails
                    )
            }
        } catch (e: Exception) {
                    val error = "Error registering with MediaStore: ${e.javaClass.simpleName}: ${e.message}"
                    println("❌ ERROR registering with MediaStore:")
                    println("   - Type: ${e.javaClass.simpleName}")
                    println("   - Message: ${e.message}")
                    errorDetails.add("❌ $error")
                    e.printStackTrace()
                    println("⚠️ Video file saved but MediaStore registration failed")
                    println("🔄 Attempting MediaScanner scan as fallback...")
                    try {
                        MediaScannerConnection.scanFile(
                            ctx,
                            arrayOf(dest.absolutePath),
                            arrayOf("video/mp4")
                        ) { path, scannedUri ->
                            println("✅ MediaScanner scan completed (fallback): $path")
                        }
                    } catch (e2: Exception) {
                        println("⚠️ WARNING: MediaScanner fallback also failed: ${e2.message}")
                        errorDetails.add("⚠️ MediaScanner fallback failed: ${e2.message}")
                    }
                    return VideoSaveResult(
                        success = true, // File was saved, but MediaStore registration failed
                        filePath = dest.absolutePath,
                        errorMessage = "Video saved but gallery registration failed",
                        errorDetails = errorDetails
                    )
                }
            }
        } catch (e: SecurityException) {
            val error = "Security exception saving video - missing permissions: ${e.message}"
            println("ERROR: $error")
            errorDetails.add("❌ $error")
            e.printStackTrace()
            return VideoSaveResult(
                success = false,
                errorMessage = "Permission denied: Cannot save video",
                errorDetails = errorDetails,
                filePath = videoPath
            )
        } catch (e: Exception) {
            val error = "Exception saving video: ${e.javaClass.simpleName}: ${e.message}"
            println("ERROR: Exception saving video: ${e.message}")
            println("ERROR: Exception type: ${e.javaClass.simpleName}")
            errorDetails.add("❌ $error")
            e.printStackTrace()
            return VideoSaveResult(
                success = false,
                errorMessage = "Failed to save video: ${e.message}",
                errorDetails = errorDetails,
                filePath = videoPath
            )
        }
    }

    actual fun listFiles(directory: String): List<FileInfo> {
        val ctx = getAppContext() ?: return emptyList()
        val files = mutableListOf<FileInfo>()
        
        // First, list files from the app's internal directory
        val dir = File(directory)
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.filter { file ->
                file.exists() && file.canRead() && file.isFile
            }?.forEach { file ->
                files.add(
                    FileInfo(
                        name = file.name,
                        path = file.absolutePath,
                        isVideo = file.extension.equals("mp4", true),
                        size = file.length(),
                        lastModified = file.lastModified()
                    )
                )
            }
        }
        
        // Also query MediaStore for videos and images saved via MediaStore
        try {
            // Query videos from Movies/Orai
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.RELATIVE_PATH
            )
            
            val videoSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
            } else {
                "${MediaStore.Video.Media.DATA} LIKE ?"
            }
            
            val videoSelectionArgs = arrayOf("%Orai%")
            
            ctx.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                videoSelection,
                videoSelectionArgs,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    -1 // DATA column not available on Android 10+
                } else {
                    cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                }
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val size = cursor.getLong(sizeColumn)
                    val dateModified = cursor.getLong(dateModifiedColumn) * 1000 // Convert seconds to milliseconds
                    
                    // Get file path
                    var filePath: String? = null
                    if (dataColumn >= 0) {
                        filePath = cursor.getString(dataColumn)
                    } else {
                        // On Android 10+, construct path from RELATIVE_PATH and DISPLAY_NAME
                        val relativePathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
                        val relativePath = cursor.getString(relativePathColumn)
                        if (relativePath != null) {
                            // RELATIVE_PATH is like "Movies/Orai" or "/storage/emulated/0/Movies/Orai"
                            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                            val oraiDir = File(moviesDir, "Orai")
                            filePath = File(oraiDir, name).absolutePath
                        }
                    }
                    
                    if (filePath != null) {
                        val file = File(filePath)
                        // Check if file exists or try to access via MediaStore URI
                        val fileExists = file.exists() || try {
                            // Try to open via MediaStore URI to verify file exists
                            val uri = android.content.ContentUris.withAppendedId(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
                        } catch (e: Exception) {
                            false
                        }
                        
                        if (fileExists) {
                            // Check if we already have this file (avoid duplicates)
                            if (!files.any { it.path == filePath || it.name == name }) {
                                files.add(
                                    FileInfo(
                                        name = name,
                                        path = filePath,
                                        isVideo = true,
                                        size = size,
                                        lastModified = dateModified
                                    )
                                )
                            }
                        }
                    }
                }
            }
            
            // Query images from Pictures/Orai
            val imageProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.RELATIVE_PATH
            )
            
            val imageSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            } else {
                "${MediaStore.Images.Media.DATA} LIKE ?"
            }
            
            val imageSelectionArgs = arrayOf("%Orai%")
            
            ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                imageSelection,
                imageSelectionArgs,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dataColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    -1 // DATA column not available on Android 10+
                } else {
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                }
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val size = cursor.getLong(sizeColumn)
                    val dateModified = cursor.getLong(dateModifiedColumn) * 1000 // Convert seconds to milliseconds
                    
                    // Get file path
                    var filePath: String? = null
                    if (dataColumn >= 0) {
                        filePath = cursor.getString(dataColumn)
                    } else {
                        // On Android 10+, construct path from RELATIVE_PATH and DISPLAY_NAME
                        val relativePathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                        val relativePath = cursor.getString(relativePathColumn)
                        if (relativePath != null) {
                            // RELATIVE_PATH is like "Pictures/Orai" or "/storage/emulated/0/Pictures/Orai"
                            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                            val oraiDir = File(picturesDir, "Orai")
                            filePath = File(oraiDir, name).absolutePath
                        }
                    }
                    
                    if (filePath != null) {
                        val file = File(filePath)
                        // Check if file exists or try to access via MediaStore URI
                        val fileExists = file.exists() || try {
                            // Try to open via MediaStore URI to verify file exists
                            val uri = android.content.ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
                        } catch (e: Exception) {
                            false
                        }
                        
                        if (fileExists) {
                            // Check if we already have this file (avoid duplicates)
                            if (!files.any { it.path == filePath || it.name == name }) {
                                files.add(
            FileInfo(
                                        name = name,
                                        path = filePath,
                                        isVideo = false,
                                        size = size,
                                        lastModified = dateModified
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("ERROR: Failed to query MediaStore: ${e.message}")
            e.printStackTrace()
        }
        
        return files
    }

    actual fun deleteFile(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            false
        }
    }

    actual fun getMediaDirectory(): String {
        // Use app-specific external directory for internal file access
        val ctx = getAppContext()
        return if (ctx != null) {
            ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.absolutePath + "/Orai"
        } else {
            // Fallback to public directory
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath + "/Orai"
        }
    }
}
