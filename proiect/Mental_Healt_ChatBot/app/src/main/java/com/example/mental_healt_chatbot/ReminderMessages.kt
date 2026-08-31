package com.example.mental_healt_chatbot

object ReminderMessages {

    // 14 variante - destul ca sa nu pareau repetitive intr-o saptamana
    private val pool = listOf(
        "Hey, ce mai faci? E totul bine?",
        "Cum te simți astăzi? Sunt aici dacă vrei să vorbim.",
        "Salut! Ai un moment pentru tine?",
        "Sper că ziua ta merge bine. Vrei să-mi povestești?",
        "Te-am prins într-un moment liber? Spune-mi ce simți.",
        "Pauză de gânduri? Hai să le punem în ordine împreună.",
        "Cum a fost ziua până acum?",
        "Respiră adânc. Sunt aici dacă ai nevoie să stăm de vorbă.",
        "Ai trecut prin ceva azi? Povestește-mi.",
        "Mă gândeam la tine. Cum stai cu emoțiile?",
        "5 minute pentru tine — ce simți chiar acum?",
        "Hai să facem un check-in scurt. Cum te simți?",
        "Sper că ai grijă de tine. Vrei să stăm de vorbă?",
        "Câte un cuvânt despre cum e ziua ta?"
    )

    private val titles = listOf(
        "MindBuddy",
        "Pauză de gânduri",
        "Check-in zilnic",
        "Un companion pentru tine"
    )

    // alege un mesaj diferit fata de ultimul trimis (din tot pool-ul)
    fun pickMessage(lastIndex: Int): Pair<Int, String> {
        if (pool.size <= 1) return 0 to pool.first()
        var idx = (pool.indices).random()
        // daca a iesit acelasi index, sarim la urmatorul - simplu si suficient
        if (idx == lastIndex) idx = (idx + 1) % pool.size
        return idx to pool[idx]
    }

    fun pickTitle(): String = titles.random()
}
