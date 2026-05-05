package com.ram.orai.orais

import android.view.KeyEvent
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

// Android KeyEvent handler (primary method)
class AndroidKeyEventHandler(
    private val settings: IntraoralCameraSettings,
    private val onEvent: (CameraButtonEvent) -> Unit
) : CameraButtonHandler {
    private var isActive = false
    
    override fun startListening(onEvent: (CameraButtonEvent) -> Unit) {
        isActive = true
    }
    
    override fun stopListening() {
        isActive = false
    }
    
    override fun isListening(): Boolean = isActive
    
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!isActive || event.action != KeyEvent.ACTION_DOWN) {
            return false
        }
        
        val buttonEvent = CameraButtonEvent(keyCode = event.keyCode)
        onEvent(buttonEvent)
        return true
    }
}

// Android implementation factory
actual fun createCameraButtonHandler(
    settings: IntraoralCameraSettings
): CameraButtonHandler? {
    return when (settings.interfaceType) {
        HardwareInterfaceType.KEY_EVENT -> {
            // Return a handler that will be set up in Compose
            object : CameraButtonHandler {
                private var onEventCallback: ((CameraButtonEvent) -> Unit)? = null
                private var isActive = false
                
                override fun startListening(onEvent: (CameraButtonEvent) -> Unit) {
                    isActive = true
                    onEventCallback = onEvent
                }
                
                override fun stopListening() {
                    isActive = false
                    onEventCallback = null
                }
                
                override fun isListening(): Boolean = isActive
            }
        }
        HardwareInterfaceType.RAW_HID -> {
            // TODO: Implement raw HID handler
            null
        }
        HardwareInterfaceType.UVC_EXTENSION -> {
            // TODO: Implement UVC extension handler
            null
        }
        HardwareInterfaceType.NONE -> null
    }
}

// Compose integration for Android KeyEvent handling
@Composable
actual fun CameraButtonHandlerEffect(
    handler: CameraButtonHandler?,
    dispatcher: CameraButtonDispatcher
) {
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    DisposableEffect(handler, view, lifecycleOwner) {
        if (handler == null) {
            onDispose { }
            return@DisposableEffect onDispose { }
        }
        
        // Create key event interceptor
        val keyEventInterceptor = object : android.view.View.OnKeyListener {
            override fun onKey(v: android.view.View?, keyCode: Int, event: android.view.KeyEvent?): Boolean {
                if (event?.action == KeyEvent.ACTION_DOWN && handler.isListening()) {
                    val buttonEvent = CameraButtonEvent(keyCode = keyCode)
                    dispatcher.handleEvent(buttonEvent)
                    return true
                }
                return false
            }
        }
        
        // Set up key event listener on root view
        view.setOnKeyListener(keyEventInterceptor)
        view.isFocusableInTouchMode = true
        view.requestFocus()
        
        // Lifecycle observer to handle focus
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

