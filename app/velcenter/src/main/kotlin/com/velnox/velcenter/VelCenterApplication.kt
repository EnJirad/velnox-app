package com.velnox.velcenter

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.velnox.core.logging.VelnoxLog
import com.velnox.core.realtime.RealtimeLifecycleController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * VelCenter application entry point.
 *
 * The same wiring as the other two apps: the redacting logger first (a moderation queue
 * shows other people's data, so verbose logging must never be the default in a release
 * build), the shared Coil loader, and realtime bound to the session and the foreground.
 *
 * Realtime here is a freshness optimisation for the dashboard and the queues — every
 * screen also works from the API alone.
 */
@HiltAndroidApp
class VelCenterApplication : Application(), ImageLoaderFactory {

    @Inject lateinit var imageLoader: ImageLoader

    @Inject lateinit var realtimeLifecycleController: RealtimeLifecycleController

    override fun onCreate() {
        super.onCreate()
        VelnoxLog.initialise(debuggable = BuildConfig.DEBUG)
        realtimeLifecycleController.start()
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}
