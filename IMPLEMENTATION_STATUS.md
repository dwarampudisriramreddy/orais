# Orais - Multiplatform USB Camera Object Detection

## ✅ Implementation Status

### Build Status
- ✅ **Android** - Compiles successfully
- ✅ **Desktop (JVM)** - Compiles successfully
- ✅ **Web (JS)** - Compiles successfully

---

## Platform-Specific Implementations

### 🤖 Android

#### Object Detection (TensorFlow Lite)
**Status:** ✅ Implemented
**Location:** `composeApp/src/androidMain/kotlin/com/ram/orai/orais/ModelInference.android.kt`

**Features:**
- TFLite Interpreter with configurable threads
- GPU delegate support (commented out, can be enabled)
- Standard object detection model format (SSD MobileNet compatible)
- Outputs: bounding boxes, class IDs, confidence scores

**Usage:**
```kotlin
val inference = ModelInference()
inference.loadModel("/path/to/model.tflite")
val results = inference.runInference(imageData, confThreshold = 0.5f)
inference.close()
```

**Dependencies:**
```kotlin
implementation("org.tensorflow:tensorflow-lite:2.17.0")
implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
implementation("org.tensorflow:tensorflow-lite-gpu:2.17.0")
```

#### Camera Integration (CameraX)
**Status:** ✅ Implemented
**Location:** `composeApp/src/androidMain/kotlin/com/ram/orai/orais/CameraManagerImpl.kt`

**Features:**
- CameraX integration with lifecycle awareness
- Image analysis pipeline
- Front/back camera switching
- Real-time frame streaming via Flow
- Automatic rotation handling

**Usage:**
```kotlin
val cameraManager = getCameraManager(context, lifecycleOwner)
cameraManager.startPreview()
cameraManager.cameraFrames.collect { frame ->
    // Process frame.imageBitmap
}
```

**Dependencies:**
```kotlin
implementation("androidx.camera:camera-core:1.3.4")
implementation("androidx.camera:camera-camera2:1.3.4")
implementation("androidx.camera:camera-lifecycle:1.3.4")
```

#### USB Camera Support
**Status:** ⚠️ Stub (requires UVC library)
**Next Steps:** Add `saki4510t/UVCCamera` dependency

---

### 🖥️ Desktop (JVM)

#### Object Detection
**Status:** ⚠️ Not Implemented
**Reason:** TensorFlow Lite Java API is Android-only

**Recommended Alternatives:**
1. **ONNX Runtime** (Preferred)
   ```kotlin
   implementation("com.microsoft.onnxruntime:onnxruntime:1.17.0")
   ```
2. **TensorFlow Java**
   ```kotlin
   implementation("org.tensorflow:tensorflow-core-platform:0.5.0")
   ```
3. **Deep Java Library (DJL)**
   ```kotlin
   implementation("ai.djl:api:0.27.0")
   ```

#### Camera Integration (webcam-capture)
**Status:** ✅ Fully Implemented
**Location:** `composeApp/src/jvmMain/kotlin/com/ram/orai/orais/CameraManagerImpl.kt`

**Features:**
- Multiple webcam support
- Camera switching
- ~30 FPS frame streaming
- BufferedImage to Compose ImageBitmap conversion

**Usage:**
```kotlin
val cameraManager = getCameraManager(null, null)
cameraManager.startPreview()
cameraManager.cameraFrames.collect { frame ->
    // Process frame.imageBitmap
}
```

**Dependencies:**
```kotlin
implementation("com.github.sarxos:webcam-capture:0.3.12")
```

---

### 🌐 Web (JavaScript/WASM)

#### Object Detection (MediaPipe)
**Status:** ✅ Implemented
**Location:** `composeApp/src/webMain/kotlin/com/ram/orai/orais/ModelInference.web.kt`

**Features:**
- MediaPipe Tasks Vision integration
- TFLite models running via WASM
- Browser-based object detection

**Usage:**
```kotlin
val inference = ModelInference()
inference.loadModel("/models/model.tflite")
val results = inference.runInference(imageData, confThreshold = 0.5f)
```

**Dependencies:**
```kotlin
implementation(npm("@mediapipe/tasks-vision", "0.10.20"))
```

#### Camera Integration
**Status:** ⚠️ Stub (requires getUserMedia implementation)
**Next Steps:** Implement WebRTC getUserMedia API with JS interop

---

## Common Interface (expect/actual)

### Core Interfaces
Located in: `composeApp/src/commonMain/kotlin/com/ram/orai/orais/`

