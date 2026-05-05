package com.ram.orai.orais

import android.content.Context
import ai.onnxruntime.*
import com.ram.orai.detection.BoundingBox
import java.nio.FloatBuffer

actual class FaceDetector {
    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private var debugPrinted = false
    private var context: Context? = null

    // FDI tooth number mapping (index -> tooth number)
    // Updated for YOLOv5 model with 34 classes
    private val fdiMapping = intArrayOf(
        11, 12, 13, 14, 15, 16, 17, 18,  // 0-7
        21, 22, 23, 24, 25, 26, 27, 28,  // 8-15
        31, 32, 327, 33, 34, 35, 36, 37, 38,  // 16-24 (note: 327 at index 18)
        41, 42, 43, 44, 45, 46, 47, 48,  // 25-32
        56  // 33
    )

    init {
        // Context will be set later, load model when context is available
    }

    fun setContext(ctx: Context) {
        context = ctx
        loadModel()
    }

    private fun loadModel() {
        try {
            val ctx = context ?: run {
                println("FDI: Context not set, cannot load model")
                return
            }

            val assetManager = ctx.assets
            val availableFiles = assetManager.list("") ?: emptyArray()
            println("FDI: Available files in assets: ${availableFiles.joinToString()}")
            
            if (!availableFiles.contains("fdi_model.onnx")) {
                println("FDI: Model file not found: fdi_model.onnx")
                println("FDI: Available files: ${availableFiles.joinToString()}")
                return
            }

            val modelBytes = assetManager.open("fdi_model.onnx").use { inputStream ->
                inputStream.readBytes()
            }
            println("FDI: Model file loaded: ${modelBytes.size} bytes")

            session = env.createSession(modelBytes)
            println("FDI: Successfully loaded FDI tooth detection model (YOLOv5)")
        } catch (e: Exception) {
            println("FDI: Error loading model: ${e.message}")
            e.printStackTrace()
        }
    }

    actual fun detectFaces(imageData: ImageData, onSuccess: (List<FaceInfo>) -> Unit, onError: (String) -> Unit) {
        val currentSession = session ?: run {
            println("FDI: Model not loaded, cannot detect")
            onError("FDI model not loaded")
            return
        }

        try {
            println("FDI: Starting detection for image ${imageData.width}x${imageData.height}")
            // Preprocess image
            val inputTensor = preprocessImage(imageData)
            println("FDI: Image preprocessed, running inference...")

            // Run inference
            val inputs = mapOf(currentSession.inputNames.first() to inputTensor)
            val results = currentSession.run(inputs)
            println("FDI: Inference completed, parsing output...")

            // Parse FDI tooth detection output
            val teeth = parseFDIOutput(results, imageData.width, imageData.height)
            println("FDI: Parsed ${teeth.size} teeth from output")

            // Close ONNX resources
            results.close()
            inputTensor.close()

            onSuccess(teeth)
        } catch (e: Exception) {
            println("FDI: Detection error: ${e.message}")
            e.printStackTrace()
            onError("Tooth detection error: ${e.message}")
        }
    }

    private fun preprocessImage(imageData: ImageData): OnnxTensor {
        val targetSize = 640 // Model expects 640x640 input
        val inputData = FloatArray(1 * 3 * targetSize * targetSize)

        // Convert RGBA bytes to normalized RGB float array [1, 3, 640, 640] (CHW format for YOLOv5)
        val pixels = imageData.bytes
        val scaleX = imageData.width.toFloat() / targetSize
        val scaleY = imageData.height.toFloat() / targetSize

        // Separate R, G, B channels
        val rChannel = FloatArray(targetSize * targetSize)
        val gChannel = FloatArray(targetSize * targetSize)
        val bChannel = FloatArray(targetSize * targetSize)

        for (y in 0 until targetSize) {
            for (x in 0 until targetSize) {
                val srcX = (x * scaleX).toInt().coerceIn(0, imageData.width - 1)
                val srcY = (y * scaleY).toInt().coerceIn(0, imageData.height - 1)
                val pixelIndex = (srcY * imageData.width + srcX) * 4 // Assuming RGBA

                if (pixelIndex + 2 < pixels.size) {
                    val r = (pixels[pixelIndex].toInt() and 0xFF) / 255f
                    val g = (pixels[pixelIndex + 1].toInt() and 0xFF) / 255f
                    val b = (pixels[pixelIndex + 2].toInt() and 0xFF) / 255f

                    val pixelIdx = y * targetSize + x
                    rChannel[pixelIdx] = r
                    gChannel[pixelIdx] = g
                    bChannel[pixelIdx] = b
                }
            }
        }

        // Interleave channels in CHW format: [C, H, W] = [R..., G..., B...]
        var idx = 0
        for (c in 0 until 3) {
            val channel = when (c) {
                0 -> rChannel
                1 -> gChannel
                else -> bChannel
            }
            for (i in channel.indices) {
                inputData[idx++] = channel[i]
            }
        }

        val shape = longArrayOf(1, 3, targetSize.toLong(), targetSize.toLong())
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape)
    }

    private fun parseFDIOutput(
        results: OrtSession.Result,
        originalWidth: Int,
        originalHeight: Int
    ): List<FaceInfo> {
        val teeth = mutableListOf<FaceInfo>()

        try {
            // YOLOv5 model output: [1, 25200, 39] in format [batch, detections, data_per_detection]
            // Each detection: [x, y, w, h, confidence, class_0_prob, class_1_prob, ..., class_33_prob]
            // Coordinates are in pixel space (0-640) representing center_x, center_y, width, height
            // 34 classes total (indices 0-33)
            
            // IMPORTANT: Preprocessing resizes original image to 640x640 model input
            // The model outputs coordinates in 640x640 space
            // We need to map these back to original image space (originalWidth x originalHeight)
            // The preprocessing uses: scaleX = originalWidth/640, scaleY = originalHeight/640
            // So model coordinate (cx, cy) maps to original: (cx * scaleX, cy * scaleY)
            val modelSize = 640f
            val scaleX = originalWidth.toFloat() / modelSize  // e.g., 1280/640 = 2.0
            val scaleY = originalHeight.toFloat() / modelSize  // e.g., 720/640 = 1.125

            val output = results[0].value

            when (output) {
                is Array<*> -> {
                    val batch = output[0] as? Array<*>
                    if (batch != null) {
                        println("FDI: Processing ${batch.size} detections")
                        println("FDI: Original image: ${originalWidth}x${originalHeight}, scale: ($scaleX, $scaleY)")
                        
                        // Simple thresholds - no complex filtering
                        val confThreshold = 0.25f  // Basic confidence threshold
                        val minClassProb = 0.2f     // Basic class probability threshold
                        
                        val allDetections = mutableListOf<Pair<FaceInfo, Float>>()

                        for (detectionIdx in 0 until batch.size) {
                            val detection = batch[detectionIdx] as? FloatArray
                            if (detection == null || detection.size < 39) continue

                            // Extract coordinates (in pixel space 0-640): [x, y, w, h]
                            // YOLOv5 outputs pixel coordinates in model space (640x640)
                            val cx = detection[0]  // center_x (model space, 0-640)
                            val cy = detection[1]  // center_y (model space, 0-640)
                            val w = detection[2]   // width (model space, 0-640)
                            val h = detection[3]   // height (model space, 0-640)
                            
                            // Debug: Log raw coordinates for first few detections
                            if (allDetections.size < 3 && detectionIdx < 10) {
                                println("FDI: Raw detection $detectionIdx - model space: cx=$cx, cy=$cy, w=$w, h=$h")
                            }
                            
                            // Map from model space (640x640) to original image space
                            // Model coordinates need to be scaled by the preprocessing scale factors
                            val originalCx = cx * scaleX  // Map to original image X
                            val originalCy = cy * scaleY  // Map to original image Y
                            val originalW = w * scaleX    // Map to original image width
                            val originalH = h * scaleY    // Map to original image height
                            
                            // Now normalize to 0-1 range based on original image dimensions
                            val normalizedCx = originalCx / originalWidth
                            val normalizedCy = originalCy / originalHeight
                            val normalizedW = originalW / originalWidth
                            val normalizedH = originalH / originalHeight
                            
                            if (allDetections.size < 3 && detectionIdx < 5) {
                                println("FDI: Model->Original: cx=$cx->$originalCx, cy=$cy->$originalCy")
                                println("FDI: Normalized: cx=$normalizedCx, cy=$normalizedCy, w=$normalizedW, h=$normalizedH")
                            }
                            
                            val confidence = detection[4]
                            
                            // Find best class
                            var maxProb = 0f
                            var maxClass = -1
                            for (classIdx in 0 until 34) {
                                val classProb = detection[5 + classIdx]
                                if (classProb > maxProb) {
                                    maxProb = classProb
                                    maxClass = classIdx
                                }
                            }

                            // Simple confidence calculation
                            val finalConfidence = confidence * maxProb

                            // Basic threshold check
                            if (finalConfidence >= confThreshold && maxProb >= minClassProb && maxClass in 0 until fdiMapping.size) {
                                // Calculate normalized bounding box coordinates (0-1)
                                val left = (normalizedCx - normalizedW / 2).coerceIn(0f, 1f)
                                val top = (normalizedCy - normalizedH / 2).coerceIn(0f, 1f)
                                val right = (normalizedCx + normalizedW / 2).coerceIn(0f, 1f)
                                val bottom = (normalizedCy + normalizedH / 2).coerceIn(0f, 1f)
                                
                                // Basic size check only
                                val boxWidth = right - left
                                val boxHeight = bottom - top
                                if (boxWidth > 0 && boxHeight > 0 && boxWidth < 0.5f && boxHeight < 0.5f) {
                                    val toothNumber = fdiMapping[maxClass]
                                    
                                    // Debug: Log first few detections to verify coordinates
                                    if (allDetections.size < 3) {
                                        println("FDI: Detection ${allDetections.size + 1} - pixel: cx=$cx, cy=$cy, w=$w, h=$h -> normalized: cx=$normalizedCx, cy=$normalizedCy, w=$normalizedW, h=$normalizedH -> box: left=$left, top=$top, right=$right, bottom=$bottom, tooth=$toothNumber")
                                    }
                                    
                                    val detectionInfo = FaceInfo(
                                        trackingId = toothNumber,
                                        boundingBox = BoundingBox(left, top, right, bottom)
                                    )
                                    allDetections.add(Pair(detectionInfo, finalConfidence))
                                }
                            }
                        }

                        println("FDI: Found ${allDetections.size} detections before NMS")
                        
                        // Simple NMS - sort by confidence and remove overlaps
                        val sorted = allDetections.sortedByDescending { it.second }
                        val selected = mutableListOf<FaceInfo>()
                        val suppressed = BooleanArray(sorted.size) { false }
                        
                        for (i in sorted.indices) {
                            if (suppressed[i]) continue
                            selected.add(sorted[i].first)
                            
                            for (j in i + 1 until sorted.size) {
                                if (suppressed[j]) continue
                                val iou = calculateIoU(sorted[i].first.boundingBox, sorted[j].first.boundingBox)
                                if (iou > 0.4f || sorted[i].first.trackingId == sorted[j].first.trackingId) {
                                    suppressed[j] = true
                                }
                            }
                        }
                        
                        // Limit to top detections
                        teeth.addAll(selected.take(10))
                        println("FDI: Final detections after NMS: ${teeth.size}")
                    }
                }
                is FloatArray -> {
                    // Handle flat array format
                    println("FDI: Processing flat array with ${output.size} elements")
                    val confThreshold = 0.25f
                    val minClassProb = 0.2f
                    val allDetections = mutableListOf<Pair<FaceInfo, Float>>()
                    
                    // IMPORTANT: Same coordinate transformation as Array case
                    val modelSize = 640f
                    val scaleX = originalWidth.toFloat() / modelSize
                    val scaleY = originalHeight.toFloat() / modelSize
                    
                    for (i in 0 until 25200) {
                        val baseIdx = i * 39
                        if (baseIdx + 38 < output.size) {
                            // Extract coordinates (in pixel space 0-640): [x, y, w, h]
                            // YOLOv5 outputs pixel coordinates in model space (640x640)
                            val cx = output[baseIdx]  // center_x (model space, 0-640)
                            val cy = output[baseIdx + 1]  // center_y (model space, 0-640)
                            val w = output[baseIdx + 2]   // width (model space, 0-640)
                            val h = output[baseIdx + 3]   // height (model space, 0-640)
                            
                            // Map from model space (640x640) to original image space
                            val originalCx = cx * scaleX
                            val originalCy = cy * scaleY
                            val originalW = w * scaleX
                            val originalH = h * scaleY
                            
                            // Normalize to 0-1 range based on original image dimensions
                            val normalizedCx = originalCx / originalWidth
                            val normalizedCy = originalCy / originalHeight
                            val normalizedW = originalW / originalWidth
                            val normalizedH = originalH / originalHeight
                            
                            val confidence = output[baseIdx + 4]
                            
                            var maxProb = 0f
                            var maxClass = -1
                            for (classIdx in 0 until 34) {
                                val classProb = output[baseIdx + 5 + classIdx]
                                if (classProb > maxProb) {
                                    maxProb = classProb
                                    maxClass = classIdx
                                }
                            }
                            
                            val finalConfidence = confidence * maxProb
                            
                            if (finalConfidence >= confThreshold && maxProb >= minClassProb && maxClass in 0 until fdiMapping.size) {
                                // Calculate normalized bounding box coordinates (0-1)
                                val left = (normalizedCx - normalizedW / 2).coerceIn(0f, 1f)
                                val top = (normalizedCy - normalizedH / 2).coerceIn(0f, 1f)
                                val right = (normalizedCx + normalizedW / 2).coerceIn(0f, 1f)
                                val bottom = (normalizedCy + normalizedH / 2).coerceIn(0f, 1f)
                                
                                val boxWidth = right - left
                                val boxHeight = bottom - top
                                if (boxWidth > 0 && boxHeight > 0 && boxWidth < 0.5f && boxHeight < 0.5f) {
                                    val toothNumber = fdiMapping[maxClass]
                                    val detectionInfo = FaceInfo(
                                        trackingId = toothNumber,
                                        boundingBox = BoundingBox(left, top, right, bottom)
                                    )
                                    allDetections.add(Pair(detectionInfo, finalConfidence))
                                }
                            }
                        }
                    }
                    
                    // Simple NMS
                    val sorted = allDetections.sortedByDescending { it.second }
                    val selected = mutableListOf<FaceInfo>()
                    val suppressed = BooleanArray(sorted.size) { false }
                    
                    for (i in sorted.indices) {
                        if (suppressed[i]) continue
                        selected.add(sorted[i].first)
                        
                        for (j in i + 1 until sorted.size) {
                            if (suppressed[j]) continue
                            val iou = calculateIoU(sorted[i].first.boundingBox, sorted[j].first.boundingBox)
                            if (iou > 0.4f || sorted[i].first.trackingId == sorted[j].first.trackingId) {
                                suppressed[j] = true
                            }
                        }
                    }
                    
                    teeth.addAll(selected.take(10))
                    println("FDI: Final detections after NMS: ${teeth.size}")
                }
                else -> {
                    println("FDI: Unexpected output format: ${output::class.simpleName}")
                }
            }
        } catch (e: Exception) {
            println("FDI: Error parsing output: ${e.message}")
            e.printStackTrace()
        }

        return teeth
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
