package com.ram.orai.orais

import java.util.prefs.Preferences

actual class PreferencesManager {
    private val prefs = Preferences.userNodeForPackage(PreferencesManager::class.java)
    
    actual fun putBoolean(key: String, value: Boolean) {
        prefs.putBoolean(key, value)
    }
    
    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }
    
    actual fun putString(key: String, value: String) {
        prefs.put(key, value)
    }
    
    actual fun getString(key: String, defaultValue: String): String {
        return prefs.get(key, defaultValue)
    }
    
    actual fun putInt(key: String, value: Int) {
        prefs.putInt(key, value)
    }
    
    actual fun getInt(key: String, defaultValue: Int): Int {
        return prefs.getInt(key, defaultValue)
    }
}
