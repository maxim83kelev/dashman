package cz.kelev.dashman

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import cz.kelev.dashman.ui.theme.MainViewModel
import cz.kelev.dashman.storage.ReminderEntity
import cz.kelev.dashman.services.voice.delete.ReminderDeletion
import cz.kelev.dashman.network.LicenseChecker
import cz.kelev.dashman.network.LicenseResult
import cz.kelev.dashman.ui.PremiumGateDialog

@Composable
fun AppRoot(
    activity: MainActivity,
    vm: MainViewModel,
    ttsManager: TtsManager,
    hotwordEnabledState: State<Boolean>,
    onHotwordToggle: (Boolean) -> Unit,
    onMicClick: () -> Unit,
    onTermsAccepted: () -> Unit,
    premiumGateMessage: androidx.compose.runtime.State<String?> = remember { mutableStateOf(null) },
    onPremiumGateDismiss: () -> Unit = {}
) {
    val terms = rememberTermsData(activity)
    val prefs = remember { cz.kelev.dashman.storage.AppPrefs(activity) }

    var acceptedTermsHash by terms.acceptedHashState
    val currentTermsHash by terms.currentHashState
    val combinedTermsText: String? = null   // не используется, экран сам грузит
    val termsLoadError by terms.errorTextState

    var stage by rememberSaveable { mutableStateOf(AppStage.LOADING) }
    var screen by rememberSaveable { mutableStateOf(AppScreen.MAIN) }
    var greetedMain by rememberSaveable { mutableStateOf(false) }
    var licenseResult by remember { mutableStateOf<LicenseResult?>(null) }
    var updateInfo by remember { mutableStateOf<cz.kelev.dashman.network.UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    val hotwordEnabled by hotwordEnabledState
    val reminders by vm.reminders.collectAsState()
    val voiceFilterMode by vm.voiceFilterResultShown.collectAsState()

    val isBriefing = remember {
        activity.intent?.getBooleanExtra(
            cz.kelev.dashman.services.BriefingReceiver.EXTRA_FROM_BRIEFING, false
        ) ?: false
    }

    val isFromReminder = remember {
        (activity.intent?.getLongExtra(
            cz.kelev.dashman.services.ReminderReceiver.EXTRA_REMINDER_ID, -1L
        ) ?: -1L) > 0L
    }

    LaunchedEffect(stage) {
        if (stage == AppStage.MAIN && screen == AppScreen.MAIN && !greetedMain && !isBriefing && !isFromReminder) {
            greetedMain = true
            ttsManager.speakNow(GreetingPhrases.random(), utteranceId = "dashman_greeting")
        }
    }

    LaunchedEffect(Unit) {
        licenseResult = LicenseChecker.check(activity)
    }

    LaunchedEffect(Unit) {
        val result = cz.kelev.dashman.network.UpdateChecker.check(activity)
        if (result is cz.kelev.dashman.network.UpdateResult.Available) {
            val info = result.info
            if (prefs.getUpdateOfferedVersion() != info.latestVersion) {
                updateInfo = info
                showUpdateDialog = true
            }
        }
    }

    val gateMsg by premiumGateMessage
    if (gateMsg != null) {
        PremiumGateDialog(
            message = gateMsg!!,
            onDismiss = onPremiumGateDismiss,
            onGoToSettings = {
                onPremiumGateDismiss()
                screen = AppScreen.SETTINGS
            }
        )
    }

    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            info = updateInfo!!,
            onDismiss = {
                showUpdateDialog = false
            },
            onDownload = { url ->
                prefs.setUpdateOfferedVersion(updateInfo!!.latestVersion)
                showUpdateDialog = false
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url)
                )
                activity.startActivity(intent)
            }
        )
    }

    when (stage) {
        AppStage.LOADING -> {
            LoadingScreen(
                brain = vm.brain as cz.kelev.dashman.services.brain.SimpleBrain,
                onFinished = {
                    if (currentTermsHash != null && acceptedTermsHash == currentTermsHash) {
                        val licStatus = prefs.getLicenseStatus()
                        stage = if (licStatus == "active" || licStatus == "expired") AppStage.MAIN else AppStage.ACTIVATION
                    } else {
                        stage = AppStage.TERMS
                    }
                }
            )
        }

        AppStage.TERMS -> {
            TermsFlow(
                combinedTermsText = combinedTermsText,
                termsLoadError = termsLoadError,
                currentTermsHash = currentTermsHash,
                onAccept = { hash ->
                    terms.saveAcceptedHash(hash)
                    acceptedTermsHash = hash
                    val licStatus = prefs.getLicenseStatus()
                    stage = if (licStatus == "active" || licStatus == "expired") AppStage.MAIN else AppStage.ACTIVATION
                    onTermsAccepted()
                }
            )
        }

        AppStage.ACTIVATION -> {
            ActivationScreen(
                onActivated = { stage = AppStage.MAIN }
            )
        }

        AppStage.MAIN -> {
            if (licenseResult == LicenseResult.Revoked) {
                LicenseBlockedScreen()
            } else if (licenseResult == LicenseResult.NotFound) {
                ActivationScreen(onActivated = { stage = AppStage.MAIN })
            } else {
                MainFlow(
                    screen = screen,
                    setScreen = { screen = it },
                    reminders = reminders,
                    voiceFilterMode = voiceFilterMode,
                    onDeleteReminder = { id ->
                        ReminderDeletion.deleteById(reminders, id) { entity ->
                            vm.delete(entity)
                        }
                        vm.onSwipeAction()
                    },
                    onMicClick = onMicClick,
                    hotwordEnabled = hotwordEnabled,
                    onHotwordToggle = onHotwordToggle,
                    onCleanupNow = { vm.cleanupNow() },
                    onSetCleanupMode = { mode -> vm.setCleanupMode(mode) },
                    onBriefingChanged = { enabled, hour, minute ->
                        vm.setBriefing(enabled, hour, minute)
                    },
                    onSpeakOnFireChanged = { enabled -> vm.setSpeakOnFire(enabled) },
                    viewModel = vm,
                )
            }
        }
    }
}

