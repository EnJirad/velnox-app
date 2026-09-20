package com.velnox.core.common.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * A scope that lives exactly as long as the application process.
 *
 * Only singletons may use it, and only for work that must outlive a screen:
 * session restoration, socket lifetime, cache warming. UI work always belongs to
 * a ViewModel scope so it cannot leak past the screen that started it.
 *
 * [SupervisorJob] keeps one failing child from tearing down unrelated background
 * work (for example, a failed catalogue warm-up must not kill the socket).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object ApplicationScopeModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
