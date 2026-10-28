package com.example

import com.example.data.model.BoardCell
import com.example.data.model.BonusCategory
import com.example.data.model.BonusType
import com.example.data.model.ComputerDifficulty
import com.example.data.model.GameMode
import com.example.data.model.MatchCategory
import com.example.data.model.MatchSummary
import com.example.data.model.PlacedTile
import com.example.data.model.Tile
import com.example.data.model.TileDistribution
import com.example.engine.BoardGenerator
import com.example.engine.ComputerPlayer
import com.example.engine.DictionaryTrie
import com.example.engine.RuleValidator
import com.example.engine.ValidationResult
import com.example.persistence.StatisticsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class WordGridGameEngineTest {
    private lateinit var dictionary: DictionaryTrie

    @Before
    fun setup() {
        dictionary = DictionaryTrie.createWithWords(
            listOf(
                "AT", "TO", "IT", "IS", "ON", "NO", "STAR", "PLANET", "WORD", "GRID", "PLAY", "CAT", "DOG",
                "BEAR", "TIGER", "LION", "TRAIN", "HOUSE", "WATER", "FAST", "QUIET", "ZEBRA", "QUIZ", "PLANETS"
            )
        )
    }

    @Test
    fun testTileDistribution_100TilesTotal() {
        val bag = TileDistribution.createStandardBag()
        assertEquals(100, bag.size)
        // 2 blanks
        val blanks = bag.filter { it.isBlank }
        assertEquals(2, blanks.size)
    }

    @Test
    fun testBoardGenerator_180DegreeSymmetryAnd32Bonuses() {
        for (seed in 1L..5L) {
            val (board, _) = BoardGenerator.generateBoard(MatchCategory.STANDARD, seed = seed)
            assertEquals(13, board.size)
            assertEquals(13, board[0].size)

            val center = board[6][6]
            assertTrue(center.isCenter)
            assertNull(center.bonus)

            var bonusCount = 0
            for (r in 0 until 13) {
                for (c in 0 until 13) {
                    val cell = board[r][c]
                    val mirror = board[12 - r][12 - c]
                    assertEquals(
                        "Mismatch at ($r, $c) and (${12 - r}, ${12 - c})",
                        cell.bonus?.category,
                        mirror.bonus?.category
                    )
                    assertEquals(
                        "Multiplier mismatch at ($r, $c)",
                        cell.bonus?.multiplier,
                        mirror.bonus?.multiplier
                    )
                    if (cell.bonus != null) {
                        bonusCount++
                    }
                }
            }
            assertEquals(32, bonusCount)
        }
    }

    @Test
    fun testBoardGenerator_boostedAndRareMultiplierRules() {
        val (boostedBoard, _) = BoardGenerator.generateBoard(MatchCategory.BOOSTED, seed = 42L)
        var highBonusFound = false
        for (r in 0 until 13) {
            for (c in 0 until 13) {
                val b = boostedBoard[r][c].bonus
                if (b != null && b.isHighMultiplier) {
                    highBonusFound = true
                    assertEquals(BonusCategory.LETTER, b.category)
                    assertTrue(b.multiplier in 4..5)
                    assertFalse(r in 4..8 && c in 4..8)
                }
            }
        }
        assertTrue(highBonusFound)
    }

    @Test
    fun testOpeningMove_mustCrossCenter() {
        val emptyBoard = BoardGenerator.createFallbackLayout(MatchCategory.STANDARD)
        val validOpening = listOf(
            PlacedTile(6, 5, Tile.create('S')),
            PlacedTile(6, 6, Tile.create('T')),
            PlacedTile(6, 7, Tile.create('A')),
            PlacedTile(6, 8, Tile.create('R'))
        )
        val validRes = RuleValidator.validateAndScore(emptyBoard, validOpening, dictionary)
        assertTrue(validRes is ValidationResult.Valid)

        val invalidOpening = listOf(
            PlacedTile(0, 0, Tile.create('S')),
            PlacedTile(0, 1, Tile.create('T')),
            PlacedTile(0, 2, Tile.create('A')),
            PlacedTile(0, 3, Tile.create('R'))
        )
        val invalidRes = RuleValidator.validateAndScore(emptyBoard, invalidOpening, dictionary)
        assertTrue(invalidRes is ValidationResult.Invalid)
        assertTrue((invalidRes as ValidationResult.Invalid).reason.contains("centre"))
    }

    @Test
    fun testScoreCalculation_itemizedBreakdownMatchesSum() {
        val emptyBoard = BoardGenerator.createFallbackLayout(MatchCategory.STANDARD)
        val opening = listOf(
            PlacedTile(6, 5, Tile.create('C')),
            PlacedTile(6, 6, Tile.create('A')),
            PlacedTile(6, 7, Tile.create('T'))
        )
        val result = RuleValidator.validateAndScore(emptyBoard, opening, dictionary)
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid

        assertEquals(1, valid.breakdown.words.size)
        val word = valid.breakdown.words[0]
        assertEquals("CAT", word.word)
        assertNotNull(word.detail)

        val detail = word.detail!!
        assertEquals(3, detail.letters.size)
        // C(3) + A(1) + T(1) = 5
        assertEquals(5, detail.score)
        assertEquals(word.score, detail.score)
        assertTrue(detail.calculationFormula.contains("CAT") || detail.calculationFormula.contains("5"))
    }

    @Test
    fun testBingoBonus_50PointsFor7Tiles() {
        val emptyBoard = BoardGenerator.createFallbackLayout(MatchCategory.STANDARD)
        val placements = listOf(
            PlacedTile(6, 3, Tile.create('P')),
            PlacedTile(6, 4, Tile.create('L')),
            PlacedTile(6, 5, Tile.create('A')),
            PlacedTile(6, 6, Tile.create('N')),
            PlacedTile(6, 7, Tile.create('E')),
            PlacedTile(6, 8, Tile.create('T')),
            PlacedTile(6, 9, Tile.create('S'))
        )
        val res = RuleValidator.validateAndScore(emptyBoard, placements, dictionary, totalRackTilesUsed = 7)
        assertTrue(res is ValidationResult.Valid)
        val valid = res as ValidationResult.Valid
        assertEquals(50, valid.breakdown.bingoBonus)
        assertTrue(valid.breakdown.totalScore >= 50)
    }

    @Test
    fun testComputerPlayer_difficultyOptions() = runBlocking {
        val emptyBoard = BoardGenerator.createFallbackLayout(MatchCategory.STANDARD)
        val rack = listOf(
            Tile.create('S'), Tile.create('T'), Tile.create('A'), Tile.create('R'),
            Tile.create('P'), Tile.create('L'), Tile.create('A')
        )

        val moveEasy = ComputerPlayer.findMove(
            committedBoard = emptyBoard,
            computerRack = rack,
            dictionary = dictionary,
            tileBagCount = 80,
            difficulty = ComputerDifficulty.EASY,
            random = Random(123)
        )

        val moveHard = ComputerPlayer.findMove(
            committedBoard = emptyBoard,
            computerRack = rack,
            dictionary = dictionary,
            tileBagCount = 80,
            difficulty = ComputerDifficulty.HARD,
            random = Random(123)
        )

        assertTrue(moveEasy.placements.isNotEmpty())
        assertTrue(moveHard.placements.isNotEmpty())
        assertTrue(moveHard.breakdown!!.totalScore >= moveEasy.breakdown!!.totalScore)
    }

    @Test
    fun testStatisticsCalculation_winRateAndAverages() {
        val match1 = MatchSummary(
            matchId = "m1",
            gameMode = GameMode.COMPUTER,
            difficulty = ComputerDifficulty.BALANCED,
            timestamp = 1000L,
            player1Name = "Player",
            player2Name = "Computer",
            player1Score = 120,
            player2Score = 90,
            winnerName = "Player",
            totalTurns = 12,
            bestWord = "PLANET",
            bestWordScore = 28
        )
        val match2 = MatchSummary(
            matchId = "m2",
            gameMode = GameMode.COMPUTER,
            difficulty = ComputerDifficulty.HARD,
            timestamp = 2000L,
            player1Name = "Player",
            player2Name = "Computer",
            player1Score = 80,
            player2Score = 110,
            winnerName = "Computer",
            totalTurns = 14,
            bestWord = "STAR",
            bestWordScore = 16
        )

        val stats = StatisticsRepository.calculateStatistics(listOf(match1, match2))
        assertEquals(2, stats.totalMatches)
        assertEquals(1, stats.wins)
        assertEquals(1, stats.losses)
        assertEquals(0, stats.draws)
        assertEquals(50, stats.winRatePercent)
        assertEquals(120, stats.highestGameScore)
        assertEquals("PLANET", stats.highestWord)
        assertEquals(28, stats.highestWordScore)
        assertEquals(100.0, stats.averageScore, 0.001)
    }
}
