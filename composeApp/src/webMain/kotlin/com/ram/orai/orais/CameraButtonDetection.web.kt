package com.ram.orai.orais

import androidx.compose.runtime.Composable

@Composable
actual fun CameraButtonDetectionEffectPlatform(
    onKeyDetected: (Int) -> Unit
) {
    // Web: Not supported
}

actual fun getKeyCodeNamePlatform(keyCode: Int): String {
    return "Key Code: $keyCode"
}

