package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GameMode
import com.example.data.model.MatchCategory
import com.example.data.model.PlayerTurn
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBluePill
import com.example.ui.theme.HighMultiplierGold
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ScoreHeader(
    playerName: String,
    playerScore: Int,
    opponentName: String,
    opponentScore: Int,
    currentTurn: PlayerTurn,
    gameMode: GameMode,
    isComputerThinking: Boolean,
    matchCategory: MatchCategory,
    moveCount: Int,
    onHomeClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onResignClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Top App Bar: Home + WordGrid Logo + Match Category + (History + Resign + Settings)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onHomeClick,
                    modifier = Modifier.testTag("home_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Return Home",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = null,
                    tint = ElectricBluePill,
                    modifier = Modifier.size(24.dp)
                )

                Text(
                    text = "WordGrid",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                if (matchCategory != MatchCategory.STANDARD) {
                    val tagText = if (matchCategory == MatchCategory.BOOSTED) "Boosted" else "Rare"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(HighMultiplierGold.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = tagText,
                            color = HighMultiplierGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onHistoryClick,
                    modifier = Modifier.testTag("move_history_button")
                ) {
                    if (moveCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = ElectricBlue,
                                    contentColor = Color.White
                                ) {
                                    Text(moveCount.toString(), fontSize = 10.sp)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Move History",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Move History",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                if (onResignClick != null) {
                    IconButton(
                        onClick = onResignClick,
                        modifier = Modifier.testTag("header_resign_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = "Resign Match",
                            tint = Color(0xFFEF4444).copy(alpha = 0.85f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Scorecards and Turn indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Player 1 Score Card (Left)
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.testTag("player_score_card")
            ) {
                Text(
                    text = playerName,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = playerScore.toString(),
                    color = TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                // Active underline indicator
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(
                            if (currentTurn == PlayerTurn.HUMAN) ElectricBluePill else Color.Transparent
                        )
                )
            }

            // Center Turn Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (currentTurn == PlayerTurn.HUMAN) ElectricBluePill else Color(0xFF273549)
                    )
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .testTag("turn_indicator_badge")
            ) {
                val turnText = when {
                    isComputerThinking -> "Thinking…"
                    currentTurn == PlayerTurn.HUMAN -> {
                        if (gameMode == GameMode.PASS_AND_PLAY) "$playerName's turn" else "Your turn"
                    }
                    else -> {
                        if (gameMode == GameMode.COMPUTER) "$opponentName's turn" else "$opponentName's turn"
                    }
                }
                Text(
                    text = turnText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Player 2 / Opponent Score Card (Right)
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.testTag("computer_score_card")
            ) {
                val label = when (gameMode) {
                    GameMode.COMPUTER -> "Computer ($opponentName)"
                    GameMode.PASS_AND_PLAY -> opponentName
                    GameMode.LOCAL_WIFI -> opponentName
                }
                Text(
                    text = label,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = opponentScore.toString(),
                    color = TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                // Active underline indicator
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(
                            if (currentTurn == PlayerTurn.COMPUTER) ElectricBluePill else Color.Transparent
                        )
                )
            }
        }
    }
}
