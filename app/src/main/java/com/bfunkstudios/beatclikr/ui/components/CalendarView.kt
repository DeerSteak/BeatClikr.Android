package com.bfunkstudios.beatclikr.ui.components

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.bfunkstudios.beatclikr.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val DAY_LABELS = listOf("S", "M", "T", "W", "T", "F", "S")

@Composable
fun CalendarView(
    markedDates: Set<Long>,
    selectedDate: Long?,
    onDateSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var displayedMonth by remember { mutableLongStateOf(startOfMonth(System.currentTimeMillis())) }
    val today = startOfDay(System.currentTimeMillis())
    val daysGrid = buildDaysGrid(displayedMonth)
    val weeks = daysGrid.chunked(7)
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }

    Card(
        modifier = modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {

            // Month navigation header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    displayedMonth = addMonths(displayedMonth, -1)
                    onDateSelected(null)
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.previous_month),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = monthFormat.format(displayedMonth),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    displayedMonth = addMonths(displayedMonth, 1)
                    onDateSelected(null)
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.next_month),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Day-of-week header
            Row(modifier = Modifier.fillMaxWidth()) {
                DAY_LABELS.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = 4.dp)
                    )
                }
            }

            // Day grid
            weeks.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { dayMs ->
                        if (dayMs != null) {
                            CalendarDayCell(
                                dayMs = dayMs,
                                isMarked = markedDates.contains(dayMs),
                                isToday = dayMs == today,
                                isSelected = selectedDate == dayMs,
                                onClick = { onDateSelected(dayMs) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f).height(36.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    dayMs: Long,
    isMarked: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val circleColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val dotColor = if (isMarked) MaterialTheme.colorScheme.primary else Color.Transparent

    val dayNumber = remember(dayMs) {
        val cal = Calendar.getInstance().apply { timeInMillis = dayMs }
        cal.get(Calendar.DAY_OF_MONTH).toString()
    }

    Column(
        modifier = modifier
            .height(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(circleColor)
            )
            Text(
                text = dayNumber,
                fontSize = 14.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
        }
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
    }
}

private fun startOfDay(epochMs: Long): Long = Calendar.getInstance().run {
    timeInMillis = epochMs
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    timeInMillis
}

private fun startOfMonth(epochMs: Long): Long = Calendar.getInstance().run {
    timeInMillis = epochMs
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    timeInMillis
}

private fun addMonths(epochMs: Long, months: Int): Long = Calendar.getInstance().run {
    timeInMillis = epochMs
    add(Calendar.MONTH, months)
    timeInMillis
}

private fun buildDaysGrid(firstOfMonthMs: Long): List<Long?> {
    val cal = Calendar.getInstance().apply { timeInMillis = firstOfMonthMs }
    val leadingBlanks = cal.get(Calendar.DAY_OF_WEEK) - 1  // Sunday=1 → 0 blanks
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val days = mutableListOf<Long?>()
    repeat(leadingBlanks) { days.add(null) }
    for (i in 0 until daysInMonth) {
        val day = Calendar.getInstance().apply {
            timeInMillis = firstOfMonthMs
            add(Calendar.DAY_OF_MONTH, i)
        }
        days.add(startOfDay(day.timeInMillis))
    }
    while (days.size % 7 != 0) days.add(null)
    return days
}
