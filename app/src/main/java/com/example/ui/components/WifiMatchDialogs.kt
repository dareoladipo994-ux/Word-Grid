package com.example.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun WifiDisconnectDialog(
    message: String = "Connection ended. This match has ended. Reconnect to start a new game.",
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Match Disconnected",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = message,
                color = TextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier.testTag("dismiss_wifi_disconnect_button")
            ) {
                Text("Return to Menu")
            }
        },
        containerColor = SlateSurface
    )
}

@Composable
fun WifiLeaveConfirmDialog(
    onConfirmLeave: () -> Unit,
    onStay: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onStay,
        title = {
            Text(
                text = "Leave Match?",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Leaving will end this match for both players. Progress will not be saved.",
                color = TextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmLeave,
                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFE53935)),
                modifier = Modifier.testTag("confirm_leave_wifi_match_button")
            ) {
                Text("Leave Match")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onStay,
                modifier = Modifier.testTag("stay_wifi_match_button")
            ) {
                Text("Stay", color = TextPrimary)
            }
        },
        containerColor = SlateSurface
    )
}

@Composable
fun LegacyWifiDiscardedDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Notice: Saved Wi-Fi Match Discarded",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "A previously saved Local Wi-Fi match was removed. Local Wi-Fi matches are temporary and strictly in-memory.",
                color = TextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier.testTag("dismiss_legacy_wifi_notice_button")
            ) {
                Text("OK")
            }
        },
        containerColor = SlateSurface
    )
}
