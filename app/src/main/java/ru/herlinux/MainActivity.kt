package ru.herlinux

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
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

// ───────────────────────── Обои ─────────────────────────
class WallpaperView(c: Context) : View(c) {
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

// ───────────────────────── Окно ─────────────────────────
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

// ───────────────────────── Проводник ─────────────────────────
class Explorer(private val a: MainActivity) : LinearLayout(a) {
    private val pathView = TextView(a)
    private val list = ListView(a)
    private var dir: File = a.root
    private var items: List<File> = emptyList()

    init {
        orientation = VERTICAL
        val bar = LinearLayout(a)
        bar.addView(btn(a, "⬆️") { up() })
        bar.addView(btn(a, "📁＋") { create(true) })
        bar.addView(btn(a, "📄＋") { create(false) })
        bar.addView(btn(a, "🧹") { emptyTrash() })
        pathView.setTextColor(0xFFB9A7E8.toInt())
        pathView.setPadding(a.dp(12), 0, a.dp(12), a.dp(4))
        addView(bar)
        addView(pathView)
        addView(list, LayoutParams(MP, 0, 1f))
        list.setOnItemClickListener { _, _, i, _ -> open(items[i]) }
        list.setOnItemLongClickListener { _, _, i, _ -> menu(items[i]); true }
        go(a.root)
    }

    fun go(d: File) {
        dir = d
        d.mkdirs()
        items = (d.listFiles()?.toList() ?: emptyList())
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
        val names = items.map { (if (it.isDirectory) "📁  " else "📄  ") + it.name }
        list.adapter = object : ArrayAdapter<String>(a, android.R.layout.simple_list_item_1, names) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(Color.WHITE)
                return v
            }
        }
        pathView.text = "/" + d.absolutePath.removePrefix(a.root.absolutePath).trimStart('/')
    }

    private fun up() {
        if (dir.canonicalPath != a.root.canonicalPath) dir.parentFile?.let { go(it) }
    }

    private fun open(f: File) {
        if (f.isDirectory) go(f) else a.openNotepad(f)
    }

    private fun inTrash() = dir.canonicalPath == a.trash.canonicalPath

    private fun menu(f: File) {
        val trash = inTrash()
        val opts = if (trash) arrayOf("Восстановить", "Удалить навсегда") else arrayOf("Открыть", "В корзину")
        AlertDialog.Builder(a).setTitle(f.name).setItems(opts) { _, w ->
            if (trash) {
                if (w == 0) move(f, a.docs) else f.deleteRecursively()
            } else {
                if (w == 0) open(f) else move(f, a.trash)
            }
            go(dir)
        }.show()
    }

    private fun move(f: File, to: File) {
        to.mkdirs()
        var t = File(to, f.name)
        var n = 1
        while (t.exists()) {
            val ext = if (f.extension.isEmpty()) "" else "." + f.extension
            t = File(to, "${f.nameWithoutExtension} ($n)$ext")
            n++
        }
        f.renameTo(t)
    }

    private fun create(folder: Boolean) = a.ask(if (folder) "Имя папки" else "Имя файла") { n ->
        val f = File(dir, n)
        runCatching { if (folder) f.mkdirs() else f.createNewFile() }
        go(dir)
    }

    private fun emptyTrash() {
        if (!inTrash()) {
            a.toast("Откройте «Корзину»")
            return
        }
        AlertDialog.Builder(a).setTitle("Очистить корзину?")
            .setPositiveButton("Да") { _, _ ->
                dir.listFiles()?.forEach { it.deleteRecursively() }
                go(dir)
            }
            .setNegativeButton("Нет", null).show()
    }
}

// ───────────────────────── Блокнот ─────────────────────────
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

// ───────────────────────── Терминал ─────────────────────────
class Terminal(private val a: MainActivity) : LinearLayout(a) {
    private val out = TextView(a)
    private val input = EditText(a)
    private val scroll = ScrollView(a)
    private val rootC = a.root.canonicalPath
    private var cwd: File = a.root

