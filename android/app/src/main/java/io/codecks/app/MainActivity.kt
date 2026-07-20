package io.codecks.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.codecks.app.ui.CodecksApp
import io.codecks.core.designsystem.CodecksTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CodecksTheme {
                CodecksApp()
            }
        }
    }
}

