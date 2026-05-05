package com.ram.orai.orais

actual class AuthManager {
    // Web: Would use Firebase JS SDK
    
    actual fun signInWithGoogle(onSuccess: (UserInfo) -> Unit, onError: (String) -> Unit) {
        onError("Google Sign-In not implemented for Web")
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
