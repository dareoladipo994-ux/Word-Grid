package com.example.engine

import com.example.data.model.BoardCell
import com.example.data.model.ComputerDifficulty
import com.example.data.model.MoveScoreBreakdown
import com.example.data.model.PlacedTile
import com.example.data.model.Tile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

data class ComputerMove(
    val placements: List<PlacedTile>,
    val breakdown: MoveScoreBreakdown?,
    val isSwap: Boolean = false,
    val isPass: Boolean = false,
    val swappedTileIds: List<String> = emptyList()
)

object ComputerPlayer {
    val NAMES = listOf("Amara", "Daniel", "Zainab", "Tobi", "Maya")

    suspend fun findMove(
        committedBoard: List<List<BoardCell>>,
        computerRack: List<Tile>,
        dictionary: DictionaryTrie,
        tileBagCount: Int,
        difficulty: ComputerDifficulty = ComputerDifficulty.BALANCED,
        random: Random = Random.Default
    ): ComputerMove = withContext(Dispatchers.Default) {
        val isFirstMove = committedBoard.all { row -> row.all { !it.isCommitted } }
        val candidateMoves = mutableListOf<Pair<List<PlacedTile>, MoveScoreBreakdown>>()

        if (isFirstMove) {
            // Find words crossing center (6, 6)
            findOpeningMoves(computerRack, dictionary, candidateMoves)
        } else {
            // Find moves connecting to existing board
            findConnectedMoves(committedBoard, computerRack, dictionary, candidateMoves, difficulty)
        }

        // Check if coroutine was cancelled
        if (!coroutineContext.isActive) {
            return@withContext ComputerMove(emptyList(), null, isPass = true)
        }

        if (candidateMoves.isNotEmpty()) {
            // Sort by score descending
            candidateMoves.sortByDescending { it.second.totalScore }

            val selected = when (difficulty) {
                ComputerDifficulty.EASY -> {
                    // Easy: prefers modest length and scores in lower-to-middle range
                    val modestMoves = candidateMoves.filter { (placements, _) -> placements.size <= 4 }
                    val pool = if (modestMoves.isNotEmpty()) {
                        val startIndex = (modestMoves.size * 0.4).toInt().coerceAtMost(modestMoves.size - 1)
                        modestMoves.drop(startIndex).take(5)
                    } else {
                        val startIndex = (candidateMoves.size * 0.4).toInt().coerceAtMost(candidateMoves.size - 1)
                        candidateMoves.drop(startIndex).take(5)
                    }
                    pool.random(random)
                }
                ComputerDifficulty.BALANCED -> {
                    // Balanced selection: pick among top 6 candidates for natural, varied play
                    val poolSize = minOf(candidateMoves.size, 6)
                    candidateMoves.take(poolSize).random(random)
                }
                ComputerDifficulty.HARD -> {
                    // Hard selection: pick strictly from top 1 or 2 highest-scoring moves
                    val poolSize = minOf(candidateMoves.size, 2)
                    candidateMoves.take(poolSize).random(random)
                }
            }

            return@withContext ComputerMove(
                placements = selected.first,
                breakdown = selected.second
            )
        }

        // If no moves found, decide whether to swap or pass
        if (tileBagCount >= 7 && computerRack.isNotEmpty()) {
            // Swap 1 to 3 tiles (e.g. vowels or high value consonants if stuck)
            val countToSwap = minOf(computerRack.size, minOf(tileBagCount, random.nextInt(1, 4)))
            val toSwap = computerRack.shuffled(random).take(countToSwap).map { it.id }
            return@withContext ComputerMove(
                placements = emptyList(),
                breakdown = null,
                isSwap = true,
                swappedTileIds = toSwap
            )
        }

        // Otherwise pass
        return@withContext ComputerMove(
            placements = emptyList(),
            breakdown = null,
            isPass = true
        )
    }

    private suspend fun findOpeningMoves(
        rack: List<Tile>,
        dictionary: DictionaryTrie,
        outMoves: MutableList<Pair<List<PlacedTile>, MoveScoreBreakdown>>
    ) {
        val center = RuleValidator.CENTER
        val emptyBoard = BoardGenerator.createFallbackLayout(com.example.data.model.MatchCategory.STANDARD)

        // Try placing horizontally or vertically through center with lengths 2..minOf(rack.size, 7)
        val maxLen = minOf(rack.size, 7)
        for (len in 2..maxLen) {
            if (!coroutineContext.isActive) return
            // Center (6) can be at index 0..(len-1) of the word
            for (offset in 0 until len) {
                val startCol = center - offset
                if (startCol < 0 || startCol + len > RuleValidator.BOARD_SIZE) continue

                // Find valid words of length 'len' using rack tiles
                val wordCandidates = findRackWords(rack, len, dictionary)
                for ((word, usedTiles) in wordCandidates) {
                    val placements = word.indices.map { i ->
                        val tile = usedTiles[i]
                        val finalTile = if (tile.isBlank) {
                            tile.copy(assignedLetter = word[i])
                        } else tile
                        PlacedTile(row = center, col = startCol + i, tile = finalTile)
                    }

                    val validation = RuleValidator.validateAndScore(emptyBoard, placements, dictionary, usedTiles.size)
                    if (validation is ValidationResult.Valid) {
                        outMoves.add(Pair(placements, validation.breakdown))
                    }
                }
            }
        }
    }

