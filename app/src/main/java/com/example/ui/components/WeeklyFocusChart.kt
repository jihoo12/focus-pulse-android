package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.viewmodel.DailyChartPoint
import kotlin.math.max

@Composable
fun WeeklyFocusChart(
    points: List<DailyChartPoint>,
    dailyGoalMinutes: Int,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return

    var selectedIndex by remember(points.size) {
        mutableIntStateOf((points.size - 1).coerceAtLeast(0))
    }
    val activePoint = points.getOrNull(selectedIndex) ?: points.last()

    val primary = MaterialTheme.colorScheme.primary
    val secondaryTrack = MaterialTheme.colorScheme.surfaceContainerHigh
    val goalLineColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)

    val maxMinutes = max(
        dailyGoalMinutes,
        points.maxOfOrNull { it.focusMinutes } ?: 60,
    ).coerceAtLeast(30)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.stats_weekly_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${activePoint.shortDateLabel} · ${stringResource(R.string.stats_minutes_fmt, activePoint.focusMinutes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.stats_weekly_goal_label, dailyGoalMinutes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(144.dp),
        ) {
            val chartHeight = size.height - 8.dp.toPx()
            val slotWidth = size.width / points.size.toFloat()
            val barWidth = (slotWidth * 0.46f).coerceAtMost(28.dp.toPx())
            val cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())

            // Dashed daily goal line
            val goalRatio = (dailyGoalMinutes.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
            val goalY = chartHeight * (1f - goalRatio)
            drawLine(
                color = goalLineColor,
                start = Offset(0f, goalY),
                end = Offset(size.width, goalY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
            )

            points.forEachIndexed { index, point ->
                val xCenter = slotWidth * index + slotWidth / 2f
                val left = xCenter - barWidth / 2f

                // Subtle background slot bar
                drawRoundRect(
                    color = secondaryTrack.copy(alpha = 0.6f),
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, chartHeight),
                    cornerRadius = cornerRadius,
                )

                val valueRatio = (point.focusMinutes.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
                val barHeight = (chartHeight * valueRatio).let { h ->
                    if (point.focusMinutes > 0) h.coerceAtLeast(8.dp.toPx()) else 0f
                }

                if (barHeight > 0f) {
                    val barColor = when {
                        index == selectedIndex -> primary
                        point.isToday -> primary.copy(alpha = 0.78f)
                        else -> primary.copy(alpha = 0.42f)
                    }
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(left, chartHeight - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = cornerRadius,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            points.forEachIndexed { index, point ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                        )
                        .clickable(role = Role.Button) { selectedIndex = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = point.shortDayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (isSelected || point.isToday) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
