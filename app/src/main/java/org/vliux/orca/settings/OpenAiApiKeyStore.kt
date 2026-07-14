package org.vliux.orca.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.vliux.orca.R
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

interface OpenAiApiKeyStore {
    fun observeApiKey(): StateFlow<String>

    fun observeAccessState(): StateFlow<OpenAiApiKeyAccessState>

    fun currentApiKey(): String

    suspend fun saveApiKey(activity: FragmentActivity, apiKey: String): OpenAiApiKeyMutationResult

    suspend fun unlockApiKey(activity: FragmentActivity): OpenAiApiKeyMutationResult

    suspend fun clearApiKey(): OpenAiApiKeyMutationResult

    fun hasStoredApiKey(): Boolean
}

enum class OpenAiApiKeyAccessState {
    NOT_STORED,
    LOCKED,
    UNLOCKED,
}

enum class OpenAiApiKeyMutationResult {
    SAVED,
    UNLOCKED,
    CLEARED,
    AUTH_CANCELLED,
    AUTH_UNAVAILABLE,
    ERROR,
}

private enum class AuthenticationResult {
    AUTHENTICATED,
    CANCELLED,
    UNAVAILABLE,
}

@Singleton
class KeystoreOpenAiApiKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : OpenAiApiKeyStore {
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val apiKeyFlow = MutableStateFlow("")
    private val accessStateFlow = MutableStateFlow(
        if (hasStoredCiphertext()) OpenAiApiKeyAccessState.LOCKED else OpenAiApiKeyAccessState.NOT_STORED,
    )

    override fun observeApiKey(): StateFlow<String> = apiKeyFlow.asStateFlow()

    override fun observeAccessState(): StateFlow<OpenAiApiKeyAccessState> = accessStateFlow.asStateFlow()

    override fun currentApiKey(): String = apiKeyFlow.value

    override suspend fun saveApiKey(
        activity: FragmentActivity,
        apiKey: String,
    ): OpenAiApiKeyMutationResult {
        val normalizedKey = apiKey.trim()
        if (normalizedKey.isBlank()) {
            return clearApiKey()
        }
        when (
            authenticate(
                activity = activity,
                title = context.getString(R.string.openai_api_key_save_prompt_title),
                subtitle = context.getString(R.string.openai_api_key_save_prompt_subtitle),
            )
        ) {
            AuthenticationResult.CANCELLED -> return OpenAiApiKeyMutationResult.AUTH_CANCELLED
            AuthenticationResult.UNAVAILABLE -> return OpenAiApiKeyMutationResult.AUTH_UNAVAILABLE
            AuthenticationResult.AUTHENTICATED -> Unit
        }
        return runCatching {
            val cipher = createCipher(Cipher.ENCRYPT_MODE)
            val ciphertext = cipher.doFinal(normalizedKey.toByteArray(Charsets.UTF_8))
            preferences.edit()
                .putString(KEY_OPENAI_API_KEY, ciphertext.encodeBase64())
                .putString(KEY_OPENAI_API_KEY_IV, requireNotNull(cipher.iv).encodeBase64())
                .apply()
            apiKeyFlow.value = normalizedKey
            accessStateFlow.value = OpenAiApiKeyAccessState.UNLOCKED
            OpenAiApiKeyMutationResult.SAVED
        }.getOrElse {
            OpenAiApiKeyMutationResult.ERROR
        }
    }

