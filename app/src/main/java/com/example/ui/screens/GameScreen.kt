package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GameMode
import com.example.data.model.PlayerTurn
import com.example.ui.WordGridUiState
import com.example.ui.WordGridViewModel
import com.example.ui.components.BlankLetterDialog
import com.example.ui.components.BoardLegendBar
import com.example.ui.components.BoardView
import com.example.ui.components.GameActionBar
import com.example.ui.components.HowToPlayDialog
import com.example.ui.components.MoveHistoryDrawer
import com.example.ui.components.PassAndPlayHandoverOverlay
import com.example.ui.components.PlayerRackView
import com.example.ui.components.ResultsDialog
import com.example.ui.components.ScoreHeader
import com.example.ui.components.SettingsDialog
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.SlateCard
import kotlinx.coroutines.delay

@Composable
fun GameScreen(
    viewModel: WordGridViewModel,
    uiState: WordGridUiState,
    modifier: Modifier = Modifier
) {
    val match = uiState.matchState ?: return
    val snackbarHostState = remember { SnackbarHostState() }
    var showMoveHistoryDrawer by remember { mutableStateOf(false) }

    BackHandler {
        if (match.gameMode == GameMode.LOCAL_WIFI && !match.isGameOver) {
            viewModel.promptLeaveWifiMatch()
        } else {
            viewModel.navigateToHome()
        }
    }

    // Auto-dismiss info toast after 2.5 seconds
    LaunchedEffect(uiState.infoToastMessage) {
        if (uiState.infoToastMessage != null) {
            delay(2500)
            viewModel.clearInfoToast()
        }
    }

    // Determine active rack and current turn authorization
    val isPassAndPlay = match.gameMode == GameMode.PASS_AND_PLAY
    val isWifi = match.gameMode == GameMode.LOCAL_WIFI
    val isCurrentTurnMine = when (match.gameMode) {
        GameMode.COMPUTER -> match.currentTurn == PlayerTurn.HUMAN
        GameMode.PASS_AND_PLAY -> true
        GameMode.LOCAL_WIFI -> {
            if (uiState.isWifiHost) match.currentTurn == PlayerTurn.HUMAN else match.currentTurn == PlayerTurn.COMPUTER
        }
    }

    val activeRack = if (isPassAndPlay && match.currentTurn == PlayerTurn.COMPUTER) {
        match.computerRack
    } else {
        match.playerRack
    }

    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize().testTag("game_screen_root")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Top Header: Scores, Turn indicator, History, Home, Settings, Resign
                ScoreHeader(
                    playerName = match.player1Name,
                    playerScore = match.playerScore,
                    opponentName = match.player2Name,
                    opponentScore = match.computerScore,
                    currentTurn = match.currentTurn,
                    gameMode = match.gameMode,
                    isComputerThinking = uiState.isComputerThinking,
                    matchCategory = match.matchCategory,
                    moveCount = match.moveHistory.size,
                    onHomeClick = {
                        if (match.gameMode == GameMode.LOCAL_WIFI && !match.isGameOver) {
                            viewModel.promptLeaveWifiMatch()
                        } else {
                            viewModel.navigateToHome()
                        }
                    },
                    onHistoryClick = { showMoveHistoryDrawer = true },
                    onSettingsClick = { viewModel.openSettings() },
                    onResignClick = { viewModel.promptResign() }
                )

                // 2. Interactive Board View (13x13 with pan/zoom)
                BoardView(
                    board = match.board,
                    lastComputerMoveCells = match.lastComputerMoveCells,
                    onCellClick = { r, c ->
                        if (!uiState.isHandoverPending) {
                            viewModel.onCellClicked(r, c)
                        }
                    },
                    onBonusSquareInfo = { info -> viewModel.showBonusInfo(info) },
                    shakeTrigger = uiState.boardShakeTrigger,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )

                // 3. Legend & Tiles Left
                BoardLegendBar(
                    tilesRemaining = match.totalTilesRemaining
                )

                // 4. Active Player's 7-Tile Rack (hidden when handover pending)
                if (!uiState.isHandoverPending) {
                    PlayerRackView(
                        rack = activeRack,
                        selectedTileId = uiState.selectedRackTileId,
                        onTileClick = { tile -> viewModel.selectRackTile(tile) }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tiles concealed for privacy",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 5. Action controls (Recall, Shuffle, Swap, Pass, Play Word)
                GameActionBar(
                    isHumanTurn = isCurrentTurnMine && !uiState.isHandoverPending && !uiState.isWifiActionPending,
                    isComputerThinking = uiState.isComputerThinking,
                    hasDraftTiles = uiState.draftPlacements.isNotEmpty(),
                    prospectiveScore = uiState.prospectiveScore,
                    prospectiveWord = uiState.prospectiveWord,
                    validationError = uiState.validationError,
                    canSwap = match.tileBag.size >= 7,
                    onRecall = { viewModel.recallDraft() },
                    onShuffle = { viewModel.shuffleRack() },
                    onSwap = { viewModel.promptSwap() },
                    onPass = { viewModel.passTurn() },
                    onPlayWord = { viewModel.playWord() }
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Info Toast Overlay
            AnimatedVisibility(
                visible = uiState.infoToastMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SlateCard.copy(alpha = 0.95f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = uiState.infoToastMessage ?: "",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }

            // Pass & Play Handover Privacy Curtain
            if (isPassAndPlay && uiState.isHandoverPending) {
                val nextPlayer = if (match.currentTurn == PlayerTurn.HUMAN) match.player1Name else match.player2Name
                PassAndPlayHandoverOverlay(
                    nextPlayerName = nextPlayer,
                    onReadyClicked = { viewModel.dismissHandover() }
                )
            }

            // Move History Drawer (Modal from Right)
            AnimatedVisibility(
                visible = showMoveHistoryDrawer,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Scrim
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { showMoveHistoryDrawer = false }
                    )
                    MoveHistoryDrawer(
                        moves = match.moveHistory,
                        onClose = { showMoveHistoryDrawer = false },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }

    // Modal Dialogs
    if (uiState.pendingBlankCoordinate != null) {
        BlankLetterDialog(
            onLetterSelected = { letter -> viewModel.assignBlankLetter(letter) },
            onDismiss = { viewModel.dismissBlankDialog() }
        )
    }

    if (uiState.showSwapDialog) {
        com.example.ui.components.SwapTilesDialog(
            rack = activeRack,
            onConfirmSwap = { ids -> viewModel.executeSwap(ids) },
            onDismiss = { viewModel.dismissSwapDialog() }
        )
    }

    if (uiState.showResignConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.dismissResignDialog() },
            title = { androidx.compose.material3.Text("Forfeit Match?") },
            text = { androidx.compose.material3.Text("Are you sure you want to resign this match? Your opponent will be awarded the victory.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.resignActiveMatch() },
                    modifier = Modifier.testTag("confirm_resign_button")
                ) {
                    androidx.compose.material3.Text(
                        "Resign",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.dismissResignDialog() },
                    modifier = Modifier.testTag("cancel_resign_button")
                ) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }

    if (uiState.showHowToPlayDialog) {
        HowToPlayDialog(
            onDismiss = { viewModel.dismissHowToPlay() }
        )
    }

    if (uiState.showResultsDialog) {
        ResultsDialog(
            matchState = match,
            onPlayAgain = { viewModel.startNewMatch(confirmedAbandon = true) },
            onHome = { viewModel.navigateToHome() }
        )
    }

    if (uiState.showWifiLeaveConfirmDialog) {
        com.example.ui.components.WifiLeaveConfirmDialog(
            onConfirmLeave = { viewModel.leaveLocalWifiMatch() },
            onStay = { viewModel.dismissWifiLeaveConfirmDialog() }
        )
    }

    if (uiState.showWifiDisconnectDialog) {
        com.example.ui.components.WifiDisconnectDialog(
            message = uiState.wifiDisconnectMessage,
            onDismiss = { viewModel.dismissWifiDisconnectDialog() }
        )
    }
}
