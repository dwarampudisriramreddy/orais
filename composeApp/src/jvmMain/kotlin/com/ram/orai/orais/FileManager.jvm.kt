package com.ram.orai.orais

import java.io.File

actual class FileManager {
    actual fun saveImage(imageData: ImageData, filename: String): Boolean {
        return try {
            val file = File(getMediaDirectory(), filename)
            file.parentFile?.mkdirs()
            file.writeBytes(imageData.bytes)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    actual fun saveVideo(videoPath: String, filename: String): Boolean {
        return try {
            val source = File(videoPath)
            val dest = File(getMediaDirectory(), filename)
            dest.parentFile?.mkdirs()
            source.copyTo(dest, overwrite = true)
            true
        } catch (e: Exception) {
            false
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
        return "$userHome/OraiMedia"
    }
}
