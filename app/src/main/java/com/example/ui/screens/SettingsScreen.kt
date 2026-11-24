package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.UiState
import com.example.data.UserProfile

@Composable
fun SettingsScreen(
    profileState: UiState<UserProfile>,
    userEmail: String?,
    onSaveSettings: (
        displayName: String,
        focusMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        dailyGoalMinutes: Int,
        hapticsEnabled: Boolean,
        autoStartBreak: Boolean,
    ) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        when (profileState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = profileState.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier.testTag("sign_out_button"),
                    ) {
                        Text(stringResource(R.string.settings_sign_out))
                    }
                }
            }

            is UiState.Success -> {
                val profile = profileState.data
                var focusMinutes by remember { mutableIntStateOf(profile.focusMinutes.toInt()) }
                var shortBreakMinutes by remember { mutableIntStateOf(profile.shortBreakMinutes.toInt()) }
                var longBreakMinutes by remember { mutableIntStateOf(profile.longBreakMinutes.toInt()) }
                var dailyGoalMinutes by remember { mutableIntStateOf(profile.dailyGoalMinutes.toInt()) }
                var hapticsEnabled by remember { mutableStateOf(profile.hapticsEnabled) }
                var autoStartBreak by remember { mutableStateOf(profile.autoStartBreak) }

                LaunchedEffect(profile) {
                    focusMinutes = profile.focusMinutes.toInt().coerceIn(5, 90)
                    shortBreakMinutes = profile.shortBreakMinutes.toInt().coerceIn(1, 30)
                    longBreakMinutes = profile.longBreakMinutes.toInt().coerceIn(5, 45)
                    dailyGoalMinutes = profile.dailyGoalMinutes.toInt().coerceIn(30, 480)
                    hapticsEnabled = profile.hapticsEnabled
                    autoStartBreak = profile.autoStartBreak
                }

                val hasUnsavedChanges =
                    focusMinutes != profile.focusMinutes.toInt() ||
                        shortBreakMinutes != profile.shortBreakMinutes.toInt() ||
                        longBreakMinutes != profile.longBreakMinutes.toInt() ||
                        dailyGoalMinutes != profile.dailyGoalMinutes.toInt() ||
                        hapticsEnabled != profile.hapticsEnabled ||
                        autoStartBreak != profile.autoStartBreak

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )

                    // Google Account Card
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.large,
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = profile.displayName.firstOrNull()?.uppercase() ?: "F",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (!userEmail.isNullOrBlank()) {
                                        Text(
                                            text = userEmail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CloudDone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_cloud_synced),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }

                                OutlinedButton(
                                    onClick = onSignOut,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("sign_out_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.settings_sign_out))
                                }
                            }
                        }
                    }

                    // Timer Duration Settings Card
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.large,
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_timer_section),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            DurationStepperRow(
                                label = stringResource(R.string.settings_focus_duration),
                                valueMinutes = focusMinutes,
                                onDecrease = { focusMinutes = (focusMinutes - 5).coerceAtLeast(5) },
                                onIncrease = { focusMinutes = (focusMinutes + 5).coerceAtMost(90) },
                                canDecrease = focusMinutes > 5,
                                canIncrease = focusMinutes < 90,
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            DurationStepperRow(
                                label = stringResource(R.string.settings_short_break_duration),
                                valueMinutes = shortBreakMinutes,
                                onDecrease = { shortBreakMinutes = (shortBreakMinutes - 1).coerceAtLeast(1) },
                                onIncrease = { shortBreakMinutes = (shortBreakMinutes + 1).coerceAtMost(30) },
                                canDecrease = shortBreakMinutes > 1,
                                canIncrease = shortBreakMinutes < 30,
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            DurationStepperRow(
                                label = stringResource(R.string.settings_long_break_duration),
                                valueMinutes = longBreakMinutes,
                                onDecrease = { longBreakMinutes = (longBreakMinutes - 5).coerceAtLeast(5) },
                                onIncrease = { longBreakMinutes = (longBreakMinutes + 5).coerceAtMost(45) },
                                canDecrease = longBreakMinutes > 5,
                                canIncrease = longBreakMinutes < 45,
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            DurationStepperRow(
                                label = stringResource(R.string.settings_daily_goal),
                                valueMinutes = dailyGoalMinutes,
                                onDecrease = { dailyGoalMinutes = (dailyGoalMinutes - 15).coerceAtLeast(30) },
                                onIncrease = { dailyGoalMinutes = (dailyGoalMinutes + 15).coerceAtMost(480) },
                                canDecrease = dailyGoalMinutes > 30,
                                canIncrease = dailyGoalMinutes < 480,
                            )
                        }
                    }

                    // Behavior Preferences Card
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.large,
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_preferences_section),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            PreferenceSwitchRow(
                                title = stringResource(R.string.settings_haptics_title),
                                description = stringResource(R.string.settings_haptics_desc),
                                checked = hapticsEnabled,
                                onCheckedChange = { hapticsEnabled = it },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            PreferenceSwitchRow(
                                title = stringResource(R.string.settings_auto_break_title),
                                description = stringResource(R.string.settings_auto_break_desc),
                                checked = autoStartBreak,
                                onCheckedChange = { autoStartBreak = it },
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onSaveSettings(
                                profile.displayName,
                                focusMinutes,
                                shortBreakMinutes,
                                longBreakMinutes,
                                dailyGoalMinutes,
                                hapticsEnabled,
                                autoStartBreak,
                            )
                        },
                        enabled = hasUnsavedChanges,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("save_settings_button"),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_save_button),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DurationStepperRow(
    label: String,
    valueMinutes: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    canDecrease: Boolean,
    canIncrease: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledTonalIconButton(
                onClick = onDecrease,
                enabled = canDecrease,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = stringResource(R.string.common_decrease_cd),
                )
            }
            Text(
                text = stringResource(R.string.stats_minutes_fmt, valueMinutes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 52.dp),
            )
            FilledTonalIconButton(
                onClick = onIncrease,
                enabled = canIncrease,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.common_increase_cd),
                )
            }
        }
    }
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
