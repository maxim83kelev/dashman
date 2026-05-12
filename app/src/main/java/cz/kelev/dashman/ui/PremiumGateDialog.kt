package cz.kelev.dashman.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PremiumGateDialog(
    message: String,
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2F2F2F),
        tonalElevation = 8.dp,
        title = {
            Text("⚡ Dashman Premium", color = Color.White, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Text(message, color = Color.White.copy(alpha = 0.85f))
        },
        confirmButton = {
            TextButton(onClick = onGoToSettings) {
                Text("Купить Premium →", color = Color.Red, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", color = Color.White)
            }
        }
    )
}