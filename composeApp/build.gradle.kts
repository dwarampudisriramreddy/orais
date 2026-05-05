import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {

    alias(libs.plugins.kotlinMultiplatform)

    alias(libs.plugins.androidApplication)

    alias(libs.plugins.composeMultiplatform)

    alias(libs.plugins.composeCompiler)

    alias(libs.plugins.composeHotReload)

}



kotlin {

    androidTarget {

        compilerOptions {

            jvmTarget.set(JvmTarget.JVM_11)

        }

    }



    jvm()



    js {

        browser()

        binaries.executable()

    }



    @OptIn(ExperimentalWasmDsl::class)

    wasmJs {

        browser()

        binaries.executable()

    }



    sourceSets {

        androidMain.dependencies {

            implementation(compose.preview)

            implementation(libs.androidx.activity.compose)

            // UVCCamera library
            implementation("org.uvccamera:lib:0.0.13")

            // CameraX for Android camera support
            val camerax_version = "1.3.4"
            // CameraX core library using the camera2 implementation
            implementation("androidx.camera:camera-core:${camerax_version}")
            implementation("androidx.camera:camera-camera2:${camerax_version}")
            // CameraX Lifecycle library
            implementation("androidx.camera:camera-lifecycle:${camerax_version}")
            // CameraX VideoCapture library
            implementation("androidx.camera:camera-video:${camerax_version}")
            // CameraX View class
            implementation("androidx.camera:camera-view:${camerax_version}")
            // CameraX Extensions library
            implementation("androidx.camera:camera-extensions:${camerax_version}")

            // Lifecycle
            implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")

            // ONNX Runtime for Android (supports .onnx models)
            implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.0")
            
            // TensorFlow Lite for Android (for FDI model if needed)
            implementation("org.tensorflow:tensorflow-lite:2.17.0")
            implementation("org.tensorflow:tensorflow-lite-gpu:2.17.0")
            implementation("org.tensorflow:tensorflow-lite-support:0.4.4") {
                // Avoid duplicate classes with LiteRT/TFLite API
                exclude(group = "com.google.ai.edge.litert", module = "litert-api")
                exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
            }

        }

        commonMain.dependencies {

            implementation(compose.runtime)

            implementation(compose.foundation)

            implementation(compose.material3)

            implementation(compose.ui)

            implementation(compose.components.resources)

            implementation(compose.components.uiToolingPreview)

            implementation(libs.androidx.lifecycle.viewmodelCompose)

            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            

        }

        commonTest.dependencies {

            implementation(libs.kotlin.test)

        }

        jvmMain.dependencies {

            implementation(compose.desktop.currentOs)

            implementation(libs.kotlinx.coroutinesSwing)
            
            // JavaCV for camera access (wraps OpenCV, more reliable than webcam-capture)
            implementation("org.bytedeco:javacv-platform:1.5.9")

            // ONNX Runtime for Desktop ML inference (TFLite models work after conversion)
            // Updated to 1.19.0 to support ONNX IR version 10 (required for YOLOv5 models)
            implementation("com.microsoft.onnxruntime:onnxruntime:1.19.0")
            
            // HID4Java for raw USB HID device access
            implementation("org.hid4java:hid4java:0.7.0")

        }

        val webMain = findByName("webMain")
        webMain?.dependencies {
            // MediaPipe Tasks for TFLite on Web (WASM)
            implementation(npm("@mediapipe/tasks-vision", "0.10.20"))
        }

    }

}



android {

    namespace = "com.ram.orai.orais"

    compileSdk = libs.versions.android.compileSdk.get().toInt()



    defaultConfig {

        applicationId = "com.ram.orai.orais"

        minSdk = libs.versions.android.minSdk.get().toInt()

        targetSdk = libs.versions.android.targetSdk.get().toInt()

        versionCode = 1

        versionName = "1.0"

    }

    packaging {

        resources {

            excludes += "/META-INF/{AL2.0,LGPL2.1}"

        }

    }

    buildTypes {

        getByName("release") {

            isMinifyEnabled = false

        }

    }

    compileOptions {

        sourceCompatibility = JavaVersion.VERSION_11

        targetCompatibility = JavaVersion.VERSION_11

    }

}



dependencies {

    debugImplementation(compose.uiTooling)

}



compose.desktop {

    application {

        mainClass = "com.ram.orai.orais.MainKt"



        nativeDistributions {

            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)

            packageName = "com.ram.orai.orais"

            packageVersion = "1.0.0"

        }

    }

}

