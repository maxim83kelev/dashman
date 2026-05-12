package cz.kelev.dashman

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import cz.kelev.dashman.storage.ReminderEntity

@Composable
fun AppNavigation(
    screen: AppScreen,
    setScreen: (AppScreen) -> Unit,
    reminders: List<ReminderEntity>,
    voiceFilterMode: Boolean,
    onMicClick: () -> Unit,
    onDeleteReminder: (Long) -> Unit,
    hotwordEnabled: Boolean,
    onHotwordToggle: (Boolean) -> Unit,
    onCleanupNow: () -> Unit,
    onSetCleanupMode: (Int) -> Unit,
    onBriefingChanged: (Boolean, Int, Int) -> Unit,
    onSpeakOnFireChanged: (Boolean) -> Unit,
    viewModel: cz.kelev.dashman.ui.theme.MainViewModel,
) {
    when (screen) {

        AppScreen.MAIN -> {
            MainScreen(
                cards = reminders,
                isListening = false,
                voiceFilterMode = voiceFilterMode,
                onMicClick = onMicClick,
                onSettingsClick = { setScreen(AppScreen.SETTINGS) },
                onDeleteReminder = onDeleteReminder,
            )
        }

        AppScreen.SETTINGS -> {
            SettingsScreen(
                hotwordEnabled = hotwordEnabled,
                onHotwordToggle = onHotwordToggle,
                onOpenEula = { setScreen(AppScreen.DOC_EULA) },
                onOpenPrivacy = { setScreen(AppScreen.DOC_PRIVACY) },
                onOpenReadme = { setScreen(AppScreen.DOC_README) },
                onOpenContactAuthor = { setScreen(AppScreen.CONTACT_AUTHOR) },
                onCleanupNow = onCleanupNow,
                onSetCleanupMode = onSetCleanupMode,
                onBriefingChanged = onBriefingChanged,
                onSpeakOnFireChanged = onSpeakOnFireChanged,
                onBack = { setScreen(AppScreen.MAIN) },
                viewModel = viewModel
            )
        }

        AppScreen.DOC_EULA -> {
            DocumentScreen(
                title = "Лицензионное соглашение",
                assetFile = "eula.txt",
                requireAcceptance = false,
                onAccepted = { setScreen(AppScreen.SETTINGS) }
            )
        }

        AppScreen.DOC_PRIVACY -> {
            DocumentScreen(
                title = "Политика конфиденциальности",
                assetFile = "privacy.txt",
                requireAcceptance = false,
                onAccepted = { setScreen(AppScreen.SETTINGS) }
            )
        }

        AppScreen.DOC_README -> {
            DocumentScreen(
                title = "README",
                assetFile = "readme_ru.txt",
                requireAcceptance = false,
                onAccepted = { setScreen(AppScreen.SETTINGS) }
            )
        }

        AppScreen.CONTACT_AUTHOR -> {
            val vm = remember { ContactAuthorViewModel() }

            ContactAuthorScreen(
                viewModel = vm,
                onBack = { setScreen(AppScreen.SETTINGS) }
            )
        }
    }
}