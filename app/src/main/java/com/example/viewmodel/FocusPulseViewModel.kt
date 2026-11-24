package com.example.viewmodel

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DateKeyUtils
import com.example.data.FocusCategory
import com.example.data.FocusRepository
import com.example.data.FocusSession
import com.example.data.FocusTask
import com.example.data.SessionType
import com.example.data.UiState
import com.example.data.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TimerUiState(
    val mode: SessionType = SessionType.FOCUS,
    val totalDurationSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val hasStarted: Boolean = false,
    val completedCyclesInSet: Int = 0,
    val selectedTaskId: String? = null,
    val selectedTaskTitle: String? = null,
    val customSessionTitle: String = "",
    val selectedCategory: FocusCategory = FocusCategory.DEEP_WORK,
) {
    val progress: Float
        get() = if (totalDurationSeconds <= 0) 0f
        else ((totalDurationSeconds - remainingSeconds).toFloat() / totalDurationSeconds.toFloat()).coerceIn(0f, 1f)

    val elapsedSeconds: Int
        get() = (totalDurationSeconds - remainingSeconds).coerceAtLeast(0)

    val formattedTime: String
        get() {
            val mins = (remainingSeconds / 60).coerceAtLeast(0)
            val secs = (remainingSeconds % 60).coerceAtLeast(0)
            return "%02d:%02d".format(mins, secs)
        }
}

data class DailyChartPoint(
    val dateKey: String,
    val shortDayLabel: String,
    val shortDateLabel: String,
    val focusMinutes: Int,
    val isToday: Boolean,
)

data class CategoryBreakdownItem(
    val category: FocusCategory,
    val totalMinutes: Int,
    val ratio: Float,
)

data class AnalyticsSummary(
    val todayFocusMinutes: Int = 0,
    val dailyGoalMinutes: Int = 120,
    val dailyGoalRatio: Float = 0f,
    val streakDays: Int = 0,
    val totalFocusSessions: Int = 0,
    val totalFocusMinutes: Int = 0,
    val weeklyPoints: List<DailyChartPoint> = emptyList(),
    val categoryBreakdown: List<CategoryBreakdownItem> = emptyList(),
)

sealed interface FocusEvent {
    data class SessionLogged(val durationMinutes: Int) : FocusEvent
    data object BreakFinished : FocusEvent
    data object NeedOneMinute : FocusEvent
    data object SettingsSaved : FocusEvent
    data class ErrorMessage(val message: String) : FocusEvent
}

