package com.ram.orai.orais

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

actual class CameraManagerImpl actual constructor(
    context: Any?,
    lifecycleOwner: Any?
) : CameraController {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val _cameraFrames = MutableSharedFlow<CameraFrame>(replay = 1)
    override val cameraFrames: SharedFlow<CameraFrame> = _cameraFrames.asSharedFlow()

    private var stream: dynamic = null
    private var video: dynamic = null
    private var canvas: dynamic = null
    private var captureJob: Job? = null

    override fun startPreview() {
        if (captureJob != null) return
        
        js("""
            navigator.mediaDevices.getUserMedia({ video: true })
                .then(function(mediaStream) {
                    this.stream = mediaStream;
                    this.video = document.createElement('video');
                    this.video.srcObject = mediaStream;
                    this.video.play();
                }.bind(this));
        """)
        
        captureJob = scope.launch {
            delay(1000) // Wait for video to initialize
            while (isActive) {
                try {
                    captureFrame()?.let { frame ->
                        _cameraFrames.emit(frame)
                    }
                    delay(33) // ~30 FPS
                } catch (_: Throwable) {
                    // Ignore frame errors
                }
            }
        }
    }

    override fun stopPreview() {
        captureJob?.cancel()
        captureJob = null
        
        js("""
            if (this.stream) {
                this.stream.getTracks().forEach(track => track.stop());
                this.stream = null;
            }
        """)
    }

    override fun switchCamera() {
        // Web camera switching would require enumerating devices
        // and requesting a different deviceId - implement if needed
    }

    override fun captureImage(): CameraFrame? {
        return captureFrame()
    }

    private fun captureFrame(): CameraFrame? {
        // TODO: Implement canvas-based frame capture and convert to ImageBitmap
        // This requires JS interop to draw video frame to canvas and get ImageData
        return null
    }
}

actual fun getCameraManager(context: Any?, lifecycleOwner: Any?): CameraController {
    return CameraManagerImpl(context, lifecycleOwner)
}
