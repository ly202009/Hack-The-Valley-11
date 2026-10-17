package com.example.htv11

import android.content.Context

enum class PinResult { NORMAL, DURESS, WRONG }

/** The fixed PINs for the check-in, plus which nudge the user last answered. */
object PinStore {
    const val PIN_LENGTH = 4

    private const val NORMAL_PIN = "1234"
    private const val DURESS_PIN = "6767"

    private const val PREFS = "pin_store"
    private const val KEY_HANDLED_NUDGE = "handled_nudge"

    fun check(pin: String): PinResult = when (pin) {
        NORMAL_PIN -> PinResult.NORMAL
        DURESS_PIN -> PinResult.DURESS
        else -> PinResult.WRONG
    }

    /** The newest nudge the user has already answered, so a nudge is only asked about once. */
    fun lastHandledNudge(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_HANDLED_NUDGE, 0L)

    fun setLastHandledNudge(context: Context, nudgedAt: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_HANDLED_NUDGE, nudgedAt).apply()
    }
}
