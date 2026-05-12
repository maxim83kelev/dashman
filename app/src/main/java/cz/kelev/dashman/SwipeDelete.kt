package cz.kelev.dashman

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.Text

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SwipeToDelete(
    onDeleteConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dismissState = rememberDismissState(
        confirmStateChange = { value ->
            if (value == DismissValue.DismissedToStart) {
                onDeleteConfirmed()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismiss(
        state = dismissState,
        modifier = modifier,
        directions = setOf(DismissDirection.EndToStart),
        // удаляем только если “дожал дальше”, а не на половине
        dismissThresholds = { _ ->
            androidx.compose.material.FractionalThreshold(0.75f)
        },
        background = {
            // показываем "Удалить?" начиная с половины свайпа
            val show = dismissState.progress.fraction >= 0.5f &&
                dismissState.dismissDirection == DismissDirection.EndToStart

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // никакой красной тени. максимум лёгкий тёмный слой.
                    .background(if (show) Color(0x22000000) else Color.Transparent),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (show) {
                    Text(
                        text = "Удалить?",
                        color = Color.White,
                        modifier = Modifier.padding(end = 20.dp)
                    )
                }
            }
        },
        dismissContent = { content() }
    )
}