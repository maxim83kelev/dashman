package cz.kelev.dashman

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import android.util.Log
import kotlinx.coroutines.delay

private const val LOADING_DELAY_MS = 1500

@Composable
fun LoadingScreen(
    brain: cz.kelev.dashman.services.brain.SimpleBrain,
    loadingDelayMillis: Long = LOADING_DELAY_MS.toLong(),
    onFinished: () -> Unit
) {
    LaunchedEffect(Unit) {
        try {
            Log.d("DashmanLoading", "LoadingScreen started")
            brain.runAutoCleanupIfNeeded()
            Log.d("DashmanLoading", "Auto-cleanup check finished")
        } catch (e: Exception) {
            Log.e("DashmanLoading", "Auto-cleanup check crashed", e)
        }

        delay(loadingDelayMillis)
        Log.d("DashmanLoading", "Loading finished -> opening main screen")
        onFinished()
    }
    Box(modifier = Modifier.fillMaxSize()) {

        // Фон-картинка во весь экран
        Image(
            painter = painterResource(id = R.drawable.splash),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Волна по буквам
        val word = "LOADING"
        val infinite = rememberInfiniteTransition(label = "loading_wave")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .align(Alignment.Center),

            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            word.forEachIndexed { index, ch ->

                // одна "волна" = 1500мс
                val period = 1500
                val step = 150 // задержка между буквами

                val scale = infinite.animateFloat(
                    initialValue = 0.75f,
                    targetValue = 0.75f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = period
                            0.75f at (index * step) with LinearEasing
                            1.35f at (index * step + 250) with LinearEasing
                            0.75f at (index * step + 500) with LinearEasing
                        },
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "scale_$index"
                )

                val alpha = infinite.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 0.35f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = period
                            0.35f at (index * step) with LinearEasing
                            1.0f at (index * step + 250) with LinearEasing
                            0.35f at (index * step + 500) with LinearEasing
                        },
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "alpha_$index"
                )

                Text(
                    text = ch.toString(),
                    color = Color(0xFFFF0000), // красный
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.White.copy(alpha = 0.85f),
                            offset = Offset(0f, 0f),
                            blurRadius = 14f
                        )
                    ),
                    modifier = Modifier
                        .graphicsLayer{
                            scaleX = scale.value
                            scaleY = scale.value
                            this.alpha = alpha.value
                        }
                )
            }
        }
    }
}