package com.ram.orai.orais

actual class AuthManager {
    // TODO: Add Firebase dependencies to build.gradle.kts

    actual fun signInWithGoogle(onSuccess: (UserInfo) -> Unit, onError: (String) -> Unit) {
        onError("Firebase Auth not configured")
    }

    actual fun signOut() {
        // No-op
    }

    actual fun getCurrentUser(): UserInfo? {
        return null
    }

    actual fun checkBlockStatus(userId: String, onResult: (Boolean) -> Unit) {
        onResult(false)
    }
}
