package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Tile
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LetterBonusSky
import com.example.ui.theme.SlateCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WordBonusLavender

@Composable
fun BoardLegendBar(
    tilesRemaining: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Legend on Left
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Letter bonus item
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(LetterBonusSky)
                )
                Text(
                    text = "Letter bonus",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Word bonus item
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(WordBonusLavender)
                )
                Text(
                    text = "Word bonus",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Tiles remaining on Right
        Text(
            text = "$tilesRemaining tiles left",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.testTag("tiles_remaining_counter")
        )
    }
}

@Composable
fun PlayerRackView(
    rack: List<Tile>,
    selectedTileId: String?,
    onTileClick: (Tile) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("player_rack_row"),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 7) {
                val tile = rack.getOrNull(i)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(0.88f),
                    contentAlignment = Alignment.Center
                ) {
                    if (tile != null) {
                        TileView(
                            tile = tile,
                            modifier = Modifier.fillMaxSize(),
                            isSelected = tile.id == selectedTileId,
                            onClick = { onTileClick(tile) }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SlateCard.copy(alpha = 0.4f))
                                .border(1.dp, Color(0xFF273549), RoundedCornerShape(8.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GameActionBar(
    isHumanTurn: Boolean,
    isComputerThinking: Boolean,
    hasDraftTiles: Boolean,
    prospectiveScore: Int?,
    prospectiveWord: String? = null,
    validationError: String?,
    canSwap: Boolean,
    onRecall: () -> Unit,
    onShuffle: () -> Unit,
    onSwap: () -> Unit,
    onPass: () -> Unit,
    onPlayWord: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Live Score Preview Floating Badge when draft word is formed
        if (hasDraftTiles && prospectiveScore != null && validationError == null) {
            Box(
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF059669))
                    .border(1.5.dp, Color(0xFF34D399), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .testTag("live_score_preview_badge")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!prospectiveWord.isNullOrBlank()) {
                        Text(
                            text = "$prospectiveWord  • ",
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "+$prospectiveScore pts",
                        color = Color(0xFFFDE047),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Status / Hint Line
        val hintText = when {
            isComputerThinking -> "Computer is thinking…"
            !isHumanTurn -> "Waiting for opponent move"
            validationError != null && hasDraftTiles -> validationError
            hasDraftTiles && prospectiveScore != null -> "Valid word! Multipliers applied"
            else -> "Select a tile, then tap the board."
        }
        val hintColor = when {
            validationError != null && hasDraftTiles -> Color(0xFFF87171) // Soft red error
            hasDraftTiles && prospectiveScore != null -> Color(0xFF6EE7B7) // Mint green score
            else -> TextSecondary
        }

        Text(
            text = hintText,
            color = hintColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .testTag("action_hint_text")
        )

        // Utility actions row: Recall, Shuffle, Swap, Pass
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionIconItem(
                icon = Icons.Default.Cached,
                label = "Recall",
                enabled = isHumanTurn && !isComputerThinking && hasDraftTiles,
                onClick = onRecall,
                testTag = "recall_button"
            )

            ActionIconItem(
                icon = Icons.Default.Shuffle,
                label = "Shuffle",
                enabled = isHumanTurn && !isComputerThinking,
                onClick = onShuffle,
                testTag = "shuffle_button"
            )

            ActionIconItem(
                icon = Icons.Default.SwapHoriz,
                label = "Swap",
                enabled = isHumanTurn && !isComputerThinking && canSwap,
                onClick = onSwap,
                testTag = "swap_button"
            )

            ActionIconItem(
                icon = Icons.Default.FastForward,
                label = "Pass",
                enabled = isHumanTurn && !isComputerThinking,
                onClick = onPass,
                testTag = "pass_button"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main "Play word" button with dynamic label: "Play [WORD] (+24 pts)"
        val canSubmit = isHumanTurn && !isComputerThinking && hasDraftTiles
        val isValid = canSubmit && prospectiveScore != null && validationError == null
        val buttonText = if (isValid) {
            if (!prospectiveWord.isNullOrBlank()) {
                "Play $prospectiveWord (+$prospectiveScore pts)"
            } else {
                "Play (+$prospectiveScore pts)"
            }
        } else {
            "Play word"
        }

        Button(
            onClick = onPlayWord,
            enabled = canSubmit,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isValid) ElectricBlue else Color(0xFF2E3D56),
                disabledContainerColor = Color(0xFF1E2D4A)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("play_word_button")
        ) {
            Text(
                text = buttonText,
                color = if (canSubmit) Color.White else Color(0xFF64748B),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ActionIconItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val tint = if (enabled) TextPrimary else TextMuted.copy(alpha = 0.5f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = tint,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
