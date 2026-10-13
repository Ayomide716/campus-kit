package com.ayomide.campuskit.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.ayomide.campuskit.R
import com.ayomide.campuskit.core.Schedule
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun <T : View> T.weight(w: Float): T = apply {
    layoutParams = LinearLayout.LayoutParams(0, WRAP, w)
}

fun <T : View> T.marginTop(px: Int): T = apply {
    val lp = (layoutParams as? LinearLayout.LayoutParams) ?: LinearLayout.LayoutParams(MATCH, WRAP)
    lp.topMargin = px
    layoutParams = lp
}

/** Small builder for the programmatic, framework-only UI. */
class Ui(val ctx: Context) {

    fun color(id: Int) = ctx.getColor(id)

    fun column(): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }

    fun row(): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }

    private fun selectable(): Drawable? {
        val a = ctx.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        val d = a.getDrawable(0)
        a.recycle()
        return d
    }

    fun card(onClick: (() -> Unit)? = null): LinearLayout = column().apply {
        background = ctx.getDrawable(R.drawable.card_bg)
        val p = ctx.dp(16)
        setPadding(p, p, p, p)
        marginTop(ctx.dp(12))
        if (onClick != null) {
            isClickable = true
            isFocusable = true
            foreground = selectable()
            setOnClickListener { onClick() }
        }
    }

    fun text(
        s: CharSequence,
        sizeSp: Float = 14f,
        colorRes: Int = R.color.text_secondary,
        bold: Boolean = false,
    ): TextView = TextView(ctx).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color(colorRes))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(0f, 1.1f)
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }

    fun title(s: CharSequence) = text(s, 16f, R.color.text_primary, true)
    fun primary(s: CharSequence) = text(s, 15f, R.color.text_primary)
    fun small(s: CharSequence) = text(s, 13f)
    fun big(s: CharSequence, colorRes: Int = R.color.text_primary) = text(s, 30f, colorRes, true)

    fun section(s: String) = text(s.uppercase(Locale.getDefault()), 12f, R.color.text_secondary, true).marginTop(ctx.dp(20))

    fun button(label: String, onClick: () -> Unit): Button = Button(ctx).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    fun flatButton(label: String, onClick: () -> Unit): Button =
        Button(ctx, null, android.R.attr.borderlessButtonStyle).apply {
            text = label
            isAllCaps = false
            setTextColor(color(R.color.accent))
            setOnClickListener { onClick() }
        }

    fun progress(percent: Int, colorRes: Int = R.color.accent): ProgressBar =
        ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = percent.coerceIn(0, 100)
            progressTintList = ColorStateList.valueOf(color(colorRes))
            layoutParams = LinearLayout.LayoutParams(MATCH, ctx.dp(8)).apply { topMargin = ctx.dp(8) }
        }

    fun input(hint: String, value: String = "", type: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES): EditText =
        EditText(ctx).apply {
            this.hint = hint
            setText(value)
            inputType = type
            setTextColor(color(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

    fun numberInput(hint: String, value: String = "") =
        input(hint, value, InputType.TYPE_CLASS_NUMBER)

    fun decimalInput(hint: String, value: String = "") =
        input(hint, value, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)

    fun multiline(hint: String, value: String = "", lines: Int = 3): EditText =
        input(hint, value, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).apply {
            minLines = lines
            gravity = Gravity.TOP or Gravity.START
        }

    fun label(s: String) = small(s).marginTop(ctx.dp(10))

    fun spinner(items: List<String>, selected: Int): Spinner = Spinner(ctx).apply {
        adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, items)
        setSelection(selected.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }

    /** Button that shows a time and opens a 24h picker. */
    fun timeField(initialMin: Int, onChange: (Int) -> Unit): Button {
        var current = initialMin
        val b = Button(ctx, null, android.R.attr.borderlessButtonStyle)
        b.text = Schedule.hhmm(current)
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        b.setOnClickListener {
            TimePickerDialog(ctx, { _, h, m ->
                current = h * 60 + m
                b.text = Schedule.hhmm(current)
                onChange(current)
            }, current / 60, current % 60, true).show()
        }
        return b
    }

    fun dateField(initial: LocalDate, onChange: (LocalDate) -> Unit): Button {
        var current = initial
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())
        val b = Button(ctx, null, android.R.attr.borderlessButtonStyle)
        b.text = current.format(fmt)
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        b.setOnClickListener {
            DatePickerDialog(ctx, { _, y, m, d ->
                current = LocalDate.of(y, m + 1, d)
                b.text = current.format(fmt)
                onChange(current)
            }, current.year, current.monthValue - 1, current.dayOfMonth).show()
        }
        return b
    }

    /**
     * Dialog with a form. [onSave] returns an error message to show, or null
     * to close. [onDelete] adds a Delete button (with confirmation).
     */
    fun form(
        title: String,
        body: LinearLayout,
        saveLabel: String = "Save",
        onDelete: (() -> Unit)? = null,
        onSave: () -> String?,
    ) {
        val error = text("", 13f, R.color.danger).apply { visibility = View.GONE }
        val wrapper = column().apply {
            val p = ctx.dp(20)
            setPadding(p, ctx.dp(8), p, 0)
            addView(body)
            addView(error.marginTop(ctx.dp(8)))
        }
        val scroll = ScrollView(ctx).apply { addView(wrapper) }
        val builder = AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(saveLabel, null)
            .setNegativeButton("Cancel", null)
        if (onDelete != null) {
            builder.setNeutralButton("Delete") { _, _ ->
                confirm("Delete?", "This can't be undone.", "Delete") { onDelete() }
            }
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val err = try {
                    onSave()
                } catch (e: Exception) {
                    "Something went wrong: ${e.message}"
                }
                if (err == null) {
                    dialog.dismiss()
                } else {
                    error.text = err
                    error.visibility = View.VISIBLE
                }
            }
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
    }

    fun confirm(title: String, message: String, yes: String, action: () -> Unit) {
        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(yes) { _, _ -> action() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun empty(message: String) = card().apply {
        addView(text(message).apply { gravity = Gravity.CENTER })
    }
}
