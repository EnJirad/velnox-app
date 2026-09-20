package com.velnox.core.network.di

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.velnox.core.network.BuildConfig
import com.velnox.core.network.auth.AuthTokenProvider
import com.velnox.core.network.interceptor.AuthHeaderInterceptor
import com.velnox.core.network.interceptor.ClientHeadersInterceptor
import com.velnox.core.network.interceptor.RetryInterceptor
import com.velnox.core.network.serialization.VelnoxJson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * The project's single Retrofit/OkHttp graph.
 *
 * Timeouts are explicit rather than OkHttp's 10-second default because Neon
 * cold starts on the free tier regularly exceed it: a 15 s connect and 30 s read
 * budget is what the web clients effectively tolerate through browser defaults,
 * and it avoids turning a slow-but-successful cold start into an error screen.
 *
 * The `CookieJar` is the projection of the Velnox session into the
 * `velnox_session` cookie the backend actually reads — see
 * `core:auth`'s `VelnoxSessionCookieJar` for why that is required rather than
 * merely convenient.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 30L
    private const val CALL_TIMEOUT_SECONDS = 60L

    /** Presigned R2 PUTs transfer real image bytes and need a longer budget. */
    private const val UPLOAD_WRITE_TIMEOUT_SECONDS = 120L

    @Provides
    @Singleton
    fun provideJson(): Json = VelnoxJson

    @Provides
    @Singleton
    @VelnoxBaseUrl
    fun provideBaseUrl(): HttpUrl {
        val configured = BuildConfig.VELNOX_API_BASE_URL.trim()
        val origin = if (configured.isEmpty()) DEFAULT_ORIGIN else configured
        val apiUrl = origin.trimEnd('/') + "/api/"
        return apiUrl.toHttpUrlOrNull()
            ?: error("velnox.api.baseUrl is not a valid URL: \"$origin\"")
    }

    @Provides
    @Singleton
    @VelnoxApiOrigin
    fun provideApiOrigin(@VelnoxBaseUrl baseUrl: HttpUrl): HttpUrl = baseUrl.newBuilder()
        .encodedPath("/")
        .build()

    @Provides
    @Singleton
    @VelnoxHttpClient
    fun provideVelnoxClient(
        tokenProvider: AuthTokenProvider,
        clientHeadersInterceptor: ClientHeadersInterceptor,
        cookieJar: CookieJar,
    ): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false) // RetryInterceptor owns retrying, with rules.
        .addInterceptor(clientHeadersInterceptor)
        .addInterceptor(AuthHeaderInterceptor(tokenProvider))
        .addInterceptor(RetryInterceptor())
        .build()

    @Provides
    @Singleton
    @PlainHttpClient
    fun providePlainClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(UPLOAD_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideClientHeadersInterceptor(@ApplicationContext context: Context): ClientHeadersInterceptor =
        ClientHeadersInterceptor(
            appId = context.packageName.substringAfterLast('.'),
            clientVersion = context.appVersionName(),
            localeProvider = { Locale.getDefault().let { "${it.language},${it.country.lowercase()};q=0.8,en;q=0.7" } },
        )

    @Provides
    @Singleton
    @VelnoxRetrofit
    fun provideRetrofit(
        @VelnoxBaseUrl baseUrl: HttpUrl,
        @VelnoxHttpClient client: OkHttpClient,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    const val DEFAULT_ORIGIN = "https://velnox-api.onrender.com"
}

@Suppress("DEPRECATION")
private fun Context.appVersionName(): String {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        packageManager.getPackageInfo(packageName, 0)
    }
    return info.versionName ?: "0"
}
