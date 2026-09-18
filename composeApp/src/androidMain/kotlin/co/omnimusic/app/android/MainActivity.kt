package co.omnimusic.app.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import co.omnimusic.app.App

/** The Android entry point. All of the interesting code is shared; this is the platform shell. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val environment = androidEnvironment(this)
        setContent {
            App(environment)
        }
    }
}
