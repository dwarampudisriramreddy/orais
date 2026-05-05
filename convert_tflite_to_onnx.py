#!/usr/bin/env python3
"""
Convert TensorFlow Lite model to ONNX format for Desktop (JVM) usage

Requirements:
pip install tf2onnx tensorflow onnx

Usage:
python convert_tflite_to_onnx.py your_model.tflite output_model.onnx
"""

import sys
import tensorflow as tf
import tf2onnx

def convert_tflite_to_onnx(tflite_path, onnx_path):
    # Load TFLite model
    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()

    # Get input/output details
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print(f"Input shape: {input_details[0]['shape']}")
    print(f"Output count: {len(output_details)}")

    # Convert TFLite to TF concrete function
    # This is a simplified approach - may need adjustments based on your model

    # Alternative: Use tf2onnx directly
    # tf2onnx.convert.from_tflite(tflite_path, output_path=onnx_path)

    print(f"\nTo convert manually, run:")
    print(f"python -m tf2onnx.convert --tflite {tflite_path} --output {onnx_path}")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python convert_tflite_to_onnx.py input.tflite output.onnx")
        sys.exit(1)

    tflite_path = sys.argv[1]
    onnx_path = sys.argv[2]

    convert_tflite_to_onnx(tflite_path, onnx_path)
