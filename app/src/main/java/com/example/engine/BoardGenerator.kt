package com.example.engine

import com.example.data.model.BoardCell
import com.example.data.model.BonusCategory
import com.example.data.model.BonusType
import com.example.data.model.MatchCategory
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

object BoardGenerator {
    const val BOARD_SIZE = 13
    const val CENTER_INDEX = 6
    const val TOTAL_BONUS_SQUARES = 32

    /**
     * Determines match category based on probabilities:
     * Standard: 80% (0.00..0.80)
     * Boosted: 17% (0.80..0.97)
     * Rare: 3% (0.97..1.00)
     */
    fun selectMatchCategory(random: Random = Random.Default): MatchCategory {
        val roll = random.nextDouble()
        return when {
            roll < 0.80 -> MatchCategory.STANDARD
            roll < 0.97 -> MatchCategory.BOOSTED
            else -> MatchCategory.RARE
        }
    }

    /**
     * Generates a 13x13 board with strict 4-way symmetry across both horizontal
     * and vertical axes, guaranteeing fair layouts without adjacent 3x bonus clusters.
     */
    fun generateBoard(
        category: MatchCategory = MatchCategory.STANDARD,
        seed: Long = System.currentTimeMillis()
    ): Pair<List<List<BoardCell>>, MatchCategory> {
        val random = Random(seed)
        val maxRetries = 30

        for (attempt in 0 until maxRetries) {
            val generated = tryGenerateSymmetricLayout(category, random)
            if (generated != null) {
                return Pair(generated, category)
            }
        }

        // Return deterministic tested 4-way symmetric fallback layout
        return Pair(createFallbackLayout(category), category)
    }

    private fun tryGenerateSymmetricLayout(
        category: MatchCategory,
        random: Random
    ): List<List<BoardCell>>? {
        val bonusGrid = Array(BOARD_SIZE) { Array<BonusType?>(BOARD_SIZE) { null } }

        // Quadrant candidates (r: 0..5, c: 0..5)
        val quadrantCandidates = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until CENTER_INDEX) {
            for (c in 0 until CENTER_INDEX) {
                quadrantCandidates.add(Pair(r, c))
            }
        }
        quadrantCandidates.shuffle(random)

        // Center cross candidates
        val rowCrossCandidates = (0 until CENTER_INDEX).map { c -> Pair(CENTER_INDEX, c) }.shuffled(random)
        val colCrossCandidates = (0 until CENTER_INDEX).map { r -> Pair(r, CENTER_INDEX) }.shuffled(random)

        // We need:
        // 6 quadrant points (each mirrors to 4 cells = 24 cells)
        // 2 row-cross points (each mirrors to 2 cells = 4 cells)
        // 2 col-cross points (each mirrors to 2 cells = 4 cells)
        // Total = 24 + 4 + 4 = 32 cells!

        val triple3xCells = mutableListOf<Pair<Int, Int>>()

        // Step 1: Pick 2 quadrant points for 3x Word bonuses (satisfying min spacing of 2 cells)
        val tripleWordCandidates = quadrantCandidates.filter { (r, c) ->
            // Prefer corners and outer diagonal for Triple Word
            (r == 0 && c == 0) || (r == 0 && c == 4) || (r == 1 && c == 1) || (r == 2 && c == 2) || (r == 3 && c == 0) || (r == 4 && c == 4)
        }.shuffled(random)

        val pickedTripleWord = mutableListOf<Pair<Int, Int>>()
        for (cand in tripleWordCandidates) {
            if (pickedTripleWord.size >= 2) break
            val group = getSymmetricGroup(cand.first, cand.second)
            if (group.none { g -> isTooCloseToAny(g.first, g.second, triple3xCells, minDistance = 2) }) {
                pickedTripleWord.add(cand)
                triple3xCells.addAll(group)
            }
        }

        if (pickedTripleWord.size < 2) return null

