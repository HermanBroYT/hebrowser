package ru.herlinux

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class AppWindow(
    ctx: Context,
    val key: String,
    title: String,
    val content: View,
    accent: Int,
    private val onClose: (AppWindow) -> Unit,
    private val onFocus: (AppWindow) -> Unit,
    private val onMin: (AppWindow) -> Unit
) : LinearLayout(ctx) {
    private val bar = LinearLayout(ctx)
    private val density = ctx.resources.displayMetrics.density
    private fun px(v: Int) = (v * density + 0.5f).toInt()
    private var ox = 0f
    private var oy = 0f

    init {
        orientation = VERTICAL
        background = rounded(0xFF1B1235.toInt(), px(10).toFloat(), 2, 0x887C3AED.toInt())
        elevation = px(8).toFloat()
        bar.orientation = HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(px(12), 0, px(4), 0)
        val t = TextView(ctx).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        bar.addView(t, LayoutParams(0, WC, 1f))
        fun tb(s: String, f: () -> Unit) = TextView(ctx).apply {
            text = s
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setOnClickListener { f() }
        }
        bar.addView(tb("—") { onMin(this@AppWindow) }, LayoutParams(px(44), px(40)))
        bar.addView(tb("✕") { onClose(this@AppWindow) }, LayoutParams(px(44), px(40)))
        addView(bar, LayoutParams(MP, px(40)))
        addView(content, LayoutParams(MP, 0, 1f))
        setAccent(accent)

        bar.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    onFocus(this)
                    ox = x - e.rawX
                    oy = y - e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    var nx = e.rawX + ox
                    var ny = e.rawY + oy
                    val p = parent as? View
                    if (p != null) {
                        nx = nx.coerceIn(-(width - px(80)).toFloat(), (p.width - px(80)).toFloat())
                        ny = ny.coerceIn(0f, (p.height - px(40)).toFloat())
                    }
                    x = nx
                    y = ny
                }
            }
            true
        }
    }

    fun setAccent(c: Int) {
        val r = px(10).toFloat()
        bar.background = GradientDrawable().apply {
            setColor(c)
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) onFocus(this)
        return false
    }
}
