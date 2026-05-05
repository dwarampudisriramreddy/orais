package com.ram.orai.orais

actual class AuthManager {
    actual fun signInWithGoogle(onSuccess: (UserInfo) -> Unit, onError: (String) -> Unit) {
        onError("Google Sign-In not implemented for Desktop")
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
