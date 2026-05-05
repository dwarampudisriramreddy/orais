package com.ram.orai.orais

// Desktop JVM stub implementation; replace with TFLite integration later
actual class ModelInference {
    actual fun loadModel(modelPath: String): Boolean = true
    actual fun runInference(imageData: ImageData, confThreshold: Float): List<DetectionResult> = emptyList()
    actual fun close() {}
}
