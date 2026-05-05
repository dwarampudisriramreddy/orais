package com.ram.orai.orais

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
actual fun CameraButtonDetectionEffectPlatform(
    onKeyDetected: (Int) -> Unit
) {
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(view, lifecycleOwner) {
        val keyListener = android.view.View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                onKeyDetected(keyCode)
                true
            } else {
                false
            }
        }

        view.setOnKeyListener(keyListener)
        view.isFocusableInTouchMode = true
        view.requestFocus()

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                view.requestFocus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            view.setOnKeyListener(null)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }
}

actual fun getKeyCodeNamePlatform(keyCode: Int): String {
    return when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_DOWN -> "Volume Down"
        KeyEvent.KEYCODE_VOLUME_UP -> "Volume Up"
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "Media Play/Pause"
        KeyEvent.KEYCODE_MEDIA_NEXT -> "Media Next"
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "Media Previous"
        KeyEvent.KEYCODE_ENTER -> "Enter"
        KeyEvent.KEYCODE_SPACE -> "Space"
        KeyEvent.KEYCODE_CAMERA -> "Camera"
        KeyEvent.KEYCODE_MEDIA_PLAY -> "Media Play"
        KeyEvent.KEYCODE_MEDIA_PAUSE -> "Media Pause"
        KeyEvent.KEYCODE_MEDIA_STOP -> "Media Stop"
        else -> "Key Code: $keyCode"
    }
}

