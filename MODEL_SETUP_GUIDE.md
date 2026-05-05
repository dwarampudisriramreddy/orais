# Model Setup Guide for Orais

## 📍 Current Model Locations

Your models are correctly placed:

```
✅ composeApp/src/androidMain/assets/
   ├── fdi_float32.tflite
   └── yolov8_640_float32.tflite

✅ composeApp/src/jvmMain/resources/
   ├── fdi_float32.tflite
   └── yolov8_640_float32.tflite

✅ composeApp/src/webMain/resources/
   ├── fdi_float32.tflite
   └── yolov8_640_float32.tflite
```

---

## 🤖 Android - Using TFLite Models

### Update ModelInference.android.kt

Since you have models in `assets/`, update the loading code:

```kotlin
// composeApp/src/androidMain/kotlin/com/ram/orai/orais/ModelInference.android.kt

import android.content.Context
import org.tensorflow.lite.support.common.FileUtil

actual class ModelInference {
    private var interpreter: Interpreter? = null
    private var context: Context? = null  // Add context field

    // Add method to set context (call this before loadModel)
    fun setContext(ctx: Context) {
        this.context = ctx
    }

    actual fun loadModel(modelPath: String): Boolean {
        return try {
            val ctx = context ?: throw IllegalStateException("Context not set")

            // Load from assets: modelPath should be just filename like "yolov8_640_float32.tflite"
            val modelBuffer = FileUtil.loadMappedFile(ctx, modelPath)

            val options = Interpreter.Options().apply {
                setNumThreads(4)
                // Enable GPU if available:
                // addDelegate(GpuDelegate())
            }

            interpreter = Interpreter(modelBuffer, options)

            // Get input shape
            val inputShape = interpreter?.getInputTensor(0)?.shape()
            inputImageHeight = inputShape?.get(1) ?: 640
            inputImageWidth = inputShape?.get(2) ?: 640

            // Initialize output arrays
            outputLocations = Array(1) { Array(10) { FloatArray(4) } }
            outputClasses = Array(1) { FloatArray(10) }
            outputScores = Array(1) { FloatArray(10) }
            numDetections = FloatArray(1)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
```

### Usage in Android:
```kotlin
val inference = ModelInference()
inference.setContext(context)  // Pass Android Context
inference.loadModel("yolov8_640_float32.tflite")  // Just filename, loads from assets
```

---

## 🖥️ Desktop - TFLite Models (3 Options)

### Option 1: Convert to ONNX (Recommended)

On a machine with more disk space, run:

```bash
# Install dependencies
pip install tf2onnx tensorflow onnx

# Convert each model
python -m tf2onnx.convert \
  --tflite composeApp/src/jvmMain/resources/fdi_float32.tflite \
  --output composeApp/src/jvmMain/resources/fdi_float32.onnx \
  --opset 13

python -m tf2onnx.convert \
  --tflite composeApp/src/jvmMain/resources/yolov8_640_float32.tflite \
  --output composeApp/src/jvmMain/resources/yolov8_640_float32.onnx \
  --opset 13
```

Then implement ONNX Runtime in Desktop:

```kotlin
// composeApp/src/jvmMain/kotlin/com/ram/orai/orais/ModelInference.jvm.kt

import ai.onnxruntime.*
import com.ram.orai.detection.BoundingBox
import java.nio.FloatBuffer

actual class ModelInference {
    private var session: OrtSession? = null
    private var environment: OrtEnvironment? = null

    actual fun loadModel(modelPath: String): Boolean {
        return try {
            environment = OrtEnvironment.getEnvironment()

            // Load from resources or absolute path
            val modelBytes = if (modelPath.startsWith("/")) {
                // Absolute path
                java.io.File(modelPath).readBytes()
            } else {
                // Load from resources
                this::class.java.getResourceAsStream("/$modelPath")?.readBytes()
                    ?: throw IllegalArgumentException("Model not found: $modelPath")
            }

            session = environment?.createSession(modelBytes)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    actual fun runInference(imageData: ImageData, confThreshold: Float): List<DetectionResult> {
        val sess = session ?: return emptyList()

        return try {
            // Prepare input tensor (adjust based on your model)
            val inputShape = longArrayOf(1, 640, 640, 3)
            val inputData = FloatArray(1 * 640 * 640 * 3) // TODO: Preprocess imageData

            val inputTensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(inputData), inputShape)
            val inputs = mapOf(sess.inputNames.iterator().next() to inputTensor)

            // Run inference
            val results = sess.run(inputs)

            // Parse outputs (model-specific)
            val detections = mutableListOf<DetectionResult>()
            // TODO: Extract bounding boxes from results

            results.close()
            inputTensor.close()

            detections
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    actual fun close() {
        session?.close()
        environment?.close()
    }
}
```

