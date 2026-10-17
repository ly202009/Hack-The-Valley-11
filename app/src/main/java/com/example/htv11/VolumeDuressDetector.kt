package com.example.htv11

/**
 * Detector for volume button duress pattern.
 * Pattern rules:
 * - When volume up and volume down are pressed rapidly within [resetTimeoutMillis], counts increment.
 * - If volume up is pressed 3 times in a row OR volume down is pressed 3 times in a row, counters reset.
 * - Otherwise, counts accumulate until upCount >= 3 AND downCount >= 3.
 * - When both upCount >= 3 and downCount >= 3, duress alarm is triggered.
 */
class VolumeDuressDetector(
    var resetTimeoutMillis: Long = 2000L,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val logger: (String, String) -> Unit = { tag, msg ->
        try {
            android.util.Log.d(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    },
    private val onStateChanged: (DetectorState) -> Unit = {},
    private val onDuressDetected: () -> Unit
) {
    enum class VolumePress {
        UP, DOWN
    }

    data class DetectorState(
        val upCountInSequence: Int = 0,
        val downCountInSequence: Int = 0,
        val consecutiveUp: Int = 0,
        val consecutiveDown: Int = 0,
        val lastPress: VolumePress? = null,
        val isTriggered: Boolean = false
    )

    private var lastPressTime: Long = 0L
    private var upCount: Int = 0
    private var downCount: Int = 0
    private var consecutiveUp: Int = 0
    private var consecutiveDown: Int = 0

    private fun updateState(lastPress: VolumePress?, triggered: Boolean = false) {
        val state = DetectorState(
            upCountInSequence = upCount,
            downCountInSequence = downCount,
            consecutiveUp = consecutiveUp,
            consecutiveDown = consecutiveDown,
            lastPress = lastPress,
            isTriggered = triggered
        )
        onStateChanged(state)
    }

    fun onVolumePress(press: VolumePress): Boolean {
        val currentTime = timeProvider()

        // Debounce rapid duplicate key events (less than 50ms apart)
        if (lastPressTime != 0L && (currentTime - lastPressTime < 50L)) {
            logger(TAG, "Ignored duplicate press within 50ms debounce window")
            return false
        }

        // 1. Check timeout between presses
        if (lastPressTime != 0L && (currentTime - lastPressTime > resetTimeoutMillis)) {
            logger(TAG, "Reset: Timeout exceeded (${currentTime - lastPressTime}ms > ${resetTimeoutMillis}ms)")
            resetState()
        }
        lastPressTime = currentTime

        // 2. Process press direction and consecutive counts
        if (press == VolumePress.UP) {
            upCount++
            consecutiveUp++
            consecutiveDown = 0
        } else {
            downCount++
            consecutiveDown++
            consecutiveUp = 0
        }

        logger(TAG, "Press: $press | Up: $upCount (consec: $consecutiveUp) | Down: $downCount (consec: $consecutiveDown)")

        // 3. Reset if 3 in a row of the same button
        if (consecutiveUp >= 3 || consecutiveDown >= 3) {
            logger(TAG, "Reset: 3 consecutive presses of $press in a row")
            resetState()
            updateState(press)
            return false
        }

        // 4. Check if duress condition met (both upCount >= 3 and downCount >= 3)
        if (upCount >= 3 && downCount >= 3) {
            triggerDuress(press)
            return true
        }

        updateState(press)
        return false
    }

    private fun triggerDuress(lastPress: VolumePress) {
        logger(TAG, "=== DURESS ALARM TRIGGERED! ===")
        updateState(lastPress, triggered = true)
        onDuressDetected()
        resetState()
    }

    fun resetState() {
        lastPressTime = 0L
        upCount = 0
        downCount = 0
        consecutiveUp = 0
        consecutiveDown = 0
        updateState(null)
    }

    companion object {
        private const val TAG = "VolumeDuressDetector"
    }
}
