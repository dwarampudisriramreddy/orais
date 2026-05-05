package com.ram.orai.orais

actual fun getCameraManager(context: Any?, lifecycleOwner: Any?): CameraController = CameraManagerImpl(context, lifecycleOwner)

actual fun getPlatformName(): String = "Desktop"
