package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.window.Dialog
import com.example.data.model.AppThemeMode
import com.example.data.model.GameSettings
import com.example.data.model.MatchState
import com.example.data.model.MatchStatus
import com.example.data.model.Tile
import com.example.ui.theme.CellBorder
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.HighMultiplierGold
import com.example.ui.theme.IvoryTile
import com.example.ui.theme.LetterBonusSky
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WordBonusLavender

@Composable
fun BlankLetterDialog(
    onLetterSelected: (Char) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SlateSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CellBorder),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Assign Blank Tile",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Choose any letter from A to Z (scores 0 points)",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                val alphabet = ('A'..'Z').toList()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.height(240.dp)
                ) {
                    items(alphabet) { letter ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(IvoryTile)
                                .clickable { onLetterSelected(letter) }
                                .testTag("blank_letter_$letter"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = letter.toString(),
                                color = Color(0xFF1E293B),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun SwapTilesDialog(
    rack: List<Tile>,
    onConfirmSwap: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedIds = remember { mutableStateListOf<String>() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SlateSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CellBorder),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Swap Letter Tiles",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Select tiles to exchange. Swapping consumes your turn.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    for (tile in rack) {
                        val isSelected = selectedIds.contains(tile.id)
                        TileView(
                            tile = tile,
                            size = 40.dp,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelected) selectedIds.remove(tile.id)
                                else selectedIds.add(tile.id)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirmSwap(selectedIds.toList()) },
                        enabled = selectedIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Swap (${selectedIds.size})")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    settings: GameSettings,
    onSaveSettings: (GameSettings) -> Unit,
    onResignClick: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(settings.playerName) }
    var sound by remember { mutableStateOf(settings.soundEnabled) }
    var haptics by remember { mutableStateOf(settings.hapticsEnabled) }
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var showResignConfirm by remember { mutableStateOf(false) }

    if (showResignConfirm) {
        AlertDialog(
            onDismissRequest = { showResignConfirm = false },
            title = { Text("Resign Match?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = { Text("Resigning ends the match and awards the victory to your opponent. Are you sure?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        showResignConfirm = false
                        onDismiss()
                        onResignClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    modifier = Modifier.testTag("confirm_resign_button")
                ) {
                    Text("Resign Match")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResignConfirm = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Settings",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Theme Mode Selector
                Text(
                    text = "Theme Mode",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = themeMode == AppThemeMode.LIGHT,
                        onClick = {
                            themeMode = AppThemeMode.LIGHT
                            onSaveSettings(settings.copy(themeMode = AppThemeMode.LIGHT, isDarkTheme = false))
                        },
                        leadingIcon = {
                            Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Light", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricBlue,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier.weight(1f).testTag("theme_light_chip")
                    )

                    FilterChip(
                        selected = themeMode == AppThemeMode.DARK,
                        onClick = {
                            themeMode = AppThemeMode.DARK
                            onSaveSettings(settings.copy(themeMode = AppThemeMode.DARK, isDarkTheme = true))
                        },
                        leadingIcon = {
                            Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Dark", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricBlue,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier.weight(1f).testTag("theme_dark_chip")
                    )

                    FilterChip(
                        selected = themeMode == AppThemeMode.SYSTEM,
                        onClick = {
                            themeMode = AppThemeMode.SYSTEM
                            onSaveSettings(settings.copy(themeMode = AppThemeMode.SYSTEM))
                        },
                        leadingIcon = {
                            Icon(Icons.Default.BrightnessAuto, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("System", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricBlue,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier.weight(1f).testTag("theme_system_chip")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Player Name", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag("player_name_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Haptic Feedback", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    Switch(
                        checked = haptics,
                        onCheckedChange = { haptics = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = ElectricBlue)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sound Effects", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    Switch(
                        checked = sound,
                        onCheckedChange = { sound = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = ElectricBlue)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Resign Button
                OutlinedButton(
                    onClick = { showResignConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("resign_match_button")
                ) {
                    Text("Resign Match")
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        onSaveSettings(
                            settings.copy(
                                playerName = name.trim().ifEmpty { "You" },
                                soundEnabled = sound,
                                hapticsEnabled = haptics,
                                themeMode = themeMode,
                                isDarkTheme = themeMode == AppThemeMode.DARK
                            )
                        )
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Settings")
                }
            }
        }
    }
}

@Composable
fun HowToPlayDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SlateSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CellBorder),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "How to Play WordGrid",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                RuleItem(
                    title = "1. Starting the Game",
                    body = "The board has 13 rows and 13 columns. The opening word must contain at least 2 letters and cross the centre square marked with a star."
                )

                RuleItem(
                    title = "2. Placing Words",
                    body = "Place tiles in a single continuous row or column without gaps. After the opening, every move must connect to at least one existing tile."
                )

                RuleItem(
                    title = "3. Multiplier Bonus Squares",
                    body = "Sky blue squares multiply letter scores (2× to 7×). Lavender squares multiply whole word scores (2× or 3×). High multipliers (4×–7×) appear only in select matches and are outlined in gold."
                )

                RuleItem(
                    title = "4. Blank Tiles & Bingo",
                    body = "Blank tiles ('?') can represent any letter of your choice and always score 0 points. Using all 7 rack tiles in one move awards a +50 point Bingo bonus!"
                )

                RuleItem(
                    title = "5. Match Ending & Deductions",
                    body = "The game finishes when the bag is empty and one player clears their rack, or after 6 consecutive passes/swaps. Remaining rack tile values are subtracted from scores."
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Got it")
                }
            }
        }
    }
}

@Composable
fun RuleItem(title: String, body: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(text = title, color = ElectricBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text = body, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
fun ResultsDialog(
    matchState: MatchState,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit
) {
    val isWin = matchState.playerScore > matchState.computerScore
    val isDraw = matchState.playerScore == matchState.computerScore
    val isResignation = matchState.status == MatchStatus.FINISHED_RESIGNATION

    val title = when {
        isResignation && matchState.winnerName != "You" -> "Match Resigned"
        isWin -> "Victory!"
        isDraw -> "Game Drawn"
        else -> "Computer Wins"
    }

    val bannerColor = when {
        isWin -> Color(0xFF10B981) // Green
        isDraw -> HighMultiplierGold
        else -> Color(0xFF64748B)
    }

    Dialog(onDismissRequest = {}) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SlateSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CellBorder),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = bannerColor,
                    modifier = Modifier.size(48.dp)
                )

                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Score Card Comparison
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlateCard)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("You", color = TextSecondary, fontSize = 14.sp)
                        Text(
                            text = matchState.playerScore.toString(),
                            color = TextPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (matchState.playerRackDeduction != null) {
                            val sign = if (matchState.playerRackDeduction >= 0) "+${matchState.playerRackDeduction}" else "${matchState.playerRackDeduction}"
                            Text(
                                text = "Adj: $sign",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(48.dp)
                            .background(CellBorder)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(matchState.computerName, color = TextSecondary, fontSize = 14.sp)
                        Text(
                            text = matchState.computerScore.toString(),
                            color = TextPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (matchState.computerRackDeduction != null) {
                            val sign = if (matchState.computerRackDeduction >= 0) "+${matchState.computerRackDeduction}" else "${matchState.computerRackDeduction}"
                            Text(
                                text = "Adj: $sign",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onPlayAgain,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("play_again_button")
                ) {
                    Text("Play Again", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onHome,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("return_home_button")
                ) {
                    Text("Return to Home", color = TextSecondary)
                }
            }
        }
    }
}
