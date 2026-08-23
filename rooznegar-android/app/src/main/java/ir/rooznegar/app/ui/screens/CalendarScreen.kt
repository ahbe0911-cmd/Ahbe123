package ir.rooznegar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.rooznegar.app.core.calendar.JalaliCalendar
import ir.rooznegar.app.core.calendar.JalaliDate
import ir.rooznegar.app.core.calendar.toPersianDigits
import ir.rooznegar.app.ui.AppViewModel
import java.time.Instant
import java.time.ZoneId

@Composable
fun CalendarScreen(vm: AppViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val today = remember { JalaliCalendar.today() }
    var year by remember { mutableIntStateOf(today.year) }
    var month by remember { mutableIntStateOf(today.month) }

    val cells = remember(year, month) {
        val firstGregorian = JalaliCalendar.toGregorian(JalaliDate(year, month, 1))
        val offset = (firstGregorian.dayOfWeek.value + 1) % 7
        val length = JalaliCalendar.monthLength(year, month)
        List(42) { index -> (index - offset + 1).takeIf { it in 1..length } }
    }
    val taskDays by remember(tasks, year, month) {
        derivedStateOf {
            tasks.asSequence()
                .filter { !it.isCompleted && !it.isCancelled }
                .mapNotNull { it.dueEpochMillis }
                .map { JalaliCalendar.fromGregorian(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()) }
                .filter { it.year == year && it.month == month }
                .map { it.day }
                .toSet()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.size(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (month == 1) { month = 12; year-- } else month-- }) { Icon(Icons.Default.ChevronRight, "ماه قبل") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(JalaliDate.MONTHS[month - 1], style = MaterialTheme.typography.headlineSmall)
                Text(toPersianDigits(year.toString()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { if (month == 12) { month = 1; year++ } else month++ }) { Icon(Icons.Default.ChevronLeft, "ماه بعد") }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEachIndexed { index, label ->
                        Text(
                            label,
                            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (index == 6) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                repeat(6) { rowIndex ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { columnIndex ->
                            val day = cells[rowIndex * 7 + columnIndex]
                            DayCell(
                                day = day,
                                isToday = day != null && year == today.year && month == today.month && day == today.day,
                                hasTask = day != null && day in taskDays,
                                isFriday = columnIndex == 6,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("● روزهای دارای کار", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text("شروع هفته: شنبه", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DayCell(day: Int?, isToday: Boolean, hasTask: Boolean, isFriday: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        if (day != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            toPersianDigits(day.toString()),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isToday -> MaterialTheme.colorScheme.onPrimary
                                isFriday -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
                Box(
                    Modifier.size(5.dp).background(
                        if (hasTask) MaterialTheme.colorScheme.primary else Color.Transparent,
                        CircleShape
                    )
                )
            }
        }
    }
}
