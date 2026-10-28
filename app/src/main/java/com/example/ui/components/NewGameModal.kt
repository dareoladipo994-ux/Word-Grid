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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ComputerDifficulty
import com.example.data.model.GameMode
import com.example.network.DiscoveredRoom
import com.example.ui.theme.CellBorder
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GoldBorder
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun NewGameModal(
    onDismiss: () -> Unit,
    onStartComputerGame: (difficulty: ComputerDifficulty, playerName: String) -> Unit,
    onStartPassAndPlay: (p1Name: String, p2Name: String) -> Unit,
    onStartWifiHost: (roomName: String, p1Name: String) -> Unit,
    onStartWifiJoin: (hostIp: String, p2Name: String) -> Unit,
    hasSavedUnfinishedMatch: Boolean = false,
    defaultPlayerName: String = "You",
    localIpAddress: String = "192.168.1.100",
    discoveredRooms: List<DiscoveredRoom> = emptyList(),
    onRefreshDiscovery: () -> Unit = {}
) {
    var selectedMode by remember { mutableStateOf(GameMode.COMPUTER) }
    var selectedDifficulty by remember { mutableStateOf(ComputerDifficulty.BALANCED) }
    var p1Name by remember { mutableStateOf(defaultPlayerName) }
    var p2Name by remember { mutableStateOf("Player 2") }
    var roomNameInput by remember { mutableStateOf("${defaultPlayerName}'s Room") }
    var hostIpInput by remember { mutableStateOf("") }
    var isWifiHostSelected by remember { mutableStateOf(true) }
    var showOverwriteConfirm by remember { mutableStateOf(false) }
    var pendingStartAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (showOverwriteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showOverwriteConfirm = false
                pendingStartAction = null
            },
            title = { Text("Replace Unfinished Match?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Starting a new match will replace your unfinished game. Are you sure you want to proceed?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOverwriteConfirm = false
                        pendingStartAction?.invoke()
                        pendingStartAction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    modifier = Modifier.testTag("confirm_overwrite_button")
                ) {
                    Text("Proceed & Replace")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showOverwriteConfirm = false
                        pendingStartAction = null
                    }
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SlateSurface
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .testTag("new_game_modal"),
            color = SlateSurface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Start New Match",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mode Selector Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeTabItem(
                        icon = Icons.Default.SmartToy,
                        label = "Computer",
                        isSelected = selectedMode == GameMode.COMPUTER,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedMode = GameMode.COMPUTER }
                    )
                    ModeTabItem(
                        icon = Icons.Default.Group,
                        label = "Pass & Play",
                        isSelected = selectedMode == GameMode.PASS_AND_PLAY,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedMode = GameMode.PASS_AND_PLAY }
                    )
                    ModeTabItem(
                        icon = Icons.Default.Wifi,
                        label = "Local Wi-Fi",
                        isSelected = selectedMode == GameMode.LOCAL_WIFI,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedMode = GameMode.LOCAL_WIFI
                            onRefreshDiscovery()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedMode) {
                    GameMode.COMPUTER -> {
                        Text(
                            text = "Computer Difficulty",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DifficultyOption(
                                label = "Easy",
                                desc = "Simpler words",
                                isSelected = selectedDifficulty == ComputerDifficulty.EASY,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedDifficulty = ComputerDifficulty.EASY }
                            )
                            DifficultyOption(
                                label = "Medium",
                                desc = "Balanced play",
                                isSelected = selectedDifficulty == ComputerDifficulty.BALANCED,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedDifficulty = ComputerDifficulty.BALANCED }
                            )
                            DifficultyOption(
                                label = "Hard",
                                desc = "High scoring",
                                isSelected = selectedDifficulty == ComputerDifficulty.HARD,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedDifficulty = ComputerDifficulty.HARD }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = p1Name,
                            onValueChange = { p1Name = it },
                            label = { Text("Your Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("player_name_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = CellBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedLabelColor = ElectricBlue,
                                unfocusedLabelColor = TextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                val action = {
                                    onDismiss()
                                    onStartComputerGame(selectedDifficulty, p1Name.ifBlank { "You" })
                                }
                                if (hasSavedUnfinishedMatch) {
                                    pendingStartAction = action
                                    showOverwriteConfirm = true
                                } else {
                                    action()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("start_game_confirm_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                        ) {
                            Text("Start Game", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    GameMode.PASS_AND_PLAY -> {
                        Text(
                            text = "Two players alternate turns on this device with private rack handover screens.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = p1Name,
                            onValueChange = { p1Name = it },
                            label = { Text("Player 1 Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = CellBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedLabelColor = ElectricBlue,
                                unfocusedLabelColor = TextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = p2Name,
                            onValueChange = { p2Name = it },
                            label = { Text("Player 2 Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = CellBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedLabelColor = ElectricBlue,
                                unfocusedLabelColor = TextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                val action = {
                                    onDismiss()
                                    onStartPassAndPlay(p1Name.ifBlank { "Player 1" }, p2Name.ifBlank { "Player 2" })
                                }
                                if (hasSavedUnfinishedMatch) {
                                    pendingStartAction = action
                                    showOverwriteConfirm = true
                                } else {
                                    action()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("start_game_confirm_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                        ) {
                            Text("Start Pass & Play", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    GameMode.LOCAL_WIFI -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = isWifiHostSelected,
                                onClick = { isWifiHostSelected = true },
                                label = { Text("Create Room") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue,
                                    selectedLabelColor = TextPrimary
                                ),
                                modifier = Modifier.testTag("wifi_create_room_tab")
                            )
                            FilterChip(
                                selected = !isWifiHostSelected,
                                onClick = {
                                    isWifiHostSelected = false
                                    onRefreshDiscovery()
                                },
                                label = { Text("Join Room") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue,
                                    selectedLabelColor = TextPrimary
                                ),
                                modifier = Modifier.testTag("wifi_join_room_tab")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (isWifiHostSelected) {
                            OutlinedTextField(
                                value = roomNameInput,
                                onValueChange = { roomNameInput = it },
                                label = { Text("Room Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("wifi_room_name_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricBlue,
                                    unfocusedBorderColor = CellBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = p1Name,
                                onValueChange = { p1Name = it },
                                label = { Text("Your Player Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("wifi_host_player_name_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricBlue,
                                    unfocusedBorderColor = CellBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Card(
                                colors = CardDefaults.cardColors(containerColor = SlateCard),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Your IP: $localIpAddress",
                                        color = GoldBorder,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Ensure the other phone is connected to the same Wi-Fi or your mobile hotspot.",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    onDismiss()
                                    onStartWifiHost(
                                        roomNameInput.ifBlank { "${p1Name}'s Room" },
                                        p1Name.ifBlank { "Host" }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("create_room_confirm_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                            ) {
                                Text("Create Room & Open Lobby", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            // Join Room
                            OutlinedTextField(
                                value = p1Name,
                                onValueChange = { p1Name = it },
                                label = { Text("Your Player Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricBlue,
                                    unfocusedBorderColor = CellBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Nearby Rooms (${discoveredRooms.size})",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = onRefreshDiscovery, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Scan for Rooms",
                                        tint = ElectricBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (discoveredRooms.isEmpty()) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Scanning local Wi-Fi for rooms...\nOr enter Host IP below.",
                                            color = TextMuted,
                                            fontSize = 12.sp,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(modifier = Modifier.height(110.dp)) {
                                    items(discoveredRooms) { room ->
                                        RoomItemRow(
                                            room = room,
                                            onJoin = {
                                                onDismiss()
                                                onStartWifiJoin(room.hostIp, p1Name.ifBlank { "Guest" })
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Manual IP Fallback",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = hostIpInput,
                                    onValueChange = { hostIpInput = it },
                                    placeholder = { Text("e.g. 192.168.1.100") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("manual_ip_input"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElectricBlue,
                                        unfocusedBorderColor = CellBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )
                                Button(
                                    onClick = {
                                        if (hostIpInput.isNotBlank()) {
                                            onDismiss()
                                            onStartWifiJoin(hostIpInput.trim(), p1Name.ifBlank { "Guest" })
                                        }
                                    },
                                    enabled = hostIpInput.isNotBlank(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                    modifier = Modifier.testTag("manual_join_button")
                                ) {
                                    Text("Join")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomItemRow(
    room: DiscoveredRoom,
    onJoin: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, CellBorder),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = room.roomName,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Host: ${room.hostName} (${room.hostIp})",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                if (!room.isCompatible) {
                    Text(
                        text = "Incompatible version",
                        color = Color(0xFFE53935),
                        fontSize = 10.sp
                    )
                } else if (room.isFull || room.isInMatch) {
                    Text(
                        text = if (room.isInMatch) "Match in progress" else "Room full",
                        color = GoldBorder,
                        fontSize = 10.sp
                    )
                }
            }

            val canJoin = room.isCompatible && !room.isFull && !room.isInMatch
            Button(
                onClick = onJoin,
                enabled = canJoin,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricBlue,
                    disabledContainerColor = CellBorder
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("join_room_button_${room.hostIp}")
            ) {
                Text(if (canJoin) "Join" else "Unavailable", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ModeTabItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) ElectricBlue.copy(alpha = 0.2f) else SlateCard)
            .border(
                width = 1.dp,
                color = if (isSelected) ElectricBlue else CellBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) ElectricBlue else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = if (isSelected) TextPrimary else TextSecondary,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DifficultyOption(
    label: String,
    desc: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) ElectricBlue.copy(alpha = 0.2f) else SlateCard)
            .border(
                width = 1.dp,
                color = if (isSelected) ElectricBlue else CellBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = if (isSelected) ElectricBlue else TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                color = TextMuted,
                fontSize = 10.sp
            )
        }
    }
}
