package com.calendo.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Clock layout: 14:00 (2 PM) is anchored at TOP, so 8:00 is at LEFT and 20:00 is at RIGHT.
private const val ANCHOR_MIN = 14 * 60      // minutes: top of circle = 2 PM
private const val DAY_MINS = 24 * 60        // 1440 minutes in a day

// Warm amber for daytime arc, deep navy for nighttime arc
private val DAY_ARC_COLOR = Color(0xFFFFD54F)    // amber 300
private val NIGHT_ARC_COLOR = Color(0xFF283593)   // indigo 800
private val DAY_TEXT_COLOR = Color(0xFFFF8F00)    // amber 800
private val NIGHT_TEXT_COLOR = Color(0xFF90CAF9)  // blue 200
private val DIVIDER_COLOR = Color(0xFFB0BEC5)     // blueGrey 200

private fun snap15(m: Float): Float {
    val snapped = ((m / 15f).roundToInt() * 15) % DAY_MINS
    return (if (snapped < 0) snapped + DAY_MINS else snapped).toFloat()
}

// minute-of-day → draw angle in degrees (screen convention: 0°=right, CW=positive)
private fun minToDeg(m: Float): Float {
    val offset = ((m - ANCHOR_MIN + DAY_MINS) % DAY_MINS).toFloat()
    return offset / DAY_MINS * 360f - 90f
}

// atan2 draw angle → minute-of-day (raw, not snapped)
private fun degToMin(deg: Float): Float {
    val θ = ((deg + 90f) % 360f + 360f) % 360f
    return ((θ / 360f * DAY_MINS) + ANCHOR_MIN) % DAY_MINS
}

private fun minToLabel(m: Float): String {
    val t = m.roundToInt().let { (it % DAY_MINS + DAY_MINS) % DAY_MINS }
    return "%02d:%02d".format(t / 60, t % 60)
}

/**
 * 24-hour clock-dial time range picker.
 *
 * Layout (fixed, matching the sketch):
 *   · 8:00  at LEFT  (9 o'clock position)
 *   · 14:00 at TOP   (12 o'clock position)
 *   · 20:00 at RIGHT (3 o'clock position)
 *   · 2:00  at BOTTOM (6 o'clock position)
 *   · Top half = 日 (daytime, warm amber track)
 *   · Bottom half = 夜 (nighttime, deep navy track)
 *
 * Two thumb handles are dragged around the ring to set start/end times (15-min snap).
 */
