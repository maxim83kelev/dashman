package cz.kelev.dashman

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kelev.dashman.network.DashmanApiClient
import cz.kelev.dashman.storage.AppPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

@Composable
fun ActivationScreen(onActivated: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val prefs = remember { AppPrefs(context) }
    val deviceId = remember { prefs.getDeviceId() }
    val scope = rememberCoroutineScope()

    var telegramHandle by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var requested by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }

    // Автопроверка каждые 10 секунд после запроса
    LaunchedEffect(requested) {
        if (!requested) return@LaunchedEffect
        while (true) {
            delay(10_000)
            val result = DashmanApiClient.checkLicense(deviceId)
            if (result is cz.kelev.dashman.network.LicenseResult.Active) {
                prefs.setLicenseStatus("active")
                onActivated()
                break
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text("⚡", fontSize = 56.sp)

            Text(
                "Активация Dashman",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Text(
                "Нажми кнопку — запрос уйдёт автору.\nКак только он одобрит, Dashman запустится автоматически.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            // Device ID блок
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Твой Device ID:",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = deviceId,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(deviceId))
                            status = "✅ Device ID скопирован"
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Копировать", color = Color.Red, fontSize = 12.sp)
                    }
                }
            }

            OutlinedTextField(
                value = telegramHandle,
                onValueChange = { telegramHandle = it },
                placeholder = {
                    Text(
                        "@твой_telegram (необязательно)",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Red,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.Red
                ),
                singleLine = true
            )

            Button(
                onClick = {
                    if (requested) return@Button
                    scope.launch {
                        checking = true
                        val ok = DashmanApiClient.requestAccess(
                            deviceId = deviceId,
                            telegram = telegramHandle.trim().ifBlank { null }
                        )
                        checking = false
                        if (ok) {
                            requested = true
                            status = "✅ Запрос отправлен. Ожидаем одобрения..."
                        } else {
                            status = "❌ Ошибка отправки. Проверь интернет."
                        }
                    }
                },
                enabled = !requested && !checking,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (checking) "Отправка..." else if (requested) "Ожидаем одобрения..." else "Запросить доступ",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            status?.let {
                Text(
                    text = it,
                    color = if (it.startsWith("✅")) Color(0xFF66BB6A) else Color(0xFFFF5252),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                "По вопросам: Telegram @kelevJob",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp
            )
        }
    }
}