package com.velnox.velseller

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
import com.velnox.velseller.navigation.VelSellerNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * The app's only Activity.
 *
 * `singleTask` plus the full `configChanges` list keeps scroll position, open dialogs
 * and in-flight requests stable across rotation and configuration changes. Nothing
 * important lives in the Activity — every screen's state is in a ViewModel, so a real
 * process recreation re-reads from the API instead of losing data.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate: it holds the splash until the first frame, so
        // a signed-in seller never sees a sign-in form flash past.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VelnoxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = VelnoxColors.Background,
                ) {
                    VelSellerNavHost(navController = rememberNavController())
                }
            }
        }
    }
}
