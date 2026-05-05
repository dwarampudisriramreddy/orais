package com.ram.orai.orais

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.Java2DFrameConverter
import org.bytedeco.javacv.OpenCVFrameConverter
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_videoio
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_videoio.VideoCapture
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicInteger

/**
 * JVM Desktop Camera Controller
 * Uses OpenCVFrameGrabber - stable for USB cameras on Windows/Linux/macOS
 * 
 * Architecture: Platform-native implementation, no cross-platform abstractions
 */
actual class CameraManagerImpl actual constructor(
    context: Any?,
    lifecycleOwner: Any?
) : CameraController {

    init {
        // Force DirectShow backend on Windows (more reliable than MSMF)
        val os = System.getProperty("os.name", "").lowercase()
        if (os.contains("windows")) {
            // Set OpenCV to use DirectShow backend instead of MSMF
            try {
                System.setProperty("OPENCV_VIDEOIO_PRIORITY_MSMF", "0")
                System.setProperty("OPENCV_VIDEOIO_PRIORITY_DSHOW", "100")
            } catch (e: Exception) {
                // Ignore if property doesn't exist
            }
        }
    }

    // Capture runs on background thread, UI stays responsive
    private val captureScope = CoroutineScope(Dispatchers.Default + Job())
    
    private val _cameraFrames = MutableSharedFlow<CameraFrame>(replay = 1)
    override val cameraFrames: SharedFlow<CameraFrame> = _cameraFrames.asSharedFlow()

    private var currentIndex = AtomicInteger(0)
    private var captureJob: Job? = null
    
    // One VideoCapture per camera index (simple cache) - using OpenCV directly with DirectShow backend
    private val videoCaptures = mutableMapOf<Int, VideoCapture>()
    private val frameConverter = Java2DFrameConverter()

    override fun startPreview() {
        if (captureJob != null) return
        
        captureJob = captureScope.launch {
            // Try to find a working camera (try multiple indices if needed)
            var videoCapture: VideoCapture? = null
            var workingIndex = currentIndex.get()
            
            // Try current index first
            videoCapture = getOrCreateVideoCapture(workingIndex)
            
            // If current index fails, try other indices (0-20) with multiple backends
            if (videoCapture == null) {
                val os = System.getProperty("os.name", "").lowercase()
                val isWindows = os.contains("windows")
                println("Camera not available at index $workingIndex, trying other indices and backends...")
                for (i in 0..20) {
                    if (i != workingIndex) {
                        // Try DirectShow first (most reliable on Windows)
                        videoCapture = getOrCreateVideoCapture(i, opencv_videoio.CAP_DSHOW)
                        if (videoCapture == null && isWindows) {
                            // Try MSMF backend as fallback
                            println("Trying MSMF backend for camera $i...")
                            videoCapture = getOrCreateVideoCapture(i, opencv_videoio.CAP_MSMF)
                        }
                        if (videoCapture == null) {
                            // Try default backend as last resort
                            println("Trying default backend for camera $i...")
                            videoCapture = getOrCreateVideoCapture(i, -1) // -1 = default
                        }
                        if (videoCapture != null) {
                            workingIndex = i
                            currentIndex.set(i)
                            println("Found working camera at index $i")
                            break
                        }
                    }
                }
            }
            
            if (videoCapture == null) {
                println("No camera available at any index (0-20) with any backend")
                println("Troubleshooting tips:")
                println("  1. Make sure your USB camera is connected")
                println("  2. Check if another application is using the camera")
                println("  3. Try unplugging and replugging the USB camera")
                println("  4. Check Device Manager to see if the camera is recognized by Windows")
                return@launch
            }
            
            val width = videoCapture.get(opencv_videoio.CAP_PROP_FRAME_WIDTH).toInt()
            val height = videoCapture.get(opencv_videoio.CAP_PROP_FRAME_HEIGHT).toInt()
            println("Camera started at index $workingIndex, resolution: ${width}x${height}")
            
            val mat = Mat()
            var consecutiveErrors = 0
            val maxErrors = 30 // Allow more errors (some frames may fail intermittently)
            var frameCount = 0
            var lastSuccessTime = System.currentTimeMillis()
            
            // Capture loop - runs on background thread
            while (isActive) {
                try {
                    if (videoCapture.read(mat) && !mat.empty()) {
                        consecutiveErrors = 0 // Reset on success
                        lastSuccessTime = System.currentTimeMillis()
                        
                        // Convert Mat → BufferedImage → ImageBitmap
                        // Direct conversion to avoid color channel issues
                        try {
                            // OpenCV Mat is in BGR format - convert to RGB
                            val rgbMat = Mat()
                            opencv_imgproc.cvtColor(mat, rgbMat, opencv_imgproc.COLOR_BGR2RGB)
                            
                            val width = rgbMat.cols()
                            val height = rgbMat.rows()
                            
                            // Create BufferedImage in RGB format
                            val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
                            
                            // Get Mat data as byte array (RGB format)
                            val matData = ByteArray(width * height * 3)
                            rgbMat.data().get(matData)
                            
                            // Copy RGB bytes directly to BufferedImage
                            var srcIdx = 0
                            for (y in 0 until height) {
                                for (x in 0 until width) {
                                    val r = (matData[srcIdx++].toInt() and 0xFF)
                                    val g = (matData[srcIdx++].toInt() and 0xFF)
                                    val b = (matData[srcIdx++].toInt() and 0xFF)
                                    val rgb = (r shl 16) or (g shl 8) or b
                                    bufferedImage.setRGB(x, y, rgb)
                                }
                            }
                            
                            rgbMat.close()
                            
                            val imageBitmap = bufferedImage.toComposeImageBitmap()
                            _cameraFrames.emit(CameraFrame(imageBitmap, System.currentTimeMillis()))
                            
                            // Record frame if recording is active
                            com.ram.orai.orais.recordFrame(mat)
                            
                            frameCount++
                            if (frameCount == 1 || frameCount % 60 == 0) {
                                println("Frame captured: $frameCount frames (${imageBitmap.width}x${imageBitmap.height})")
                            }
                        } catch (convError: Exception) {
                            if (frameCount < 3) {
                                println("Conversion error: ${convError.message}")
                                convError.printStackTrace()
                            }
                        }
                    } else {
                        consecutiveErrors++
                        // Only log if we haven't had success in a while
                        val timeSinceSuccess = System.currentTimeMillis() - lastSuccessTime
                        if (consecutiveErrors == 1 || (consecutiveErrors % 60 == 0 && timeSinceSuccess > 2000)) {
                            println("Warning: Failed to read frame (errors: $consecutiveErrors)")
                        }
                    }
                } catch (e: Exception) {
                    consecutiveErrors++
                    // Only log if we haven't had success in a while
                    val timeSinceSuccess = System.currentTimeMillis() - lastSuccessTime
                    if (consecutiveErrors == 1 || (consecutiveErrors % 60 == 0 && timeSinceSuccess > 2000)) {
                        println("Frame capture error: ${e.message}")
                    }
                    
                    // Only stop if we've had no success for a long time
                    if (consecutiveErrors >= maxErrors && timeSinceSuccess > 5000) {
                        println("Too many consecutive errors, camera may be disconnected")
                        break
                    }
                }
                
                // ~30 FPS for smooth preview (33ms = ~30fps)
                delay(33)
            }
            
            mat.close()
        }
    }

    override fun stopPreview() {
        captureJob?.cancel()
        captureJob = null
    }

    override fun switchCamera() {
        val nextIndex = (currentIndex.get() + 1) % 10 // Try 0-9
        switchToCamera(nextIndex)
    }

    override fun switchToCamera(index: Int) {
        if (index == currentIndex.get()) return
        
        stopPreview()
        currentIndex.set(index)
        // Release old video capture
        videoCaptures[currentIndex.get()]?.let {
            try {
                it.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
        videoCaptures.remove(currentIndex.get())
        
        startPreview()
    }

    override fun getAvailableCameras(): List<CameraInfo> {
        // OpenCV doesn't provide enumeration API
        // Return currently known/open cameras
        // For full enumeration, this should be async - keeping simple for now
        return videoCaptures.keys.map { index ->
            CameraInfo(
                id = index.toString(),
                name = "Camera $index",
                isUvc = true, // Assume UVC for desktop
                lensFacing = 0 // Not applicable for desktop
            )
        }
    }

    override fun getCurrentCameraIndex(): Int = currentIndex.get()

    override fun captureImage(): CameraFrame? {
        // Use existing video capture if available, otherwise return null
        val videoCapture = videoCaptures[currentIndex.get()] ?: return null
        
        return try {
            val mat = Mat()
            if (videoCapture.read(mat) && !mat.empty()) {
                // Convert BGR to RGB
                val rgbMat = Mat()
                opencv_imgproc.cvtColor(mat, rgbMat, opencv_imgproc.COLOR_BGR2RGB)
                
                val width = rgbMat.cols()
                val height = rgbMat.rows()
                val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
                
                val matData = ByteArray(width * height * 3)
                rgbMat.data().get(matData)
                
                var srcIdx = 0
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val r = (matData[srcIdx++].toInt() and 0xFF)
                        val g = (matData[srcIdx++].toInt() and 0xFF)
                        val b = (matData[srcIdx++].toInt() and 0xFF)
                        val rgb = (r shl 16) or (g shl 8) or b
                        bufferedImage.setRGB(x, y, rgb)
                    }
                }
                
                mat.close()
                rgbMat.close()
                
                val imageBitmap = bufferedImage.toComposeImageBitmap()
                CameraFrame(imageBitmap, System.currentTimeMillis())
            } else {
                mat.close()
                null
            }
        } catch (e: Exception) {
            println("Capture error: ${e.message}")
            null
        }
    }

    /**
     * Get or create VideoCapture for given index using specified backend
     * Uses OpenCV VideoCapture API directly with explicit backend selection
     * @param backend Backend ID (-1 for default, CAP_DSHOW=700, CAP_MSMF=1400)
     */
    private suspend fun getOrCreateVideoCapture(index: Int, backend: Int = opencv_videoio.CAP_DSHOW): VideoCapture? {
        // Return cached video capture if available and working
        videoCaptures[index]?.let { capture ->
            try {
                val mat = Mat()
                if (capture.read(mat) && !mat.empty()) {
                    mat.close()
                    return capture
                }
                mat.close()
            } catch (e: Exception) {
                // Capture is dead, remove it
                try {
                    capture.release()
                } catch (e2: Exception) {
                    // Ignore
                }
                videoCaptures.remove(index)
            }
        }
        
        // Create new VideoCapture with specified backend
        val os = System.getProperty("os.name", "").lowercase()
        val isWindows = os.contains("windows")
        
        return try {
            val videoCapture = if (backend == -1) {
                // Use default backend
                VideoCapture(index)
            } else {
                // Use specified backend (DirectShow, MSMF, etc.)
                VideoCapture(index, backend)
            }
            
            val backendName = when (backend) {
                opencv_videoio.CAP_DSHOW -> "DirectShow"
                opencv_videoio.CAP_MSMF -> "MSMF"
                else -> "Default"
            }
            println("Attempting to open camera $index with $backendName backend...")
            
            if (!videoCapture.isOpened) {
                videoCapture.release()
                return null
            }
            
            // Set higher resolution for better quality
            try {
                // Try 1280x720 first (HD quality)
                videoCapture.set(opencv_videoio.CAP_PROP_FRAME_WIDTH, 1280.0)
                videoCapture.set(opencv_videoio.CAP_PROP_FRAME_HEIGHT, 720.0)
                println("Attempting to set camera resolution to 1280x720...")
            } catch (e: Exception) {
                println("Could not set 1280x720, trying 640x480...")
                try {
                    videoCapture.set(opencv_videoio.CAP_PROP_FRAME_WIDTH, 640.0)
                    videoCapture.set(opencv_videoio.CAP_PROP_FRAME_HEIGHT, 480.0)
                } catch (e2: Exception) {
                    println("Could not set resolution, using camera default")
                }
            }
            
            // Wait for initialization
            delay(1000)
            
            // Test read
            val testMat = Mat()
            var readSuccess = false
            for (attempt in 1..5) {
                if (videoCapture.read(testMat) && !testMat.empty()) {
                    readSuccess = true
                    break
                }
                delay(300)
            }
            
            if (readSuccess) {
                val width = videoCapture.get(opencv_videoio.CAP_PROP_FRAME_WIDTH).toInt()
                val height = videoCapture.get(opencv_videoio.CAP_PROP_FRAME_HEIGHT).toInt()
                val backendName = when (backend) {
                    opencv_videoio.CAP_DSHOW -> "DirectShow"
                    opencv_videoio.CAP_MSMF -> "MSMF"
                    else -> "Default"
                }
                println("✓ Camera $index opened successfully with $backendName backend: ${width}x${height}")
                testMat.close()
                videoCaptures[index] = videoCapture
                videoCapture
            } else {
                val backendName = when (backend) {
                    opencv_videoio.CAP_DSHOW -> "DirectShow"
                    opencv_videoio.CAP_MSMF -> "MSMF"
                    else -> "Default"
                }
                println("✗ Camera $index failed to read frames with $backendName backend")
                testMat.close()
                videoCapture.release()
                null
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error"
            val backendName = when (backend) {
                opencv_videoio.CAP_DSHOW -> "DirectShow"
                opencv_videoio.CAP_MSMF -> "MSMF"
                else -> "Default"
            }
            if (!errorMsg.contains("index out of range")) {
                println("Failed to open camera $index with $backendName backend: $errorMsg")
            }
            null
        }
    }
    
    // Cleanup on destruction
    fun release() {
        stopPreview()
        videoCaptures.values.forEach { capture ->
            try {
                capture.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
        videoCaptures.clear()
    }
}