    override suspend fun unlockApiKey(
        activity: FragmentActivity,
    ): OpenAiApiKeyMutationResult {
        if (!hasStoredCiphertext()) {
            apiKeyFlow.value = ""
            accessStateFlow.value = OpenAiApiKeyAccessState.NOT_STORED
            return OpenAiApiKeyMutationResult.ERROR
        }
        when (
            authenticate(
                activity = activity,
                title = context.getString(R.string.openai_api_key_unlock_prompt_title),
                subtitle = context.getString(R.string.openai_api_key_unlock_prompt_subtitle),
            )
        ) {
            AuthenticationResult.CANCELLED -> return OpenAiApiKeyMutationResult.AUTH_CANCELLED
            AuthenticationResult.UNAVAILABLE -> return OpenAiApiKeyMutationResult.AUTH_UNAVAILABLE
            AuthenticationResult.AUTHENTICATED -> Unit
        }
        return runCatching {
            val iv = decodeBase64(preferences.getString(KEY_OPENAI_API_KEY_IV, "").orEmpty())
            val ciphertext = decodeBase64(preferences.getString(KEY_OPENAI_API_KEY, "").orEmpty())
            val cipher = createCipher(Cipher.DECRYPT_MODE, iv)
            val decrypted = cipher.doFinal(ciphertext).toString(Charsets.UTF_8).trim()
            apiKeyFlow.value = decrypted
            accessStateFlow.value = OpenAiApiKeyAccessState.UNLOCKED
            OpenAiApiKeyMutationResult.UNLOCKED
        }.getOrElse {
            apiKeyFlow.value = ""
            accessStateFlow.value = if (hasStoredCiphertext()) {
                OpenAiApiKeyAccessState.LOCKED
            } else {
                OpenAiApiKeyAccessState.NOT_STORED
            }
            OpenAiApiKeyMutationResult.ERROR
        }
    }

    override suspend fun clearApiKey(): OpenAiApiKeyMutationResult {
        preferences.edit()
            .remove(KEY_OPENAI_API_KEY)
            .remove(KEY_OPENAI_API_KEY_IV)
            .apply()
        apiKeyFlow.value = ""
        accessStateFlow.value = OpenAiApiKeyAccessState.NOT_STORED
        return OpenAiApiKeyMutationResult.CLEARED
    }

    override fun hasStoredApiKey(): Boolean = hasStoredCiphertext()

    private suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
    ): AuthenticationResult {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            accessStateFlow.value = if (hasStoredCiphertext()) {
                OpenAiApiKeyAccessState.LOCKED
            } else {
                OpenAiApiKeyAccessState.NOT_STORED
            }
            return AuthenticationResult.UNAVAILABLE
        }
        return suspendCancellableCoroutine { continuation ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuation.isActive) {
                            continuation.resume(AuthenticationResult.AUTHENTICATED)
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (continuation.isActive) {
                            continuation.resume(AuthenticationResult.CANCELLED)
                        }
                    }
                },
            )
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .build()
            prompt.authenticate(promptInfo)
        }
    }

    private fun createCipher(
        mode: Int,
        iv: ByteArray? = null,
    ): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        if (mode == Cipher.ENCRYPT_MODE) {
            cipher.init(mode, getOrCreateSecretKey())
        } else {
            cipher.init(mode, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, requireNotNull(iv)))
        }
        return cipher
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }
        val keyGenerator = KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
        ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                AUTH_VALIDITY_SECONDS,
                android.security.keystore.KeyProperties.AUTH_BIOMETRIC_STRONG or
                    android.security.keystore.KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
        }
        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    private fun hasStoredCiphertext(): Boolean {
        return !preferences.getString(KEY_OPENAI_API_KEY, "").isNullOrBlank() &&
            !preferences.getString(KEY_OPENAI_API_KEY_IV, "").isNullOrBlank()
    }

    private fun ByteArray.encodeBase64(): String = android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

    private fun decodeBase64(value: String): ByteArray = android.util.Base64.decode(value, android.util.Base64.NO_WRAP)

    private companion object {
        const val PREFERENCES_NAME = "openai_api_key_preferences"
        const val KEY_OPENAI_API_KEY = "openai_api_key_ciphertext"
        const val KEY_OPENAI_API_KEY_IV = "openai_api_key_iv"
        const val KEY_ALIAS = "orca_openai_api_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val AUTH_VALIDITY_SECONDS = 900
    }
}
