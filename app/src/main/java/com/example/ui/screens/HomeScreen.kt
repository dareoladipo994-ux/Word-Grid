package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ComputerDifficulty
import com.example.data.model.GameMode
import com.example.data.model.MatchState
import com.example.data.model.PlayerTurn
import com.example.ui.components.NewGameModal
import com.example.ui.theme.CellBorder
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBluePill
import com.example.ui.theme.GoldBorder
import com.example.ui.theme.IvoryTile
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TileText

@Composable
fun HomeScreen(
    hasSavedMatch: Boolean,
    savedMatch: MatchState?,
    onStartComputerGame: (difficulty: ComputerDifficulty, playerName: String) -> Unit,
    onStartPassAndPlay: (p1Name: String, p2Name: String) -> Unit,
    onStartWifiHost: (roomName: String, p1Name: String) -> Unit,
    onStartWifiJoin: (hostIp: String, p2Name: String) -> Unit,
    onResumeGameClick: () -> Unit,
    onStatisticsClick: () -> Unit,
    onHowToPlayClick: () -> Unit,
    onSettingsClick: () -> Unit,
    localIpAddress: String = "192.168.1.100",
    defaultPlayerName: String = "You",
    discoveredRooms: List<com.example.network.DiscoveredRoom> = emptyList(),
    onRefreshDiscovery: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showNewGameModal by remember { mutableStateOf(false) }

    val isUnfinished = savedMatch != null && !savedMatch.isGameOver && savedMatch.gameMode != GameMode.LOCAL_WIFI

    if (showNewGameModal) {
        NewGameModal(
            onDismiss = { showNewGameModal = false },
            onStartComputerGame = { diff, pName ->
                showNewGameModal = false
                onStartComputerGame(diff, pName)
            },
            onStartPassAndPlay = { p1, p2 ->
                showNewGameModal = false
                onStartPassAndPlay(p1, p2)
            },
            onStartWifiHost = { roomName, p1 ->
                showNewGameModal = false
                onStartWifiHost(roomName, p1)
            },
            onStartWifiJoin = { ip, p2 ->
                showNewGameModal = false
                onStartWifiJoin(ip, p2)
            },
            hasSavedUnfinishedMatch = isUnfinished,
            defaultPlayerName = defaultPlayerName,
            localIpAddress = localIpAddress,
            discoveredRooms = discoveredRooms,
            onRefreshDiscovery = onRefreshDiscovery
        )
    }

    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header Graphic & App Title
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf('W', 'O', 'R', 'D').forEach { char ->
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(IvoryTile)
                            .border(1.dp, Color(0xFFDDD9C4), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = char.toString(),
                            color = TileText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "WordGrid",
                color = TextPrimary,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )

            Text(
                text = "Offline Word-Board Challenge",
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
            )

            // Continue Match Card (if active unfinished match exists)
            if (hasSavedMatch && isUnfinished && savedMatch != null) {
                Card(
                    onClick = onResumeGameClick,
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("continue_game_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val modeLabel = when (savedMatch.gameMode) {
                                GameMode.COMPUTER -> "vs ${savedMatch.computerName} (${savedMatch.computerDifficulty.name.lowercase().replaceFirstChar { it.uppercase() }})"
                                GameMode.PASS_AND_PLAY -> "Pass & Play"
                                GameMode.LOCAL_WIFI -> "Local Wi-Fi"
                            }
                            Text(
                                text = modeLabel,
                                color = ElectricBluePill,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${savedMatch.totalTilesRemaining} tiles in bag",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${savedMatch.player1Name}: ${savedMatch.playerScore} pts",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${savedMatch.player2Name}: ${savedMatch.computerScore} pts",
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val turnWho = if (savedMatch.currentTurn == PlayerTurn.HUMAN) {
                                    "${savedMatch.player1Name}'s turn"
                                } else {
                                    "${savedMatch.player2Name}'s turn"
                                }
                                Text(
                                    text = "Current: $turnWho",
                                    color = GoldBorder,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Resume",
                                tint = ElectricBlue,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }

            // Primary Actions: New Game
            Button(
                onClick = { showNewGameModal = true },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("new_game_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isUnfinished) "Start New Match" else "Play WordGrid",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Statistics Button
            OutlinedButton(
                onClick = onStatisticsClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                border = androidx.compose.foundation.BorderStroke(1.dp, CellBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("home_statistics_button")
            ) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Personal Statistics", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // How to Play Button
            OutlinedButton(
                onClick = onHowToPlayClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                border = androidx.compose.foundation.BorderStroke(1.dp, CellBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("how_to_play_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("How to Play", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Settings Button
            OutlinedButton(
                onClick = onSettingsClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                border = androidx.compose.foundation.BorderStroke(1.dp, CellBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("home_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Settings", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
