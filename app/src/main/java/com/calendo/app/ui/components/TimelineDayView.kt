package com.calendo.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.calendo.app.data.CalendarItem
import com.calendo.app.ui.theme.TimelineGrid
import com.calendo.app.ui.theme.colorsForPaletteIndex
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun TimelineDayView(
    items: List<CalendarItem>,
    startHour: Int,
    endHour: Int,
    hourHeight: Dp,
    onBackgroundHourClick: (LocalTime) -> Unit,
    onEventClick: (CalendarItem) -> Unit,
    onTodoToggle: (CalendarItem) -> Unit,
    onEventDragEnd: (CalendarItem, deltaMinutes: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val minutesTotal = (endHour - startHour) * 60
    val totalHeight = hourHeight * (endHour - startHour)
    val pxPerMinute = with(density) { hourHeight.toPx() } / 60f

    var dragState by remember { mutableStateOf<Pair<String, Float>?>(null) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier.width(40.dp),
        ) {
            for (hour in startHour until endHour) {
                Box(
                    modifier = Modifier
                        .height(hourHeight)
                        .fillMaxWidth()
                        .padding(end = 6.dp, top = 2.dp),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    Text(
                        text = "%02d:00".format(hour),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(totalHeight),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    for (hour in startHour until endHour) {
                        val slotInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .height(hourHeight)
                                .clickable(
                                    interactionSource = slotInteraction,
                                    indication = null,
                                ) {
                                    onBackgroundHourClick(LocalTime.of(hour, 0))
                                },
                        ) {
                            HorizontalDivider(
                                color = TimelineGrid,
                                thickness = 1.dp,
                                modifier = Modifier.align(Alignment.TopStart),
                            )
                            Text(
                                text = "轻点添加",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 8.dp),
                            )
                        }
                    }
                }

                items.forEach { item ->
                    val layout = layoutEvent(item, startHour, endHour, minutesTotal) ?: return@forEach
                    val topPx = layout.startMinuteOffset * pxPerMinute
                    val heightPx = max(layout.durationMinutes, 15) * pxPerMinute
                    val topDp = with(density) { topPx.toDp() }
                    val heightDp = with(density) { heightPx.toDp() }
                    val h = if (heightDp < 48.dp) 48.dp else heightDp

                    val dragging = dragState?.first == item.id
                    val dragDy = if (dragging) dragState!!.second else 0f

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .fillMaxWidth()
                            .padding(top = topDp)
                            .height(h)
                            .zIndex(if (dragging) 24f else 1f)
                            .graphicsLayer {
                                translationY = dragDy
                                scaleX = if (dragging) 1.03f else 1f
                                scaleY = if (dragging) 1.03f else 1f
                            }
                            .pointerInput(item.id, pxPerMinute) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        dragState = item.id to 0f
                                    },
                                    onDrag = { _, dragAmount ->
                                        val cur = dragState
                                        if (cur?.first == item.id) {
                                            dragState = item.id to (cur.second + dragAmount.y)
                                        }
                                    },
                                    onDragCancel = { dragState = null },
                                    onDragEnd = {
                                        val cur = dragState
                                        dragState = null
                                        if (cur?.first == item.id) {
                                            val dmRaw = (cur.second / pxPerMinute).roundToInt()
                                            val dm = (dmRaw / 15f).roundToInt() * 15
                                            if (dm != 0) {
                                                onEventDragEnd(item, dm)
                                            }
                                        }
                                    },
                                )
                            },
                    ) {
                        EventCard(
                            item = item,
                            onClick = { onEventClick(item) },
                            onTodoToggle = { onTodoToggle(item) },
                            elevated = dragging,
                        )
                    }
                }
            }
        }
    }
}

private data class EventLayout(
    val startMinuteOffset: Float,
    val durationMinutes: Int,
)

private fun layoutEvent(
    item: CalendarItem,
    startHour: Int,
    endHour: Int,
    minutesTotal: Int,
): EventLayout? {
    val dayStart = LocalTime.of(startHour, 0)
    if (!item.end.isAfter(item.start)) return null

    val visibleStart = max(
        ChronoUnit.MINUTES.between(dayStart, item.start).toFloat(),
        0f,
    )
    val rawEnd = ChronoUnit.MINUTES.between(dayStart, item.end).toFloat()
    val visibleEnd = min(rawEnd, minutesTotal.toFloat())
    if (visibleEnd <= visibleStart) return null

    return EventLayout(
        startMinuteOffset = visibleStart,
        durationMinutes = (visibleEnd - visibleStart).roundToInt(),
    )
}

@Composable
private fun EventCard(
    item: CalendarItem,
    onClick: () -> Unit,
    onTodoToggle: () -> Unit,
    elevated: Boolean,
) {
    val shape = RoundedCornerShape(12.dp)
    val strike = item.isTodo && item.completed
    val block = colorsForPaletteIndex(item.paletteIndex)

    Surface(
        shape = shape,
        color = block.fill,
        tonalElevation = if (elevated) 6.dp else 0.dp,
        shadowElevation = if (elevated) 10.dp else 1.dp,
        border = BorderStroke(1.dp, block.border),
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (strike) TextDecoration.LineThrough else TextDecoration.None,
                    color = when {
                        strike -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        else -> block.onBlock
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick,
                        ),
                )
                if (item.priority != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.06f),
                    ) {
                        Text(
                            text = item.priority,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = block.onBlock,
                        )
                    }
                }
                if (item.isTodo) {
                    Checkbox(
                        checked = item.completed,
                        onCheckedChange = { onTodoToggle() },
                    )
                }
            }

            Text(
                text = "${item.start.format(TimeFormatter)} – ${item.end.format(TimeFormatter)}",
                style = MaterialTheme.typography.labelSmall,
                color = block.onBlock.copy(alpha = 0.85f),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            )

            if (item.participants.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Group,
                        contentDescription = null,
                        tint = block.onBlock.copy(alpha = 0.75f),
                        modifier = Modifier.height(16.dp),
                    )
                    Text(
                        text = item.participants.joinToString("，"),
                        style = MaterialTheme.typography.labelSmall,
                        color = block.onBlock.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
