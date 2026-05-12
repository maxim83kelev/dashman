package cz.kelev.dashman

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.animation.animateContentSize

import cz.kelev.dashman.storage.ReminderEntity
import cz.kelev.dashman.ui.formatRepeatRange
import cz.kelev.dashman.ui.formatRepeatText
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MainScreen(
    isListening: Boolean = false,
    hotwordEnabled: Boolean = false,
    cards: List<ReminderEntity>,
    voiceFilterMode: Boolean = false,
    onMicClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onHotwordToggle: (Boolean) -> Unit = {},
    onDeleteReminder: (Long) -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {

        // Фон splash1
        Image(
            painter = painterResource(id = R.drawable.splash1),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Нижняя 1/3: затемнение от прозрачного к чёрному (чтобы низ был “в киношном” стиле)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.33f)
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black)
                    )
                )
        )

        var expandedCardId by rememberSaveable { mutableStateOf<Long?>(null) }
        val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
        var collapseJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        val listState = rememberLazyListState()

        // Карточки: занимают ВЕСЬ экран и скроллятся под нижний блок кнопок
        val voiceResultTitle = when {
            !voiceFilterMode -> null
            cards.isEmpty() -> "Ничего не найдено"
            cards.size == 1 -> "Найдено 1 напоминание"
            else -> "Найдено ${cards.size} напоминания(й)"
        }

        // Следим за изменением первого элемента — если список не пустой, скроллим к 0
        val firstItemKey = cards.firstOrNull()?.id
        LaunchedEffect(firstItemKey) {
            if (cards.isNotEmpty()) {
                listState.animateScrollToItem(0)
            }
        }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(
                    top = 24.dp,
                    bottom = 260.dp
                )
            ) {
                if (voiceResultTitle != null) {
                    item(key = "voice_filter_header") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xCC202020)
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 6.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Голосовой поиск",
                                    color = Color.Red,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = voiceResultTitle,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                items(
                    items = cards,
                    key = { it.id }
                ) { reminder ->

                    SwipeToDelete(
                        onDeleteConfirmed = { onDeleteReminder(reminder.id) }
                    ) {
                        ReminderCard(
                            reminder = reminder,
                            expanded = (expandedCardId == reminder.id),
                            onClick = {
                                collapseJob?.cancel()
                                expandedCardId =
                                    if (expandedCardId == reminder.id) null
                                    else {
                                        collapseJob = coroutineScope.launch {
                                            kotlinx.coroutines.delay(5_000L)
                                            expandedCardId = null
                                        }
                                        reminder.id
                                    }
                            }
                        )
                    }
                }
            }

        // Блок с кнопками (overlay поверх карточек)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.55f to Color.Transparent,

                            // начинаем резко темнеть
                            0.70f to Color(0x88000000),

                            // нижняя треть – почти чёрная
                            0.85f to Color(0xCC000000),
                            1.00f to Color(0xFF000000)
                        )
                    )
                )
                .padding(start = 24.dp, end = 24.dp, bottom = 26.dp)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val micScale by animateFloatAsState(
                    targetValue = if (isListening) 1.10f else 1.00f,
                    animationSpec = if (isListening)
                        infiniteRepeatable(
                            animation = tween(420, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        )
                    else tween(180),
                    label = "micPulse"
                )
                // КНОПКА МИКРОФОНА (чуть больше + чуть выше)
                RoundIconButton(
                    iconRes = R.drawable.mic,
                    size = 170.dp,                 // <-- РАЗМЕР МИКРОФОНА
                    ringColor = Color.Red,
                    onClick = onMicClick,
                    modifier = Modifier
                        .offset(y = (-18).dp) // <-- ВЫШЕ/НИЖЕ МИКРОФОН
                        .graphicsLayer {
                            scaleX = micScale
                            scaleY = micScale
                        }
                )

                // КНОПКА НАСТРОЕК (чуть ближе к центру + чуть ниже)
                RoundIconButton(
                    iconRes = R.drawable.settings,
                    size = 96.dp,
                    ringColor = Color.Red,
                    onClick = onSettingsClick,
                    modifier = Modifier.offset(x = (-26).dp, y = 14.dp) // <-- X к центру / Y ниже
                )
            }
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: ReminderEntity,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val dueAtText = formatReminderDateTime(reminder.dueAt)
    val repeatText = formatRepeatText(reminder.repeat)
    val rangeText = formatRepeatRange(reminder.repeatFrom, reminder.repeatUntil)

    val priorityStripeColor = when {
        reminder.status == "fired" -> Color.Transparent
        reminder.priority == "critical" -> Color(0xFFE53935)
        reminder.priority == "important" -> Color(0xFFFFC107)
        else -> Color.Transparent
    }

    val firedStripeColor = Color(0xFF607D8B)

    // val borderWidth = if (reminder.status == "fired") 2.dp else 0.dp

    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = interactionSource
            ) {
                onClick()
            }
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xCC3A3A3A)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 6.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (priorityStripeColor != Color.Transparent) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .background(priorityStripeColor)
                )

                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = reminder.text,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (reminder.status == "fired") {
                    Text(
                        text = "Сработало",
                        color = Color(0xFFFFD600),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (rangeText.isNotBlank()) {
                        Text(
                            text = rangeText,
                            color = Color(0xFFB0B0B0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    if (repeatText.isNotBlank()) {
                        Text(
                            text = repeatText,
                            color = Color(0xFFB0B0B0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Text(
                        text = dueAtText,
                        color = Color(0xFFB0B0B0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (reminder.status == "fired") {
                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .background(firedStripeColor)
                )
            }
        }
    }
}

private fun formatReminderDateTime(dueAtMillis: Long?): String {
    if (dueAtMillis == null || dueAtMillis <= 0L) return "--:--"

    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = dueAtMillis }

    val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(dueAtMillis))

    fun Calendar.startOfDayMillis(): Long {
        val copy = this.clone() as Calendar
        copy.set(Calendar.HOUR_OF_DAY, 0)
        copy.set(Calendar.MINUTE, 0)
        copy.set(Calendar.SECOND, 0)
        copy.set(Calendar.MILLISECOND, 0)
        return copy.timeInMillis
    }

    val todayStart = now.startOfDayMillis()
    val targetStart = target.startOfDayMillis()

    val dateText = when {
        targetStart == todayStart -> "сегодня"
        target.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
            SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(dueAtMillis))
        else ->
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(dueAtMillis))
    }

    return "$dateText, $timeText"
}

@Composable
private fun CircularImageButton(
    size: androidx.compose.ui.unit.Dp,
    imageRes: Int,
    borderColor: Color,
    borderWidth: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable { onClick() }
            .drawBehind {
                // круглая обводка
                drawCircle(
                    color = borderColor,
                    style = Stroke(width = borderWidth.toPx())
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .padding((size * 0.18f)), // чуть воздуха внутри
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun RoundIconButton(
    iconRes: Int,
    size: Dp,
    ringColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Transparent)
            .border(2.dp, ringColor, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}