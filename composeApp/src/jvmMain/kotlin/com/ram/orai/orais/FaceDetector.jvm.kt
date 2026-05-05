package com.ram.orai.orais

import ai.onnxruntime.*
import com.ram.orai.detection.BoundingBox
import java.nio.FloatBuffer

actual class FaceDetector {
    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private var debugPrinted = false

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
        loadModel()
    }

    private fun loadModel() {
        try {
            val modelBytes = this::class.java.classLoader.getResourceAsStream("fdi_model.onnx")?.readBytes()
            if (modelBytes == null) {
                println("Failed to load FDI model: fdi_model.onnx not found in resources")
                return
            }

            session = env.createSession(modelBytes)
            println("Successfully loaded FDI tooth detection model (YOLOv5)")
        } catch (e: Exception) {
            println("Error loading FDI model: ${e.message}")
            e.printStackTrace()
        }
    }

    actual fun detectFaces(imageData: ImageData, onSuccess: (List<FaceInfo>) -> Unit, onError: (String) -> Unit) {
        val currentSession = session
        if (currentSession == null) {
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
            // Coordinates are normalized (0-1) representing center_x, center_y, width, height
            // 34 classes total (indices 0-33)

            val output = results[0].value

            when (output) {
                is Array<*> -> {
                    println("FDI: Output is Array with ${output.size} elements")
                    val batch = output[0] as? Array<*>
                    if (batch != null) {
                        println("FDI: Batch size: ${batch.size} (expected 25200 detections)")
                        
                        // YOLOv5 format: batch is array of detections, each detection is FloatArray[39]
                        val numDetections = batch.size
                        val confThreshold = 0.1f // Confidence threshold set to 0.1
                        val minClassProb = 0.15f // Higher minimum class probability to reduce false positives
                        val postNMSMinConf = 0.55f // Very high minimum confidence after NMS (was 0.45f) - aggressively reject false positives
                        val postNMSMinClassProb = 0.4f // Very high class probability requirement (was 0.3f)
                        val maxDetections = 6 // Very low maximum number of detections (was 8)
                        val minAvgConfidence = 0.6f // Very high average confidence threshold (was 0.5f) - reject if average is too low
                        val minMaxConfidence = 0.65f // Require at least one detection with very high confidence (was not checked)
                        val minDetectionsAfterNMS = 3 // Require at least 3 detections after NMS (fewer might be false positives)
                        val rejectEdgeBoxes = true // Reject boxes that touch image edges (almost always false positives)
                        val cornerThreshold = 0.08f // Reject boxes in corners (8% from each edge, was 5%)
                        val strictEdgeThreshold = 0.1f // Strict edge threshold for rejection (10% from edges)
                        // Store detection with both confidence and class probability
                        data class DetectionWithConf(val confidence: Float, val classProb: Float, val info: FaceInfo)
                        val allDetections = mutableListOf<DetectionWithConf>()
                        var processedCount = 0
                        var maxConfidenceSeen = 0f
                        var sampleCount = 0
                        val maxSamples = 100 // Sample first 100 detections to see confidence range
                        
                        // Filter out boxes that are too small or too large (likely false positives)
                        val minBoxSize = 0.02f // Minimum 2% of image size (filter tiny false positives)
                        val maxBoxSize = 0.4f  // Maximum 40% of image size (filter huge false positives)
                        val minAspectRatio = 0.3f // Minimum width/height ratio (filter very thin boxes)
                        val maxAspectRatio = 3.0f  // Maximum width/height ratio (filter very wide boxes)
                        val edgeThreshold = 0.03f // Reject boxes too close to edges (3% margin)
                        val minEdgeBoxSize = 0.08f // Minimum size for boxes near edges (8% of image)

                        for (detectionIdx in 0 until numDetections) {
                            val detection = batch[detectionIdx] as? FloatArray
                            if (detection == null || detection.size < 39) {
                                continue
                            }

                            // Extract coordinates (in pixel space 0-640): [x, y, w, h]
                            // YOLOv5 outputs pixel coordinates, need to normalize to 0-1
                            val modelSize = 640f
                            val cx = detection[0]  // center_x (pixel space)
                            val cy = detection[1]  // center_y (pixel space)
                            val w = detection[2]   // width (pixel space)
                            val h = detection[3]   // height (pixel space)
                            
                            // Normalize to 0-1 range
                            val normalizedCx = cx / modelSize
                            val normalizedCy = cy / modelSize
                            val normalizedW = w / modelSize
                            val normalizedH = h / modelSize
                            
                            // Extract confidence
                            val confidence = detection[4]
                            
                            // Extract class probabilities (indices 5-38: 34 classes)
                            var maxProb = 0f
                            var maxClass = -1
                            for (classIdx in 0 until 34) {
                                val classProb = detection[5 + classIdx]
                                if (classProb > maxProb) {
                                    maxProb = classProb
                                    maxClass = classIdx
                                }
                            }

                            // Apply confidence threshold
                            // Try both: (object confidence * class probability) and just class probability
                            // Some YOLOv5 models output differently
                            val finalConfidence1 = confidence * maxProb
                            val finalConfidence2 = maxProb // Alternative: just use class probability
                            val finalConfidence = maxOf(finalConfidence1, finalConfidence2)
                            
                            // Debug: Sample first few detections to see confidence values
                            if (sampleCount < maxSamples) {
                                if (finalConfidence > maxConfidenceSeen) {
                                    maxConfidenceSeen = finalConfidence
                                }
                                if (sampleCount < 10) {
                                    println("FDI: Sample detection $sampleCount - obj_conf=$confidence, max_class_prob=$maxProb, combined=$finalConfidence1, class_only=$finalConfidence2, using=$finalConfidence, class=$maxClass")
                                }
                                sampleCount++
                            }
                            
                            // Apply stricter filtering: both confidence threshold AND minimum class probability
                            if (finalConfidence >= confThreshold && maxProb >= minClassProb && maxClass in 0 until 34) {
                                processedCount++
                                
                                // Debug: Log first few detections
                                if (processedCount <= 5) {
                                    println("FDI: Detection $processedCount - pixel: cx=$cx, cy=$cy, w=$w, h=$h -> normalized: cx=$normalizedCx, cy=$normalizedCy, w=$normalizedW, h=$normalizedH, conf=$finalConfidence, class=$maxClass (tooth ${fdiMapping[maxClass]})")
                                }

                                // Calculate normalized bounding box coordinates
                                // Use normalized coordinates (0-1)
                                val left = (normalizedCx - normalizedW / 2).coerceIn(0f, 1f)
                                val top = (normalizedCy - normalizedH / 2).coerceIn(0f, 1f)
                                val right = (normalizedCx + normalizedW / 2).coerceIn(0f, 1f)
                                val bottom = (normalizedCy + normalizedH / 2).coerceIn(0f, 1f)
                                
                                // Filter out invalid or suspicious boxes
                                val boxWidth = right - left
                                val boxHeight = bottom - top
                                
                                // Skip boxes with invalid dimensions
                                if (boxWidth <= 0 || boxHeight <= 0 || left >= right || top >= bottom) {
                                    continue
                                }
                                
                                // Skip boxes that are too small or too large
                                if (boxWidth < minBoxSize || boxHeight < minBoxSize ||
                                    boxWidth > maxBoxSize || boxHeight > maxBoxSize) {
                                    continue
                                }
                                
                                // Filter by aspect ratio (teeth should be roughly square-ish, not extremely wide/tall)
                                val aspectRatio = boxWidth / boxHeight
                                if (aspectRatio < minAspectRatio || aspectRatio > maxAspectRatio) {
                                    continue
                                }
                                
                                // Filter boxes too close to edges (likely false positives)
                                // Teeth are usually in the center/middle of the image, not at the very edges
                                val isNearEdge = left < edgeThreshold || top < edgeThreshold || 
                                                right > (1f - edgeThreshold) || bottom > (1f - edgeThreshold)
                                if (isNearEdge) {
                                    // Only allow boxes near edges if they're quite large (likely valid edge teeth)
                                    // Otherwise reject them as edge artifacts
                                    if (boxWidth < minEdgeBoxSize || boxHeight < minEdgeBoxSize) {
                                        continue
                                    }
                                }
                                
                                // Reject boxes that touch the very edges (almost always false positives)
                                if (rejectEdgeBoxes) {
                                    val touchesEdge = left <= 0.001f || right >= 0.999f || top <= 0.001f || bottom >= 0.999f
                                    if (touchesEdge) {
                                        // Only allow if box is very large (likely valid edge tooth)
                                        if (boxWidth < 0.2f || boxHeight < 0.2f) {
                                            continue
                                        }
                                    }
                                    
                                    // Also reject boxes very close to edges (within 10%)
                                    val veryCloseToEdge = left < strictEdgeThreshold || right > (1f - strictEdgeThreshold) ||
                                                         top < strictEdgeThreshold || bottom > (1f - strictEdgeThreshold)
                                    if (veryCloseToEdge) {
                                        // Only allow if box is quite large
                                        if (boxWidth < 0.15f || boxHeight < 0.15f) {
                                            continue
                                        }
                                    }
                                }
                                
                                // Reject boxes in corners (almost always false positives)
                                val isInCorner = (left < cornerThreshold && top < cornerThreshold) ||
                                               (right > (1f - cornerThreshold) && top < cornerThreshold) ||
                                               (left < cornerThreshold && bottom > (1f - cornerThreshold)) ||
                                               (right > (1f - cornerThreshold) && bottom > (1f - cornerThreshold))
                                if (isInCorner) {
                                    // Always reject corner boxes unless they're very large
                                    if (boxWidth < 0.25f || boxHeight < 0.25f) {
                                        continue
                                    }
                                }

                                val toothNumber = fdiMapping[maxClass]
                                val detectionInfo = FaceInfo(
                                    trackingId = toothNumber,
                                    boundingBox = BoundingBox(left, top, right, bottom)
                                )

                                // Store all valid detections with both confidence and class probability
                                allDetections.add(DetectionWithConf(finalConfidence, maxProb, detectionInfo))
                            }
                        }

                        println("=".repeat(80))
                        println("FDI DEBUG: Detection Pipeline Summary")
                        println("=".repeat(80))
                        println("FDI: Processed $processedCount detections above threshold (threshold=$confThreshold)")
                        println("FDI: Valid boxes after size/aspect/edge filters: ${allDetections.size}")
                        
                        if (allDetections.isNotEmpty()) {
                            val confStats = allDetections.map { it.confidence }
                            val classProbStats = allDetections.map { it.classProb }
                            println("FDI: Confidence stats - min=${confStats.minOrNull()}, max=${confStats.maxOrNull()}, avg=${confStats.average()}")
                            println("FDI: Class prob stats - min=${classProbStats.minOrNull()}, max=${classProbStats.maxOrNull()}, avg=${classProbStats.average()}")
                            
                            // Show top 10 detections
                            println("FDI: Top 10 detections by confidence:")
                            allDetections.sortedByDescending { it.confidence }.take(10).forEachIndexed { idx, det ->
                                val confStr = String.format("%.4f", det.confidence)
                                val probStr = String.format("%.4f", det.classProb)
                                println("  ${idx + 1}. Tooth ${det.info.trackingId}, conf=$confStr, classProb=$probStr, box=${det.info.boundingBox}")
                            }
                        }
                        
                        if (maxConfidenceSeen > 0f) {
                            println("FDI: Max confidence seen in samples: $maxConfidenceSeen (threshold: $confThreshold)")
                        }

                        // Apply proper NMS to remove overlapping detections
                        // Sort by confidence (highest first)
                        val sortedDetections = allDetections.sortedByDescending { it.confidence }
                        println("FDI: Sorted detections: ${sortedDetections.size}")
                        
                        // Filter by BOTH confidence AND class probability before NMS to reduce false positives
                        val beforeConfFilter = sortedDetections.size
                        val highConfDetections = sortedDetections.filter { det ->
                            det.confidence >= postNMSMinConf && det.classProb >= postNMSMinClassProb
                        }
                        val afterConfFilter = highConfDetections.size
                        println("FDI: Confidence filter (conf>=${postNMSMinConf} AND classProb>=${postNMSMinClassProb})")
                        println("FDI:   Before: $beforeConfFilter, After: $afterConfFilter, Filtered: ${beforeConfFilter - afterConfFilter}")
                        
                        // Check if average confidence is too low - indicates mostly false positives
                        if (highConfDetections.isNotEmpty()) {
                            val confidences = highConfDetections.map { it.confidence }
                            val avgConf = confidences.average().toFloat()
                            val maxConf = confidences.maxOrNull() ?: 0f
                            
                            println("FDI: Average confidence of high-confidence detections: $avgConf (min required: $minAvgConfidence)")
                            println("FDI: Max confidence: $maxConf (min required: $minMaxConfidence)")
                            
                            // Reject if average confidence is too low OR if max confidence is too low
                            if (avgConf < minAvgConfidence || maxConf < minMaxConfidence) {
                                println("FDI: WARNING - Confidence too low (avg=$avgConf < $minAvgConfidence OR max=$maxConf < $minMaxConfidence), likely false positives. Rejecting all detections.")
                                println("FDI: ===== FINAL RESULT =====")
                                println("FDI: Total detections: 0 (rejected due to low confidence)")
                                println("FDI: Tooth numbers: []")
                                println("=".repeat(80))
                                // Don't add any detections - teeth list remains empty
                            } else {
                                // Average confidence is good, proceed with NMS
                                println("FDI: High-confidence detections:")
                                highConfDetections.take(10).forEachIndexed { idx, det ->
                                    val confStr = String.format("%.4f", det.confidence)
                                    val probStr = String.format("%.4f", det.classProb)
                                    println("  ${idx + 1}. Tooth ${det.info.trackingId}, conf=$confStr, classProb=$probStr")
                                }
                                
                                // Apply NMS with stricter IoU threshold (lower = more aggressive)
                                val beforeNMS = highConfDetections.size
                                val nmsResult = applyNMSWithIoU(
                                    highConfDetections.map { it.info }, 
                                    highConfDetections.map { it.confidence }, 
                                    0.3f  // More aggressive NMS (was 0.4f)
                                )
                                val afterNMS = nmsResult.size
                                println("FDI: NMS (IoU=0.4)")
                                println("FDI:   Before: $beforeNMS, After: $afterNMS, Removed: ${beforeNMS - afterNMS}")
                                
                                // Limit maximum number of detections to prevent false positives
                                val beforeLimit = nmsResult.size
                                val limitedResult = nmsResult.take(maxDetections)
                                val afterLimit = limitedResult.size
                                println("FDI: Limit (max=$maxDetections)")
                                println("FDI:   Before: $beforeLimit, After: $afterLimit, Removed: ${beforeLimit - afterLimit}")
                                
                                // Reject if too few detections (likely all false positives)
                                if (afterLimit < minDetectionsAfterNMS) {
                                    println("FDI: WARNING - Too few detections ($afterLimit < $minDetectionsAfterNMS), likely false positives. Rejecting all.")
                                    println("FDI: ===== FINAL RESULT =====")
                                    println("FDI: Total detections: 0 (rejected - too few detections)")
                                    println("FDI: Tooth numbers: []")
                                    println("=".repeat(80))
                                    // Don't add any detections
                                } else {
                                    teeth.addAll(limitedResult)
                                }
                            }
                        } else {
                            println("FDI: No high-confidence detections found after filtering")
                        }

                        val toothNumbers = teeth.mapNotNull { it.trackingId }.sorted()
                        println("FDI: ===== FINAL RESULT =====")
                        println("FDI: Total detections: ${teeth.size}")
                        println("FDI: Tooth numbers: $toothNumbers")
                        if (teeth.isNotEmpty()) {
                            println("FDI: Final detection details:")
                            teeth.forEachIndexed { idx, det ->
                                println("  ${idx + 1}. Tooth ${det.trackingId}, box=${det.boundingBox}")
                            }
                        }
                        println("=".repeat(80))
                        
                        // Don't fall back to low-confidence detections - if nothing passes the filters, return empty
                        // This prevents false positives when there are no teeth in the image
                        if (teeth.isEmpty()) {
                            println("FDI: WARNING - No detections passed all filters!")
                            println("FDI: This is expected when there are no teeth in the image")
                        }
                    } else {
                        println("FDI: Batch is null")
                    }
                }
                else -> {
                    println("FDI: Unexpected output format: ${output::class.simpleName}")
                }
            }
        } catch (e: Exception) {
            println("Error parsing FDI output: ${e.message}")
            e.printStackTrace()
        }

        return teeth
    }

    private fun applyNMS(detections: List<FaceInfo>, iouThreshold: Float): List<FaceInfo> {
        // Legacy method - kept for compatibility
        return applyNMSWithIoU(detections, detections.map { 1.0f }, iouThreshold)
    }
    
    private fun applyNMSWithIoU(
        detections: List<FaceInfo>, 
        confidences: List<Float>,
        iouThreshold: Float
    ): List<FaceInfo> {
        if (detections.isEmpty()) return emptyList()
        
        // Create list with confidence for sorting
        val detectionsWithConf = detections.zip(confidences).sortedByDescending { it.second }
        val selected = mutableListOf<FaceInfo>()
        val suppressed = BooleanArray(detectionsWithConf.size) { false }
        
        for (i in detectionsWithConf.indices) {
            if (suppressed[i]) continue
            
            val current = detectionsWithConf[i].first
            selected.add(current)
            
            // Suppress overlapping detections
            for (j in i + 1 until detectionsWithConf.size) {
                if (suppressed[j]) continue
                
                val other = detectionsWithConf[j].first
                val iou = calculateIoU(current.boundingBox, other.boundingBox)
                
                // Suppress if IoU is high (overlapping) OR if same tooth number
                if (iou > iouThreshold || current.trackingId == other.trackingId) {
                    suppressed[j] = true
                }
            }
        }
        
        return selected
    }

    private fun calculateBoxArea(box: BoundingBox): Float {
        return (box.right - box.left) * (box.bottom - box.top)
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.left, box2.left)
        val y1 = maxOf(box1.top, box2.top)
        val x2 = minOf(box1.right, box2.right)
        val y2 = minOf(box1.bottom, box2.bottom)

        if (x2 < x1 || y2 < y1) return 0f

        val intersection = (x2 - x1) * (y2 - y1)
        val area1 = calculateBoxArea(box1)
        val area2 = calculateBoxArea(box2)
        val union = area1 + area2 - intersection

        return if (union > 0f) intersection / union else 0f
    }

    actual fun close() {
        session?.close()
        session = null
    }
}
