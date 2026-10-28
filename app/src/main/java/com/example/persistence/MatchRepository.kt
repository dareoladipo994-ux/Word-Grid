package com.example.persistence

import android.content.Context
import com.example.data.model.BoardCell
import com.example.data.model.BonusCategory
import com.example.data.model.BonusType
import com.example.data.model.ComputerDifficulty
import com.example.data.model.GameMode
import com.example.data.model.GameSettings
import com.example.data.model.LetterScoreDetail
import com.example.data.model.MatchCategory
import com.example.data.model.MatchState
import com.example.data.model.MatchStatus
import com.example.data.model.MoveRecord
import com.example.data.model.MoveType
import com.example.data.model.PlacedTile
import com.example.data.model.PlayerTurn
import com.example.data.model.Tile
import com.example.data.model.WordScoreDetail
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MatchRepository(private val context: Context) {
    private val matchFile = File(context.filesDir, "wordgrid_saved_match.json")
    private val prefs = context.getSharedPreferences("wordgrid_settings", Context.MODE_PRIVATE)

    companion object {
        const val SAVE_VERSION = 2
    }

    fun hasSavedMatch(): Boolean {
        if (!matchFile.exists() || matchFile.length() == 0L) return false
        return try {
            val content = matchFile.readText()
            val root = JSONObject(content)
            val modeStr = root.optString("gameMode", "")
            if (modeStr == GameMode.LOCAL_WIFI.name) {
                matchFile.delete()
                setLegacyWifiMatchDiscardedNotice(true)
                false
            } else {
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun isLegacyWifiMatchDiscardedNoticePending(): Boolean {
        return prefs.getBoolean("legacy_wifi_notice_pending", false)
    }

    fun clearLegacyWifiMatchDiscardedNotice() {
        prefs.edit().putBoolean("legacy_wifi_notice_pending", false).apply()
    }

    private fun setLegacyWifiMatchDiscardedNotice(value: Boolean) {
        prefs.edit().putBoolean("legacy_wifi_notice_pending", value).apply()
    }

    @Synchronized
    fun saveMatch(match: MatchState) {
        // Local Wi-Fi matches are strictly temporary and in-memory. DO NOT PERSIST.
        if (match.gameMode == GameMode.LOCAL_WIFI) {
            return
        }
        try {
            val root = JSONObject()
            root.put("version", SAVE_VERSION)
            root.put("matchId", match.matchId)
            root.put("revision", match.revision)
            root.put("gameMode", match.gameMode.name)
            root.put("computerDifficulty", match.computerDifficulty.name)
            root.put("player1Name", match.player1Name)
            root.put("player2Name", match.player2Name)
            root.put("playerScore", match.playerScore)
            root.put("computerScore", match.computerScore)
            root.put("currentTurn", match.currentTurn.name)
            root.put("startingPlayer", match.startingPlayer.name)
            root.put("status", match.status.name)
            root.put("computerName", match.computerName)
            root.put("consecutiveSkips", match.consecutiveSkips)
            root.put("matchCategory", match.matchCategory.name)
            root.put("lastMoveSummary", match.lastMoveSummary ?: "")
            root.put("playerRackDeduction", match.playerRackDeduction ?: -999)
            root.put("computerRackDeduction", match.computerRackDeduction ?: -999)
            root.put("winnerName", match.winnerName ?: "")
            root.put("isHandoverPending", match.isHandoverPending)

            // Serialize computer move cells
            val lastCellsArray = JSONArray()
            for ((r, c) in match.lastComputerMoveCells) {
                val cObj = JSONObject()
                cObj.put("r", r)
                cObj.put("c", c)
                lastCellsArray.put(cObj)
            }
            root.put("lastComputerMoveCells", lastCellsArray)

            // Serialize Board
            val boardArray = JSONArray()
            for (row in match.board) {
                val rowArray = JSONArray()
                for (cell in row) {
                    val cellObj = JSONObject()
                    cellObj.put("r", cell.row)
                    cellObj.put("c", cell.col)
                    cellObj.put("isCenter", cell.isCenter)
                    cellObj.put("isBonusConsumed", cell.isBonusConsumed)

                    if (cell.bonus != null) {
                        val bObj = JSONObject()
                        bObj.put("cat", cell.bonus.category.name)
                        bObj.put("mult", cell.bonus.multiplier)
                        cellObj.put("bonus", bObj)
                    }

                    if (cell.tile != null && cell.isCommitted) {
                        val tObj = serializeTile(cell.tile)
                        cellObj.put("tile", tObj)
                    }
                    rowArray.put(cellObj)
                }
                boardArray.put(rowArray)
            }
            root.put("board", boardArray)

            // Serialize Racks and Bag
            root.put("playerRack", serializeTileList(match.playerRack))
            root.put("computerRack", serializeTileList(match.computerRack))
            root.put("tileBag", serializeTileList(match.tileBag))

            // Serialize Draft Placements
            val draftsArray = JSONArray()
            for (dp in match.draftPlacements) {
                val dObj = JSONObject()
                dObj.put("r", dp.row)
                dObj.put("c", dp.col)
                dObj.put("tile", serializeTile(dp.tile))
                draftsArray.put(dObj)
            }
            root.put("draftPlacements", draftsArray)

            // Serialize Move History
            val historyArray = JSONArray()
            for (m in match.moveHistory) {
                val mObj = JSONObject()
                mObj.put("turnNumber", m.turnNumber)
                mObj.put("playerName", m.playerName)
                mObj.put("isHuman", m.isHuman)
                mObj.put("moveType", m.moveType.name)
                mObj.put("word", m.word ?: "")
                mObj.put("scoreGained", m.scoreGained)
                mObj.put("bingoBonus", m.bingoBonus)
                mObj.put("runningPlayerScore", m.runningPlayerScore)
                mObj.put("runningOpponentScore", m.runningOpponentScore)
                mObj.put("timestamp", m.timestamp)

                val detailsArray = JSONArray()
                for (wd in m.scoreDetails) {
                    val wdObj = JSONObject()
                    wdObj.put("word", wd.word)
                    wdObj.put("wordMultiplier", wd.wordMultiplier)
                    wdObj.put("score", wd.score)
                    wdObj.put("isMainWord", wd.isMainWord)

                    val lettersArray = JSONArray()
                    for (l in wd.letters) {
                        val lObj = JSONObject()
                        lObj.put("letter", l.letter.toString())
                        lObj.put("baseValue", l.baseValue)
                        lObj.put("multiplier", l.multiplier)
                        lObj.put("isWordMultiplier", l.isWordMultiplier)
                        lObj.put("isCommittedAlready", l.isCommittedAlready)
                        lettersArray.put(lObj)
                    }
                    wdObj.put("letters", lettersArray)
                    detailsArray.put(wdObj)
                }
                mObj.put("scoreDetails", detailsArray)
                historyArray.put(mObj)
            }
            root.put("moveHistory", historyArray)

            // Atomic file write using temporary file
            val tempFile = File(context.filesDir, "wordgrid_saved_match.tmp")
            tempFile.writeText(root.toString())
            if (tempFile.exists()) {
                if (matchFile.exists()) matchFile.delete()
                tempFile.renameTo(matchFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun loadMatch(): MatchState? {
        if (!hasSavedMatch()) return null
        return try {
            val content = matchFile.readText()
            val root = JSONObject(content)
            val version = root.optInt("version", 1)

            // Migration: if saving from version 1, create backup file .bak
            if (version < 2) {
                try {
                    val backupFile = File(context.filesDir, "wordgrid_saved_match.v1.bak")
                    backupFile.writeText(content)
                } catch (_: Exception) {}
            }

            val matchId = root.getString("matchId")
            val revision = root.optLong("revision", 0L)
            val gameMode = GameMode.valueOf(root.optString("gameMode", GameMode.COMPUTER.name))
            if (gameMode == GameMode.LOCAL_WIFI) {
                deleteSavedMatch()
                setLegacyWifiMatchDiscardedNotice(true)
                return null
            }
            val difficulty = ComputerDifficulty.valueOf(
                root.optString("computerDifficulty", ComputerDifficulty.BALANCED.name)
            )
            val computerName = root.getString("computerName")
            val player1Name = root.optString("player1Name", "You")
            val player2Name = root.optString("player2Name", computerName)

            val playerScore = root.getInt("playerScore")
            val computerScore = root.getInt("computerScore")
            val currentTurn = PlayerTurn.valueOf(root.getString("currentTurn"))
            val startingPlayer = PlayerTurn.valueOf(root.getString("startingPlayer"))
            val status = MatchStatus.valueOf(root.getString("status"))
            val consecutiveSkips = root.optInt("consecutiveSkips", 0)
            val matchCategory = MatchCategory.valueOf(root.optString("matchCategory", MatchCategory.STANDARD.name))
            val lastMoveSummary = root.optString("lastMoveSummary").takeIf { it.isNotEmpty() }

            val pDed = root.optInt("playerRackDeduction", -999).takeIf { it != -999 }
            val cDed = root.optInt("computerRackDeduction", -999).takeIf { it != -999 }
            val winnerName = root.optString("winnerName").takeIf { it.isNotEmpty() }
            val isHandoverPending = root.optBoolean("isHandoverPending", false)

            val lastCellsArray = root.optJSONArray("lastComputerMoveCells")
            val lastCells = mutableListOf<Pair<Int, Int>>()
            if (lastCellsArray != null) {
                for (i in 0 until lastCellsArray.length()) {
                    val obj = lastCellsArray.getJSONObject(i)
                    lastCells.add(obj.getInt("r") to obj.getInt("c"))
                }
            }

            // Deserialize Board
            val boardArray = root.getJSONArray("board")
            val board = mutableListOf<List<BoardCell>>()
            for (r in 0 until boardArray.length()) {
                val rowArray = boardArray.getJSONArray(r)
                val rowCells = mutableListOf<BoardCell>()
                for (c in 0 until rowArray.length()) {
                    val cellObj = rowArray.getJSONObject(c)
                    val isCenter = cellObj.optBoolean("isCenter", false)
                    val isBonusConsumed = cellObj.optBoolean("isBonusConsumed", false)

                    val bonus = if (cellObj.has("bonus")) {
                        val bObj = cellObj.getJSONObject("bonus")
                        BonusType(BonusCategory.valueOf(bObj.getString("cat")), bObj.getInt("mult"))
                    } else null

                    val tile = if (cellObj.has("tile")) {
                        deserializeTile(cellObj.getJSONObject("tile"))
                    } else null

                    rowCells.add(
                        BoardCell(
                            row = r,
                            col = c,
                            bonus = bonus,
                            tile = tile,
                            isCenter = isCenter,
                            isDraft = false,
                            isBonusConsumed = isBonusConsumed
                        )
                    )
                }
                board.add(rowCells)
            }

            val playerRack = deserializeTileList(root.getJSONArray("playerRack"))
            val computerRack = deserializeTileList(root.getJSONArray("computerRack"))
            val tileBag = deserializeTileList(root.getJSONArray("tileBag"))

            // Draft placements
            val drafts = mutableListOf<PlacedTile>()
            val draftsArray = root.optJSONArray("draftPlacements")
            if (draftsArray != null) {
                for (i in 0 until draftsArray.length()) {
                    val dObj = draftsArray.getJSONObject(i)
                    drafts.add(
                        PlacedTile(
                            row = dObj.getInt("r"),
                            col = dObj.getInt("c"),
                            tile = deserializeTile(dObj.getJSONObject("tile"))
                        )
                    )
                }
            }

            // Move History
            val history = mutableListOf<MoveRecord>()
            val historyArray = root.optJSONArray("moveHistory")
            if (historyArray != null) {
                for (i in 0 until historyArray.length()) {
                    val mObj = historyArray.getJSONObject(i)
                    val wordDetails = mutableListOf<WordScoreDetail>()
                    val detailsArray = mObj.optJSONArray("scoreDetails")
                    if (detailsArray != null) {
                        for (j in 0 until detailsArray.length()) {
                            val wdObj = detailsArray.getJSONObject(j)
                            val letters = mutableListOf<LetterScoreDetail>()
                            val lettersArray = wdObj.optJSONArray("letters")
                            if (lettersArray != null) {
                                for (k in 0 until lettersArray.length()) {
                                    val lObj = lettersArray.getJSONObject(k)
                                    letters.add(
                                        LetterScoreDetail(
                                            letter = lObj.getString("letter")[0],
                                            baseValue = lObj.getInt("baseValue"),
                                            multiplier = lObj.optInt("multiplier", 1),
                                            isWordMultiplier = lObj.optBoolean("isWordMultiplier", false),
                                            isCommittedAlready = lObj.optBoolean("isCommittedAlready", false)
                                        )
                                    )
                                }
                            }
                            wordDetails.add(
                                WordScoreDetail(
                                    word = wdObj.getString("word"),
                                    letters = letters,
                                    wordMultiplier = wdObj.optInt("wordMultiplier", 1),
                                    score = wdObj.optInt("score", 0),
                                    isMainWord = wdObj.optBoolean("isMainWord", false)
                                )
                            )
                        }
                    }

                    history.add(
                        MoveRecord(
                            turnNumber = mObj.getInt("turnNumber"),
                            playerName = mObj.getString("playerName"),
                            isHuman = mObj.optBoolean("isHuman", true),
                            moveType = MoveType.valueOf(mObj.optString("moveType", MoveType.PLAY.name)),
                            word = mObj.optString("word").takeIf { it.isNotEmpty() },
                            scoreGained = mObj.optInt("scoreGained", 0),
                            scoreDetails = wordDetails,
                            bingoBonus = mObj.optInt("bingoBonus", 0),
                            runningPlayerScore = mObj.optInt("runningPlayerScore", 0),
                            runningOpponentScore = mObj.optInt("runningOpponentScore", 0),
                            timestamp = mObj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }

            MatchState(
                matchId = matchId,
                revision = revision,
                gameMode = gameMode,
                computerDifficulty = difficulty,
                player1Name = player1Name,
                player2Name = player2Name,
                board = board,
                playerRack = playerRack,
                computerRack = computerRack,
                tileBag = tileBag,
                playerScore = playerScore,
                computerScore = computerScore,
                currentTurn = currentTurn,
                startingPlayer = startingPlayer,
                status = status,
                computerName = computerName,
                consecutiveSkips = consecutiveSkips,
                matchCategory = matchCategory,
                lastMoveSummary = lastMoveSummary,
                lastComputerMoveCells = lastCells,
                playerRackDeduction = pDed,
                computerRackDeduction = cDed,
                winnerName = winnerName,
                moveHistory = history,
                draftPlacements = drafts,
                isHandoverPending = isHandoverPending
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @Synchronized
    fun deleteSavedMatch() {
        if (matchFile.exists()) {
            matchFile.delete()
        }
    }

    private fun serializeTile(tile: Tile): JSONObject {
        val obj = JSONObject()
        obj.put("id", tile.id)
        obj.put("letter", tile.letter.toString())
        obj.put("value", tile.value)
        obj.put("isBlank", tile.isBlank)
        if (tile.assignedLetter != null) {
            obj.put("assignedLetter", tile.assignedLetter.toString())
        }
        return obj
    }

    private fun deserializeTile(obj: JSONObject): Tile {
        val id = obj.optString("id")
        val letter = obj.getString("letter")[0]
        val value = obj.getInt("value")
        val isBlank = obj.optBoolean("isBlank", false)
        val assigned = if (obj.has("assignedLetter")) obj.getString("assignedLetter")[0] else null
        return Tile(
            id = id,
            letter = letter,
            value = value,
            isBlank = isBlank,
            assignedLetter = assigned
        )
    }

    private fun serializeTileList(tiles: List<Tile>): JSONArray {
        val array = JSONArray()
        for (t in tiles) {
            array.put(serializeTile(t))
        }
        return array
    }

    private fun deserializeTileList(array: JSONArray): List<Tile> {
        val list = mutableListOf<Tile>()
        for (i in 0 until array.length()) {
            list.add(deserializeTile(array.getJSONObject(i)))
        }
        return list
    }

    // Settings
    fun loadSettings(): GameSettings {
        val themeModeStr = prefs.getString("theme_mode", null)
        val legacyDark = prefs.getBoolean("is_dark_theme", true)
        val mode = if (themeModeStr != null) {
            try {
                com.example.data.model.AppThemeMode.valueOf(themeModeStr)
            } catch (_: Exception) {
                if (legacyDark) com.example.data.model.AppThemeMode.DARK else com.example.data.model.AppThemeMode.LIGHT
            }
        } else {
            if (legacyDark) com.example.data.model.AppThemeMode.DARK else com.example.data.model.AppThemeMode.LIGHT
        }

        return GameSettings(
            playerName = prefs.getString("player_name", "You") ?: "You",
            soundEnabled = prefs.getBoolean("sound_enabled", true),
            hapticsEnabled = prefs.getBoolean("haptics_enabled", true),
            themeMode = mode,
            isDarkTheme = mode == com.example.data.model.AppThemeMode.DARK
        )
    }

    fun saveSettings(settings: GameSettings) {
        prefs.edit()
            .putString("player_name", settings.playerName)
            .putBoolean("sound_enabled", settings.soundEnabled)
            .putBoolean("haptics_enabled", settings.hapticsEnabled)
            .putString("theme_mode", settings.themeMode.name)
            .putBoolean("is_dark_theme", settings.themeMode == com.example.data.model.AppThemeMode.DARK)
            .apply()
    }
}
