package com.ram.orai.orais

import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

// Singleton key detection using AWT event listener (works globally, even with dialogs)
private object KeyDetectionWindow {
    private var listener: AWTEventListener? = null
    private val callbacks = mutableListOf<(Int) -> Unit>()
    private val toolkit = Toolkit.getDefaultToolkit()
    
    fun addCallback(callback: (Int) -> Unit) {
        synchronized(this) {
            if (listener == null) {
                createListener()
            }
            callbacks.add(callback)
            println("Added key callback, total callbacks: ${callbacks.size}")
        }
    }
    
    fun removeCallback(callback: (Int) -> Unit) {
        synchronized(this) {
            callbacks.remove(callback)
            println("Removed key callback, remaining: ${callbacks.size}")
            if (callbacks.isEmpty() && listener != null) {
                disposeListener()
            }
        }
    }
    
    private fun createListener() {
        val awtListener = AWTEventListener { event ->
            if (event is KeyEvent && event.id == KeyEvent.KEY_PRESSED) {
                val keyCode = event.keyCode
                val keyText = KeyEvent.getKeyText(keyCode)
                println("Global key detected: keyCode=$keyCode, keyText=$keyText, source=${event.source}")
                
                // Notify all callbacks
                callbacks.forEach { callback ->
                    try {
                        SwingUtilities.invokeLater {
                            callback(keyCode)
                        }
                    } catch (e: Exception) {
                        println("Error in key callback: $e")
                    }
                }
            }
        }
        
        // Add global AWT event listener - this captures ALL keyboard events system-wide
        toolkit.addAWTEventListener(awtListener, AWTEvent.KEY_EVENT_MASK)
        this.listener = awtListener
        println("Global AWT key listener created (captures all keyboard events)")
    }
    
    private fun disposeListener() {
        listener?.let {
            toolkit.removeAWTEventListener(it)
            listener = null
            println("Global AWT key listener removed")
        }
    }
}

@Composable
actual fun CameraButtonDetectionEffectPlatform(
    onKeyDetected: (Int) -> Unit
) {
    DisposableEffect(Unit) {
        KeyDetectionWindow.addCallback(onKeyDetected)
        println("Key detection callback registered")
        
        onDispose {
            KeyDetectionWindow.removeCallback(onKeyDetected)
            println("Key detection callback unregistered")
        }
    }
}

actual fun getKeyCodeNamePlatform(keyCode: Int): String {
    return when (keyCode) {
        KeyEvent.VK_DOWN -> "Volume Down (Down Arrow)"
        KeyEvent.VK_UP -> "Volume Up (Up Arrow)"
        KeyEvent.VK_SPACE -> "Space"
        KeyEvent.VK_ENTER -> "Enter"
        KeyEvent.VK_PAGE_DOWN -> "Page Down"
        KeyEvent.VK_PAGE_UP -> "Page Up"
        else -> "Key Code: $keyCode"
    }
}

