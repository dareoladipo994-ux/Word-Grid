package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BoardCell
import com.example.data.model.BonusCategory
import com.example.engine.BoardGenerator
import com.example.ui.theme.CellBorder
import com.example.ui.theme.CellNeutral
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.HighMultiplierGold
import com.example.ui.theme.LetterBonusDark
import com.example.ui.theme.LetterBonusSky
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.WordBonusDark
import com.example.ui.theme.WordBonusLavender
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun BoardView(
    board: List<List<BoardCell>>,
    lastComputerMoveCells: List<Pair<Int, Int>>,
    onCellClick: (row: Int, col: Int) -> Unit,
    onBonusSquareInfo: (String) -> Unit,
    shakeTrigger: Long = 0L,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val shakeOffset = remember { Animatable(0f) }
    val hapticFeedback = LocalHapticFeedback.current

    // Shake animation when an invalid word is submitted
    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger > 0L) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            val keyframes = listOf(-16f, 15f, -12f, 10f, -6f, 3f, 0f)
            for (kf in keyframes) {
                shakeOffset.animateTo(
                    targetValue = kf,
                    animationSpec = tween(durationMillis = 35, easing = LinearEasing)
                )
            }
        }
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 2.5f)
        scale = newScale
        if (newScale > 1f) {
            val maxOffset = 180f * (newScale - 1f)
            offset = Offset(
                x = (offset.x + panChange.x).coerceIn(-maxOffset, maxOffset),
                y = (offset.y + panChange.y).coerceIn(-maxOffset, maxOffset)
            )
        } else {
            offset = Offset.Zero
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NavyBackground)
            .transformable(state = transformState)
            .testTag("game_board_viewport"),
        contentAlignment = Alignment.Center
    ) {
        val availableWidth = maxWidth
        // 13 columns + spacing + padding
        val cellSpacing = 2.dp
        val boardPadding = 6.dp
        val totalSpacing = cellSpacing * (BoardGenerator.BOARD_SIZE - 1) + (boardPadding * 2)
        val cellSize = ((availableWidth - totalSpacing) / BoardGenerator.BOARD_SIZE).coerceAtLeast(24.dp)

        Box(
            modifier = Modifier
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x + shakeOffset.value,
                    translationY = offset.y
                )
                .padding(boardPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(cellSpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (r in 0 until BoardGenerator.BOARD_SIZE) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(cellSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (c in 0 until BoardGenerator.BOARD_SIZE) {
                            val cell = board[r][c]
                            val isHighlighted = lastComputerMoveCells.contains(r to c)

                            BoardCellItem(
                                cell = cell,
                                cellSize = cellSize,
                                isHighlighted = isHighlighted,
                                onClick = {
                                    if (cell.tile != null) {
                                        onCellClick(r, c)
                                    } else if (cell.bonus != null) {
                                        onCellClick(r, c)
                                        onBonusSquareInfo(cell.bonus.accessibleDescription)
                                    } else {
                                        onCellClick(r, c)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Reset View button if zoomed or panned
        if (scale > 1.05f || offset != Offset.Zero) {
            SmallFloatingActionButton(
                onClick = {
                    scale = 1f
                    offset = Offset.Zero
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .testTag("reset_view_button"),
                containerColor = ElectricBlue,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "Reset Board View",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun BoardCellItem(
    cell: BoardCell,
    cellSize: androidx.compose.ui.unit.Dp,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val cornerRadius = 4.dp
    val bonus = cell.bonus

    val isLetterBonus = bonus?.category == BonusCategory.LETTER
    val isWordBonus = bonus?.category == BonusCategory.WORD
    val hasGoldBorder = bonus?.isHighMultiplier == true

    // Slide-in animation for tiles when placed on the board
    val slideOffsetY = remember { Animatable(0f) }
    val tileAlpha = remember { Animatable(1f) }

    LaunchedEffect(cell.tile?.id) {
        if (cell.tile != null) {
            slideOffsetY.snapTo(-24f)
            tileAlpha.snapTo(0.2f)
            launch {
                slideOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            launch {
                tileAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 180)
                )
            }
        }
    }

    val cellBackground = when {
        cell.tile != null -> Color.Transparent
        isLetterBonus -> LetterBonusSky
        isWordBonus -> WordBonusLavender
        else -> CellNeutral
    }

    val cellBorder = when {
        isHighlighted -> ElectricBlue
        hasGoldBorder -> HighMultiplierGold
        else -> CellBorder
    }

    val borderWidth = when {
        isHighlighted -> 2.dp
        hasGoldBorder -> 1.5.dp
        else -> 0.75.dp
    }

    Box(
        modifier = Modifier
            .size(cellSize)
            .clip(RoundedCornerShape(cornerRadius))
            .background(cellBackground)
            .border(borderWidth, cellBorder, RoundedCornerShape(cornerRadius))
            .clickable(onClick = onClick)
            .testTag("board_cell_${cell.row}_${cell.col}"),
        contentAlignment = Alignment.Center
    ) {
        if (cell.tile != null) {
            TileView(
                tile = cell.tile,
                size = cellSize,
                isDraft = cell.isDraft,
                isHighlighted = isHighlighted,
                hasGoldBorder = hasGoldBorder,
                onClick = onClick,
                modifier = Modifier.graphicsLayer {
                    translationY = slideOffsetY.value.dp.toPx()
                    alpha = tileAlpha.value
                }
            )
        } else if (bonus != null) {
            // Unoccupied bonus square
            val textColor = if (isLetterBonus) LetterBonusDark else WordBonusDark
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = bonus.displayLabel,
                    color = textColor,
                    fontSize = (cellSize.value * 0.36f).sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = (cellSize.value * 0.36f).sp
                )
                // Small category indicator marker
                if (isLetterBonus) {
                    Box(
                        modifier = Modifier
                            .size((cellSize.value * 0.12f).dp)
                            .clip(CircleShape)
                            .background(textColor)
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        Box(
                            modifier = Modifier
                                .size((cellSize.value * 0.10f).dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(textColor)
                        )
                        Box(
                            modifier = Modifier
                                .size((cellSize.value * 0.10f).dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(textColor)
                        )
                    }
                }
            }
        } else if (cell.isCenter) {
            // Starting center star
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Starting Centre Square",
                tint = Color(0xFF64748B),
                modifier = Modifier.size((cellSize.value * 0.5f).dp)
            )
        }
    }
}
