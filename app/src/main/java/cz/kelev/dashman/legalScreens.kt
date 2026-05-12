package cz.kelev.dashman

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.navigationBarsPadding

// ─── Главный экран согласия (компактный) ────────────────────────────────────

@Composable
fun TermsGateScreen(
    title: String,
    combinedTermsText: String?,   // не используется в UI, но сохраняем сигнатуру для AppRoot
    errorText: String?,
    accepted: Boolean,
    onAcceptChanged: (Boolean) -> Unit,
    onAccept: () -> Unit,
) {
    var showDocs by remember { mutableStateOf(false) }

    if (showDocs) {
        DocsViewerScreen(onClose = { showDocs = false })
        return
    }

    val bg = Brush.verticalGradient(
        colors = listOf(Color(0xFF0B0B0D), Color(0xFF0F0F12), Color(0xFF0B0B0D))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF141416))
                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Иконка / лого
            Text(
                text = "⚡",
                fontSize = 36.sp
            )

            Text(
                text = "Добро пожаловать в Dashman",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            // Суть в трёх строках
            Text(
                text = "Коротко о главном:\n" +
                       "— твои данные мне нахуй не нужны\n" +
                       "— за тобой не слежу\n" +
                       "— Device ID использую только для лицензии\n" +
                       "— логи хранятся только у тебя\n" +
                       "— установка на свой страх и риск\n" +
                       "— приложение работает как есть\n" +
                       "— если несовершеннолетний,\n пользуйся только с разрешения родителей\n" +
                       "— за твои дела ответственности не несу",
                color = Color(0xFFB0B0B0),
                fontSize = 14.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            // Кликабельная ссылка на документы
            if (errorText == null) {
                Text(
                    text = "Подробнее: правила, политика, README →",
                    color = Color(0xFFE35B5B),
                    fontSize = 13.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clickable { showDocs = true }
                        .padding(vertical = 4.dp)
                )
            } else {
                Text(
                    text = "⚠ $errorText",
                    color = Color(0xFFFF6B6B),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Кнопка принять
            Button(
                onClick = {
                    onAcceptChanged(true)
                    onAccept()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFE35B5B)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0x14FFFFFF),
                    contentColor = Color.White,
                )
            ) {
                Text("Понял, принимаю", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            Text(
                text = "Нажимая кнопку, ты подтверждаешь согласие с условиями",
                color = Color(0xFF606060),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Просмотрщик документов (открывается по ссылке) ─────────────────────────

@Composable
fun DocsViewerScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var currentDoc by remember { mutableStateOf(0) } // 0=EULA, 1=Privacy, 2=README

    val docs = listOf(
        Triple("EULA", "eula.txt", "Соглашение"),
        Triple("Privacy", "privacy.txt", "Конфиденциальность"),
        Triple("README", "readme_ru.txt", "О приложении"),
    )

    var docText by remember(currentDoc) { mutableStateOf<String?>(null) }

    LaunchedEffect(currentDoc) {
        docText = try {
            context.assets.open(docs[currentDoc].second).bufferedReader().use { it.readText() }
        } catch (_: Throwable) {
            "Не удалось загрузить файл: ${docs[currentDoc].second}"
        }
    }

    val scrollState = rememberScrollState()

    // При смене вкладки — скроллим наверх
    LaunchedEffect(currentDoc) { scrollState.scrollTo(0) }

    val bg = Brush.verticalGradient(
        colors = listOf(Color(0xFF0B0B0D), Color(0xFF0F0F12), Color(0xFF0B0B0D))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // Контент — СВЕРХУ
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 48.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF141416))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = docText ?: "Загрузка…",
                color = Color(0xFFDDDDDD),
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }

        // Таббар + закрыть — ВНИЗУ
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F12))
                .navigationBarsPadding()
        ) {
            // Таббар
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                docs.forEachIndexed { index, (_, _, label) ->
                    val isActive = currentDoc == index
                    Text(
                        text = label,
                        color = if (isActive) Color.White else Color(0xFF707070),
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isActive) Color(0x22E35B5B) else Color.Transparent)
                            .border(1.dp, if (isActive) Color(0x88E35B5B) else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { currentDoc = index }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Кнопка назад — отдельная строка
            Text(
                text = "← Назад к согласию",
                color = Color(0xFFE35B5B),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClose() }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

// ─── DocumentScreen оставляем (используется в других местах?) ───────────────

@Composable
fun DocumentScreen(
    title: String,
    assetFile: String,
    requireAcceptance: Boolean,
    onAccepted: () -> Unit
) {
    val context = LocalContext.current
    var docText by remember(assetFile) { mutableStateOf<String?>(null) }
    var error by remember(assetFile) { mutableStateOf<String?>(null) }

    LaunchedEffect(assetFile) {
        try {
            docText = context.assets.open(assetFile).bufferedReader().use { it.readText() }
            error = null
        } catch (t: Throwable) {
            docText = null
            error = "Не могу открыть: $assetFile"
        }
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E10))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFFE6E6E6),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF151518))
                    .padding(14.dp)
            ) {
                when {
                    error != null -> Text(text = error!!, color = Color(0xFFFF6B6B), fontSize = 14.sp)
                    docText == null -> Text(text = "Загружаю…", color = Color(0xFFBDBDBD), fontSize = 14.sp)
                    else -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        Text(text = docText!!, color = Color(0xFFE6E6E6), fontSize = 14.sp, lineHeight = 20.sp)
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            if (!requireAcceptance) {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }

        if (!requireAcceptance) {
            RoundBackButton(
                onClick = onAccepted,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 70.dp, bottom = 90.dp),
                sizeDp = 72,
                fillColor = Color(0x14000000)
            )
        }
    }
}