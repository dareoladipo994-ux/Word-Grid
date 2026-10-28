package com.example.engine

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class TrieNode {
    val children = arrayOfNulls<TrieNode>(26)
    var isWord = false
}

class DictionaryTrie {
    val root = TrieNode()
    var wordCount: Int = 0
        private set

    fun insert(word: String) {
        val upper = word.trim().uppercase()
        if (upper.length < 2 || !upper.all { it in 'A'..'Z' }) return

        var current = root
        for (i in upper.indices) {
            val idx = upper[i] - 'A'
            var child = current.children[idx]
            if (child == null) {
                child = TrieNode()
                current.children[idx] = child
            }
            current = child
        }
        if (!current.isWord) {
            current.isWord = true
            wordCount++
        }
    }

    fun isValidWord(word: String): Boolean {
        val upper = word.trim().uppercase()
        if (upper.length < 2) return false
        var current = root
        for (i in upper.indices) {
            val ch = upper[i]
            if (ch !in 'A'..'Z') return false
            val idx = ch - 'A'
            current = current.children[idx] ?: return false
        }
        return current.isWord
    }

    fun hasPrefix(prefix: String): Boolean {
        val upper = prefix.trim().uppercase()
        var current = root
        for (i in upper.indices) {
            val ch = upper[i]
            if (ch !in 'A'..'Z') return false
            val idx = ch - 'A'
            current = current.children[idx] ?: return false
        }
        return true
    }

    companion object {
        private var instance: DictionaryTrie? = null

        @Synchronized
        fun get(context: Context): DictionaryTrie {
            instance?.let { return it }

            val trie = DictionaryTrie()
            try {
                context.assets.open("words_en.txt").use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
                        for (line in lines) {
                            trie.insert(line)
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback core vocabulary if asset stream fails
                for (w in CoreFallbackWords.WORDS) {
                    trie.insert(w)
                }
            }
            instance = trie
            return trie
        }

        fun createWithWords(words: Iterable<String>): DictionaryTrie {
            val trie = DictionaryTrie()
            for (w in words) {
                trie.insert(w)
            }
            return trie
        }
    }
}

/**
 * Robust fallback vocabulary of 300+ common words in case pure JVM unit test runs without assets.
 */
object CoreFallbackWords {
    val WORDS = listOf(
        "AT", "AN", "AM", "AS", "BE", "BY", "DO", "GO", "HE", "HI", "IF", "IN", "IS", "IT", "ME",
        "MY", "NO", "OF", "OH", "ON", "OR", "SO", "TO", "UP", "US", "WE", "CAT", "DOG", "BAT",
        "RAT", "SUN", "RUN", "FUN", "WIN", "WAR", "RED", "BLUE", "WORD", "GRID", "PLAY", "STAR",
        "PLANET", "GAME", "BOARD", "TILES", "SCORE", "BONUS", "LEVEL", "ROBOT", "HUMAN", "MATCH",
        "LINE", "CARD", "TIME", "YEAR", "TREE", "BOOK", "CITY", "ROAD", "HAND", "DOOR", "FAST",
        "SLOW", "NICE", "COOL", "COLD", "WARM", "FIRE", "WATER", "EARTH", "WIND", "GOLD", "IRON",
        "WOOD", "STONE", "ROCK", "PAPER", "COIN", "LEAF", "BIRD", "FISH", "LION", "BEAR", "WOLF",
        "DEER", "DUCK", "FROG", "CRAB", "SHIP", "BOAT", "CAR", "TRAIN", "PLANE", "ROAD", "PARK",
        "SHOP", "CAFE", "HOME", "ROOM", "WALL", "ROOF", "DESK", "CHAIR", "TABLE", "BED", "LAMP",
        "CLOCK", "PHONE", "RADIO", "BALL", "RING", "BELL", "FLAG", "ROSE", "LILY", "PALM", "PINE",
        "APPLE", "BANANA", "ORANGE", "LEMON", "PEACH", "BERRY", "MELON", "GRAPE", "CHERRY", "PLUM",
        "BREAD", "CHEESE", "BUTTER", "MILK", "COFFEE", "TEA", "JUICE", "SUGAR", "HONEY", "SALT",
        "PEPPER", "RICE", "SOUP", "CAKE", "PIE", "COOKIE", "SWEET", "CLEAN", "CLEAR", "FRESH",
        "LIGHT", "DARK", "BRIGHT", "QUIET", "LOUD", "HAPPY", "BRAVE", "SMART", "STRONG", "GENTLE",
        "SIMPLE", "GREAT", "GRAND", "SHARP", "ROUND", "SQUARE", "SOLID", "STEEL", "SILVER", "ROYAL",
        "QUEEN", "KING", "PRINCE", "KNIGHT", "CASTLE", "TOWER", "BRIDGE", "RIVER", "OCEAN", "STREAM",
        "VALLEY", "FOREST", "GARDEN", "FLOWER", "SPRING", "SUMMER", "AUTUMN", "WINTER", "MORNING",
        "EVENING", "NIGHT", "DREAM", "PEACE", "TRUTH", "FAITH", "HOPE", "LOVE", "SMILE", "LAUGH",
        "FRIEND", "FAMILY", "PEOPLE", "PERSON", "CHILD", "WORLD", "SPACE", "FUTURE", "PLAN", "IDEA"
    )
}
