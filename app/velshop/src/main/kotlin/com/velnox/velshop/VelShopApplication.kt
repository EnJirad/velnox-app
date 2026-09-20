package com.velnox.velshop

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.velnox.core.logging.VelnoxLog
import com.velnox.core.realtime.RealtimeLifecycleController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * VelShop application entry point.
 *
 * Three things happen here and nowhere else:
 *
 *  1. **Logging is armed before anything can log.** [VelnoxLog] only emits verbose
 *     output in debuggable builds, and every message passes through the redactor, so
 *     no accidental `log("$response")` can leak a session token.
 *  2. **Coil is given the shared image loader.** `ImageLoaderFactory` is the supported
 *     way to set it globally, so no screen can silently create its own loader and lose
 *     the caching policy.
 *  3. **Realtime is wired to the session and the app lifecycle** — connected only
 *     while signed in and in the foreground. It is an optimisation: every screen also
 *     works from the API, so a dropped socket degrades freshness, never correctness.
 */
@HiltAndroidApp
class VelShopApplication : Application(), ImageLoaderFactory {

    @Inject lateinit var imageLoader: ImageLoader

    @Inject lateinit var realtimeLifecycleController: RealtimeLifecycleController

    override fun onCreate() {
        super.onCreate()
        VelnoxLog.initialise(debuggable = BuildConfig.DEBUG)
        realtimeLifecycleController.start()
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}
