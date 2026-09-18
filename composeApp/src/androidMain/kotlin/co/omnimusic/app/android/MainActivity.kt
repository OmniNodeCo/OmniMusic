package co.omnimusic.app.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import co.omnimusic.app.App
import co.omnimusic.app.AppModel

/** The Android entry point. All of the interesting code is shared; this is the platform shell. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val environment = androidEnvironment(this)
        val model = AppModel(environment)

        // System back closes the now-playing panel and then walks back through detail screens,
        // instead of leaving the app from wherever the user happened to be.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!model.navigateBack()) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )

        setContent {
            App(environment, model)
        }
    }
}
