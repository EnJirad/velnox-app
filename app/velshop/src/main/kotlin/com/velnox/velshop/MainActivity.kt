package com.velnox.velshop

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
import com.velnox.velshop.navigation.VelShopNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * The app's only Activity.
 *
 * `singleTask` plus a full `configChanges` list in the manifest means a rotation, a
 * dark/light switch or a keyboard event reconfigures in place instead of recreating
 * the Activity. That is deliberate: it is what keeps scroll position, open sheets and
 * in-flight requests stable across configuration changes, and it removes the most
 * common cause of a duplicated checkout submission.
 *
 * Even so, nothing important is stored in the Activity — every screen's state lives in
 * a `ViewModel`, so a genuine process recreation (which Android can still perform)
 * restores from `SavedStateHandle` or re-reads from the API rather than losing data.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate: it swaps the starting theme once the first
        // frame is ready, which is what stops a splash flash from showing the
        // sign-in screen before the session has been checked.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VelnoxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = VelnoxColors.Background,
                ) {
                    VelShopNavHost(navController = rememberNavController())
                }
            }
        }
    }
}
