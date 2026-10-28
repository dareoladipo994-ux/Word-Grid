package com.example.network

import org.json.JSONArray
import org.json.JSONObject

const val PROTOCOL_VERSION = 2
const val APP_VERSION = "1.0"
const val DICTIONARY_VERSION = "twl06"
const val RULES_VERSION = "standard_v1"
const val DISCOVERY_PORT = 8989
const val TCP_PORT = 8988
const val HEARTBEAT_INTERVAL_MS = 3000L
const val HEARTBEAT_TIMEOUT_MS = 10000L

data class DiscoveredRoom(
    val roomName: String,
    val hostName: String,
    val hostIp: String,
    val port: Int,
    val protocolVersion: Int,
    val appVersion: String,
    val isFull: Boolean,
    val isInMatch: Boolean,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
) {
    val isCompatible: Boolean
        get() = protocolVersion == PROTOCOL_VERSION

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("type", "ROOM_BEACON")
        obj.put("roomName", roomName)
        obj.put("hostName", hostName)
        obj.put("hostIp", hostIp)
        obj.put("port", port)
        obj.put("protocolVersion", protocolVersion)
        obj.put("appVersion", appVersion)
        obj.put("isFull", isFull)
        obj.put("isInMatch", isInMatch)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): DiscoveredRoom? {
            return try {
                DiscoveredRoom(
                    roomName = obj.optString("roomName", "WordGrid Room"),
                    hostName = obj.optString("hostName", "Host"),
                    hostIp = obj.optString("hostIp", ""),
                    port = obj.optInt("port", TCP_PORT),
                    protocolVersion = obj.optInt("protocolVersion", 1),
                    appVersion = obj.optString("appVersion", "1.0"),
                    isFull = obj.optBoolean("isFull", false),
                    isInMatch = obj.optBoolean("isInMatch", false),
                    lastSeenTimestamp = System.currentTimeMillis()
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

data class LobbyState(
    val roomName: String,
    val hostName: String,
    val guestName: String? = null,
    val hostReady: Boolean = false,
    val guestReady: Boolean = false,
    val isConnected: Boolean = false
) {
    val canStart: Boolean
        get() = isConnected && guestName != null && hostReady && guestReady
}

enum class NetworkActionType {
    PLAY_WORD,
    SWAP,
    PASS,
    RESIGN
}
