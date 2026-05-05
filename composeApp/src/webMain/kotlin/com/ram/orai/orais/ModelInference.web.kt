package com.ram.orai.orais

import com.ram.orai.detection.BoundingBox

// External declarations for MediaPipe Tasks
@JsModule("@mediapipe/tasks-vision")
@JsNonModule
external object MediaPipeVision {
    class ObjectDetector {
        companion object {
            fun createFromOptions(options: dynamic): dynamic
        }
        fun detect(imageData: dynamic): dynamic
        fun close()
    }
}

actual class ModelInference {
    private var detector: dynamic = null
    
    actual fun loadModel(modelPath: String): Boolean {
        return try {
            val options = js("""({
                baseOptions: {
                    modelAssetPath: modelPath
                },
                runningMode: 'IMAGE',
                maxResults: 10
            })""")
            
            detector = MediaPipeVision.ObjectDetector.createFromOptions(options)
            true
        } catch (e: Throwable) {
            console.error("Failed to load model: ${e.message}")
            false
        }
    }
    
    actual fun runInference(imageData: ImageData, confThreshold: Float): List<DetectionResult> {
        if (detector == null) return emptyList()
        
        return try {
            // Convert ImageData to format MediaPipe expects
            val imageInput = js("""({
                data: imageData.bytes,
                width: imageData.width,
                height: imageData.height
            })""")
            
            val results = detector.detect(imageInput)
            val detections = mutableListOf<DetectionResult>()
            
            // Parse MediaPipe results
            val detectionsArray = results.detections as Array<dynamic>
            for (detection in detectionsArray) {
                val score = detection.categories[0].score as Float
                if (score >= confThreshold) {
                    val bbox = detection.boundingBox
                    detections.add(
                        DetectionResult(
                            boundingBox = BoundingBox(
                                left = (bbox.originX as Number).toFloat(),
                                top = (bbox.originY as Number).toFloat(),
                                right = ((bbox.originX as Number).toFloat() + (bbox.width as Number).toFloat()),
                                bottom = ((bbox.originY as Number).toFloat() + (bbox.height as Number).toFloat())
                            ),
                            classId = detection.categories[0].index as Int,
                            confidence = score
                        )
                    )
                }
            }
            
            detections
        } catch (e: Throwable) {
            console.error("Inference failed: ${e.message}")
            emptyList()
        }
    }
    
    actual fun close() {
        detector?.close()
        detector = null
    }
}
