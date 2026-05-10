package com.calendo.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import kotlin.math.roundToInt

private fun localTimeFromMinuteOfDay(m: Int): LocalTime {
    val h = (m / 60).coerceIn(0, 23)
    val min = (m % 60).coerceIn(0, 59)
    return LocalTime.of(h, min)
}

private fun snap15(m: Float): Int {
    val r = (m / 15f).roundToInt() * 15
    return r.coerceIn(0, 23 * 60 + 59)
}

/**
 * 通过拖动双滑块设置开始/结束时间（15 分钟粒度），替代手动输入 HH:mm。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleTimeRangeSlider(
    startMinuteOfDay: Int,
    endMinuteOfDay: Int,
    onRangeChange: (startMinuteOfDay: Int, endMinuteOfDay: Int) -> Unit,
    dayStartHour: Int,
    dayEndHour: Int,
    modifier: Modifier = Modifier,
) {
    val rangeMin = (dayStartHour * 60).toFloat()
    val rangeMax = (dayEndHour * 60 + 45).toFloat()
    val span = rangeMax - rangeMin
    val stepsCount = ((span / 15f).toInt() - 1).coerceAtLeast(0)

    var range by remember(startMinuteOfDay, endMinuteOfDay, dayStartHour, dayEndHour) {
        val s = startMinuteOfDay.toFloat().coerceIn(rangeMin, rangeMax)
        val e = endMinuteOfDay.toFloat().coerceIn(rangeMin, rangeMax)
        val spanSafe = if (e > s + 14f) e else (s + 60f).coerceAtMost(rangeMax)
        mutableStateOf(s..spanSafe)
    }

    LaunchedEffect(startMinuteOfDay, endMinuteOfDay) {
        val s = startMinuteOfDay.toFloat().coerceIn(rangeMin, rangeMax)
        val e = endMinuteOfDay.toFloat().coerceIn(rangeMin, rangeMax)
        if (e > s + 14f) {
            range = s..e
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "时间（拖动两端设置）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${localTimeFromMinuteOfDay(snap15(range.start))} – ${localTimeFromMinuteOfDay(snap15(range.endInclusive))}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        RangeSlider(
            value = range,
            onValueChange = { new ->
                val rawStart = snap15(new.start).toFloat().coerceIn(rangeMin, rangeMax - 15f)
                val rawEnd = snap15(new.endInclusive).toFloat().coerceIn(rangeMin, rangeMax)
                val minDur = 15f
                var s = rawStart
                var e = rawEnd.coerceAtLeast(s + minDur)
                if (e > rangeMax) {
                    e = rangeMax
                    s = (e - minDur).coerceAtLeast(rangeMin)
                }
                if (s > e - minDur) s = e - minDur
                range = s..e
                onRangeChange(snap15(s).coerceAtLeast(rangeMin.toInt()), snap15(e).coerceAtMost(rangeMax.toInt()))
            },
            valueRange = rangeMin..rangeMax,
            steps = stepsCount,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
