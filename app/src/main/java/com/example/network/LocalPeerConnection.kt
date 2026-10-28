package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Collections

sealed class PeerConnectionState {
    object Disconnected : PeerConnectionState()
    data class Hosting(val localIp: String, val port: Int) : PeerConnectionState()
    object Connecting : PeerConnectionState()
    data class Connected(val peerAddress: String, val isHost: Boolean) : PeerConnectionState()
    data class MatchEndedDisconnection(val reason: String = "Connection ended. This match has ended. Reconnect to start a new game.") : PeerConnectionState()
    data class Error(val message: String) : PeerConnectionState()
}

class LocalPeerConnection(
    private val context: Context,
    private val onMessageReceived: (JSONObject) -> Unit,
    private val onStateChanged: (PeerConnectionState) -> Unit
) {
    companion object {
        const val DEFAULT_PORT = TCP_PORT
    }

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var writer: PrintWriter? = null
    private var readerJob: Job? = null
    private var heartbeatJob: Job? = null
    private var isHosting = false
    private var isMatchActive = false

    @Volatile
    private var lastReceivedTimestamp = System.currentTimeMillis()

    private val scope = CoroutineScope(Dispatchers.IO)

    fun setMatchActive(active: Boolean) {
        isMatchActive = active
    }

    fun getLocalIpAddress(): String {
        return LanIpResolver.findLanIpAddress(context)
    }

    fun startHosting(port: Int = DEFAULT_PORT) {
        disconnect()
        isHosting = true
        isMatchActive = false
        val localIp = getLocalIpAddress()
        onStateChanged(PeerConnectionState.Hosting(localIp, port))

        readerJob = scope.launch {
            try {
                val server = ServerSocket(port).apply {
                    reuseAddress = true
                }
                serverSocket = server

                while (isActive) {
                    val socket = server.accept()
                    // Reject 2nd guest if one is already connected or match in progress
                    if (clientSocket != null && clientSocket?.isClosed == false) {
                        launch(Dispatchers.IO) {
                            try {
                                val tempWriter = PrintWriter(socket.getOutputStream(), true)
                                val rejectMsg = JSONObject().apply {
                                    put("type", "REJECT")
                                    put("reason", if (isMatchActive) "Room is already in a match." else "Room is full (max 2 players).")
                                }
                                tempWriter.println(rejectMsg.toString())
                                tempWriter.flush()
                                socket.close()
                            } catch (_: Exception) {}
                        }
                        continue
                    }

                    clientSocket = socket
                    lastReceivedTimestamp = System.currentTimeMillis()
                    withContext(Dispatchers.Main) {
                        onStateChanged(PeerConnectionState.Connected(socket.inetAddress.hostAddress ?: "Peer", isHost = true))
                    }
                    startHeartbeat()
                    listenToSocket(socket, isHost = true)
                }
            } catch (e: Exception) {
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        onStateChanged(PeerConnectionState.Error(e.message ?: "Server error"))
                    }
                }
            }
        }
    }

    fun connectToHost(hostIp: String, port: Int = DEFAULT_PORT) {
        disconnect()
        isHosting = false
        isMatchActive = false
        onStateChanged(PeerConnectionState.Connecting)

        readerJob = scope.launch {
            try {
                val socket = Socket(hostIp, port).apply {
                    soTimeout = 15000
                }
                clientSocket = socket
                lastReceivedTimestamp = System.currentTimeMillis()
                withContext(Dispatchers.Main) {
                    onStateChanged(PeerConnectionState.Connected(hostIp, isHost = false))
                }
                startHeartbeat()
                listenToSocket(socket, isHost = false)
            } catch (e: Exception) {
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        onStateChanged(PeerConnectionState.Error("Could not connect to $hostIp: ${e.message}"))
                    }
                }
            }
        }
    }

    private suspend fun listenToSocket(socket: Socket, isHost: Boolean) = withContext(Dispatchers.IO) {
        try {
            val out = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            writer = out

            while (isActive && !socket.isClosed) {
                val line: String? = try {
                    reader.readLine()
                } catch (_: SocketTimeoutException) {
                    continue
                }
                if (line == null) break

                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    lastReceivedTimestamp = System.currentTimeMillis()
                    try {
                        val json = JSONObject(trimmed)
                        val type = json.optString("type")
                        if (type == "HEARTBEAT_PING") {
                            sendMessage(JSONObject().apply {
                                put("type", "HEARTBEAT_PONG")
                                put("timestamp", System.currentTimeMillis())
                            })
                            continue
                        } else if (type == "HEARTBEAT_PONG") {
                            continue
                        }

                        withContext(Dispatchers.Main) {
                            onMessageReceived(json)
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            // Socket IO exception / break
        } finally {
            heartbeatJob?.cancel()
            try {
                writer?.close()
                socket.close()
            } catch (_: Exception) {}
            writer = null
            clientSocket = null

            withContext(Dispatchers.Main) {
                if (isMatchActive) {
                    isMatchActive = false
                    onStateChanged(PeerConnectionState.MatchEndedDisconnection())
                } else {
                    if (isHost && serverSocket?.isClosed == false) {
                        // Host stays in hosting mode in lobby waiting for a new guest
                        onStateChanged(PeerConnectionState.Hosting(getLocalIpAddress(), DEFAULT_PORT))
                    } else {
                        onStateChanged(PeerConnectionState.Disconnected)
                    }
                }
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                // Send Ping
                val ping = JSONObject().apply {
                    put("type", "HEARTBEAT_PING")
                    put("timestamp", System.currentTimeMillis())
                }
                sendMessage(ping)

                // Check timeout
                val now = System.currentTimeMillis()
                if (now - lastReceivedTimestamp > HEARTBEAT_TIMEOUT_MS) {
                    // Exceeded heartbeat timeout!
                    withContext(Dispatchers.Main) {
                        handleHeartbeatTimeout()
                    }
                    break
                }
            }
        }
    }

    private fun handleHeartbeatTimeout() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        try {
            writer?.close()
            clientSocket?.close()
        } catch (_: Exception) {}
        writer = null
        clientSocket = null

        if (isMatchActive) {
            isMatchActive = false
            onStateChanged(PeerConnectionState.MatchEndedDisconnection())
        } else {
            if (isHosting && serverSocket?.isClosed == false) {
                onStateChanged(PeerConnectionState.Hosting(getLocalIpAddress(), DEFAULT_PORT))
            } else {
                onStateChanged(PeerConnectionState.Disconnected)
            }
        }
    }

    fun sendMessage(json: JSONObject) {
        scope.launch(Dispatchers.IO) {
            try {
                writer?.println(json.toString())
                writer?.flush()
            } catch (_: Exception) {}
        }
    }

    fun sendLeaveNotification() {
        try {
            val leaveMsg = JSONObject().apply {
                put("type", "LEAVE_MATCH")
                put("reason", "Player intentionally left the match.")
            }
            writer?.println(leaveMsg.toString())
            writer?.flush()
        } catch (_: Exception) {}
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        readerJob?.cancel()
        readerJob = null
        isMatchActive = false
        isHosting = false

        try {
            writer?.close()
            clientSocket?.close()
            serverSocket?.close()
        } catch (_: Exception) {}
        writer = null
        clientSocket = null
        serverSocket = null
        onStateChanged(PeerConnectionState.Disconnected)
    }
}
