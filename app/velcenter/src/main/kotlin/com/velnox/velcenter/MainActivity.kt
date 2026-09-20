package com.velnox.velcenter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.velnox.core.ui.theme.VelnoxColors
import com.velnox.core.ui.theme.VelnoxTheme
import com.velnox.velcenter.navigation.VelCenterNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * The app's only Activity.
 *
 * As in the other two apps, `singleTask` and the full `configChanges` list keep an
 * in-progress moderation decision alive across rotation, and no business state lives
 * here — a ViewModel owns it, so a process recreation re-reads from the API.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VelnoxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = VelnoxColors.Background,
                ) {
                    VelCenterNavHost(navController = rememberNavController())
                }
            }
        }
    }
}