class FocusPulseViewModel(
    private val repository: FocusRepository,
    private val currentUserId: String,
    private val initialDisplayName: String,
) : ViewModel() {

    private val _events = MutableSharedFlow<FocusEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<FocusEvent> = _events.asSharedFlow()

    private val _hapticTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val hapticTrigger: SharedFlow<Unit> = _hapticTrigger.asSharedFlow()

    val profileState: StateFlow<UiState<UserProfile>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile>> { profile ->
            UiState.Success(
                profile ?: UserProfile(
                    id = currentUserId,
                    userId = currentUserId,
                    displayName = initialDisplayName.ifBlank { "FocusPulse User" },
                ),
            )
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing profile", rootError)
            emit(UiState.Error(rootError.message ?: "프로필을 불러오지 못했습니다."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val sessionsState: StateFlow<UiState<List<FocusSession>>> = repository.observeSessions(currentUserId)
        .map<List<FocusSession>, UiState<List<FocusSession>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing sessions", rootError)
            emit(UiState.Error(rootError.message ?: "집중 기록을 불러오지 못했습니다."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val tasksState: StateFlow<UiState<List<FocusTask>>> = repository.observeTasks(currentUserId)
        .map<List<FocusTask>, UiState<List<FocusTask>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing tasks", rootError)
            emit(UiState.Error(rootError.message ?: "할 일을 불러오지 못했습니다."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val analyticsSummary: StateFlow<AnalyticsSummary> = combine(
        sessionsState,
        profileState,
    ) { sessionsUi, profileUi ->
        val sessions = (sessionsUi as? UiState.Success)?.data.orEmpty()
        val goal = (profileUi as? UiState.Success)?.data?.dailyGoalMinutes?.toInt()?.coerceIn(15, 720) ?: 120
        computeAnalytics(sessions, goal)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = AnalyticsSummary(),
    )

    private val _timerState = MutableStateFlow(TimerUiState())
    val timerState: StateFlow<TimerUiState> = _timerState.asStateFlow()

    private var timerJob: Job? = null
    private var latestProfile: UserProfile = UserProfile(
        id = currentUserId,
        userId = currentUserId,
        displayName = initialDisplayName.ifBlank { "FocusPulse User" },
    )

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(currentUserId, initialDisplayName)
        }
        viewModelScope.launch {
            profileState.collect { ui ->
                if (ui is UiState.Success) {
                    latestProfile = ui.data
                    val current = _timerState.value
                    if (!current.isRunning && !current.hasStarted) {
                        val seconds = ui.data.durationMinutesFor(current.mode) * 60
                        _timerState.update {
                            it.copy(totalDurationSeconds = seconds, remainingSeconds = seconds)
                        }
                    }
                }
            }
        }
    }

    fun selectMode(mode: SessionType) {
        timerJob?.cancel()
        val seconds = latestProfile.durationMinutesFor(mode) * 60
        _timerState.update {
            it.copy(
                mode = mode,
                totalDurationSeconds = seconds,
                remainingSeconds = seconds,
                isRunning = false,
                hasStarted = false,
            )
        }
    }

    fun toggleTimer() {
        val current = _timerState.value
        if (current.isRunning) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    fun startTimer() {
        if (_timerState.value.isRunning) return
        if (latestProfile.hapticsEnabled) {
            _hapticTrigger.tryEmit(Unit)
        }
        val startingSeconds = _timerState.value.remainingSeconds.coerceAtLeast(1)
        val endRealtimeMs = SystemClock.elapsedRealtime() + startingSeconds * 1000L

        _timerState.update { it.copy(isRunning = true, hasStarted = true) }

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                val remainingMs = endRealtimeMs - SystemClock.elapsedRealtime()
                val nextSeconds = ((remainingMs + 999L) / 1000L).toInt().coerceAtLeast(0)
                if (nextSeconds != _timerState.value.remainingSeconds) {
                    _timerState.update { it.copy(remainingSeconds = nextSeconds) }
                }
                if (remainingMs <= 0L) {
                    onTimerNaturallyCompleted()
                    break
                }
                delay(250L)
            }
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
        _timerState.update { it.copy(isRunning = false) }
    }

    fun resetTimer() {
        timerJob?.cancel()
        val seconds = latestProfile.durationMinutesFor(_timerState.value.mode) * 60
        _timerState.update {
            it.copy(
                totalDurationSeconds = seconds,
                remainingSeconds = seconds,
                isRunning = false,
                hasStarted = false,
            )
        }
    }

    fun completeEarlyOrSkip() {
        val current = _timerState.value
        if (current.mode != SessionType.FOCUS) {
            selectMode(SessionType.FOCUS)
            return
        }
        val elapsedMinutes = current.elapsedSeconds / 60
        if (elapsedMinutes < 1) {
            _events.tryEmit(FocusEvent.NeedOneMinute)
            return
        }
        timerJob?.cancel()
        finalizeFocusSession(loggedMinutes = elapsedMinutes)
    }

    fun selectTaskForFocus(task: FocusTask?) {
        _timerState.update {
            if (task == null) {
                it.copy(selectedTaskId = null, selectedTaskTitle = null)
            } else {
                it.copy(
                    selectedTaskId = task.id,
                    selectedTaskTitle = task.title,
                    selectedCategory = task.parsedCategory(),
                )
            }
        }
    }

    fun updateFocusTarget(
        customTitle: String,
        category: FocusCategory,
        task: FocusTask?,
    ) {
        _timerState.update {
            it.copy(
                selectedTaskId = task?.id,
                selectedTaskTitle = task?.title,
                customSessionTitle = if (task != null) "" else customTitle.trim().take(120),
                selectedCategory = task?.parsedCategory() ?: category,
            )
        }
    }

    private fun onTimerNaturallyCompleted() {
        val current = _timerState.value
        if (latestProfile.hapticsEnabled) {
            _hapticTrigger.tryEmit(Unit)
        }
        if (current.mode == SessionType.FOCUS) {
            val minutes = (current.totalDurationSeconds / 60).coerceAtLeast(1)
            finalizeFocusSession(loggedMinutes = minutes)
        } else {
            _events.tryEmit(FocusEvent.BreakFinished)
            selectMode(SessionType.FOCUS)
        }
    }

    private fun finalizeFocusSession(loggedMinutes: Int) {
        val snapshot = _timerState.value
        val sessionTitle = snapshot.selectedTaskTitle
            ?.takeIf { it.isNotBlank() }
            ?: snapshot.customSessionTitle.takeIf { it.isNotBlank() }
            ?: "자유 집중 세션"

        val nextCycles = (snapshot.completedCyclesInSet + 1) % 4
        val nextMode = if (nextCycles == 0) SessionType.LONG_BREAK else SessionType.SHORT_BREAK
        val nextDurationSec = latestProfile.durationMinutesFor(nextMode) * 60

        _timerState.update {
            it.copy(
                mode = nextMode,
                totalDurationSeconds = nextDurationSec,
                remainingSeconds = nextDurationSec,
                isRunning = false,
                hasStarted = false,
                completedCyclesInSet = nextCycles,
            )
        }

        viewModelScope.launch {
            val logResult = repository.logSession(
                userId = currentUserId,
                title = sessionTitle,
                category = snapshot.selectedCategory,
                durationMinutes = loggedMinutes,
                sessionType = SessionType.FOCUS,
                dateKey = DateKeyUtils.todayKey(),
                taskId = snapshot.selectedTaskId,
            )
            if (logResult.isSuccess) {
                if (!snapshot.selectedTaskId.isNullOrBlank()) {
                    repository.incrementTaskPomodoro(currentUserId, snapshot.selectedTaskId)
                }
                _events.emit(FocusEvent.SessionLogged(loggedMinutes))
            } else {
                _events.emit(
                    FocusEvent.ErrorMessage(
                        logResult.exceptionOrNull()?.localizedMessage ?: "세션 저장에 실패했습니다.",
                    ),
                )
            }
        }

        if (latestProfile.autoStartBreak) {
            startTimer()
        }
    }

    fun addTask(title: String, category: FocusCategory, estimatedPomodoros: Int) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val res = repository.addTask(currentUserId, trimmed, category, estimatedPomodoros)
            if (res.isFailure) {
                _events.emit(
                    FocusEvent.ErrorMessage(
                        res.exceptionOrNull()?.localizedMessage ?: "할 일을 추가하지 못했습니다.",
                    ),
                )
            }
        }
    }

    fun updateTask(taskId: String, title: String, category: FocusCategory, estimatedPomodoros: Int) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val res = repository.updateTask(currentUserId, taskId, trimmed, category, estimatedPomodoros)
            if (res.isSuccess && _timerState.value.selectedTaskId == taskId) {
                _timerState.update {
                    it.copy(selectedTaskTitle = trimmed, selectedCategory = category)
                }
            } else if (res.isFailure) {
                _events.emit(
                    FocusEvent.ErrorMessage(
                        res.exceptionOrNull()?.localizedMessage ?: "할 일을 수정하지 못했습니다.",
                    ),
                )
            }
        }
    }

    fun toggleTaskCompleted(task: FocusTask) {
        viewModelScope.launch {
            val res = repository.toggleTaskCompleted(currentUserId, task.id, !task.completed)
            if (res.isFailure) {
                _events.emit(
                    FocusEvent.ErrorMessage(
                        res.exceptionOrNull()?.localizedMessage ?: "상태를 변경하지 못했습니다.",
                    ),
                )
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            val res = repository.deleteTask(currentUserId, taskId)
            if (res.isSuccess && _timerState.value.selectedTaskId == taskId) {
                _timerState.update { it.copy(selectedTaskId = null, selectedTaskTitle = null) }
            } else if (res.isFailure) {
                _events.emit(
                    FocusEvent.ErrorMessage(
                        res.exceptionOrNull()?.localizedMessage ?: "할 일을 삭제하지 못했습니다.",
                    ),
                )
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            val res = repository.deleteSession(currentUserId, sessionId)
            if (res.isFailure) {
                _events.emit(
                    FocusEvent.ErrorMessage(
                        res.exceptionOrNull()?.localizedMessage ?: "세션을 삭제하지 못했습니다.",
                    ),
                )
            }
        }
    }

    fun saveSettings(
        displayName: String,
        focusMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        dailyGoalMinutes: Int,
        hapticsEnabled: Boolean,
        autoStartBreak: Boolean,
    ) {
        viewModelScope.launch {
            val res = repository.updateSettings(
                userId = currentUserId,
                displayName = displayName,
                focusMinutes = focusMinutes,
                shortBreakMinutes = shortBreakMinutes,
                longBreakMinutes = longBreakMinutes,
                dailyGoalMinutes = dailyGoalMinutes,
                hapticsEnabled = hapticsEnabled,
                autoStartBreak = autoStartBreak,
            )
            if (res.isSuccess) {
                _events.emit(FocusEvent.SettingsSaved)
            } else {
                _events.emit(
                    FocusEvent.ErrorMessage(
                        res.exceptionOrNull()?.localizedMessage ?: "설정을 저장하지 못했습니다.",
                    ),
                )
            }
        }
    }

    private fun computeAnalytics(sessions: List<FocusSession>, dailyGoal: Int): AnalyticsSummary {
        val focusSessions = sessions.filter { it.parsedSessionType() == SessionType.FOCUS }
        val today = DateKeyUtils.todayKey()
        val minutesByDate = focusSessions.groupBy { it.dateKey }
            .mapValues { (_, list) -> list.sumOf { it.durationMinutes.toInt() } }

        val todayMinutes = minutesByDate[today] ?: 0
        val totalMinutes = focusSessions.sumOf { it.durationMinutes.toInt() }
        val streak = DateKeyUtils.calculateStreak(minutesByDate.keys)

        val weeklyPoints = DateKeyUtils.pastDays(7).map { bucket ->
            DailyChartPoint(
                dateKey = bucket.dateKey,
                shortDayLabel = bucket.shortDayLabel,
                shortDateLabel = bucket.shortDateLabel,
                focusMinutes = minutesByDate[bucket.dateKey] ?: 0,
                isToday = bucket.isToday,
            )
        }

        val categoryBreakdown = if (totalMinutes > 0) {
            focusSessions.groupBy { it.parsedCategory() }
                .map { (cat, list) ->
                    val mins = list.sumOf { it.durationMinutes.toInt() }
                    CategoryBreakdownItem(
                        category = cat,
                        totalMinutes = mins,
                        ratio = (mins.toFloat() / totalMinutes.toFloat()).coerceIn(0f, 1f),
                    )
                }
                .sortedByDescending { it.totalMinutes }
        } else {
            emptyList()
        }

        return AnalyticsSummary(
            todayFocusMinutes = todayMinutes,
            dailyGoalMinutes = dailyGoal,
            dailyGoalRatio = (todayMinutes.toFloat() / dailyGoal.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
            streakDays = streak,
            totalFocusSessions = focusSessions.size,
            totalFocusMinutes = totalMinutes,
            weeklyPoints = weeklyPoints,
            categoryBreakdown = categoryBreakdown,
        )
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    private companion object {
        const val TAG = "FocusPulseVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
