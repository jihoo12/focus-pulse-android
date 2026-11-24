package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.SessionType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ChronometerDial(
    formattedTime: String,
    progress: Float,
    mode: SessionType,
    isRunning: Boolean,
    hasStarted: Boolean,
    completedCyclesInSet: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "dial_progress",
    )
    val animatedAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dial_accent",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val tickBaseColor = MaterialTheme.colorScheme.onSurfaceVariant
    val innerBg = MaterialTheme.colorScheme.surfaceContainerLowest

    val statusText = when {
        isRunning && mode == SessionType.FOCUS -> stringResource(R.string.timer_status_running)
        isRunning -> stringResource(R.string.timer_status_break_running)
        hasStarted -> stringResource(R.string.timer_status_paused)
        else -> stringResource(R.string.timer_status_ready)
    }

    Box(
        modifier = modifier
            .widthIn(max = 310.dp)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minDim = size.minDimension
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = minDim / 2f

            // Atmospheric radial pulse glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedAccent.copy(alpha = if (isRunning) 0.16f else 0.08f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = outerRadius,
                ),
                radius = outerRadius,
                center = center,
            )

            // 60 Swiss precision radial ticks
            val tickOuter = outerRadius - 4.dp.toPx()
            for (i in 0 until 60) {
                val angleDeg = i * 6.0 - 90.0
                val angleRad = angleDeg * PI / 180.0
                val isMajor = i % 5 == 0
                val tickLen = if (isMajor) 11.dp.toPx() else 5.dp.toPx()
                val tickProgress = i / 60f
                val isLit = tickProgress <= animatedProgress && animatedProgress > 0f

                val start = Offset(
                    x = center.x + (tickOuter - tickLen) * cos(angleRad).toFloat(),
                    y = center.y + (tickOuter - tickLen) * sin(angleRad).toFloat(),
                )
                val end = Offset(
                    x = center.x + tickOuter * cos(angleRad).toFloat(),
                    y = center.y + tickOuter * sin(angleRad).toFloat(),
                )
                drawLine(
                    color = when {
                        isLit -> animatedAccent.copy(alpha = if (isMajor) 0.95f else 0.55f)
                        isMajor -> tickBaseColor.copy(alpha = 0.42f)
                        else -> tickBaseColor.copy(alpha = 0.18f)
                    },
                    start = start,
                    end = end,
                    strokeWidth = if (isMajor) 2.2.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // Inner dial face
            val ringInset = 26.dp.toPx()
            val strokeWidth = 11.dp.toPx()
            val innerRadius = outerRadius - ringInset - strokeWidth / 2f
            drawCircle(
                color = innerBg,
                radius = innerRadius,
                center = center,
            )

            // Base circular track
            val arcTopLeft = Offset(
                x = center.x - (outerRadius - ringInset),
                y = center.y - (outerRadius - ringInset),
            )
            val arcDiameter = (outerRadius - ringInset) * 2f
            val arcSize = Size(arcDiameter, arcDiameter)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Active progress sweep
            val sweep = animatedProgress * 360f
            if (sweep > 0.5f) {
                drawArc(
                    color = animatedAccent,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )

                // Luminous indicator pip at the tip of the sweep
                val tipRad = (sweep - 90f) * PI / 180.0
                val ringRadius = outerRadius - ringInset
                val tipOffset = Offset(
                    x = center.x + ringRadius * cos(tipRad).toFloat(),
                    y = center.y + ringRadius * sin(tipRad).toFloat(),
                )
                drawCircle(
                    color = innerBg,
                    radius = 4.dp.toPx(),
                    center = tipOffset,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 36.dp),
        ) {
            Text(
                text = stringResource(mode.labelRes).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = animatedAccent,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(14.dp))

            // 4-Pomodoro cycle indicator pips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (index in 0 until 4) {
                    val isFilled = index < completedCyclesInSet
                    Box(
                        modifier = Modifier
                            .size(if (isFilled) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) animatedAccent
                                else MaterialTheme.colorScheme.outlineVariant,
                            ),
                    )
                }
            }
        }
    }
}
