package com.ayomide.campuskit.screens

import android.os.Bundle
import android.view.Gravity
import com.ayomide.campuskit.R
import com.ayomide.campuskit.core.Leitner
import com.ayomide.campuskit.data.Card
import com.ayomide.campuskit.data.Db
import com.ayomide.campuskit.ui.BaseScreen
import com.ayomide.campuskit.ui.dp
import java.time.LocalDate

class CardStudyActivity : BaseScreen() {

    override val screenTitle = "Study"

    private var queue = ArrayDeque<Card>()
    private var showBackSide = false
    private var done = 0
    private var total = 0
    private var correct = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deckId = intent.getLongExtra(DeckActivity.EXTRA_ID, 0)
        val today = LocalDate.now().toEpochDay()
        queue = ArrayDeque(Db.get(this).cards(deckId).filter { Leitner.isDue(it.dueDay, today) }.shuffled())
        total = queue.size
    }

    // Keep progress when returning to the screen; only render from memory.
    override fun render() {
        val card = queue.firstOrNull()
        if (card == null) {
            add(ui.card().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(ui.text("🎉", 48f).apply { gravity = Gravity.CENTER })
                addView(ui.title("All done!").apply { gravity = Gravity.CENTER })
                addView(ui.small(if (total == 0) "No cards were due." else "You reviewed $total card(s), $correct right first time.").apply { gravity = Gravity.CENTER })
                addView(ui.button("Back to deck") { finish() })
            })
            return
        }
        add(ui.small("Card ${(done + 1).coerceAtMost(total)} of $total"))
        add(ui.progress(if (total == 0) 0 else done * 100 / total))
        add(ui.card { showBackSide = !showBackSide; refresh() }.apply {
            minimumHeight = dp(220)
            gravity = Gravity.CENTER
            addView(ui.small(if (showBackSide) "ANSWER" else "QUESTION").apply { gravity = Gravity.CENTER })
            addView(ui.text(if (showBackSide) card.back else card.front, 22f, R.color.text_primary, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(12))
            })
            if (!showBackSide) addView(ui.small("Tap to reveal").apply { gravity = Gravity.CENTER })
        })
        if (showBackSide) {
            add(ui.row().apply {
                addView(ui.button("✗ Again") { answer(card, false) }.apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(ui.button("✓ Got it") { answer(card, true) }.apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
            })
        }
    }

    private val missed = HashSet<Long>()

    private fun answer(card: Card, right: Boolean) {
        val today = LocalDate.now().toEpochDay()
        queue.removeFirst()
        if (right) {
            val n = Leitner.answer(card.box, true, today)
            // A card missed earlier in this session only climbs one box from 1.
            Db.get(this).saveCard(card.copy(box = n.box, dueDay = n.dueDay))
            if (card.id !in missed) correct++
            done++
        } else {
            val n = Leitner.answer(card.box, false, today)
            val updated = card.copy(box = n.box, dueDay = n.dueDay)
            Db.get(this).saveCard(updated)
            missed += card.id
            queue.addLast(updated) // see it again this session
        }
        showBackSide = false
        refresh()
    }
}
