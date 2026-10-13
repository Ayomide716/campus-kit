package com.ayomide.campuskit.screens

import android.content.Intent
import android.os.Bundle
import com.ayomide.campuskit.data.Card
import com.ayomide.campuskit.data.Db
import com.ayomide.campuskit.data.Deck
import com.ayomide.campuskit.core.Leitner
import com.ayomide.campuskit.ui.BaseScreen
import java.time.LocalDate

class DeckActivity : BaseScreen() {

    companion object {
        const val EXTRA_ID = "deck_id"
    }

    override val screenTitle = "Deck"
    private var deckId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        deckId = intent.getLongExtra(EXTRA_ID, 0)
        super.onCreate(savedInstanceState)
    }

    override fun render() {
        val db = Db.get(this)
        val deck = db.deck(deckId) ?: run { finish(); return }
        title = deck.name
        val today = LocalDate.now().toEpochDay()
        val cards = db.cards(deckId)
        val due = cards.count { Leitner.isDue(it.dueDay, today) }

        add(ui.card().apply {
            addView(ui.big(deck.name).apply { textSize = 24f })
            addView(ui.small("${cards.size} cards · $due due today"))
            if (due > 0) {
                addView(ui.button("▶ Study $due card${if (due == 1) "" else "s"}") {
                    startActivity(Intent(this@DeckActivity, CardStudyActivity::class.java).putExtra(EXTRA_ID, deckId))
                })
            }
        })
        add(ui.row().apply {
            addView(ui.flatButton("+ Card") { editCard(null) })
            addView(ui.flatButton("Paste many") { bulk() })
            addView(ui.flatButton("Rename") { rename(deck) })
        })

        if (cards.isEmpty()) {
            add(ui.empty("No cards yet. Tap \"+ Card\", or \"Paste many\" to add a list like:\nMitosis | Cell division\nOsmosis | Movement of water"))
        }
        for (c in cards) {
            add(ui.card { editCard(c) }.apply {
                addView(ui.title(c.front))
                addView(ui.small(c.back))
                addView(ui.small("Box ${c.box}/${Leitner.MAX_BOX}" + if (Leitner.isDue(c.dueDay, today)) " · due" else " · in ${c.dueDay - today} day(s)"))
            })
        }
        add(ui.flatButton("Delete deck") {
            ui.confirm("Delete ${deck.name}?", "All ${cards.size} cards will be deleted.", "Delete") {
                db.deleteDeck(deckId); finish()
            }
        })
    }

    private fun editCard(existing: Card?) {
        val front = ui.multiline("Front (question)", existing?.front ?: "", 2)
        val back = ui.multiline("Back (answer)", existing?.back ?: "", 2)
        ui.form(
            if (existing == null) "New card" else "Edit card",
            ui.column().apply { addView(front); addView(back) },
            onDelete = existing?.let { e -> { Db.get(this).deleteCard(e.id); refresh() } },
        ) {
            val f = front.text.toString().trim()
            val b = back.text.toString().trim()
            if (f.isEmpty() || b.isEmpty()) "Fill in both sides." else {
                Db.get(this).saveCard(existing?.copy(front = f, back = b) ?: Card(0, deckId, f, b, 1, 0))
                refresh(); null
            }
        }
    }

    private fun bulk() {
        val text = ui.multiline("One card per line:\nquestion | answer", "", 6)
        ui.form("Paste many cards", ui.column().apply {
            addView(text)
            addView(ui.small("Separate the two sides with |, = or a tab. Lines without one are skipped."))
        }, saveLabel = "Add") {
            val pairs = Leitner.parseBulk(text.text.toString())
            if (pairs.isEmpty()) "No cards found. Use: question | answer" else {
                Db.get(this).addCards(deckId, pairs)
                android.widget.Toast.makeText(this, "Added ${pairs.size} cards", android.widget.Toast.LENGTH_SHORT).show()
                refresh(); null
            }
        }
    }

    private fun rename(deck: Deck) {
        val name = ui.input("Deck name", deck.name)
        ui.form("Rename deck", ui.column().apply { addView(name) }) {
            val n = name.text.toString().trim()
            if (n.isEmpty()) "Enter a name." else {
                Db.get(this).saveDeck(deck.copy(name = n)); refresh(); null
            }
        }
    }
}