    private suspend fun findConnectedMoves(
        committedBoard: List<List<BoardCell>>,
        rack: List<Tile>,
        dictionary: DictionaryTrie,
        outMoves: MutableList<Pair<List<PlacedTile>, MoveScoreBreakdown>>,
        difficulty: ComputerDifficulty = ComputerDifficulty.BALANCED
    ) {
        val size = RuleValidator.BOARD_SIZE

        // Find anchor cells: empty cells that neighbor at least one committed tile
        val anchors = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (!committedBoard[r][c].isCommitted) {
                    val neighbors = listOf(
                        (r - 1) to c, (r + 1) to c, r to (c - 1), r to (c + 1)
                    )
                    if (neighbors.any { (nr, nc) -> nr in 0 until size && nc in 0 until size && committedBoard[nr][nc].isCommitted }) {
                        anchors.add(Pair(r, c))
                    }
                }
            }
        }

        val anchorLimit = when (difficulty) {
            ComputerDifficulty.EASY -> 12
            ComputerDifficulty.BALANCED -> 18
            ComputerDifficulty.HARD -> 36
        }
        val maxMovesLimit = when (difficulty) {
            ComputerDifficulty.EASY -> 15
            ComputerDifficulty.BALANCED -> 25
            ComputerDifficulty.HARD -> 45
        }

        // Limit search to a difficulty-scaled subset of anchors for fast, smooth responsiveness
        val searchAnchors = anchors.shuffled().take(anchorLimit)

        for ((anchorR, anchorC) in searchAnchors) {
            if (!coroutineContext.isActive) return

            // Search Horizontal placements crossing this anchor
            searchLinePlacements(
                isHoriz = true,
                fixed = anchorR,
                anchor = anchorC,
                committedBoard = committedBoard,
                rack = rack,
                dictionary = dictionary,
                outMoves = outMoves
            )

            // Search Vertical placements crossing this anchor
            searchLinePlacements(
                isHoriz = false,
                fixed = anchorC,
                anchor = anchorR,
                committedBoard = committedBoard,
                rack = rack,
                dictionary = dictionary,
                outMoves = outMoves
            )

            if (outMoves.size >= maxMovesLimit) break
        }
    }

    private fun searchLinePlacements(
        isHoriz: Boolean,
        fixed: Int,
        anchor: Int,
        committedBoard: List<List<BoardCell>>,
        rack: List<Tile>,
        dictionary: DictionaryTrie,
        outMoves: MutableList<Pair<List<PlacedTile>, MoveScoreBreakdown>>
    ) {
        val size = RuleValidator.BOARD_SIZE
        val maxLen = minOf(7, size)

        for (len in 2..maxLen) {
            for (start in (anchor - len + 1)..anchor) {
                val end = start + len - 1
                if (start < 0 || end >= size) continue

                // Check that line has at least one empty square and at least one connected square
                var hasEmpty = false
                val slots = mutableListOf<Char?>()
                for (pos in start..end) {
                    val r = if (isHoriz) fixed else pos
                    val c = if (isHoriz) pos else fixed
                    val cell = committedBoard[r][c]
                    if (cell.isCommitted) {
                        slots.add(cell.tile?.displayLetter)
                    } else {
                        hasEmpty = true
                        slots.add(null)
                    }
                }

                if (!hasEmpty) continue

                // Try to fill null slots from rack
                val formed = matchSlotsWithRack(slots, rack, dictionary)
                for ((word, usedTiles) in formed) {
                    val placements = mutableListOf<PlacedTile>()
                    var usedIdx = 0
                    for (i in slots.indices) {
                        if (slots[i] == null) {
                            val r = if (isHoriz) fixed else (start + i)
                            val c = if (isHoriz) (start + i) else fixed
                            val tile = usedTiles[usedIdx++]
                            val finalTile = if (tile.isBlank) {
                                tile.copy(assignedLetter = word[i])
                            } else tile
                            placements.add(PlacedTile(r, c, finalTile))
                        }
                    }

                    if (placements.isNotEmpty()) {
                        val validation = RuleValidator.validateAndScore(committedBoard, placements, dictionary, placements.size)
                        if (validation is ValidationResult.Valid) {
                            outMoves.add(Pair(placements, validation.breakdown))
                        }
                    }
                }
            }
        }
    }

    private fun matchSlotsWithRack(
        slots: List<Char?>,
        rack: List<Tile>,
        dictionary: DictionaryTrie
    ): List<Pair<String, List<Tile>>> {
        val results = mutableListOf<Pair<String, List<Tile>>>()

        fun backtrack(
            index: Int,
            currentWord: StringBuilder,
            availableRack: List<Tile>,
            usedTiles: MutableList<Tile>
        ) {
            if (index == slots.size) {
                val word = currentWord.toString()
                if (dictionary.isValidWord(word)) {
                    results.add(Pair(word, ArrayList(usedTiles)))
                }
                return
            }

            if (!dictionary.hasPrefix(currentWord.toString())) {
                return
            }

            val fixedChar = slots[index]
            if (fixedChar != null) {
                currentWord.append(fixedChar)
                backtrack(index + 1, currentWord, availableRack, usedTiles)
                currentWord.deleteCharAt(currentWord.length - 1)
            } else {
                // Try each available tile from rack
                val visitedLetters = mutableSetOf<Char>()
                for (i in availableRack.indices) {
                    val tile = availableRack[i]
                    if (tile.isBlank) {
                        // Try common vowels and consonants for blank
                        val commonLetters = "ETAOINSHRDLCUMWFGYPBVKJXQZ"
                        for (ch in commonLetters) {
                            currentWord.append(ch)
                            if (dictionary.hasPrefix(currentWord.toString())) {
                                val remaining = availableRack.toMutableList().also { it.removeAt(i) }
                                usedTiles.add(tile)
                                backtrack(index + 1, currentWord, remaining, usedTiles)
                                usedTiles.removeAt(usedTiles.size - 1)
                            }
                            currentWord.deleteCharAt(currentWord.length - 1)
                        }
                    } else {
                        if (visitedLetters.add(tile.letter)) {
                            currentWord.append(tile.letter)
                            if (dictionary.hasPrefix(currentWord.toString())) {
                                val remaining = availableRack.toMutableList().also { it.removeAt(i) }
                                usedTiles.add(tile)
                                backtrack(index + 1, currentWord, remaining, usedTiles)
                                usedTiles.removeAt(usedTiles.size - 1)
                            }
                            currentWord.deleteCharAt(currentWord.length - 1)
                        }
                    }
                }
            }
        }

        backtrack(0, StringBuilder(), rack, mutableListOf())
        return results.take(12) // Cap candidate matches per slot line
    }

    private fun findRackWords(
        rack: List<Tile>,
        length: Int,
        dictionary: DictionaryTrie
    ): List<Pair<String, List<Tile>>> {
        val results = mutableListOf<Pair<String, List<Tile>>>()

        fun backtrack(
            currentWord: StringBuilder,
            availableRack: List<Tile>,
            usedTiles: MutableList<Tile>
        ) {
            if (currentWord.length == length) {
                val w = currentWord.toString()
                if (dictionary.isValidWord(w)) {
                    results.add(Pair(w, ArrayList(usedTiles)))
                }
                return
            }

            if (!dictionary.hasPrefix(currentWord.toString())) return

            val visited = mutableSetOf<Char>()
            for (i in availableRack.indices) {
                val tile = availableRack[i]
                if (tile.isBlank) {
                    val common = "ETAOINSHRD"
                    for (ch in common) {
                        currentWord.append(ch)
                        if (dictionary.hasPrefix(currentWord.toString())) {
                            val remaining = availableRack.toMutableList().also { it.removeAt(i) }
                            usedTiles.add(tile)
                            backtrack(currentWord, remaining, usedTiles)
                            usedTiles.removeAt(usedTiles.size - 1)
                        }
                        currentWord.deleteCharAt(currentWord.length - 1)
                    }
                } else {
                    if (visited.add(tile.letter)) {
                        currentWord.append(tile.letter)
                        if (dictionary.hasPrefix(currentWord.toString())) {
                            val remaining = availableRack.toMutableList().also { it.removeAt(i) }
                            usedTiles.add(tile)
                            backtrack(currentWord, remaining, usedTiles)
                            usedTiles.removeAt(usedTiles.size - 1)
                        }
                        currentWord.deleteCharAt(currentWord.length - 1)
                    }
                }
            }
        }

        backtrack(StringBuilder(), rack, mutableListOf())
        return results.take(15)
    }
}
