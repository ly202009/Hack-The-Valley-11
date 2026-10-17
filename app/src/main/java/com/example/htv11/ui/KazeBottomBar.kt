package com.example.htv11.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.example.htv11.R

enum class AppTab { GROUP, MAP }

@Composable
fun KazeBottomBar(selectedTab: AppTab, onTabSelected: (AppTab) -> Unit) {
    NavigationBar(
        containerColor = Color(0xFF020B1E),
        contentColor = Color(0xFF8BB5E2)
    ) {
        NavigationBarItem(
            selected = selectedTab == AppTab.GROUP,
            onClick = { onTabSelected(AppTab.GROUP) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_group),
                    contentDescription = "Group view"
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF00FFCC),
                unselectedIconColor = Color(0xFF8BB5E2),
                indicatorColor = Color(0xFF0A1B33)
            )
        )
        NavigationBarItem(
            selected = selectedTab == AppTab.MAP,
            onClick = { onTabSelected(AppTab.MAP) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_map),
                    contentDescription = "Map view"
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF00FFCC),
                unselectedIconColor = Color(0xFF8BB5E2),
                indicatorColor = Color(0xFF0A1B33)
            )
        )
    }
}
