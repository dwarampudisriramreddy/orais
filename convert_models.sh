#!/bin/bash
# Convert TFLite models to ONNX for Desktop (JVM)

echo "Installing required packages..."
pip install tf2onnx tensorflow-cpu onnx --quiet

echo ""
echo "Converting models..."
echo "==================="

# Model 1: fdi_float32.tflite
echo "Converting fdi_float32.tflite..."
python -m tf2onnx.convert \
  --tflite composeApp/src/jvmMain/resources/fdi_float32.tflite \
  --output composeApp/src/jvmMain/resources/fdi_float32.onnx \
  --opset 13

# Model 2: yolov8_640_float32.tflite
echo "Converting yolov8_640_float32.tflite..."
python -m tf2onnx.convert \
  --tflite composeApp/src/jvmMain/resources/yolov8_640_float32.tflite \
  --output composeApp/src/jvmMain/resources/yolov8_640_float32.onnx \
  --opset 13

echo ""
echo "Conversion complete!"
echo "ONNX models saved to: composeApp/src/jvmMain/resources/"
ls -lh composeApp/src/jvmMain/resources/*.onnx
