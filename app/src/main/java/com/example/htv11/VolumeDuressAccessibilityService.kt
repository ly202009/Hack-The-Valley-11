package com.example.htv11

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VolumeDuressAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VolumeDuressAccessibilityService created")

        VolumeDuressManager.onDuressTriggered = {
            handleDuressTriggered()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for key event filtering
    }

    override fun onInterrupt() {
        Log.d(TAG, "VolumeDuressAccessibilityService interrupted")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    Log.d(TAG, "Volume Up pressed")
                    VolumeDuressManager.onVolumePress(VolumeDuressDetector.VolumePress.UP)
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    Log.d(TAG, "Volume Down pressed")
                    VolumeDuressManager.onVolumePress(VolumeDuressDetector.VolumePress.DOWN)
                }
            }
        }
        return super.onKeyEvent(event)
    }

    private fun handleDuressTriggered() {
        Log.w(TAG, "SILENT DURESS ALARM ACTIVATED IN BACKGROUND SERVICE!")

        try {
            val prefs = applicationContext.getSharedPreferences("htv11_prefs", Context.MODE_PRIVATE)
            val currentUserId = AuthManager.currentUser?.uid ?: prefs.getString("current_user_id", null)
            // Blank (not "DEFAULT_SQUAD") means genuinely no active group — solo mode.
            val currentGroupId = prefs.getString("current_group_id", "") ?: ""

            if (currentUserId != null) {
                val userName = AuthManager.currentUser?.displayName?.ifBlank { null } ?: "Someone"

                if (currentGroupId.isNotBlank()) {
                    // Group mode: flip status so the squad sees it, on top of alerting contacts.
                    UserRepository.setStatus(currentUserId, SafetyStatus.DANGER)
                    Log.d(TAG, "Group mode: updated status to danger for user $currentUserId in group $currentGroupId")
                } else {
                    // Solo mode: no group to see a status flip, so skip it — just alert contacts.
                    Log.d(TAG, "Solo mode: no active group, skipping status flip")
                }

                scope.launch { DistressAlert.send(applicationContext, userName) }
            } else {
                Log.w(TAG, "No user ID found when duress triggered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle duress trigger", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        Log.d(TAG, "VolumeDuressAccessibilityService destroyed")
    }

    companion object {
        private const val TAG = "VolumeDuressSvc"
    }
}
