package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

object LanIpResolver {
    // Cellular and virtual interfaces that MUST be ignored
    private val CELLULAR_AND_VIRTUAL_PREFIXES = listOf(
        "rmnet",   // Qualcomm mobile data
        "ccmni",   // MediaTek mobile data
        "pdp_ip",  // Generic cellular PDP context
        "wwan",    // Wireless WAN
        "usbnet",  // USB tethering
        "dummy",   // Dummy virtual interface
        "tun",     // VPN tunnel
        "tap",     // VPN tap
        "sit",     // IPv6-in-IPv4 tunnel
        "ppp",     // Point-to-Point protocol
        "lo"       // Loopback
    )

    // Preferred Wi-Fi, Hotspot, and LAN interfaces
    private val LAN_INTERFACE_KEYWORDS = listOf(
        "wlan",    // Standard Wi-Fi
        "ap",      // Access point / Hotspot
        "softap",  // SoftAP
        "bridge",  // Local bridge
        "br",      // Bridge
        "en",      // Ethernet / Wi-Fi on some Unix
        "eth",     // Ethernet
        "rndis"    // RNDIS local USB ethernet fallback
    )

    fun findLanIpAddress(context: Context? = null): String {
        try {
            // 1. Try WifiManager if connected as a client to Wi-Fi
            if (context != null) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val wifiIp = wifiManager?.connectionInfo?.ipAddress ?: 0
                if (wifiIp != 0) {
                    val ipStr = String.format(
                        "%d.%d.%d.%d",
                        wifiIp and 0xff,
                        wifiIp shr 8 and 0xff,
                        wifiIp shr 16 and 0xff,
                        wifiIp shr 24 and 0xff
                    )
                    if (isValidLanIp(ipStr)) {
                        return ipStr
                    }
                }
            }

            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())

            // Sort interfaces: LAN/Wi-Fi interfaces first
            val sortedInterfaces = interfaces.filter { intf ->
                try {
                    intf.isUp && !intf.isLoopback && !isCellularOrVirtual(intf.name)
                } catch (_: Exception) {
                    false
                }
            }.sortedByDescending { intf ->
                val name = intf.name.lowercase()
                when {
                    LAN_INTERFACE_KEYWORDS.any { name.contains(it) } -> 2
                    else -> 1
                }
            }

            // Phase 1: Look for private IPv4 addresses on preferred LAN interfaces
            for (intf in sortedInterfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        if (isPrivateSubnet(host)) {
                            return host
                        }
                    }
                }
            }

            // Phase 2: Any non-loopback IPv4 on non-cellular interfaces
            for (intf in sortedInterfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        if (!host.startsWith("127.") && !host.startsWith("169.254.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return "127.0.0.1"
    }

    fun getBroadcastAddresses(): List<InetAddress> {
        val result = mutableListOf<InetAddress>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isUp && !intf.isLoopback && !isCellularOrVirtual(intf.name)) {
                    for (intfAddr in intf.interfaceAddresses) {
                        val broadcast = intfAddr.broadcast
                        if (broadcast != null && broadcast is Inet4Address) {
                            result.add(broadcast)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun isCellularOrVirtual(name: String): Boolean {
        val lower = name.lowercase()
        return CELLULAR_AND_VIRTUAL_PREFIXES.any { prefix -> lower.startsWith(prefix) || lower.contains(prefix) }
    }

    private fun isPrivateSubnet(ip: String): Boolean {
        // 192.168.x.x
        if (ip.startsWith("192.168.")) return true
        // 172.16.x.x - 172.31.x.x (standard Android Hotspot 172.20.x.x, etc.)
        if (ip.startsWith("172.")) {
            val parts = ip.split(".")
            if (parts.size >= 2) {
                val second = parts[1].toIntOrNull()
                if (second != null && second in 16..31) return true
            }
        }
        // 10.x.x.x (only if not on cellular)
        if (ip.startsWith("10.")) return true
        return false
    }

    private fun isValidLanIp(ip: String): Boolean {
        return ip != "0.0.0.0" && ip != "127.0.0.1" && !ip.startsWith("169.254.")
    }
}
