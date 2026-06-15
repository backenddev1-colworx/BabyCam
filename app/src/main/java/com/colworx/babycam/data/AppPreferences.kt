package com.colworx.babycam.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "babycam_prefs")

enum class Role { NONE, BABY, PARENT }

class AppPreferences(private val context: Context) {

    private val roleKey = stringPreferencesKey("role")
    private val crySensKey = floatPreferencesKey("cry_sensitivity")
    private val dataSaverKey = booleanPreferencesKey("data_saver")

    val role: Flow<Role> = context.dataStore.data.map { prefs ->
        when (prefs[roleKey]) {
            "BABY" -> Role.BABY
            "PARENT" -> Role.PARENT
            else -> Role.NONE
        }
    }

    /** 0.0 = low sensitivity … 1.0 = high sensitivity. Default 0.55. */
    val crySensitivity: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[crySensKey] ?: 0.55f
    }

    val dataSaver: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[dataSaverKey] ?: false
    }

    suspend fun setRole(role: Role) {
        context.dataStore.edit { it[roleKey] = role.name }
    }

    suspend fun setCrySensitivity(value: Float) {
        context.dataStore.edit { it[crySensKey] = value }
    }

    suspend fun setDataSaver(on: Boolean) {
        context.dataStore.edit { it[dataSaverKey] = on }
    }
}
