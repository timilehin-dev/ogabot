package io.github.timilehin_dev.ogabot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.timilehin_dev.ogabot.ui.AppRoot
import io.github.timilehin_dev.ogabot.ui.OgabotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OgabotTheme {
                AppRoot()
            }
        }
    }
}
