package com.example.data.model

enum class PlayerTurn {
    HUMAN,
    COMPUTER
}

enum class GameMode {
    COMPUTER,
    PASS_AND_PLAY,
    LOCAL_WIFI
}

enum class ComputerDifficulty {
    EASY,
    BALANCED,
    HARD
}

enum class MoveType {
    PLAY,
    SWAP,
    PASS,
    RESIGN
}

enum class MatchStatus {
    IN_PROGRESS,
    FINISHED_NORMAL,
    FINISHED_RESIGNATION
}

data class LetterScoreDetail(
    val letter: Char,
    val baseValue: Int,
    val multiplier: Int = 1,
    val isWordMultiplier: Boolean = false,
    val isCommittedAlready: Boolean = false
) {
    val effectiveLetterValue: Int
        get() = if (isCommittedAlready || isWordMultiplier) baseValue else baseValue * multiplier
}

data class WordScoreDetail(
    val word: String,
    val letters: List<LetterScoreDetail>,
    val wordMultiplier: Int = 1,
    val score: Int = 0,
    val isMainWord: Boolean = false
) {
    val calculationFormula: String
        get() {
            val letterParts = letters.joinToString(" + ") { l ->
                if (l.isCommittedAlready) {
                    "${l.letter}(${l.baseValue})"
                } else if (l.multiplier > 1) {
                    "${l.letter}(${l.baseValue}×${l.multiplier})"
                } else {
                    "${l.letter}(${l.baseValue})"
                }
            }
            return if (wordMultiplier > 1) {
                "($letterParts) × $wordMultiplier = $score"
            } else {
                "$letterParts = $score"
            }
        }
}

data class ScoredWord(
    val word: String,
    val score: Int,
    val isMainWord: Boolean = false,
    val detail: WordScoreDetail? = null
)

data class MoveScoreBreakdown(
    val words: List<ScoredWord>,
    val bingoBonus: Int, // 50 points if all 7 tiles used
    val totalScore: Int
)

data class MoveRecord(
    val turnNumber: Int,
    val playerName: String,
    val isHuman: Boolean,
    val moveType: MoveType,
    val word: String? = null,
    val scoreGained: Int = 0,
    val scoreDetails: List<WordScoreDetail> = emptyList(),
    val bingoBonus: Int = 0,
    val runningPlayerScore: Int = 0,
    val runningOpponentScore: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

data class PlacedTile(
    val row: Int,
    val col: Int,
    val tile: Tile
)

data class MatchSummary(
    val matchId: String,
    val gameMode: GameMode,
    val difficulty: ComputerDifficulty?,
    val timestamp: Long,
    val player1Name: String,
    val player2Name: String,
    val player1Score: Int,
    val player2Score: Int,
    val winnerName: String?,
    val totalTurns: Int,
    val bestWord: String?,
    val bestWordScore: Int
)

data class GameStatistics(
    val totalMatches: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val highestWord: String = "",
    val highestWordScore: Int = 0,
    val highestGameScore: Int = 0,
    val averageScore: Double = 0.0,
    val recentMatches: List<MatchSummary> = emptyList()
) {
    val winRatePercent: Int
        get() = if (totalMatches > 0) ((wins.toDouble() / totalMatches) * 100).toInt() else 0
}

data class MatchState(
    val matchId: String,
    val revision: Long = 0L,
    val gameMode: GameMode = GameMode.COMPUTER,
    val computerDifficulty: ComputerDifficulty = ComputerDifficulty.BALANCED,
    val player1Name: String = "You",
    val player2Name: String = "Computer",
    val board: List<List<BoardCell>>,
    val playerRack: List<Tile>,
    val computerRack: List<Tile>,
    val tileBag: List<Tile>,
    val playerScore: Int = 0,
    val computerScore: Int = 0,
    val currentTurn: PlayerTurn,
    val startingPlayer: PlayerTurn,
    val status: MatchStatus = MatchStatus.IN_PROGRESS,
    val computerName: String = "Computer",
    val consecutiveSkips: Int = 0,
    val matchCategory: MatchCategory = MatchCategory.STANDARD,
    val lastMoveSummary: String? = null,
    val lastComputerMoveCells: List<Pair<Int, Int>> = emptyList(),
    val playerRackDeduction: Int? = null,
    val computerRackDeduction: Int? = null,
    val winnerName: String? = null,
    val moveHistory: List<MoveRecord> = emptyList(),
    val draftPlacements: List<PlacedTile> = emptyList(),
    val isHandoverPending: Boolean = false
) {
    val isGameOver: Boolean
        get() = status != MatchStatus.IN_PROGRESS

    val totalTilesRemaining: Int
        get() = tileBag.size

    val activePlayerName: String
        get() = if (currentTurn == PlayerTurn.HUMAN) player1Name else player2Name

    val inactivePlayerName: String
        get() = if (currentTurn == PlayerTurn.HUMAN) player2Name else player1Name
}

enum class AppThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

data class GameSettings(
    val playerName: String = "You",
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val isDarkTheme: Boolean = true
)
