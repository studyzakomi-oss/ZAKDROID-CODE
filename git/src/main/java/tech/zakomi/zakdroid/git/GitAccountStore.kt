package tech.zakomi.zakdroid.git

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import tech.zakomi.zakdroid.core.di.IoDispatcher
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The commit identity and the GitHub access token. The token is encrypted with an
 * AES-GCM key that lives in the Android Keystore and never leaves it, so the stored
 * value is useless outside this device and app. It is never logged or shown again.
 */
@Singleton
class GitAccountStore @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val prefs = context.getSharedPreferences("git_account", Context.MODE_PRIVATE)

    suspend fun identity(): GitIdentity = withContext(io) {
        GitIdentity(prefs.getString(KEY_NAME, "").orEmpty(), prefs.getString(KEY_EMAIL, "").orEmpty())
    }

    suspend fun setIdentity(identity: GitIdentity) = withContext(io) {
        prefs.edit().putString(KEY_NAME, identity.name.trim()).putString(KEY_EMAIL, identity.email.trim()).apply()
    }

    suspend fun hasToken(): Boolean = withContext(io) { prefs.contains(KEY_TOKEN) }

    /** The token, or null if none is stored or it can no longer be decrypted (e.g. after a restore). */
    suspend fun token(): String? = withContext(io) {
        val stored = prefs.getString(KEY_TOKEN, null) ?: return@withContext null
        try {
            val parts = stored.split(':')
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /** Stores [token] encrypted; a blank value removes it. */
    suspend fun setToken(token: String) = withContext(io) {
        val clean = token.trim()
        if (clean.isEmpty()) {
            prefs.edit().remove(KEY_TOKEN).apply()
            return@withContext
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString(KEY_TOKEN, value).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "zakdroid.git.token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_NAME = "user.name"
        const val KEY_EMAIL = "user.email"
        const val KEY_TOKEN = "token"
    }
}
