package com.example.htv11

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.htv11.ui.GlassBuildingMapScreen
import com.example.htv11.ui.theme.Htv11Theme
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        enableEdgeToEdge()
        setContent {
            Htv11Theme {
                GlassBuildingMapScreen()
            }
        }
    }
}