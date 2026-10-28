package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BoardCell
import com.example.data.model.ComputerDifficulty
import com.example.data.model.GameMode
import com.example.data.model.GameSettings
import com.example.data.model.GameStatistics
import com.example.data.model.MatchCategory
import com.example.data.model.MatchState
import com.example.data.model.MatchStatus
import com.example.data.model.MatchSummary
import com.example.data.model.MoveRecord
import com.example.data.model.MoveType
import com.example.data.model.PlacedTile
import com.example.data.model.PlayerTurn
import com.example.data.model.Tile
import com.example.data.model.TileDistribution
import com.example.engine.BoardGenerator
import com.example.engine.ComputerMove
import com.example.engine.ComputerPlayer
import com.example.engine.DictionaryTrie
import com.example.engine.RuleValidator
import com.example.engine.ValidationResult
import com.example.network.APP_VERSION
import com.example.network.DiscoveredRoom
import com.example.network.LobbyState
import com.example.network.LocalPeerConnection
import com.example.network.NetworkActionType
import com.example.network.NetworkPacketHelper
import com.example.network.PeerConnectionState
import com.example.network.PROTOCOL_VERSION
import com.example.network.RoomBroadcaster
import com.example.network.RoomScanner
import com.example.persistence.MatchRepository
import com.example.persistence.StatisticsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random

data class WordGridUiState(
    val currentScreen: Screen = Screen.HOME,
    val matchState: MatchState? = null,
    val hasSavedMatch: Boolean = false,
    val settings: GameSettings = GameSettings(),
    val statistics: GameStatistics = GameStatistics(),
    val draftPlacements: List<PlacedTile> = emptyList(),
    val selectedRackTileId: String? = null,
    val isComputerThinking: Boolean = false,
    val prospectiveScore: Int? = null,
    val prospectiveWord: String? = null,
    val validationError: String? = null,
    val pendingBlankCoordinate: Pair<Int, Int>? = null,
    val showSwapDialog: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val showHowToPlayDialog: Boolean = false,
    val showResultsDialog: Boolean = false,
    val showResignConfirmDialog: Boolean = false,
    val infoToastMessage: String? = null,
    val boardShakeTrigger: Long = 0L,
    val isHandoverPending: Boolean = false,
    val isWifiHost: Boolean = true,
    val peerConnectionState: PeerConnectionState = PeerConnectionState.Disconnected,
    val localIpAddress: String = "127.0.0.1",
    val wifiLobbyState: LobbyState? = null,
    val discoveredRooms: List<DiscoveredRoom> = emptyList(),
    val showWifiDisconnectDialog: Boolean = false,
    val wifiDisconnectMessage: String = "Connection ended. This match has ended. Reconnect to start a new game.",
    val showWifiLeaveConfirmDialog: Boolean = false,
    val showLegacyWifiDiscardedDialog: Boolean = false,
    val isWifiActionPending: Boolean = false
)

enum class Screen {
    HOME,
    LOBBY,
    GAME,
    STATISTICS
}

class WordGridViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MatchRepository(application)
    private val statsRepository = StatisticsRepository(application)
    private val dictionary = DictionaryTrie.get(application)

    private val _uiState = MutableStateFlow(WordGridUiState())
    val uiState: StateFlow<WordGridUiState> = _uiState.asStateFlow()

    private var computerTurnJob: Job? = null
    private var peerConnection: LocalPeerConnection? = null
    private var roomBroadcaster: RoomBroadcaster? = null
    private var roomScanner: RoomScanner? = null

    // Set of action IDs processed by host for idempotent deduplication
    private val processedActionIds = mutableSetOf<String>()

    init {
        val settings = repository.loadSettings()
        val hasSaved = repository.hasSavedMatch()
        val saved = if (hasSaved) repository.loadMatch() else null
        val stats = statsRepository.loadStatistics()
        val legacyDiscardedNotice = repository.isLegacyWifiMatchDiscardedNoticePending()

        peerConnection = LocalPeerConnection(
            context = application,
            onMessageReceived = { json -> handlePeerMessage(json) },
            onStateChanged = { state -> handlePeerConnectionState(state) }
        )
        val ip = peerConnection?.getLocalIpAddress() ?: "127.0.0.1"

        roomBroadcaster = RoomBroadcaster {
            val lobby = _uiState.value.wifiLobbyState
            DiscoveredRoom(
                roomName = lobby?.roomName ?: "WordGrid Room",
                hostName = lobby?.hostName ?: settings.playerName,
                hostIp = ip,
                port = LocalPeerConnection.DEFAULT_PORT,
                protocolVersion = PROTOCOL_VERSION,
                appVersion = APP_VERSION,
                isFull = lobby?.guestName != null,
                isInMatch = _uiState.value.matchState != null && _uiState.value.matchState?.isGameOver == false
            )
        }

        roomScanner = RoomScanner(application)
        viewModelScope.launch {
            roomScanner?.discoveredRooms?.collect { rooms ->
                _uiState.update { it.copy(discoveredRooms = rooms) }
            }
        }

        _uiState.update {
            it.copy(
                settings = settings,
                hasSavedMatch = hasSaved && saved != null && !saved.isGameOver && saved.gameMode != GameMode.LOCAL_WIFI,
                matchState = saved,
                statistics = stats,
                localIpAddress = ip,
                showLegacyWifiDiscardedDialog = legacyDiscardedNotice
            )
        }
    }

    // Navigation
    fun navigateToHome() {
        val currentMatch = _uiState.value.matchState
        computerTurnJob?.cancel()

        if (currentMatch != null && currentMatch.gameMode == GameMode.LOCAL_WIFI) {
            // Local Wi-Fi matches cannot be saved to Home. Show confirmation if game is unfinished.
            if (!currentMatch.isGameOver) {
                _uiState.update { it.copy(showWifiLeaveConfirmDialog = true) }
                return
            } else {
                leaveLocalWifiMatch()
                return
            }
        }

        if (currentMatch != null && !currentMatch.isGameOver) {
            val matchWithDrafts = currentMatch.copy(
                draftPlacements = _uiState.value.draftPlacements
            )
            repository.saveMatch(matchWithDrafts)
            _uiState.update {
                it.copy(
                    currentScreen = Screen.HOME,
                    hasSavedMatch = true,
                    matchState = matchWithDrafts,
                    isComputerThinking = false
                )
            }
        } else {
            _uiState.update { it.copy(currentScreen = Screen.HOME) }
        }
    }

    fun openStatistics() {
        val stats = statsRepository.loadStatistics()
        _uiState.update { it.copy(currentScreen = Screen.STATISTICS, statistics = stats) }
    }

    fun resetStatistics() {
        statsRepository.resetStatistics()
        _uiState.update { it.copy(statistics = GameStatistics()) }
    }

    fun resumeSavedMatch() {
        val saved = repository.loadMatch() ?: _uiState.value.matchState ?: return
        if (saved.gameMode == GameMode.LOCAL_WIFI) {
            // Safety: never resume Wi-Fi match
            repository.deleteSavedMatch()
            return
        }

        computerTurnJob?.cancel()
        val restoredBoard = updateBoardWithDrafts(saved.board, saved.draftPlacements)
        val activeMatch = saved.copy(board = restoredBoard)

        _uiState.update {
            it.copy(
                currentScreen = Screen.GAME,
                matchState = activeMatch,
                draftPlacements = saved.draftPlacements,
                selectedRackTileId = null,
                isHandoverPending = saved.isHandoverPending,
                isComputerThinking = false
            )
        }

        recalculateProspectiveScore(activeMatch, saved.draftPlacements)

        if (activeMatch.gameMode == GameMode.COMPUTER &&
            activeMatch.currentTurn == PlayerTurn.COMPUTER &&
            !activeMatch.isGameOver
        ) {
            triggerComputerTurn(activeMatch)
        }
    }

    // Match Initialization
    fun startNewMatch(confirmedAbandon: Boolean = false) {
        startComputerMatch(ComputerDifficulty.BALANCED, _uiState.value.settings.playerName)
    }

    fun startComputerMatch(difficulty: ComputerDifficulty, playerName: String) {
        val random = Random.Default
        val computerName = ComputerPlayer.NAMES.random(random)
        initializeSingleDeviceMatch(
            gameMode = GameMode.COMPUTER,
            difficulty = difficulty,
            p1Name = playerName,
            p2Name = computerName,
            startingTurn = if (random.nextBoolean()) PlayerTurn.HUMAN else PlayerTurn.COMPUTER
        )
    }

    fun startPassAndPlayMatch(p1Name: String, p2Name: String) {
        val random = Random.Default
        initializeSingleDeviceMatch(
            gameMode = GameMode.PASS_AND_PLAY,
            difficulty = ComputerDifficulty.BALANCED,
            p1Name = p1Name,
            p2Name = p2Name,
            startingTurn = if (random.nextBoolean()) PlayerTurn.HUMAN else PlayerTurn.COMPUTER
        )
    }

    private fun initializeSingleDeviceMatch(
        gameMode: GameMode,
        difficulty: ComputerDifficulty,
        p1Name: String,
        p2Name: String,
        startingTurn: PlayerTurn
    ) {
        computerTurnJob?.cancel()

        val random = Random.Default
        val category = BoardGenerator.selectMatchCategory(random)
        val (board, finalCategory) = BoardGenerator.generateBoard(category)

        val bag = TileDistribution.createStandardBag()
        val playerRack = mutableListOf<Tile>()
        val computerRack = mutableListOf<Tile>()

        repeat(7) {
            if (bag.isNotEmpty()) playerRack.add(bag.removeAt(0))
            if (bag.isNotEmpty()) computerRack.add(bag.removeAt(0))
        }

        val newMatch = MatchState(
            matchId = UUID.randomUUID().toString(),
            gameMode = gameMode,
            computerDifficulty = difficulty,
            player1Name = p1Name,
            player2Name = p2Name,
            board = board,
            playerRack = playerRack,
            computerRack = computerRack,
            tileBag = bag,
            playerScore = 0,
            computerScore = 0,
            currentTurn = startingTurn,
            startingPlayer = startingTurn,
            status = MatchStatus.IN_PROGRESS,
            computerName = p2Name,
            matchCategory = finalCategory,
            moveHistory = emptyList(),
            draftPlacements = emptyList(),
            isHandoverPending = false
        )

        repository.saveMatch(newMatch)

        _uiState.update {
            it.copy(
                currentScreen = Screen.GAME,
                matchState = newMatch,
                hasSavedMatch = true,
                draftPlacements = emptyList(),
                selectedRackTileId = null,
                prospectiveScore = null,
                validationError = null,
                showResultsDialog = false,
                isHandoverPending = false
            )
        }

        if (gameMode == GameMode.COMPUTER && startingTurn == PlayerTurn.COMPUTER) {
            triggerComputerTurn(newMatch)
        }
    }

    // --- Local Wi-Fi Room & Lobby Flow ---

    fun refreshRoomDiscovery() {
        roomScanner?.start(viewModelScope)
    }

    fun startWifiHost(roomName: String, p1Name: String) {
        peerConnection?.disconnect()
        roomScanner?.stop()

        val lobby = LobbyState(
            roomName = roomName,
            hostName = p1Name,
            guestName = null,
            hostReady = false,
            guestReady = false,
            isConnected = false
        )

        _uiState.update {
            it.copy(
                currentScreen = Screen.LOBBY,
                isWifiHost = true,
                wifiLobbyState = lobby,
                matchState = null
            )
        }

        peerConnection?.startHosting()
        roomBroadcaster?.start(viewModelScope)
    }

    fun startWifiJoin(hostIp: String, p2Name: String) {
        peerConnection?.disconnect()
        roomScanner?.stop()
        roomBroadcaster?.stop()

        val lobby = LobbyState(
            roomName = "Connecting to $hostIp...",
            hostName = "Host",
            guestName = p2Name,
            hostReady = false,
            guestReady = false,
            isConnected = false
        )

        _uiState.update {
            it.copy(
                currentScreen = Screen.LOBBY,
                isWifiHost = false,
                wifiLobbyState = lobby,
                matchState = null
            )
        }

        peerConnection?.connectToHost(hostIp)
    }

    fun toggleLobbyReady(isReady: Boolean) {
        val currentLobby = _uiState.value.wifiLobbyState ?: return
        if (_uiState.value.isWifiHost) {
            val updated = currentLobby.copy(hostReady = isReady)
            _uiState.update { it.copy(wifiLobbyState = updated) }
            broadcastLobbyState(updated)
        } else {
            val updated = currentLobby.copy(guestReady = isReady)
            _uiState.update { it.copy(wifiLobbyState = updated) }
            peerConnection?.sendMessage(JSONObject().apply {
                put("type", "LOBBY_SET_READY")
                put("ready", isReady)
            })
        }
    }

    private fun broadcastLobbyState(lobby: LobbyState) {
        peerConnection?.sendMessage(JSONObject().apply {
            put("type", "LOBBY_STATE")
            put("roomName", lobby.roomName)
            put("hostName", lobby.hostName)
            put("guestName", lobby.guestName ?: "")
            put("hostReady", lobby.hostReady)
            put("guestReady", lobby.guestReady)
        })
    }

    fun leaveWifiLobby() {
        if (_uiState.value.isWifiHost) {
            peerConnection?.sendMessage(JSONObject().apply {
                put("type", "LOBBY_LEAVE")
                put("reason", "Host closed the room.")
            })
        } else {
            peerConnection?.sendMessage(JSONObject().apply {
                put("type", "LOBBY_LEAVE")
                put("reason", "Guest left the room.")
            })
        }

        roomBroadcaster?.stop()
        peerConnection?.disconnect()
        _uiState.update {
            it.copy(
                currentScreen = Screen.HOME,
                wifiLobbyState = null
            )
        }
    }

    fun startWifiMatchFromLobby() {
        val lobby = _uiState.value.wifiLobbyState ?: return
        if (!_uiState.value.isWifiHost || !lobby.canStart) return

        peerConnection?.setMatchActive(true)
        processedActionIds.clear()

        val random = Random.Default
        val category = BoardGenerator.selectMatchCategory(random)
        val (board, finalCategory) = BoardGenerator.generateBoard(category)

        val bag = TileDistribution.createStandardBag()
        val hostRack = mutableListOf<Tile>()
        val guestRack = mutableListOf<Tile>()

        repeat(7) {
            if (bag.isNotEmpty()) hostRack.add(bag.removeAt(0))
            if (bag.isNotEmpty()) guestRack.add(bag.removeAt(0))
        }

        val startingTurn = if (random.nextBoolean()) PlayerTurn.HUMAN else PlayerTurn.COMPUTER
        val matchId = UUID.randomUUID().toString()

        val authoritativeMatch = MatchState(
            matchId = matchId,
            revision = 1L,
            gameMode = GameMode.LOCAL_WIFI,
            computerDifficulty = ComputerDifficulty.BALANCED,
            player1Name = lobby.hostName,
            player2Name = lobby.guestName ?: "Guest",
            board = board,
            playerRack = hostRack,
            computerRack = guestRack, // Held authoritatively on host
            tileBag = bag,
            playerScore = 0,
            computerScore = 0,
            currentTurn = startingTurn,
            startingPlayer = startingTurn,
            status = MatchStatus.IN_PROGRESS,
            computerName = lobby.guestName ?: "Guest",
            matchCategory = finalCategory,
            moveHistory = emptyList(),
            draftPlacements = emptyList(),
            isHandoverPending = false
        )

        // Send START_MATCH to guest (sanitized: only guest rack and public board)
        val startPayload = JSONObject().apply {
            put("type", "START_MATCH")
            put("matchId", matchId)
            put("revision", 1L)
            put("startingPlayer", startingTurn.name)
            put("player1Name", lobby.hostName)
            put("player2Name", lobby.guestName ?: "Guest")
            put("matchCategory", finalCategory.name)
            put("guestRack", NetworkPacketHelper.serializeTileList(guestRack))
            put("board", NetworkPacketHelper.serializeBoard(board))
            put("bagCount", bag.size)
        }
        peerConnection?.sendMessage(startPayload)

        _uiState.update {
            it.copy(
                currentScreen = Screen.GAME,
                matchState = authoritativeMatch,
                draftPlacements = emptyList(),
                selectedRackTileId = null,
                prospectiveScore = null,
                validationError = null,
                showResultsDialog = false,
                isWifiActionPending = false
            )
        }
    }

    // --- Peer Message Processing ---

    private fun handlePeerMessage(json: JSONObject) {
        val type = json.optString("type")
        val isHost = _uiState.value.isWifiHost

        when (type) {
            "HELLO" -> {
                if (isHost) {
                    val clientProtocol = json.optInt("protocolVersion", 1)
                    if (clientProtocol != PROTOCOL_VERSION) {
                        peerConnection?.sendMessage(JSONObject().apply {
                            put("type", "REJECT")
                            put("reason", "Incompatible protocol version ($clientProtocol vs $PROTOCOL_VERSION). Both devices must use the same WordGrid release.")
                        })
                        return
                    }

                    val guestName = json.optString("playerName", "Guest")
                    val currentLobby = _uiState.value.wifiLobbyState ?: return
                    val updatedLobby = currentLobby.copy(guestName = guestName, isConnected = true)
                    _uiState.update { it.copy(wifiLobbyState = updatedLobby) }

                    // Send WELCOME & updated LOBBY_STATE
                    peerConnection?.sendMessage(JSONObject().apply {
                        put("type", "WELCOME")
                        put("protocolVersion", PROTOCOL_VERSION)
                        put("roomName", updatedLobby.roomName)
                        put("hostName", updatedLobby.hostName)
                    })
                    broadcastLobbyState(updatedLobby)
                }
            }

            "WELCOME" -> {
                if (!isHost) {
                    val roomName = json.optString("roomName", "WordGrid Room")
                    val hostName = json.optString("hostName", "Host")
                    _uiState.update {
                        it.copy(
                            wifiLobbyState = it.wifiLobbyState?.copy(
                                roomName = roomName,
                                hostName = hostName,
                                isConnected = true
                            )
                        )
                    }
                }
            }

            "REJECT" -> {
                val reason = json.optString("reason", "Connection rejected.")
                peerConnection?.disconnect()
                _uiState.update {
                    it.copy(
                        currentScreen = Screen.HOME,
                        showWifiDisconnectDialog = true,
                        wifiDisconnectMessage = reason
                    )
                }
            }

            "LOBBY_STATE" -> {
                if (!isHost) {
                    val roomName = json.optString("roomName")
                    val hostName = json.optString("hostName")
                    val guestName = json.optString("guestName")
                    val hostReady = json.optBoolean("hostReady")
                    val guestReady = json.optBoolean("guestReady")

                    _uiState.update {
                        it.copy(
                            wifiLobbyState = LobbyState(
                                roomName = roomName,
                                hostName = hostName,
                                guestName = guestName.takeIf { g -> g.isNotBlank() },
                                hostReady = hostReady,
                                guestReady = guestReady,
                                isConnected = true
                            )
                        )
                    }
                }
            }

            "LOBBY_SET_READY" -> {
                if (isHost) {
                    val ready = json.optBoolean("ready", false)
                    val updated = _uiState.value.wifiLobbyState?.copy(guestReady = ready)
                    if (updated != null) {
                        _uiState.update { it.copy(wifiLobbyState = updated) }
                        broadcastLobbyState(updated)
                    }
                }
            }

            "LOBBY_LEAVE" -> {
                val reason = json.optString("reason", "Peer left lobby.")
                if (isHost) {
                    // Guest left before match started -> host stays waiting in lobby for another guest
                    val current = _uiState.value.wifiLobbyState
                    if (current != null) {
                        val resetGuest = current.copy(guestName = null, guestReady = false, isConnected = false)
                        _uiState.update { it.copy(wifiLobbyState = resetGuest) }
                    }
                } else {
                    // Host closed lobby -> return guest to Home
                    peerConnection?.disconnect()
                    _uiState.update {
                        it.copy(
                            currentScreen = Screen.HOME,
                            wifiLobbyState = null,
                            infoToastMessage = reason
                        )
                    }
                }
            }

            "START_MATCH" -> {
                if (!isHost) {
                    peerConnection?.setMatchActive(true)
                    val matchId = json.getString("matchId")
                    val revision = json.getLong("revision")
                    val startingPlayer = PlayerTurn.valueOf(json.getString("startingPlayer"))
                    val p1Name = json.getString("player1Name")
                    val p2Name = json.getString("player2Name")
                    val catName = json.optString("matchCategory", MatchCategory.STANDARD.name)
                    val cat = MatchCategory.valueOf(catName)
                    val guestRack = NetworkPacketHelper.deserializeTileList(json.getJSONArray("guestRack"))
                    val board = NetworkPacketHelper.deserializeBoard(json.getJSONArray("board"))
                    val bagCount = json.getInt("bagCount")

                    val guestMatch = MatchState(
                        matchId = matchId,
                        revision = revision,
                        gameMode = GameMode.LOCAL_WIFI,
                        computerDifficulty = ComputerDifficulty.BALANCED,
                        player1Name = p1Name,
                        player2Name = p2Name,
                        board = board,
                        playerRack = guestRack, // Guest's private rack
                        computerRack = emptyList(), // Host rack is private and concealed
                        tileBag = List(bagCount) { Tile("dummy_${it}", ' ', 0) },
                        playerScore = 0,
                        computerScore = 0,
                        currentTurn = startingPlayer,
                        startingPlayer = startingPlayer,
                        status = MatchStatus.IN_PROGRESS,
                        computerName = p2Name,
                        matchCategory = cat,
                        moveHistory = emptyList(),
                        draftPlacements = emptyList(),
                        isHandoverPending = false
                    )

                    _uiState.update {
                        it.copy(
                            currentScreen = Screen.GAME,
                            matchState = guestMatch,
                            draftPlacements = emptyList(),
                            selectedRackTileId = null,
                            prospectiveScore = null,
                            validationError = null,
                            showResultsDialog = false,
                            isWifiActionPending = false
                        )
                    }
                }
            }

            "ACTION_SUBMIT" -> {
                if (isHost) {
                    handleGuestActionSubmission(json)
                }
            }

            "ACTION_ACK" -> {
                _uiState.update { it.copy(isWifiActionPending = false) }
            }

            "ACTION_REJECT" -> {
                val reason = json.optString("reason", "Move rejected.")
                _uiState.update {
                    it.copy(
                        isWifiActionPending = false,
                        validationError = reason,
                        boardShakeTrigger = System.currentTimeMillis()
                    )
                }
            }

            "STATE_SYNC" -> {
                if (!isHost) {
                    handleGuestStateSync(json)
                }
            }

            "LEAVE_MATCH" -> {
                val reason = json.optString("reason", "Player left the match.")
                handlePeerMatchTermination(reason)
            }
        }
    }

    private fun handlePeerConnectionState(state: PeerConnectionState) {
        _uiState.update { it.copy(peerConnectionState = state) }

        when (state) {
            is PeerConnectionState.Connected -> {
                if (!_uiState.value.isWifiHost) {
                    // Send HELLO handshake
                    peerConnection?.sendMessage(JSONObject().apply {
                        put("type", "HELLO")
                        put("protocolVersion", PROTOCOL_VERSION)
                        put("appVersion", APP_VERSION)
                        put("playerName", _uiState.value.wifiLobbyState?.guestName ?: "Guest")
                    })
                }
            }
            is PeerConnectionState.MatchEndedDisconnection -> {
                handlePeerMatchTermination(state.reason)
            }
            is PeerConnectionState.Disconnected -> {
                val match = _uiState.value.matchState
                if (match != null && match.gameMode == GameMode.LOCAL_WIFI && !match.isGameOver) {
                    handlePeerMatchTermination("Connection ended. This match has ended. Reconnect to start a new game.")
                }
            }
            is PeerConnectionState.Error -> {
                val match = _uiState.value.matchState
                if (match != null && match.gameMode == GameMode.LOCAL_WIFI && !match.isGameOver) {
                    handlePeerMatchTermination("Connection lost: ${state.message}")
                }
            }
            else -> {}
        }
    }

    private fun handlePeerMatchTermination(message: String) {
        val match = _uiState.value.matchState
        if (match != null && match.gameMode == GameMode.LOCAL_WIFI) {
            peerConnection?.setMatchActive(false)
            peerConnection?.disconnect()
            roomBroadcaster?.stop()

            _uiState.update {
                it.copy(
                    matchState = null,
                    draftPlacements = emptyList(),
                    isWifiActionPending = false,
                    showWifiDisconnectDialog = true,
                    wifiDisconnectMessage = message
                )
            }
        }
    }

    // --- Host Authoritative Action Handling ---

    private fun handleGuestActionSubmission(json: JSONObject) {
        val match = _uiState.value.matchState ?: return
        if (match.gameMode != GameMode.LOCAL_WIFI || match.isGameOver) return

        val actionId = json.getString("actionId")
        val matchId = json.getString("matchId")
        val actionTypeStr = json.getString("actionType")

        // 1. Validate session and idempotency
        if (match.matchId != matchId) {
            peerConnection?.sendMessage(JSONObject().apply {
                put("type", "ACTION_REJECT")
                put("actionId", actionId)
                put("reason", "Action does not match active session.")
            })
            return
        }

        if (processedActionIds.contains(actionId)) {
            // Already processed this action -> re-send ACK and latest state
            peerConnection?.sendMessage(JSONObject().apply {
                put("type", "ACTION_ACK")
                put("actionId", actionId)
                put("revision", match.revision)
            })
            sendAuthoritativeStateSync(match)
            return
        }

        // 2. Validate turn
        if (match.currentTurn != PlayerTurn.COMPUTER) {
            peerConnection?.sendMessage(JSONObject().apply {
                put("type", "ACTION_REJECT")
                put("actionId", actionId)
                put("reason", "It is not your turn.")
            })
            return
        }

        when (NetworkActionType.valueOf(actionTypeStr)) {
            NetworkActionType.PLAY_WORD -> {
                val placements = NetworkPacketHelper.deserializePlacements(json.getJSONArray("placements"))
                if (placements.isEmpty()) {
                    peerConnection?.sendMessage(JSONObject().apply {
                        put("type", "ACTION_REJECT")
                        put("actionId", actionId)
                        put("reason", "No placements submitted.")
                    })
                    return
                }

                // Verify Guest holds these tiles in computerRack
                val guestRack = match.computerRack.toMutableList()
                val usedTiles = mutableListOf<Tile>()
                for (p in placements) {
                    val matching = guestRack.find { it.id == p.tile.id }
                    if (matching == null) {
                        peerConnection?.sendMessage(JSONObject().apply {
                            put("type", "ACTION_REJECT")
                            put("actionId", actionId)
                            put("reason", "Tiles do not match your authoritative rack.")
                        })
                        return
                    }
                    guestRack.remove(matching)
                    usedTiles.add(matching)
                }

                // Validate on authoritative board
                val committedBoard = match.board.map { row ->
                    row.map { if (it.isDraft) it.copy(tile = null, isDraft = false) else it }
                }
                val validation = RuleValidator.validateAndScore(committedBoard, placements, dictionary, placements.size)
                if (validation !is ValidationResult.Valid) {
                    peerConnection?.sendMessage(JSONObject().apply {
                        put("type", "ACTION_REJECT")
                        put("actionId", actionId)
                        put("reason", (validation as ValidationResult.Invalid).reason)
                    })
                    return
                }

                // Commit action
                processedActionIds.add(actionId)
                val score = validation.breakdown.totalScore
                val newCompScore = match.computerScore + score

                val newBoard = match.board.map { row ->
                    row.map { cell ->
                        val placed = placements.find { it.row == cell.row && it.col == cell.col }
                        if (placed != null) {
                            cell.copy(tile = placed.tile, isDraft = false, isBonusConsumed = true)
                        } else cell
                    }
                }

                val bag = match.tileBag.toMutableList()
                while (guestRack.size < 7 && bag.isNotEmpty()) {
                    guestRack.add(bag.removeAt(0))
                }

                val primaryWord = validation.breakdown.words.firstOrNull { it.isMainWord }?.word
                val summary = "${match.player2Name} played $primaryWord for $score pts"

                val moveRecord = MoveRecord(
                    turnNumber = match.moveHistory.size + 1,
                    playerName = match.player2Name,
                    isHuman = true,
                    moveType = MoveType.PLAY,
                    word = primaryWord,
                    scoreGained = score,
                    scoreDetails = validation.breakdown.words.mapNotNull { it.detail },
                    bingoBonus = validation.breakdown.bingoBonus,
                    runningPlayerScore = newCompScore,
                    runningOpponentScore = match.playerScore
                )

                val updatedMatch = match.copy(
                    revision = match.revision + 1L,
                    board = newBoard,
                    computerRack = guestRack,
                    tileBag = bag,
                    computerScore = newCompScore,
                    currentTurn = PlayerTurn.HUMAN,
                    consecutiveSkips = 0,
                    lastMoveSummary = summary,
                    lastComputerMoveCells = placements.map { it.row to it.col },
                    moveHistory = match.moveHistory + moveRecord,
                    draftPlacements = emptyList()
                )

                _uiState.update { it.copy(matchState = updatedMatch) }

                peerConnection?.sendMessage(JSONObject().apply {
                    put("type", "ACTION_ACK")
                    put("actionId", actionId)
                    put("revision", updatedMatch.revision)
                })

                if (!checkMatchEndingConditions(updatedMatch)) {
                    sendAuthoritativeStateSync(updatedMatch)
                }
            }

            NetworkActionType.SWAP -> {
                val swappedIds = mutableListOf<String>()
                val arr = json.getJSONArray("swappedTileIds")
                for (i in 0 until arr.length()) swappedIds.add(arr.getString(i))

                if (match.tileBag.size < 7 || swappedIds.isEmpty()) {
                    peerConnection?.sendMessage(JSONObject().apply {
                        put("type", "ACTION_REJECT")
                        put("actionId", actionId)
                        put("reason", "Cannot swap tiles right now.")
                    })
                    return
                }

                processedActionIds.add(actionId)
                val bag = match.tileBag.toMutableList()
                val guestRack = match.computerRack.toMutableList()
                val swapped = guestRack.filter { swappedIds.contains(it.id) }
                guestRack.removeAll { swappedIds.contains(it.id) }

                repeat(swapped.size) {
                    if (bag.isNotEmpty()) guestRack.add(bag.removeAt(0))
                }
                bag.addAll(swapped)
                bag.shuffle()

                val moveRecord = MoveRecord(
                    turnNumber = match.moveHistory.size + 1,
                    playerName = match.player2Name,
                    isHuman = true,
                    moveType = MoveType.SWAP,
                    scoreGained = 0,
                    runningPlayerScore = match.computerScore,
                    runningOpponentScore = match.playerScore
                )

                val updatedMatch = match.copy(
                    revision = match.revision + 1L,
                    computerRack = guestRack,
                    tileBag = bag,
                    currentTurn = PlayerTurn.HUMAN,
                    consecutiveSkips = match.consecutiveSkips + 1,
                    lastMoveSummary = "${match.player2Name} swapped ${swapped.size} tiles",
                    moveHistory = match.moveHistory + moveRecord,
                    draftPlacements = emptyList()
                )

                _uiState.update { it.copy(matchState = updatedMatch) }

                peerConnection?.sendMessage(JSONObject().apply {
                    put("type", "ACTION_ACK")
                    put("actionId", actionId)
                    put("revision", updatedMatch.revision)
                })

                if (!checkMatchEndingConditions(updatedMatch)) {
                    sendAuthoritativeStateSync(updatedMatch)
                }
            }

            NetworkActionType.PASS -> {
                processedActionIds.add(actionId)
                val moveRecord = MoveRecord(
                    turnNumber = match.moveHistory.size + 1,
                    playerName = match.player2Name,
                    isHuman = true,
                    moveType = MoveType.PASS,
                    scoreGained = 0,
                    runningPlayerScore = match.computerScore,
                    runningOpponentScore = match.playerScore
                )

                val updatedMatch = match.copy(
                    revision = match.revision + 1L,
                    currentTurn = PlayerTurn.HUMAN,
                    consecutiveSkips = match.consecutiveSkips + 1,
                    lastMoveSummary = "${match.player2Name} passed",
                    moveHistory = match.moveHistory + moveRecord,
                    draftPlacements = emptyList()
                )

                _uiState.update { it.copy(matchState = updatedMatch) }

                peerConnection?.sendMessage(JSONObject().apply {
                    put("type", "ACTION_ACK")
                    put("actionId", actionId)
                    put("revision", updatedMatch.revision)
                })

                if (!checkMatchEndingConditions(updatedMatch)) {
                    sendAuthoritativeStateSync(updatedMatch)
                }
            }

            NetworkActionType.RESIGN -> {
                processedActionIds.add(actionId)
                val resignedMatch = match.copy(
                    revision = match.revision + 1L,
                    status = MatchStatus.FINISHED_RESIGNATION,
                    winnerName = match.player1Name,
                    draftPlacements = emptyList()
                )
                recordCompletedMatchSummary(resignedMatch)
                _uiState.update {
                    it.copy(
                        matchState = resignedMatch,
                        showResultsDialog = true
                    )
                }
                sendAuthoritativeStateSync(resignedMatch)
            }
        }
    }

    private fun sendAuthoritativeStateSync(match: MatchState) {
        val payload = JSONObject().apply {
            put("type", "STATE_SYNC")
            put("matchId", match.matchId)
            put("revision", match.revision)
            put("currentTurn", match.currentTurn.name)
            put("board", NetworkPacketHelper.serializeBoard(match.board))
            put("guestRack", NetworkPacketHelper.serializeTileList(match.computerRack))
            put("bagCount", match.tileBag.size)
            put("playerScore", match.playerScore)
            put("computerScore", match.computerScore)
            put("moveHistory", NetworkPacketHelper.serializeMoveHistory(match.moveHistory))
            put("lastMoveSummary", match.lastMoveSummary ?: "")
            put("status", match.status.name)
            put("winnerName", match.winnerName ?: "")
            put("consecutiveSkips", match.consecutiveSkips)
            put("playerRackDeduction", match.playerRackDeduction ?: -999)
            put("computerRackDeduction", match.computerRackDeduction ?: -999)
        }
        peerConnection?.sendMessage(payload)
    }

    private fun handleGuestStateSync(json: JSONObject) {
        val currentMatch = _uiState.value.matchState ?: return
        val revision = json.getLong("revision")
        if (revision < currentMatch.revision) return // Stale revision ignore

        val newTurn = PlayerTurn.valueOf(json.getString("currentTurn"))
        val newBoard = NetworkPacketHelper.deserializeBoard(json.getJSONArray("board"))
        val guestRack = NetworkPacketHelper.deserializeTileList(json.getJSONArray("guestRack"))
        val bagCount = json.getInt("bagCount")
        val pScore = json.getInt("playerScore")
        val cScore = json.getInt("computerScore")
        val history = NetworkPacketHelper.deserializeMoveHistory(json.getJSONArray("moveHistory"))
        val lastSummary = json.optString("lastMoveSummary").takeIf { it.isNotEmpty() }
        val status = MatchStatus.valueOf(json.getString("status"))
        val winner = json.optString("winnerName").takeIf { it.isNotEmpty() }
        val pDed = json.optInt("playerRackDeduction", -999).takeIf { it != -999 }
        val cDed = json.optInt("computerRackDeduction", -999).takeIf { it != -999 }

        val updatedMatch = currentMatch.copy(
            revision = revision,
            board = newBoard,
            playerRack = guestRack, // Guest's private rack
            tileBag = List(bagCount) { Tile("dummy_${it}", ' ', 0) },
            playerScore = pScore,
            computerScore = cScore,
            currentTurn = newTurn,
            moveHistory = history,
            lastMoveSummary = lastSummary,
            status = status,
            winnerName = winner,
            playerRackDeduction = pDed,
            computerRackDeduction = cDed,
            draftPlacements = emptyList()
        )

        _uiState.update {
            it.copy(
                matchState = updatedMatch,
                draftPlacements = emptyList(),
                selectedRackTileId = null,
                isWifiActionPending = false,
                showResultsDialog = status != MatchStatus.IN_PROGRESS
            )
        }

        if (status != MatchStatus.IN_PROGRESS) {
            recordCompletedMatchSummary(updatedMatch)
        }
    }

    // --- Tile Placement Interaction ---

    fun selectRackTile(tile: Tile) {
        val current = _uiState.value.selectedRackTileId
        _uiState.update {
            it.copy(selectedRackTileId = if (current == tile.id) null else tile.id)
        }
    }

    fun onCellClicked(row: Int, col: Int) {
        val match = _uiState.value.matchState ?: return
        if (_uiState.value.isWifiActionPending) return

        val drafts = _uiState.value.draftPlacements
        val cell = match.board[row][col]

        val existingDraft = drafts.find { it.row == row && it.col == col }
        if (existingDraft != null) {
            returnDraftTileToRack(existingDraft)
            return
        }

        if (cell.isCommitted) return

        val selectedId = _uiState.value.selectedRackTileId
        val activeRack = if (match.gameMode == GameMode.PASS_AND_PLAY && match.currentTurn == PlayerTurn.COMPUTER) {
            match.computerRack
        } else {
            match.playerRack
        }

        val tileToPlace = activeRack.find { it.id == selectedId }

        if (tileToPlace != null) {
            if (tileToPlace.isBlank && tileToPlace.assignedLetter == null) {
                _uiState.update {
                    it.copy(pendingBlankCoordinate = Pair(row, col))
                }
            } else {
                placeTileOnBoard(row, col, tileToPlace)
            }
        }
    }

    fun assignBlankLetter(letter: Char) {
        val coord = _uiState.value.pendingBlankCoordinate ?: return
        val match = _uiState.value.matchState ?: return
        val selectedId = _uiState.value.selectedRackTileId ?: return

        val activeRack = if (match.gameMode == GameMode.PASS_AND_PLAY && match.currentTurn == PlayerTurn.COMPUTER) {
            match.computerRack
        } else {
            match.playerRack
        }

        val blankTile = activeRack.find { it.id == selectedId } ?: return
        val configuredTile = blankTile.copy(assignedLetter = letter.uppercaseChar())

        _uiState.update { it.copy(pendingBlankCoordinate = null) }
        placeTileOnBoard(coord.first, coord.second, configuredTile)
    }

    fun dismissBlankDialog() {
        _uiState.update { it.copy(pendingBlankCoordinate = null) }
    }

    private fun placeTileOnBoard(row: Int, col: Int, tile: Tile) {
        val match = _uiState.value.matchState ?: return
        val currentDrafts = _uiState.value.draftPlacements.toMutableList()

        val isPlayer2Turn = match.gameMode == GameMode.PASS_AND_PLAY && match.currentTurn == PlayerTurn.COMPUTER
        val activeRack = if (isPlayer2Turn) match.computerRack.toMutableList() else match.playerRack.toMutableList()

        activeRack.removeAll { it.id == tile.id }
        val placed = PlacedTile(row, col, tile)
        currentDrafts.add(placed)

        val updatedBoard = updateBoardWithDrafts(match.board, currentDrafts)
        val updatedMatch = if (isPlayer2Turn) {
            match.copy(computerRack = activeRack, board = updatedBoard)
        } else {
            match.copy(playerRack = activeRack, board = updatedBoard)
        }

        _uiState.update {
            it.copy(
                matchState = updatedMatch,
                draftPlacements = currentDrafts,
                selectedRackTileId = null
            )
        }
        recalculateProspectiveScore(updatedMatch, currentDrafts)
    }

    private fun returnDraftTileToRack(placed: PlacedTile) {
        val match = _uiState.value.matchState ?: return
        val updatedDrafts = _uiState.value.draftPlacements.filterNot { it.row == placed.row && it.col == placed.col }

        val originalTile = if (placed.tile.isBlank) placed.tile.copy(assignedLetter = null) else placed.tile
        val isPlayer2Turn = match.gameMode == GameMode.PASS_AND_PLAY && match.currentTurn == PlayerTurn.COMPUTER
        val updatedBoard = updateBoardWithDrafts(match.board, updatedDrafts)

        val updatedMatch = if (isPlayer2Turn) {
            match.copy(computerRack = match.computerRack + originalTile, board = updatedBoard)
        } else {
            match.copy(playerRack = match.playerRack + originalTile, board = updatedBoard)
        }

        _uiState.update {
            it.copy(
                matchState = updatedMatch,
                draftPlacements = updatedDrafts,
                selectedRackTileId = null
            )
        }
        recalculateProspectiveScore(updatedMatch, updatedDrafts)
    }

    private fun recalculateProspectiveScore(match: MatchState, drafts: List<PlacedTile>) {
        if (drafts.isEmpty()) {
            _uiState.update { it.copy(prospectiveScore = null, validationError = null) }
            return
        }

        val committedBoard = match.board.map { row ->
            row.map { cell -> if (cell.isDraft) cell.copy(tile = null, isDraft = false) else cell }
        }

        when (val result = RuleValidator.validateAndScore(committedBoard, drafts, dictionary, drafts.size)) {
            is ValidationResult.Valid -> {
                val primaryWord = result.breakdown.words.firstOrNull { it.isMainWord }?.word
                    ?: result.breakdown.words.firstOrNull()?.word
                _uiState.update {
                    it.copy(
                        prospectiveScore = result.breakdown.totalScore,
                        prospectiveWord = primaryWord,
                        validationError = null
                    )
                }
            }
            is ValidationResult.Invalid -> {
                _uiState.update {
                    it.copy(
                        prospectiveScore = null,
                        prospectiveWord = null,
                        validationError = result.reason
                    )
                }
            }
        }
    }

    private fun updateBoardWithDrafts(
        originalBoard: List<List<BoardCell>>,
        drafts: List<PlacedTile>
    ): List<List<BoardCell>> {
        val draftMap = drafts.associateBy { it.row to it.col }
        return originalBoard.map { row ->
            row.map { cell ->
                val draft = draftMap[cell.row to cell.col]
                if (draft != null) {
                    cell.copy(tile = draft.tile, isDraft = true)
                } else if (cell.isDraft) {
                    cell.copy(tile = null, isDraft = false)
                } else cell
            }
        }
    }

    fun recallDraft() {
        val match = _uiState.value.matchState ?: return
        val drafts = _uiState.value.draftPlacements
        if (drafts.isEmpty()) return

        val isPlayer2Turn = match.gameMode == GameMode.PASS_AND_PLAY && match.currentTurn == PlayerTurn.COMPUTER
        val restoredTiles = drafts.map { if (it.tile.isBlank) it.tile.copy(assignedLetter = null) else it.tile }
        val restoredBoard = match.board.map { row ->
            row.map { if (it.isDraft) it.copy(tile = null, isDraft = false) else it }
        }

        val updatedMatch = if (isPlayer2Turn) {
            match.copy(computerRack = match.computerRack + restoredTiles, board = restoredBoard)
        } else {
            match.copy(playerRack = match.playerRack + restoredTiles, board = restoredBoard)
        }

        _uiState.update {
            it.copy(
                matchState = updatedMatch,
                draftPlacements = emptyList(),
                selectedRackTileId = null,
                prospectiveScore = null,
                prospectiveWord = null,
                validationError = null
            )
        }
    }

    fun shuffleRack() {
        val match = _uiState.value.matchState ?: return
        val isPlayer2Turn = match.gameMode == GameMode.PASS_AND_PLAY && match.currentTurn == PlayerTurn.COMPUTER
        val activeRack = if (isPlayer2Turn) match.computerRack.shuffled() else match.playerRack.shuffled()

        val updatedMatch = if (isPlayer2Turn) {
            match.copy(computerRack = activeRack)
        } else {
            match.copy(playerRack = activeRack)
        }

        _uiState.update { it.copy(matchState = updatedMatch, selectedRackTileId = null) }
    }

    fun dismissHandover() {
        _uiState.update { it.copy(isHandoverPending = false, selectedRackTileId = null) }
    }

    fun promptSwap() {
        recallDraft()
        _uiState.update { it.copy(showSwapDialog = true) }
    }

    fun dismissSwapDialog() {
        _uiState.update { it.copy(showSwapDialog = false) }
    }

    fun promptResign() {
        _uiState.update { it.copy(showResignConfirmDialog = true) }
    }

    fun dismissResignDialog() {
        _uiState.update { it.copy(showResignConfirmDialog = false) }
    }

    fun resignActiveMatch() {
        val match = _uiState.value.matchState ?: return
        dismissResignDialog()
        dismissSettings()

        if (match.gameMode == GameMode.LOCAL_WIFI) {
            if (!_uiState.value.isWifiHost) {
                // Guest submits RESIGN to Host
                val actionId = UUID.randomUUID().toString()
                val payload = JSONObject().apply {
                    put("type", "ACTION_SUBMIT")
                    put("actionId", actionId)
                    put("matchId", match.matchId)
                    put("actionType", NetworkActionType.RESIGN.name)
                }
                peerConnection?.sendMessage(payload)

                val guestResigned = match.copy(
                    status = MatchStatus.FINISHED_RESIGNATION,
                    winnerName = match.player1Name,
                    draftPlacements = emptyList()
                )
                recordCompletedMatchSummary(guestResigned)
                _uiState.update {
                    it.copy(
                        matchState = guestResigned,
                        showResultsDialog = true
                    )
                }
            } else {
                // Host resigns
                val hostResigned = match.copy(
                    revision = match.revision + 1L,
                    status = MatchStatus.FINISHED_RESIGNATION,
                    winnerName = match.player2Name,
                    draftPlacements = emptyList()
                )
                recordCompletedMatchSummary(hostResigned)
                _uiState.update {
                    it.copy(
                        matchState = hostResigned,
                        showResultsDialog = true
                    )
                }
                sendAuthoritativeStateSync(hostResigned)
            }
        } else {
            // Computer or Pass & Play
            val winner = if (match.gameMode == GameMode.PASS_AND_PLAY) {
                if (match.currentTurn == PlayerTurn.HUMAN) match.player2Name else match.player1Name
            } else {
                match.computerName
            }
            val resignedMatch = match.copy(
                status = MatchStatus.FINISHED_RESIGNATION,
                winnerName = winner,
                draftPlacements = emptyList()
            )
            recordCompletedMatchSummary(resignedMatch)
            repository.saveMatch(resignedMatch)
            _uiState.update {
                it.copy(
                    matchState = resignedMatch,
                    showResultsDialog = true
                )
            }
        }
    }

    fun executeSwap(selectedTileIds: List<String>) {
        val match = _uiState.value.matchState ?: return
        if (match.tileBag.size < 7 || selectedTileIds.isEmpty()) return

        if (match.gameMode == GameMode.LOCAL_WIFI && !_uiState.value.isWifiHost) {
            // Guest submits SWAP to Host
            val actionId = UUID.randomUUID().toString()
            val payload = JSONObject().apply {
                put("type", "ACTION_SUBMIT")
                put("actionId", actionId)
                put("matchId", match.matchId)
                put("actionType", NetworkActionType.SWAP.name)
                val arr = org.json.JSONArray()
                for (id in selectedTileIds) arr.put(id)
                put("swappedTileIds", arr)
            }
            _uiState.update { it.copy(isWifiActionPending = true, showSwapDialog = false) }
            peerConnection?.sendMessage(payload)
            return
        }

        val bag = match.tileBag.toMutableList()
        val isPlayer2Turn = match.gameMode == GameMode.PASS_AND_PLAY && match.currentTurn == PlayerTurn.COMPUTER
        val currentRack = (if (isPlayer2Turn) match.computerRack else match.playerRack).toMutableList()

        val tilesToSwap = currentRack.filter { selectedTileIds.contains(it.id) }
        currentRack.removeAll { selectedTileIds.contains(it.id) }

        repeat(tilesToSwap.size) {
            if (bag.isNotEmpty()) currentRack.add(bag.removeAt(0))
        }
        bag.addAll(tilesToSwap)
        bag.shuffle()

        val nextTurn = if (match.currentTurn == PlayerTurn.HUMAN) PlayerTurn.COMPUTER else PlayerTurn.HUMAN
        val nextSkips = match.consecutiveSkips + 1
        val actorName = if (match.currentTurn == PlayerTurn.HUMAN) match.player1Name else match.player2Name

        val moveRecord = MoveRecord(
            turnNumber = match.moveHistory.size + 1,
            playerName = actorName,
            isHuman = true,
            moveType = MoveType.SWAP,
            scoreGained = 0,
            runningPlayerScore = if (match.currentTurn == PlayerTurn.HUMAN) match.playerScore else match.computerScore,
            runningOpponentScore = if (match.currentTurn == PlayerTurn.HUMAN) match.computerScore else match.playerScore
        )

        val updatedMatch = if (isPlayer2Turn) {
            match.copy(
                revision = match.revision + 1L,
                computerRack = currentRack,
                tileBag = bag,
                currentTurn = nextTurn,
                consecutiveSkips = nextSkips,
                lastMoveSummary = "$actorName swapped ${tilesToSwap.size} tiles",
                moveHistory = match.moveHistory + moveRecord,
                draftPlacements = emptyList()
            )
        } else {
            match.copy(
                revision = match.revision + 1L,
                playerRack = currentRack,
                tileBag = bag,
                currentTurn = nextTurn,
                consecutiveSkips = nextSkips,
                lastMoveSummary = "$actorName swapped ${tilesToSwap.size} tiles",
                moveHistory = match.moveHistory + moveRecord,
                draftPlacements = emptyList()
            )
        }

        repository.saveMatch(updatedMatch)

        val isHandover = match.gameMode == GameMode.PASS_AND_PLAY
        _uiState.update {
            it.copy(
                matchState = updatedMatch,
                showSwapDialog = false,
                draftPlacements = emptyList(),
                selectedRackTileId = null,
                isHandoverPending = isHandover
            )
        }

        if (match.gameMode == GameMode.LOCAL_WIFI && _uiState.value.isWifiHost) {
            sendAuthoritativeStateSync(updatedMatch)
        }

        if (checkMatchEndingConditions(updatedMatch)) return

        if (match.gameMode == GameMode.COMPUTER && nextTurn == PlayerTurn.COMPUTER) {
            triggerComputerTurn(updatedMatch)
        }
    }

    fun passTurn() {
        recallDraft()
        val match = _uiState.value.matchState ?: return

        if (match.gameMode == GameMode.LOCAL_WIFI && !_uiState.value.isWifiHost) {
            // Guest submits PASS to Host
            val actionId = UUID.randomUUID().toString()
            val payload = JSONObject().apply {
                put("type", "ACTION_SUBMIT")
                put("actionId", actionId)
                put("matchId", match.matchId)
                put("actionType", NetworkActionType.PASS.name)
            }
            _uiState.update { it.copy(isWifiActionPending = true) }
            peerConnection?.sendMessage(payload)
            return
        }

        val nextTurn = if (match.currentTurn == PlayerTurn.HUMAN) PlayerTurn.COMPUTER else PlayerTurn.HUMAN
        val nextSkips = match.consecutiveSkips + 1
        val actorName = if (match.currentTurn == PlayerTurn.HUMAN) match.player1Name else match.player2Name

        val moveRecord = MoveRecord(
            turnNumber = match.moveHistory.size + 1,
            playerName = actorName,
            isHuman = true,
            moveType = MoveType.PASS,
            scoreGained = 0,
            runningPlayerScore = if (match.currentTurn == PlayerTurn.HUMAN) match.playerScore else match.computerScore,
            runningOpponentScore = if (match.currentTurn == PlayerTurn.HUMAN) match.computerScore else match.playerScore
        )

        val updatedMatch = match.copy(
            revision = match.revision + 1L,
            currentTurn = nextTurn,
            consecutiveSkips = nextSkips,
            lastMoveSummary = "$actorName passed turn",
            moveHistory = match.moveHistory + moveRecord,
            draftPlacements = emptyList()
        )

        repository.saveMatch(updatedMatch)

        val isHandover = match.gameMode == GameMode.PASS_AND_PLAY
        _uiState.update {
            it.copy(
                matchState = updatedMatch,
                draftPlacements = emptyList(),
                selectedRackTileId = null,
                isHandoverPending = isHandover
            )
        }

        if (match.gameMode == GameMode.LOCAL_WIFI && _uiState.value.isWifiHost) {
            sendAuthoritativeStateSync(updatedMatch)
        }

        if (checkMatchEndingConditions(updatedMatch)) return

        if (match.gameMode == GameMode.COMPUTER && nextTurn == PlayerTurn.COMPUTER) {
            triggerComputerTurn(updatedMatch)
        }
    }

    fun playWord() {
        val match = _uiState.value.matchState ?: return
        val drafts = _uiState.value.draftPlacements
        if (drafts.isEmpty()) {
            _uiState.update {
                it.copy(
                    boardShakeTrigger = System.currentTimeMillis(),
                    validationError = "Place tiles on the board first."
                )
            }
            return
        }

        if (match.gameMode == GameMode.LOCAL_WIFI && !_uiState.value.isWifiHost) {
            // Guest submits PLAY_WORD to Host
            val actionId = UUID.randomUUID().toString()
            val payload = JSONObject().apply {
                put("type", "ACTION_SUBMIT")
                put("actionId", actionId)
                put("matchId", match.matchId)
                put("actionType", NetworkActionType.PLAY_WORD.name)
                put("placements", NetworkPacketHelper.serializePlacements(drafts))
            }
            _uiState.update { it.copy(isWifiActionPending = true) }
            peerConnection?.sendMessage(payload)
            return
        }

        val committedBoard = match.board.map { row ->
            row.map { if (it.isDraft) it.copy(tile = null, isDraft = false) else it }
        }

        val validation = RuleValidator.validateAndScore(committedBoard, drafts, dictionary, drafts.size)
        if (validation !is ValidationResult.Valid) {
            val reason = (validation as ValidationResult.Invalid).reason
            _uiState.update {
                it.copy(
                    boardShakeTrigger = System.currentTimeMillis(),
                    validationError = reason
                )
            }
            return
        }

        val score = validation.breakdown.totalScore
        val isPlayer1 = match.currentTurn == PlayerTurn.HUMAN
        val isPlayer2Turn = match.gameMode == GameMode.PASS_AND_PLAY && !isPlayer1

        val newP1Score = if (isPlayer1) match.playerScore + score else match.playerScore
        val newP2Score = if (isPlayer2Turn) match.computerScore + score else match.computerScore

        val newBoard = match.board.map { row ->
            row.map { cell ->
                if (cell.isDraft) {
                    cell.copy(isDraft = false, isBonusConsumed = true)
                } else cell
            }
        }

        val bag = match.tileBag.toMutableList()
        val currentRack = (if (isPlayer2Turn) match.computerRack else match.playerRack).toMutableList()
        while (currentRack.size < 7 && bag.isNotEmpty()) {
            currentRack.add(bag.removeAt(0))
        }

        val primaryWord = validation.breakdown.words.firstOrNull { it.isMainWord }?.word
        val actorName = if (isPlayer1) match.player1Name else match.player2Name
        val summary = "$actorName played $primaryWord for $score pts"

        val moveRecord = MoveRecord(
            turnNumber = match.moveHistory.size + 1,
            playerName = actorName,
            isHuman = true,
            moveType = MoveType.PLAY,
            word = primaryWord,
            scoreGained = score,
            scoreDetails = validation.breakdown.words.mapNotNull { it.detail },
            bingoBonus = validation.breakdown.bingoBonus,
            runningPlayerScore = if (isPlayer1) newP1Score else newP2Score,
            runningOpponentScore = if (isPlayer1) newP2Score else newP1Score
        )

        val nextTurn = if (isPlayer1) PlayerTurn.COMPUTER else PlayerTurn.HUMAN
        val isHandover = match.gameMode == GameMode.PASS_AND_PLAY

        val updatedMatch = if (isPlayer2Turn) {
            match.copy(
                revision = match.revision + 1L,
                board = newBoard,
                computerRack = currentRack,
                tileBag = bag,
                computerScore = newP2Score,
                currentTurn = nextTurn,
                consecutiveSkips = 0,
                lastMoveSummary = summary,
                lastComputerMoveCells = emptyList(),
                moveHistory = match.moveHistory + moveRecord,
                draftPlacements = emptyList()
            )
        } else {
            match.copy(
                revision = match.revision + 1L,
                board = newBoard,
                playerRack = currentRack,
                tileBag = bag,
                playerScore = newP1Score,
                currentTurn = nextTurn,
                consecutiveSkips = 0,
                lastMoveSummary = summary,
                lastComputerMoveCells = emptyList(),
                moveHistory = match.moveHistory + moveRecord,
                draftPlacements = emptyList()
            )
        }

        repository.saveMatch(updatedMatch)

        _uiState.update {
            it.copy(
                matchState = updatedMatch,
                draftPlacements = emptyList(),
                selectedRackTileId = null,
                prospectiveScore = null,
                validationError = null,
                isHandoverPending = isHandover
            )
        }

        if (match.gameMode == GameMode.LOCAL_WIFI && _uiState.value.isWifiHost) {
            sendAuthoritativeStateSync(updatedMatch)
        }

        if (checkMatchEndingConditions(updatedMatch)) return

        if (match.gameMode == GameMode.COMPUTER && nextTurn == PlayerTurn.COMPUTER) {
            triggerComputerTurn(updatedMatch)
        }
    }

    fun resignMatch() {
        val match = _uiState.value.matchState ?: return

        if (match.gameMode == GameMode.LOCAL_WIFI && !_uiState.value.isWifiHost) {
            // Guest submits RESIGN to Host
            val actionId = UUID.randomUUID().toString()
            val payload = JSONObject().apply {
                put("type", "ACTION_SUBMIT")
                put("actionId", actionId)
                put("matchId", match.matchId)
                put("actionType", NetworkActionType.RESIGN.name)
            }
            _uiState.update { it.copy(isWifiActionPending = true) }
            peerConnection?.sendMessage(payload)
            return
        }

        val resignedMatch = match.copy(
            revision = match.revision + 1L,
            status = MatchStatus.FINISHED_RESIGNATION,
            winnerName = match.player2Name,
            draftPlacements = emptyList()
        )
        recordCompletedMatchSummary(resignedMatch)
        repository.saveMatch(resignedMatch)

        _uiState.update {
            it.copy(
                matchState = resignedMatch,
                isComputerThinking = false,
                showResultsDialog = true
            )
        }

        if (match.gameMode == GameMode.LOCAL_WIFI && _uiState.value.isWifiHost) {
            sendAuthoritativeStateSync(resignedMatch)
        }
    }

    private fun checkMatchEndingConditions(match: MatchState): Boolean {
        val bagEmpty = match.tileBag.isEmpty()
        val playerRackEmpty = match.playerRack.isEmpty()
        val computerRackEmpty = match.computerRack.isEmpty()
        val sixSkips = match.consecutiveSkips >= 6

        if ((bagEmpty && (playerRackEmpty || computerRackEmpty)) || sixSkips) {
            endMatchNormally(match, playerCleared = playerRackEmpty, computerCleared = computerRackEmpty)
            return true
        }
        return false
    }

    private fun endMatchNormally(match: MatchState, playerCleared: Boolean, computerCleared: Boolean) {
        val playerRemainingVal = match.playerRack.sumOf { it.scoreValue }
        val compRemainingVal = match.computerRack.sumOf { it.scoreValue }

        var finalPlayerScore = match.playerScore - playerRemainingVal
        var finalCompScore = match.computerScore - compRemainingVal

        if (playerCleared) {
            finalPlayerScore += compRemainingVal
        } else if (computerCleared) {
            finalCompScore += playerRemainingVal
        }

        val winner = when {
            finalPlayerScore > finalCompScore -> match.player1Name
            finalCompScore > finalPlayerScore -> match.player2Name
            else -> "Draw"
        }

        val finishedMatch = match.copy(
            revision = match.revision + 1L,
            playerScore = finalPlayerScore,
            computerScore = finalCompScore,
            status = MatchStatus.FINISHED_NORMAL,
            playerRackDeduction = if (playerCleared) compRemainingVal else -playerRemainingVal,
            computerRackDeduction = if (computerCleared) playerRemainingVal else -compRemainingVal,
            winnerName = winner,
            draftPlacements = emptyList()
        )

        recordCompletedMatchSummary(finishedMatch)
        repository.saveMatch(finishedMatch)

        _uiState.update {
            it.copy(
                matchState = finishedMatch,
                isComputerThinking = false,
                showResultsDialog = true
            )
        }

        if (match.gameMode == GameMode.LOCAL_WIFI && _uiState.value.isWifiHost) {
            sendAuthoritativeStateSync(finishedMatch)
        }
    }

    private fun recordCompletedMatchSummary(match: MatchState) {
        val p1Moves = match.moveHistory.filter { it.playerName == match.player1Name && it.word != null }
        val bestMove = p1Moves.maxByOrNull { it.scoreGained }

        val summary = MatchSummary(
            matchId = match.matchId,
            gameMode = match.gameMode,
            difficulty = if (match.gameMode == GameMode.COMPUTER) match.computerDifficulty else null,
            timestamp = System.currentTimeMillis(),
            player1Name = match.player1Name,
            player2Name = match.player2Name,
            player1Score = match.playerScore,
            player2Score = match.computerScore,
            winnerName = match.winnerName,
            totalTurns = match.moveHistory.size,
            bestWord = bestMove?.word,
            bestWordScore = bestMove?.scoreGained ?: 0
        )
        statsRepository.recordMatch(summary)
    }

    // --- Intentional Leaving & Disconnection Dialogs ---

    fun promptLeaveWifiMatch() {
        _uiState.update { it.copy(showWifiLeaveConfirmDialog = true) }
    }

    fun dismissWifiLeaveConfirmDialog() {
        _uiState.update { it.copy(showWifiLeaveConfirmDialog = false) }
    }

    fun leaveLocalWifiMatch() {
        peerConnection?.sendLeaveNotification()
        peerConnection?.setMatchActive(false)
        peerConnection?.disconnect()
        roomBroadcaster?.stop()

        _uiState.update {
            it.copy(
                currentScreen = Screen.HOME,
                matchState = null,
                draftPlacements = emptyList(),
                showWifiLeaveConfirmDialog = false,
                isWifiActionPending = false
            )
        }
    }

    fun handleAppBackgroundedWifiMatch() {
        val match = _uiState.value.matchState
        if (match != null && match.gameMode == GameMode.LOCAL_WIFI && !match.isGameOver) {
            leaveLocalWifiMatch()
        }
    }

    fun dismissWifiDisconnectDialog() {
        _uiState.update {
            it.copy(
                showWifiDisconnectDialog = false,
                currentScreen = Screen.HOME,
                matchState = null
            )
        }
    }

    fun dismissLegacyWifiDiscardedDialog() {
        repository.clearLegacyWifiMatchDiscardedNotice()
        _uiState.update { it.copy(showLegacyWifiDiscardedDialog = false) }
    }

    // --- Computer AI Player Logic ---

    private fun triggerComputerTurn(match: MatchState) {
        computerTurnJob?.cancel()
        _uiState.update { it.copy(isComputerThinking = true) }

        computerTurnJob = viewModelScope.launch {
            delay(1200L) // Realistic thinking pause
            val committedBoard = match.board.map { row ->
                row.map { if (it.isDraft) it.copy(tile = null, isDraft = false) else it }
            }

            val move = ComputerPlayer.findMove(
                committedBoard = committedBoard,
                computerRack = match.computerRack,
                dictionary = dictionary,
                tileBagCount = match.tileBag.size,
                difficulty = match.computerDifficulty
            )

            executeComputerMove(move)
        }
    }

    private fun executeComputerMove(move: ComputerMove) {
        val currentMatch = _uiState.value.matchState ?: return
        if (currentMatch.currentTurn != PlayerTurn.COMPUTER || currentMatch.isGameOver) {
            _uiState.update { it.copy(isComputerThinking = false) }
            return
        }

        when {
            move.placements.isNotEmpty() -> {
                val newBoard = currentMatch.board.map { row ->
                    row.map { cell ->
                        val p = move.placements.find { it.row == cell.row && it.col == cell.col }
                        if (p != null) cell.copy(tile = p.tile, isDraft = false, isBonusConsumed = true) else cell
                    }
                }

                val remainingRack = currentMatch.computerRack.toMutableList()
                for (p in move.placements) {
                    val idx = remainingRack.indexOfFirst { it.id == p.tile.id }
                    if (idx >= 0) remainingRack.removeAt(idx)
                }

                val bag = currentMatch.tileBag.toMutableList()
                while (remainingRack.size < 7 && bag.isNotEmpty()) {
                    remainingRack.add(bag.removeAt(0))
                }

                val score = move.breakdown?.totalScore ?: 0
                val newCompScore = currentMatch.computerScore + score
                val primaryWord = move.breakdown?.words?.firstOrNull { it.isMainWord }?.word ?: ""
                val moveSummary = "${currentMatch.computerName} played $primaryWord for $score pts"

                val moveRecord = MoveRecord(
                    turnNumber = currentMatch.moveHistory.size + 1,
                    playerName = currentMatch.computerName,
                    isHuman = false,
                    moveType = MoveType.PLAY,
                    word = primaryWord,
                    scoreGained = score,
                    scoreDetails = move.breakdown?.words?.mapNotNull { it.detail } ?: emptyList(),
                    bingoBonus = move.breakdown?.bingoBonus ?: 0,
                    runningPlayerScore = newCompScore,
                    runningOpponentScore = currentMatch.playerScore
                )

                val updatedMatch = currentMatch.copy(
                    board = newBoard,
                    computerRack = remainingRack,
                    tileBag = bag,
                    computerScore = newCompScore,
                    currentTurn = PlayerTurn.HUMAN,
                    consecutiveSkips = 0,
                    lastMoveSummary = moveSummary,
                    lastComputerMoveCells = move.placements.map { it.row to it.col },
                    moveHistory = currentMatch.moveHistory + moveRecord,
                    draftPlacements = emptyList()
                )

                repository.saveMatch(updatedMatch)
                _uiState.update { it.copy(matchState = updatedMatch, isComputerThinking = false) }
                checkMatchEndingConditions(updatedMatch)
            }

            move.isSwap -> {
                val bag = currentMatch.tileBag.toMutableList()
                val currentRack = currentMatch.computerRack.toMutableList()
                val swapped = currentRack.filter { move.swappedTileIds.contains(it.id) }
                currentRack.removeAll { move.swappedTileIds.contains(it.id) }

                repeat(swapped.size) {
                    if (bag.isNotEmpty()) currentRack.add(bag.removeAt(0))
                }
                bag.addAll(swapped)
                bag.shuffle()

                val moveRecord = MoveRecord(
                    turnNumber = currentMatch.moveHistory.size + 1,
                    playerName = currentMatch.computerName,
                    isHuman = false,
                    moveType = MoveType.SWAP,
                    scoreGained = 0,
                    runningPlayerScore = currentMatch.computerScore,
                    runningOpponentScore = currentMatch.playerScore
                )

                val updatedMatch = currentMatch.copy(
                    computerRack = currentRack,
                    tileBag = bag,
                    currentTurn = PlayerTurn.HUMAN,
                    consecutiveSkips = currentMatch.consecutiveSkips + 1,
                    lastMoveSummary = "${currentMatch.computerName} swapped tiles",
                    moveHistory = currentMatch.moveHistory + moveRecord,
                    draftPlacements = emptyList()
                )

                repository.saveMatch(updatedMatch)
                _uiState.update { it.copy(matchState = updatedMatch, isComputerThinking = false) }
                checkMatchEndingConditions(updatedMatch)
            }

            else -> {
                val moveRecord = MoveRecord(
                    turnNumber = currentMatch.moveHistory.size + 1,
                    playerName = currentMatch.computerName,
                    isHuman = false,
                    moveType = MoveType.PASS,
                    scoreGained = 0,
                    runningPlayerScore = currentMatch.computerScore,
                    runningOpponentScore = currentMatch.playerScore
                )

                val updatedMatch = currentMatch.copy(
                    currentTurn = PlayerTurn.HUMAN,
                    consecutiveSkips = currentMatch.consecutiveSkips + 1,
                    lastMoveSummary = "${currentMatch.computerName} passed",
                    moveHistory = currentMatch.moveHistory + moveRecord,
                    draftPlacements = emptyList()
                )

                repository.saveMatch(updatedMatch)
                _uiState.update { it.copy(matchState = updatedMatch, isComputerThinking = false) }
                checkMatchEndingConditions(updatedMatch)
            }
        }
    }

    fun openSettings() = _uiState.update { it.copy(showSettingsDialog = true) }
    fun dismissSettings() = _uiState.update { it.copy(showSettingsDialog = false) }

    fun saveSettings(settings: GameSettings) {
        repository.saveSettings(settings)
        _uiState.update { it.copy(settings = settings, showSettingsDialog = false) }
    }

    fun openHowToPlay() = _uiState.update { it.copy(showHowToPlayDialog = true) }
    fun dismissHowToPlay() = _uiState.update { it.copy(showHowToPlayDialog = false) }

    fun showBonusInfo(info: String) {
        _uiState.update { it.copy(infoToastMessage = info) }
    }

    fun clearInfoToast() {
        _uiState.update { it.copy(infoToastMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        roomBroadcaster?.stop()
        roomScanner?.stop()
        peerConnection?.disconnect()
    }
}
