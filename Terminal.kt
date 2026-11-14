package ru.herlinux

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Terminal(private val a: MainActivity) : LinearLayout(a) {
    private val out = TextView(a)
    private val input = EditText(a)
    private val scroll = ScrollView(a)
    private var cwd: File = a.root
    private val hist = ArrayList<String>()
    private var hi = 0
    private val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

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
        val row = LinearLayout(a)
        row.addView(input, LayoutParams(0, WC, 1f))
        row.addView(btn(a, "▲") { histMove(-1) })
        row.addView(btn(a, "▼") { histMove(1) })
        addView(scroll, LayoutParams(MP, 0, 1f))
        addView(row, LayoutParams(MP, WC))
        say("HerLinux [версия 2.1]\nКоманды Linux и Windows. Введите help.")
    }

    private fun histMove(d: Int) {
        if (hist.isEmpty()) return
        hi = (hi + d).coerceIn(0, hist.size)
        input.setText(if (hi < hist.size) hist[hi] else "")
        input.setSelection(input.text.length)
    }

    private fun pretty(f: File): String {
        val p = f.path
        return when {
            p == a.root.path -> "~"
            p.startsWith(a.root.path + "/") -> "~" + p.removePrefix(a.root.path)
            else -> p
        }
    }

    private fun prompt() = "her@linux:" + pretty(cwd) + "\$ "

    private fun say(s: String) {
        out.append(s + "\n")
        if (out.length() > 20000) out.text = out.text.takeLast(15000)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun submit() {
        val line = input.text.toString().trim()
        input.setText("")
        say(prompt() + line)
        if (line.isEmpty()) return
        hist.add(line)
        hi = hist.size
        try {
            exec(line)
        } catch (e: Exception) {
            say("Ошибка: ${e.message}")
        }
    }

    private fun tokens(s: String): List<String> {
        val r = ArrayList<String>()
        val sb = StringBuilder()
        var q = false
        var has = false
        for (ch in s) {
            when {
                ch == '"' -> {
                    q = !q
                    has = true
                }
                ch.isWhitespace() && !q -> {
                    if (sb.isNotEmpty() || has) {
                        r.add(sb.toString())
                        sb.setLength(0)
                        has = false
                    }
                }
                else -> sb.append(ch)
            }
        }
        if (sb.isNotEmpty() || has) r.add(sb.toString())
        return r
    }

    private fun res(n0: String): File {
        var n = n0.replace('\\', '/')
        val base = when {
            Regex("^[cC]:").containsMatchIn(n) -> { n = n.drop(2); a.root }
            Regex("^[dD]:").containsMatchIn(n) -> { n = n.drop(2); a.extRoot }
            n == "~" || n.startsWith("~/") -> { n = n.drop(1); a.root }
            n.startsWith("/") -> File("/")
            else -> cwd
        }
        return runCatching { File(base, n).canonicalFile }.getOrDefault(File(base, n))
    }

    private fun isProtected(f: File) = f.path == "/" || f.path == a.root.path || f.path == a.extRoot.path

    private fun isFlag(s: String) =
        (s.startsWith("-") && s.length > 1) || (s.length == 2 && s[0] == '/' && s[1].isLetter())

    @Suppress("DEPRECATION")
    private fun exec(line: String) {
        val tk = tokens(line).toMutableList()
        if (tk.isEmpty()) return
        if (tk[0] == "sudo" && tk.size > 1) tk.removeAt(0)
        val c = tk[0].lowercase()
        val args = tk.drop(1)
        val files = args.filter { !isFlag(it) }
        val flags = args.filter { isFlag(it) }.joinToString("").lowercase()
        val rec = flags.contains('r') || flags.contains('s')

        when (c) {
            "help", "?" -> say(
                "Файлы:  ls/dir  cd  pwd  mkdir/md  rmdir/rd  touch  cat/type  rm/del  cp/copy  mv/move/ren  tree  head  wc  grep\n" +
                    "Текст:  echo [текст] [> файл]  [>> файл]\n" +
                    "Система: date  time  whoami  hostname  uname  ver  neofetch  systeminfo  df  free  uptime  ps/tasklist\n" +
                    "Сеть:   ping  ipconfig/ifconfig\n" +
                    "Прочее: start/open <файл>  install <файл.apk>  apps  history  clear/cls  exit\n" +
                    "Диски:  C: — диск HerLinux, D: — внутренняя память"
            )
            "ls", "dir" -> list(files.firstOrNull(), c == "dir" || flags.contains('l'))
            "cd", "chdir" -> cd(files.firstOrNull() ?: "~")
            "cd.." -> cd("..")
            "pwd" -> say(cwd.path)
            "mkdir", "md" -> files.forEach {
                val f = res(it)
                if (f.exists()) say("уже существует: $it") else if (!f.mkdirs()) say("не удалось создать: $it")
            }
            "rmdir", "rd" -> files.forEach {
                val f = res(it)
                when {
                    isProtected(f) -> say("запрещено: $it")
                    !f.isDirectory -> say("не папка: $it")
                    rec -> f.deleteRecursively()
                    !f.delete() -> say("папка не пуста (rmdir -r / rd /s): $it")
                }
            }
            "touch" -> files.forEach {
                val f = res(it)
                val ok = runCatching { f.exists() || f.createNewFile() }.getOrDefault(false)
                if (!ok) say("не удалось создать: $it")
            }
            "cat", "type" -> files.forEach {
                val f = res(it)
                if (f.isFile && f.length() < 200_000) say(f.readText()) else say("нельзя открыть: $it")
            }
            "rm", "del", "erase" -> files.forEach {
                val f = res(it)
                when {
                    isProtected(f) -> say("запрещено: $it")
                    !f.exists() -> say("не найдено: $it")
                    f.isDirectory && !rec -> say("это папка (rm -r): $it")
                    else -> f.deleteRecursively()
                }
            }
            "cp", "copy", "xcopy" -> {
                if (files.size < 2) say("использование: cp <откуда> <куда>") else {
                    val src = res(files[0])
                    val dst0 = res(files[1])
                    val dst = if (dst0.isDirectory) File(dst0, src.name) else dst0
                    if (src.isDirectory) src.copyRecursively(dst, true) else src.copyTo(dst, true)
                }
            }
            "mv", "move", "ren", "rename" -> {
                if (files.size < 2) say("использование: mv <откуда> <куда>") else {
                    val src = res(files[0])
                    val dst0 = res(files[1])
                    val dst = if (dst0.isDirectory) File(dst0, src.name) else dst0
                    if (isProtected(src)) say("запрещено")
                    else if (!src.renameTo(dst)) {
                        if (src.isDirectory) src.copyRecursively(dst, true) else src.copyTo(dst, true)
                        src.deleteRecursively()
                    }
                }
            }
            "echo" -> echo(args)
            "head" -> files.firstOrNull()?.let { say(res(it).readLines().take(10).joinToString("\n")) }
            "wc" -> files.firstOrNull()?.let {
                val t = res(it).readText()
                say("${t.lines().size} строк, ${t.split(Regex("\\s+")).count { w -> w.isNotEmpty() }} слов, ${t.length} символов")
            }
            "grep" -> {
                if (files.size < 2) say("использование: grep <текст> <файл>")
                else say(res(files[1]).readLines().filter { it.contains(files[0], true) }.joinToString("\n"))
            }
            "tree" -> tree(res(files.firstOrNull() ?: "."), "", 0, IntArray(1))
            "date" -> say(Date().toString())
            "time" -> say(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))
            "whoami" -> say("her")
            "hostname" -> say("herlinux")
            "ver" -> say("HerLinux [версия 2.1]")
            "uname" -> say(
                if (flags.contains('a')) "HerLinux 2.1 herlinux Android ${Build.VERSION.RELEASE} ${Build.SUPPORTED_ABIS.firstOrNull() ?: ""}"
                else "HerLinux"
            )
            "neofetch", "systeminfo" -> info()
            "df" -> df()
            "free" -> {
                val r = a.ramInfo()
                say("ОЗУ  всего ${fmtSize(r.first)}  занято ${fmtSize(r.first - r.second)}  свободно ${fmtSize(r.second)}")
            }
            "uptime" -> {
                val s = SystemClock.elapsedRealtime() / 1000
                say("работает: ${s / 3600} ч ${s % 3600 / 60} мин")
            }
            "ps", "tasklist" -> say(a.runningApps().joinToString("\n").ifEmpty { "(нет окон)" })
            "clear", "cls" -> out.text = ""
            "ping" -> ping(files.firstOrNull())
            "ipconfig", "ifconfig", "ip" -> net()
            "start", "open", "xdg-open" -> {
                val n = files.firstOrNull()
                if (n == null) say("укажите файл") else {
                    val f = res(n)
                    if (!f.exists()) say("не найдено: $n")
                    else if (f.isDirectory) a.openExplorer(f) else a.openFile(f)
                }
            }
            "install" -> {
                val n = files.firstOrNull()
                if (n == null) say("использование: install <файл.apk>") else a.installApk(res(n))
            }
            "apps" -> {
                val pm = a.packageManager
                val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                say(pm.queryIntentActivities(i, 0).map { it.loadLabel(pm).toString() }.sorted().joinToString("\n"))
            }
            "history" -> hist.forEachIndexed { i, s -> say("${i + 1}  $s") }
            "exit", "logout" -> a.closeKey("terminal")
            else -> {
                if (c.endsWith(".exe")) say("Windows-программы (.exe) на Android не запускаются. Для .apk: install файл.apk")
                else say("$c: команда не найдена. Введите help.")
            }
        }
    }

    private fun list(arg: String?, long: Boolean) {
        val d = if (arg == null) cwd else res(arg)
        if (!d.exists()) {
            say("не найдено: ${arg ?: ""}")
            return
        }
        if (d.isFile) {
            say(d.name)
            return
        }
        val fs = d.listFiles()
        if (fs == null) {
            say("нет доступа (выдайте доступ ко всем файлам: Настройки → Память)")
            return
        }
        val sorted = fs.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
        if (long) {
            sorted.forEach {
                val sz = if (it.isDirectory) "<DIR>" else fmtSize(it.length())
                say(df.format(Date(it.lastModified())) + "  " + sz.padStart(10) + "  " + it.name)
            }
        } else {
            say(sorted.joinToString("  ") { it.name + (if (it.isDirectory) "/" else "") }.ifEmpty { "(пусто)" })
        }
    }

    private fun cd(p: String) {
        val t = res(p)
        if (t.isDirectory) cwd = t else say("cd: нет такой папки: $p")
    }

    private fun echo(words: List<String>) {
        val i = words.indexOfFirst { it == ">" || it == ">>" }
        if (i < 0) {
            say(words.joinToString(" "))
            return
        }
        if (i + 1 >= words.size) {
            say("echo: не указан файл")
            return
        }
        val f = res(words[i + 1])
        val text = words.take(i).joinToString(" ") + "\n"
        if (words[i] == ">>") f.appendText(text) else f.writeText(text)
    }

    private fun tree(d: File, prefix: String, depth: Int, count: IntArray) {
        if (depth > 3 || count[0] > 200) return
        val fs = d.listFiles()?.sortedBy { it.name.lowercase() } ?: return
        for (f in fs) {
            if (count[0]++ > 200) return
            say(prefix + (if (f.isDirectory) "📁 " else "📄 ") + f.name)
            if (f.isDirectory) tree(f, "$prefix   ", depth + 1, count)
        }
    }

    private fun df() {
        for ((n, f) in listOf("HerLinux (C:)" to a.root, "Память (D:)" to a.extRoot)) {
            val d = diskInfo(f)
            say(
                if (d == null) "$n: нет данных"
                else "$n  всего ${fmtSize(d.first)}  занято ${fmtSize(d.first - d.second)}  свободно ${fmtSize(d.second)}"
            )
        }
    }

    private fun info() {
        val r = a.ramInfo()
        val d = diskInfo(a.extRoot)
        say(
            "ОС: HerLinux 2.1\n" +
                "Устройство: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                "Процессор: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}\n" +
                "ОЗУ: ${fmtSize(r.first)} (свободно ${fmtSize(r.second)})\n" +
                "Память: ${if (d == null) "?" else fmtSize(d.first) + " (свободно " + fmtSize(d.second) + ")"}"
        )
    }

    private fun ping(host: String?) {
        if (host == null) {
            say("ping: укажите адрес")
            return
        }
        say("Обмен пакетами с $host:")
        Thread {
            repeat(4) {
                val t0 = System.nanoTime()
                val ok = runCatching { InetAddress.getByName(host).isReachable(2000) }.getOrDefault(false)
                val ms = (System.nanoTime() - t0) / 1_000_000
                post { say(if (ok) "Ответ от $host: время=$ms мс" else "Превышен интервал ожидания") }
                Thread.sleep(500)
            }
        }.start()
    }

    private fun net() {
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
                if (ni.isUp && !ni.isLoopback) {
                    say(ni.name + ":")
                    ni.interfaceAddresses.forEach { say("   " + (it.address.hostAddress ?: "")) }
                }
            }
        }.onFailure { say("нет данных о сети") }
    }
}
