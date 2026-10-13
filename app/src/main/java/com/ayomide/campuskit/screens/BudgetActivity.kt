package com.ayomide.campuskit.screens

import com.ayomide.campuskit.R
import com.ayomide.campuskit.core.Money
import com.ayomide.campuskit.data.Db
import com.ayomide.campuskit.data.Expense
import com.ayomide.campuskit.data.Prefs
import com.ayomide.campuskit.ui.BaseScreen
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class BudgetActivity : BaseScreen() {

    override val screenTitle = "Budget"

    private var month: YearMonth = YearMonth.now()

    override fun render() {
        val db = Db.get(this)
        val prefs = Prefs(this)
        val zone = ZoneId.systemDefault()
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val list = db.expenses(from, to)
        val spent = list.sumOf { it.amountKobo }
        val allowance = prefs.allowanceKobo
        val isCurrent = month == YearMonth.now(zone)

        add(ui.row().apply {
            addView(ui.flatButton("‹") { month = month.minusMonths(1); refresh() })
            addView(ui.title(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))).apply {
                gravity = android.view.Gravity.CENTER
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(ui.flatButton("›") { month = month.plusMonths(1); refresh() })
        })

        add(ui.card { editAllowance() }.apply {
            if (allowance > 0) {
                val left = allowance - spent
                addView(ui.small("LEFT THIS MONTH"))
                addView(ui.big(Money.format(left), if (left < 0) R.color.danger else R.color.success))
                addView(ui.small("Spent ${Money.format(spent)} of ${Money.format(allowance)} allowance"))
                addView(ui.progress(((spent * 100) / allowance).toInt(), if (left < 0) R.color.danger else R.color.accent))
                if (isCurrent) {
                    val daysLeft = month.lengthOfMonth() - LocalDate.now(zone).dayOfMonth + 1
                    addView(ui.primary("≈ ${Money.format(Money.perDay(left, daysLeft))} per day for the next $daysLeft day(s)").marginTopDp(8))
                }
            } else {
                addView(ui.small("SPENT THIS MONTH"))
                addView(ui.big(Money.format(spent)))
                addView(ui.text("Tap to set your monthly allowance", 14f, R.color.accent, true))
            }
        })

        add(ui.button("+ Add expense") { edit(null) })

        if (list.isEmpty()) {
            add(ui.empty("No expenses this month."))
            return
        }

        add(ui.section("By category"))
        add(ui.card().apply {
            val byCat = list.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amountKobo } }
                .entries.sortedByDescending { it.value }
            for ((cat, amt) in byCat) {
                addView(ui.primary("$cat · ${Money.format(amt)}"))
                addView(ui.progress(((amt * 100) / spent.coerceAtLeast(1)).toInt()))
            }
        })

        add(ui.section("Expenses"))
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
        add(ui.card().apply {
            for (e in list) {
                val day = Instant.ofEpochMilli(e.atMillis).atZone(zone).format(fmt)
                val note = if (e.note.isNotBlank()) " · ${e.note}" else ""
                addView(ui.flatButton("${Money.format(e.amountKobo)} · ${e.category}$note · $day") { edit(e) }.apply {
                    gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                    setTextColor(getColor(R.color.text_primary))
                })
            }
        })
    }

    private fun android.view.View.marginTopDp(v: Int) = apply {
        (layoutParams as? android.widget.LinearLayout.LayoutParams)?.topMargin = (v * resources.displayMetrics.density).toInt()
    }

    private fun editAllowance() {
        val prefs = Prefs(this)
        val input = ui.decimalInput("Monthly allowance in ₦", if (prefs.allowanceKobo > 0) (prefs.allowanceKobo / 100).toString() else "")
        ui.form("Monthly allowance", ui.column().apply {
            addView(input)
            addView(ui.small("Used for every month. Leave empty to remove."))
        }) {
            val s = input.text.toString().trim()
            if (s.isEmpty()) {
                prefs.allowanceKobo = 0; refresh(); null
            } else {
                val k = Money.parseKobo(s)
                if (k == null) "Enter an amount like 50000." else {
                    prefs.allowanceKobo = k; refresh(); null
                }
            }
        }
    }

    private fun edit(existing: Expense?) {
        val zone = ZoneId.systemDefault()
        var date: LocalDate = existing?.let { Instant.ofEpochMilli(it.atMillis).atZone(zone).toLocalDate() }
            ?: if (month == YearMonth.now(zone)) LocalDate.now(zone) else month.atDay(1)
        val amount = ui.decimalInput("Amount in ₦", existing?.let { Money.format(it.amountKobo).removePrefix("₦").replace(",", "") } ?: "")
        val cats = Db.EXPENSE_CATEGORIES
        val cat = ui.spinner(cats, existing?.let { cats.indexOf(it.category).coerceAtLeast(0) } ?: 0)
        val note = ui.input("Note (optional), e.g. Jollof rice", existing?.note ?: "")
        ui.form(
            if (existing == null) "Add expense" else "Edit expense",
            ui.column().apply {
                addView(amount)
                addView(ui.label("Category")); addView(cat)
                addView(note)
                addView(ui.label("Date")); addView(ui.dateField(date) { date = it })
            },
            onDelete = existing?.let { e -> { Db.get(this).deleteExpense(e.id); refresh() } },
        ) {
            val k = Money.parseKobo(amount.text.toString())
            if (k == null) "Enter an amount like 1500." else {
                val at = if (existing != null && date == Instant.ofEpochMilli(existing.atMillis).atZone(zone).toLocalDate()) {
                    existing.atMillis
                } else {
                    date.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()
                }
                Db.get(this).saveExpense(Expense(existing?.id ?: 0, at, k, cats[cat.selectedItemPosition], note.text.toString().trim()))
                refresh(); null
            }
        }
    }
}
