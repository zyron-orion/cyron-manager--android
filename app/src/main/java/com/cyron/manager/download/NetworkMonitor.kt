package com.cyron.manager.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class NetworkState(val online: Boolean, val unmetered: Boolean)

@Singleton
class NetworkMonitor @Inject constructor(@ApplicationContext context: Context) {
    private val manager: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)

    fun currentState(): NetworkState {
        val connectivity = manager ?: return NetworkState(true, true)
        return runCatching {
            val network = connectivity.activeNetwork ?: return NetworkState(false, false)
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return NetworkState(false, false)
            val online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val unmetered = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            NetworkState(online, unmetered)
        }.getOrDefault(NetworkState(true, true))
    }

    fun isOnline(): Boolean = currentState().online

    fun isUnmetered(): Boolean = currentState().unmetered

    fun observe(): Flow<NetworkState> = callbackFlow {
        val connectivity = manager ?: run { trySend(NetworkState(true, true)); close(); return@callbackFlow }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(state(network)) }
            override fun onLost(network: Network) { trySend(currentState()) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(state(network, capabilities))
            }
        }
        trySend(currentState())
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { runCatching { connectivity.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    private fun state(network: Network, capabilities: NetworkCapabilities? = null): NetworkState = runCatching {
        val actual = capabilities ?: manager?.getNetworkCapabilities(network)
        val online = actual?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            actual.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val unmetered = actual?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
            actual?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true ||
            actual?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
        NetworkState(online, unmetered)
    }.getOrDefault(NetworkState(false, false))
}
