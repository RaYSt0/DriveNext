package ru.student.drivenext.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import ru.student.drivenext.domain.NetworkRepository

class AndroidNetworkRepository(context: Context) : NetworkRepository {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var callback: ConnectivityManager.NetworkCallback? = null

    override fun isOnline(): Boolean {
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun observe(onChanged: (Boolean) -> Unit) {
        stopObserving()
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = notifyChange()
            override fun onLost(network: Network) = notifyChange()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = notifyChange()

            private fun notifyChange() {
                mainHandler.post { onChanged(isOnline()) }
            }
        }.also { manager.registerDefaultNetworkCallback(it) }
        onChanged(isOnline())
    }

    override fun stopObserving() {
        callback?.let { manager.unregisterNetworkCallback(it) }
        callback = null
    }
}
