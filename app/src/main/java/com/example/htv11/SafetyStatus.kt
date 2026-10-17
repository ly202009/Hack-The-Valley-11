package com.example.htv11

import androidx.compose.ui.graphics.Color

/** How a member is doing, as seen by the rest of the group. Stored in the database by [key]. */
enum class SafetyStatus(val key: String, val label: String, val color: Color) {
    UNSURE("unsure", "Unsure", Color(0xFFFFC107)),
    SAFE("safe", "Safe", Color(0xFF00FFCC)),
    DANGER("danger", "In danger", Color(0xFFFF5252));

    companion object {
        /** Missing or unrecognised values count as safe, matching a member who has never been nudged. */
        fun fromKey(key: String?): SafetyStatus =
            entries.firstOrNull { it.key == key?.lowercase() } ?: SAFE
    }
}
