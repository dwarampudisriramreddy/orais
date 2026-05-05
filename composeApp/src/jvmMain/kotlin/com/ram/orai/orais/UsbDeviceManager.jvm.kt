package com.ram.orai.orais

actual class UsbDeviceManager {
    // Desktop: Could use usb4java or similar library
    
    actual fun initialize(onDeviceConnected: (UsbDeviceInfo) -> Unit) {
        // Stub implementation
    }
    
    actual fun requestPermission(deviceId: String) {
        // Not needed on desktop
    }
    
    actual fun openDevice(deviceId: String): Boolean {
        return false
    }
    
    actual fun closeDevice(deviceId: String) {
        // No-op
    }
    
    actual fun getConnectedDevices(): List<UsbDeviceInfo> {
        return emptyList()
    }
}
