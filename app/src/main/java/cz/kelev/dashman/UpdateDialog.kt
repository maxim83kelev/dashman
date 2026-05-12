package cz.kelev.dashman

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kelev.dashman.network.UpdateInfo

@Composable
fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2F2F2F),
        tonalElevation = 8.dp,
        title = {
            Text(
                "Обновление ${info.latestVersion}",
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (info.releaseNotes.isNotBlank()) {
                    Text(
                        info.releaseNotes.take(400),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDownload(info.downloadUrl) }) {
                Text(
                    "Скачать",
                    color = Color.Red,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Позже", color = Color.White)
            }
        }
    )
}