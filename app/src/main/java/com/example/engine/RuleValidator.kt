package com.example.engine

import com.example.data.model.BoardCell
import com.example.data.model.BonusCategory
import com.example.data.model.LetterScoreDetail
import com.example.data.model.MoveScoreBreakdown
import com.example.data.model.PlacedTile
import com.example.data.model.ScoredWord
import com.example.data.model.WordScoreDetail

sealed class ValidationResult {
    data class Valid(val breakdown: MoveScoreBreakdown) : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}

object RuleValidator {
    const val BOARD_SIZE = 13
    const val CENTER = 6

    /**
     * Validates a candidate move consisting of newly placed tiles on top of the committed board.
     * @param committedBoard 13x13 grid with currently committed tiles
     * @param placements List of newly placed tiles with board coordinates
     * @param dictionary DictionaryTrie to validate English words
     * @param totalRackTilesUsed Number of tiles placed from the rack (to check 7-tile bingo)
     */
    fun validateAndScore(
        committedBoard: List<List<BoardCell>>,
        placements: List<PlacedTile>,
        dictionary: DictionaryTrie,
        totalRackTilesUsed: Int = placements.size
    ): ValidationResult {
        if (placements.isEmpty()) {
            return ValidationResult.Invalid("Place at least one tile on the board.")
        }

        // 1. Boundary & Overwrite check
        for (p in placements) {
            if (p.row !in 0 until BOARD_SIZE || p.col !in 0 until BOARD_SIZE) {
                return ValidationResult.Invalid("Tile placed outside the board boundaries.")
            }
            if (committedBoard[p.row][p.col].isCommitted) {
                return ValidationResult.Invalid("Cannot overwrite an existing committed tile.")
            }
        }

        // Check for duplicate placement coordinates
        val coords = placements.map { it.row to it.col }
        if (coords.toSet().size != coords.size) {
            return ValidationResult.Invalid("Multiple tiles placed on the same square.")
        }

        // Determine if board is currently empty
        val isFirstMove = committedBoard.all { row -> row.all { !it.isCommitted } }

        // 2. Alignment Check: all placed tiles must be in a single row or single column
        val sameRow = placements.all { it.row == placements[0].row }
        val sameCol = placements.all { it.col == placements[0].col }

        if (!sameRow && !sameCol) {
            return ValidationResult.Invalid("Place tiles in a single row or column.")
        }

        val isHorizontal = sameRow && (!sameCol || placements.size == 1)

        // 3. Opening move must cross the center square (6, 6)
        if (isFirstMove) {
            val coversCenter = placements.any { it.row == CENTER && it.col == CENTER }
            if (!coversCenter) {
                return ValidationResult.Invalid("Your first word must cross the centre square.")
            }
        }

        // Create an overlay lookup for quick access: committed tiles + new placements
        val placementMap = placements.associateBy { it.row to it.col }
        fun getTileAt(r: Int, c: Int) = placementMap[r to c]?.tile ?: committedBoard[r][c].tile

        // 4. Connectivity Check for non-first moves:
        // Must connect to at least one already committed tile
        if (!isFirstMove) {
            var connectsToCommitted = false
            for (p in placements) {
                // Check adjacent committed tiles
                val neighbors = listOf(
                    (p.row - 1) to p.col,
                    (p.row + 1) to p.col,
                    p.row to (p.col - 1),
                    p.row to (p.col + 1)
                )
                for ((nr, nc) in neighbors) {
                    if (nr in 0 until BOARD_SIZE && nc in 0 until BOARD_SIZE) {
                        if (committedBoard[nr][nc].isCommitted) {
                            connectsToCommitted = true
                            break
                        }
                    }
                }
                if (connectsToCommitted) break
            }

            if (!connectsToCommitted) {
                return ValidationResult.Invalid("Connect your word to the existing tiles.")
            }
        }

        // 5. Gap Check along the primary placement axis:
        // Between the minimum and maximum placed coordinate, every square must have a tile (new or committed).
        if (isHorizontal) {
            val row = placements[0].row
            val minCol = placements.minOf { it.col }
            val maxCol = placements.maxOf { it.col }
            for (c in minCol..maxCol) {
                if (getTileAt(row, c) == null) {
                    return ValidationResult.Invalid("Words cannot contain gaps between tiles.")
                }
            }
        } else {
            val col = placements[0].col
            val minRow = placements.minOf { it.row }
            val maxRow = placements.maxOf { it.row }
            for (r in minRow..maxRow) {
                if (getTileAt(r, col) == null) {
                    return ValidationResult.Invalid("Words cannot contain gaps between tiles.")
                }
            }
        }

        // 6. Extract Main Word and Crosswords, and Score Them
        val scoredWords = mutableListOf<ScoredWord>()

        // Helper to extract a contiguous line of tiles
        fun extractWord(
            startR: Int,
            startC: Int,
            dr: Int,
            dc: Int
        ): Triple<String, List<Pair<Int, Int>>, List<Char>> {
            // Find start
            var r = startR
            var c = startC
            while (r - dr in 0 until BOARD_SIZE && c - dc in 0 until BOARD_SIZE && getTileAt(r - dr, c - dc) != null) {
                r -= dr
                c -= dc
            }
            val wordChars = mutableListOf<Char>()
            val wordCoords = mutableListOf<Pair<Int, Int>>()
            while (r in 0 until BOARD_SIZE && c in 0 until BOARD_SIZE && getTileAt(r, c) != null) {
                val tile = getTileAt(r, c)!!
                val ch = if (tile.isBlank) {
                    tile.assignedLetter ?: return Triple("", emptyList(), emptyList())
                } else {
                    tile.letter
                }
                wordChars.add(ch.uppercaseChar())
                wordCoords.add(r to c)
                r += dr
                c += dc
            }
            return Triple(wordChars.joinToString(""), wordCoords, wordChars)
        }

        // Extract primary word along placement direction
        val (mainWord, mainCoords, _) = if (isHorizontal) {
            extractWord(placements[0].row, placements[0].col, 0, 1)
        } else {
            extractWord(placements[0].row, placements[0].col, 1, 0)
        }

        if (mainWord.isEmpty() && placements.any { it.tile.isBlank && it.tile.assignedLetter == null }) {
            return ValidationResult.Invalid("Please assign a letter to the blank tile.")
        }

        // If main word length >= 2, score and validate it
        if (mainWord.length >= 2) {
            if (!dictionary.isValidWord(mainWord)) {
                return ValidationResult.Invalid("‘$mainWord’ is not in this game’s dictionary.")
            }
            val (mainScore, mainDetail) = scoreWord(mainWord, mainCoords, committedBoard, placementMap, isMainWord = true)
            scoredWords.add(ScoredWord(mainWord, mainScore, isMainWord = true, detail = mainDetail))
        }

        // Extract crosswords perpendicular to each placed tile
        for (p in placements) {
            val (crossWord, crossCoords, _) = if (isHorizontal) {
                // Perpendicular is vertical (dr=1, dc=0)
                extractWord(p.row, p.col, 1, 0)
            } else {
                // Perpendicular is horizontal (dr=0, dc=1)
                extractWord(p.row, p.col, 0, 1)
            }

            if (crossWord.length >= 2) {
                if (!dictionary.isValidWord(crossWord)) {
                    return ValidationResult.Invalid("‘$crossWord’ is not in this game’s dictionary.")
                }
                val (crossScore, crossDetail) = scoreWord(crossWord, crossCoords, committedBoard, placementMap, isMainWord = false)
                scoredWords.add(ScoredWord(crossWord, crossScore, isMainWord = false, detail = crossDetail))
            }
        }

        // A single newly placed tile must form at least one valid word of >= 2 letters
        if (scoredWords.isEmpty()) {
            return ValidationResult.Invalid("Placement must form at least one word with 2 or more letters.")
        }

        val baseWordsScore = scoredWords.sumOf { it.score }
        val bingoBonus = if (totalRackTilesUsed == 7) 50 else 0
        val totalScore = baseWordsScore + bingoBonus

        return ValidationResult.Valid(
            MoveScoreBreakdown(
                words = scoredWords,
                bingoBonus = bingoBonus,
                totalScore = totalScore
            )
        )
    }

