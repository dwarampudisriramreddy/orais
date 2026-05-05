package com.ram.orai.orais

import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.JFrame
import javax.swing.JPanel
import org.hid4java.HidDevice
import org.hid4java.HidManager
import org.hid4java.HidServices
import org.hid4java.HidServicesListener
import org.hid4java.event.HidServicesEvent
import kotlinx.coroutines.*
import androidx.compose.runtime.Composable

// JVM AWT KeyEvent handler (primary method)
class JvmKeyEventHandler(
    private val settings: IntraoralCameraSettings,
    private val onEvent: (CameraButtonEvent) -> Unit
) : CameraButtonHandler {
    private var isActive = false
    private var frame: JFrame? = null
    private var panel: JPanel? = null
    private var keyListener: KeyListener? = null
    
    override fun startListening(onEvent: (CameraButtonEvent) -> Unit) {
        if (isActive) return
        
        isActive = true
        
        // Create a hidden frame to capture keyboard events
        val frame = JFrame()
        frame.isUndecorated = true
        frame.isVisible = false
        frame.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        
        val panel = JPanel()
        panel.isFocusable = true
        
        val keyListener = object : KeyListener {
            override fun keyPressed(e: KeyEvent) {
                if (isActive) {
                    val buttonEvent = CameraButtonEvent(keyCode = e.keyCode)
                    onEvent(buttonEvent)
                }
            }
            override fun keyReleased(e: KeyEvent) {}
            override fun keyTyped(e: KeyEvent) {}
        }
        
        panel.addKeyListener(keyListener)
        frame.add(panel)
        frame.pack()
        frame.isVisible = true
        panel.requestFocus()
        
        this.frame = frame
        this.panel = panel
        this.keyListener = keyListener
        println("JVM KeyEvent handler started listening")
    }
    
    override fun stopListening() {
        if (!isActive) return
        
        isActive = false
        panel?.removeKeyListener(keyListener)
        frame?.dispose()
        frame = null
        panel = null
        keyListener = null
        println("JVM KeyEvent handler stopped listening")
    }
    
    override fun isListening(): Boolean = isActive
}

// JVM implementation factory
actual fun createCameraButtonHandler(
    settings: IntraoralCameraSettings
): CameraButtonHandler? {
    return when (settings.interfaceType) {
        HardwareInterfaceType.KEY_EVENT -> {
            // Return handler that uses the global AWT listener (no window needed)
            // The actual listening is done by CameraButtonDetectionEffectPlatform
            object : CameraButtonHandler {
                private var isActive = false
                
                override fun startListening(onEvent: (CameraButtonEvent) -> Unit) {
                    if (isActive) return
                    isActive = true
                    // The global AWT listener in CameraButtonDetectionEffectPlatform
                    // will handle the actual key detection
                    println("JVM KeyEvent handler started (using global AWT listener)")
                }
                
                override fun stopListening() {
                    if (!isActive) return
                    isActive = false
                    println("JVM KeyEvent handler stopped")
                }
                
                override fun isListening(): Boolean = isActive
            }
        }
        HardwareInterfaceType.RAW_HID -> {
            RawHidButtonHandler(settings)
        }
        HardwareInterfaceType.UVC_EXTENSION -> {
            // Not supported on JVM
            null
        }
        HardwareInterfaceType.NONE -> null
    }
}

@Composable
actual fun CameraButtonHandlerEffect(
    handler: CameraButtonHandler?,
    dispatcher: CameraButtonDispatcher
) {
    // JVM: Use global AWT listener for KEY_EVENT mode (no window needed)
    if (handler != null && handler.isListening()) {
        CameraButtonDetectionEffectPlatform { keyCode ->
            // Convert key code to CameraButtonEvent and dispatch
            val event = CameraButtonEvent(keyCode = keyCode)
            dispatcher.handleEvent(event)
        }
    }
}

