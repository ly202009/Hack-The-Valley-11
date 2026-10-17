package com.example.myapplication

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.components.ProfileCard
import android.widget.Toast
import android.os.SystemClock


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ProfileCard(
                        name = "Android",
                        role = "Software Developer",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    val distressed = false
    val silentAlarmResetTime = 1.0f;
//    We will be tracking the time between each keyup and keydown event,
//    if the user spams up and down more than 3 times each, then we are distressed

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when(keyCode){
            KeyEvent.KEYCODE_VOLUME_UP -> {
                Toast.makeText(this, "Volume Up Pressed", Toast.LENGTH_SHORT).show()
                // Return true if you want to consume/block the default system volume change
                return true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                Toast.makeText(this, "Volume Down Pressed", Toast.LENGTH_SHORT).show()
                // Return true if you want to consume/block the default system volume change
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Android")
    }
}