@Composable
private fun TermsFlow(
    combinedTermsText: String?,
    termsLoadError: String?,
    currentTermsHash: String?,
    onAccept: (String) -> Unit
) {
    var accepted by remember { mutableStateOf(false) }

    TermsGateScreen(
        title = "Условия использования",
        combinedTermsText = combinedTermsText,
        errorText = termsLoadError,
        accepted = accepted,
        onAcceptChanged = { accepted = it },
        onAccept = {
            val hash = currentTermsHash
            if (hash != null && accepted) {
                onAccept(hash)
            }
        }
    )
}

@Composable
private fun MainFlow(
    screen: AppScreen,
    setScreen: (AppScreen) -> Unit,
    reminders: List<ReminderEntity>,
    voiceFilterMode: Boolean,
    onDeleteReminder: (Long) -> Unit,
    onMicClick: () -> Unit,
    hotwordEnabled: Boolean,
    onHotwordToggle: (Boolean) -> Unit,
    onCleanupNow: () -> Unit,
    onSetCleanupMode: (Int) -> Unit,
    onBriefingChanged: (Boolean, Int, Int) -> Unit,
    onSpeakOnFireChanged: (Boolean) -> Unit,
    viewModel: MainViewModel,
) {
    AppNavigation(
        screen = screen,
        setScreen = setScreen,
        reminders = reminders,
        voiceFilterMode = voiceFilterMode,
        onMicClick = onMicClick,
        onDeleteReminder = onDeleteReminder,
        hotwordEnabled = hotwordEnabled,
        onHotwordToggle = onHotwordToggle,
        onCleanupNow = onCleanupNow,
        onSetCleanupMode = onSetCleanupMode,
        onBriefingChanged = onBriefingChanged,
        onSpeakOnFireChanged = onSpeakOnFireChanged,
        viewModel = viewModel,
    )
}