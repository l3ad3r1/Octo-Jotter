package com.l3ad3r1.octojotter

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.test.core.app.ApplicationProvider
import com.l3ad3r1.octojotter.ui.canSatisfyAppLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The app lock must work on a device with no fingerprint sensor, using
 * Android's own pattern / PIN / password.
 *
 * `androidx.biometric` validates the authenticator combination per API level
 * and rejects `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` on exactly API 28 and 29:
 * `PromptInfo.Builder.build()` throws `IllegalArgumentException` and
 * `canAuthenticate()` returns `BIOMETRIC_ERROR_UNSUPPORTED`. Shipping that
 * combination would leave every Android 9 and 10 user (minSdk is 24) facing a
 * disabled Unlock button with no way into their own notes, now that the old
 * "turn off app lock" escape hatch is gone. `BIOMETRIC_WEAK` is the half of the
 * pair that is accepted everywhere, so it is what the app uses.
 *
 * API 29 is the level that matters — it and 28 are the same branch of
 * `AuthenticatorUtils.isSupportedCombination` (`SDK_INT < 28 || SDK_INT > 29`),
 * so covering one covers the rule. 33 and 34 check the modern path still works.
 * All three ship with Robolectric's cached SDK jars, so this needs no download.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 33, 34])
class AppLockAuthenticatorTest {

    /** Mirrors NoteApp.APP_LOCK_AUTHENTICATORS, which is private to that file. */
    private val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun setScreenLock(secure: Boolean) {
        val keyguard = context().getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        Shadows.shadowOf(keyguard).setIsDeviceSecure(secure)
    }

    @Test
    fun `the unlock prompt can actually be built on this api level`() {
        // build() is where androidx rejects an unsupported combination. If it
        // throws, the unlock button is dead and the user cannot get in.
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Octo Jotter")
            .setSubtitle("Use your fingerprint, pattern, PIN or password")
            .setAllowedAuthenticators(authenticators)
            .build()

        assertEquals(authenticators, info.allowedAuthenticators)
    }

    @Test
    fun `the combination is not reported unsupported on this api level`() {
        val status = BiometricManager.from(context()).canAuthenticate(authenticators)
        assertTrue(
            "authenticator combination rejected by BiometricManager",
            status != BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
        )
    }

    @Test
    fun `a device with only a screen lock and no biometrics can still unlock`() {
        // The case this exists for: a phone or tablet with no fingerprint
        // reader, secured with a pattern, PIN or password.
        setScreenLock(true)
        assertTrue(
            "a device with a screen lock but no fingerprint must still be able to unlock",
            canSatisfyAppLock(context()),
        )
    }

    @Test
    fun `a device with no screen lock at all reports that it cannot lock`() {
        // Nothing to authenticate against. Settings uses this to refuse to turn
        // the lock on, and the lock screen uses it to offer a way out instead
        // of stranding the user.
        setScreenLock(false)
        assertFalse(canSatisfyAppLock(context()))
    }

    @Test
    fun `the strong combination is rejected on api 28-29, which is why WEAK is used`() {
        // Pins the constraint itself. Swapping BIOMETRIC_WEAK for
        // BIOMETRIC_STRONG looks like a security upgrade and is in fact a
        // lockout on Android 9 and 10; this fails loudly if anyone tries, and
        // will also fail if a future library version lifts the restriction — at
        // which point the choice can be revisited deliberately.
        if (android.os.Build.VERSION.SDK_INT !in 28..29) return

        val strong = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val threw = runCatching {
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Octo Jotter")
                .setAllowedAuthenticators(strong)
                .build()
        }.exceptionOrNull()

        assertTrue(
            "BIOMETRIC_STRONG or DEVICE_CREDENTIAL was expected to be rejected on API " +
                "${android.os.Build.VERSION.SDK_INT}, got: $threw",
            threw is IllegalArgumentException,
        )
    }
}
