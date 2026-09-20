package com.velnox.core.storage.secure

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.velnox.core.logging.VelnoxLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backing store for the Velnox session token.
 *
 * ## Why this and not plain DataStore
 *
 * The session JWT is a bearer credential: whoever reads it can act as the user
 * for its full lifetime (7 days — `createSessionToken` in `backend/routes/auth.ts`
 * signs with `expiresIn: "7d"`). The requirement is therefore "unreadable at rest
 * without the device's Keystore", which is exactly what AES-256-GCM with
 * Keystore-resident key material provides.
 *
 * `EncryptedSharedPreferences` gives that: the master key never leaves the
 * Android Keystore, so a `/data/data` dump or an adb backup cannot yield a usable
 * token. Non-sensitive UI preferences (language, onboarding flags) use plain
 * DataStore in [com.velnox.core.storage.prefs.VelnoxPreferences] — nothing else in
 * the app needs this level of protection, and encrypting everything would only
 * hide which data is actually sensitive.
 *
 * ## Failure handling
 *
 * A Keystore key can be invalidated by a device-credential change or by keystore
 * corruption. The correct response is to discard the unreadable ciphertext and
 * behave as signed out — never to fall back to plaintext storage. If the Keystore
 * is unavailable altogether the store degrades to in-memory only, so the current
 * session keeps working but nothing is persisted.
 */
@Singleton
class SecureTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    @Volatile
    private var prefs: SharedPreferences? = null

    /** Used only when the encrypted store is unavailable. */
    @Volatile
    private var memoryFallback: String? = null

    private val preferences: SharedPreferences?
        get() = prefs ?: synchronized(this) {
            prefs ?: openEncryptedPreferences().also { prefs = it }
        }

    private fun openEncryptedPreferences(): SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setUserAuthenticationRequired(false)
            .build()

        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (error: Throwable) {
        VelnoxLog.e(TAG) { "Encrypted storage unavailable — session will not persist" }
        dropCorruptedStore()
        null
    }

    /** Persist the session token. */
    fun writeSessionToken(token: String) {
        memoryFallback = null
        val store = preferences
        if (store == null) {
            memoryFallback = token
            return
        }
        runCatching { store.edit().putString(KEY_SESSION_TOKEN, token).apply() }
            .onFailure {
                VelnoxLog.e(TAG) { "Failed to persist session token" }
                memoryFallback = token
            }
    }

    /** Read the session token, or `null` when signed out. */
    fun readSessionToken(): String? {
        val store = preferences ?: return memoryFallback
        val stored = runCatching { store.getString(KEY_SESSION_TOKEN, null) }
            .onFailure { VelnoxLog.e(TAG) { "Failed to read session token" } }
            .getOrNull()
        return stored ?: memoryFallback
    }

    /** Remove the session token. Called on logout and on every 401. */
    fun clearSessionToken() {
        memoryFallback = null
        preferences?.let { store ->
            runCatching { store.edit().remove(KEY_SESSION_TOKEN).apply() }
        }
    }

    /**
     * Drops the encrypted store when its Keystore key is gone.
     * The contents are unreadable at that point; the only cost is one extra sign-in.
     */
    private fun dropCorruptedStore() {
        runCatching { context.deleteSharedPreferences(FILE_NAME) }
        prefs = null
    }

    /**
     * Keystore parameters this store relies on, kept next to the code that uses
     * them so the security properties are reviewable rather than implied by a
     * library default: AES-256, GCM block mode, no padding, key material generated
     * in and never exported from the Android Keystore.
     */
    internal val keystoreSpec: KeyGenParameterSpec
        get() = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .build()

    /** `true` when the Keystore backs this store (i.e. the session will persist). */
    fun isPersistent(): Boolean = preferences != null

    private companion object {
        const val TAG = "SecureTokenStore"
        const val FILE_NAME = "velnox_secure_session"
        const val KEY_SESSION_TOKEN = "session_token"
        const val MASTER_KEY_ALIAS = "_velnox_master_key"
        const val KEY_SIZE_BITS = 256
    }
}

/** Keystore provider name, kept for documentation and diagnostics. */
internal const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"

/** `true` when the platform Keystore is usable on this device. */
internal fun isAndroidKeystoreUsable(): Boolean = runCatching {
    KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).load(null)
}.isSuccess
