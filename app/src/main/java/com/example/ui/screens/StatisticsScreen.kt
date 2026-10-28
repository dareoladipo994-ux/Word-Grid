package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ComputerDifficulty
import com.example.data.model.GameMode
import com.example.data.model.GameStatistics
import com.example.data.model.MatchSummary
import com.example.ui.theme.BonusLavender
import com.example.ui.theme.BonusSkyBlue
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GoldBorder
import com.example.ui.theme.IvoryTile
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TileText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class StatsFilter {
    ALL,
    COMPUTER_ALL,
    COMPUTER_EASY,
    COMPUTER_MEDIUM,
    COMPUTER_HARD,
    PASS_AND_PLAY,
    LOCAL_WIFI
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    statistics: GameStatistics,
    onBackClick: () -> Unit,
    onResetStatistics: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(StatsFilter.ALL) }
    var showResetDialog by remember { mutableStateOf(false) }

    // Filter matches
    val filteredMatches = remember(statistics.recentMatches, selectedFilter) {
        when (selectedFilter) {
            StatsFilter.ALL -> statistics.recentMatches
            StatsFilter.COMPUTER_ALL -> statistics.recentMatches.filter { it.gameMode == GameMode.COMPUTER }
            StatsFilter.COMPUTER_EASY -> statistics.recentMatches.filter {
                it.gameMode == GameMode.COMPUTER && it.difficulty == ComputerDifficulty.EASY
            }
            StatsFilter.COMPUTER_MEDIUM -> statistics.recentMatches.filter {
                it.gameMode == GameMode.COMPUTER && (it.difficulty == ComputerDifficulty.BALANCED || it.difficulty == null)
            }
            StatsFilter.COMPUTER_HARD -> statistics.recentMatches.filter {
                it.gameMode == GameMode.COMPUTER && it.difficulty == ComputerDifficulty.HARD
            }
            StatsFilter.PASS_AND_PLAY -> statistics.recentMatches.filter { it.gameMode == GameMode.PASS_AND_PLAY }
            StatsFilter.LOCAL_WIFI -> statistics.recentMatches.filter { it.gameMode == GameMode.LOCAL_WIFI }
        }
    }

    // Dynamic stats for the current filter
    val filterStats = remember(filteredMatches) {
        if (filteredMatches.isEmpty()) {
            GameStatistics()
        } else {
            var wins = 0
            var losses = 0
            var draws = 0
            var highestScore = 0
            var bestWord = ""
            var bestWordScore = 0
            var totalPlayerScore = 0

            for (m in filteredMatches) {
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

            val avg = totalPlayerScore.toDouble() / filteredMatches.size
            GameStatistics(
                totalMatches = filteredMatches.size,
                wins = wins,
                losses = losses,
                draws = draws,
                highestWord = bestWord,
                highestWordScore = bestWordScore,
                highestGameScore = highestScore,
                averageScore = avg,
                recentMatches = filteredMatches
            )
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Statistics?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will permanently clear your match history and all lifetime statistics. This cannot be undone.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        onResetStatistics()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFBA1A1A))
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SlateSurface
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("statistics_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Personal Statistics",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("stats_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    if (statistics.totalMatches > 0) {
                        IconButton(
                            onClick = { showResetDialog = true },
                            modifier = Modifier.testTag("reset_stats_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Reset Stats",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background)
            )
        },
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter chips row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatsChip("All", selectedFilter == StatsFilter.ALL) { selectedFilter = StatsFilter.ALL }
                StatsChip("Computer", selectedFilter == StatsFilter.COMPUTER_ALL) { selectedFilter = StatsFilter.COMPUTER_ALL }
                StatsChip("Easy", selectedFilter == StatsFilter.COMPUTER_EASY) { selectedFilter = StatsFilter.COMPUTER_EASY }
                StatsChip("Medium", selectedFilter == StatsFilter.COMPUTER_MEDIUM) { selectedFilter = StatsFilter.COMPUTER_MEDIUM }
                StatsChip("Hard", selectedFilter == StatsFilter.COMPUTER_HARD) { selectedFilter = StatsFilter.COMPUTER_HARD }
                StatsChip("Pass & Play", selectedFilter == StatsFilter.PASS_AND_PLAY) { selectedFilter = StatsFilter.PASS_AND_PLAY }
                StatsChip("Local Wi-Fi", selectedFilter == StatsFilter.LOCAL_WIFI) { selectedFilter = StatsFilter.LOCAL_WIFI }
            }

            // Summary Metric Cards 2x2 Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatMetricCard(
                        title = "Matches Played",
                        value = "${filterStats.totalMatches}",
                        subtitle = "${filterStats.wins}W - ${filterStats.losses}L - ${filterStats.draws}D",
                        icon = Icons.Default.Timeline,
                        accentColor = ElectricBlue,
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricCard(
                        title = "Win Rate",
                        value = "${filterStats.winRatePercent}%",
                        subtitle = if (filterStats.totalMatches > 0) "${filterStats.wins} wins" else "No matches",
                        icon = Icons.Default.EmojiEvents,
                        accentColor = GoldBorder,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatMetricCard(
                        title = "Best Word",
                        value = filterStats.highestWord.ifEmpty { "—" },
                        subtitle = if (filterStats.highestWordScore > 0) "${filterStats.highestWordScore} pts" else "None",
                        icon = Icons.Default.Star,
                        accentColor = BonusLavender,
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricCard(
                        title = "High Score",
                        value = if (filterStats.highestGameScore > 0) "${filterStats.highestGameScore}" else "—",
                        subtitle = "Avg: ${String.format(Locale.US, "%.1f", filterStats.averageScore)} pts",
                        icon = Icons.Default.Speed,
                        accentColor = BonusSkyBlue,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Recent matches list header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Matches (${filteredMatches.size})",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Last 50",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            if (filteredMatches.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No completed matches recorded for this filter yet.",
                        color = TextMuted,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredMatches) { match ->
                        RecentMatchItem(match = match)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = ElectricBlue,
            selectedLabelColor = TextPrimary,
            containerColor = SlateCard,
            labelColor = TextSecondary
        ),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun StatMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun RecentMatchItem(match: MatchSummary) {
    val dateStr = remember(match.timestamp) {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        sdf.format(Date(match.timestamp))
    }

    val isUserWinner = match.winnerName == match.player1Name
    val isDraw = match.winnerName == null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val modeLabel = when (match.gameMode) {
                        GameMode.COMPUTER -> "vs ${match.difficulty?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Computer"}"
                        GameMode.PASS_AND_PLAY -> "Pass & Play"
                        GameMode.LOCAL_WIFI -> "Wi-Fi Match"
                    }
                    Text(
                        text = modeLabel,
                        color = ElectricBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = dateStr,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${match.player1Name} vs ${match.player2Name}",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                if (match.bestWord != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Best word: ${match.bestWord} (${match.bestWordScore} pts)",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${match.player1Score} - ${match.player2Score}",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                val (resultText, resultColor) = when {
                    isDraw -> "Draw" to TextSecondary
                    isUserWinner -> "Victory" to GoldBorder
                    else -> "Defeat" to TextMuted
                }
                Text(
                    text = resultText,
                    color = resultColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
