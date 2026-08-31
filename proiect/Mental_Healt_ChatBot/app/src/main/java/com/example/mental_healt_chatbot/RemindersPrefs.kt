package com.example.mental_healt_chatbot

import android.content.Context

// frecventele disponibile - OFF inseamna ca nu trimite nimic
enum class ReminderFrequency(val label: String, val hours: List<Int>) {
    OFF         ("Dezactivat",        emptyList()),
    ONCE_PER_DAY("O dată pe zi",      listOf(10)),
    TWO_PER_DAY ("De 2 ori pe zi",    listOf(10, 18)),
    FOUR_PER_DAY("De 4 ori pe zi",    listOf(9, 13, 17, 21)),
    HOURLY      ("Din oră în oră",    (8..22).toList())
}

class RemindersPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)

    fun getFrequency(): ReminderFrequency {
        val name = prefs.getString(KEY_FREQ, ReminderFrequency.OFF.name)
        return runCatching { ReminderFrequency.valueOf(name!!) }.getOrDefault(ReminderFrequency.OFF)
    }

    fun setFrequency(f: ReminderFrequency) {
        prefs.edit().putString(KEY_FREQ, f.name).apply()
    }

    // index-ul ultimului mesaj trimis - ca sa nu repete consecutiv
    fun getLastMessageIndex(): Int = prefs.getInt(KEY_LAST_MSG, -1)

    fun setLastMessageIndex(i: Int) {
        prefs.edit().putInt(KEY_LAST_MSG, i).apply()
    }

    // ora la care a trimis ultima notificare (in ms epoch) - ca sa nu trimita
    // doua notificari pentru aceeasi fereastra orara daca worker-ul ruleaza de doua ori
    fun getLastNotifiedAt(): Long = prefs.getLong(KEY_LAST_AT, 0L)

    fun setLastNotifiedAt(ts: Long) {
        prefs.edit().putLong(KEY_LAST_AT, ts).apply()
    }

    companion object {
        private const val KEY_FREQ     = "frequency"
        private const val KEY_LAST_MSG = "last_msg_index"
        private const val KEY_LAST_AT  = "last_notified_at"
    }
}
