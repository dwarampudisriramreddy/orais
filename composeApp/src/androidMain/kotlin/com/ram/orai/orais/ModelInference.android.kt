package com.ram.orai.orais

import android.content.Context
import com.ram.orai.detection.BoundingBox
import ai.onnxruntime.*
import java.nio.FloatBuffer

actual class ModelInference {
    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private var debugPrinted = false

    // Android-specific: Store context for asset loading
    var context: Context? = null

    actual fun loadModel(modelPath: String): Boolean {
        return try {
            val ctx = context ?: throw IllegalStateException(
                "Context not set. Call modelInference.context = context before loadModel()"
            )

            println("Loading ONNX model: $modelPath")
            
            // Check if file exists in assets
            val assetManager = ctx.assets
            val availableFiles = assetManager.list("") ?: emptyArray()
            println("Available files in assets: ${availableFiles.joinToString()}")
            
            if (!availableFiles.contains(modelPath)) {
                println("ERROR: Model file not found in assets: $modelPath")
                println("Available files: ${availableFiles.joinToString()}")
                return false
            }
            
            // Load model from assets
            val modelBytes = assetManager.open(modelPath).use { inputStream ->
                inputStream.readBytes()
            }
            println("Model file loaded: ${modelBytes.size} bytes")

            // Create ONNX Runtime session
            session = env.createSession(modelBytes)
            println("ONNX Runtime session created successfully")
            println("Model inputs: ${session?.inputNames?.joinToString()}")
            println("Model outputs: ${session?.outputNames?.joinToString()}")

            true
        } catch (e: Exception) {
            println("ERROR: Failed to load ONNX model: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    actual fun runInference(imageData: ImageData, confThreshold: Float): List<DetectionResult> {
        val currentSession = session ?: return emptyList()

        return try {
            // Preprocess image to 640x640 normalized float array [1, 640, 640, 3] (HWC format)
            val inputTensor = preprocessImage(imageData)

            // Run inference
            val inputs = mapOf(currentSession.inputNames.first() to inputTensor)
            val results = currentSession.run(inputs)

            // Parse Conditions model output: [1, 300, 6]
            val detections = parseConditionsOutput(results, confThreshold, imageData.width, imageData.height)

            // Close ONNX resources
            results.close()
            inputTensor.close()

            detections
        } catch (e: Exception) {
            println("Inference error: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    private fun preprocessImage(imageData: ImageData): OnnxTensor {
        val targetSize = 640
        val inputData = FloatArray(1 * targetSize * targetSize * 3)

        // Convert RGBA bytes to normalized RGB float array [1, 640, 640, 3] (HWC format)
        val pixels = imageData.bytes
        val scaleX = imageData.width.toFloat() / targetSize
        val scaleY = imageData.height.toFloat() / targetSize

        for (y in 0 until targetSize) {
            for (x in 0 until targetSize) {
                val srcX = (x * scaleX).toInt().coerceIn(0, imageData.width - 1)
                val srcY = (y * scaleY).toInt().coerceIn(0, imageData.height - 1)
                val pixelIndex = (srcY * imageData.width + srcX) * 4 // Assuming RGBA

                if (pixelIndex + 2 < pixels.size) {
                    // Normalize to 0-1 range (divide by 255)
                    val r = (pixels[pixelIndex].toInt() and 0xFF) / 255f
                    val g = (pixels[pixelIndex + 1].toInt() and 0xFF) / 255f
                    val b = (pixels[pixelIndex + 2].toInt() and 0xFF) / 255f

                    // HWC format: [H, W, C]
                    val baseIdx = (y * targetSize + x) * 3
                    inputData[baseIdx] = r
                    inputData[baseIdx + 1] = g
                    inputData[baseIdx + 2] = b
                }
            }
        }

        val shape = longArrayOf(1, targetSize.toLong(), targetSize.toLong(), 3)
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape)
    }

    private fun parseConditionsOutput(
        results: OrtSession.Result,
        confThreshold: Float,
        originalWidth: Int,
        originalHeight: Int
    ): List<DetectionResult> {
        val detections = mutableListOf<DetectionResult>()

        try {
            // Output shape: [1, 300, 6]
            // Each proposal: [x_center, y_center, width, height, confidence, class_id]
            // Coordinates are normalized (0..1) relative to 640x640 input

            val output = results[0].value

            if (!debugPrinted) {
                debugPrinted = true
                println("Conditions output type: ${output?.javaClass?.name}")
                when (output) {
                    is Array<*> -> {
                        println("  Array size: ${output.size}")
                        val batch = output[0] as? Array<*>
                        if (batch != null && batch.size > 0) {
                            println("  Batch size: ${batch.size}")
                            // Check first few proposals
                            for (i in 0 until minOf(3, batch.size)) {
                                val proposal = batch[i] as? FloatArray
                                if (proposal != null && proposal.size >= 6) {
                                    println("  Proposal[$i]: cx=${proposal[0]}, cy=${proposal[1]}, w=${proposal[2]}, h=${proposal[3]}, conf=${proposal[4]}, class=${proposal[5]}")
                                }
                            }
                        }
                    }
                    is FloatArray -> println("  FloatArray size: ${output.size}")
                }
            }

            // Handle different possible output formats
            when (output) {
                is Array<*> -> {
                    val batch = output[0] as? Array<*>
                    if (batch != null) {
                        for (proposal in batch) {
                            if (proposal is FloatArray && proposal.size >= 6) {
                                parseProposal(proposal, confThreshold, originalWidth, originalHeight, detections)
                            }
                        }
                    }
                }
                is FloatArray -> {
                    // Flat array: reshape to [300, 6]
                    for (i in 0 until 300) {
                        val baseIdx = i * 6
                        if (baseIdx + 5 < output.size) {
                            val proposal = FloatArray(6) { output[baseIdx + it] }
                            parseProposal(proposal, confThreshold, originalWidth, originalHeight, detections)
                        }
                    }
                }
            }

            println("Conditions: Detected ${detections.size} conditions (before NMS)")
        } catch (e: Exception) {
            println("Error parsing Conditions output: ${e.message}")
            e.printStackTrace()
        }

        return applyNMS(detections, 0.45f)
    }

    private fun parseProposal(
        proposal: FloatArray,
        confThreshold: Float,
        originalWidth: Int,
        originalHeight: Int,
        detections: MutableList<DetectionResult>
    ) {
        val cx = proposal[0]
        val cy = proposal[1]
        val w = proposal[2]
        val h = proposal[3]
        val rawConfidence = proposal[4]
        val classId = proposal[5].toInt()

        // YOLOv8 outputs raw confidence (0.0 for no detection)
        // Only apply sigmoid if confidence is non-zero
        val confidence = if (rawConfidence > 0.001f || rawConfidence < -0.001f) {
            // Apply sigmoid activation
            1f / (1f + kotlin.math.exp(-rawConfidence))
        } else {
            0f // Empty proposal
        }

        if (confidence >= confThreshold) {
            // Coordinates are already normalized (0-1)
            // Use the model's predicted box dimensions but limit the size
            val boxW = (w * 0.8f).coerceAtMost(0.08f)  // Use 80% of predicted width, max 8%
            val boxH = (h * 0.8f).coerceAtMost(0.08f)  // Use 80% of predicted height, max 8%

            // Keep coordinates normalized (0-1) to match FDI format
            // App.kt will convert to pixel coordinates for rendering
            val left = (cx - boxW / 2).coerceIn(0f, 1f)
            val top = (cy - boxH / 2).coerceIn(0f, 1f)
            val right = (cx + boxW / 2).coerceIn(0f, 1f)
            val bottom = (cy + boxH / 2).coerceIn(0f, 1f)

            detections.add(
                DetectionResult(
                    boundingBox = BoundingBox(left, top, right, bottom),
                    classId = classId,
                    confidence = confidence
                )
            )
        }
    }

    private fun applyNMS(detections: List<DetectionResult>, iouThreshold: Float): List<DetectionResult> {
        if (detections.isEmpty()) return emptyList()

        val sorted = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<DetectionResult>()
        val suppressed = mutableSetOf<Int>()

        for (i in sorted.indices) {
            if (i in suppressed) continue
            selected.add(sorted[i])

            for (j in i + 1 until sorted.size) {
                if (j in suppressed) continue
                if (calculateIoU(sorted[i].boundingBox, sorted[j].boundingBox) > iouThreshold) {
                    suppressed.add(j)
                }
            }
        }

        return selected
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.left, box2.left)
        val y1 = maxOf(box1.top, box2.top)
        val x2 = minOf(box1.right, box2.right)
        val y2 = minOf(box1.bottom, box2.bottom)

        if (x2 < x1 || y2 < y1) return 0f

        val intersection = (x2 - x1) * (y2 - y1)
        val area1 = (box1.right - box1.left) * (box1.bottom - box1.top)
        val area2 = (box2.right - box2.left) * (box2.bottom - box2.top)
        val union = area1 + area2 - intersection

        return if (union > 0f) intersection / union else 0f
    }

    actual fun close() {
        session?.close()
        session = null
    }
}
