package ir.rooznegar.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Notifications
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.rooznegar.app.core.calendar.JalaliCalendar
import ir.rooznegar.app.core.calendar.toPersianDigits
import ir.rooznegar.app.data.TaskEntity
import ir.rooznegar.app.ui.AppViewModel
import ir.rooznegar.app.ui.theme.RooznegarGreen
import ir.rooznegar.app.ui.theme.RooznegarOrange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(vm: AppViewModel, onSettings: () -> Unit) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf(false) }

    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val jalali = remember(today) { JalaliCalendar.fromGregorian(today) }
    val start = remember(today) { today.atStartOfDay(zone).toInstant().toEpochMilli() }
    val end = remember(today) { today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() }

    val todayTasks by remember(tasks, start, end) {
        derivedStateOf {
            tasks.asSequence()
                .filter { !it.isCompleted && !it.isCancelled }
                .filter { it.dueEpochMillis != null && it.dueEpochMillis in start until end }
                .sortedBy { it.dueEpochMillis }
                .toList()
        }
    }
    val overdue by remember(tasks, start) {
        derivedStateOf { tasks.count { !it.isCompleted && !it.isCancelled && it.dueEpochMillis != null && it.dueEpochMillis < start } }
    }
    val pinnedNotes by remember(notes) { derivedStateOf { notes.filter { it.isPinned }.take(3) } }

    if (search) {
        SearchOverlay(tasks = tasks, notes = notes, onClose = { search = false })
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(6.dp))
            TodayTopBar(onSettings = onSettings, onSearch = { search = true })
        }

        item {
            DateHeroCard(
                weekday = JalaliCalendar.dayOfWeekFa(today),
                jalaliDate = jalali.formatLong(JalaliCalendar.dayOfWeekFa(today)),
                gregorian = JalaliCalendar.gregorianFa(today),
                taskCount = todayTasks.size,
                overdue = overdue
            )
        }

        item { SectionHeader("برنامه امروز", if (todayTasks.isEmpty()) null else "${toPersianDigits(todayTasks.size.toString())} کار") }

        if (todayTasks.isEmpty()) {
            item { FriendlyEmptyCard("امروز برنامه‌ای ثبت نشده", "با دکمه + یک کار یا یادآور اضافه کن.") }
        } else {
            items(todayTasks.take(6), key = { it.id }) { task -> TimelineTask(task = task, onToggle = { vm.toggleTask(task) }) }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallInfoCard(
                    modifier = Modifier.weight(1f),
                    title = "یادآورها",
                    value = toPersianDigits(tasks.count { !it.isCompleted && it.reminderEpochMillis != null }.toString()),
                    supporting = "فعال",
                    accent = MaterialTheme.colorScheme.primary
                )
                SmallInfoCard(
                    modifier = Modifier.weight(1f),
                    title = "انجام‌شده",
                    value = toPersianDigits(tasks.count { it.isCompleted }.toString()),
                    supporting = "کل کارها",
                    accent = RooznegarGreen
                )
            }
        }

        item { SectionHeader("یادداشت‌های مهم") }
        if (pinnedNotes.isEmpty()) {
            item { FriendlyEmptyCard("یادداشت سنجاق‌شده نداری", "یادداشت‌های مهمت را سنجاق کن تا اینجا ببینی.") }
        } else {
            items(pinnedNotes, key = { it.id }) { note ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Notifications, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(note.title.ifBlank { "یادداشت" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        if (note.body.isNotBlank()) {
                            Text(note.body, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun TodayTopBar(onSettings: () -> Unit, onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row {
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "تنظیمات") }
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "جستجو") }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("روزنگار", style = MaterialTheme.typography.titleLarge)
            Text("امروزت را ساده‌تر مدیریت کن", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DateHeroCard(weekday: String, jalaliDate: String, gregorian: String, taskCount: Int, overdue: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                    Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(9.dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(weekday, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(jalaliDate.substringAfter("، ", jalaliDate), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(gregorian, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .66f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroMetric(Modifier.weight(1f), "کار امروز", taskCount, MaterialTheme.colorScheme.primary)
                HeroMetric(Modifier.weight(1f), "عقب‌افتاده", overdue, RooznegarOrange)
            }
        }
    }
}

@Composable
private fun HeroMetric(modifier: Modifier, label: String, value: Int, accent: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .88f)) {
        Column(Modifier.padding(13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(toPersianDigits(value.toString()), style = MaterialTheme.typography.headlineSmall, color = accent)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TimelineTask(task: TaskEntity, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(task.dueEpochMillis?.let(::timeOf) ?: "—", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
            Box(Modifier.size(3.dp, 42.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(task.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onToggle) { Icon(Icons.Outlined.Circle, "انجام شد", tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun SmallInfoCard(modifier: Modifier, title: String, value: String, supporting: String, accent: Color) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(15.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = accent)
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.End)
    }
}

@Composable
private fun FriendlyEmptyCard(title: String, supporting: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.End) {
            Icon(Icons.Default.CheckCircle, null, tint = RooznegarGreen)
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun timeOf(epoch: Long): String = Instant.ofEpochMilli(epoch)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("HH:mm"))
    .let(::toPersianDigits)
