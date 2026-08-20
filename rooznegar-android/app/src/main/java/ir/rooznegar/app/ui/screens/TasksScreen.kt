package ir.rooznegar.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.rooznegar.app.data.TaskEntity
import ir.rooznegar.app.ui.AppViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun TasksScreen(vm: AppViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("همه") }
    val zone = remember { ZoneId.systemDefault() }
    val startOfToday = remember { LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli() }

    val shown by remember(tasks, filter, startOfToday) {
        derivedStateOf {
            when (filter) {
                "امروز" -> tasks.filter { it.dueEpochMillis?.let(::isToday) == true }
                "عقب‌افتاده" -> tasks.filter { !it.isCompleted && it.dueEpochMillis != null && it.dueEpochMillis < startOfToday }
                "انجام‌شده" -> tasks.filter { it.isCompleted }
                else -> tasks
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("کارها", style = MaterialTheme.typography.headlineSmall)
        Text("برنامه‌ها را سریع ببین و انجام‌شده‌ها را علامت بزن.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("همه", "امروز", "عقب‌افتاده", "انجام‌شده").forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) })
            }
        }
        if (shown.isEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Text("در این فهرست کاری وجود ندارد.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(shown, key = { it.id }, contentType = { "task" }) { task -> TaskRow(task, vm) }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskEntity, vm: AppViewModel) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.fillMaxWidth().clickable { vm.toggleTask(task) }.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { vm.toggleTask(task) }) {
                Icon(if (task.isCompleted) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked, "تغییر وضعیت", tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val line = task.description.ifBlank { task.category }
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun isToday(epoch: Long): Boolean = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
