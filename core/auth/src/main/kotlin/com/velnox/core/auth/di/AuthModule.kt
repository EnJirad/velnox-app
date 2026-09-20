package com.velnox.core.auth.di

import com.velnox.core.auth.api.VelnoxAuthApi
import com.velnox.core.auth.session.SessionManager
import com.velnox.core.auth.session.VelnoxSessionCookieJar
import com.velnox.core.network.auth.AuthTokenProvider
import com.velnox.core.network.di.VelnoxRetrofit
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.CookieJar
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthApiModule {

    @Provides
    @Singleton
    fun provideAuthApi(@VelnoxRetrofit retrofit: Retrofit): VelnoxAuthApi = retrofit.create()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindingsModule {

    /**
     * The network layer sees only the interface, so `core:network` never depends on
     * `core:auth` and the token has exactly one owner.
     */
    @Binds
    @Singleton
    abstract fun bindAuthTokenProvider(sessionManager: SessionManager): AuthTokenProvider

    /**
     * Presenting the session as the `velnox_session` cookie is what makes the native
     * client work against the unmodified backend (see [VelnoxSessionCookieJar]).
     */
    @Binds
    @Singleton
    abstract fun bindCookieJar(jar: VelnoxSessionCookieJar): CookieJar
}
