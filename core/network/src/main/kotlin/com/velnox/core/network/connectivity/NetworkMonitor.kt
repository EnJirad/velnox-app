package com.velnox.core.network.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device connectivity as a hot, always-current signal.
 *
 * This reports *reachability*, not *reachability of the Velnox backend*. Screens
 * use it for one purpose: to distinguish "you are offline" from "the server
 * refused the request", so the offline banner and the retry affordance stay
 * honest. It never short-circuits a request — a captive portal reports as
 * connected, and only the real call can tell the truth — and it never authorises
 * showing cached data as if it were fresh.
 *
 * `NET_CAPABILITY_VALIDATED` is required, which filters out the very common
 * "connected to Wi-Fi with no internet" case that would otherwise surface as a
 * misleading server error.
 *
 * `registerDefaultNetworkCallback` is available from API 24, which is this
 * project's `minSdk`, so no legacy branch is needed.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val connectivityManager: ConnectivityManager? =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(currentlyOnline())

    /** Current connectivity. Reading it synchronously yields a truthful value. */
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        connectivityManager?.let { manager ->
            runCatching {
                manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) = refresh()
                    override fun onLost(network: Network) = refresh()
                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
                })
            }
        }
    }

    /** Observes connectivity changes, starting with the current value. */
    fun observe(): Flow<Boolean> = isOnline

    /** `true` only when there is a validated internet-capable network. */
    private fun currentlyOnline(): Boolean {
        val manager = connectivityManager ?: return true
        val active = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(active) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun refresh() {
        _isOnline.value = currentlyOnline()
    }
}

/** Convenience for collectors that only care when the value flips. */
fun Flow<Boolean>.distinctConnectivity(): Flow<Boolean> = distinctUntilChanged()
