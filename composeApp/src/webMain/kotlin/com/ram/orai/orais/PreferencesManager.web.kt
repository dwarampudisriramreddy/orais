package com.ram.orai.orais

actual class PreferencesManager {
    // Web: Use localStorage
    
    actual fun putBoolean(key: String, value: Boolean) {
        js("localStorage.setItem(key, value.toString())")
    }
    
    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val stored = js("localStorage.getItem(key)")
        return if (stored != null) stored.toString() == "true" else defaultValue
    }
    
    actual fun putString(key: String, value: String) {
        js("localStorage.setItem(key, value)")
    }
    
    actual fun getString(key: String, defaultValue: String): String {
        val stored = js("localStorage.getItem(key)")
        return stored?.toString() ?: defaultValue
    }
    
    actual fun putInt(key: String, value: Int) {
        js("localStorage.setItem(key, value.toString())")
    }
    
    actual fun getInt(key: String, defaultValue: Int): Int {
        val stored = js("localStorage.getItem(key)")
        return stored?.toString()?.toIntOrNull() ?: defaultValue
    }
}
