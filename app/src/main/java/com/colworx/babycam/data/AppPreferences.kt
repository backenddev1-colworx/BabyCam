package com.colworx.babycam.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "babycam_prefs")

enum class Role { NONE, BABY, PARENT }

/** Lightweight wrapper over DataStore for app-level preferences. */
class AppPreferences(private val context: Context) {

    private val roleKey = stringPreferencesKey("role")

    val role: Flow<Role> = context.dataStore.data.map { prefs ->
        when (prefs[roleKey]) {
            "BABY" -> Role.BABY
            "PARENT" -> Role.PARENT
            else -> Role.NONE
        }
    }

    suspend fun setRole(role: Role) {
        context.dataStore.edit { it[roleKey] = role.name }
    }
}
