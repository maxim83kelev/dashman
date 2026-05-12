package cz.kelev.dashman

import android.content.Context
import androidx.compose.runtime.*

data class TermsData(
    val acceptedHashState: MutableState<String?>,
    val currentHashState: State<String?>,
    val errorTextState: State<String?>,
    val saveAcceptedHash: (String) -> Unit
)

@Composable
fun rememberTermsData(context: Context): TermsData {
    val prefs = remember {
        context.getSharedPreferences("dashman_prefs", Context.MODE_PRIVATE)
    }

    val acceptedHash = remember { mutableStateOf(prefs.getString("accepted_terms_hash", null)) }
    val currentHash = remember { mutableStateOf<String?>(null) }
    val errorText = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val eula = context.assets.open("eula.txt").bufferedReader().use { it.readText() }
            val privacy = context.assets.open("privacy.txt").bufferedReader().use { it.readText() }
            val combined = eula.trim() + "\n\n" + privacy.trim()
            currentHash.value = sha256(combined)
            errorText.value = null
        } catch (_: Throwable) {
            currentHash.value = null
            errorText.value = "Ошибка загрузки документов"
        }
    }

    val saveAccepted: (String) -> Unit = { hash ->
        prefs.edit().putString("accepted_terms_hash", hash).apply()
        acceptedHash.value = hash
    }

    return TermsData(
        acceptedHashState = acceptedHash,
        currentHashState = currentHash,
        errorTextState = errorText,
        saveAcceptedHash = saveAccepted
    )
}