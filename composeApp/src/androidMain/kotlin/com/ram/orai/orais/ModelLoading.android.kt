package com.ram.orai.orais

import android.content.Context

actual fun getYoloModelPath(): String {
    // Android uses .onnx files from assets folder (using ONNX Runtime)
    return "yolov8_640_float32.onnx"
}

actual fun setModelInferenceContext(modelInference: ModelInference) {
    // Get context from Gallery.android.kt
    val context = getAppContext()
    if (context != null) {
        modelInference.context = context
        println("ModelInference context set successfully")
    } else {
        println("ERROR: Context not available for ModelInference")
    }
}

actual fun setFaceDetectorContext(faceDetector: FaceDetector) {
    // Get context from Gallery.android.kt
    val context = getAppContext()
    if (context != null) {
        faceDetector.setContext(context)
        println("FaceDetector context set successfully")
    } else {
        println("ERROR: Context not available for FaceDetector")
    }
}

