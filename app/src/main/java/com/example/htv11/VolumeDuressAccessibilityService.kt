package com.example.htv11

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat

class VolumeDuressAccessibilityService : AccessibilityService() {

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
        if (event.action == KeyEvent.ACTION_DOWN) {
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
        Log.w(TAG, "DURESS ALARM ACTIVATED IN BACKGROUND SERVICE!")

        // 1. Send system notification
        sendDuressNotification()

        // 2. Vibrate device safely
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? Vibrator
            }

            val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 500), -1)
            vibrator?.vibrate(effect)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vibrate", e)
        }

        // 3. Show Toast notification
        try {
            Toast.makeText(applicationContext, "🚨 DURESS EMERGENCY TRIGGERED! 🚨", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show toast", e)
        }

        // 4. Launch Emergency / MainActivity
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("EMERGENCY_TRIGGERED", true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start emergency activity", e)
        }
    }

    private fun sendDuressNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "duress_alert_channel"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Duress Emergency Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when emergency volume duress pattern is triggered"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EMERGENCY_TRIGGERED", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🚨 EMERGENCY DURESS TRIGGERED! 🚨")
                .setContentText("Volume button duress pattern detected in background!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(1001, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send notification", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "VolumeDuressAccessibilityService destroyed")
    }

    companion object {
        private const val TAG = "VolumeDuressSvc"
    }
}
