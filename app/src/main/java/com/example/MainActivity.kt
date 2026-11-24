package com.example

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthScreen
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.FocusRepository
import com.example.data.UiState
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.TimerScreen
import com.example.ui.theme.FocusPulseTheme
import com.example.viewmodel.FocusEvent
import com.example.viewmodel.FocusPulseViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocusPulseTheme {
                FocusPulseAppNavigation()
            }
        }
    }
}

private enum class FocusTab(
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String,
) {
    TIMER(R.string.nav_timer, Icons.Rounded.Timer, Icons.Outlined.Timer, "nav_timer"),
    TASKS(R.string.nav_tasks, Icons.Rounded.CheckCircle, Icons.Outlined.CheckCircle, "nav_tasks"),
    STATS(R.string.nav_stats, Icons.Rounded.BarChart, Icons.Outlined.BarChart, "nav_stats"),
    SETTINGS(R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings, "nav_settings"),
}

@Composable
private fun FocusPulseAppNavigation() {
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { /* authStateFlow updates automatically */ })
    } else {
        key(user.uid) {
            FocusPulseMainHost(
                currentUserId = user.uid,
                initialDisplayName = user.displayName ?: "FocusPulse User",
                userEmail = user.email,
            )
        }
    }
}

@Composable
private fun FocusPulseMainHost(
    currentUserId: String,
    initialDisplayName: String,
    userEmail: String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: FocusPulseViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                FocusPulseViewModel(
                    repository = FocusRepository(db),
                    currentUserId = currentUserId,
                    initialDisplayName = initialDisplayName,
                )
            }
        },
    )

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val timerState by viewModel.timerState.collectAsStateWithLifecycle()
    val analytics by viewModel.analyticsSummary.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by rememberSaveable { mutableStateOf(FocusTab.TIMER) }

    BackHandler(enabled = currentTab != FocusTab.TIMER) {
        currentTab = FocusTab.TIMER
    }

    LaunchedEffect(viewModel) {
        viewModel.hapticTrigger.collect {
            triggerPulseVibration(context)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val msg = when (event) {
                is FocusEvent.SessionLogged -> context.getString(R.string.timer_session_saved_fmt, event.durationMinutes)
                is FocusEvent.BreakFinished -> context.getString(R.string.timer_break_complete)
                is FocusEvent.NeedOneMinute -> context.getString(R.string.timer_need_one_minute)
                is FocusEvent.SettingsSaved -> context.getString(R.string.settings_saved_toast)
                is FocusEvent.ErrorMessage -> event.message
            }
            snackbarHostState.showSnackbar(msg)
        }
    }

    val activeTasks = remember(tasksState) {
        (tasksState as? UiState.Success)?.data?.filter { !it.completed }.orEmpty()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        if (useNavRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    FocusTab.entries.forEach { tab ->
                        val selected = currentTab == tab
                        NavigationRailItem(
                            selected = selected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = stringResource(tab.labelRes),
                                )
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                            modifier = Modifier.testTag(tab.tag),
                        )
                    }
                }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { innerPadding ->
                    FocusPulseTabContent(
                        currentTab = currentTab,
                        timerState = timerState,
                        analytics = analytics,
                        activeTasks = activeTasks,
                        tasksState = tasksState,
                        sessionsState = sessionsState,
                        profileState = profileState,
                        userEmail = userEmail,
                        viewModel = viewModel,
                        onNavigateToTimer = { currentTab = FocusTab.TIMER },
                        onSignOut = { signOut(credentialManager, scope) },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        windowInsets = WindowInsets.navigationBars,
                    ) {
                        FocusTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes),
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.tag),
                            )
                        }
                    }
                },
            ) { innerPadding ->
                FocusPulseTabContent(
                    currentTab = currentTab,
                    timerState = timerState,
                    analytics = analytics,
                    activeTasks = activeTasks,
                    tasksState = tasksState,
                    sessionsState = sessionsState,
                    profileState = profileState,
                    userEmail = userEmail,
                    viewModel = viewModel,
                    onNavigateToTimer = { currentTab = FocusTab.TIMER },
                    onSignOut = { signOut(credentialManager, scope) },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun FocusPulseTabContent(
    currentTab: FocusTab,
    timerState: com.example.viewmodel.TimerUiState,
    analytics: com.example.viewmodel.AnalyticsSummary,
    activeTasks: List<com.example.data.FocusTask>,
    tasksState: UiState<List<com.example.data.FocusTask>>,
    sessionsState: UiState<List<com.example.data.FocusSession>>,
    profileState: UiState<com.example.data.UserProfile>,
    userEmail: String?,
    viewModel: FocusPulseViewModel,
    onNavigateToTimer: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (currentTab) {
        FocusTab.TIMER -> TimerScreen(
            timerState = timerState,
            analytics = analytics,
            activeTasks = activeTasks,
            onSelectMode = viewModel::selectMode,
            onToggleTimer = viewModel::toggleTimer,
            onResetTimer = viewModel::resetTimer,
            onCompleteOrSkip = viewModel::completeEarlyOrSkip,
            onUpdateTarget = viewModel::updateFocusTarget,
            modifier = modifier,
        )

        FocusTab.TASKS -> TasksScreen(
            tasksState = tasksState,
            selectedTaskId = timerState.selectedTaskId,
            onSelectTaskForFocus = { task ->
                viewModel.selectTaskForFocus(task)
                onNavigateToTimer()
            },
            onAddTask = viewModel::addTask,
            onUpdateTask = viewModel::updateTask,
            onToggleCompleted = viewModel::toggleTaskCompleted,
            onDeleteTask = viewModel::deleteTask,
            modifier = modifier,
        )

        FocusTab.STATS -> StatsScreen(
            sessionsState = sessionsState,
            analytics = analytics,
            onDeleteSession = viewModel::deleteSession,
            modifier = modifier,
        )

        FocusTab.SETTINGS -> SettingsScreen(
            profileState = profileState,
            userEmail = userEmail,
            onSaveSettings = viewModel::saveSettings,
            onSignOut = onSignOut,
            modifier = modifier,
        )
    }
}

@Suppress("DEPRECATION")
private fun triggerPulseVibration(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(90L, VibrationEffect.DEFAULT_AMPLITUDE),
            )
        } else {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(90L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator?.vibrate(90L)
            }
        }
    } catch (_: Exception) {
        // Ignore on devices without vibrator hardware
    }
}
