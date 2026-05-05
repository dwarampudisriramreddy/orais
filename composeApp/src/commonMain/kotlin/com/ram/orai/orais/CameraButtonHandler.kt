package com.ram.orai.orais

// Common camera actions
enum class CameraAction {
    CAPTURE,
    LIGHT_TOGGLE
}

// Hardware interface types
enum class HardwareInterfaceType {
    KEY_EVENT,      // HID keyboard/media keys (most common)
    RAW_HID,        // Raw HID reports (advanced)
    UVC_EXTENSION,  // UVC extension units (Android only)
    NONE            // No hardware button support
}

// Normalized button event
data class CameraButtonEvent(
    val keyCode: Int? = null,
    val hidReport: ByteArray? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CameraButtonEvent) return false
        if (keyCode != other.keyCode) return false
        if (hidReport != null) {
            if (other.hidReport == null) return false
            if (!hidReport.contentEquals(other.hidReport)) return false
        } else if (other.hidReport != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = keyCode ?: 0
        result = 31 * result + (hidReport?.contentHashCode() ?: 0)
        return result
    }
}

// Button mapping configuration
data class ButtonMapping(
    val action: CameraAction,
    val event: CameraButtonEvent
)

// Intraoral camera settings
data class IntraoralCameraSettings(
    val interfaceType: HardwareInterfaceType = HardwareInterfaceType.KEY_EVENT,
    val capture: ButtonMapping? = null,
    val light: ButtonMapping? = null,
    val deviceVid: Int? = null,  // USB Vendor ID
    val devicePid: Int? = null,   // USB Product ID
    // Long press recording
    val enableLongPressRecording: Boolean = false,
    // Button sensitivity
    val buttonDebounceMs: Int = 500,  // 0-2000ms
    val longPressThresholdMs: Int = 800,  // 0-3000ms
    // Advanced pattern matching
    val advancedMode: Boolean = false,
    val capturePattern: String = "",  // Hex pattern for capture button
    val lightPattern: String = "",    // Hex pattern for light button
    // Test mode
    val testMode: Boolean = false
)

// Common button handler interface
interface CameraButtonHandler {
    fun startListening(onEvent: (CameraButtonEvent) -> Unit)
    fun stopListening()
    fun isListening(): Boolean
}

// Common dispatcher (shared logic)
class CameraButtonDispatcher(
    private val settings: IntraoralCameraSettings,
    private val onCapture: () -> Unit,
    private val onLight: () -> Unit,
    private val onLongPressStart: () -> Unit = {},  // Start recording
    private val onLongPressEnd: () -> Unit = {}     // Stop recording
) {
    private var lastEventTime = 0L
    private var capturePressStartTime: Long? = null
    private var isLongPressActive = false
    
    fun handleEvent(event: CameraButtonEvent) {
        val now = System.currentTimeMillis()
        
        // Debounce: ignore events within configured debounce time
        if (now - lastEventTime < settings.buttonDebounceMs) {
            return
        }
        lastEventTime = now
        
        // Check capture mapping
        settings.capture?.let { mapping ->
            if (eventsMatch(event, mapping.event)) {
                handleCaptureEvent(now)
                return
            }
        }
        
        // Check light mapping
        settings.light?.let { mapping ->
            if (eventsMatch(event, mapping.event)) {
                onLight()
                return
            }
        }
    }
    
    private fun handleCaptureEvent(now: Long) {
        if (settings.enableLongPressRecording) {
            // Long press mode: track press duration
            if (capturePressStartTime == null) {
                // Press started
                capturePressStartTime = now
                isLongPressActive = false
            } else {
                // Check if long press threshold reached
                val pressDuration = now - capturePressStartTime!!
                if (!isLongPressActive && pressDuration >= settings.longPressThresholdMs) {
                    // Long press detected - start recording
                    isLongPressActive = true
                    onLongPressStart()
                }
            }
        } else {
            // Normal mode: immediate capture
            onCapture()
        }
    }
    
    fun handleCaptureRelease() {
        if (settings.enableLongPressRecording && capturePressStartTime != null) {
            val pressDuration = System.currentTimeMillis() - capturePressStartTime!!
            if (isLongPressActive) {
                // Long press was active - stop recording
                onLongPressEnd()
            } else if (pressDuration < settings.longPressThresholdMs) {
                // Short press - take photo
                onCapture()
            }
            capturePressStartTime = null
            isLongPressActive = false
        }
    }
    
    private fun eventsMatch(event1: CameraButtonEvent, event2: CameraButtonEvent): Boolean {
        // Match by keyCode if both have it
        if (event1.keyCode != null && event2.keyCode != null) {
            return event1.keyCode == event2.keyCode
        }
        
        // Match by HID report if both have it
        if (event1.hidReport != null && event2.hidReport != null) {
            return event1.hidReport.contentEquals(event2.hidReport)
        }
        
        return false
    }
}

