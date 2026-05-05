package com.ram.orai.orais

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.RowScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.SolidColor
import java.text.SimpleDateFormat
import java.util.*
import com.ram.orai.detection.BoundingBox

enum class DetectionMode {
    NONE,              // No detection
    FDI_ONLY,          // Only FDI tooth detection
    CONDITIONS_ONLY,   // Only condition detection
    CONDITIONS_AND_FDI // Both FDI and conditions
}

// Persistent detection data for scanner-like behavior
data class PersistentDetection(
    val toothNumber: Int,
    val boundingBox: BoundingBox,
    val condition: String?,
    val baseConfidence: Float,
    val persistenceCount: Int = 1,
    val lastSeen: Long = System.currentTimeMillis()
) {
    // Calculate boosted confidence based on persistence
    val boostedConfidence: Float
        get() = (baseConfidence * (1f + persistenceCount * 0.1f)).coerceAtMost(1f)
}


// Helper function to create icon with label
@Composable
fun IconWithLabel(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    iconTint: Color = Color.Unspecified,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelSmall,
    textColor: Color = Color.Unspecified
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(iconSize),
            tint = iconTint
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = textStyle,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        var currentFrameDetections by remember { mutableStateOf<List<ToothDetection>>(emptyList()) }
        var accumulatedTeethData by remember { mutableStateOf<Map<Int, ToothConditionHistory>>(emptyMap()) }
        var persistentDetections by remember { mutableStateOf<Map<Int, PersistentDetection>>(emptyMap()) }
        // All-time detections for debug view (accumulates all detections ever seen)
        var allTimeDetections by remember { mutableStateOf<Set<String>>(emptySet()) }
        var currentPatientId by remember { mutableStateOf<String?>(null) }
        var detectionStatus by remember { mutableStateOf("Ready") }
        var isFaceRecognitionMode by remember { mutableStateOf(false) }
        // Store actual camera image dimensions for coordinate transformation
        var cameraImageWidth by remember { mutableStateOf(1280f) }
        var cameraImageHeight by remember { mutableStateOf(720f) }
        var isCastingEnabled by remember { mutableStateOf(false) }
        var castingUrl by remember { mutableStateOf<String?>(null) }
        var detectionMode by remember { mutableStateOf(DetectionMode.NONE) }
        var isRecording by remember { mutableStateOf(false) }
        var showGallery by remember { mutableStateOf(false) }
        var showCameraSelector by remember { mutableStateOf(false) }
        var showCameraButtonSettings by remember { mutableStateOf(false) }
        var toastMessage by remember { mutableStateOf<String?>(null) }
        var videoSaveError by remember { mutableStateOf<VideoSaveResult?>(null) }
        var flipCamera by remember { mutableStateOf(false) }

        val modelInference = remember { ModelInference() }
        val faceDetector = remember { FaceDetector() }
        
        // Set context for FaceDetector on Android
        LaunchedEffect(Unit) {
            setFaceDetectorContext(faceDetector)
        }
        val castManager = remember { ScreenCastManager() }
        val fileManager = remember { FileManager() }
        val preferencesManager = remember { PreferencesManager() }
        val scope = rememberCoroutineScope()

        // Load models on startup
        LaunchedEffect(Unit) {
            println("Starting model loading...")
            
            // Set context for Android
            setModelInferenceContext(modelInference)
            
            // Load model with platform-specific path
            val modelPath = getYoloModelPath()
            println("Attempting to load model: $modelPath")
            val yoloLoaded = modelInference.loadModel(modelPath)
            
            // FaceDetector (FDI model) loads automatically in its init block
            if (yoloLoaded) {
                detectionStatus = "Models loaded (YOLO + FDI)"
                println("SUCCESS: Models loaded")
            } else {
                detectionStatus = "Failed to load YOLO model: $modelPath"
                println("ERROR: Failed to load YOLO model: $modelPath")
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                modelInference.close()
                faceDetector.close()
            }
        }

        // System UI padding to avoid status bar and navigation bar overlap
        val systemUiPaddingTop = 24.dp // Status bar padding
        val systemUiPaddingBottom = 16.dp // Navigation bar padding
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = systemUiPaddingTop, bottom = systemUiPaddingBottom)
        ) {
            // Camera interface - starts automatically on app open
            val cameraManager: CameraController = remember { getCameraManager(null, null) }
            
            // Load camera button settings
            var cameraButtonSettings by remember { 
                mutableStateOf(loadCameraButtonSettings(preferencesManager))
            }
            
            // Create button dispatcher (after cameraManager is available)
            val buttonDispatcher = remember(cameraButtonSettings, cameraManager) {
                CameraButtonDispatcher(
                    settings = cameraButtonSettings,
                    onCapture = {
                        scope.launch {
                            captureImageWithDetections(cameraManager, currentFrameDetections, fileManager, flipCamera)
                            toastMessage = "Photo saved"
                            delay(2000)
                            toastMessage = null
                        }
                    },
                    onLight = {
                        // TODO: Implement light toggle if camera supports it
                        scope.launch {
                            toastMessage = "Light toggle (not implemented)"
                            delay(1000)
                            toastMessage = null
                        }
                    },
                    onLongPressStart = {
                        // Start video recording
                        scope.launch {
                            if (!isRecording) {
                                val filename = "video_${getCurrentTimestamp()}.mp4"
                                val videoPath = startVideoRecording(cameraManager, fileManager, filename, flipCamera)
                                if (videoPath != null) {
                                    isRecording = true
                                    toastMessage = "Recording started..."
                                } else {
                                    toastMessage = "Failed to start recording"
                                    isRecording = false
                                }
                            }
                        }
                    },
                    onLongPressEnd = {
                        // Stop video recording
                        scope.launch {
                            if (isRecording) {
                                val videoPath = stopVideoRecording(cameraManager)
                                if (videoPath != null) {
                                    val filename = videoPath.substringAfterLast("/")
                                    val result = fileManager.saveVideo(videoPath, filename)
                                    if (result.success) {
                                        toastMessage = "Video saved"
                                    } else {
                                        toastMessage = "Video saved with warnings"
                                    }
                                    isRecording = false
                                } else {
                                    toastMessage = "Recording stopped"
                                    isRecording = false
                                }
                            }
                        }
                    }
                )
            }
            
            // Initialize button handler
            val buttonHandler = remember(cameraButtonSettings) {
                if (cameraButtonSettings.interfaceType != HardwareInterfaceType.NONE) {
                    createCameraButtonHandler(cameraButtonSettings)
                } else {
                    null
                }
            }
            
            LaunchedEffect(buttonHandler, cameraButtonSettings) {
                // Stop old handler
                buttonHandler?.stopListening()
                
                // Start new handler with updated settings
                buttonHandler?.startListening { event ->
                    buttonDispatcher.handleEvent(event)
                }
            }
            
            // Platform-specific button handler effect
            CameraButtonHandlerEffect(buttonHandler, buttonDispatcher)

                LaunchedEffect(cameraManager, currentPatientId, detectionMode) {
                    cameraManager.startPreview()

                    // Process frames for detection - optimized for real-time response
                    // Use sample to throttle detection processing (every 50ms) for faster response
                    scope.launch(Dispatchers.Default) {
                        cameraManager.cameraFrames
                            .sample(50) // Process detection every 50ms for faster response
                            .flowOn(Dispatchers.Default)
                            .collect { frame ->
                                val imageData = convertToImageData(frame)

                                if (imageData != null && !isFaceRecognitionMode) {
                                    // Update actual camera image dimensions for coordinate transformation
                                    val newWidth = imageData.width.toFloat()
                                    val newHeight = imageData.height.toFloat()
                                    if (cameraImageWidth != newWidth || cameraImageHeight != newHeight) {
                                        println("Camera: Image dimensions changed: ${cameraImageWidth}x${cameraImageHeight} -> ${newWidth}x${newHeight}")
                                        cameraImageWidth = newWidth
                                        cameraImageHeight = newHeight
                                    }
                                    when (detectionMode) {
                                        DetectionMode.NONE -> {
                                            // No detection - clear detections and persistent detections
                                            // Keep allTimeDetections for debug view
                                            currentFrameDetections = emptyList()
                                            persistentDetections = emptyMap()
                                            detectionStatus = "Mode: None | No detection"
                                        }
                                        
                                        DetectionMode.FDI_ONLY -> {
                                            // Only FDI tooth detection - NO conditions
                                            // Clear any previous conditions
                                            persistentDetections = emptyMap<Int, PersistentDetection>()
                                            allTimeDetections = emptySet<String>()
                                            
                                            val fdiDeferred = kotlinx.coroutines.CompletableDeferred<List<FaceInfo>>()
                                            
                                            faceDetector.detectFaces(
                                                imageData = imageData,
                                                onSuccess = { detected -> 
                                                    if (detected.isNotEmpty()) {
                                                        println("FDI ONLY: Detected ${detected.size} teeth")
                                                    } else {
                                                        println("FDI ONLY: No teeth detected in this frame")
                                                    }
                                                    fdiDeferred.complete(detected)
                                                },
                                                onError = { error ->
                                                    println("FDI ONLY: Detection error: $error")
                                                    fdiDeferred.complete(emptyList())
                                                }
                                            )

                                            val fdiTeeth = try {
                                                kotlinx.coroutines.withTimeout(2000) {
                                                    fdiDeferred.await()
                                                }
                                            } catch (e: Exception) {
                                                println("FDI ONLY: Detection timeout or error: ${e.message}")
                                                emptyList()
                                            }

                                            // Convert to detections with NO conditions - ensure condition is explicitly null
                                            val detections = fdiTeeth.map { tooth ->
                                                val detection = ToothDetection(
                                                    toothNumber = tooth.trackingId ?: -1,
                                                    boundingBox = tooth.boundingBox,
                                                    condition = null,  // Explicitly null - no conditions in FDI_ONLY mode
                                                    conditionConfidence = null
                                                )
                                                // Verify condition is null
                                                if (detection.condition != null) {
                                                    println("FDI ONLY: WARNING - Condition is not null for tooth ${detection.toothNumber}!")
                                                }
                                                println("FDI ONLY: Created detection for tooth ${detection.toothNumber}, condition=${detection.condition}, box: (${detection.boundingBox.left}, ${detection.boundingBox.top}, ${detection.boundingBox.right}, ${detection.boundingBox.bottom})")
                                                detection
                                            }
                                            
                                            println("FDI ONLY: Created ${detections.size} detections (NO conditions)")
                                            // Verify all detections have null condition
                                            detections.forEach { det ->
                                                if (det.condition != null) {
                                                    println("FDI ONLY: ERROR - Detection for tooth ${det.toothNumber} has condition: ${det.condition}")
                                                }
                                            }
                                            currentFrameDetections = detections
                                            
                                            detectionStatus = "Mode: FDI Only | Teeth: ${detections.size}"
                                        }
                                        
                                        DetectionMode.CONDITIONS_ONLY -> {
                                            // Only detect conditions - NO FDI
                                            // Clear any previous FDI detections
                                            persistentDetections = emptyMap<Int, PersistentDetection>()
                                            allTimeDetections = emptySet<String>()
                                            
                                            println("CONDITIONS ONLY: Running inference on ${imageData.width}x${imageData.height} image...")
                                            val conditions = modelInference.runInference(imageData, confThreshold = 0.25f)
                                            
                                            // Debug logging
                                            println("CONDITIONS ONLY: Model returned ${conditions.size} detections")
                                            if (conditions.isNotEmpty()) {
                                                conditions.forEachIndexed { idx, cond ->
                                                    println("CONDITIONS ONLY: Detection $idx - Class: ${cond.classId}, Confidence: ${cond.confidence}, Box: (${cond.boundingBox.left}, ${cond.boundingBox.top}, ${cond.boundingBox.right}, ${cond.boundingBox.bottom})")
                                                }
                                            } else {
                                                println("CONDITIONS ONLY: No conditions detected in this frame")
                                            }
                                            
                                            // Convert conditions to tooth detections without FDI
                                            val detections = conditions.map { condition ->
                                                val conditionLabels = listOf("Normal", "Initial Caries", "Moderate Caries", "Severe Caries", "Tooth Stain", "Dental Calculus", "Other Lesions")
                                                val conditionName = if (condition.classId in conditionLabels.indices) {
                                                    conditionLabels[condition.classId]
                                                } else "Unknown"
                                                
                                                ToothDetection(
                                                    toothNumber = -1, // No tooth number in conditions-only mode
                                                    boundingBox = condition.boundingBox,
                                                    condition = conditionName,
                                                    conditionConfidence = condition.confidence
                                                )
                                            }
                                            
                                            println("CONDITIONS ONLY: Converted to ${detections.size} detections")
                                            
                                            currentFrameDetections = detections
                                            
                                            detectionStatus = "Mode: Conditions Only | Detections: ${detections.size}"
                                        }
                                        
                                        DetectionMode.CONDITIONS_AND_FDI -> {
                                            // Run both FDI and conditions detection - works without patient ID
                                            // Use CompletableDeferred to wait for FDI detection
                                            val fdiDeferred = kotlinx.coroutines.CompletableDeferred<List<FaceInfo>>()
                                            
                                            faceDetector.detectFaces(
                                                imageData = imageData,
                                                onSuccess = { detected -> 
                                                    if (detected.isNotEmpty()) {
                                                        println("FDI detected: ${detected.size} teeth")
                                                    }
                                                    fdiDeferred.complete(detected)
                                                },
                                                onError = { error ->
                                                    println("FDI detection error: $error")
                                                    fdiDeferred.complete(emptyList())
                                                }
                                            )

                                            // Wait for FDI detection to complete
                                            val fdiTeeth = try {
                                                kotlinx.coroutines.withTimeout(2000) {
                                                    fdiDeferred.await()
                                                }
                                            } catch (e: Exception) {
                                                println("FDI detection timeout or error: ${e.message}")
                                                emptyList()
                                            }

                                            val conditions = modelInference.runInference(imageData, confThreshold = 0.25f)
                                            
                                            // Debug logging
                                            if (conditions.isNotEmpty()) {
                                                println("Conditions detected: ${conditions.size} detections")
                                            }

                                            // Combine FDI teeth with matching conditions
                                            val newDetections = combineFDIAndConditions(fdiTeeth, conditions)
                                            
                                            println("FDI+Conditions: ${fdiTeeth.size} FDI teeth, ${conditions.size} conditions, ${newDetections.size} combined detections")

                                            // Update persistent detections (scanner-like behavior)
                                            val updatedPersistent = persistentDetections.toMutableMap()
                                            val currentTime = System.currentTimeMillis()
                                            val maxAge = 5000L // Remove detections older than 5 seconds
                                            
                                            // Remove old detections
                                            updatedPersistent.entries.removeAll { (_, detection) ->
                                                currentTime - detection.lastSeen > maxAge
                                            }
                                            
                                            // Update or add new detections
                                            newDetections.forEach { detection ->
                                                val toothNumber = detection.toothNumber
                                                val existing = updatedPersistent[toothNumber]
                                                
                                                if (existing != null) {
                                                    // Check if detection is similar (same tooth number and similar position)
                                                    val boxOverlap = boxesOverlap(existing.boundingBox, detection.boundingBox)
                                                    if (boxOverlap) {
                                                        // Update existing: increase persistence count and boost confidence
                                                        val newPersistenceCount = existing.persistenceCount + 1
                                                        val newConfidence = detection.conditionConfidence ?: existing.baseConfidence
                                                        updatedPersistent[toothNumber] = existing.copy(
                                                            boundingBox = detection.boundingBox, // Update position
                                                            condition = detection.condition ?: existing.condition,
                                                            baseConfidence = newConfidence,
                                                            persistenceCount = newPersistenceCount,
                                                            lastSeen = currentTime
                                                        )
                                                    } else {
                                                        // Different position, replace
                                                        updatedPersistent[toothNumber] = PersistentDetection(
                                                            toothNumber = toothNumber,
                                                            boundingBox = detection.boundingBox,
                                                            condition = detection.condition,
                                                            baseConfidence = detection.conditionConfidence ?: 0.5f,
                                                            persistenceCount = 1,
                                                            lastSeen = currentTime
                                                        )
                                                    }
                                                } else {
                                                    // New detection
                                                    updatedPersistent[toothNumber] = PersistentDetection(
                                                        toothNumber = toothNumber,
                                                        boundingBox = detection.boundingBox,
                                                        condition = detection.condition,
                                                        baseConfidence = detection.conditionConfidence ?: 0.5f,
                                                        persistenceCount = 1,
                                                        lastSeen = currentTime
                                                    )
                                                }
                                            }
                                            
                                            persistentDetections = updatedPersistent
                                            
                                            // Use NEW detections from current frame for real-time boxes (appear/disappear immediately)
                                            currentFrameDetections = newDetections

                                            // Accumulate all-time detections for debug view (never removes)
                                            val updatedAllTime = allTimeDetections.toMutableSet()
                                            newDetections.forEach { detection ->
                                                val detectionKey = if (detection.toothNumber > 0) {
                                                    "${detection.toothNumber}-${detection.condition ?: "Normal"}"
                                                } else {
                                                    "Condition-${detection.condition ?: "Unknown"}"
                                                }
                                                updatedAllTime.add(detectionKey)
                                            }
                                            allTimeDetections = updatedAllTime

                                            // Accumulate readings for each tooth (only if patient is selected)
                                            // Use persistent detections for patient data (with boosted confidence)
                                            if (currentPatientId != null) {
                                                val updatedData = accumulatedTeethData.toMutableMap()
                                                // Convert persistent detections for patient history (with boosted confidence)
                                                val persistentDetectionsForHistory = persistentDetections.values.map { persistent ->
                                                    ToothDetection(
                                                        toothNumber = persistent.toothNumber,
                                                        boundingBox = persistent.boundingBox,
                                                        condition = persistent.condition,
                                                        conditionConfidence = persistent.boostedConfidence
                                                    )
                                                }
                                                persistentDetectionsForHistory.forEach { tooth ->
                                                    val history = updatedData.getOrPut(tooth.toothNumber) {
                                                        ToothConditionHistory(tooth.toothNumber)
                                                    }

                                                    // Add new reading
                                                    history.readings.add(
                                                        ConditionReading(
                                                            condition = tooth.condition ?: "Normal",
                                                            confidence = tooth.conditionConfidence ?: 1.0f,
                                                            timestamp = getCurrentTimestamp()
                                                        )
                                                    )

                                                    // Calculate most common condition
                                                    val conditionCounts = history.readings.groupingBy { it.condition }.eachCount()
                                                    val mostCommon = conditionCounts.maxByOrNull { it.value }?.key ?: "Normal"
                                                    val avgConfidence = history.readings
                                                        .filter { it.condition == mostCommon }
                                                        .map { it.confidence }
                                                        .average()
                                                        .toFloat()

                                                    updatedData[tooth.toothNumber] = history.copy(
                                                        mostCommonCondition = mostCommon,
                                                        confidence = avgConfidence
                                                    )
                                                }
                                                accumulatedTeethData = updatedData
                                                val maxPersistence = persistentDetections.values.maxOfOrNull { it.persistenceCount } ?: 0
                                                detectionStatus = "Mode: Conditions + FDI | Patient: $currentPatientId | Detections: ${newDetections.size} | Scanned: ${accumulatedTeethData.size} teeth | Max Scan: $maxPersistence"
                                            } else {
                                                val maxPersistence = persistentDetections.values.maxOfOrNull { it.persistenceCount } ?: 0
                                                detectionStatus = "Mode: Conditions + FDI | Detections: ${newDetections.size} | Max Scan: $maxPersistence | Image: ${imageData.width}x${imageData.height}"
                                            }
                                        }
                                    }
                                }
                            }
                    }
                }

                DisposableEffect(cameraManager) {
                    onDispose {
                        cameraManager.stopPreview()
                    }
                }

                // Full screen layout with layered elements
                // 1. Camera preview (bottom layer)
                CameraPreview(
                    cameraManager = cameraManager, 
                    modifier = Modifier.fillMaxSize(),
                    flipHorizontal = flipCamera
                )

                // 2. Detection overlay (middle layer) - show current frame detections
                ToothDetectionOverlay(
                    teeth = currentFrameDetections,
                    modifier = Modifier.fillMaxSize(),
                    flipHorizontal = flipCamera,
                    cameraImageWidth = cameraImageWidth,
                    cameraImageHeight = cameraImageHeight
                )

                    // Camera selector dropdown overlay
                    if (showCameraSelector) {
                        CameraSelectorDropdown(
                            cameraManager = cameraManager,
                            onDismiss = { showCameraSelector = false },
                            onCameraSelected = { index ->
                                cameraManager.switchToCamera(index)
                                showCameraSelector = false
                            }
                        )
                    }

                    // Gallery overlay
                    if (showGallery) {
                        GalleryView(
                            fileManager = fileManager,
                            onDismiss = { showGallery = false }
                        )
                    }

                    // 3. UI controls (top layer)
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val isHorizontal = maxWidth > 600.dp // Consider horizontal if width > 600dp
                val isDesktop = maxWidth > 1000.dp // Desktop typically has wider screens
                
                // Debug overlay showing detection info on camera feed (hidden when gallery is open)
                // Show all-time detections in CONDITIONS_AND_FDI mode, current detections otherwise
                // Small compact overlay positioned on camera feed area (below top bar)
                // Position: Account for system status bar (~48dp) + Column padding (16dp) + top bar (~70dp) = ~134dp
                if (detectionMode == DetectionMode.CONDITIONS_AND_FDI && allTimeDetections.isNotEmpty() && !showGallery) {
                    DetectionDebugOverlay(
                        allTimeDetections = allTimeDetections,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = 24.dp, y = 160.dp)
                            .zIndex(10f)
                    )
                } else if (currentFrameDetections.isNotEmpty() && !showGallery) {
                    DetectionDebugOverlay(
                        detections = currentFrameDetections,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = 24.dp, y = 160.dp)
                            .zIndex(10f)
                    )
                }
                
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        // Top status bar with mode selection and casting indicator (hidden when gallery is open)
                // Fixed at top - always reserves same space to maintain consistent position
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .height(56.dp), // Fixed height to maintain consistent position
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                    if (!showGallery) {
                                // Camera info card showing active camera
                                var availableCameras by remember { mutableStateOf<List<CameraInfo>>(emptyList()) }
                                var currentCameraIndex by remember { mutableStateOf(0) }
                                
                                LaunchedEffect(cameraManager) {
                                    try {
                                        availableCameras = cameraManager.getAvailableCameras()
                                        currentCameraIndex = cameraManager.getCurrentCameraIndex()
                                    } catch (e: Exception) {
                                        println("Error getting camera info: ${e.message}")
                                    }
                                }
                                
                                // Refresh camera info periodically to catch camera switches
                                LaunchedEffect(Unit) {
                                    while (true) {
                                        delay(500) // Check every 500ms
                                        try {
                                            val newIndex = cameraManager.getCurrentCameraIndex()
                                            if (newIndex != currentCameraIndex) {
                                                currentCameraIndex = newIndex
                                                availableCameras = cameraManager.getAvailableCameras()
                                            }
                                        } catch (e: Exception) {
                                            // Ignore errors during refresh
                                        }
                                    }
                                }
                                
                                val currentCamera = availableCameras.getOrNull(currentCameraIndex)
                                val cameraName = currentCamera?.let {
                                    when {
                                        it.name.contains("Back", ignoreCase = true) -> "Back Camera"
                                        it.name.contains("Front", ignoreCase = true) -> "Front Camera"
                                        it.isUvc -> "USB Camera"
                                        else -> it.name
                                    }
                                } ?: "Camera"
                                
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF1E1E2E).copy(alpha = 0.95f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                ) {
                                    Text(
                                        text = cameraName,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        color = Color(0xFFE0E0E0),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                // Mode selection dropdown with modern design
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                    Card(
                                    modifier = Modifier
                                        .clickable { expanded = true }
                                            .shadow(4.dp, RoundedCornerShape(12.dp)),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFF2196F3).copy(alpha = 0.9f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = when (detectionMode) {
                                            DetectionMode.NONE -> "None"
                                            DetectionMode.FDI_ONLY -> "FDI Only"
                                            DetectionMode.CONDITIONS_ONLY -> "Conditions Only"
                                            DetectionMode.CONDITIONS_AND_FDI -> "FDI + Conditions"
                                        },
                                        color = Color.White,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                    )
                                            Text("▼", color = Color.White.copy(alpha = 0.8f), fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                        }
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                        modifier = Modifier.shadow(8.dp, RoundedCornerShape(12.dp))
                                ) {
                                    DropdownMenuItem(
                                            text = { Text("None", fontWeight = FontWeight.Medium) },
                                        onClick = {
                                            detectionMode = DetectionMode.NONE
                                            expanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                            text = { Text("FDI Only", fontWeight = FontWeight.Medium) },
                                        onClick = {
                                            detectionMode = DetectionMode.FDI_ONLY
                                            expanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                            text = { Text("Conditions Only", fontWeight = FontWeight.Medium) },
                                        onClick = {
                                            detectionMode = DetectionMode.CONDITIONS_ONLY
                                            expanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                            text = { Text("FDI + Conditions", fontWeight = FontWeight.Medium) },
                                        onClick = {
                                            detectionMode = DetectionMode.CONDITIONS_AND_FDI
                                            expanded = false
                                        }
                                    )
                                }
                            }

                                // Settings button with modern design
                                val settingsInteractionSource = remember { MutableInteractionSource() }
                                val isSettingsPressed by settingsInteractionSource.collectIsPressedAsState()
                                val settingsScale by animateFloatAsState(
                                    targetValue = if (isSettingsPressed) 0.9f else 1f,
                                    animationSpec = tween(100), label = ""
                                )
                                
                                IconButton(
                                onClick = { showCameraButtonSettings = true },
                                    modifier = Modifier
                                        .graphicsLayer { scaleX = settingsScale; scaleY = settingsScale }
                                        .shadow(4.dp, RoundedCornerShape(10.dp)),
                                    interactionSource = settingsInteractionSource
                                ) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFF6366F1)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = MaterialIcons.Settings,
                                                contentDescription = "Settings",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                // Cast button with modern design - small icon box without text
                                val castInteractionSource = remember { MutableInteractionSource() }
                                val isCastPressed by castInteractionSource.collectIsPressedAsState()
                                val castScale by animateFloatAsState(
                                    targetValue = if (isCastPressed) 0.9f else 1f,
                                    animationSpec = tween(100), label = ""
                                )
                                
                                IconButton(
                                onClick = {
                                    if (!isCastingEnabled) {
                                        castManager.startCasting(
                                            onSuccess = { info ->
                                                isCastingEnabled = true
                                                castingUrl = info
                                                detectionStatus = "Casting: $info"
                                            },
                                            onError = { error ->
                                                detectionStatus = "Cast error: $error"
                                            }
                                        )
                                    } else {
                                        castManager.stopCasting()
                                        isCastingEnabled = false
                                        castingUrl = null
                                        detectionStatus = "Casting stopped"
                                    }
                                },
                                enabled = castManager.isCastingAvailable(),
                                    modifier = Modifier
                                        .graphicsLayer { scaleX = castScale; scaleY = castScale }
                                        .shadow(4.dp, RoundedCornerShape(10.dp)),
                                    interactionSource = castInteractionSource
                                ) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isCastingEnabled) Color(0xFF10B981) else Color(0xFF6B7280)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = MaterialIcons.Radio,
                                                contentDescription = "Cast",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Accumulated teeth summary box (side panel)
                // Only show in CONDITIONS_AND_FDI mode to keep layout consistent with other modes
                if (detectionMode == DetectionMode.CONDITIONS_AND_FDI && currentPatientId != null && accumulatedTeethData.isNotEmpty()) {
                            AccumulatedTeethSummary(
                                teethData = accumulatedTeethData,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 250.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Bottom control bar with camera controls
                        CameraControlBar(
                            cameraManager = cameraManager,
                            isRecording = isRecording,
                            flipCamera = flipCamera,
                            onSwitchCamera = { showCameraSelector = true },
                            onFlipCamera = { flipCamera = !flipCamera },
                            onCapture = {
                                scope.launch {
                                    captureImageWithDetections(cameraManager, currentFrameDetections, fileManager, flipCamera)
                                    toastMessage = "Photo saved"
                                    delay(2000)
                                    toastMessage = null
                                }
                            },
                            onRecord = {
                                isRecording = !isRecording
                                if (isRecording) {
                                    scope.launch {
                                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                        val filename = "Orai_Video_$timestamp.mp4"
                                        val result = startVideoRecording(cameraManager, fileManager, filename, flipCamera)
                                        if (result != null) {
                                            detectionStatus = "Recording started..."
                                        } else {
                                            detectionStatus = "Failed to start recording"
                                            isRecording = false
                                        }
                                    }
                                } else {
                                    scope.launch {
                                        val videoPath = stopVideoRecording(cameraManager)
                                        if (videoPath != null) {
                                            // Extract filename from path
                                            val filename = videoPath.substringAfterLast("/")
                                            println("Attempting to save video to gallery: $filename")
                                            println("Video path: $videoPath")
                                            
                                            // Save video to gallery using MediaStore
                                            val result = fileManager.saveVideo(videoPath, filename)
                                            if (result.success) {
                                                if (result.errorMessage != null) {
                                                    // Partial success - show warning
                                                    videoSaveError = result
                                                } else {
                                                    toastMessage = "Video saved to gallery"
                                                    println("✅ Video saved to gallery: $filename")
                                                }
                                            } else {
                                                // Complete failure - show error dialog
                                                videoSaveError = result
                                                println("❌ ERROR: Video save failed")
                                                println("❌ Error: ${result.errorMessage}")
                                            }
                                            delay(2000)
                                            toastMessage = null
                                        } else {
                                            detectionStatus = "Recording stopped"
                                            println("WARNING: stopVideoRecording returned null")
                                        }
                                    }
                                }
                            },
                            onGallery = { showGallery = true }
                        )

                        // Patient control buttons (if patient is selected)
                        if (currentPatientId != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            PatientControlButtons(
                                currentPatientId = currentPatientId,
                                isFaceRecognitionMode = isFaceRecognitionMode,
                                onFaceUnlock = {
                                    isFaceRecognitionMode = true
                                    detectionStatus = "Scanning face..."
                                    scope.launch {
                                        delay(2000) // Simulate face recognition
                                        currentPatientId = "PATIENT_${getCurrentTimestamp() % 10000}"
                                        isFaceRecognitionMode = false
                                        detectionStatus = "Patient identified: $currentPatientId"
                                    }
                                },
                                onSendToAPI = {
                                    scope.launch {
                                        sendPatientDataToAPI(currentPatientId, accumulatedTeethData)
                                    }
                                },
                                onCancel = {
                                    currentPatientId = null
                                    currentFrameDetections = emptyList()
                                    accumulatedTeethData = emptyMap()
                                    detectionStatus = "Cancelled"
                                }
                            )
                        }
                    }
                }
                
                // Toast notification
                toastMessage?.let { message ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    ToastNotification(
                        message = message,
                        modifier = Modifier
                    )
                }
                }
                
                // Video save error dialog
                videoSaveError?.let { error ->
                    VideoSaveErrorDialog(
                        error = error,
                        onDismiss = { videoSaveError = null }
                    )
                }
            
            // Camera button settings dialog
            if (showCameraButtonSettings) {
                val currentButtonHandler = buttonHandler
                CameraButtonSettingsDialog(
                    currentSettings = cameraButtonSettings,
                    onDismiss = { showCameraButtonSettings = false },
                    onSave = { newSettings ->
                        cameraButtonSettings = newSettings
                        saveCameraButtonSettings(preferencesManager, newSettings)
                        showCameraButtonSettings = false
                        // Restart handler with new settings
                        scope.launch {
                            currentButtonHandler?.stopListening()
                        }
                    }
                )
            }
        }
    }
}