@Composable
fun ScheduleTimeRangeSlider(
    startMinuteOfDay: Int,
    endMinuteOfDay: Int,
    onRangeChange: (startMinuteOfDay: Int, endMinuteOfDay: Int) -> Unit,
    dayStartHour: Int,
    dayEndHour: Int,
    modifier: Modifier = Modifier,
) {
    var startMin by remember(startMinuteOfDay) { mutableStateOf(snap15(startMinuteOfDay.toFloat())) }
    var endMin by remember(endMinuteOfDay) { mutableStateOf(snap15(endMinuteOfDay.toFloat())) }
    var dragging by remember { mutableIntStateOf(0) } // 0=none, 1=start, 2=end

    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .aspectRatio(1f)
            .pointerInput(Unit) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = min(size.width, size.height) / 2f * 0.70f

                fun pos(min: Float): Offset {
                    val rad = Math.toRadians(minToDeg(min).toDouble())
                    return Offset(cx + (r * cos(rad)).toFloat(), cy + (r * sin(rad)).toFloat())
                }

                detectDragGestures(
                    onDragStart = { offset ->
                        dragging = if ((offset - pos(startMin)).getDistance() <=
                            (offset - pos(endMin)).getDistance()) 1 else 2
                    },
                    onDragEnd = { dragging = 0 },
                    onDragCancel = { dragging = 0 },
                    onDrag = { change, _ ->
                        val dx = change.position.x - cx
                        val dy = change.position.y - cy
                        val m = snap15(degToMin(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()))
                        val minGapDeg = 15f / DAY_MINS * 360f
                        if (dragging == 1) {
                            if (((minToDeg(endMin) - minToDeg(m) + 360f) % 360f) >= minGapDeg) startMin = m
                        } else {
                            if (((minToDeg(m) - minToDeg(startMin) + 360f) % 360f) >= minGapDeg) endMin = m
                        }
                        onRangeChange(startMin.roundToInt(), endMin.roundToInt())
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension * 0.35f
            val trackW = size.minDimension * 0.13f
            val thumbR = size.minDimension * 0.062f
            val innerR = r - trackW / 2f
            val tl = Offset(cx - r, cy - r)
            val sz = Size(r * 2f, r * 2f)

            // Fill inner circle with surface color (creates the "hole" in the ring)
            drawCircle(surface, innerR - 1f, Offset(cx, cy))

            // Night arc — bottom half: from LEFT(180°) clockwise 180° to RIGHT(0°)
            drawArc(
                color = NIGHT_ARC_COLOR,
                startAngle = 180f, sweepAngle = 180f,
                useCenter = false, topLeft = tl, size = sz,
                style = Stroke(width = trackW),
            )

            // Day arc — top half: from LEFT(180°) counterclockwise -180° to RIGHT(0°)
            drawArc(
                color = DAY_ARC_COLOR,
                startAngle = 180f, sweepAngle = -180f,
                useCenter = false, topLeft = tl, size = sz,
                style = Stroke(width = trackW),
            )

            // Horizontal divider line (8:00–20:00 equator)
            drawLine(
                color = DIVIDER_COLOR.copy(alpha = 0.5f),
                start = Offset(cx - r - trackW / 2f, cy),
                end = Offset(cx + r + trackW / 2f, cy),
                strokeWidth = 1.5f,
            )

            // Hour tick marks (every 3 hours; 6-hour marks are larger)
            for (h in 0 until 24 step 3) {
                val angRad = Math.toRadians(minToDeg(h * 60f).toDouble())
                val isMajor = h % 6 == 0
                val tickInner = innerR + trackW * 0.1f
                val tickOuter = innerR + trackW * 0.9f
                drawLine(
                    color = Color.Black.copy(alpha = if (isMajor) 0.22f else 0.10f),
                    start = Offset((cx + tickInner * cos(angRad)).toFloat(), (cy + tickInner * sin(angRad)).toFloat()),
                    end = Offset((cx + tickOuter * cos(angRad)).toFloat(), (cy + tickOuter * sin(angRad)).toFloat()),
                    strokeWidth = if (isMajor) 2.5f else 1.5f,
                )
            }

            // Selected time arc (primary color, slightly thinner, on top)
            val startDeg = minToDeg(startMin)
            val sweep = ((minToDeg(endMin) - startDeg + 360f) % 360f).coerceAtLeast(1f)
            drawArc(
                color = primary,
                startAngle = startDeg, sweepAngle = sweep,
                useCenter = false, topLeft = tl, size = sz,
                style = Stroke(width = trackW * 0.58f, cap = StrokeCap.Round),
            )

            // Thumb handles
            fun drawThumb(min: Float) {
                val ang = Math.toRadians(minToDeg(min).toDouble())
                val tc = Offset((cx + r * cos(ang)).toFloat(), (cy + r * sin(ang)).toFloat())
                // Outer glow ring
                drawCircle(primary.copy(alpha = 0.20f), thumbR + 7f, tc)
                // Thumb body
                drawCircle(surface, thumbR + 3f, tc)   // white border gap
                drawCircle(primary, thumbR, tc)
                drawCircle(Color.White, thumbR * 0.42f, tc)
            }
            drawThumb(startMin)
            drawThumb(endMin)

            // Fixed clock labels
            val timeLabelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = onSurface)
            val sectionStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)

            // "8:00" at LEFT
            val t8 = textMeasurer.measure("8:00", timeLabelStyle)
            drawText(t8, topLeft = Offset(cx - r - trackW / 2f - t8.size.width - 10f, cy - t8.size.height / 2f))

            // "20:00" at RIGHT
            val t20 = textMeasurer.measure("20:00", timeLabelStyle)
            drawText(t20, topLeft = Offset(cx + r + trackW / 2f + 10f, cy - t20.size.height / 2f))

            // "日" in upper interior
            val tDay = textMeasurer.measure("日", sectionStyle.copy(color = DAY_TEXT_COLOR))
            drawText(tDay, topLeft = Offset(cx - tDay.size.width / 2f, cy - r * 0.58f - tDay.size.height / 2f))

            // "夜" in lower interior
            val tNight = textMeasurer.measure("夜", sectionStyle.copy(color = NIGHT_TEXT_COLOR))
            drawText(tNight, topLeft = Offset(cx - tNight.size.width / 2f, cy + r * 0.58f - tNight.size.height / 2f))
        }

        // Center: current time range display
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = minToLabel(startMin),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primary,
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = "→",
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = minToLabel(endMin),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primary,
            )
        }
    }
}