        // Step 2: Pick 1 quadrant point for 3x Letter bonuses (also respecting spacing with all 3x bonuses)
        val remainingQuadrant = quadrantCandidates.filter { !pickedTripleWord.contains(it) }.toMutableList()
        var pickedTripleLetter: Pair<Int, Int>? = null
        for (cand in remainingQuadrant) {
            val group = getSymmetricGroup(cand.first, cand.second)
            if (group.none { g -> isTooCloseToAny(g.first, g.second, triple3xCells, minDistance = 2) }) {
                pickedTripleLetter = cand
                triple3xCells.addAll(group)
                remainingQuadrant.remove(cand)
                break
            }
        }

        if (pickedTripleLetter == null) return null

        // Step 3: Handle Boosted/Rare high multiplier (4x..7x letter bonus)
        var pickedHighLetter: Pair<Int, Int>? = null
        var highBonusType: BonusType? = null
        if (category == MatchCategory.BOOSTED || category == MatchCategory.RARE) {
            val multiplier = if (category == MatchCategory.BOOSTED) {
                if (random.nextBoolean()) 4 else 5
            } else {
                if (random.nextBoolean()) 6 else 7
            }
            highBonusType = BonusType(BonusCategory.LETTER, multiplier)

            // Must be outside central 5x5 (row < 4 or col < 4)
            val eligibleForHigh = remainingQuadrant.filter { (r, c) -> r < 4 || c < 4 }
            if (eligibleForHigh.isEmpty()) return null
            val picked = eligibleForHigh.first()
            pickedHighLetter = picked
            remainingQuadrant.remove(picked)
        }

        // Step 4: Pick remaining quadrant points to reach 6 total
        val targetQuadrantCount = 6
        val currentQuadrantCount = 2 + 1 + (if (pickedHighLetter != null) 1 else 0)
        val neededQuadrant = targetQuadrantCount - currentQuadrantCount

        if (remainingQuadrant.size < neededQuadrant) return null
        val otherQuadrantPicks = remainingQuadrant.take(neededQuadrant)

        // Step 5: Pick 2 row-cross and 2 col-cross points
        val pickedRowCross = rowCrossCandidates.take(2)
        val pickedColCross = colCrossCandidates.take(2)

        if (pickedRowCross.size < 2 || pickedColCross.size < 2) return null

        // Assign to bonusGrid with strict 4-way symmetry
        fun applyBonusGroup(r: Int, c: Int, bonus: BonusType) {
            for (pt in getSymmetricGroup(r, c)) {
                bonusGrid[pt.first][pt.second] = bonus
            }
        }

        // 1. Triple Word bonuses
        for (pt in pickedTripleWord) {
            applyBonusGroup(pt.first, pt.second, BonusType(BonusCategory.WORD, 3))
        }

        // 2. Triple Letter bonus
        applyBonusGroup(pickedTripleLetter.first, pickedTripleLetter.second, BonusType(BonusCategory.LETTER, 3))

        // 3. High Multiplier (if applicable)
        if (pickedHighLetter != null && highBonusType != null) {
            applyBonusGroup(pickedHighLetter.first, pickedHighLetter.second, highBonusType)
        }

        // 4. Double Word bonuses (1 from other quadrant + 1 from cross)
        val otherQList = otherQuadrantPicks.toMutableList()
        if (otherQList.isNotEmpty()) {
            val dw = otherQList.removeAt(0)
            applyBonusGroup(dw.first, dw.second, BonusType(BonusCategory.WORD, 2))
        }
        applyBonusGroup(pickedRowCross[0].first, pickedRowCross[0].second, BonusType(BonusCategory.WORD, 2))

        // 5. Double Letter bonuses for all remaining picks
        for (pt in otherQList) {
            applyBonusGroup(pt.first, pt.second, BonusType(BonusCategory.LETTER, 2))
        }
        applyBonusGroup(pickedRowCross[1].first, pickedRowCross[1].second, BonusType(BonusCategory.LETTER, 2))
        for (pt in pickedColCross) {
            applyBonusGroup(pt.first, pt.second, BonusType(BonusCategory.LETTER, 2))
        }

