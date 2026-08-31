package com.example.mental_healt_chatbot

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object RemindersScheduler {

    private const val WORK_NAME = "mindbuddy_reminder_periodic"

    // porneste worker-ul periodic (rerularea minima permisa de WorkManager e 15 min,
    // noi rulam la ~60 min - oricum verificam in worker daca e momentul potrivit)
    fun apply(context: Context, frequency: ReminderFrequency) {
        val wm = WorkManager.getInstance(context.applicationContext)

        if (frequency == ReminderFrequency.OFF) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }

        val req = PeriodicWorkRequestBuilder<ReminderWorker>(
            // intervalul efectiv e ~1h; WorkManager poate intarzia putin pentru optimizare baterie
            1, TimeUnit.HOURS
        )
            .setInitialDelay(1, TimeUnit.MINUTES) // primul check imediat
            .build()

        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE, // daca exista deja, il inlocuim
            req
        )
    }
}
