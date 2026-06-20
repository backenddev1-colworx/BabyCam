package com.colworx.babycam.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

val Context.dataStore by preferencesDataStore(name = "babycam_prefs")

enum class Role { NONE, BABY, PARENT }

/** A baby profile the parent has paired with, kept locally (no backend/account). */
data class SavedBaby(
    val room: String,
    val name: String,
    val lastActiveAt: Long,
    /** Persisted so a privacy mute survives leaving and re-entering this baby's live view. */
    val micMuted: Boolean = false,
)

class AppPreferences(private val context: Context) {

    private val roleKey = stringPreferencesKey("role")
    private val crySensKey = floatPreferencesKey("cry_sensitivity")
    private val dataSaverKey = booleanPreferencesKey("data_saver")
    private val parentRoomKey = stringPreferencesKey("parent_room")
    private val babyRoomKey = stringPreferencesKey("baby_room")
    private val savedBabiesKey = stringPreferencesKey("saved_babies")
    private val lastVisitedViewKey = stringPreferencesKey("last_visited_view")
    private val notificationsEnabledKey = booleanPreferencesKey("notifications_enabled")
    private val cryDetectionEnabledKey = booleanPreferencesKey("cry_detection_enabled")
    private val nightModeKey = booleanPreferencesKey("night_mode")
    private val videoQualityKey = stringPreferencesKey("video_quality")
    private val babySetupDoneKey = booleanPreferencesKey("baby_setup_done")

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

