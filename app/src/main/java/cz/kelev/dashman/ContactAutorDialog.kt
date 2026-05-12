package cz.kelev.dashman

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kelev.dashman.network.DashmanApiClient
import cz.kelev.dashman.storage.AppPrefs
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ContactAuthorDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val scope = rememberCoroutineScope()

    var messageText by remember { mutableStateOf("") }
    var logAttached by remember { mutableStateOf(false) }
    var logContent by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    // Ищем лог-файл
    val logFile = remember {
        listOf(
            File(context.filesDir, "dashman.log"),
            File(context.cacheDir, "dashman.log")
        ).firstOrNull { it.exists() }
    }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        containerColor = Color(0xFF2F2F2F),
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                "Связь с автором",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Напиши что случилось — отвечу в Telegram.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
                Text(
                    "Прямая связь: @kelevJob",
                    color = Color(0xFF66BB6A),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = {
                        Text(
                            "Сообщение...",
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Red,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.Red
                    ),
                    maxLines = 6
                )

                // Кнопка прикрепить лог
                if (logFile != null) {
                    TextButton(
                        onClick = {
                            if (!logAttached) {
                                val content = logFile.readText()
                                logContent = content.takeLast(3000)
                                logAttached = content.isNotBlank()
                            } else {
                                logContent = null
                                logAttached = false
                            }
                        }
                    ) {
                        Text(
                            text = if (logAttached) "✓ Лог прикреплён (${logFile.length()} байт)" else "Прикрепить лог",
                            color = if (logAttached) Color(0xFF66BB6A) else Color.Red,
                            fontSize = 13.sp
                        )
                    }
                }
                // Результат отправки
                result?.let {
                    Text(
                        text = it,
                        color = if (it.startsWith("✓")) Color(0xFF66BB6A) else Color(0xFFFF5252),
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (messageText.isBlank() || sending) return@TextButton
                    sending = true
                    result = null
                    scope.launch {
                        val ok = DashmanApiClient.sendFeedback(
                            deviceId = prefs.getDeviceId(),
                            message = messageText.trim(),
                            logText = logContent
                        )
                        sending = false
                        if (ok) {
                            result = "✓ Отправлено"
                            kotlinx.coroutines.delay(1500)
                            onDismiss()
                        } else {
                            result = "✗ Ошибка отправки. Проверь интернет."
                        }
                    }
                },
                enabled = !sending && messageText.isNotBlank()
            ) {
                Text(
                    text = if (sending) "Отправка..." else "Отправить",
                    color = if (sending) Color.Gray else Color.Red,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!sending) onDismiss() }) {
                Text("Закрыть", color = Color.White)
            }
        }
    )
}