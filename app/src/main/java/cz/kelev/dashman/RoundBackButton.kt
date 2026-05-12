package cz.kelev.dashman

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RoundBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Int = 72,
    borderColor: Color = Color(0xFFE53935),
    borderWidthDp: Float = 1.5f,
    // максимально прозрачная заливка, чтоб не жрало контент
    fillColor: Color = Color(0x14000000)
) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .border(borderWidthDp.dp, borderColor, CircleShape)
            .background(fillColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "🔙",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}