    init {
        orientation = VERTICAL
        out.setTextColor(0xFF9EFFB5.toInt())
        out.typeface = Typeface.MONOSPACE
        out.textSize = 13f
        out.setPadding(a.dp(8), a.dp(8), a.dp(8), a.dp(8))
        scroll.addView(out)
        input.setTextColor(Color.WHITE)
        input.typeface = Typeface.MONOSPACE
        input.hint = "команда…"
        input.setHintTextColor(0x88FFFFFF.toInt())
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        input.imeOptions = EditorInfo.IME_ACTION_DONE
        input.setOnEditorActionListener { _, id, ev ->
            if (id == EditorInfo.IME_ACTION_DONE ||
                (ev != null && ev.action == KeyEvent.ACTION_DOWN && ev.keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                submit()
                true
            } else false
        }
        addView(scroll, LayoutParams(MP, 0, 1f))
        addView(input, LayoutParams(MP, WC))
        say("HerLinux 2.0 — терминал. Введите help.")
    }

    private fun rel(f: File): String = "/" + f.absolutePath.removePrefix(rootC).trimStart('/')
    private fun prompt() = "her@linux:" + rel(cwd) + "\$ "

    private fun res(n: String): File? {
        val f = File(cwd, n).canonicalFile
        return if (f.path == rootC || f.path.startsWith(rootC + File.separator)) f else null
    }

    private fun say(s: String) {
        out.append(s + "\n")
        if (out.length() > 20000) out.text = out.text.takeLast(15000)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun submit() {
        val line = input.text.toString().trim()
        input.setText("")
        say(prompt() + line)
        if (line.isNotEmpty()) exec(line)
    }

    private fun exec(line: String) {
        val p = line.split(Regex("\\s+"))
        val c = p[0]
        val args = p.drop(1)
        when (c) {
            "help" -> say("help ls cd pwd mkdir touch cat rm echo date whoami uname neofetch clear")
            "ls" -> {
                val d = if (args.isEmpty()) cwd else res(args[0])
                if (d == null || !d.isDirectory) {
                    say("ls: нет такой папки")
                } else {
                    say((d.listFiles()?.sortedBy { it.name } ?: emptyList())
                        .joinToString("  ") { it.name + (if (it.isDirectory) "/" else "") })
                }
            }
            "pwd" -> say(rel(cwd))
            "cd" -> {
                val t = if (args.isEmpty()) a.root else res(args[0])
                if (t != null && t.isDirectory) cwd = t else say("cd: нет такой папки")
            }
            "mkdir" -> args.forEach { n ->
                val f = res(n)
                if (f == null || !f.mkdirs()) say("mkdir: не удалось: $n")
            }
            "touch" -> args.forEach { n ->
                val f = res(n)
                val ok = f != null && runCatching { f.exists() || f.createNewFile() }.getOrDefault(false)
                if (!ok) say("touch: не удалось: $n")
            }
            "cat" -> args.forEach { n ->
                val f = res(n)
                if (f != null && f.isFile && f.length() < 200_000) say(f.readText()) else say("cat: нельзя открыть: $n")
            }
            "rm" -> args.forEach { n ->
                val f = res(n)
                if (f != null && f.path != rootC && f.exists()) f.deleteRecursively() else say("rm: не найдено: $n")
            }
            "echo" -> {
                val i = args.indexOf(">")
                if (i >= 0 && i + 1 < args.size) {
                    val f = res(args[i + 1])
                    if (f != null) {
                        runCatching { f.writeText(args.take(i).joinToString(" ") + "\n") }
                    } else {
                        say("echo: ошибка")
                    }
                } else {
                    say(args.joinToString(" "))
                }
            }
            "date" -> say(Date().toString())
            "whoami" -> say("her")
            "uname" -> say("HerLinux 2.0 (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
            "neofetch" -> say(
                "OS: HerLinux 2.0\nHost: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                    "Android: ${Build.VERSION.RELEASE}\nShell: her-sh"
            )
            "clear" -> out.text = ""
            else -> say("$c: команда не найдена")
        }
    }
}

// ───────────────────────── Браузер ─────────────────────────
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

// ───────────────────────── Калькулятор ─────────────────────────
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

// ───────────────────────── Рабочий стол ─────────────────────────
class MainActivity : Activity() {
    lateinit var root: File
    lateinit var docs: File
    lateinit var trash: File

    private lateinit var wallpaper: WallpaperView
    private lateinit var windowLayer: FrameLayout
    private lateinit var startMenu: LinearLayout
    private lateinit var startBtn: TextView
    private lateinit var clock: TextView
    private lateinit var battery: TextView
    private lateinit var overlay: TextView

    private val windows = linkedMapOf<String, AppWindow>()
    private val tbButtons = linkedMapOf<String, TextView>()
    private val handler = Handler(Looper.getMainLooper())
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private var themeIdx = 0
    private val tbH: Int get() = dp(52)

    private val tick = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 1000)
        }
    }

    fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
    fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    fun ask(title: String, cb: (String) -> Unit) {
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_TEXT
        AlertDialog.Builder(this).setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ ->
                val n = et.text.toString().trim()
                if (n.isNotEmpty() && !n.contains('/')) cb(n)
            }
            .setNegativeButton("Отмена", null).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val base = File(filesDir, "HerLinux")
        base.mkdirs()
        root = base.canonicalFile
        docs = File(root, "Документы")
        trash = File(root, "Корзина")
        listOf("Рабочий стол", "Документы", "Загрузки", "Корзина").forEach { File(root, it).mkdirs() }
        val hello = File(docs, "Привет.txt")
        if (!hello.exists()) hello.writeText("Добро пожаловать в HerLinux 2.0!\n")
        themeIdx = getSharedPreferences("her", MODE_PRIVATE).getInt("theme", 0)
            .coerceIn(0, PALETTES.size - 1)
        setContentView(buildUi())
        applyTheme(themeIdx)
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (overlay.visibility == View.VISIBLE) return
        if (startMenu.visibility == View.VISIBLE) {
            hideStart()
            return
        }
        for (i in windowLayer.childCount - 1 downTo 0) {
            val w = windowLayer.getChildAt(i) as AppWindow
            if (w.visibility == View.VISIBLE) {
                closeWin(w)
                return
            }
        }
    }

    // ── UI ──
    private fun buildUi(): View {
        val r = FrameLayout(this)
        r.setOnClickListener { hideStart() }
        wallpaper = WallpaperView(this)
        r.addView(wallpaper, FrameLayout.LayoutParams(MP, MP))

        val ic1 = FrameLayout.LayoutParams(WC, WC, Gravity.TOP or Gravity.START)
        ic1.setMargins(dp(12), dp(16), 0, 0)
        r.addView(desktopIcon("🖥️", "Компьютер") { openExplorer(root) }, ic1)

        val ic2 = FrameLayout.LayoutParams(WC, WC, Gravity.BOTTOM or Gravity.END)
        ic2.setMargins(0, 0, dp(12), tbH + dp(12))
        r.addView(desktopIcon("🗑️", "Корзина") { openExplorer(trash) }, ic2)

        windowLayer = FrameLayout(this)
        val wl = FrameLayout.LayoutParams(MP, MP)
        wl.bottomMargin = tbH
        r.addView(windowLayer, wl)

        startMenu = buildStart()
        startMenu.visibility = View.GONE
        val sw = min(dp(300), (resources.displayMetrics.widthPixels * 0.9f).toInt())
        val sl = FrameLayout.LayoutParams(sw, WC, Gravity.BOTTOM or Gravity.START)
        sl.setMargins(dp(8), 0, 0, tbH + dp(8))
        r.addView(startMenu, sl)

        r.addView(buildTaskbar(), FrameLayout.LayoutParams(MP, tbH, Gravity.BOTTOM))

        overlay = TextView(this).apply {
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            visibility = View.GONE
            isClickable = true
        }
        r.addView(overlay, FrameLayout.LayoutParams(MP, MP))
        return r
    }

    private fun desktopIcon(emoji: String, label: String, onClick: () -> Unit): View {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.gravity = Gravity.CENTER_HORIZONTAL
        l.setPadding(dp(8), dp(8), dp(8), dp(8))
        l.addView(TextView(this).apply {
            text = emoji
            textSize = 34f
            gravity = Gravity.CENTER
        })
        l.addView(TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 13f
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
        })
        l.setOnClickListener {
            hideStart()
            onClick()
        }
        return l
    }

    private fun buildTaskbar(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(0xEE120A2A.toInt())
        bar.setPadding(dp(6), 0, dp(6), 0)
        bar.isClickable = true

        startBtn = TextView(this).apply {
            text = "H"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setOnClickListener { toggleStart() }
        }
        bar.addView(startBtn, LinearLayout.LayoutParams(dp(40), dp(40)))

        val div = View(this)
        div.setBackgroundColor(0x33FFFFFF)
        val dl = LinearLayout.LayoutParams(dp(1), dp(28))
        dl.setMargins(dp(8), 0, dp(8), 0)
        bar.addView(div, dl)

        for ((key, label) in listOf("explorer" to "📁", "browser" to "🌐", "terminal" to ">_")) {
            val b = TextView(this).apply {
                text = label
                textSize = 20f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setOnClickListener {
                    hideStart()
                    toggle(key)
                }
            }
            tbButtons[key] = b
            val lp = LinearLayout.LayoutParams(dp(40), dp(40))
            lp.marginEnd = dp(2)
            bar.addView(b, lp)
        }

        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        val vol = TextView(this).apply {
            text = "🔊"
            textSize = 16f
            gravity = Gravity.CENTER
            setOnClickListener {
                (getSystemService(Context.AUDIO_SERVICE) as AudioManager).adjustStreamVolume(
                    AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI
                )
            }
        }
        bar.addView(vol, LinearLayout.LayoutParams(dp(32), dp(40)))

        val scr = TextView(this).apply {
            text = "🖥️"
            textSize = 16f
            gravity = Gravity.CENTER
            setOnClickListener { displaySettings() }
        }
        bar.addView(scr, LinearLayout.LayoutParams(dp(32), dp(40)))

        battery = TextView(this).apply {
            text = "🔋"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        bar.addView(battery, LinearLayout.LayoutParams(WC, dp(40)))

        clock = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }
        bar.addView(clock, LinearLayout.LayoutParams(WC, dp(40)))
        return bar
    }

    private fun buildStart(): LinearLayout {
        val m = LinearLayout(this)
        m.orientation = LinearLayout.VERTICAL
        m.background = rounded(0xF2160D30.toInt(), dp(12).toFloat(), 2, 0x557C3AED)
        m.setPadding(dp(12), dp(12), dp(12), dp(12))
        m.isClickable = true

        m.addView(TextView(this).apply {
            text = "▦  Пуск"
            setTextColor(Color.WHITE)
            textSize = 22f
            setPadding(dp(4), dp(4), dp(4), dp(10))
        })

        fun item(icon: String, label: String, action: () -> Unit) {
            m.addView(TextView(this).apply {
                text = "$icon   $label"
                setTextColor(Color.WHITE)
                textSize = 17f
                setPadding(dp(8), dp(11), dp(8), dp(11))
                setOnClickListener {
                    hideStart()
                    action()
                }
            })
        }
        item("⚙️", "Настройки") { open("settings") }
        item("⌨️", "Командной строки") { open("terminal") }
        item("🖥️", "Параметры экрана") { displaySettings() }
        item("📁", "Проводник") { open("explorer") }
        item("🌐", "Браузер") { open("browser") }
        item("📝", "Блокнот") { open("notepad") }
        item("🧮", "Калькулятор") { open("calc") }

        val line = View(this)
        line.setBackgroundColor(0x33FFFFFF)
        val ll = LinearLayout.LayoutParams(MP, dp(1))
        ll.setMargins(0, dp(24), 0, dp(8))
        m.addView(line, ll)

        fun foot(icon: String, label: String, restart: Boolean) {
            m.addView(TextView(this).apply {
                text = "$label   $icon"
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.END
                setPadding(dp(8), dp(9), dp(8), dp(9))
                setOnClickListener { power(restart) }
            })
        }
        foot("⏻", "Завершить работу", false)
        foot("⟳", "Перезапуск", true)
        return m
    }

    private fun toggleStart() {
        startMenu.visibility = if (startMenu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun hideStart() {
        startMenu.visibility = View.GONE
    }

    private fun power(restart: Boolean) {
        hideStart()
        overlay.text = if (restart) "Перезапуск…" else "Завершение работы…"
        overlay.visibility = View.VISIBLE
        handler.postDelayed({ if (restart) recreate() else finishAffinity() }, 1200)
    }

    private fun displaySettings() {
        runCatching { startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
    }

    private fun updateStatus() {
        val now = Date()
        clock.text = timeFmt.format(now) + "\n" + dateFmt.format(now)
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (i != null) {
            val lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (lv >= 0 && sc > 0) battery.text = "🔋${lv * 100 / sc}%"
        }
    }

    private fun applyTheme(i: Int) {
        themeIdx = i
        val p = PALETTES[i]
        getSharedPreferences("her", MODE_PRIVATE).edit().putInt("theme", i).apply()
        wallpaper.setColors(p.top, p.bottom, p.accent)
        startBtn.background = rounded(p.accent, dp(10).toFloat())
        windows.values.forEach { it.setAccent(p.accent) }
    }

    // ── Окна и приложения ──
    private fun refreshTaskbar() {
        for ((k, b) in tbButtons) {
            val vis = windows[k]?.visibility == View.VISIBLE
            b.background = if (vis) rounded(0x447C3AED, dp(8).toFloat()) else null
        }
    }

    private fun closeWin(w: AppWindow) {
        windowLayer.removeView(w)
        windows.remove(w.key)
        (w.content as? Browser)?.release()
        refreshTaskbar()
    }

    fun showApp(key: String, title: String, small: Boolean = false, build: () -> View): AppWindow {
        val existing = windows[key]
        if (existing != null) {
            existing.visibility = View.VISIBLE
            existing.bringToFront()
            refreshTaskbar()
            return existing
        }
        val dm = resources.displayMetrics
        val w = min(dp(if (small) 300 else 560), (dm.widthPixels * 0.96f).toInt())
        val h = min(dp(if (small) 440 else 460), ((dm.heightPixels - tbH) * 0.8f).toInt())
        val win = AppWindow(
            this, key, title, build(), PALETTES[themeIdx].accent,
            { closeWin(it) },
            { f -> f.bringToFront(); hideStart() },
            { it.visibility = View.GONE; refreshTaskbar() }
        )
        windowLayer.addView(win, FrameLayout.LayoutParams(w, h))
        val off = (windows.size % 5) * dp(26)
        val maxX = (dm.widthPixels - w).coerceAtLeast(0)
        win.x = min(dp(8) + off, maxX).toFloat()
        win.y = (dp(16) + off).toFloat()
        windows[key] = win
        win.bringToFront()
        refreshTaskbar()
        return win
    }

    private fun toggle(key: String) {
        val w = windows[key]
        if (w != null && w.visibility == View.VISIBLE) {
            w.visibility = View.GONE
            refreshTaskbar()
        } else {
            open(key)
        }
    }

    private fun open(key: String) {
        when (key) {
            "explorer" -> openExplorer(root)
            "browser" -> showApp("browser", "Браузер") { Browser(this) }
            "terminal" -> showApp("terminal", "Командная строка") { Terminal(this) }
            "notepad" -> openNotepad(null)
            "calc" -> showApp("calc", "Калькулятор", true) { Calc(this) }
            "settings" -> showApp("settings", "Настройки") { buildSettings() }
        }
    }

    private fun openExplorer(dir: File) {
        val w = showApp("explorer", "Проводник") { Explorer(this) }
        (w.content as Explorer).go(dir)
    }

    fun openNotepad(f: File?) {
        val w = showApp("notepad", "Блокнот") { Notepad(this) }
        if (f != null) (w.content as Notepad).load(f)
    }

    private fun buildSettings(): View {
        val sv = ScrollView(this)
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(dp(16), dp(8), dp(16), dp(16))
        sv.addView(l)

        fun title(t: String) = TextView(this).apply {
            text = t
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(12), 0, dp(4))
        }

        l.addView(title("Оформление"))
        PALETTES.forEachIndexed { i, p ->
            l.addView(TextView(this).apply {
                text = "●  ${p.name}"
                setTextColor(p.accent)
                textSize = 17f
                setPadding(dp(8), dp(12), dp(8), dp(12))
                setOnClickListener { applyTheme(i) }
            })
        }
        l.addView(title("Экран"))
        l.addView(btn(this, "Параметры экрана Android") { displaySettings() })
        l.addView(title("О системе"))
        l.addView(TextView(this).apply {
            text = "HerLinux 2.0\nAndroid ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                "${Build.MANUFACTURER} ${Build.MODEL}"
            setTextColor(0xFFB9A7E8.toInt())
            textSize = 15f
        })
        return sv
    }
}
