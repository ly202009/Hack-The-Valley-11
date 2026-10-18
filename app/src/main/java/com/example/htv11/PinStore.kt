package com.example.htv11

enum class PinResult { NORMAL, DURESS, WRONG }

/** The fixed PINs for the check-in dialog. */
object PinStore {
    const val PIN_LENGTH = 4

    private const val NORMAL_PIN = "1234"
    private const val DURESS_PIN = "6767"

    fun check(pin: String): PinResult = when (pin) {
        NORMAL_PIN -> PinResult.NORMAL
        DURESS_PIN -> PinResult.DURESS
        else -> PinResult.WRONG
    }
}
