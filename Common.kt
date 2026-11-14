package ru.herlinux

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.StatFs
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.util.Locale
import kotlin.math.min

const val MP = ViewGroup.LayoutParams.MATCH_PARENT
const val WC = ViewGroup.LayoutParams.WRAP_CONTENT

class Palette(val name: String, val top: Int, val bottom: Int, val accent: Int)

val PALETTES = listOf(
    Palette("Фиолетовая", 0xFF0A0420.toInt(), 0xFF2D0B73.toInt(), 0xFF7C3AED.toInt()),
    Palette("Синяя", 0xFF020617.toInt(), 0xFF0B3A8C.toInt(), 0xFF3B82F6.toInt()),
    Palette("Розовая", 0xFF1A0420.toInt(), 0xFF6B0B5A.toInt(), 0xFFEC4899.toInt())
)

fun rounded(color: Int, r: Float, stroke: Int = 0, strokeColor: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = r
        if (stroke > 0) setStroke(stroke, strokeColor)
    }

fun btn(a: MainActivity, t: String, f: () -> Unit): TextView = TextView(a).apply {
    text = t
    setTextColor(Color.WHITE)
    textSize = 16f
    setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(8))
    setOnClickListener { f() }
}

fun fmtSize(b: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    val l = Locale.getDefault()
    return when {
        b >= gb -> String.format(l, "%.1f ГБ", b / gb)
        b >= mb -> String.format(l, "%.1f МБ", b / mb)
        b >= kb -> String.format(l, "%.0f КБ", b / kb)
        else -> "$b Б"
    }
}

/** Возвращает пару (всего, свободно) в байтах. */
fun diskInfo(f: File): Pair<Long, Long>? = runCatching {
    val s = StatFs(f.path)
    Pair(s.blockCountLong * s.blockSizeLong, s.availableBytes)
}.getOrNull()

fun usageBar(c: Context, frac: Float, color: Int, hPx: Int): View {
    val f = frac.coerceIn(0f, 1f)
    val l = LinearLayout(c)
    l.orientation = LinearLayout.HORIZONTAL
    l.background = rounded(0x33FFFFFF, hPx / 2f)
    val fill = View(c)
    fill.background = rounded(color, hPx / 2f)
    l.addView(fill, LinearLayout.LayoutParams(0, MP, f))
    l.addView(View(c), LinearLayout.LayoutParams(0, MP, 1f - f))
    return l
}

fun logoView(c: Context, sizePx: Int): TextView = TextView(c).apply {
    text = "H"
    gravity = Gravity.CENTER
    setTextColor(0xFF1A0B3D.toInt())
    typeface = Typeface.DEFAULT_BOLD
    setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx * 0.55f)
    background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0xFFB794F6.toInt())
    }
    layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
}

class WallpaperView(c: Context) : View(c) {
    var showLogo = true
    private var top = 0xFF0A0420.toInt()
    private var bottom = 0xFF2D0B73.toInt()
    private var accent = 0xFF7C3AED.toInt()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setColors(a: Int, b: Int, acc: Int) {
        top = a; bottom = b; accent = acc
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.shader = LinearGradient(0f, 0f, w, h, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        for (i in 0..2) {
            val path = Path()
            val y = h * (0.55f + 0.12f * i)
            path.moveTo(0f, y)
            path.cubicTo(w * 0.3f, y - h * 0.25f, w * 0.6f, y + h * 0.25f, w, y - h * 0.15f)
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.close()
            p.color = accent
            p.alpha = 40 + i * 30
            c.drawPath(path, p)
        }
        if (!showLogo) return
        t.textAlign = Paint.Align.CENTER
        t.typeface = Typeface.DEFAULT_BOLD
        t.color = 0xFFD9C8FF.toInt()
        t.textSize = min(w, h) * 0.11f
        val ty = h * 0.55f
        c.drawText("Her Linux 2", w / 2, ty, t)
        val r = t.textSize * 0.7f
        val cy = ty - t.textSize - r * 0.4f
        p.color = 0xFFB794F6.toInt()
        p.alpha = 255
        c.drawCircle(w / 2, cy, r, p)
        t.color = 0xFF1A0B3D.toInt()
        t.textSize = r
        c.drawText("H", w / 2, cy + r * 0.35f, t)
    }
}
