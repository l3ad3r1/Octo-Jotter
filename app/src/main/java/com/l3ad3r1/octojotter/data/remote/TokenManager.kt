package com.l3ad3r1.octojotter.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Stores the GitHub personal access token in EncryptedSharedPreferences, keyed
 * by an AES256-GCM master key held in the device keystore.
 *
 * That keystore key is device-bound: it is not included in a cloud backup and
 * is not reissued by a device-to-device transfer. If the encrypted file ever
 * arrives on a device whose keystore cannot decrypt it — a restored backup that
 * predates the exclusion rules, a keystore reset after a lock-screen change, or
 * corruption — `EncryptedSharedPreferences.create()` throws. This class is
 * constructed from NoteViewModel, so an uncaught throw there takes the app down
 * on launch and leaves it unlaunchable.
 *
 * So creation is attempted, and on failure the unreadable file and its key alias
 * are cleared and creation is retried once. If that still fails the app runs
 * with token storage unavailable: notes and offline editing work, sync asks the
 * user to sign in again. Degraded, never fatal, and never silently downgraded to
 * plaintext storage.
 */
class TokenManager(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences? by lazy {
        createEncryptedPrefs() ?: run {
            Log.w(TAG, "Token store unreadable; clearing it and retrying once")
            clearCorruptStore()
            createEncryptedPrefs()
        }
    }

    /** False when the encrypted store could not be opened at all. */
    val isStorageAvailable: Boolean get() = prefs != null

    private fun createEncryptedPrefs(): SharedPreferences? = try {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // GeneralSecurityException, IOException and the AEAD/proto failures that
        // surface as unchecked exceptions all mean the same thing here.
        Log.e(TAG, "Could not open encrypted token store", e)
        null
    }

    /** Delete the undecryptable prefs file and the keystore alias behind it. */
    private fun clearCorruptStore() {
        runCatching {
            appContext.deleteSharedPreferences(PREFS_NAME)
        }.onFailure { Log.e(TAG, "Could not delete token prefs", it) }
        runCatching {
            val ks = java.security.KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(MASTER_KEY_ALIAS)) ks.deleteEntry(MASTER_KEY_ALIAS)
        }.onFailure { Log.e(TAG, "Could not delete master key alias", it) }
    }

    private val _tokenFlow = MutableStateFlow(getToken() ?: "")
    val tokenFlow: StateFlow<String> = _tokenFlow

    fun saveToken(token: String) {
        val store = prefs
        if (store == null) {
            Log.e(TAG, "Token not persisted: encrypted storage unavailable")
        } else {
            store.edit().putString(KEY_GITHUB_TOKEN, token).apply()
        }
        // Reflect it either way so the current session can sync.
        _tokenFlow.value = token
    }

    fun getToken(): String? {
        val raw = runCatching { prefs?.getString(KEY_GITHUB_TOKEN, null) }
            .onFailure { Log.e(TAG, "Could not read token", it) }
            .getOrNull()
        return if (raw.isNullOrEmpty()) null else raw
    }

    fun clearToken() {
        runCatching { prefs?.edit()?.remove(KEY_GITHUB_TOKEN)?.apply() }
            .onFailure { Log.e(TAG, "Could not clear token", it) }
        _tokenFlow.value = ""
    }

    companion object {
        private const val TAG = "TokenManager"
        private const val PREFS_NAME = "secure_gist_notes_prefs"
        private const val KEY_GITHUB_TOKEN = "github_pat_token"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        /** Default alias used by MasterKey.Builder. */
        private const val MASTER_KEY_ALIAS = MasterKey.DEFAULT_MASTER_KEY_ALIAS
    }
}
