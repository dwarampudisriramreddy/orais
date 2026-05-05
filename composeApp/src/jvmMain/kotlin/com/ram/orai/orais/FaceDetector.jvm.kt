package com.ram.orai.orais

actual class FaceDetector {
    actual fun detectFaces(imageData: ImageData, onSuccess: (List<FaceInfo>) -> Unit, onError: (String) -> Unit) {
        onError("Face detection not implemented for Desktop")
    }
    
    actual fun close() {
        // No-op
    }
}
