package com.example.persistence

import android.content.Context
import com.example.data.model.ComputerDifficulty
import com.example.data.model.GameMode
import com.example.data.model.GameStatistics
import com.example.data.model.MatchSummary
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class StatisticsRepository(context: Context) {
    private val statsFile = File(context.filesDir, "wordgrid_statistics.json")

    @Synchronized
    fun loadStatistics(): GameStatistics {
        if (!statsFile.exists() || statsFile.length() == 0L) {
            return GameStatistics()
        }
        return try {
            val root = JSONObject(statsFile.readText())
            val recentArray = root.optJSONArray("recentMatches") ?: JSONArray()
            val matches = mutableListOf<MatchSummary>()

            for (i in 0 until recentArray.length()) {
                val obj = recentArray.getJSONObject(i)
                val mode = GameMode.valueOf(obj.optString("gameMode", GameMode.COMPUTER.name))
                val diffStr = obj.optString("difficulty", "")
                val diff = if (diffStr.isNotEmpty()) ComputerDifficulty.valueOf(diffStr) else null

                matches.add(
                    MatchSummary(
                        matchId = obj.getString("matchId"),
                        gameMode = mode,
                        difficulty = diff,
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        player1Name = obj.optString("player1Name", "Player 1"),
                        player2Name = obj.optString("player2Name", "Computer"),
                        player1Score = obj.optInt("player1Score", 0),
                        player2Score = obj.optInt("player2Score", 0),
                        winnerName = obj.optString("winnerName").takeIf { it.isNotEmpty() },
                        totalTurns = obj.optInt("totalTurns", 0),
                        bestWord = obj.optString("bestWord").takeIf { it.isNotEmpty() },
                        bestWordScore = obj.optInt("bestWordScore", 0)
                    )
                )
            }

            calculateStatistics(matches)
        } catch (_: Exception) {
            GameStatistics()
        }
    }

    @Synchronized
    fun recordMatch(summary: MatchSummary) {
        val currentStats = loadStatistics()
        // Deduplicate by matchId
        val existingIndex = currentStats.recentMatches.indexOfFirst { it.matchId == summary.matchId }
        val updatedMatches = currentStats.recentMatches.toMutableList()

        if (existingIndex >= 0) {
            updatedMatches[existingIndex] = summary
        } else {
            updatedMatches.add(0, summary) // Newest first
        }

        // Limit to 50 recent matches
        val trimmed = updatedMatches.take(50)
        saveMatches(trimmed)
    }

    @Synchronized
    fun resetStatistics() {
        if (statsFile.exists()) {
            statsFile.delete()
        }
    }

    @Synchronized
    private fun saveMatches(matches: List<MatchSummary>) {
        try {
            val root = JSONObject()
            val array = JSONArray()
            for (m in matches) {
                val obj = JSONObject()
                obj.put("matchId", m.matchId)
                obj.put("gameMode", m.gameMode.name)
                obj.put("difficulty", m.difficulty?.name ?: "")
                obj.put("timestamp", m.timestamp)
                obj.put("player1Name", m.player1Name)
                obj.put("player2Name", m.player2Name)
                obj.put("player1Score", m.player1Score)
                obj.put("player2Score", m.player2Score)
                obj.put("winnerName", m.winnerName ?: "")
                obj.put("totalTurns", m.totalTurns)
                obj.put("bestWord", m.bestWord ?: "")
                obj.put("bestWordScore", m.bestWordScore)
                array.put(obj)
            }
            root.put("recentMatches", array)
            statsFile.writeText(root.toString())
        } catch (_: Exception) {}
    }

    companion object {
        fun calculateStatistics(matches: List<MatchSummary>): GameStatistics {
            if (matches.isEmpty()) return GameStatistics()

            var wins = 0
            var losses = 0
            var draws = 0
            var highestScore = 0
            var bestWord = ""
            var bestWordScore = 0
            var totalPlayerScore = 0

            for (m in matches) {
                val pScore = m.player1Score
                totalPlayerScore += pScore
                if (pScore > highestScore) highestScore = pScore

                if (m.bestWord != null && m.bestWordScore > bestWordScore) {
                    bestWord = m.bestWord
                    bestWordScore = m.bestWordScore
                }

                if (m.winnerName == null) {
                    draws++
                } else if (m.winnerName == m.player1Name) {
                    wins++
                } else {
                    losses++
                }
            }

            val avg = totalPlayerScore.toDouble() / matches.size

            return GameStatistics(
                totalMatches = matches.size,
                wins = wins,
                losses = losses,
                draws = draws,
                highestWord = bestWord,
                highestWordScore = bestWordScore,
                highestGameScore = highestScore,
                averageScore = avg,
                recentMatches = matches
            )
        }
    }
}