// Raw HID handler for USB devices
class RawHidButtonHandler(
    private val settings: IntraoralCameraSettings
) : CameraButtonHandler {
    private var isActive = false
    private var hidServices: HidServices? = null
    private var hidDevice: HidDevice? = null
    private var onEventCallback: ((CameraButtonEvent) -> Unit)? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun startListening(onEvent: (CameraButtonEvent) -> Unit) {
        if (isActive) return
        
        isActive = true
        onEventCallback = onEvent
        
        try {
            // Initialize HID services
            val services = HidManager.getHidServices()
            services.addHidServicesListener(object : HidServicesListener {
                override fun hidDeviceAttached(event: HidServicesEvent) {
                    println("HID device attached: ${event.hidDevice}")
                    if (isActive && hidDevice == null) {
                        connectToDevice(services)
                    }
                }
                
                override fun hidDeviceDetached(event: HidServicesEvent) {
                    println("HID device detached: ${event.hidDevice}")
                    if (event.hidDevice == hidDevice) {
                        hidDevice = null
                    }
                }
                
                override fun hidFailure(event: HidServicesEvent) {
                    println("HID failure: ${event.hidDevice}")
                }
            })
            
            this.hidServices = services
            
            // Try to connect to device
            connectToDevice(services)
            
            println("Raw HID handler started")
        } catch (e: Exception) {
            println("Error starting Raw HID handler: ${e.message}")
            e.printStackTrace()
            isActive = false
        }
    }
    
    private fun connectToDevice(services: HidServices) {
        val targetVid = settings.deviceVid ?: 0xEB1A // Default to user's device
        val targetPid = settings.devicePid ?: 0x5000
        
        println("Searching for HID device: VID=0x${targetVid.toString(16).uppercase()}, PID=0x${targetPid.toString(16).uppercase()}")
        
        // List all available HID devices for debugging
        println("All available HID devices:")
        services.attachedHidDevices.forEach { dev ->
            println("  - VID=0x${dev.vendorId.toString(16).uppercase()}, PID=0x${dev.productId.toString(16).uppercase()}, " +
                    "Product='${dev.product}', Manufacturer='${dev.manufacturer}', " +
                    "Serial='${dev.serialNumber}', Path='${dev.path}'")
        }
        
        val device = services.attachedHidDevices.find { device ->
            device.vendorId == targetVid && device.productId == targetPid
        }
        
        if (device != null) {
            try {
                println("Found matching device, attempting to open...")
                println("  Interface Number: ${device.interfaceNumber}")
                println("  Usage Page: ${device.usagePage}")
                println("  Usage: ${device.usage}")
                
                if (device.open()) {
                    this.hidDevice = device
                    println("✓ Connected to HID device: ${device.product}, ${device.manufacturer}")
                    
                    // Start reading HID reports in a coroutine
                    readJob = scope.launch {
                        readHidReports(device)
                    }
                } else {
                    println("✗ Failed to open HID device. It may be in use by another application.")
                    println("  Try closing other applications that might be using this device.")
                }
            } catch (e: Exception) {
                println("✗ Error opening HID device: ${e.message}")
                e.printStackTrace()
            }
        } else {
            println("✗ HID device not found: VID=0x${targetVid.toString(16).uppercase()}, PID=0x${targetPid.toString(16).uppercase()}")
            println("")
            println("Possible reasons:")
            println("  1. Device is not a standard HID device (might be a composite USB device)")
            println("  2. Device drivers are not installed")
            println("  3. Device is using a different USB interface (not HID)")
            println("  4. Device needs to be accessed through a different method")
            println("")
            println("Your device path shows: USB\\VID_EB1A&PID_5000&REV_0312&MI_00")
            println("The 'MI_00' indicates it's interface 0 of a composite device.")
            println("The button interface might be on a different interface number.")
            println("")
            println("Try:")
            println("  - Check if the device appears in Device Manager")
            println("  - Try using 'Keyboard/Media Keys' mode instead if the buttons send keyboard events")
            println("  - The device might need special drivers or permissions")
        }
    }
    
    private suspend fun readHidReports(device: HidDevice) {
        val buffer = ByteArray(64) // Standard HID report size
        var consecutiveErrors = 0
        val maxErrors = 10
        
        println("Starting to read HID reports from device...")
        
        while (isActive && device.isOpen) {
            try {
                val bytesRead = withContext(Dispatchers.IO) {
                    device.read(buffer, 100) // 100ms timeout
                }
                
                if (bytesRead > 0) {
                    consecutiveErrors = 0
                    val report = buffer.copyOf(bytesRead)
                    val hexString = report.joinToString(" ") { "%02X".format(it) }
                    println("✓ HID report received ($bytesRead bytes): $hexString")
                    
                    // Create event with HID report
                    val event = CameraButtonEvent(hidReport = report)
                    onEventCallback?.invoke(event)
                } else if (bytesRead == 0) {
                    // Timeout - this is normal, just continue
                    consecutiveErrors = 0
                }
            } catch (e: Exception) {
                consecutiveErrors++
                if (isActive) {
                    if (consecutiveErrors <= maxErrors) {
                        println("⚠ Error reading HID report (attempt $consecutiveErrors/$maxErrors): ${e.message}")
                    } else if (consecutiveErrors == maxErrors + 1) {
                        println("⚠ Too many errors, will stop reporting but continue trying...")
                    }
                }
                if (consecutiveErrors > maxErrors * 2) {
                    println("✗ Too many consecutive errors, stopping HID read loop")
                    break
                }
                delay(100) // Wait before retrying
            }
        }
        
        println("Stopped reading HID reports")
    }
    
    override fun stopListening() {
        if (!isActive) return
        
        isActive = false
        readJob?.cancel()
        readJob = null
        
        hidDevice?.close()
        hidDevice = null
        
        hidServices?.shutdown()
        hidServices = null
        
        onEventCallback = null
        println("Raw HID handler stopped")
    }
    
    override fun isListening(): Boolean = isActive && hidDevice?.isOpen == true
}

