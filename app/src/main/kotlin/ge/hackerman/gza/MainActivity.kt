package ge.hackerman.gza

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import ge.hackerman.gza.ui.GzaApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GzaTheme {
                GzaApp()
            }
        }
    }
}
