package com.ram.orai.orais

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.io.File
import java.util.concurrent.atomic.AtomicReference

actual class CameraManagerImpl actual constructor(
    context: Any?,
    lifecycleOwner: Any?
) : CameraController {

    private val androidContext = context as? Context
    private val androidLifecycleOwner = lifecycleOwner as? LifecycleOwner

    private val _cameraFrames = MutableSharedFlow<CameraFrame>(replay = 1)
    override val cameraFrames: SharedFlow<CameraFrame> = _cameraFrames.asSharedFlow()

    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private lateinit var cameraExecutor: ExecutorService
    private var isPreviewStarted = false
    private val activeRecordingRef = AtomicReference<Recording?>(null)

    override fun startPreview() {
        val ctx = androidContext ?: run {
            println("ERROR: Android context is null, cannot start preview")
            return
        }
        val lifecycleOwner = androidLifecycleOwner ?: run {
            println("ERROR: Lifecycle owner is null, cannot start preview")
            return
        }

        if (isPreviewStarted) {
            println("Preview already started, skipping")
            return
        }

        println("Starting camera preview...")
        cameraExecutor = Executors.newSingleThreadExecutor()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                println("Camera provider obtained, binding use cases...")
                bindCameraUseCases(ctx, lifecycleOwner)
                isPreviewStarted = true
                println("Camera preview started successfully")
            } catch (e: Exception) {
                println("ERROR: Failed to get camera provider: ${e.message}")
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun bindCameraUseCases(context: Context, lifecycleOwner: LifecycleOwner) {
        val provider = cameraProvider ?: run {
            println("ERROR: Camera provider is null, cannot bind use cases")
            return
        }

        try {
            // Unbind all use cases before rebinding
            provider.unbindAll()
            println("Unbound previous camera use cases")

            // Camera selector
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()
            
            println("Using camera: ${if (lensFacing == CameraSelector.LENS_FACING_BACK) "BACK" else "FRONT"}")

            // Preview use case (for better preview quality)
            preview = Preview.Builder()
                .build()

            // Image analysis use case (for frame processing)
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            // Video capture use case (for recording)
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)))
                .build()
            
            videoCapture = VideoCapture.Builder(recorder)
                .build()

            // Bind use cases to camera
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis,
                videoCapture
            )
            
            println("Camera use cases bound successfully (Preview, ImageAnalysis, VideoCapture)")
            println("VideoCapture state: ${if (videoCapture != null) "Initialized" else "NULL"}")
        } catch (e: Exception) {
            println("ERROR: Failed to bind camera use cases: ${e.message}")
            e.printStackTrace()
        }
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                // Convert YUV to RGB bitmap
                val bitmap = imageProxy.toBitmap()

                // Rotate bitmap if needed
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val rotatedBitmap = if (rotationDegrees != 0) {
                    rotateBitmap(bitmap, rotationDegrees.toFloat())
                } else {
                    bitmap
                }

                // Emit frame
                val imageBitmap = rotatedBitmap.asImageBitmap()
                val frame = CameraFrame(imageBitmap, System.currentTimeMillis())

                val emitted = _cameraFrames.tryEmit(frame)
                if (!emitted) {
                    println("WARNING: Failed to emit camera frame (backpressure)")
                }
            } else {
                println("WARNING: MediaImage is null in imageProxy")
            }
        } catch (e: Exception) {
            println("ERROR: Failed to process image proxy: ${e.message}")
            e.printStackTrace()
        } finally {
            imageProxy.close()
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    override fun stopPreview() {
        println("Stopping camera preview...")
        cameraProvider?.unbindAll()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
        isPreviewStarted = false
        println("Camera preview stopped")
    }

    override fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }

        val ctx = androidContext ?: return
        val lifecycleOwner = androidLifecycleOwner ?: return
        bindCameraUseCases(ctx, lifecycleOwner)
    }

    override fun switchToCamera(index: Int) {
        // Android: Switch between front/back cameras
        val newLensFacing = if (index == 0) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
        
        if (lensFacing == newLensFacing) {
            println("Camera already set to index $index, skipping switch")
            return
        }
        
        println("Switching camera to index $index (${if (index == 0) "BACK" else "FRONT"})")
        lensFacing = newLensFacing
        val ctx = androidContext ?: run {
            println("ERROR: Context is null, cannot switch camera")
            return
        }
        val lifecycleOwner = androidLifecycleOwner ?: run {
            println("ERROR: Lifecycle owner is null, cannot switch camera")
            return
        }
        
        // Make sure camera provider is ready
        if (cameraProvider == null) {
            println("Camera provider not ready, initializing...")
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            try {
                cameraProvider = cameraProviderFuture.get(5, TimeUnit.SECONDS)
                println("Camera provider obtained for camera switch")
            } catch (e: Exception) {
                println("ERROR: Failed to get camera provider for switch: ${e.message}")
                return
            }
        }
        
        bindCameraUseCases(ctx, lifecycleOwner)
    }

    override fun getAvailableCameras(): List<CameraInfo> {
        val cameras = mutableListOf<CameraInfo>()
        val ctx = androidContext ?: return cameras
        
        try {
            // Use existing camera provider if available, otherwise get a new one
            val provider = cameraProvider ?: run {
                val future = ProcessCameraProvider.getInstance(ctx)
                future.get(5, java.util.concurrent.TimeUnit.SECONDS)
            }
            
            // Check available camera infos
            val availableInfos = provider.availableCameraInfos
            
            println("Found ${availableInfos.size} camera(s)")
            
            // Check for back camera
            val backSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()
            val backCameras = backSelector.filter(availableInfos)
            if (backCameras.isNotEmpty()) {
                cameras.add(CameraInfo("0", "Back Camera", false, 0))
                println("Back camera detected")
            }
            
            // Check for front camera
            val frontSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build()
            val frontCameras = frontSelector.filter(availableInfos)
            if (frontCameras.isNotEmpty()) {
                cameras.add(CameraInfo("1", "Front Camera", false, 1))
                println("Front camera detected")
            }
            
            // If no cameras detected, assume both are available (fallback)
            if (cameras.isEmpty()) {
                println("No cameras detected, using fallback")
                cameras.add(CameraInfo("0", "Back Camera", false, 0))
                cameras.add(CameraInfo("1", "Front Camera", false, 1))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error detecting cameras: ${e.message}")
            // Fallback to default cameras if detection fails
            cameras.add(CameraInfo("0", "Back Camera", false, 0))
            cameras.add(CameraInfo("1", "Front Camera", false, 1))
        }
        
        return cameras
    }

    override fun getCurrentCameraIndex(): Int {
        return if (lensFacing == CameraSelector.LENS_FACING_BACK) 0 else 1
    }

    override fun captureImage(): CameraFrame? {
        // Return the last emitted frame (if any)
        return _cameraFrames.replayCache.lastOrNull()
    }
    
    // Video recording methods (Android-specific, accessed via casting)
    fun startVideoRecording(outputFile: File): Recording? {
        println("Attempting to start video recording to: ${outputFile.absolutePath}")
        
        val videoCapture = this.videoCapture ?: run {
            println("ERROR: VideoCapture is null, cannot start recording")
            println("ERROR: Make sure camera preview is started before recording")
            return null
        }
        
        val ctx = androidContext ?: run {
            println("ERROR: Context is null, cannot start recording")
            return null
        }
        
        val provider = cameraProvider ?: run {
            println("ERROR: Camera provider is null, cannot start recording")
            return null
        }
        
        // Check if there's already an active recording
        val existingRecording = activeRecordingRef.get()
        if (existingRecording != null) {
            println("WARNING: There's already an active recording, stopping it first")
            try {
                existingRecording.stop()
            } catch (e: Exception) {
                println("WARNING: Error stopping existing recording: ${e.message}")
            }
        }
        
        return try {
            // Ensure parent directory exists
            outputFile.parentFile?.mkdirs()
            
            // Create file output options
            val fileOutputOptions = FileOutputOptions.Builder(outputFile).build()
            println("File output options created for: ${outputFile.absolutePath}")
            
            // Start recording to file
            val recording = videoCapture.output
                .prepareRecording(ctx, fileOutputOptions)
                .apply {
                    // Try to enable audio, but don't fail if it's not available
                    try {
                        withAudioEnabled()
                        println("Audio recording enabled")
                    } catch (e: Exception) {
                        println("WARNING: Could not enable audio recording: ${e.message}")
                        // Continue without audio
                    }
                }
                .start(ContextCompat.getMainExecutor(ctx)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            println("✅ Video recording started: ${outputFile.absolutePath}")
                        }
                        is VideoRecordEvent.Finalize -> {
                            if (event.hasError()) {
                                println("ERROR: Video recording failed: ${event.cause}")
                                event.cause?.printStackTrace()
                            } else {
                                val outputUri = event.outputResults.outputUri
                                println("✅ Video recording completed: ${outputFile.absolutePath}")
                                if (outputUri != null) {
                                    println("Video URI: $outputUri")
                                }
                            }
                        }
                        is VideoRecordEvent.Status -> {
                            // Recording status updates
                            val recordingStats = event.recordingStats
                            println("Recording: ${recordingStats.numBytesRecorded} bytes")
                        }
                        is VideoRecordEvent.Pause -> {
                            println("Video recording paused")
                        }
                        is VideoRecordEvent.Resume -> {
                            println("Video recording resumed")
                        }
                    }
                }
            
            activeRecordingRef.set(recording)
            println("✅ Recording object created and stored")
            recording
        } catch (e: SecurityException) {
            println("ERROR: Security exception - missing RECORD_AUDIO permission? ${e.message}")
            e.printStackTrace()
            null
        } catch (e: IllegalStateException) {
            println("ERROR: Illegal state - camera not ready or VideoCapture not bound: ${e.message}")
            e.printStackTrace()
            null
        } catch (e: Exception) {
            println("ERROR: Failed to start video recording: ${e.message}")
            println("ERROR: Exception type: ${e.javaClass.simpleName}")
            e.printStackTrace()
            null
        }
    }
    
    fun stopVideoRecording() {
        val recording = activeRecordingRef.getAndSet(null)
        try {
            recording?.stop()
            println("Video recording stop requested")
        } catch (e: Exception) {
            println("ERROR: Failed to stop video recording: ${e.message}")
            e.printStackTrace()
        }
    }
}

actual fun getCameraManager(context: Any?, lifecycleOwner: Any?): CameraController {
    // Use provided context/lifecycleOwner, or fall back to stored ones
    val ctx = (context as? Context) ?: com.ram.orai.orais.getAppContext()
    val owner = (lifecycleOwner as? androidx.lifecycle.LifecycleOwner) ?: com.ram.orai.orais.getAppLifecycleOwner()
    return CameraManagerImpl(ctx, owner)
}