// Platform-specific button handler factory
expect fun createCameraButtonHandler(
    settings: IntraoralCameraSettings
): CameraButtonHandler?

// Settings persistence helpers
fun saveCameraButtonSettings(
    prefs: PreferencesManager,
    settings: IntraoralCameraSettings
) {
    prefs.putString("camera_button_interface_type", settings.interfaceType.name)
    prefs.putInt("camera_button_capture_keycode", settings.capture?.event?.keyCode ?: -1)
    prefs.putInt("camera_button_light_keycode", settings.light?.event?.keyCode ?: -1)
    prefs.putInt("camera_button_device_vid", settings.deviceVid ?: -1)
    prefs.putInt("camera_button_device_pid", settings.devicePid ?: -1)
    prefs.putBoolean("enable_long_press_recording", settings.enableLongPressRecording)
    prefs.putInt("button_debounce_ms", settings.buttonDebounceMs)
    prefs.putInt("long_press_threshold_ms", settings.longPressThresholdMs)
    prefs.putBoolean("advanced_mode", settings.advancedMode)
    prefs.putString("capture_pattern", settings.capturePattern)
    prefs.putString("light_pattern", settings.lightPattern)
    prefs.putBoolean("test_mode", settings.testMode)
}

fun loadCameraButtonSettings(
    prefs: PreferencesManager
): IntraoralCameraSettings {
    val interfaceTypeName = prefs.getString("camera_button_interface_type", HardwareInterfaceType.KEY_EVENT.name)
    val interfaceType = try {
        HardwareInterfaceType.valueOf(interfaceTypeName)
    } catch (e: Exception) {
        HardwareInterfaceType.KEY_EVENT
    }
    
    val captureKeyCode = prefs.getInt("camera_button_capture_keycode", -1)
    val lightKeyCode = prefs.getInt("camera_button_light_keycode", -1)
    val deviceVid = prefs.getInt("camera_button_device_vid", -1).takeIf { it != -1 }
    val devicePid = prefs.getInt("camera_button_device_pid", -1).takeIf { it != -1 }
    
    val capture = if (captureKeyCode != -1) {
        ButtonMapping(
            action = CameraAction.CAPTURE,
            event = CameraButtonEvent(keyCode = captureKeyCode)
        )
    } else null
    
    val light = if (lightKeyCode != -1) {
        ButtonMapping(
            action = CameraAction.LIGHT_TOGGLE,
            event = CameraButtonEvent(keyCode = lightKeyCode)
        )
    } else null
    
    return IntraoralCameraSettings(
        interfaceType = interfaceType,
        capture = capture,
        light = light,
        deviceVid = deviceVid,
        devicePid = devicePid,
        enableLongPressRecording = prefs.getBoolean("enable_long_press_recording", false),
        buttonDebounceMs = prefs.getInt("button_debounce_ms", 500),
        longPressThresholdMs = prefs.getInt("long_press_threshold_ms", 800),
        advancedMode = prefs.getBoolean("advanced_mode", false),
        capturePattern = prefs.getString("capture_pattern", "") ?: "",
        lightPattern = prefs.getString("light_pattern", "") ?: "",
        testMode = prefs.getBoolean("test_mode", false)
    )
}

// Helper function to parse hex pattern string
fun parsePattern(pattern: String): ByteArray? {
    if (pattern.isBlank()) return null

    try {
        val bytes = pattern.trim().split(" ").mapNotNull { hex ->
            if (hex.equals("XX", ignoreCase = true)) {
                null // Wildcard - any value
            } else {
                hex.toIntOrNull(16)?.toByte()
            }
        }
        return bytes.toByteArray()
    } catch (e: Exception) {
        return null
    }
}

// Helper function to match pattern with wildcards
fun matchesPattern(data: ByteArray, pattern: String): Boolean {
    val patternBytes = parsePattern(pattern) ?: return false
    if (data.size < patternBytes.size) return false

    val patternParts = pattern.trim().split(" ")
    for (i in patternParts.indices) {
        if (patternParts[i].equals("XX", ignoreCase = true)) {
            continue // Wildcard matches anything
        }
        if (i >= data.size || data[i] != patternBytes[i]) {
            return false
        }
    }
    return true
}

