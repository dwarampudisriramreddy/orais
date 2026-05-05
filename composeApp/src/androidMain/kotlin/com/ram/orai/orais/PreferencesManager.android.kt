package com.ram.orai.orais

actual class PreferencesManager {
    // TODO: Pass context via dependency injection or singleton
    private val prefs = mutableMapOf<String, Any>()

    actual fun putBoolean(key: String, value: Boolean) {
        prefs[key] = value
    }

    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs[key] as? Boolean ?: defaultValue
    }

    actual fun putString(key: String, value: String) {
        prefs[key] = value
    }

    actual fun getString(key: String, defaultValue: String): String {
        return prefs[key] as? String ?: defaultValue
    }

    actual fun putInt(key: String, value: Int) {
        prefs[key] = value
    }

    actual fun getInt(key: String, defaultValue: Int): Int {
        return prefs[key] as? Int ?: defaultValue
    }
}
