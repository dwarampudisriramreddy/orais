package com.ram.orai.orais

// Minimal web stub to ensure JS/WASM build succeeds. Replace with real implementation later.

actual class ModelInference {
    actual fun loadModel(modelPath: String): Boolean = true

    actual fun runInference(imageData: ImageData, confThreshold: Float): List<DetectionResult> = emptyList()

    actual fun close() {}
}
