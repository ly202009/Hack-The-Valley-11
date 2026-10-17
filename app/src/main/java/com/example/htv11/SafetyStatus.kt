package com.example.htv11

import androidx.compose.ui.graphics.Color

/** How a member is doing, as seen by the rest of the group. Stored in the database by [key]. */
enum class SafetyStatus(val key: String, val label: String, val color: Color) {
    UNSURE("unsure", "Unsure", Color(0xFFFFB020)),
    SAFE("safe", "Safe", Color(0xFF3DDC84)),
    DANGER("danger", "In danger", Color(0xFFFF3B30));

    companion object {
        fun fromKey(key: String?): SafetyStatus = entries.firstOrNull { it.key == key } ?: UNSURE
    }
}