    private fun scoreWord(
        wordStr: String,
        coords: List<Pair<Int, Int>>,
        committedBoard: List<List<BoardCell>>,
        placementMap: Map<Pair<Int, Int>, PlacedTile>,
        isMainWord: Boolean
    ): Pair<Int, WordScoreDetail> {
        var wordLetterSum = 0
        var wordMultiplier = 1
        val letterDetails = mutableListOf<LetterScoreDetail>()

        for ((r, c) in coords) {
            val placed = placementMap[r to c]
            if (placed != null) {
                // Newly placed tile: active bonus square applies!
                val cell = committedBoard[r][c]
                val tile = placed.tile
                val letterVal = if (tile.isBlank) 0 else tile.value
                val char = tile.assignedLetter ?: tile.letter

                var letterMult = 1
                var isWordMult = false
                if (cell.bonus != null && !cell.isBonusConsumed) {
                    when (cell.bonus.category) {
                        BonusCategory.LETTER -> {
                            letterMult = cell.bonus.multiplier
                        }
                        BonusCategory.WORD -> {
                            // Blank tile on word multiplier still activates that multiplier!
                            wordMultiplier *= cell.bonus.multiplier
                            isWordMult = true
                        }
                    }
                }
                wordLetterSum += (letterVal * letterMult)
                letterDetails.add(
                    LetterScoreDetail(
                        letter = char,
                        baseValue = letterVal,
                        multiplier = letterMult,
                        isWordMultiplier = isWordMult,
                        isCommittedAlready = false
                    )
                )
            } else {
                // Already committed tile: regular value only, bonus inactive/consumed
                val committedTile = committedBoard[r][c].tile ?: continue
                val letterVal = if (committedTile.isBlank) 0 else committedTile.value
                val char = committedTile.assignedLetter ?: committedTile.letter
                wordLetterSum += letterVal
                letterDetails.add(
                    LetterScoreDetail(
                        letter = char,
                        baseValue = letterVal,
                        multiplier = 1,
                        isWordMultiplier = false,
                        isCommittedAlready = true
                    )
                )
            }
        }

        val totalWordScore = wordLetterSum * wordMultiplier
        val detail = WordScoreDetail(
            word = wordStr,
            letters = letterDetails,
            wordMultiplier = wordMultiplier,
            score = totalWordScore,
            isMainWord = isMainWord
        )
        return totalWordScore to detail
    }
}
