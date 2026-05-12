package cz.kelev.dashman

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kelev.dashman.storage.AppPrefs
import cz.kelev.dashman.BuildConfig

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val deviceId = remember { prefs.getDeviceId() }
    val licenseStatus = remember { prefs.getLicenseStatus() }
    val expiresAt = remember { prefs.getLicenseExpiry() }

    val daysLeft = remember {
        if (expiresAt == 0L) null
        else {
            val diff = expiresAt - System.currentTimeMillis() / 1000
            if (diff <= 0) 0L else diff / 86400
        }
    }

    val statusText = when (licenseStatus) {
        "active"        -> "✅ Активна"
        "revoked"       -> "🚫 Отозвана"
        "expired"       -> "⏰ Истекла"
        "not_found"     -> "❓ Не найдена"
        "network_error" -> "📡 Нет связи с сервером"
        else            -> "❓ Неизвестно"
    }

    val statusColor = when (licenseStatus) {
        "active"            -> Color(0xFF66BB6A)
        "revoked", "expired"-> Color(0xFFFF5252)
        else                -> Color.White.copy(alpha = 0.6f)
    }

    val expiryText = when {
        daysLeft == null -> "Бессрочная ∞"
        daysLeft == 0L   -> "Истекла"
        daysLeft <= 7    -> "⚠️ Осталось ${daysLeft} дн."
        else             -> "Осталось ${daysLeft} дн."
    }

    val expiryColor = when {
        daysLeft == null -> Color(0xFF66BB6A)
        daysLeft == 0L   -> Color(0xFFFF5252)
        daysLeft <= 7    -> Color(0xFFFFB300)
        else             -> Color.White
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2F2F2F),
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                "О приложении",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                AboutRow(
                    label = "Версия",
                    value = BuildConfig.VERSION_NAME
                )

                AboutRow(
                    label = "Лицензия",
                    value = statusText,
                    valueColor = statusColor
                )

                AboutRow(
                    label = "Срок лицензии",
                    value = expiryText,
                    valueColor = expiryColor
                )

                AboutRow(
                    label = "Device ID",
                    value = deviceId.take(18) + "..."
                )

                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("device_id", deviceId))
                    }
                ) {
                    Text(
                        "📋 Скопировать Device ID",
                        color = Color.Red,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", color = Color.White)
            }
        }
    )
}

@Composable
private fun AboutRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}