### Option 2: Use TFLite via JNI (Advanced)

Requires building TFLite C++ library and creating JNI bindings. Not recommended.

### Option 3: Keep Stub for Now

Current implementation returns empty list - Web and Android will work, Desktop camera preview works but no detections.

---

## 🌐 Web - Using TFLite Models with MediaPipe

Your models are already in `webMain/resources/`. Update the implementation:

```kotlin
// composeApp/src/webMain/kotlin/com/ram/orai/orais/ModelInference.web.kt

actual fun loadModel(modelPath: String): Boolean {
    return try {
        // For models in resources, use relative path
        val options = js("""({
            baseOptions: {
                modelAssetPath: '/resources/' + modelPath
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
```

### Usage in Web:
```kotlin
val inference = ModelInference()
inference.loadModel("yolov8_640_float32.tflite")  // Loads from /resources/
```

**Note:** Make sure your web server serves the resources folder. In development:
```bash
./gradlew :composeApp:jsBrowserDevelopmentRun
```

---

## 📊 Model Information

### Model 1: `fdi_float32.tflite`
- **Type:** Face Detection (likely)
- **Input:** 640x640 RGB image (probably)
- **Output:** Bounding boxes + confidence scores

### Model 2: `yolov8_640_float32.tflite`
- **Type:** YOLOv8 Object Detection
- **Input:** 640x640 RGB image
- **Output:**
  - Bounding boxes (x, y, w, h)
  - Class IDs
  - Confidence scores
- **Classes:** 80 COCO classes (person, car, dog, etc.)

---

## 🔧 Quick Start Per Platform

### Android:
```kotlin
val inference = ModelInference()
inference.setContext(context)
if (inference.loadModel("yolov8_640_float32.tflite")) {
    // Start camera and run inference
    cameraManager.startPreview()
    cameraManager.cameraFrames.collect { frame ->
        val imageData = convertBitmapToImageData(frame.imageBitmap)
        val detections = inference.runInference(imageData, 0.5f)
        // Draw bounding boxes
    }
}
```

### Desktop (with ONNX converted models):
```kotlin
val inference = ModelInference()
if (inference.loadModel("yolov8_640_float32.onnx")) {
    cameraManager.startPreview()
    cameraManager.cameraFrames.collect { frame ->
        val imageData = convertBitmapToImageData(frame.imageBitmap)
        val detections = inference.runInference(imageData, 0.5f)
        // Draw bounding boxes
    }
}
```

### Web:
```kotlin
val inference = ModelInference()
if (inference.loadModel("yolov8_640_float32.tflite")) {
    cameraManager.startPreview()
    cameraManager.cameraFrames.collect { frame ->
        val imageData = convertBitmapToImageData(frame.imageBitmap)
        val detections = inference.runInference(imageData, 0.5f)
        // Draw bounding boxes
    }
}
```

---

## ⚠️ Current Limitations

1. **Desktop:** Needs ONNX conversion or alternative ML framework
2. **All platforms:** Image preprocessing (resize, normalize) needs implementation
3. **Web:** Camera getUserMedia needs full implementation
4. **Android:** Need to pass Context to ModelInference

---

## 🎯 Next Steps

1. **For Android:** Add Context passing mechanism (see updated code above)
2. **For Desktop:** Convert models to ONNX on a machine with sufficient disk space
3. **For Web:** Ensure web server serves `/resources/` folder correctly
4. **All platforms:** Implement proper image preprocessing pipeline

---

## 📝 Testing Models

To verify model loading works:

```kotlin
fun testModelLoading() {
    val inference = ModelInference()

    // Android
    inference.setContext(context)
    val loaded = inference.loadModel("yolov8_640_float32.tflite")
    println("Model loaded: $loaded")

    // Create dummy input
    val dummyImage = ImageData(640, 640, 0, ByteArray(640 * 640 * 3))
    val results = inference.runInference(dummyImage, 0.5f)
    println("Detections: ${results.size}")

    inference.close()
}
```