// New combined tooth detection overlay
@Composable
private fun ToothDetectionOverlay(
    teeth: List<ToothDetection>,
    modifier: Modifier = Modifier,
    flipHorizontal: Boolean = false,
    cameraImageWidth: Float = 1280f,
    cameraImageHeight: Float = 720f
) {
    val textMeasurer = rememberTextMeasurer()
    val labelFontSize = MaterialTheme.typography.labelLarge.fontSize

    // Debug: Log detections
    LaunchedEffect(teeth.size, teeth.map { it.toothNumber }.joinToString()) {
        println("ToothDetectionOverlay: Received ${teeth.size} detections")
        if (teeth.isNotEmpty()) {
            teeth.forEachIndexed { idx, tooth ->
                val box = tooth.boundingBox
                val boxWidth = box.right - box.left
                val boxHeight = box.bottom - box.top
                println("  Detection $idx: Tooth #${tooth.toothNumber}, Condition: ${tooth.condition}")
                println("    Box: (${box.left}, ${box.top}, ${box.right}, ${box.bottom})")
                println("    Box size (normalized): ${boxWidth}x${boxHeight}")
            }
        } else {
            println("  No detections to draw")
        }
    }

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        if (teeth.isEmpty()) {
            println("Canvas: No teeth to draw, canvas size: ${canvasWidth}x${canvasHeight}")
            return@Canvas
        }

        println("Canvas: Drawing ${teeth.size} detections on canvas ${canvasWidth}x${canvasHeight}")
        println("Canvas: Using actual camera image dimensions: ${cameraImageWidth}x${cameraImageHeight}")
        
        // Log first detection coordinates to verify they're changing
        if (teeth.isNotEmpty()) {
            val firstBox = teeth[0].boundingBox
            val firstCenterX = (firstBox.left + firstBox.right) / 2f
            val firstCenterY = (firstBox.top + firstBox.bottom) / 2f
            println("Canvas: First detection center: ($firstCenterX, $firstCenterY) - should change if object moves!")
        }

        // Model preprocessing: camera image (e.g., 1280x720) -> 640x640 model input
        // The model outputs coordinates in 640x640 space, normalized to 0-1
        // These normalized coordinates map directly to the original camera image
        // because the preprocessing samples proportionally
        
        // Use actual camera image dimensions (not hardcoded)
        val cameraWidth = cameraImageWidth
        val cameraHeight = cameraImageHeight
        
        // Calculate how camera preview is displayed on canvas
        // ContentScale.Fit scales to fit canvas, maintaining aspect ratio (no cropping)
        val scaleX = canvasWidth / cameraWidth
        val scaleY = canvasHeight / cameraHeight
        val previewScale = minOf(scaleX, scaleY) // Fit uses min to fit within canvas
        
        // Calculate the displayed image size and centering offsets
        val displayedWidth = cameraWidth * previewScale
        val displayedHeight = cameraHeight * previewScale
        val offsetX = (canvasWidth - displayedWidth) / 2f  // Center horizontally
        val offsetY = (canvasHeight - displayedHeight) / 2f  // Center vertically
        
        println("Canvas: canvas=$canvasWidth x $canvasHeight, camera=$cameraWidth x $cameraHeight")
        println("Canvas: Preview scale=$previewScale, displayed size=$displayedWidth x $displayedHeight, offset=($offsetX, $offsetY)")

        teeth.forEachIndexed { idx, tooth ->
            val box = tooth.boundingBox
            
            // Debug: Log tooth info
            println("Canvas: Processing detection $idx - Tooth #${tooth.toothNumber}, box: (${box.left}, ${box.top}, ${box.right}, ${box.bottom})")
            val normalizedWidth = box.right - box.left
            val normalizedHeight = box.bottom - box.top
            val centerX = (box.left + box.right) / 2f
            val centerY = (box.top + box.bottom) / 2f
            println("Canvas:   Normalized size: ${normalizedWidth}x${normalizedHeight}, center: ($centerX, $centerY)")
            
            // Validate bounding box coordinates (should be normalized 0-1)
            if (box.left < 0 || box.top < 0 || box.right > 1 || box.bottom > 1 ||
                box.left >= box.right || box.top >= box.bottom) {
                println("Canvas: Invalid bounding box $idx (tooth #${tooth.toothNumber}): (${box.left}, ${box.top}, ${box.right}, ${box.bottom})")
                return@forEachIndexed
            }
            
            // If box is extremely small, it might be at the wrong scale - log warning
            if (normalizedWidth < 0.001f || normalizedHeight < 0.001f) {
                println("Canvas: WARNING - Box $idx is extremely small (${normalizedWidth}x${normalizedHeight}), might be coordinate issue")
            }
            
            // Check if box is at center (might indicate coordinate issue)
            val isAtCenter = kotlin.math.abs(centerX - 0.5f) < 0.1f && kotlin.math.abs(centerY - 0.5f) < 0.1f
            if (isAtCenter) {
                println("Canvas: ⚠️ WARNING - Box $idx (tooth #${tooth.toothNumber}) is at center! Center: ($centerX, $centerY)")
            }
            
            // Step 1: Normalized coordinates (0-1) from model map directly to camera image (0-1)
            // If flipped, mirror the X coordinates horizontally
            val normalizedLeft = if (flipHorizontal) 1f - box.right else box.left
            val normalizedRight = if (flipHorizontal) 1f - box.left else box.right
            
            // Map to camera pixel coordinates
            val cameraLeft = normalizedLeft * cameraWidth
            val cameraTop = box.top * cameraHeight
            val cameraRight = normalizedRight * cameraWidth
            val cameraBottom = box.bottom * cameraHeight
            
            println("Canvas:   Camera coords: left=$cameraLeft, top=$cameraTop, right=$cameraRight, bottom=$cameraBottom")
            
            // Step 2: Transform from camera space to canvas space
            // Apply preview scale, then add offset to center the image
            var left = cameraLeft * previewScale + offsetX
            var top = cameraTop * previewScale + offsetY
            var right = cameraRight * previewScale + offsetX
            var bottom = cameraBottom * previewScale + offsetY
            
            println("Canvas:   Canvas coords: left=$left, top=$top, right=$right, bottom=$bottom")
            
            println("Canvas: Box $idx - Normalized: (${box.left}, ${box.top}) -> Camera: (${cameraLeft}, ${cameraTop}) -> Canvas: (${left}, ${top})")
            
            // Step 3: Check if box is within the displayed image area (not in letterbox)
            // With ContentScale.Fit, the image is centered, so check if box is in displayed area
            val isInDisplayedArea = left < displayedWidth + offsetX && right > offsetX && 
                                   top < displayedHeight + offsetY && bottom > offsetY
            
            if (!isInDisplayedArea) {
                println("Canvas: Box $idx is outside displayed image area, skipping")
                println("Canvas:   Canvas coords: (${left}, ${top}, ${right}, ${bottom}), displayed area: ($offsetX, $offsetY, ${offsetX + displayedWidth}, ${offsetY + displayedHeight})")
                return@forEachIndexed
            }
            
            // Step 4: Calculate box dimensions before expansion
            var boxWidth = right - left
            var boxHeight = bottom - top
            
            // Ensure minimum box size for visibility
            // If box is too small, expand it around its center, but keep center in bounds
            val minBoxSize = 30f // Increased to make boxes more visible
            val originalWidth = boxWidth
            val originalHeight = boxHeight
            
            if (boxWidth < minBoxSize && boxWidth > 0) {
                val centerX = (left + right) / 2f
                val halfSize = minBoxSize / 2f
                // Try to expand around original center
                var newLeft = centerX - halfSize
                var newRight = centerX + halfSize
                
                // If expansion goes outside bounds, shift the box to stay within bounds
                if (newLeft < offsetX) {
                    // Shift right to fit
                    val shift = offsetX - newLeft
                    newLeft = offsetX
                    newRight = (centerX + halfSize + shift).coerceAtMost(offsetX + displayedWidth)
                }
                if (newRight > offsetX + displayedWidth) {
                    // Shift left to fit
                    val shift = newRight - (offsetX + displayedWidth)
                    newRight = offsetX + displayedWidth
                    newLeft = (centerX - halfSize - shift).coerceAtLeast(offsetX)
                }
                
                // Ensure minimum width
                if (newRight - newLeft < minBoxSize) {
                    if (newLeft <= offsetX) {
                        newRight = (offsetX + minBoxSize).coerceAtMost(offsetX + displayedWidth)
                    } else {
                        newLeft = ((offsetX + displayedWidth) - minBoxSize).coerceAtLeast(offsetX)
                    }
                }
                
                left = newLeft
                right = newRight
                boxWidth = right - left
                println("Canvas: Expanded box $idx width from $originalWidth to $boxWidth (min size: $minBoxSize), center: $centerX -> ${(left + right) / 2f}, final: (${left}, ${right})")
            }
            if (boxHeight < minBoxSize && boxHeight > 0) {
                val centerY = (top + bottom) / 2f
                val halfSize = minBoxSize / 2f
                // Try to expand around original center
                var newTop = centerY - halfSize
                var newBottom = centerY + halfSize
                
                // If expansion goes outside bounds, shift the box to stay within bounds
                if (newTop < offsetY) {
                    // Shift down to fit
                    val shift = offsetY - newTop
                    newTop = offsetY
                    newBottom = (centerY + halfSize + shift).coerceAtMost(offsetY + displayedHeight)
                }
                if (newBottom > offsetY + displayedHeight) {
                    // Shift up to fit
                    val shift = newBottom - (offsetY + displayedHeight)
                    newBottom = offsetY + displayedHeight
                    newTop = (centerY - halfSize - shift).coerceAtLeast(offsetY)
                }
                
                // Ensure minimum height
                if (newBottom - newTop < minBoxSize) {
                    if (newTop <= offsetY) {
                        newBottom = (offsetY + minBoxSize).coerceAtMost(offsetY + displayedHeight)
                    } else {
                        newTop = ((offsetY + displayedHeight) - minBoxSize).coerceAtLeast(offsetY)
                    }
                }
                
                top = newTop
                bottom = newBottom
                boxHeight = bottom - top
                println("Canvas: Expanded box $idx height from $originalHeight to $boxHeight (min size: $minBoxSize), center: $centerY -> ${(top + bottom) / 2f}, final: (${top}, ${bottom})")
            }
            
            // Final clamp to ensure coordinates are within displayed area
            left = left.coerceIn(offsetX, offsetX + displayedWidth)
            top = top.coerceIn(offsetY, offsetY + displayedHeight)
            right = right.coerceIn(offsetX, offsetX + displayedWidth)
            bottom = bottom.coerceIn(offsetY, offsetY + displayedHeight)
            
            // Recalculate dimensions after final clamp
            boxWidth = right - left
            boxHeight = bottom - top
            
            // Skip if box is still invalid
            if (boxWidth <= 0 || boxHeight <= 0) {
                println("Canvas: Box $idx (tooth #${tooth.toothNumber}) has invalid dimensions: ${boxWidth}x${boxHeight}, skipping")
                return@forEachIndexed
            }

            // Draw box colors: Yellow for FDI, Green for "Normal" condition, Red for other conditions
            val boxColor = when {
                tooth.toothNumber > 0 -> Color.Yellow // FDI teeth in yellow
                tooth.condition == "Normal" -> Color.Green // Normal condition in green
                tooth.condition != null -> Color.Red // Other conditions in red
                else -> Color.Green // Default to green
            }
            
            println("Canvas: Drawing box $idx (tooth #${tooth.toothNumber}) at (${left}, ${top}) size ${boxWidth}x${boxHeight}, color=$boxColor")
            println("Canvas:   Original calculated position was (${(left + right) / 2f - boxWidth / 2f}, ${(top + bottom) / 2f - boxHeight / 2f})")

            // TEMPORARY: Draw debug center point and coordinates for FDI_ONLY mode
            val debugCenterX = (left + right) / 2f
            val debugCenterY = (top + bottom) / 2f
            
            // Draw debug center point (red circle)
            drawCircle(
                color = Color.Red,
                radius = 15f,
                center = Offset(debugCenterX, debugCenterY)
            )
            
            // Draw debug text showing normalized center coordinates
            val debugText = "#${tooth.toothNumber}\nN:(${String.format("%.2f", centerX)},${String.format("%.2f", centerY)})"
            val debugTextLayout = textMeasurer.measure(
                text = debugText,
                style = TextStyle(
                    fontSize = labelFontSize * 0.8f,
                    color = Color.Yellow,
                    background = Color.Black.copy(alpha = 0.7f)
                )
            )
            // Draw debug text using drawText
            drawText(
                textLayoutResult = debugTextLayout,
                topLeft = Offset(
                    debugCenterX + 20f,
                    debugCenterY - debugTextLayout.size.height / 2
                )
            )

            // Draw filled semi-transparent background for better visibility
            drawRect(
                color = boxColor.copy(alpha = 0.2f),
                topLeft = Offset(left, top),
                size = Size(boxWidth, boxHeight)
            )
            
            // Draw box with moderate thickness
            drawRect(
                color = boxColor,
                topLeft = Offset(left, top),
                size = Size(boxWidth, boxHeight),
                style = Stroke(width = 3f)
            )

            // Draw tooth number and condition label
            // In FDI_ONLY mode: only show tooth number (condition will be null)
            // In CONDITIONS_ONLY mode: only show condition (toothNumber will be -1)
            // In CONDITIONS_AND_FDI mode: show both tooth number and condition
            val labelText = buildString {
                if (tooth.toothNumber > 0) {
                    append("#${tooth.toothNumber}")
                }
                // Only show condition if it's actually detected (not null)
                // In FDI_ONLY mode, condition will be null, so don't show anything
                tooth.condition?.let { condition ->
                    if (tooth.toothNumber > 0) append("\n")
                    append(condition)
                    tooth.conditionConfidence?.let { conf ->
                        append(" ${(conf * 100).toInt()}%")
                    }
                } ?: run {
                    // If no condition and no tooth number, show "Condition" (for CONDITIONS_ONLY mode)
                    if (tooth.toothNumber <= 0) {
                        append("Condition")
                    }
                    // If toothNumber > 0 but condition is null (FDI_ONLY mode), don't add anything
                }
            }

            if (labelText.isNotBlank()) {
                val textLayoutResult = textMeasurer.measure(
                    text = labelText,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = labelFontSize,
                        fontWeight = FontWeight.Bold,
                        background = Color.Black.copy(alpha = 0.8f)
                    )
                )
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        left + 4,
                        maxOf(0f, top - textLayoutResult.size.height - 4)
                    )
                )
            }
        }
    }
}

