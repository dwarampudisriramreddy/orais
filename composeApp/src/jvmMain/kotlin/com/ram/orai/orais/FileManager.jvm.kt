package com.ram.orai.orais

import java.io.File
import java.awt.image.BufferedImage
import java.awt.Graphics2D
import java.awt.Color as AwtColor
import java.awt.Font
import javax.imageio.ImageIO

actual class FileManager {
    actual fun saveImage(imageData: ImageData, filename: String): Boolean {
        return try {
            val file = File(getMediaDirectory(), filename)
            file.parentFile?.mkdirs()
            
            // Convert ImageData bytes to BufferedImage and save as PNG
            val bufferedImage = BufferedImage(imageData.width, imageData.height, BufferedImage.TYPE_INT_ARGB)
            var idx = 0
            for (y in 0 until imageData.height) {
                for (x in 0 until imageData.width) {
                    val a = imageData.bytes[idx++].toInt() and 0xFF
                    val r = imageData.bytes[idx++].toInt() and 0xFF
                    val g = imageData.bytes[idx++].toInt() and 0xFF
                    val b = imageData.bytes[idx++].toInt() and 0xFF
                    val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
                    bufferedImage.setRGB(x, y, argb)
                }
            }
            
            ImageIO.write(bufferedImage, "PNG", file)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    actual fun saveVideo(videoPath: String, filename: String): VideoSaveResult {
        return try {
            val source = File(videoPath)
            val dest = File(getMediaDirectory(), filename)
            dest.parentFile?.mkdirs()
            source.copyTo(dest, overwrite = true)
            VideoSaveResult(
                success = true,
                filePath = dest.absolutePath
            )
        } catch (e: Exception) {
            VideoSaveResult(
                success = false,
                errorMessage = "Failed to save video: ${e.message}",
                errorDetails = listOf("❌ Exception: ${e.javaClass.simpleName}", "❌ Message: ${e.message}"),
                filePath = videoPath
            )
        }
    }
    
    actual fun listFiles(directory: String): List<FileInfo> {
        val dir = File(directory)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        
        return dir.listFiles()?.map { file ->
            FileInfo(
                name = file.name,
                path = file.absolutePath,
                isVideo = file.extension in listOf("mp4", "avi", "mov"),
                size = file.length(),
                lastModified = file.lastModified()
            )
        } ?: emptyList()
    }
    
    actual fun deleteFile(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            false
        }
    }
    
    actual fun getMediaDirectory(): String {
        val userHome = System.getProperty("user.home")
        val downloadsDir = File(userHome, "Downloads")
        val oraiDir = File(downloadsDir, "Orai")
        oraiDir.mkdirs()
        return oraiDir.absolutePath
    }
}
