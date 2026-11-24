package com.example.data

import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.example.R
import com.example.ui.theme.CatCoding
import com.example.ui.theme.CatCreative
import com.example.ui.theme.CatDeepWork
import com.example.ui.theme.CatOther
import com.example.ui.theme.CatReading
import com.example.ui.theme.CatStudy
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class FocusCategory(
    @StringRes val labelRes: Int,
    val accentColor: Color,
) {
    DEEP_WORK(R.string.cat_deep_work, CatDeepWork),
    CODING(R.string.cat_coding, CatCoding),
    STUDY(R.string.cat_study, CatStudy),
    READING(R.string.cat_reading, CatReading),
    CREATIVE(R.string.cat_creative, CatCreative),
    OTHER(R.string.cat_other, CatOther);

    companion object {
        fun fromRaw(raw: String?): FocusCategory =
            entries.firstOrNull { it.name == raw } ?: DEEP_WORK
    }
}

enum class SessionType(@StringRes val labelRes: Int) {
    FOCUS(R.string.mode_focus),
    SHORT_BREAK(R.string.mode_short_break),
    LONG_BREAK(R.string.mode_long_break);

    companion object {
        fun fromRaw(raw: String?): SessionType =
            entries.firstOrNull { it.name == raw } ?: FOCUS
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Focus User",
    val focusMinutes: Long = 25L,
    val shortBreakMinutes: Long = 5L,
    val longBreakMinutes: Long = 15L,
    val dailyGoalMinutes: Long = 120L,
    val hapticsEnabled: Boolean = true,
    val autoStartBreak: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun durationMinutesFor(type: SessionType): Int = when (type) {
        SessionType.FOCUS -> focusMinutes.toInt().coerceIn(1, 120)
        SessionType.SHORT_BREAK -> shortBreakMinutes.toInt().coerceIn(1, 60)
        SessionType.LONG_BREAK -> longBreakMinutes.toInt().coerceIn(1, 60)
    }
}

@Keep
@IgnoreExtraProperties
data class FocusSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = FocusCategory.DEEP_WORK.name,
    val durationMinutes: Long = 25L,
    val sessionType: String = SessionType.FOCUS.name,
    val dateKey: String = "",
    val taskId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
) {
    fun parsedCategory(): FocusCategory = FocusCategory.fromRaw(category)
    fun parsedSessionType(): SessionType = SessionType.fromRaw(sessionType)
}

@Keep
@IgnoreExtraProperties
data class FocusTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = FocusCategory.DEEP_WORK.name,
    val estimatedPomodoros: Long = 2L,
    val completedPomodoros: Long = 0L,
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun parsedCategory(): FocusCategory = FocusCategory.fromRaw(category)
}

object DateKeyUtils {
    private val keyFormatter: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val dayLabelFormatter: SimpleDateFormat
        get() = SimpleDateFormat("E", Locale.KOREAN)

    private val timeFormatter: SimpleDateFormat
        get() = SimpleDateFormat("HH:mm", Locale.US)

    private val shortDateFormatter: SimpleDateFormat
        get() = SimpleDateFormat("M월 d일", Locale.KOREAN)

    fun todayKey(): String = keyFormatter.format(Date())

    fun pastDays(count: Int = 7): List<DayBucketMeta> {
        val cal = Calendar.getInstance()
        val result = mutableListOf<DayBucketMeta>()
        for (offset in (count - 1) downTo 0) {
            val dayCal = (cal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -offset)
            }
            val date = dayCal.time
            result += DayBucketMeta(
                dateKey = keyFormatter.format(date),
                shortDayLabel = dayLabelFormatter.format(date),
                shortDateLabel = shortDateFormatter.format(date),
                isToday = offset == 0,
            )
        }
        return result
    }

    fun calculateStreak(focusDateKeys: Set<String>): Int {
        if (focusDateKeys.isEmpty()) return 0
        val cal = Calendar.getInstance()
        val today = keyFormatter.format(cal.time)
        if (today !in focusDateKeys) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterday = keyFormatter.format(cal.time)
            if (yesterday !in focusDateKeys) return 0
        }
        var streak = 0
        while (keyFormatter.format(cal.time) in focusDateKeys) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    fun formatSessionTime(timestamp: Timestamp?): String {
        val date = (timestamp ?: Timestamp.now()).toDate()
        return "${shortDateFormatter.format(date)} · ${timeFormatter.format(date)}"
    }
}

data class DayBucketMeta(
    val dateKey: String,
    val shortDayLabel: String,
    val shortDateLabel: String,
    val isToday: Boolean,
)
