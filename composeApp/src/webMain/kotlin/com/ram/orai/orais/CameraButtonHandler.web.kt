package com.ram.orai.orais

// Web implementation (not supported)
actual fun createCameraButtonHandler(
    settings: IntraoralCameraSettings
): CameraButtonHandler? {
    // Web platform doesn't support hardware button access
    return null
}

@Composable
actual fun CameraButtonHandlerEffect(
    handler: CameraButtonHandler?,
    dispatcher: CameraButtonDispatcher
) {
    // Web: Not supported
}

