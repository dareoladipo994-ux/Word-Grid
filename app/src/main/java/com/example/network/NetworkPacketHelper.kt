package com.example.network

import com.example.data.model.BoardCell
import com.example.data.model.BonusCategory
import com.example.data.model.BonusType
import com.example.data.model.LetterScoreDetail
import com.example.data.model.MoveRecord
import com.example.data.model.MoveType
import com.example.data.model.PlacedTile
import com.example.data.model.Tile
import com.example.data.model.WordScoreDetail
import org.json.JSONArray
import org.json.JSONObject

object NetworkPacketHelper {

    fun serializeTile(tile: Tile): JSONObject {
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

    fun deserializeTile(obj: JSONObject): Tile {
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

    fun serializeTileList(tiles: List<Tile>): JSONArray {
        val array = JSONArray()
        for (t in tiles) {
            array.put(serializeTile(t))
        }
        return array
    }

    fun deserializeTileList(array: JSONArray): List<Tile> {
        val list = mutableListOf<Tile>()
        for (i in 0 until array.length()) {
            list.add(deserializeTile(array.getJSONObject(i)))
        }
        return list
    }

    fun serializeBoard(board: List<List<BoardCell>>, maskDrafts: Boolean = true): JSONArray {
        val boardArray = JSONArray()
        for (row in board) {
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

                if (cell.tile != null && (cell.isCommitted || !maskDrafts)) {
                    cellObj.put("tile", serializeTile(cell.tile))
                }
                rowArray.put(cellObj)
            }
            boardArray.put(rowArray)
        }
        return boardArray
    }

    fun deserializeBoard(boardArray: JSONArray): List<List<BoardCell>> {
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

                val cellRow = cellObj.optInt("r", r)
                val cellCol = cellObj.optInt("c", c)

                rowCells.add(
                    BoardCell(
                        row = cellRow,
                        col = cellCol,
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
        return board
    }

    fun serializePlacements(placements: List<PlacedTile>): JSONArray {
        val array = JSONArray()
        for (p in placements) {
            val obj = JSONObject()
            obj.put("r", p.row)
            obj.put("c", p.col)
            obj.put("tile", serializeTile(p.tile))
            array.put(obj)
        }
        return array
    }

    fun deserializePlacements(array: JSONArray): List<PlacedTile> {
        val list = mutableListOf<PlacedTile>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                PlacedTile(
                    row = obj.getInt("r"),
                    col = obj.getInt("c"),
                    tile = deserializeTile(obj.getJSONObject("tile"))
                )
            )
        }
        return list
    }

    fun serializeMoveHistory(history: List<MoveRecord>): JSONArray {
        val historyArray = JSONArray()
        for (m in history) {
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
        return historyArray
    }

    fun deserializeMoveHistory(historyArray: JSONArray): List<MoveRecord> {
        val history = mutableListOf<MoveRecord>()
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
        return history
    }
}
