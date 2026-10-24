package com.tdater.blackbox.system

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Fingerprint, face or the phone's own PIN/pattern in front of BLACKBOX. Nothing is stored by BLACKBOX itself. */
object AppLock {
    private const val ALLOWED = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** Re-lock after the app has been in the background this long. Folding/unfolding never re-locks. */
    const val RELOCK_AFTER_MS = 30_000L

    fun canUse(context: Context) = BiometricManager.from(context).canAuthenticate(ALLOWED) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(activity: FragmentActivity, onUnlocked: () -> Unit, onFailed: (String) -> Unit) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailed(errString.toString())
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock BLACKBOX")
            .setSubtitle("Use your fingerprint, face or screen lock")
            .setAllowedAuthenticators(ALLOWED)
            .build())
    }
}
