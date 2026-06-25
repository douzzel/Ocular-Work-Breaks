package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun EyeExerciseAnimation(
    step: EyeStep,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "eye_movement")

    // Rotation angle for ROLL_EYES
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    // Ping-pong horizontal movement for TRACE_HORIZONTAL
    val horizontalSweep by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweep"
    )

    // Pulse wave for LOOK_FAR
    val lookFarPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    // Blink wave for BLINK_RELAX
    val blinkScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3000
                1.0f at 0
                1.0f at 2200
                0.05f at 2400
                1.0f at 2600
                0.05f at 2700
                1.0f at 2900
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )

    Canvas(modifier = modifier.size(160.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val outerRadius = 70.dp.toPx()
        val eyeWidth = 120.dp.toPx()
        val eyeHeight = 70.dp.toPx()

        when (step) {
            EyeStep.LOOK_FAR -> {
                // Drawing 20/20/20 Focal Shift: Near eye looking at a far pulse indicator
                val startX = center.x - 50.dp.toPx()
                val targetX = center.x + 50.dp.toPx()

                // Draw Near Eye (Left-side static eye representation)
                val leftEyeCenter = Offset(startX, center.y)
                drawCircle(
                    color = Color(0xFF6750A4).copy(alpha = 0.1f),
                    radius = 24.dp.toPx(),
                    center = leftEyeCenter
                )
                drawCircle(
                    color = Color(0xFF6750A4),
                    radius = 20.dp.toPx(),
                    center = leftEyeCenter,
                    style = Stroke(width = 3.dp.toPx())
                )
                // Iris/Pupil looking right
                drawCircle(
                    color = Color(0xFF21005D),
                    radius = 9.dp.toPx(),
                    center = Offset(startX + 6.dp.toPx(), center.y)
                )

                // Dotted path representing distance
                val path = Path().apply {
                    moveTo(startX + 24.dp.toPx(), center.y)
                    lineTo(targetX - 25.dp.toPx(), center.y)
                }
                drawPath(
                    path = path,
                    color = Color(0xFF6750A4).copy(alpha = 0.4f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(10f, 10f), 0f
                        )
                    )
                )

                // Draw pulses emitting outwards representing the 6 meters (20 feet) focus point
                val currentPulseRadius = 6.dp.toPx() + (30.dp.toPx() * lookFarPulse)
                val pulseColor = Color(0xFF6750A4).copy(alpha = 1.0f - lookFarPulse)
                
                val rightCircleCenter = Offset(targetX, center.y)
                drawCircle(
                    brush = SolidColor(pulseColor),
                    radius = currentPulseRadius,
                    center = rightCircleCenter,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Draw Far Target Object (a tree outline or star representing distance)
                drawCircle(
                    color = Color(0xFF21005D),
                    radius = 12.dp.toPx(),
                    center = rightCircleCenter
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = rightCircleCenter
                )
            }

            EyeStep.ROLL_EYES -> {
                // Drawing eye roll: Eye silhouette outline + pupil moving in circular rotation
                drawEyeBaseShape(eyeWidth, eyeHeight, center)

                // Pupil rotation offset calculation
                val pupilRadius = 15.dp.toPx()
                val moveRadius = 18.dp.toPx()
                val offsetX = moveRadius * cos(rotationAngle)
                val offsetY = moveRadius * sin(rotationAngle)
                val pupilCenter = Offset(center.x + offsetX, center.y + offsetY)

                // Iris (Outer colored pupil)
                drawCircle(
                    color = Color(0xFF6750A4),
                    radius = 24.dp.toPx(),
                    center = pupilCenter
                )
                // Core black Pupil
                drawCircle(
                    color = Color(0xFF21005D),
                    radius = pupilRadius,
                    center = pupilCenter
                )
                // High-fidelity glare spot
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = Offset(pupilCenter.x - 6.dp.toPx(), pupilCenter.y - 6.dp.toPx())
                )
            }

            EyeStep.TRACE_HORIZONTAL -> {
                // Eye tracking horizontal: Sweep side to side
                drawEyeBaseShape(eyeWidth, eyeHeight, center)

                // Pupil offset
                val pupilCenter = Offset(center.x + horizontalSweep.dp.toPx(), center.y)

                // Iris
                drawCircle(
                    color = Color(0xFF6750A4),
                    radius = 24.dp.toPx(),
                    center = pupilCenter
                )
                // Core pupil
                drawCircle(
                    color = Color(0xFF21005D),
                    radius = 15.dp.toPx(),
                    center = pupilCenter
                )
                // Glare spot
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = Offset(pupilCenter.x - 6.dp.toPx(), pupilCenter.y - 6.dp.toPx())
                )
            }

            EyeStep.BLINK_RELAX -> {
                if (blinkScale < 0.2f) {
                    // Closed eye: represent using curved bottom eye fold curve + cute eyelashes lines
                    val path = Path().apply {
                        val start = center.x - eyeWidth / 2
                        val end = center.x + eyeWidth / 2
                        moveTo(start, center.y)
                        quadraticBezierTo(center.x, center.y + 15.dp.toPx(), end, center.y)
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF6750A4),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Eyelashes
                    drawLine(
                        color = Color(0xFF6750A4),
                        start = Offset(center.x - 30.dp.toPx(), center.y + 7.dp.toPx()),
                        end = Offset(center.x - 35.dp.toPx(), center.y + 18.dp.toPx()),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFF6750A4),
                        start = Offset(center.x, center.y + 10.dp.toPx()),
                        end = Offset(center.x, center.y + 22.dp.toPx()),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFF6750A4),
                        start = Offset(center.x + 30.dp.toPx(), center.y + 7.dp.toPx()),
                        end = Offset(center.x + 35.dp.toPx(), center.y + 18.dp.toPx()),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                } else {
                    // Open with animated lid scale (for regular blinks)
                    val activeHeight = eyeHeight * blinkScale
                    
                    // Draw eye contour with blink scale
                    val path = Path().apply {
                        val start = center.x - eyeWidth / 2
                        val end = center.x + eyeWidth / 2
                        moveTo(start, center.y)
                        quadraticTo(center.x, center.y - activeHeight, end, center.y)
                        quadraticTo(center.x, center.y + activeHeight, start, center.y)
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF6750A4).copy(alpha = 0.15f)
                    )
                    drawPath(
                        path = path,
                        color = Color(0xFF6750A4),
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Pupil (scaling height according to lid scale)
                    val pupilCenter = center
                    val activeIrisHeight = 24.dp.toPx() * blinkScale
                    val activePupilHeight = 15.dp.toPx() * blinkScale
                    
                    // Oval Iris
                    drawOvalCustom(
                        color = Color(0xFF6750A4),
                        topLeft = Offset(pupilCenter.x - 24.dp.toPx(), pupilCenter.y - activeIrisHeight),
                        size = androidx.compose.ui.geometry.Size(48.dp.toPx(), activeIrisHeight * 2)
                    )
                    // Oval Pupil
                    drawOvalCustom(
                        color = Color(0xFF21005D),
                        topLeft = Offset(pupilCenter.x - 15.dp.toPx(), pupilCenter.y - activePupilHeight),
                        size = androidx.compose.ui.geometry.Size(30.dp.toPx(), activePupilHeight * 2)
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEyeBaseShape(
    width: Float,
    height: Float,
    center: Offset
) {
    val path = Path().apply {
        val start = center.x - width / 2
        val end = center.x + width / 2
        moveTo(start, center.y)
        quadraticTo(center.x, center.y - height, end, center.y)
        quadraticTo(center.x, center.y + height, start, center.y)
    }

    // Inside Eye Ball tint
    drawPath(
        path = path,
        color = Color(0xFFEADDFF).copy(alpha = 0.3f)
    )

    // Eye Outline
    drawPath(
        path = path,
        color = Color(0xFF6750A4),
        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOvalCustom(
    color: Color,
    topLeft: Offset,
    size: androidx.compose.ui.geometry.Size
) {
    drawOval(
        color = color,
        topLeft = topLeft,
        size = size
    )
}
