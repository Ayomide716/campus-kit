package com.ayomide.campuskit.screens

import android.content.Intent
import com.ayomide.campuskit.R
import com.ayomide.campuskit.data.Db
import com.ayomide.campuskit.data.Deck
import com.ayomide.campuskit.ui.BaseScreen
import java.time.LocalDate

class DecksActivity : BaseScreen() {

    override val screenTitle = "Flashcards"

    override fun render() {
        val db = Db.get(this)
        val today = LocalDate.now().toEpochDay()
        val decks = db.decks()
        add(ui.button("+ New deck") { newDeck() })
        if (decks.isEmpty()) {
            add(ui.empty("Make a deck per course (e.g. BIO 101), add cards, then study the ones that are due each day. Cards you know come back less often."))
            return
        }
        for (d in decks) {
            val total = db.cards(d.id).size
            val due = db.dueCount(d.id, today)
            add(ui.card { startActivity(Intent(this, DeckActivity::class.java).putExtra(DeckActivity.EXTRA_ID, d.id)) }.apply {
                addView(ui.title(d.name))
                addView(ui.text(if (due > 0) "$due due today · $total cards" else "All done for today · $total cards", 14f,
                    if (due > 0) R.color.accent else R.color.text_secondary, due > 0))
            })
        }
    }

    private fun newDeck() {
        val name = ui.input("Deck name, e.g. BIO 101")
        ui.form("New deck", ui.column().apply { addView(name) }) {
            val n = name.text.toString().trim()
            if (n.isEmpty()) "Enter a name." else {
                Db.get(this).saveDeck(Deck(0, n)); refresh(); null
            }
        }
    }
}
