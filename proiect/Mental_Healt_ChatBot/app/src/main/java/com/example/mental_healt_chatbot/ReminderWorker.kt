package com.example.mental_healt_chatbot

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.util.Calendar

// worker-ul ruleaza periodic (la ~60 min) si decide daca trimite notificare:
// - frecventa nu e OFF
// - ora curenta e in lista de ore pentru frecventa aleasa
// - nu s-a mai trimis o notificare in ultimele ~50 min (anti-duplicat)
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = RemindersPrefs(ctx)
        val freq = prefs.getFrequency()

        if (freq == ReminderFrequency.OFF) return Result.success()

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        // 08:00 - 22:00 - intervalul e definit deja in fiecare frecventa,
        // dar punem si o garda dura ca sa nu existe surprize peste noapte
        if (hour !in 8..22) return Result.success()
        if (hour !in freq.hours) return Result.success()

        // anti-duplicat: daca am trimis deja in ultimele 50 min, sarim
        val now = System.currentTimeMillis()
        val last = prefs.getLastNotifiedAt()
        if (now - last < 50 * 60 * 1000L) return Result.success()

        sendNotification(ctx, prefs)
        prefs.setLastNotifiedAt(now)
        return Result.success()
    }

    private fun sendNotification(ctx: Context, prefs: RemindersPrefs) {
        ensureChannel(ctx)

        val (idx, msg) = ReminderMessages.pickMessage(prefs.getLastMessageIndex())
        prefs.setLastMessageIndex(idx)

        val title = ReminderMessages.pickTitle()

        // tap pe notificare deschide aplicatia (MainActivity)
        val openAppIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .build()

        // pe Android 13+ trebuie permisiunea POST_NOTIFICATIONS - daca nu o avem, nu putem trimite
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        NotificationManagerCompat.from(ctx).notify(NOTIF_ID, notif)
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Reminders MindBuddy",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Mesaje scurte de check-in pe parcursul zilei"
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "mindbuddy_reminders"
        const val NOTIF_ID   = 1001
    }
}
