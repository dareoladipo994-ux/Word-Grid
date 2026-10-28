package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.BoardCell
import com.example.data.model.ComputerDifficulty
import com.example.data.model.GameMode
import com.example.data.model.MatchCategory
import com.example.data.model.MatchState
import com.example.data.model.MatchStatus
import com.example.data.model.PlacedTile
import com.example.data.model.PlayerTurn
import com.example.data.model.Tile
import com.example.network.DiscoveredRoom
import com.example.network.NetworkPacketHelper
import com.example.network.PROTOCOL_VERSION
import com.example.persistence.MatchRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalWifiMultiplayerTest {

    private lateinit var context: Context
    private lateinit var repository: MatchRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = MatchRepository(context)
        repository.deleteSavedMatch()
        repository.clearLegacyWifiMatchDiscardedNotice()
    }

    private fun createSampleMatch(mode: GameMode): MatchState {
        return MatchState(
            matchId = UUID.randomUUID().toString(),
            revision = 1L,
            gameMode = mode,
            computerDifficulty = ComputerDifficulty.BALANCED,
            player1Name = "Alice",
            player2Name = "Bob",
            board = List(13) { r -> List(13) { c -> BoardCell(row = r, col = c) } },
            playerRack = listOf(Tile("t1", 'A', 1)),
            computerRack = listOf(Tile("t2", 'B', 3)),
            tileBag = listOf(Tile("t3", 'C', 3)),
            playerScore = 12,
            computerScore = 8,
            currentTurn = PlayerTurn.HUMAN,
            startingPlayer = PlayerTurn.HUMAN,
            status = MatchStatus.IN_PROGRESS,
            computerName = "Bob",
            matchCategory = MatchCategory.STANDARD
        )
    }

    @Test
    fun testLocalWifiMatchNeverPersistedToDisk() {
        val wifiMatch = createSampleMatch(GameMode.LOCAL_WIFI)
        repository.saveMatch(wifiMatch)

        // Must NOT save to disk
        assertFalse(repository.hasSavedMatch())
        assertNull(repository.loadMatch())
    }

    @Test
    fun testComputerAndPassAndPlayMatchesArePreserved() {
        val computerMatch = createSampleMatch(GameMode.COMPUTER)
        repository.saveMatch(computerMatch)

        assertTrue(repository.hasSavedMatch())
        val loaded = repository.loadMatch()
        assertNotNull(loaded)
        assertEquals(GameMode.COMPUTER, loaded?.gameMode)
        assertEquals("Alice", loaded?.player1Name)

        val passMatch = createSampleMatch(GameMode.PASS_AND_PLAY)
        repository.saveMatch(passMatch)

        assertTrue(repository.hasSavedMatch())
        val loadedPass = repository.loadMatch()
        assertNotNull(loadedPass)
        assertEquals(GameMode.PASS_AND_PLAY, loadedPass?.gameMode)
    }

    @Test
    fun testLegacySavedWifiMatchIsDiscardedWithNotice() {
        // Manually write a legacy saved match with gameMode = LOCAL_WIFI
        val matchFile = File(context.filesDir, "wordgrid_saved_match.json")
        val json = """
            {
               "version": 2,
               "matchId": "legacy-123",
               "gameMode": "LOCAL_WIFI",
               "computerDifficulty": "BALANCED",
               "computerName": "Guest",
               "playerScore": 10,
               "computerScore": 5,
               "currentTurn": "HUMAN",
               "startingPlayer": "HUMAN",
               "status": "IN_PROGRESS",
               "board": [],
               "playerRack": [],
               "computerRack": [],
               "tileBag": []
            }
        """.trimIndent()
        matchFile.writeText(json)

        assertTrue(matchFile.exists())

        // hasSavedMatch() should detect it is LOCAL_WIFI, delete it, and flag notice
        assertFalse(repository.hasSavedMatch())
        assertFalse(matchFile.exists())
        assertTrue(repository.isLegacyWifiMatchDiscardedNoticePending())

        repository.clearLegacyWifiMatchDiscardedNotice()
        assertFalse(repository.isLegacyWifiMatchDiscardedNoticePending())
    }

    @Test
    fun testNetworkPacketHelperTileAndBoardSerialization() {
        val tile = Tile("tile_x", 'W', 4, isBlank = false, assignedLetter = null)
        val json = NetworkPacketHelper.serializeTile(tile)
        val restored = NetworkPacketHelper.deserializeTile(json)
        assertEquals(tile.id, restored.id)
        assertEquals(tile.letter, restored.letter)
        assertEquals(tile.value, restored.value)

        val cell = BoardCell(row = 3, col = 5, tile = tile, isDraft = false, isBonusConsumed = true)
        val board = listOf(listOf(cell))
        val boardJson = NetworkPacketHelper.serializeBoard(board)
        val restoredBoard = NetworkPacketHelper.deserializeBoard(boardJson)
        assertEquals(1, restoredBoard.size)
        assertEquals('W', restoredBoard[0][0].tile?.letter)
        assertEquals(3, restoredBoard[0][0].row)
        assertEquals(5, restoredBoard[0][0].col)
    }

    @Test
    fun testDiscoveredRoomCompatibility() {
        val compatibleRoom = DiscoveredRoom(
            roomName = "Alpha",
            hostName = "Host1",
            hostIp = "192.168.1.50",
            port = 8988,
            protocolVersion = PROTOCOL_VERSION,
            appVersion = "1.0",
            isFull = false,
            isInMatch = false
        )
        assertTrue(compatibleRoom.isCompatible)

        val incompatibleRoom = compatibleRoom.copy(protocolVersion = 999)
        assertFalse(incompatibleRoom.isCompatible)

        val json = compatibleRoom.toJson()
        val fromJson = DiscoveredRoom.fromJson(json)
        assertNotNull(fromJson)
        assertEquals("Alpha", fromJson?.roomName)
        assertEquals("192.168.1.50", fromJson?.hostIp)
    }
}
