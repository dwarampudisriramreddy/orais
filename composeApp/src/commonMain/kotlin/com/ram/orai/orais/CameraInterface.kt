package com.ram.orai.orais

import com.ram.orai.detection.BoundingBox
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.SharedFlow

// Unified camera controller API used across platforms
interface CameraController {
    fun startPreview()
    fun stopPreview()
    fun switchCamera()
    fun switchToCamera(index: Int)
    fun getAvailableCameras(): List<CameraInfo>
    fun getCurrentCameraIndex(): Int
    fun captureImage(): CameraFrame?
    val cameraFrames: SharedFlow<CameraFrame>
}

// Raw image container for ML pipelines (expect/actual per platform)
data class ImageData(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val bytes: ByteArray
)

data class CameraInfo(val id: String, val name: String, val isUvc: Boolean, val lensFacing: Int)

expect class ModelInference() {
    fun loadModel(modelPath: String): Boolean
    fun runInference(imageData: ImageData, confThreshold: Float): List<DetectionResult>
    fun close()
}

data class DetectionResult(val boundingBox: BoundingBox, val classId: Int, val confidence: Float)

expect class FileManager() {
    fun saveImage(imageData: ImageData, filename: String): Boolean
    fun saveVideo(videoPath: String, filename: String): VideoSaveResult
    fun listFiles(directory: String): List<FileInfo>
    fun deleteFile(path: String): Boolean
    fun getMediaDirectory(): String
}

data class FileInfo(val name: String, val path: String, val isVideo: Boolean, val size: Long, val lastModified: Long)

data class VideoSaveResult(
    val success: Boolean,
    val errorMessage: String? = null,
    val errorDetails: List<String> = emptyList(),
    val filePath: String? = null,
    val mediaStoreUri: String? = null
)

expect class UsbDeviceManager() {
    fun initialize(onDeviceConnected: (UsbDeviceInfo) -> Unit)
    fun requestPermission(deviceId: String)
    fun openDevice(deviceId: String): Boolean
    fun closeDevice(deviceId: String)
    fun getConnectedDevices(): List<UsbDeviceInfo>
}

data class UsbDeviceInfo(val id: String, val name: String, val vendorId: Int, val productId: Int)

expect class AuthManager() {
    fun signInWithGoogle(onSuccess: (UserInfo) -> Unit, onError: (String) -> Unit)
    fun signOut()
    fun getCurrentUser(): UserInfo?
    fun checkBlockStatus(userId: String, onResult: (Boolean) -> Unit)
}

data class UserInfo(val uid: String, val email: String?, val displayName: String?)

expect class PreferencesManager() {
    fun putBoolean(key: String, value: Boolean)
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putString(key: String, value: String)
    fun getString(key: String, defaultValue: String): String
    fun putInt(key: String, value: Int)
    fun getInt(key: String, defaultValue: Int): Int
}

expect class FaceDetector() {
    fun detectFaces(imageData: ImageData, onSuccess: (List<FaceInfo>) -> Unit, onError: (String) -> Unit)
    fun close()
}

data class FaceInfo(val trackingId: Int?, val boundingBox: BoundingBox)

// Combined tooth detection with condition
data class ToothDetection(
    val toothNumber: Int,
    val boundingBox: BoundingBox,
    val condition: String? = null,
    val conditionConfidence: Float? = null
)

// Patient data with accumulated readings
data class PatientData(
    val patientId: String,
    val faceEmbedding: FloatArray?,
    val teeth: Map<Int, ToothConditionHistory>,
    val timestamp: Long
)

// Track multiple readings for each tooth
data class ToothConditionHistory(
    val toothNumber: Int,
    val readings: MutableList<ConditionReading> = mutableListOf(),
    val mostCommonCondition: String = "Normal",
    val confidence: Float = 0f
)

data class ConditionReading(
    val condition: String,
    val confidence: Float,
    val timestamp: Long
)

// Cross-platform timestamp function
expect fun getCurrentTimestamp(): Long

// Screen casting interface
expect class ScreenCastManager() {
    fun startCasting(onSuccess: (String) -> Unit, onError: (String) -> Unit)
    fun stopCasting()
    fun isCastingAvailable(): Boolean
    fun getCastDevices(): List<CastDevice>
}

data class CastDevice(
    val id: String,
    val name: String,
    val type: CastDeviceType
)

enum class CastDeviceType {
    CHROMECAST,
    MIRACAST,
    HDMI,
    AIRPLAY,
    DLNA
}
