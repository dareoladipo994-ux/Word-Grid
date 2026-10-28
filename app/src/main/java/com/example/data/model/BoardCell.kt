package com.example.data.model

enum class BonusCategory {
    LETTER,
    WORD
}

data class BonusType(
    val category: BonusCategory,
    val multiplier: Int
) {
    val displayLabel: String
        get() = "${multiplier}×"

    val accessibleDescription: String
        get() = when (category) {
            BonusCategory.LETTER -> "${multiplier}× letter score"
            BonusCategory.WORD -> "${multiplier}× word score"
        }

    val isHighMultiplier: Boolean
        get() = multiplier >= 4
}

enum class MatchCategory {
    STANDARD, // 80% matches: only 2x and 3x bonuses
    BOOSTED,  // 17% matches: mostly 2x and 3x, one mirrored pair of 4x or 5x letter bonuses
    RARE      // 3% matches: mostly 2x and 3x, one mirrored pair of 6x or 7x letter bonuses
}

data class BoardCell(
    val row: Int,
    val col: Int,
    val bonus: BonusType? = null,
    val tile: Tile? = null,
    val isCenter: Boolean = false,
    val isDraft: Boolean = false,
    val isBonusConsumed: Boolean = false
) {
    val isOccupied: Boolean
        get() = tile != null

    val isCommitted: Boolean
        get() = tile != null && !isDraft
}