// Debug overlay to show detection info
@Composable
private fun DetectionDebugOverlay(
    detections: List<ToothDetection>? = null,
    allTimeDetections: Set<String>? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E2E).copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .widthIn(max = 160.dp)
        ) {
            if (allTimeDetections != null) {
                // Show all-time accumulated detections (for CONDITIONS_AND_FDI mode)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Text(
                        text = "Detections",
                        color = Color(0xFFE0E0E0),
                        style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                    Surface(
                        color = Color(0xFF2196F3).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${allTimeDetections.size}",
                            color = Color(0xFF2196F3),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                
                // Show top 5 detections sorted
                allTimeDetections
                    .sorted()
                    .take(5)
                    .forEach { detectionKey ->
                        Surface(
                            color = Color(0xFF2A2A3E).copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(vertical = 1.dp)
                        ) {
                        Text(
                            text = detectionKey,
                                color = Color(0xFFE0E0E0),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
            } else if (detections != null) {
                // Show current frame detections (for other modes)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Text(
                        text = "Detections",
                        color = Color(0xFFE0E0E0),
                        style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                    Surface(
                        color = Color(0xFF2196F3).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${detections.size}",
                            color = Color(0xFF2196F3),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                
                // Show top 5 detections sorted by confidence
                detections
                    .sortedByDescending { it.conditionConfidence ?: 0f }
                    .take(5)
                    .forEach { detection ->
                        // In FDI_ONLY mode, don't show conditions (they should be null)
                        val label = if (detection.toothNumber > 0) {
                            if (detection.condition != null) {
                                "#${detection.toothNumber} = ${detection.condition}"
                            } else {
                                "#${detection.toothNumber}"  // No condition in FDI_ONLY mode
                            }
                        } else {
                            "Condition: ${detection.condition ?: "Unknown"}"
                        }
                        val confidence = detection.conditionConfidence ?: 0f
                        Surface(
                            color = Color(0xFF2A2A3E).copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(vertical = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                        Text(
                            text = label,
                                    color = Color(0xFFE0E0E0),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (confidence > 0f) {
                                    Text(
                                        text = "${(confidence * 100).toInt()}%",
                                        color = Color(0xFF9CA3AF),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun AccumulatedTeethSummary(
    teethData: Map<Int, ToothConditionHistory>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E2E).copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                    text = "Scan Results",
                    color = Color(0xFFE0E0E0),
                    style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
                Surface(
                    color = Color(0xFF2196F3).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${teethData.size} teeth",
                        color = Color(0xFF2196F3),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            teethData.values.sortedBy { it.toothNumber }.forEach { history ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2A2A3E).copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = Color(0xFF6366F1).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                                    text = "#${history.toothNumber}",
                                    color = Color(0xFF6366F1),
                        style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                            }
                    Text(
                                text = history.mostCommonCondition,
                                color = Color(0xFFE0E0E0),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = if (history.mostCommonCondition == "Normal") 
                                    Color(0xFF10B981).copy(alpha = 0.2f) 
                                else 
                                    Color(0xFFEF4444).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${(history.confidence * 100).toInt()}%",
                                    color = if (history.mostCommonCondition == "Normal") 
                                        Color(0xFF10B981) 
                                    else 
                                        Color(0xFFEF4444),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                color = Color(0xFF6B7280).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${history.readings.size}x",
                                    color = Color(0xFF9CA3AF),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatientControlButtons(
    currentPatientId: String?,
    isFaceRecognitionMode: Boolean,
    onFaceUnlock: () -> Unit,
    onSendToAPI: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilledTonalButton(
            onClick = onFaceUnlock,
            enabled = !isFaceRecognitionMode && currentPatientId == null,
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .shadow(4.dp, RoundedCornerShape(12.dp)),
            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                containerColor = Color(0xFF6366F1),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                if (isFaceRecognitionMode) "Scanning..." else "Face Unlock",
                fontWeight = FontWeight.SemiBold
            )
        }

        FilledTonalButton(
            onClick = onSendToAPI,
            enabled = currentPatientId != null,
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .shadow(4.dp, RoundedCornerShape(12.dp)),
            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                containerColor = Color(0xFF10B981),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Send to API", fontWeight = FontWeight.SemiBold)
        }

        OutlinedButton(
            onClick = onCancel,
            enabled = currentPatientId != null,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFFEF4444)
            ),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFEF4444))
        ) {
            Text("Cancel", fontWeight = FontWeight.SemiBold)
        }
    }
}

// API function to send aggregated patient data
private suspend fun sendPatientDataToAPI(patientId: String?, teethData: Map<Int, ToothConditionHistory>) {
    if (patientId == null) return

    val jsonData = buildString {
        append("{")
        append("\"patientId\":\"$patientId\",")
        append("\"timestamp\":${getCurrentTimestamp()},")
        append("\"teeth\":[")
        teethData.values.sortedBy { it.toothNumber }.forEachIndexed { index, history ->
            if (index > 0) append(",")
            append("{")
            append("\"toothNumber\":${history.toothNumber},")
            append("\"condition\":\"${history.mostCommonCondition}\",")
            append("\"confidence\":${history.confidence},")
            append("\"readingsCount\":${history.readings.size}")
            append("}")
        }
        append("]")
        append("}")
    }

    println("Sending to API: $jsonData")
    // TODO: Implement actual HTTP POST to case history app
}


// Helper function to convert CameraFrame to ImageData (platform-agnostic)
private fun convertToImageData(frame: CameraFrame): ImageData? {
    return try {
        val bitmap = frame.imageBitmap
        val width = bitmap.width
        val height = bitmap.height

        val pixelMap = bitmap.toPixelMap()
        val bytes = ByteArray(width * height * 4) // RGBA format

        var idx = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = pixelMap[x, y]
                // Compose Color components are in 0f..1f, convert to 0-255
                val r = (c.red * 255f).toInt().coerceIn(0, 255)
                val g = (c.green * 255f).toInt().coerceIn(0, 255)
                val b = (c.blue * 255f).toInt().coerceIn(0, 255)
                val a = (c.alpha * 255f).toInt().coerceIn(0, 255)

                // Store as RGBA (matches what detection models expect)
                bytes[idx++] = r.toByte()
                bytes[idx++] = g.toByte()
                bytes[idx++] = b.toByte()
                bytes[idx++] = a.toByte()
            }
        }

        ImageData(width, height, 0, bytes)
    } catch (e: Exception) {
        println("Error converting frame to ImageData: ${e.message}")
        e.printStackTrace()
        null
    }
}

// Combine FDI tooth detections with overlapping condition detections
private fun combineFDIAndConditions(
    fdiTeeth: List<FaceInfo>,
    conditions: List<DetectionResult>
): List<ToothDetection> {
    val conditionLabels = listOf("Normal", "Initial Caries", "Moderate Caries", "Severe Caries", "Tooth Stain", "Dental Calculus", "Other Lesions")

    return fdiTeeth.mapNotNull { tooth ->
        val toothNumber = tooth.trackingId ?: return@mapNotNull null

        // Find overlapping condition
        val overlappingCondition = conditions.firstOrNull { condition ->
            boxesOverlap(tooth.boundingBox, condition.boundingBox)
        }

        ToothDetection(
            toothNumber = toothNumber,
            boundingBox = tooth.boundingBox,
            condition = overlappingCondition?.let {
                if (it.classId in conditionLabels.indices) conditionLabels[it.classId] else "Unknown"
            },
            conditionConfidence = overlappingCondition?.confidence
        )
    }
}

// Check if two bounding boxes overlap
private fun boxesOverlap(box1: com.ram.orai.detection.BoundingBox, box2: com.ram.orai.detection.BoundingBox): Boolean {
    return !(box1.right < box2.left || box1.left > box2.right ||
             box1.bottom < box2.top || box1.top > box2.bottom)
}

// Bottom control bar with camera controls
@Composable
private fun CameraControlBar(
    cameraManager: CameraController,
    isRecording: Boolean,
    flipCamera: Boolean,
    onSwitchCamera: () -> Unit,
    onFlipCamera: () -> Unit,
    onCapture: () -> Unit,
    onRecord: () -> Unit,
    onGallery: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E2E).copy(alpha = 0.98f)
        ),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Switch camera button
            val switchInteractionSource = remember { MutableInteractionSource() }
            val isSwitchPressed by switchInteractionSource.collectIsPressedAsState()
            val switchScale by animateFloatAsState(
                targetValue = if (isSwitchPressed) 0.92f else 1f,
                animationSpec = tween(100), label = ""
            )
            
            FilledTonalButton(
                onClick = onSwitchCamera,
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer { scaleX = switchScale; scaleY = switchScale },
                interactionSource = switchInteractionSource,
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF3B82F6),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MaterialIcons.CameraSwitch,
                        contentDescription = "Switch",
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .aspectRatio(1f),
                        tint = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Flip camera button
            val flipInteractionSource = remember { MutableInteractionSource() }
            val isFlipPressed by flipInteractionSource.collectIsPressedAsState()
            val flipScale by animateFloatAsState(
                targetValue = if (isFlipPressed) 0.92f else 1f,
                animationSpec = tween(100), label = ""
            )
            
            FilledTonalButton(
                onClick = onFlipCamera,
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer { scaleX = flipScale; scaleY = flipScale },
                interactionSource = flipInteractionSource,
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (flipCamera) Color(0xFF6366F1) else Color(0xFF6B7280),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MaterialIcons.FlipHorizontal,
                        contentDescription = if (flipCamera) "On" else "Flip",
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .aspectRatio(1f),
                        tint = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))

            // Capture button - larger and prominent
            val captureInteractionSource = remember { MutableInteractionSource() }
            val isCapturePressed by captureInteractionSource.collectIsPressedAsState()
            val captureScale by animateFloatAsState(
                targetValue = if (isCapturePressed) 0.88f else 1f,
                animationSpec = tween(100), label = ""
            )
            
            Button(
                onClick = onCapture,
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer { scaleX = captureScale; scaleY = captureScale }
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                interactionSource = captureInteractionSource,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3)
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MaterialIcons.Camera,
                        contentDescription = "Capture",
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .aspectRatio(1f),
                        tint = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))

            // Record button
            val recordInteractionSource = remember { MutableInteractionSource() }
            val isRecordPressed by recordInteractionSource.collectIsPressedAsState()
            val recordScale by animateFloatAsState(
                targetValue = if (isRecordPressed) 0.92f else 1f,
                animationSpec = tween(100), label = ""
            )
            
            FilledTonalButton(
                onClick = onRecord,
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer { scaleX = recordScale; scaleY = recordScale },
                interactionSource = recordInteractionSource,
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isRecording) Color(0xFFEF4444) else Color(0xFF6B7280),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRecording) MaterialIcons.Square else MaterialIcons.Video,
                        contentDescription = if (isRecording) "Stop" else "Record",
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .aspectRatio(1f),
                        tint = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))

            // Gallery button
            val galleryInteractionSource = remember { MutableInteractionSource() }
            val isGalleryPressed by galleryInteractionSource.collectIsPressedAsState()
            val galleryScale by animateFloatAsState(
                targetValue = if (isGalleryPressed) 0.92f else 1f,
                animationSpec = tween(100), label = ""
            )
            
            FilledTonalButton(
                onClick = onGallery,
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer { scaleX = galleryScale; scaleY = galleryScale },
                interactionSource = galleryInteractionSource,
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF10B981),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MaterialIcons.Images,
                        contentDescription = "Gallery",
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .aspectRatio(1f),
                        tint = Color.White
                    )
                }
            }
        }
    }
}
// Camera selector dropdown
@Composable
private fun CameraSelectorDropdown(
    cameraManager: CameraController,
    onDismiss: () -> Unit,
    onCameraSelected: (Int) -> Unit
) {
    // Get cameras dynamically instead of using remember, so it updates when camera provider is ready
    var cameras by remember { mutableStateOf<List<CameraInfo>>(emptyList()) }
    
    LaunchedEffect(cameraManager) {
        // Wait a bit for camera provider to initialize
        kotlinx.coroutines.delay(500)
        cameras = cameraManager.getAvailableCameras()
    }
    var expanded by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clickable(enabled = false) { }
                .shadow(16.dp, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E2E)
            ),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Select Camera",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFE0E0E0),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
                cameras.forEachIndexed { index, camera ->
                    FilledTonalButton(
                        onClick = {
                            onCameraSelected(index)
                            expanded = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .height(52.dp),
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF3B82F6),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "${if (camera.isUvc) "🔌 " else ""}${camera.name}",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF9CA3AF)
                    ),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF6B7280))
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// Gallery view
@Composable
private fun GalleryView(
    fileManager: FileManager,
    onDismiss: () -> Unit
) {
    val mediaDir = remember { fileManager.getMediaDirectory() }
    var files by remember { mutableStateOf(fileManager.listFiles(mediaDir).sortedByDescending { it.lastModified }) }
    var selectedFile by remember { mutableStateOf<FileInfo?>(null) }
    var showPreview by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var debugMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Refresh files when dialog closes
    LaunchedEffect(showRenameDialog) {
        if (!showRenameDialog) {
            files = fileManager.listFiles(mediaDir).sortedByDescending { it.lastModified }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
    ) {
        // Debug text overlay
        debugMessage?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .padding(horizontal = 16.dp),
                color = Color(0xFF000000).copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = message,
                    color = Color.Yellow,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                    maxLines = 3
                )
            }
        }
        if (showPreview && selectedFile != null) {
            // Preview screen
            MediaPreviewScreen(
                file = selectedFile!!,
                fileManager = fileManager,
                onBack = { 
                    showPreview = false
                    selectedFile = null
                },
                onShare = {
                    scope.launch {
                        try {
                            val mimeType = when {
                                selectedFile!!.isVideo -> "video/mp4"
                                selectedFile!!.name.endsWith(".png", ignoreCase = true) -> "image/png"
                                selectedFile!!.name.endsWith(".jpg", ignoreCase = true) ||
                                selectedFile!!.name.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                                else -> "image/jpeg"
                            }
                            shareMedia(
                                filePath = selectedFile!!.path,
                                mimeType = mimeType,
                                title = "Select WhatsApp or any app to share the file."
                            )
                        } catch (e: Exception) {
                            println("ERROR: Exception in shareMedia: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                },
                onRename = {
                    newFileName = selectedFile!!.name.substringBeforeLast(".")
                    showRenameDialog = true
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Header with back button, share, and rename
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1E1E2E).copy(alpha = 0.98f)
                    ),
                    shape = RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button - very visible (top-left corner)
                        FilledTonalButton(
                        onClick = {
                            println("Gallery back button clicked - closing gallery")
                            onDismiss()
                        },
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF2196F3),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.shadow(4.dp, RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = MaterialIcons.ArrowLeft,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text("Back", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                    Text(
                                text = "Gallery",
                        style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFFE0E0E0),
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = Color(0xFF2196F3).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "${files.size} items",
                                    color = Color(0xFF2196F3),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    
                    // Placeholder for alignment (share/rename will be on selected items)
                        Spacer(modifier = Modifier.width(80.dp))
                    }
                }

                // Grid view of files
                if (files.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                color = Color(0xFF2A2A3E).copy(alpha = 0.5f),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.size(80.dp)
                            ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                                    Text(
                                        text = "No images",
                                        fontSize = MaterialTheme.typography.displaySmall.fontSize
                                    )
                                }
                            }
                        Text(
                            text = "No saved images or videos",
                                color = Color(0xFF9CA3AF),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Capture photos or videos to see them here",
                                color = Color(0xFF6B7280),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(files.size) { index ->
                            val file = files[index]
                            GalleryThumbnailItem(
                                file = file,
                                fileManager = fileManager,
                                onClick = {
                                    selectedFile = file
                                    showPreview = true
                                },
                                onRename = {
                                    selectedFile = file
                                    newFileName = file.name.substringBeforeLast(".")
                                    showRenameDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // Rename dialog
        if (showRenameDialog && selectedFile != null) {
            RenameFileDialog(
                currentName = selectedFile!!.name,
                onConfirm = { newName ->
                    scope.launch {
                        renameFile(selectedFile!!.path, newName, fileManager)
                        showRenameDialog = false
                        selectedFile = null
                        files = fileManager.listFiles(mediaDir).sortedByDescending { it.lastModified }
                    }
                },
                onDismiss = {
                    showRenameDialog = false
                    selectedFile = null
                }
            )
        }
    }
}

// Helper function to capture image with detection boxes
private suspend fun captureImageWithDetections(
    cameraManager: CameraController,
    detections: List<ToothDetection>,
    fileManager: FileManager,
    flipHorizontal: Boolean = false
) {
    val frame = cameraManager.captureImage() ?: return
    var bitmap = frame.imageBitmap
    
    // Flip the image horizontally if requested
    if (flipHorizontal) {
        bitmap = flipImageBitmapHorizontally(bitmap)
    }
    
    // Convert to ImageData first, then draw detections directly on ImageData
    // This avoids bitmap conversion issues that cause green tint
    var imageData = convertImageBitmapToImageData(bitmap) ?: return
    
    // Draw detection boxes directly on ImageData if there are detections
    if (detections.isNotEmpty()) {
        val annotatedImageData = drawDetectionsOnImageData(imageData, detections, flipHorizontal, bitmap.width, bitmap.height)
        if (annotatedImageData != null) {
            imageData = annotatedImageData
        } else {
            println("Warning: Failed to draw detections, saving image without boxes")
        }
    }
    
    // Generate filename with timestamp
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val filename = "Orai_Capture_$timestamp.png"
    
    // Save image
    val saved = fileManager.saveImage(imageData, filename)
    if (saved) {
        println("Image saved with ${detections.size} detection boxes: ${fileManager.getMediaDirectory()}/$filename")
    } else {
        println("Failed to save image")
    }
}

// Draw detection boxes directly on ImageData (avoids bitmap conversion issues)
private fun drawDetectionsOnImageData(
    imageData: ImageData,
    detections: List<ToothDetection>,
    flipHorizontal: Boolean,
    width: Int,
    height: Int
): ImageData? {
    // Use platform-specific function to draw detections on ImageData
    return drawDetectionsOnImageDataPlatform(imageData, detections, flipHorizontal, width, height)
}

// Platform-specific function to draw detection boxes on ImageData
expect fun drawDetectionsOnImageDataPlatform(
    imageData: ImageData,
    detections: List<ToothDetection>,
    flipHorizontal: Boolean,
    width: Int,
    height: Int
): ImageData?

// Convert ImageData to ImageBitmap
private fun convertImageDataToImageBitmap(imageData: ImageData): ImageBitmap? {
    return try {
        // This is a simplified conversion - platform-specific implementations would be better
        // For now, we'll use the existing conversion path
        null // Will be implemented per platform if needed
    } catch (e: Exception) {
        null
    }
}

// Convert ImageBitmap to ImageData
private fun convertImageBitmapToImageData(bitmap: ImageBitmap): ImageData? {
    return try {
        val width = bitmap.width
        val height = bitmap.height
        val pixelMap = bitmap.toPixelMap()
        val bytes = ByteArray(width * height * 4)

        var idx = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = pixelMap[x, y]
                // Write as ARGB format to match FileManager expectation
                bytes[idx++] = (c.alpha * 255f).toInt().coerceIn(0, 255).toByte()
                bytes[idx++] = (c.red * 255f).toInt().coerceIn(0, 255).toByte()
                bytes[idx++] = (c.green * 255f).toInt().coerceIn(0, 255).toByte()
                bytes[idx++] = (c.blue * 255f).toInt().coerceIn(0, 255).toByte()
            }
        }

        ImageData(width, height, 0, bytes)
    } catch (e: Exception) {
        println("Error converting bitmap to ImageData: ${e.message}")
        null
    }
}

// Format file size
private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> String.format("%.2f MB", mb)
        kb >= 1 -> String.format("%.2f KB", kb)
        else -> "$bytes B"
    }
}

// Format date
private fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return format.format(date)
}

// File status check result
data class FileStatusCheck(
    val exists: Boolean,
    val canRead: Boolean,
    val debugMessages: List<String>
)

// Check file status (platform-specific implementation needed)
// Note: expect declaration is at the bottom with other platform-specific functions

// Gallery thumbnail item
@Composable
private fun GalleryThumbnailItem(
    file: FileInfo,
    fileManager: FileManager,
    onClick: () -> Unit,
    onRename: () -> Unit
) {
    var thumbnail by remember { mutableStateOf<ImageBitmap?>(null) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(100), label = ""
    )
    
    LaunchedEffect(file.path) {
        thumbnail = loadThumbnail(file.path, file.isVideo)
    }
    
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(4.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A3E)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Clickable content area (not the share button)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        onClick = onClick,
                        interactionSource = interactionSource,
                        indication = null
                    )
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail!!,
                contentDescription = file.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                            text = if (file.isVideo) "Video" else "Image",
                    style = MaterialTheme.typography.displaySmall
                )
            }
        }
            }
        // Video indicator
        if (file.isVideo) {
                Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                        .padding(8.dp),
                    color = Color(0xFF1E1E2E).copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "Video",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// Media preview screen
@Composable
private fun MediaPreviewScreen(
    file: FileInfo,
    fileManager: FileManager,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit
) {
    var previewImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var loadingError by remember { mutableStateOf<String?>(null) }
    var debugInfo by remember { mutableStateOf<List<String>>(emptyList()) }
    var debugMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(file.path) {
        if (!file.isVideo) {
            loadingError = null
            previewImage = null
            debugInfo = emptyList()
            
            val debugMessages = mutableListOf<String>()
            debugMessages.add("📂 Loading preview for image: ${file.path}")
            debugMessages.add("📁 File name: ${file.name}")
            debugMessages.add("📊 File size: ${formatFileSize(file.size)}")
            debugMessages.add("📅 Last modified: ${formatDate(file.lastModified)}")
            
            println("Loading preview for image: ${file.path}")
            
            // Check file status before loading
            val fileCheck = checkFileStatus(file.path)
            debugMessages.addAll(fileCheck.debugMessages)
            debugInfo = debugMessages.toList()
            
            if (!fileCheck.exists) {
                loadingError = "File does not exist"
                debugMessages.add("❌ ERROR: File does not exist at path")
            } else if (!fileCheck.canRead) {
                loadingError = "File cannot be read (permission denied)"
                debugMessages.add("❌ ERROR: File exists but cannot be read")
            } else {
                debugMessages.add("🔄 Attempting to load image with BitmapFactory...")
                val loaded = loadFullImage(file.path)
                if (loaded != null) {
                    previewImage = loaded
                    debugMessages.add("✅ Image loaded successfully: ${loaded.width}x${loaded.height}")
                    println("Preview image loaded successfully")
                } else {
                    loadingError = "BitmapFactory failed to decode image"
                    debugMessages.add("❌ ERROR: BitmapFactory.decodeFile() returned null")
                    debugMessages.add("💡 Common causes:")
                    debugMessages.add("   • File is corrupted or incomplete")
                    debugMessages.add("   • File format not supported by BitmapFactory")
                    debugMessages.add("   • Insufficient memory (try smaller image)")
                    debugMessages.add("   • File is locked or being written to")
                    debugMessages.add("   • File header verification failed")
                    debugMessages.add("🔄 Tried input stream fallback method...")
                }
            }
            
            debugInfo = debugMessages.toList()
            println("ERROR: Failed to load preview image from: ${file.path}")
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Debug text overlay
        debugMessage?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .padding(horizontal = 16.dp)
                    .zIndex(10f),
                color = Color(0xFF000000).copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = message,
                    color = Color.Yellow,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                    maxLines = 3
                )
            }
        }
        
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top bar with back, share, rename buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        println("Preview back button clicked")
                        onBack()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3) // Blue color
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = MaterialIcons.ArrowLeft,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Back", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
                
                Row {
                    IconButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val mimeType = when {
                                        file.isVideo -> "video/mp4"
                                        file.name.endsWith(".png", ignoreCase = true) -> "image/png"
                                        file.name.endsWith(".jpg", ignoreCase = true) ||
                                        file.name.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                                        else -> "image/jpeg"
                                    }
                                    shareMedia(
                                        filePath = file.path,
                                        mimeType = mimeType,
                                        title = "Select WhatsApp or any app to share the file."
                                    )
                                } catch (e: Exception) {
                                    println("ERROR: Exception in shareMedia: ${e.message}")
                                    e.printStackTrace()
                                }
                            }
                        },
                        modifier = Modifier.zIndex(1f)
                    ) {
                        Icon(
                            imageVector = MaterialIcons.Share2,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onRename) {
                        Icon(
                            imageVector = MaterialIcons.Edit,
                            contentDescription = "Rename",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            // Preview content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(bottom = 80.dp), // Add bottom padding to prevent video controls overlap with navigation bar
                contentAlignment = Alignment.Center
            ) {
                if (file.isVideo) {
                    BuiltInVideoPlayer(
                        videoPath = file.path,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (previewImage != null) {
                    Image(
                        bitmap = previewImage!!,
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else if (loadingError != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Text(
                            text = "⚠️ Unable to load image",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = loadingError!!,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Debug console text
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.Black.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = "🔍 Debug Console:",
                                    color = Color.Yellow,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                debugInfo.forEach { message ->
                                    Text(
                                        text = message,
                                        color = when {
                                            message.contains("ERROR", ignoreCase = true) -> Color.Red
                                            message.contains("✅", ignoreCase = true) -> Color.Green
                                            message.contains("❌", ignoreCase = true) -> Color.Red
                                            message.startsWith("📂") || message.startsWith("📁") || message.startsWith("📊") || message.startsWith("📅") -> Color.Cyan
                                            else -> Color.White
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "File: ${file.name}",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Path: ${file.path}",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Loading image...", color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = file.name,
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            // File info at bottom
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.8f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = file.name,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${if (file.isVideo) "Video" else "Image"} • ${formatFileSize(file.size)} • ${formatDate(file.lastModified)}",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// Rename file dialog
@Composable
private fun RenameFileDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf(currentName.substringBeforeLast(".")) }
    val extension = currentName.substringAfterLast(".", "")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename File") },
        text = {
            TextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("File name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalName = if (extension.isNotEmpty()) "$newName.$extension" else newName
                    onConfirm(finalName)
                }
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Toast notification component
@Composable
private fun ToastNotification(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(16.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E2E).copy(alpha = 0.98f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
) {
    Surface(
                color = Color(0xFF10B981).copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "✓",
                    color = Color(0xFF10B981),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        Text(
            text = message,
                color = Color(0xFFE0E0E0),
            style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Camera button settings dialog
@Composable
private fun CameraButtonSettingsDialog(
    currentSettings: IntraoralCameraSettings,
    onDismiss: () -> Unit,
    onSave: (IntraoralCameraSettings) -> Unit
) {
    var interfaceType by remember { mutableStateOf(currentSettings.interfaceType) }
    var detectingForCapture by remember { mutableStateOf(false) }
    var detectingForLight by remember { mutableStateOf(false) }
    var captureKeyCode by remember { mutableStateOf(currentSettings.capture?.event?.keyCode) }
    var lightKeyCode by remember { mutableStateOf(currentSettings.light?.event?.keyCode) }
    var deviceVid by remember { mutableStateOf(currentSettings.deviceVid?.toString(16)?.uppercase() ?: "EB1A") }
    var devicePid by remember { mutableStateOf(currentSettings.devicePid?.toString(16)?.uppercase() ?: "5000") }
    
    // New settings state
    var enableLongPressRecording by remember { mutableStateOf(currentSettings.enableLongPressRecording) }
    var buttonDebounceMs by remember { mutableStateOf(currentSettings.buttonDebounceMs.toFloat()) }
    var longPressThresholdMs by remember { mutableStateOf(currentSettings.longPressThresholdMs.toFloat()) }
    var advancedMode by remember { mutableStateOf(currentSettings.advancedMode) }
    var capturePattern by remember { mutableStateOf(currentSettings.capturePattern) }
    var lightPattern by remember { mutableStateOf(currentSettings.lightPattern) }
    var testMode by remember { mutableStateOf(currentSettings.testMode) }
    var testOutput by remember { mutableStateOf("") }
    
    // Key detection effect
    if (detectingForCapture || detectingForLight) {
        CameraButtonDetectionEffectPlatform { keyCode ->
            println("Settings dialog: Key detected - keyCode=$keyCode, detectingForCapture=$detectingForCapture, detectingForLight=$detectingForLight")
            if (detectingForCapture) {
                captureKeyCode = keyCode
                detectingForCapture = false
                println("Settings dialog: Capture key set to $keyCode")
            } else if (detectingForLight) {
                lightKeyCode = keyCode
                detectingForLight = false
                println("Settings dialog: Light key set to $keyCode")
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = MaterialIcons.Settings,
                    contentDescription = "Settings",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(20.dp)
                )
                Text("Camera Button Settings")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Interface Type Selection
                Text(
                    text = "Interface Type:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                HardwareInterfaceType.values().forEach { type ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = interfaceType == type,
                            onClick = { interfaceType = type }
                        )
                        Text(
                            text = when (type) {
                                HardwareInterfaceType.KEY_EVENT -> "Keyboard/Media Keys"
                                HardwareInterfaceType.RAW_HID -> "Raw HID (Advanced)"
                                HardwareInterfaceType.UVC_EXTENSION -> "UVC Extension (Android)"
                                HardwareInterfaceType.NONE -> "None (Disabled)"
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                
                if (interfaceType == HardwareInterfaceType.RAW_HID) {
                    HorizontalDivider()
                    Text(
                        text = "Raw HID Configuration:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Device will be detected automatically when you save settings. Make sure your USB device is connected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "USB Vendor ID (VID):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextField(
                        value = deviceVid,
                        onValueChange = { deviceVid = it.uppercase().filter { c -> c.isLetterOrDigit() } },
                        label = { Text("VID (hex, e.g., EB1A)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "USB Product ID (PID):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextField(
                        value = devicePid,
                        onValueChange = { devicePid = it.uppercase().filter { c -> c.isLetterOrDigit() } },
                        label = { Text("PID (hex, e.g., 5000)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Note: HID reports will be automatically detected when buttons are pressed. The first unique report for each button will be saved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Blue,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                    if (interfaceType == HardwareInterfaceType.KEY_EVENT) {
                    HorizontalDivider()
                    
                    if (detectingForCapture || detectingForLight) {
                        Text(
                            text = "⚠️ Click on this dialog, then press a key on your USB device or keyboard",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Blue,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    // Capture Key Mapping
                    Text(
                        text = "Capture Button:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (detectingForCapture) "Press a key..." else (captureKeyCode?.let { getKeyCodeNamePlatform(it) } ?: "Not set"),
                            modifier = Modifier.weight(1f),
                            color = if (detectingForCapture) Color.Blue else Color.Unspecified
                        )
                        Button(
                            onClick = {
                                detectingForCapture = true
                                detectingForLight = false
                            },
                            enabled = !detectingForCapture && !detectingForLight
                        ) {
                            Text(if (detectingForCapture) "Listening..." else "Set Key")
                        }
                        if (captureKeyCode != null) {
                            TextButton(
                                onClick = { captureKeyCode = null }
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                    
                    // Light Key Mapping
                    Text(
                        text = "Light Toggle Button:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (detectingForLight) "Press a key..." else (lightKeyCode?.let { getKeyCodeNamePlatform(it) } ?: "Not set"),
                            modifier = Modifier.weight(1f),
                            color = if (detectingForLight) Color.Blue else Color.Unspecified
                        )
                        Button(
                            onClick = {
                                detectingForLight = true
                                detectingForCapture = false
                            },
                            enabled = !detectingForCapture && !detectingForLight
                        ) {
                            Text(if (detectingForLight) "Listening..." else "Set Key")
                        }
                        if (lightKeyCode != null) {
                            TextButton(
                                onClick = { lightKeyCode = null }
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                }
                
                // Long Press Recording Section
                if (interfaceType == HardwareInterfaceType.KEY_EVENT) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Long Press Recording",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (enableLongPressRecording) {
                                    "📸 Short press = Take photo\n🎥 Long press (hold) = Record video\n⏹️ Release = Stop recording"
                                } else {
                                    "📸 Press = Take photo only\n\nEnable long press to record video by holding the capture button"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Switch(
                            checked = enableLongPressRecording,
                            onCheckedChange = { enableLongPressRecording = it }
                        )
                    }
                }
                
                // Button Sensitivity Section
                if (interfaceType == HardwareInterfaceType.KEY_EVENT) {
                    HorizontalDivider()
                    Text(
                        text = "Button Sensitivity:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Debounce slider
                    Text(
                        text = "Debounce: ${buttonDebounceMs.toInt()}ms",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = buttonDebounceMs,
                        onValueChange = { buttonDebounceMs = it },
                        valueRange = 0f..2000f,
                        steps = 199
                    )
                    
                    // Long press threshold slider
                    Text(
                        text = "Long Press Threshold: ${longPressThresholdMs.toInt()}ms",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Slider(
                        value = longPressThresholdMs,
                        onValueChange = { longPressThresholdMs = it },
                        valueRange = 0f..3000f,
                        steps = 299
                    )
                }
                
                // Advanced Mode Section
                if (interfaceType == HardwareInterfaceType.KEY_EVENT) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Advanced Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "⚠️ Use with caution! Pattern matching for custom button detection.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFF9800),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Switch(
                            checked = advancedMode,
                            onCheckedChange = { advancedMode = it }
                        )
                    }
                    
                    AnimatedVisibility(visible = advancedMode) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Capture Pattern (hex bytes, space-separated):",
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextField(
                                value = capturePattern,
                                onValueChange = { capturePattern = it },
                                label = { Text("e.g., 01 00 or 00 XX") },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Leave empty for auto detection") }
                            )
                            
                            Text(
                                text = "Light Pattern (hex bytes, space-separated):",
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextField(
                                value = lightPattern,
                                onValueChange = { lightPattern = it },
                                label = { Text("e.g., 01 00 or 00 XX") },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Leave empty for auto detection") }
                            )
                            
                            Button(
                                onClick = {
                                    capturePattern = ""
                                    lightPattern = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Reset Patterns")
                            }
                            
                            Text(
                                text = """
                                    Pattern Format: Hex bytes separated by spaces
                                    Examples:
                                    • "01" - First byte equals 0x01
                                    • "01 00" - First byte 0x01, second 0x00
                                    • "00 XX" - First byte 0x00, second any value
                                    
                                    Leave empty to use automatic detection.
                                """.trimIndent(),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
                
                // Test Mode Section
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Test Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "🔬 Button data will be logged when enabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Switch(
                        checked = testMode,
                        onCheckedChange = { testMode = it }
                    )
                }
                
                AnimatedVisibility(visible = testMode) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Test Output:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp),
                            color = Color(0xFF1E1E2E),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = testOutput.ifEmpty { "Test mode active. Press camera buttons to see data...\n" },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState()),
                                color = Color(0xFFE0E0E0)
                            )
                        }
                        Button(
                            onClick = { testOutput = "Test log cleared.\n" },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear Test Log")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val vid = try {
                        deviceVid.toInt(16)
                    } catch (e: Exception) {
                        0xEB1A // Default
                    }
                    val pid = try {
                        devicePid.toInt(16)
                    } catch (e: Exception) {
                        0x5000 // Default
                    }
                    
                    val newSettings = IntraoralCameraSettings(
                        interfaceType = interfaceType,
                        capture = captureKeyCode?.let {
                            ButtonMapping(
                                action = CameraAction.CAPTURE,
                                event = CameraButtonEvent(keyCode = it)
                            )
                        },
                        light = lightKeyCode?.let {
                            ButtonMapping(
                                action = CameraAction.LIGHT_TOGGLE,
                                event = CameraButtonEvent(keyCode = it)
                            )
                        },
                        deviceVid = if (interfaceType == HardwareInterfaceType.RAW_HID) vid else currentSettings.deviceVid,
                        devicePid = if (interfaceType == HardwareInterfaceType.RAW_HID) pid else currentSettings.devicePid,
                        enableLongPressRecording = enableLongPressRecording,
                        buttonDebounceMs = buttonDebounceMs.toInt(),
                        longPressThresholdMs = longPressThresholdMs.toInt(),
                        advancedMode = advancedMode,
                        capturePattern = capturePattern,
                        lightPattern = lightPattern,
                        testMode = testMode
                    )
                    onSave(newSettings)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun VideoSaveErrorDialog(
    error: VideoSaveResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (error.success) "⚠️ Video Saved with Warnings" else "❌ Video Save Failed",
                color = if (error.success) Color(0xFFFF9800) else Color(0xFFD32F2F)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (error.errorMessage != null) {
                    Text(
                        text = error.errorMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (error.success) Color(0xFFFF9800) else Color(0xFFD32F2F)
                    )
                }
                
                if (error.filePath != null) {
                    Text(
                        text = "File path: ${error.filePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                
                if (error.mediaStoreUri != null) {
                    Text(
                        text = "MediaStore URI: ${error.mediaStoreUri}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                
                if (error.errorDetails.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Details:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    error.errorDetails.forEach { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

// Built-in video player component
@Composable
private fun BuiltInVideoPlayer(
    videoPath: String,
    modifier: Modifier = Modifier
) {
    // Use platform-specific video player for smooth playback
    VideoPlayerView(
        videoPath = videoPath,
        modifier = modifier
    )
}

// Platform-specific functions (expect/actual)
expect suspend fun loadThumbnail(path: String, isVideo: Boolean): ImageBitmap?
expect suspend fun loadFullImage(path: String): ImageBitmap?
expect fun checkFileStatus(path: String): FileStatusCheck
expect suspend fun shareFile(path: String)
expect suspend fun shareToWhatsApp(path: String)

/**
 * Share media file (image/video) with patient via WhatsApp or any compatible app.
 * 
 * @param filePath Absolute path to the media file
 * @param mimeType MIME type (e.g., "image/jpeg", "video/mp4")
 * @param title Title for the share dialog (default: "Share with patient")
 * @throws Exception if sharing fails (file not found, no apps available, etc.)
 */
expect suspend fun shareMedia(
    filePath: String,
    mimeType: String,
    title: String = "Share with patient"
)
expect suspend fun shareToEmail(path: String, filename: String)
expect suspend fun shareToTelegram(path: String)
expect suspend fun renameFile(oldPath: String, newName: String, fileManager: FileManager)
expect suspend fun startVideoRecording(cameraManager: CameraController, fileManager: FileManager, filename: String, flipHorizontal: Boolean): String?
expect suspend fun stopVideoRecording(cameraManager: CameraController): String?
expect fun playVideo(path: String)
expect suspend fun loadVideoFrames(videoPath: String, onFrame: (ImageBitmap) -> Unit)
expect fun flipImageBitmapHorizontally(bitmap: ImageBitmap): ImageBitmap

// Platform-specific video player view
@Composable
expect fun VideoPlayerView(videoPath: String, modifier: Modifier)

// Platform-specific camera button handler effect
@Composable
expect fun CameraButtonHandlerEffect(
    handler: CameraButtonHandler?,
    dispatcher: CameraButtonDispatcher
)

// Platform-specific key detection for settings
@Composable
expect fun CameraButtonDetectionEffectPlatform(
    onKeyDetected: (Int) -> Unit
)

// Platform-specific key code name helper
expect fun getKeyCodeNamePlatform(keyCode: Int): String

// Platform-specific functions for model loading
expect fun getYoloModelPath(): String
expect fun setModelInferenceContext(modelInference: ModelInference)
expect fun setFaceDetectorContext(faceDetector: FaceDetector)