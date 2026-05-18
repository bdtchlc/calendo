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

/**
 * 始终返回满足 start < endInclusive（最小间距 15 分钟）且在 [rangeMin, rangeMax] 内的合法区间。
 * 防止传给 RangeSlider 的 value 出现 start >= endInclusive 导致负宽度崩溃。
 */
private fun safeRange(
    rawS: Float,
    rawE: Float,
    rangeMin: Float,
    rangeMax: Float,
): ClosedFloatingPointRange<Float> {
    var s = rawS.coerceIn(rangeMin, rangeMax - 15f)
    var e = rawE.coerceIn(rangeMin + 15f, rangeMax)
    e = e.coerceAtLeast(s + 15f)
    if (e > rangeMax) {
        e = rangeMax
        s = (rangeMax - 15f).coerceAtLeast(rangeMin)
    }
    return s..e
}

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
        mutableStateOf(safeRange(startMinuteOfDay.toFloat(), endMinuteOfDay.toFloat(), rangeMin, rangeMax))
    }

    LaunchedEffect(startMinuteOfDay, endMinuteOfDay) {
        range = safeRange(startMinuteOfDay.toFloat(), endMinuteOfDay.toFloat(), rangeMin, rangeMax)
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
                // 防止两端交叉：取小值作为起始、大值作为结束
                val lo = minOf(new.start, new.endInclusive)
                val hi = maxOf(new.start, new.endInclusive)
                val snappedS = snap15(lo).toFloat().coerceIn(rangeMin, rangeMax - 15f)
                val snappedE = snap15(hi).toFloat().coerceIn(rangeMin + 15f, rangeMax)
                val next = safeRange(snappedS, snappedE, rangeMin, rangeMax)
                range = next
                onRangeChange(
                    snap15(next.start).coerceIn(rangeMin.toInt(), rangeMax.toInt()),
                    snap15(next.endInclusive).coerceIn(rangeMin.toInt(), rangeMax.toInt()),
                )
            },
            valueRange = rangeMin..rangeMax,
            steps = stepsCount,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
