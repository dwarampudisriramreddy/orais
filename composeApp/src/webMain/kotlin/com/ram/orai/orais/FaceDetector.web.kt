package com.ram.orai.orais

actual class FaceDetector {
    // Web: Could use MediaPipe Face Detection
    
    actual fun detectFaces(imageData: ImageData, onSuccess: (List<FaceInfo>) -> Unit, onError: (String) -> Unit) {
        onError("Face detection not implemented for Web")
    }
    
    actual fun close() {
        // No-op
    }
}
