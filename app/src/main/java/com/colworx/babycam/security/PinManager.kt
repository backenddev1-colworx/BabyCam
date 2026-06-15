package com.colworx.babycam.security

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.colworx.babycam.data.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

/**
 * Stores and verifies the app-lock PIN.
 *
 * The PIN is never persisted in plain text — only its SHA-256 hash is stored
 * in the shared DataStore.
 */
class PinManager(private val context: Context) {

    private val pinHashKey = stringPreferencesKey("app_lock_pin_hash")
    private val enabledKey = booleanPreferencesKey("app_lock_enabled")

    /** Returns the lowercase hex SHA-256 hash of [pin]. */
    fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Stores the hashed [pin] and enables the app lock. */
    suspend fun setPin(pin: String) {
        val hashed = hash(pin)
        context.dataStore.edit { prefs ->
            prefs[pinHashKey] = hashed
            prefs[enabledKey] = true
        }
    }

    /** Returns true if [pin] matches the stored hash. */
    suspend fun verify(pin: String): Boolean {
        val stored = context.dataStore.data.first()[pinHashKey] ?: return false
        return stored == hash(pin)
    }

    /** Emits whether the app lock is currently enabled. */
    val isEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[enabledKey] ?: false
    }

    /** Clears the stored PIN and disables the app lock. */
    suspend fun disable() {
        context.dataStore.edit { prefs ->
            prefs.remove(pinHashKey)
            prefs[enabledKey] = false
        }
    }
}
