package com.example.htv11

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS = "distress_alert"
private const val KEY_CONTACTS = "contacts"
private const val KEY_LAST_SENT = "last_sent"
private const val COOLDOWN_MS = 60_000L

/**
 * A local copy of the user's emergency contacts. The distress alert can fire from a background
 * service or with no internet, where the database can't be read.
 */
object EmergencyContactStore {
    fun save(context: Context, contacts: List<EmergencyContact>) {
        val array = JSONArray()
        contacts.forEach { array.put(JSONObject().put("name", it.name).put("phone", it.phone)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_CONTACTS, array.toString()).apply()
    }

    fun load(context: Context): List<EmergencyContact> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CONTACTS, null)
            ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map {
                val item = array.getJSONObject(it)
                EmergencyContact(name = item.optString("name"), phone = item.optString("phone"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/** What happens when the user signals distress, whether or not anyone else is in the group. */
object DistressAlert {
    /**
     * Silently texts every emergency contact with the user's location. Shows nothing on screen,
     * and does nothing if the SMS permission, a contact or a recent send is missing, since asking
     * for permission here would give the signal away.
     */
    suspend fun send(context: Context, userName: String) {
        val contacts = EmergencyContactStore.load(context).filter { it.phone.isNotBlank() }
        if (contacts.isEmpty() || !hasSmsPermission(context)) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_SENT, 0L) < COOLDOWN_MS) return

        prefs.edit().putLong(KEY_LAST_SENT, now).apply()

        val location = LocationHelper.currentLocation(context) ?: LocationHelper.cachedLocation(context)
        val address = location?.let { LocationHelper.addressFor(context, it) }
        val message = buildDangerMessage(userName, location, address)
        contacts.forEach { sendSms(context, it.phone, message) }
    }
}
