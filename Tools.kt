package ru.herlinux

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

class Notepad(private val a: MainActivity) : LinearLayout(a) {
    private val et = EditText(a)
    private val info = TextView(a)
    private var file: File? = null

    init {
        orientation = VERTICAL
        val bar = LinearLayout(a)
        bar.addView(btn(a, "💾 Сохранить") { save() })
        bar.addView(btn(a, "🆕 Новый") {
            file = null
            et.setText("")
            info.text = "Без имени"
        })
        info.text = "Без имени"
        info.setTextColor(0xFFB9A7E8.toInt())
        info.setPadding(a.dp(12), 0, a.dp(12), a.dp(4))
        et.setTextColor(Color.WHITE)
        et.setBackgroundColor(0x22FFFFFF)
        et.gravity = Gravity.TOP or Gravity.START
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        addView(bar)
        addView(info)
        addView(et, LayoutParams(MP, 0, 1f))
    }

    fun load(f: File) {
        if (f.length() > 1_000_000) {
            a.toast("Файл слишком большой")
            return
        }
        file = f
        et.setText(runCatching { f.readText() }.getOrDefault(""))
        info.text = f.name
    }

    private fun save() {
        val f = file
        if (f != null) {
            runCatching { f.writeText(et.text.toString()) }
            a.toast("Сохранено")
        } else {
            a.ask("Имя файла") { n ->
                val nf = File(a.docs, if (n.contains('.')) n else "$n.txt")
                runCatching { nf.writeText(et.text.toString()) }
                file = nf
                info.text = nf.name
                a.toast("Сохранено")
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
class Browser(private val a: MainActivity) : LinearLayout(a) {
    private val web = WebView(a)
    private val url = EditText(a)

    init {
        orientation = VERTICAL
        val bar = LinearLayout(a)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.addView(btn(a, "◄") { if (web.canGoBack()) web.goBack() })
        bar.addView(btn(a, "►") { if (web.canGoForward()) web.goForward() })
        bar.addView(btn(a, "⟳") { web.reload() })
        url.setTextColor(Color.WHITE)
        url.hint = "Поиск или адрес"
        url.setHintTextColor(0x88FFFFFF.toInt())
        url.textSize = 14f
        url.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        url.imeOptions = EditorInfo.IME_ACTION_GO
        url.setOnEditorActionListener { _, _, _ -> go(); true }
        bar.addView(url, LayoutParams(0, WC, 1f))
        bar.addView(btn(a, "➜") { go() })
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, u: String?, favicon: Bitmap?) {
                if (u != null) url.setText(u)
            }
        }
        addView(bar)
        addView(web, LayoutParams(MP, 0, 1f))
        web.loadUrl("https://ya.ru")
    }

    private fun go() {
        val t = url.text.toString().trim()
        if (t.isEmpty()) return
        val u = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            !t.contains(' ') && t.contains('.') -> "https://$t"
            else -> "https://yandex.ru/search/?text=" + Uri.encode(t)
        }
        web.loadUrl(u)
    }

    fun release() {
        web.stopLoading()
        removeView(web)
        web.destroy()
    }
}

class Calc(private val a: MainActivity) : LinearLayout(a) {
    private val disp = TextView(a)
    private var acc = 0.0
    private var op: Char? = null
    private var cur = ""
    private var fresh = true

    init {
        orientation = VERTICAL
        disp.text = "0"
        disp.setTextColor(Color.WHITE)
        disp.textSize = 36f
        disp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        disp.setPadding(a.dp(12), 0, a.dp(12), 0)
        addView(disp, LayoutParams(MP, a.dp(90)))
        val keys = listOf(
            "C", "±", "%", "÷", "7", "8", "9", "×", "4", "5", "6", "−",
            "1", "2", "3", "+", "0", ".", "⌫", "="
        )
        for (r in 0 until 5) {
            val row = LinearLayout(a)
            for (c in 0 until 4) {
                val k = keys[r * 4 + c]
                val cell = TextView(a).apply {
                    text = k
                    setTextColor(Color.WHITE)
                    textSize = 20f
                    gravity = Gravity.CENTER
                    background = rounded(
                        if (k in "÷×−+=") 0xFF7C3AED.toInt() else 0x33FFFFFF,
                        a.dp(8).toFloat()
                    )
                    setOnClickListener { press(k) }
                }
                val lp = LayoutParams(0, MP, 1f)
                lp.setMargins(a.dp(3), a.dp(3), a.dp(3), a.dp(3))
                row.addView(cell, lp)
            }
            addView(row, LayoutParams(MP, 0, 1f))
        }
    }

    private fun fmt(v: Double): String = when {
        v.isNaN() || v.isInfinite() -> "Ошибка"
        v == Math.floor(v) && Math.abs(v) < 1e15 -> v.toLong().toString()
        else -> v.toString()
    }

    private fun show(s: String) {
        disp.text = s
    }

    private fun calc() {
        val b = cur.toDoubleOrNull() ?: 0.0
        acc = when (op) {
            '+' -> acc + b
            '−' -> acc - b
            '×' -> acc * b
            '÷' -> if (b == 0.0) Double.NaN else acc / b
            else -> b
        }
        cur = fmt(acc)
        show(cur)
    }

    private fun press(k: String) {
        when {
            k[0].isDigit() || k == "." -> {
                if (fresh) {
                    cur = ""
                    fresh = false
                }
                if (k == "." && cur.contains('.')) return
                if (k == "." && cur.isEmpty()) cur = "0"
                cur = if (cur == "0" && k != ".") k else cur + k
                show(cur)
            }
            k == "C" -> {
                acc = 0.0; op = null; cur = ""; fresh = true
                show("0")
            }
            k == "±" -> {
                if (cur.isNotEmpty() && cur != "Ошибка") {
                    cur = if (cur.startsWith("-")) cur.drop(1) else "-$cur"
                    show(cur)
                }
            }
            k == "%" -> {
                cur = fmt((cur.toDoubleOrNull() ?: 0.0) / 100)
                show(cur)
            }
            k == "⌫" -> {
                if (cur.isNotEmpty()) cur = cur.dropLast(1)
                show(if (cur.isEmpty()) "0" else cur)
            }
            k == "=" -> {
                calc()
                op = null
                fresh = true
            }
            else -> {
                if (op != null && !fresh) calc()
                else if (op == null) acc = cur.toDoubleOrNull() ?: 0.0
                op = k[0]
                fresh = true
            }
        }
    }
}
