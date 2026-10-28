package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GameMode
import com.example.ui.Screen
import com.example.ui.WordGridViewModel
import com.example.ui.components.HowToPlayDialog
import com.example.ui.components.LegacyWifiDiscardedDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.screens.GameScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LocalWifiLobbyScreen
import com.example.ui.screens.StatisticsScreen
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.WordGridTheme

class MainActivity : ComponentActivity() {
    private val viewModel: WordGridViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            WordGridTheme(themeMode = uiState.settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    when (uiState.currentScreen) {
                        Screen.HOME -> {
                            HomeScreen(
                                hasSavedMatch = uiState.hasSavedMatch,
                                savedMatch = uiState.matchState,
                                onStartComputerGame = { diff, name -> viewModel.startComputerMatch(diff, name) },
                                onStartPassAndPlay = { p1, p2 -> viewModel.startPassAndPlayMatch(p1, p2) },
                                onStartWifiHost = { roomName, p1 -> viewModel.startWifiHost(roomName, p1) },
                                onStartWifiJoin = { ip, p2 -> viewModel.startWifiJoin(ip, p2) },
                                onResumeGameClick = { viewModel.resumeSavedMatch() },
                                onStatisticsClick = { viewModel.openStatistics() },
                                onHowToPlayClick = { viewModel.openHowToPlay() },
                                onSettingsClick = { viewModel.openSettings() },
                                localIpAddress = uiState.localIpAddress,
                                defaultPlayerName = uiState.settings.playerName,
                                discoveredRooms = uiState.discoveredRooms,
                                onRefreshDiscovery = { viewModel.refreshRoomDiscovery() }
                            )

                            if (uiState.showHowToPlayDialog) {
                                HowToPlayDialog(
                                    onDismiss = { viewModel.dismissHowToPlay() }
                                )
                            }
                            if (uiState.showLegacyWifiDiscardedDialog) {
                                LegacyWifiDiscardedDialog(
                                    onDismiss = { viewModel.dismissLegacyWifiDiscardedDialog() }
                                )
                            }
                        }

                        Screen.LOBBY -> {
                            LocalWifiLobbyScreen(
                                viewModel = viewModel,
                                uiState = uiState
                            )
                        }

                        Screen.GAME -> {
                            GameScreen(
                                viewModel = viewModel,
                                uiState = uiState
                            )
                        }

                        Screen.STATISTICS -> {
                            StatisticsScreen(
                                statistics = uiState.statistics,
                                onBackClick = { viewModel.navigateToHome() },
                                onResetStatistics = { viewModel.resetStatistics() }
                            )
                        }
                    }

                    if (uiState.showSettingsDialog) {
                        SettingsDialog(
                            settings = uiState.settings,
                            onSaveSettings = { updated -> viewModel.saveSettings(updated) },
                            onResignClick = { viewModel.promptResign() },
                            onDismiss = { viewModel.dismissSettings() }
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // When true background transition occurs (leaving app / device home)
        val uiState = viewModel.uiState.value
        if (uiState.currentScreen == Screen.GAME) {
            if (uiState.matchState?.gameMode == GameMode.LOCAL_WIFI) {
                viewModel.handleAppBackgroundedWifiMatch()
            } else {
                viewModel.navigateToHome()
            }
        }
    }
}