    /** Alert notifications (cry / low-battery). OFF by default — the user opts in from Settings. */
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[notificationsEnabledKey] ?: false
    }

    /** Baby cry detection. OFF by default; toggled locally on the baby or remotely by the parent. */
    val cryDetectionEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[cryDetectionEnabledKey] ?: false
    }

    /** Parent's night-mode (green low-light filter) preference, persisted across sessions. */
    val nightMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[nightModeKey] ?: false
    }

    /** Baby capture quality: "HIGH" (1280x720@30) or "SAVER" (640x480@15). Default HIGH. */
    val videoQuality: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[videoQualityKey] ?: "HIGH"
    }

    /**
     * True once the baby device has finished first-time setup (chosen role + reached the monitor).
     * Used so an already-set-up baby reopens straight to the monitor/status screen instead of
     * being walked through welcome/permissions/pairing again.
     */
    val babySetupDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[babySetupDoneKey] ?: false
    }

    /** The currently-active baby room for the parent (last one viewed), or null if none. */
    val parentRoom: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[parentRoomKey]
    }

    /** All babies the parent has ever paired with, most-recently-active first. */
    val savedBabies: Flow<List<SavedBaby>> = context.dataStore.data.map { prefs ->
        parseBabies(prefs[savedBabiesKey]).sortedByDescending { it.lastActiveAt }
    }

    /**
     * Which top-level screen the user was last on ("baby_active" / "parent_live"), so a hard
     * kill + relaunch can resume there instead of resetting to the welcome screen. Null means
     * no active session worth resuming (e.g. the user explicitly disconnected/stopped).
     */
    val lastVisitedView: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[lastVisitedViewKey]
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

    suspend fun setNotificationsEnabled(on: Boolean) {
        context.dataStore.edit { it[notificationsEnabledKey] = on }
    }

    suspend fun setCryDetectionEnabled(on: Boolean) {
        context.dataStore.edit { it[cryDetectionEnabledKey] = on }
    }

    suspend fun setNightMode(on: Boolean) {
        context.dataStore.edit { it[nightModeKey] = on }
    }

    suspend fun setVideoQuality(quality: String) {
        context.dataStore.edit { it[videoQualityKey] = quality }
    }

    suspend fun setBabySetupDone(done: Boolean) {
        context.dataStore.edit { it[babySetupDoneKey] = done }
    }

    /** Remembers [room] as the parent's currently-active baby for one-tap reconnect. */
    suspend fun setParentRoom(room: String) {
        context.dataStore.edit { it[parentRoomKey] = room }
    }

    /** Forgets the remembered active-baby room (used when no babies are saved anymore). */
    suspend fun clearParentRoom() {
        context.dataStore.edit { it.remove(parentRoomKey) }
    }

    suspend fun setLastVisitedView(view: String?) {
        context.dataStore.edit { prefs ->
            if (view == null) prefs.remove(lastVisitedViewKey) else prefs[lastVisitedViewKey] = view
        }
    }

    /** Adds a new saved baby (or refreshes [name] + activity time if [room] is already saved). */
    suspend fun upsertBaby(room: String, name: String, activeNow: Long) {
        context.dataStore.edit { prefs ->
            val babies = parseBabies(prefs[savedBabiesKey]).toMutableList()
            val idx = babies.indexOfFirst { it.room == room }
            if (idx >= 0) {
                // Preserve micMuted (and any other future per-baby settings) for an existing entry.
                babies[idx] = babies[idx].copy(name = name, lastActiveAt = activeNow)
            } else {
                babies.add(SavedBaby(room = room, name = name, lastActiveAt = activeNow))
            }
            prefs[savedBabiesKey] = serializeBabies(babies)
        }
    }

    /** Persists the privacy-mute toggle for [room] so it survives leaving/re-entering live view. */
    suspend fun setBabyMicMuted(room: String, muted: Boolean) {
        context.dataStore.edit { prefs ->
            val babies = parseBabies(prefs[savedBabiesKey]).toMutableList()
            val idx = babies.indexOfFirst { it.room == room }
            if (idx >= 0) {
                babies[idx] = babies[idx].copy(micMuted = muted)
                prefs[savedBabiesKey] = serializeBabies(babies)
            }
        }
    }

    /** Bumps [room]'s last-active timestamp without changing its name. */
    suspend fun touchBaby(room: String, activeNow: Long) {
        context.dataStore.edit { prefs ->
            val babies = parseBabies(prefs[savedBabiesKey]).toMutableList()
            val idx = babies.indexOfFirst { it.room == room }
            if (idx >= 0) {
                babies[idx] = babies[idx].copy(lastActiveAt = activeNow)
                prefs[savedBabiesKey] = serializeBabies(babies)
            }
        }
    }

    /** Renames a saved baby without touching its activity timestamp. */
    suspend fun renameBaby(room: String, newName: String) {
        context.dataStore.edit { prefs ->
            val babies = parseBabies(prefs[savedBabiesKey]).toMutableList()
            val idx = babies.indexOfFirst { it.room == room }
            if (idx >= 0) {
                babies[idx] = babies[idx].copy(name = newName)
                prefs[savedBabiesKey] = serializeBabies(babies)
            }
        }
    }

    /**
     * One-time migration for installs that paired before multi-baby support existed: if a
     * legacy [parentRoomKey] is set but isn't in the saved-babies list yet, adopt it as "Baby 1"
     * so existing users aren't asked to re-enter their pairing code.
     */
    suspend fun migrateLegacyParentRoomIfNeeded() {
        val data = context.dataStore.data.first()
        val legacyRoom = data[parentRoomKey] ?: return
        val existing = parseBabies(data[savedBabiesKey])
        if (existing.none { it.room == legacyRoom }) {
            upsertBaby(legacyRoom, "Baby 1", System.currentTimeMillis())
        }
    }

    /** Removes [room] from the saved-babies list. */
    suspend fun removeBaby(room: String) {
        context.dataStore.edit { prefs ->
            val babies = parseBabies(prefs[savedBabiesKey]).filterNot { it.room == room }
            prefs[savedBabiesKey] = serializeBabies(babies)
            if (prefs[parentRoomKey] == room) prefs.remove(parentRoomKey)
        }
    }

    private fun parseBabies(json: String?): List<SavedBaby> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SavedBaby(
                    room = o.getString("room"),
                    name = o.getString("name"),
                    lastActiveAt = o.optLong("lastActiveAt", 0L),
                    micMuted = o.optBoolean("micMuted", false),
                )
            }
        } catch (_: Exception) {
            // Corrupt/old-format data — treat as no saved babies rather than crash.
            emptyList()
        }
    }

    private fun serializeBabies(babies: List<SavedBaby>): String {
        val arr = JSONArray()
        babies.forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("room", b.room)
                    put("name", b.name)
                    put("lastActiveAt", b.lastActiveAt)
                    put("micMuted", b.micMuted)
                }
            )
        }
        return arr.toString()
    }

    /**
     * Returns the persisted baby room, generating and saving a fresh one on first use
     * so the baby keeps the same pairing token across restarts.
     */
    suspend fun babyRoomOnce(): String {
        val existing = context.dataStore.data.first()[babyRoomKey]
        if (existing != null) return existing
        val generated = com.colworx.babycam.signaling.RoomToken.generate()
        context.dataStore.edit { it[babyRoomKey] = generated }
        return generated
    }
}
