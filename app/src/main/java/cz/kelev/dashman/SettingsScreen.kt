package cz.kelev.dashman

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import cz.kelev.dashman.ui.theme.MainViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.Button

@Composable
fun SettingsScreen(
    hotwordEnabled: Boolean,
    onHotwordToggle: (Boolean) -> Unit = {},
    onOpenEula: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onOpenReadme: () -> Unit = {},
    onOpenContactAuthor: () -> Unit = {},
    onCleanupNow: () -> Unit = {},
    onSetCleanupMode: (Int) -> Unit = {},
    onBriefingChanged: (enabled: Boolean, hour: Int, minute: Int) -> Unit = { _, _, _ -> },
    onSpeakOnFireChanged: (Boolean) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    var showWidgetIconPicker by remember { mutableStateOf(false) }
    var showCleanupDialog by remember { mutableStateOf(false) }
    var selectedCleanupMode by remember { mutableStateOf(-1) }
    var showBriefingDialog by remember { mutableStateOf(false) }
    val prefs = remember { cz.kelev.dashman.storage.AppPrefs(context) }
    val premiumChecker = remember { cz.kelev.dashman.storage.PremiumChecker(prefs) }
    var isPremium by remember { mutableStateOf(false) }
    var showUpdateCheckResult by remember { mutableStateOf<String?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<cz.kelev.dashman.network.UpdateInfo?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val status = prefs.getLicenseStatus()
        val expiry = prefs.getLicenseExpiry()
        isPremium = status == "active" && (expiry <= 0L || System.currentTimeMillis() / 1000L < expiry)
    }
    androidx.compose.runtime.LaunchedEffect(isCheckingUpdate) {
        if (!isCheckingUpdate) return@LaunchedEffect
        val result = cz.kelev.dashman.network.UpdateChecker.check(context)
        when (result) {
            is cz.kelev.dashman.network.UpdateResult.Available -> {
                pendingUpdate = result.info
                showUpdateCheckResult = null
            }
            cz.kelev.dashman.network.UpdateResult.UpToDate ->
                showUpdateCheckResult = "Версия актуальна"
            cz.kelev.dashman.network.UpdateResult.NetworkError ->
                showUpdateCheckResult = "Нет соединения"
        }
        isCheckingUpdate = false
    }
    var lastBackupTime by remember { mutableStateOf(prefs.getLastBackupTime()) }
    var briefingEnabled by remember { mutableStateOf(prefs.getBriefingEnabled()) }
    var briefingHour by remember { mutableStateOf(prefs.getBriefingTime().first) }
    var briefingMinute by remember { mutableStateOf(prefs.getBriefingTime().second) }
    var speakOnFire by remember { mutableStateOf(prefs.getSpeakOnFire()) }
    var showContactAuthor by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showPremiumDialog by remember { mutableStateOf(false) }
    val backupMessage by viewModel.backupMessage.collectAsState()
    var lastBackupPath by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(backupMessage) {
        backupMessage?.let {
            if (it.startsWith("Резервная копия сохранена")) {
                lastBackupPath = it.removePrefix("Резервная копия сохранена:\n")
                lastBackupTime = prefs.getLastBackupTime()
                viewModel.clearBackupMessage()
            }
        }
    }

    if (pendingUpdate != null) {
        UpdateDialog(
            info = pendingUpdate!!,
            onDismiss = { pendingUpdate = null },
            onDownload = { url ->
                pendingUpdate = null
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url)
                )
                context.startActivity(intent)
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // тот же фон, что на главном экране
        Image(
            painter = painterResource(id = R.drawable.splash1),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // затемнение снизу (киношный низ)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.40f)
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {

            // Header: "Настройки" (верхній правий кут)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, end = 16.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Text(
                    text = "Настройки",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Hotword
                SettingsToggleRow(
                    title = "Hotword (Hey Dashman)",
                    subtitle = if (hotwordEnabled) "Включено" else "Выключено",
                    enabled = hotwordEnabled,
                    onToggle = { onHotwordToggle(!hotwordEnabled) }
                )

                // Купить Premium
                SettingsButtonRow(
                    title = "⚡️ Купить Dashman Premium",
                    subtitle = "5€ — доступ ко всем функциям",
                    onClick = { showPremiumDialog = true }
                )

                // EULA
                SettingsButtonRow(
                    title = "Лицензионное соглашение (EULA)",
                    subtitle = "Открыть текст соглашения",
                    onClick = onOpenEula
                )

                // Privacy
                SettingsButtonRow(
                    title = "Политика конфиденциальности",
                    subtitle = "Открыть Privacy",
                    onClick = onOpenPrivacy
                )

                // Инструкция
                SettingsButtonRow(
                    title = "ReadMe",
                    subtitle = "Короткая инструкция по функциям",
                    onClick = onOpenReadme
                )

                // Связь с автором
                SettingsButtonRow(
                    title = "Связь с автором",
                    subtitle = "Написать сообщение",
                    onClick = { showContactAuthor = true }
                )
                // О приложении
                SettingsButtonRow(
                    title = "О приложении",
                    subtitle = buildString {
                        append("v${BuildConfig.VERSION_NAME}")
                        if (isPremium) {
                            val exp = prefs.getLicenseExpiry()
                            if (premiumChecker.isTrialActive) {
                                append(" · 🕐 Пробный период: ещё ${premiumChecker.trialDaysLeft} дн.")
                            } else if (exp > 0L) {
                                val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                                append(" · ⚡️ Premium до ${sdf.format(java.util.Date(exp * 1000))}")
                            } else {
                                append(" · ⚡️ Premium активирован")
                            }
                        } else {
                            append(" · Бесплатная версия")
                        }
                    },
                    onClick = { showAbout = true }
                )

                //Проверить обновления
                SettingsButtonRow(
                    title = if (isCheckingUpdate) "Проверяю..." else "Проверить обновления",
                    subtitle = showUpdateCheckResult ?: "Сравнить с последней версией на GitHub",
                    onClick = {
                        if (!isCheckingUpdate) {
                            isCheckingUpdate = true
                            showUpdateCheckResult = null
                        }
                    }
                )

                // Очистка сработанных напоминаний
                SettingsButtonRow(
                    title = "Очистка сработанных напоминаний",
                    subtitle = "Сейчас / не очищать / раз в сутки / раз в неделю",
                    onClick = { showCleanupDialog = true }
                )

                // Резервное копирование
                SettingsButtonRow(
                    title = "Резервное копирование",
                    subtitle = if (lastBackupTime > 0L) {
                        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                        "Последний бэкап: ${sdf.format(java.util.Date(lastBackupTime))}"
                    } else {
                        "Бэкап не создавался"
                    },
                    onClick = { showBackupDialog = true }
                )

                // Иконка виджета
                SettingsButtonRow(
                    title = "Иконка виджета",
                    subtitle = "Выбрать картинку для виджета",
                    onClick = { showWidgetIconPicker = true }
                )

                //Брифинг
                SettingsButtonRow(
                    title = if (isPremium) "Брифинг" else "Брифинг ⚡️ Premium",
                    subtitle = if (!isPremium) "Недоступно в бесплатной версии"
                               else if (briefingEnabled) "Включён в ${"%02d".format(briefingHour)}:${"%02d".format(briefingMinute)}"
                               else "Выключен",
                    onClick = { if (isPremium) showBriefingDialog = true else showPremiumDialog = true }
                )

                //Озвучивание напоминаний
                SettingsButtonRow(
                    title = if (isPremium) "Озвучивание напоминаний" else "Озвучивание напоминаний ⚡️ Premium",
                    subtitle = if (!isPremium) "Недоступно в бесплатной версии"
                               else if (speakOnFire) "Включено — Dashman зачитает напоминание вслух"
                               else "Выключено — только уведомление",
                    onClick = {
                        if (isPremium) {
                            speakOnFire = !speakOnFire
                            onSpeakOnFireChanged(speakOnFire)
                        } else {
                            showPremiumDialog = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
            if (showWidgetIconPicker) {
                WidgetIconPickerDialog(
                    currentIcon = getSavedWidgetIcon(context),
                    onDismiss = { showWidgetIconPicker = false },
                    onSelect = { icon ->
                        saveWidgetIconAndRefresh(context, icon)
                        showWidgetIconPicker = false
                    }
                )
            }

            if (showCleanupDialog) {
                AlertDialog(
                    onDismissRequest = { showCleanupDialog = false },
                    containerColor =  Color(0xFF2F2F2F),
                    tonalElevation = 8.dp,
                    title = {
                        Text(
                            "Очистка сработанных напоминаний",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsDialogAction(
                                text = "Очистить сейчас",
                                onClick = {
                                    onCleanupNow()
                                    showCleanupDialog = false
                                }
                            )
                            SettingsDialogAction(
                                text = "Не очищать",
                                onClick = {
                                    onSetCleanupMode(0)
                                    showCleanupDialog = false
                                }
                            )
                            SettingsDialogAction(
                                text = "Раз в сутки",
                                selected = selectedCleanupMode == 1,
                                onClick = {
                                    selectedCleanupMode = 1
                                    onSetCleanupMode(1)
                                    showCleanupDialog = false
                                }
                            )
                            SettingsDialogAction(
                                text = "Раз в неделю",
                                onClick = {
                                    onSetCleanupMode(2)
                                    showCleanupDialog = false
                                }
                            )
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showCleanupDialog = false }) {
                            Text("Закрыть")
                        }
                    }
                )
            }

            if (showBriefingDialog) {
                BriefingTimeDialog(
                    currentEnabled = briefingEnabled,
                    currentHour = briefingHour,
                    currentMinute = briefingMinute,
                    onDismiss = { showBriefingDialog = false },
                    onDisable = {
                        briefingEnabled = false
                        onBriefingChanged(false, briefingHour, briefingMinute)
                        showBriefingDialog = false
                    },
                    onConfirm = { h, m ->
                        briefingEnabled = true
                        briefingHour = h
                        briefingMinute = m
                        onBriefingChanged(true, h, m)
                        showBriefingDialog = false
                    }
                )
            }

            if (showContactAuthor) {
                ContactAuthorDialog(onDismiss = { showContactAuthor = false })
            }

            if (showAbout) {
                AboutDialog(onDismiss = { showAbout = false })
            }

            if (showPremiumDialog) {
                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                val deviceId = remember { prefs.getDeviceId() }
                AlertDialog(
                    onDismissRequest = { showPremiumDialog = false },
                    containerColor = Color(0xFF2F2F2F),
                    tonalElevation = 8.dp,
                    title = {
                        Text(
                            "⚡ Dashman Premium",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "Перед оплатой скопируй свой Device ID и вставь его в комментарий при оплате на Ko-fi.",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = deviceId,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(
                                            androidx.compose.ui.text.AnnotatedString(deviceId)
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("Копировать", color = Color.Red, fontSize = 12.sp)
                                }
                            }
                            Text(
                                "После оплаты активация в течение 24 часов.",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showPremiumDialog = false
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://ko-fi.com/kelev_job")
                            )
                            context.startActivity(intent)
                        }) {
                            Text(
                                "Перейти к оплате →",
                                color = Color.Red,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPremiumDialog = false }) {
                            Text("Отмена", color = Color.White)
                        }
                    }
                )
            }

            if (showBackupDialog) {
                AlertDialog(
                    onDismissRequest = { showBackupDialog = false },
                    containerColor = Color(0xFF2F2F2F),
                    tonalElevation = 8.dp,
                    title = {
                        Text(
                            "Резервное копирование",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(
                                onClick = { viewModel.triggerExport() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Создать резервную копию", color = Color.White)
                            }
                            lastBackupPath?.let { path ->
                                Text(
                                    text = path,
                                    color = Color(0xFF4CAF50),
                                    fontSize = 12.sp
                                )
                            }
                            TextButton(
                                onClick = { viewModel.triggerImport() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Восстановить из файла", color = Color.White)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showBackupDialog = false }) {
                            Text("Закрыть", color = Color.White)
                        }
                    }
                )
            }
        }
        // Плавающая кнопка "Назад" (вне скролла)
        RoundBackButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                // end больше = ближе к центру, меняй под палец
                .padding(end = 70.dp, bottom = 90.dp),
            sizeDp = 72,
            fillColor = Color(0x14000000) // почти прозрачная, чтобы не "ела" экран
        )
    }
}

@Composable
private fun SettingsButtonRow(

    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC2F2F2F))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Column {
            Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun SettingsDialogAction(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC3A3A3A))
            .border(
                2.dp,
                if (selected) Color(0xFFFF4444) else Color(0x44FFFFFF),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val tint = if (enabled) Color(0f, 1f, 0f, 0.20f) else Color(1f, 0f, 0f, 0.20f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC2F2F2F))
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
            Box(
                modifier = Modifier
                    .size(width = 74.dp, height = 34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (enabled) "ON" else "OFF",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun WidgetIconPickerDialog(
    currentIcon: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Выбери иконку виджета",
                color = Color.White
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WidgetIconOption(
                    iconName = "tap_me",
                    drawableRes = R.drawable.tap_me,
                    selected = currentIcon == "tap_me",
                    onClick = { onSelect("tap_me") }
                )

                WidgetIconOption(
                    iconName = "death_mic",
                    drawableRes = R.drawable.death_mic,
                    selected = currentIcon == "death_mic",
                    onClick = { onSelect("death_mic") }
                )

                WidgetIconOption(
                    iconName = "in_my_hand",
                    drawableRes = R.drawable.in_my_hand,
                    selected = currentIcon == "in_my_hand",
                    onClick = { onSelect("in_my_hand") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        containerColor = Color(0xFF2F2F2F),
        tonalElevation = 8.dp
    )
}

@Composable
private fun WidgetIconOption(
    iconName: String,
    drawableRes: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) Color(0xFFFF4444) else Color.White.copy(alpha = 0.20f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xCC3A3A3A))
            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = iconName,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column {
            Text(
                text = iconName,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (selected) "Выбрано" else "Нажми, чтобы выбрать",
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 13.sp
            )
        }
    }
}

private fun getSavedWidgetIcon(context: Context): String {
    val prefs = context.getSharedPreferences("dashman_prefs", Context.MODE_PRIVATE)
    return prefs.getString("widget_icon", "tap_me") ?: "tap_me"
}

private fun saveWidgetIconAndRefresh(context: Context, icon: String) {
    val prefs = context.getSharedPreferences("dashman_prefs", Context.MODE_PRIVATE)
    prefs.edit().putString("widget_icon", icon).apply()

    val manager = AppWidgetManager.getInstance(context)
    val component = ComponentName(context, DashmanWidgetProvider::class.java)
    val ids = manager.getAppWidgetIds(component)

    for (id in ids) {
        val views = RemoteViews(context.packageName, R.layout.widget_dashman)
        val iconRes = when (icon) {
            "death_mic" -> R.drawable.death_mic
            "in_my_hand" -> R.drawable.in_my_hand
            else -> R.drawable.tap_me
        }

        views.setImageViewResource(R.id.widget_button, iconRes)
        manager.updateAppWidget(id, views)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BriefingTimeDialog(
    currentEnabled: Boolean,
    currentHour: Int,
    currentMinute: Int,
    onDismiss: () -> Unit,
    onDisable: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = currentHour,
        initialMinute = currentMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2F2F2F),
        tonalElevation = 8.dp,
        title = {
            Text(
                "Время брифинга",
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = Color(0xFF3A3A3A),
                        clockDialSelectedContentColor = Color.White,
                        clockDialUnselectedContentColor = Color.White.copy(alpha = 0.6f),
                        selectorColor = Color.Red,
                        containerColor = Color(0xFF2F2F2F),
                        timeSelectorSelectedContainerColor = Color.Red,
                        timeSelectorUnselectedContainerColor = Color(0xFF3A3A3A),
                        timeSelectorSelectedContentColor = Color.White,
                        timeSelectorUnselectedContentColor = Color.White.copy(alpha = 0.6f)
                    )
                )

                SettingsDialogAction(
                    text = "Не устраивать брифинг",
                    selected = !currentEnabled,
                    onClick = onDisable
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(timePickerState.hour, timePickerState.minute)
            }) {
                Text(
                    "Включить",
                    color = Color.Red,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", color = Color.White)
            }
        }
    )
}