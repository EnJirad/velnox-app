package com.velnox.velseller

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.velnox.core.logging.VelnoxLog
import com.velnox.core.realtime.RealtimeLifecycleController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Velseller application entry point.
 *
 * The same three responsibilities as the other two apps, in the same order: arm the
 * redacting logger before anything can log, install the shared Coil loader, and wire
 * realtime to the session and the app lifecycle. Realtime stays an optimisation — the
 * seller workspace works entirely from the API, so a dropped socket costs freshness,
 * never correctness.
 */
@HiltAndroidApp
class VelsellerApplication : Application(), ImageLoaderFactory {

    @Inject lateinit var imageLoader: ImageLoader

    @Inject lateinit var realtimeLifecycleController: RealtimeLifecycleController

    override fun onCreate() {
        super.onCreate()
        VelnoxLog.initialise(debuggable = BuildConfig.DEBUG)
        realtimeLifecycleController.start()
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}