        // Verify count is exactly 32
        var totalCount = 0
        for (r in 0 until BOARD_SIZE) {
            for (c in 0 until BOARD_SIZE) {
                if (bonusGrid[r][c] != null) totalCount++
            }
        }
        if (totalCount != TOTAL_BONUS_SQUARES) return null

        // Construct 2D BoardCell matrix
        return (0 until BOARD_SIZE).map { r ->
            (0 until BOARD_SIZE).map { c ->
                BoardCell(
                    row = r,
                    col = c,
                    bonus = bonusGrid[r][c],
                    isCenter = (r == CENTER_INDEX && c == CENTER_INDEX)
                )
            }
        }
    }

    /**
     * Returns the 2 or 4 symmetric coordinates for (r, c) on a 13x13 board.
     */
    fun getSymmetricGroup(r: Int, c: Int): List<Pair<Int, Int>> {
        return listOf(
            Pair(r, c),
            Pair(r, BOARD_SIZE - 1 - c),
            Pair(BOARD_SIZE - 1 - r, c),
            Pair(BOARD_SIZE - 1 - r, BOARD_SIZE - 1 - c)
        ).distinct()
    }

    private fun isTooCloseToAny(
        r: Int,
        c: Int,
        existingCells: List<Pair<Int, Int>>,
        minDistance: Int = 2
    ): Boolean {
        for (ex in existingCells) {
            val chebyshevDist = max(abs(r - ex.first), abs(c - ex.second))
            if (chebyshevDist < minDistance) {
                return true
            }
        }
        return false
    }

    /**
     * Deterministic, perfectly balanced 4-way symmetric fallback layout
     * across both horizontal and vertical axes (32 bonus squares).
     */
    fun createFallbackLayout(category: MatchCategory): List<List<BoardCell>> {
        val bonusGrid = Array(BOARD_SIZE) { Array<BonusType?>(BOARD_SIZE) { null } }

        fun place4Way(r: Int, c: Int, bonus: BonusType) {
            for (pt in getSymmetricGroup(r, c)) {
                bonusGrid[pt.first][pt.second] = bonus
            }
        }

        // 1. Triple Word (3x Word) - 8 squares (corners and outer edges)
        place4Way(0, 0, BonusType(BonusCategory.WORD, 3))
        place4Way(0, 4, BonusType(BonusCategory.WORD, 3))

        // 2. Double Word (2x Word) - 8 squares (balanced inner rings)
        place4Way(2, 2, BonusType(BonusCategory.WORD, 2))
        place4Way(CENTER_INDEX, 1, BonusType(BonusCategory.WORD, 2))

        // 3. Triple Letter (3x Letter) - 4 squares (well-spaced from 3x Word)
        place4Way(4, 4, BonusType(BonusCategory.LETTER, 3))

        // 4. Double Letter (2x Letter) - 12 squares
        place4Way(1, 1, BonusType(BonusCategory.LETTER, 2))
        place4Way(3, 0, BonusType(BonusCategory.LETTER, 2))
        place4Way(1, CENTER_INDEX, BonusType(BonusCategory.LETTER, 2))
        place4Way(5, 3, BonusType(BonusCategory.LETTER, 2))

        // Boosted or Rare high multiplier substitution on (3, 0)
        if (category == MatchCategory.BOOSTED) {
            place4Way(3, 0, BonusType(BonusCategory.LETTER, 5))
        } else if (category == MatchCategory.RARE) {
            place4Way(3, 0, BonusType(BonusCategory.LETTER, 7))
        }

        return (0 until BOARD_SIZE).map { r ->
            (0 until BOARD_SIZE).map { c ->
                BoardCell(
                    row = r,
                    col = c,
                    bonus = bonusGrid[r][c],
                    isCenter = (r == CENTER_INDEX && c == CENTER_INDEX)
                )
            }
        }
    }
}
