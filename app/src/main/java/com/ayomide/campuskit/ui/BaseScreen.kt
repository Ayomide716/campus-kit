package com.ayomide.campuskit.ui

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ayomide.campuskit.R

/**
 * Base for every screen: a top bar with back arrow and title, and a scrolling
 * column that is rebuilt by [render] whenever the screen is shown or data
 * changes (call [refresh]).
 */
abstract class BaseScreen : Activity() {

    protected lateinit var ui: Ui
    protected lateinit var content: LinearLayout
    protected lateinit var actions: LinearLayout
    private lateinit var scroll: ScrollView

    abstract val screenTitle: String
    protected open val showBack: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.bg))
        }

        val bar = ui.row().apply {
            setPadding(dp(8), dp(8), dp(8), dp(4))
            minimumHeight = dp(56)
        }
        if (showBack) {
            bar.addView(TextView(this).apply {
                text = "←"
                contentDescription = "Back"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTextColor(getColor(R.color.text_primary))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                isClickable = true
                isFocusable = true
                val a = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
                background = a.getDrawable(0)
                a.recycle()
                setOnClickListener { finish() }
            })
        }
        bar.addView(TextView(this).apply {
            text = screenTitle
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(getColor(R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(if (showBack) dp(4) else dp(8), 0, 0, 0)
        }.weight(1f))
        actions = ui.row().apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) }
        bar.addView(actions)

        content = ui.column().apply { setPadding(dp(16), 0, dp(16), dp(32)) }
        scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(content)
        }

        root.addView(bar)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        applyInsets(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    fun refresh() {
        val y = scroll.scrollY
        content.removeAllViews()
        render()
        scroll.post { scroll.scrollTo(0, y) }
    }

    abstract fun render()

    protected fun add(v: View) = content.addView(v)

    private fun applyInsets(root: View) {
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(b.left, b.top, b.right, b.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
    }
}
