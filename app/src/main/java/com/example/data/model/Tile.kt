package com.example.data.model

import java.util.UUID

/**
 * Standard English letter values and tile definitions.
 */
data class Tile(
    val id: String = UUID.randomUUID().toString(),
    val letter: Char,
    val value: Int,
    val isBlank: Boolean = false,
    val assignedLetter: Char? = null
) {
    /**
     * The character displayed on the board or in the rack.
     * If blank and assigned, shows the assigned letter; otherwise '?'.
     */
    val displayLetter: Char
        get() = when {
            isBlank && assignedLetter != null -> assignedLetter
            isBlank -> '?'
            else -> letter
        }

    /**
     * The actual score contributed by this tile.
     * Blanks always score 0, regardless of the assigned letter.
     */
    val scoreValue: Int
        get() = if (isBlank) 0 else value

    companion object {
        fun letterValue(ch: Char): Int = when (ch.uppercaseChar()) {
            'A', 'E', 'I', 'L', 'N', 'O', 'R', 'S', 'T', 'U' -> 1
            'D', 'G' -> 2
            'B', 'C', 'M', 'P' -> 3
            'F', 'H', 'V', 'W', 'Y' -> 4
            'K' -> 5
            'J', 'X' -> 8
            'Q', 'Z' -> 10
            '?' -> 0
            else -> 0
        }

        fun create(letter: Char): Tile {
            val upper = letter.uppercaseChar()
            return if (upper == '?' || upper == ' ') {
                Tile(letter = '?', value = 0, isBlank = true)
            } else {
                Tile(letter = upper, value = letterValue(upper), isBlank = false)
            }
        }
    }
}

/**
 * Standard 100-tile English Scrabble distribution.
 */
object TileDistribution {
    val COUNTS = mapOf(
        'E' to 12, 'A' to 9, 'I' to 9, 'O' to 8, 'N' to 6, 'R' to 6, 'T' to 6,
        'L' to 4, 'S' to 4, 'U' to 4, 'D' to 4, 'G' to 3,
        'B' to 2, 'C' to 2, 'M' to 2, 'P' to 2, 'F' to 2, 'H' to 2, 'V' to 2, 'W' to 2, 'Y' to 2,
        'K' to 1, 'J' to 1, 'X' to 1, 'Q' to 1, 'Z' to 1,
        '?' to 2 // Blanks
    )

    fun createStandardBag(): MutableList<Tile> {
        val bag = mutableListOf<Tile>()
        for ((char, count) in COUNTS) {
            repeat(count) {
                bag.add(Tile.create(char))
            }
        }
        bag.shuffle()
        return bag
    }
}
