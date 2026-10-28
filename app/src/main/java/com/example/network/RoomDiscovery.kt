package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException

class RoomBroadcaster(
    private val roomSupplier: () -> DiscoveredRoom
) {
    private var job: Job? = null
    private var socket: DatagramSocket? = null

    fun start(scope: CoroutineScope) {
        stop()
        job = scope.launch(Dispatchers.IO) {
            try {
                socket = DatagramSocket().apply {
                    broadcast = true
                }
                val globalBroadcast = InetAddress.getByName("255.255.255.255")
                while (isActive) {
                    try {
                        val room = roomSupplier()
                        val bytes = room.toJson().toString().toByteArray(Charsets.UTF_8)
                        val targets = mutableListOf<InetAddress>(globalBroadcast)
                        targets.addAll(LanIpResolver.getBroadcastAddresses())

                        for (target in targets.distinct()) {
                            try {
                                val packet = DatagramPacket(bytes, bytes.size, target, DISCOVERY_PORT)
                                socket?.send(packet)
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                    delay(1000L)
                }
            } catch (_: Exception) {
            } finally {
                socket?.close()
                socket = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
    }
}

class RoomScanner(
    private val context: Context
) {
    private var job: Job? = null
    private var prunerJob: Job? = null
    private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _discoveredRooms = MutableStateFlow<List<DiscoveredRoom>>(emptyList())
    val discoveredRooms: StateFlow<List<DiscoveredRoom>> = _discoveredRooms.asStateFlow()

    fun start(scope: CoroutineScope) {
        stop()
        _discoveredRooms.value = emptyList()

        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("WordGridRoomScanner")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (_: Exception) {}

        job = scope.launch(Dispatchers.IO) {
            try {
                val s = DatagramSocket(DISCOVERY_PORT).apply {
                    broadcast = true
                    reuseAddress = true
                }
                socket = s
                val buffer = ByteArray(2048)

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        s.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        val senderIp = packet.address?.hostAddress ?: ""
                        if (text.isNotEmpty()) {
                            val json = JSONObject(text)
                            if (json.optString("type") == "ROOM_BEACON") {
                                var room = DiscoveredRoom.fromJson(json)
                                if (room != null) {
                                    if (room.hostIp.isBlank() || room.hostIp == "127.0.0.1") {
                                        room = room.copy(hostIp = senderIp)
                                    }
                                    updateRoom(room)
                                }
                            }
                        }
                    } catch (_: SocketException) {
                        break
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {
            } finally {
                socket?.close()
                socket = null
            }
        }

        // Periodic pruner for rooms that went offline (> 4 seconds without beacon)
        prunerJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(2000L)
                val now = System.currentTimeMillis()
                val current = _discoveredRooms.value
                val filtered = current.filter { now - it.lastSeenTimestamp < 4500L }
                if (filtered.size != current.size) {
                    _discoveredRooms.value = filtered
                }
            }
        }
    }

    private fun updateRoom(room: DiscoveredRoom) {
        val current = _discoveredRooms.value.toMutableList()
        val index = current.indexOfFirst { it.hostIp == room.hostIp && it.port == room.port }
        if (index >= 0) {
            current[index] = room
        } else {
            current.add(room)
        }
        _discoveredRooms.value = current
    }

    fun stop() {
        job?.cancel()
        job = null
        prunerJob?.cancel()
        prunerJob = null
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
        multicastLock = null
    }
}
