package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Tile
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.HighMultiplierGold
import com.example.ui.theme.IvoryTile
import com.example.ui.theme.IvoryTileBevel
import com.example.ui.theme.TileSubscript
import com.example.ui.theme.TileText

@Composable
fun TileView(
    tile: Tile,
    modifier: Modifier = Modifier,
    size: Dp = Dp.Unspecified,
    isSelected: Boolean = false,
    isDraft: Boolean = false,
    isHighlighted: Boolean = false,
    hasGoldBorder: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val borderColor = when {
        isSelected -> ElectricBlue
        isDraft -> ElectricBlue.copy(alpha = 0.85f)
        hasGoldBorder -> HighMultiplierGold
        isHighlighted -> ElectricBlue
        else -> IvoryTileBevel
    }

    val borderWidth = when {
        isSelected -> 2.5.dp
        isDraft -> 2.dp
        hasGoldBorder -> 2.dp
        else -> 1.dp
    }

    val boxModifier = if (size != Dp.Unspecified && size > 0.dp) {
        modifier.size(size)
    } else {
        modifier
    }

    BoxWithConstraints(
        modifier = boxModifier
            .shadow(if (isDraft) 4.dp else 2.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(IvoryTile)
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else Modifier
            )
            .testTag("tile_${tile.letter}_${tile.id}"),
        contentAlignment = Alignment.Center
    ) {
        val minDimension = if (maxWidth < maxHeight) maxWidth else maxHeight
        val effectiveSize = if (minDimension > 0.dp) minDimension else 44.dp
        val letterFontSize = (effectiveSize.value * 0.46f).coerceAtLeast(10f).sp
        val subscriptFontSize = (effectiveSize.value * 0.22f).coerceAtLeast(8f).sp

        // Main letter
        Text(
            text = tile.displayLetter.toString(),
            color = TileText,
            fontSize = letterFontSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )

        // Subscript point value (bottom-right)
        if (!tile.isBlank || tile.assignedLetter != null) {
            val scoreText = if (tile.isBlank) "0" else tile.value.toString()
            Text(
                text = scoreText,
                color = TileSubscript,
                fontSize = subscriptFontSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 2.dp)
            )
        }
    }
}
