package ir.rooznegar.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ir.rooznegar.app.data.AppDatabase
import ir.rooznegar.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduler = AndroidReminderScheduler(context)
                AppDatabase.get(context).taskDao().futureReminders(System.currentTimeMillis()).forEach(scheduler::schedule)
                if (SettingsStore(context).settings.first().persistentDateNotification) Notifications.showDateBar(context)
                else Notifications.hideDateBar(context)
            } finally { pending.finish() }
        }
    }
}
