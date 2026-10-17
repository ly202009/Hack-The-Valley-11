package com.example.htv11

/**
 * Detector for volume button duress pattern.
 * Pattern rules:
 * - Up once or twice, Down once or twice, repeated 3 times.
 * - OR alternatively Down once or twice, Up once or twice, repeated 3 times.
 * - If the user presses up or down 3 times in a row rapidly, both counts reset.
 * - Adjustable reset timer between presses (resetTimeoutMillis).
 * - Exposes live state (up/down counts, block progress) via callback for live UI display.
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

    enum class PatternType {
        UP_DOWN, DOWN_UP
    }

    data class DetectorState(
        val patternType: PatternType? = null,
        val currentBlock: Int = 0,       // 0, 1, 2
        val currentHalf: Int = 0,        // 0 = first half, 1 = second half
        val upCountInSequence: Int = 0,
        val downCountInSequence: Int = 0,
        val lastPress: VolumePress? = null,
        val isTriggered: Boolean = false
    )

    private var lastPressTime: Long = 0L
    private var lastDirection: VolumePress? = null
    private var consecutiveSameCount: Int = 0

    private var patternType: PatternType? = null
    private var currentBlock: Int = 0
    private var currentHalf: Int = 0
    private var currentHalfPressCount: Int = 0
    private var detectedHalfPressCount: Int? = null

    private var upCountInSequence: Int = 0
    private var downCountInSequence: Int = 0

    private fun updateState(lastPress: VolumePress?, triggered: Boolean = false) {
        val state = DetectorState(
            patternType = patternType,
            currentBlock = currentBlock,
            currentHalf = currentHalf,
            upCountInSequence = upCountInSequence,
            downCountInSequence = downCountInSequence,
            lastPress = lastPress,
            isTriggered = triggered
        )
        onStateChanged(state)
    }

    fun onVolumePress(press: VolumePress): Boolean {
        val currentTime = timeProvider()

        if (press == VolumePress.UP) {
            upCountInSequence++
        } else {
            downCountInSequence++
        }

        if (lastPressTime != 0L && (currentTime - lastPressTime > resetTimeoutMillis)) {
            logger(TAG, "Reset: Timeout exceeded (${currentTime - lastPressTime}ms > ${resetTimeoutMillis}ms)")
            resetState()
        }
        lastPressTime = currentTime

        if (lastDirection == press) {
            consecutiveSameCount++
        } else {
            consecutiveSameCount = 1
            lastDirection = press
        }

        if (consecutiveSameCount >= 3) {
            logger(TAG, "Reset: 3 consecutive presses of $press in a row")
            resetState()
            updateState(press)
            return false
        }

        if (patternType == null) {
            patternType = if (press == VolumePress.UP) PatternType.UP_DOWN else PatternType.DOWN_UP
            currentBlock = 0
            currentHalf = 0
            currentHalfPressCount = 1
            logger(TAG, "Pattern initialized: $patternType, Block 0, Half 0, press $press (count=1)")
            updateState(press)
            return false
        }

        val firstHalfDir = if (patternType == PatternType.UP_DOWN) VolumePress.UP else VolumePress.DOWN
        val secondHalfDir = if (patternType == PatternType.UP_DOWN) VolumePress.DOWN else VolumePress.UP

        val targetDir = if (currentHalf == 0) firstHalfDir else secondHalfDir

        if (press == targetDir) {
            currentHalfPressCount++
            if (currentHalfPressCount > 2) {
                logger(TAG, "Reset: Half exceeded 2 presses")
                resetState()
                updateState(press)
                return false
            }
            logger(TAG, "Continued Block $currentBlock Half $currentHalf with $press (count=$currentHalfPressCount)")

            if (currentBlock == 2 && currentHalf == 1) {
                val targetCount = detectedHalfPressCount ?: 1
                if (currentHalfPressCount == targetCount) {
                    triggerDuress(press)
                    return true
                }
            }
        } else {
            if (detectedHalfPressCount == null) {
                detectedHalfPressCount = currentHalfPressCount
                logger(TAG, "Locked half press count to: $detectedHalfPressCount")
            } else if (currentHalfPressCount != detectedHalfPressCount) {
                logger(TAG, "Reset: Inconsistent press count ($currentHalfPressCount vs $detectedHalfPressCount)")
                resetState()
                updateState(press)
                return false
            }

            if (currentHalf == 0) {
                if (press != secondHalfDir) {
                    logger(TAG, "Reset: Unexpected direction $press, expected $secondHalfDir")
                    resetState()
                    updateState(press)
                    return false
                }
                currentHalf = 1
                currentHalfPressCount = 1
                logger(TAG, "Transitioned to Block $currentBlock Second Half ($press, count=1)")
            } else {
                if (press != firstHalfDir) {
                    logger(TAG, "Reset: Unexpected direction $press when starting next block, expected $firstHalfDir")
                    resetState()
                    updateState(press)
                    return false
                }
                currentBlock++
                if (currentBlock >= 3) {
                    triggerDuress(press)
                    return true
                }
                currentHalf = 0
                currentHalfPressCount = 1
                logger(TAG, "Transitioned to Block $currentBlock First Half ($press, count=1)")
            }
        }

        if (currentBlock == 2 && currentHalf == 1 && currentHalfPressCount == 1 && detectedHalfPressCount == 1) {
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
        lastDirection = null
        consecutiveSameCount = 0
        patternType = null
        currentBlock = 0
        currentHalf = 0
        currentHalfPressCount = 0
        detectedHalfPressCount = null
        upCountInSequence = 0
        downCountInSequence = 0
        updateState(null)
    }

    companion object {
        private const val TAG = "VolumeDuressDetector"
    }
}
