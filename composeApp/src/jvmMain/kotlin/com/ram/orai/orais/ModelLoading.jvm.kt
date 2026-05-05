package com.ram.orai.orais

actual fun getYoloModelPath(): String {
    // Desktop JVM uses .onnx files from resources folder
    return "yolov8_640_float32.onnx"
}

actual fun setModelInferenceContext(modelInference: ModelInference) {
    // No context needed for JVM (uses classloader resources)
}

actual fun setFaceDetectorContext(faceDetector: FaceDetector) {
    // No context needed for JVM (FaceDetector loads model in init block)
}

