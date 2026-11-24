package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.FocusCategory
import com.example.data.FocusTask
import com.example.data.SessionType
import com.example.ui.components.ChronometerDial
import com.example.viewmodel.AnalyticsSummary
import com.example.viewmodel.TimerUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    timerState: TimerUiState,
    analytics: AnalyticsSummary,
    activeTasks: List<FocusTask>,
    onSelectMode: (SessionType) -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onCompleteOrSkip: () -> Unit,
    onUpdateTarget: (customTitle: String, category: FocusCategory, task: FocusTask?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTargetSheet by remember { mutableStateOf(false) }

    val modeAccent by animateColorAsState(
        targetValue = when (timerState.mode) {
            SessionType.FOCUS -> MaterialTheme.colorScheme.primary
            SessionType.SHORT_BREAK -> MaterialTheme.colorScheme.secondary
            SessionType.LONG_BREAK -> MaterialTheme.colorScheme.tertiary
        },
        label = "mode_accent",
    )

    val displayTargetTitle = timerState.selectedTaskTitle
        ?.takeIf { it.isNotBlank() }
        ?: timerState.customSessionTitle.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.timer_free_focus)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Header: Wordmark + Daily Goal Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(
                            R.string.timer_cycle_fmt,
                            timerState.completedCyclesInSet + 1,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocalFireDepartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(
                                R.string.timer_today_goal_fmt,
                                analytics.todayFocusMinutes,
                                analytics.dailyGoalMinutes,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Mode Segmented Bar
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SessionType.entries.forEach { mode ->
                        val selected = timerState.mode == mode
                        val chipColor = when (mode) {
                            SessionType.FOCUS -> MaterialTheme.colorScheme.primary
                            SessionType.SHORT_BREAK -> MaterialTheme.colorScheme.secondary
                            SessionType.LONG_BREAK -> MaterialTheme.colorScheme.tertiary
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.surfaceContainerLowest
                                    else Color.Transparent,
                                )
                                .clickable { onSelectMode(mode) }
                                .testTag("mode_${mode.name.lowercase()}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(mode.labelRes),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) chipColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Active Focus Target Selector Pill
            Surface(
                onClick = { showTargetSheet = true },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = MaterialTheme.shapes.medium,
                    )
                    .testTag("focus_target_selector"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(timerState.selectedCategory.accentColor),
                        )
                        Column {
                            Text(
                                text = stringResource(timerState.selectedCategory.labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = displayTargetTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = stringResource(R.string.timer_select_focus_target),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bespoke Precision Chronometer Dial
            ChronometerDial(
                formattedTime = timerState.formattedTime,
                progress = timerState.progress,
                mode = timerState.mode,
                isRunning = timerState.isRunning,
                hasStarted = timerState.hasStarted,
                completedCyclesInSet = timerState.completedCyclesInSet,
                accentColor = modeAccent,
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .testTag("chronometer_dial"),
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Transport Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = onResetTimer,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("timer_reset_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.timer_action_reset),
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = onToggleTimer,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = modeAccent,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 32.dp),
                    modifier = Modifier
                        .height(64.dp)
                        .widthIn(min = 168.dp)
                        .testTag("timer_play_pause_button"),
                ) {
                    Icon(
                        imageVector = if (timerState.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            timerState.isRunning -> stringResource(R.string.timer_action_pause)
                            timerState.hasStarted -> stringResource(R.string.timer_action_resume)
                            else -> stringResource(R.string.timer_action_start)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                FilledTonalIconButton(
                    onClick = onCompleteOrSkip,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("timer_complete_skip_button"),
                ) {
                    Icon(
                        imageVector = if (timerState.mode == SessionType.FOCUS) {
                            Icons.Rounded.DoneAll
                        } else {
                            Icons.Rounded.SkipNext
                        },
                        contentDescription = if (timerState.mode == SessionType.FOCUS) {
                            stringResource(R.string.timer_action_complete)
                        } else {
                            stringResource(R.string.timer_action_skip)
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showTargetSheet) {
        FocusTargetPickerSheet(
            timerState = timerState,
            activeTasks = activeTasks,
            onDismiss = { showTargetSheet = false },
            onConfirm = { customTitle, category, task ->
                onUpdateTarget(customTitle, category, task)
                showTargetSheet = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusTargetPickerSheet(
    timerState: TimerUiState,
    activeTasks: List<FocusTask>,
    onDismiss: () -> Unit,
    onConfirm: (customTitle: String, category: FocusCategory, task: FocusTask?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTask by remember {
        mutableStateOf(activeTasks.firstOrNull { it.id == timerState.selectedTaskId })
    }
    var customTitle by remember { mutableStateOf(timerState.customSessionTitle) }
    var selectedCategory by remember { mutableStateOf(timerState.selectedCategory) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.timer_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Category chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FocusCategory.entries.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedCategory = cat
                            if (selectedTask != null && selectedTask!!.parsedCategory() != cat) {
                                selectedTask = null
                            }
                        },
                        label = { Text(stringResource(cat.labelRes)) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = cat.accentColor.copy(alpha = 0.18f),
                            selectedLabelColor = cat.accentColor,
                            selectedLeadingIconColor = cat.accentColor,
                        ),
                    )
                }
            }

            OutlinedTextField(
                value = customTitle,
                onValueChange = {
                    customTitle = it.take(120)
                    if (it.isNotBlank()) selectedTask = null
                },
                label = { Text(stringResource(R.string.timer_custom_session_label)) },
                placeholder = { Text(stringResource(R.string.timer_custom_session_placeholder)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )

            if (activeTasks.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.tasks_active_section, activeTasks.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeTasks.take(5).forEach { task ->
                        val picked = selectedTask?.id == task.id
                        Surface(
                            onClick = {
                                selectedTask = if (picked) null else task
                                if (!picked) {
                                    selectedCategory = task.parsedCategory()
                                    customTitle = ""
                                }
                            },
                            shape = MaterialTheme.shapes.small,
                            color = if (picked) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.TaskAlt,
                                        contentDescription = null,
                                        tint = task.parsedCategory().accentColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = "${task.completedPomodoros}/${task.estimatedPomodoros}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { onConfirm(customTitle, selectedCategory, selectedTask) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(
                    text = stringResource(R.string.timer_apply_target),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
