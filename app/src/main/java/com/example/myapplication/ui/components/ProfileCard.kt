package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun ProfileCard(
    name: String,
    role: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = role,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// Add a Preview so you can see your UI inside Android Studio without running the app!
@Preview(showBackground = true)
@Composable
fun ProfileCardPreview() {
    ProfileCard(
        name = "Alex Developer",
        role = "Android Engineer"
    )
}