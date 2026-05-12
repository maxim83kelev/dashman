package cz.kelev.dashman

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ContactAuthorScreen(
    viewModel: ContactAuthorViewModel,
    onBack: () -> Unit
) {
    val message by viewModel.message.collectAsState()

    val minChars = 20
    val maxChars = 2000

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0E0E0E),
                        Color(0xFF151515),
                        Color(0xFF1B1B1B)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {

            Spacer(modifier = Modifier.height(24.dp))

            // Поле ввода
            OutlinedTextField(
                value = message,
                onValueChange = {
                    if (it.length <= maxChars) viewModel.updateMessage(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                placeholder = {
                    if (message.isEmpty()) {
                        Text(
                            text = "Опиши проблему минимум в 20 символах и прикрепи отчёт об ошибках",
                            color = Color.Gray
                        )
                    }
                },
                textStyle = LocalTextStyle.current.copy(color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.DarkGray,
                    unfocusedBorderColor = Color.DarkGray,
                    cursorColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Счётчик символов
            Text(
                text = "${message.length} / $maxChars",
                color = if (message.length < minChars) Color.Gray else Color(0xFF7CFC98),
                modifier = Modifier.align(Alignment.End)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Кнопки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // Прикрепить отчёт
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .background(
                            color = Color(0xFF2A2A2A),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Прикрепить\nотчёт",
                        color = Color.White
                    )
                }

                // Отправить
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .background(
                            color = if (message.length >= minChars)
                                Color(0xFF3A3A3A)
                            else
                                Color(0xFF1F1F1F),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Отправить",
                        color = if (message.length >= minChars)
                            Color.White
                        else
                            Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
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