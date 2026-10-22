package com.droiddeck.launcher.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.droiddeck.launcher.core.SessionPart
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import org.json.JSONArray
import org.json.JSONObject

class LinuxNetworkLinkComponent(
    context: Context,
    private val rootDir: File,
) : SessionPart() {
    private val appContext = context.applicationContext
    private val connectivity =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val lock = Any()
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var currentNetwork: Network? = null
    private var currentProperties: LinkProperties? = null
    private var currentCapabilities: NetworkCapabilities? = null
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var wifiReceiver: BroadcastReceiver? = null

    fun publish() = synchronized(lock) {
        currentNetwork = connectivity.activeNetwork
        currentProperties = currentNetwork?.let(connectivity::getLinkProperties)
        currentCapabilities = currentNetwork?.let(connectivity::getNetworkCapabilities)
        write(currentProperties, currentCapabilities)
    }

    override fun start() {
        val registered = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = synchronized(lock) {
                currentNetwork = network
                currentProperties = null
                currentCapabilities = null
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = synchronized(lock) {
                if (network == currentNetwork) {
                    currentCapabilities = capabilities
                    if (currentProperties != null) write(currentProperties, capabilities)
                }
            }

            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) = synchronized(lock) {
                if (network == currentNetwork) {
                    currentProperties = properties
                    write(properties, currentCapabilities)
                }
            }

            override fun onLost(network: Network) = synchronized(lock) {
                if (network == currentNetwork) {
                    currentNetwork = null
                    currentProperties = null
                    currentCapabilities = null
                    write(null, null)
                }
            }
        }
        synchronized(lock) { callback = registered }
        connectivity.registerDefaultNetworkCallback(registered)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                synchronized(lock) { writeNetworkState(currentProperties, currentCapabilities) }
            }
        }
        wifiReceiver = receiver
        appContext.registerReceiver(receiver, IntentFilter().apply {
            addAction(WifiManager.RSSI_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        })
    }

    override fun stop() {
        wifiReceiver?.let { appContext.unregisterReceiver(it) }
        wifiReceiver = null
        val registered = synchronized(lock) { callback.also { callback = null } } ?: return
        try {
            connectivity.unregisterNetworkCallback(registered)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Network callback was already gone", e)
        }
    }


    private fun write(properties: LinkProperties?, capabilities: NetworkCapabilities?) {
        val file = File(rootDir, LINK_FILE)
        synchronized(lock) {
            try {
                file.parentFile?.mkdirs()
                val staged = File(file.path + ".staged")
                staged.writeText(describe(properties))
                if (!staged.renameTo(file)) throw IOException("Could not replace $file")
            } catch (e: IOException) {
                Log.w(TAG, "Could not publish the network link", e)
            }
            writeNetworkState(properties, capabilities)
            writeResolver(properties)
        }
    }

    @Suppress("DEPRECATION")
    private fun writeNetworkState(properties: LinkProperties?, capabilities: NetworkCapabilities?) {
        val transport = when {
            capabilities == null -> "none"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
        val state = JSONObject().apply {
            put("transport", transport)
            put("interface", properties?.interfaceName ?: "android")
            put("mtu", properties?.mtu?.takeIf { it > 0 } ?: DEFAULT_MTU)
            put("connected", properties != null && capabilities != null)
            put("validated", capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
            put("captivePortal", capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true)
            put("metered", capabilities?.let { !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) })
            put("wifiEnabled", wifiManager?.isWifiEnabled == true)
            put("scanAllowed", false)
            put("lastScan", -1L)
            put("accessPoints", JSONArray())
            put("addresses", JSONArray().apply {
                properties?.linkAddresses.orEmpty().forEach {
                    put(JSONObject().put("address", it.address.hostAddress?.substringBefore('%')).put("prefix", it.prefixLength))
                }
            })
            put("gateways", JSONArray().apply {
                properties?.routes.orEmpty().filter { it.isDefaultRoute }.forEach {
                    it.gateway?.takeUnless { address -> address.isAnyLocalAddress }?.let { address ->
                        put(address.hostAddress?.substringBefore('%'))
                    }
                }
            })
            put("dns", JSONArray().apply {
                properties?.dnsServers.orEmpty().filter { !it.isLinkLocalAddress }.forEach {
                    put(it.hostAddress?.substringBefore('%'))
                }
            })
            if (transport == "wifi") {
                val connectionInfo = try { wifiManager?.connectionInfo } catch (e: SecurityException) { null }
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    (capabilities?.transportInfo as? WifiInfo) ?: connectionInfo
                } else {
                    connectionInfo
                }
                if (info != null) {
                    put("strength", WifiManager.calculateSignalLevel(info.rssi, 101))
                    put("frequency", info.frequency.coerceAtLeast(0))
                    put("bitrate", info.linkSpeed.coerceAtLeast(0) * 1000)
                }
            }
        }
        try {
            val file = File(rootDir, "etc/droiddeck-network.json")
            val staged = File(file.path + ".staged")
            staged.writeText(state.toString())
            if (!staged.renameTo(file)) throw IOException("Could not replace network state")
        } catch (e: IOException) {
            Log.w(TAG, "Could not publish the network state", e)
        }
    }

    private fun writeResolver(properties: LinkProperties?) {
        val own = properties?.dnsServers.orEmpty()
            .filter { !it.isLinkLocalAddress && !it.isLoopbackAddress && !it.isAnyLocalAddress }
            .sortedBy { if (it is Inet4Address) 0 else 1 }
            .mapNotNull { it.hostAddress?.substringBefore('%') }
        val servers = (own + listOf("8.8.8.8", "1.1.1.1", "2001:4860:4860::8888")).distinct().take(6)
        val text = buildString {
            append("# Written by the app from the device's active network; edits are overwritten.\n")
            for (server in servers) append("nameserver $server\n")
            append("options timeout:2 attempts:2\n")
        }
        try {
            val etc = File(rootDir, "etc")
            val staged = File(etc, "resolv.conf.staged")
            staged.writeText(text)
            if (!staged.renameTo(File(etc, "resolv.conf"))) throw IOException("Could not replace resolv.conf")
            val hosts = File(etc, "hosts")
            if (!hosts.isFile || !hosts.readText().contains("localhost")) {
                hosts.writeText("127.0.0.1 localhost\n::1 localhost\n")
            }
            Log.i(TAG, "resolver: " + servers.joinToString(" "))
        } catch (e: IOException) {
            Log.w(TAG, "Could not write the resolver", e)
        }
    }

    private fun describe(properties: LinkProperties?): String {
        val addresses = properties?.linkAddresses.orEmpty()
            .filter { it.address is Inet4Address || it.address is Inet6Address }
        val name = properties?.interfaceName?.takeIf { addresses.isNotEmpty() } ?: OFFLINE_NAME
        val mtu = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) properties?.mtu ?: 0 else 0
        return buildString {
            append("if $name $LINK_INDEX ${if (mtu > 0) mtu else DEFAULT_MTU}\n")
            append("mac ${seededMac()}\n")
            if (addresses.isEmpty()) {
                append("addr $OFFLINE_ADDRESS\n")
                return@buildString
            }
            for (address in addresses) {
                append("addr ${address.address.hostAddress?.substringBefore('%')} ${address.prefixLength}\n")
            }
            for (family in listOf(Inet4Address::class.java, Inet6Address::class.java)) {
                val gateway = properties?.routes.orEmpty()
                    .firstOrNull { it.isDefaultRoute && family.isInstance(it.gateway) && !it.gateway!!.isAnyLocalAddress }
                    ?.gateway ?: continue
                append("gw ${gateway.hostAddress?.substringBefore('%')}\n")
            }
        }
    }

    private fun seededMac(): String {
        val id = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID) ?: "bannerlator"
        val bytes = java.security.MessageDigest.getInstance("SHA-1").digest(id.toByteArray())
        val b0 = (bytes[0].toInt() and 0xfc) or 0x02
        return listOf(b0, bytes[1].toInt(), bytes[2].toInt(), bytes[3].toInt(), bytes[4].toInt(), bytes[5].toInt())
            .joinToString(":") { "%02x".format(it and 0xff) }
    }

    companion object {
        private const val TAG = "LinuxNetworkLink"
        private const val LINK_FILE = "etc/droiddeck-net"
        private const val LINK_INDEX = 2
        private const val DEFAULT_MTU = 1500
        private const val OFFLINE_NAME = "eth0"
        private const val OFFLINE_ADDRESS = "10.0.0.2 24"
    }
}
