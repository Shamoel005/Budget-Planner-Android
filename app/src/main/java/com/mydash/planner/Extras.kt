package com.mydash.planner

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

object Reminders {
    const val CH = "reminders"
    private const val DAILY_CODE = 1_999_999_999

    private fun pi(ctx: Context, code: Int, title: String, text: String): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .putExtra("title", title).putExtra("text", text).putExtra("code", code)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun am(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private fun code(b: Bill): Int = (b.id % 100_000_000L).toInt() * 2

    fun cancelBill(ctx: Context, b: Bill) {
        am(ctx).cancel(pi(ctx, code(b), "", ""))
        am(ctx).cancel(pi(ctx, code(b) + 1, "", ""))
    }

    fun scheduleBill(ctx: Context, b: Bill) {
        cancelBill(ctx, b)
        val due = nextDue(b)
        val now = System.currentTimeMillis()
        val text = b.name + " · " + money(b.amount)
        if (due - DAY > now) am(ctx).set(AlarmManager.RTC_WAKEUP, due - DAY, pi(ctx, code(b), "Bill due tomorrow", text))
        if (due > now) am(ctx).set(AlarmManager.RTC_WAKEUP, due, pi(ctx, code(b) + 1, "Bill due today", text))
    }

    fun scheduleAll(ctx: Context, d: Data) = d.bills.forEach { scheduleBill(ctx, it) }

    fun setDaily(ctx: Context, on: Boolean) {
        val p = pi(ctx, DAILY_CODE, "Log today's spending", "Take 10 seconds to add what you spent today.")
        if (!on) { am(ctx).cancel(p); return }
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        am(ctx).setInexactRepeating(AlarmManager.RTC_WAKEUP, c.timeInMillis, AlarmManager.INTERVAL_DAY, p)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(Reminders.CH, "Reminders", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, Reminders.CH)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(intent.getStringExtra("title") ?: "Reminder")
            .setContentText(intent.getStringExtra("text") ?: "")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(intent.getIntExtra("code", 0), n)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Reminders.scheduleAll(ctx, Store.load(ctx))
            if (ctx.getSharedPreferences("p", Context.MODE_PRIVATE).getBoolean("daily", false)) Reminders.setDaily(ctx, true)
        }
    }
}
