package dev.patrickgold.florisboard.ime.text.dictation.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest

enum class DownloadWaitReason { NO_INTERNET, WIFI_REQUIRED, STARTING }

/** A transport choice is not a billing/metering constraint. VPNs inherit their underlying transports. */
object ModelDownloadNetwork {
    const val RESUMABLE_CHUNK_BYTES = 64L * 1024

    fun request(allowMobileData: Boolean): NetworkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        .apply {
            if (!allowMobileData) {
                addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            }
        }.build()

    fun allows(capabilities: NetworkCapabilities?, allowMobileData: Boolean): Boolean =
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            (allowMobileData || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))

    fun waitReason(context: Context, allowMobileData: Boolean): DownloadWaitReason {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        return when {
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true -> DownloadWaitReason.NO_INTERNET
            !allows(capabilities, allowMobileData) -> DownloadWaitReason.WIFI_REQUIRED
            else -> DownloadWaitReason.STARTING
        }
    }
}