#### CameraController
```kotlin
interface CameraController {
    fun startPreview()
    fun stopPreview()
    fun switchCamera()
    fun captureImage(): CameraFrame?
    val cameraFrames: SharedFlow<CameraFrame>
}
```

#### ModelInference
```kotlin
expect class ModelInference() {
    fun loadModel(modelPath: String): Boolean
    fun runInference(imageData: ImageData, confThreshold: Float): List<DetectionResult>
    fun close()
}
```

#### Data Models
```kotlin
data class ImageData(val width: Int, val height: Int, val rotationDegrees: Int, val bytes: ByteArray)
data class DetectionResult(val boundingBox: BoundingBox, val classId: Int, val confidence: Float)
data class BoundingBox(val left: Float, val top: Float, val right: Float, val bottom: Float)
```

---

## Next Steps

### Priority 1: Complete Camera Implementations
1. **Android:** Add UVC camera library for USB camera support
2. **Web:** Implement getUserMedia with canvas-based frame capture

### Priority 2: Desktop Object Detection
Choose and implement one of:
- ONNX Runtime (recommended for simplicity)
- TensorFlow Java (full TF ecosystem)
- DJL (flexible framework support)

### Priority 3: Add Model Files
Place `.tflite` model files in:
- Android: `composeApp/src/androidMain/assets/`
- Desktop: Application resources
- Web: `composeApp/src/webMain/resources/`

### Priority 4: Testing
Create test applications for each platform demonstrating:
- Camera preview
- Real-time object detection
- Bounding box visualization

---

## File Structure

```
composeApp/src/
├── commonMain/kotlin/com/ram/orai/orais/
│   ├── CameraInterface.kt          # expect declarations
│   ├── CameraManager.kt            # CameraController interface
│   ├── CameraPreview.kt            # Compose preview component
│   ├── App.kt                      # Main app composable
│   └── detection/BoundingBox.kt    # Data models
│
├── androidMain/kotlin/com/ram/orai/orais/
│   ├── CameraManagerImpl.kt        # ✅ CameraX implementation
│   ├── ModelInference.android.kt   # ✅ TFLite implementation
│   ├── Platform.android.kt         # Platform info
│   ├── FileManager.android.kt      # File operations
│   ├── UsbDeviceManager.android.kt # ⚠️ Stub
│   ├── AuthManager.android.kt      # Auth stub
│   └── PreferencesManager.android.kt
│
├── jvmMain/kotlin/com/ram/orai/orais/
│   ├── CameraManagerImpl.kt        # ✅ Webcam implementation
│   ├── ModelInference.jvm.kt       # ⚠️ Stub (needs ONNX/TF)
│   ├── Platform.jvm.kt
│   └── [other managers]
│
└── webMain/kotlin/com/ram/orai/orais/
    ├── CameraManagerImpl.kt        # ⚠️ Stub
    ├── ModelInference.web.kt       # ✅ MediaPipe implementation
    ├── Platform.web.kt
    └── [other managers]
```

---

## Dependencies Summary

### Android (`androidMain.dependencies`)
```kotlin
// Camera
implementation("androidx.camera:camera-core:1.3.4")
implementation("androidx.camera:camera-camera2:1.3.4")
implementation("androidx.camera:camera-lifecycle:1.3.4")

// TFLite
implementation("org.tensorflow:tensorflow-lite:2.17.0")
implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
implementation("org.tensorflow:tensorflow-lite-gpu:2.17.0")
```

### Desktop (`jvmMain.dependencies`)
```kotlin
// Camera
implementation("com.github.sarxos:webcam-capture:0.3.12")

// ML (choose one)
// implementation("com.microsoft.onnxruntime:onnxruntime:1.17.0")
```

### Web (`webMain.dependencies`)
```kotlin
// MediaPipe
implementation(npm("@mediapipe/tasks-vision", "0.10.20"))
```

---

## Known Limitations

1. **Desktop:** TFLite is Android-only; requires alternative ML framework
2. **Web:** Camera access via getUserMedia not yet implemented
3. **Android:** USB camera requires external UVC library
4. **All:** Model preprocessing (resize, normalize) needs refinement

---

## Build Commands

```bash
# Compile all platforms
./gradlew :composeApp:compileDebugKotlinAndroid \
          :composeApp:compileKotlinJvm \
          :composeApp:compileKotlinJs

# Run Android
./gradlew :composeApp:installDebug

# Run Desktop
./gradlew :composeApp:runJvm

# Run Web (development)
./gradlew :composeApp:jsBrowserDevelopmentRun
```
