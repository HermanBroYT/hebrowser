package ru.herlinux

import android.app.AlertDialog
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Row(
    val title: String,
    val sub: String? = null,
    val frac: Float? = null,
    val target: File? = null,
    val needsAll: Boolean = false
)

class Explorer(private val a: MainActivity) : LinearLayout(a) {
    private val pathView = TextView(a)
    private val list = ListView(a)
    private var dir: File? = null
    private var rows: List<Row> = emptyList()
    private val dateFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    init {
        orientation = VERTICAL
        val bar = LinearLayout(a)
        bar.addView(btn(a, "⬆️") { up() })
        bar.addView(btn(a, "🖥️") { go(null) })
        bar.addView(btn(a, "📁＋") { create(true) })
        bar.addView(btn(a, "📄＋") { create(false) })
        bar.addView(btn(a, "🧹") { emptyTrash() })
        pathView.setTextColor(0xFFB9A7E8.toInt())
        pathView.textSize = 12f
        pathView.setPadding(a.dp(12), 0, a.dp(12), a.dp(4))
        addView(bar)
        addView(pathView)
        addView(list, LayoutParams(MP, 0, 1f))
        list.setOnItemClickListener { _, _, i, _ -> onRow(rows[i]) }
        list.setOnItemLongClickListener { _, _, i, _ ->
            val t = rows[i].target
            if (t != null && dir != null) menu(t)
            true
        }
        go(null, false)
    }

    fun go(d: File?, animate: Boolean = true) {
        dir = d
        if (d != null && !d.exists()) runCatching { d.mkdirs() }
        rows = if (d == null) computerRows() else listRows(d)
        list.adapter = Adapter()
        pathView.text = if (d == null) "Этот компьютер" else a.pretty(d)
        if (animate && a.animOn) {
            list.animate().cancel()
            list.alpha = 0f
            list.translationX = a.dp(30).toFloat()
            list.animate().alpha(1f).translationX(0f).setDuration(a.dur(180)).start()
        }
    }

    private fun computerRows(): List<Row> {
        val r = ArrayList<Row>()
        fun drive(title: String, f: File, needs: Boolean) {
            val d = diskInfo(f)
            if (d != null && d.first > 0) {
                val used = d.first - d.second
                r.add(
                    Row(
                        title,
                        "Занято ${fmtSize(used)} из ${fmtSize(d.first)} · свободно ${fmtSize(d.second)}",
                        used.toFloat() / d.first, f, needs
                    )
                )
            } else {
                r.add(Row(title, null, null, f, needs))
            }
        }
        drive("💽  Диск HerLinux", a.root, false)
        drive("📱  Внутренняя память", a.extRoot, true)
        a.sdRoots().forEach { drive("💾  Карта памяти", it, true) }
        r.add(Row("🗑️  Корзина", "Элементов: ${a.trash.listFiles()?.size ?: 0}", null, a.trash, false))
        r.add(Row("⬇️  Загрузки", null, null, File(a.extRoot, "Download"), true))
        r.add(Row("📷  Камера", null, null, File(a.extRoot, "DCIM"), true))
        r.add(Row("🎵  Музыка", null, null, File(a.extRoot, "Music"), true))
        return r
    }

    private fun listRows(d: File): List<Row> {
        val files = d.listFiles()
            ?: return listOf(Row("⛔  Нет доступа к этой папке", "Выдайте доступ ко всем файлам: Настройки → Память"))
        if (files.isEmpty()) return listOf(Row("(папка пуста)"))
        return files.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })).map {
            val date = dateFmt.format(Date(it.lastModified()))
            Row(
                icon(it) + "  " + it.name,
                if (it.isDirectory) "Папка · $date" else fmtSize(it.length()) + " · " + date,
                null, it
            )
        }
    }

    private fun icon(f: File): String {
        if (f.isDirectory) return "📁"
        return when (f.extension.lowercase()) {
            "apk" -> "📦"
            "exe", "msi", "bat" -> "🪟"
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "🖼️"
            "mp3", "wav", "ogg", "flac", "m4a" -> "🎵"
            "mp4", "mkv", "avi", "webm", "3gp" -> "🎬"
            "pdf" -> "📕"
            "zip", "rar", "7z", "tar", "gz" -> "🗜️"
            else -> "📄"
        }
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val r = rows[position]
            val l = LinearLayout(a)
            l.orientation = LinearLayout.VERTICAL
            l.setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(10))
            l.addView(TextView(a).apply {
                text = r.title
                setTextColor(Color.WHITE)
                textSize = 16f
            })
            val fr = r.frac
            if (fr != null) {
                val lp = LinearLayout.LayoutParams(MP, a.dp(8))
                lp.topMargin = a.dp(6)
                lp.bottomMargin = a.dp(2)
                l.addView(usageBar(a, fr, PALETTES[a.themeIdx].accent, a.dp(8)), lp)
            }
            val s = r.sub
            if (s != null) {
                l.addView(TextView(a).apply {
                    text = s
                    setTextColor(0xFFB9A7E8.toInt())
                    textSize = 12f
                })
            }
            return l
        }
    }

    private fun onRow(r: Row) {
        val t = r.target ?: return
        if (r.needsAll && !a.hasAllFiles()) {
            a.askAllFiles()
            return
        }
        if (dir != null && !t.isDirectory) a.openFile(t) else go(t)
    }

    private fun up() {
        val d = dir ?: return
        if (a.isDriveRoot(d)) go(null) else d.parentFile?.let { go(it) }
    }

    private fun inTrash(): Boolean {
        val d = dir ?: return false
        return d.canonicalPath == a.trash.canonicalPath
    }

    private fun menu(f: File) {
        val trash = inTrash()
        val opts = if (trash) arrayOf("Восстановить", "Удалить навсегда", "Свойства")
        else arrayOf("Открыть", "В корзину", "Свойства")
        AlertDialog.Builder(a).setTitle(f.name).setItems(opts) { _, w ->
            val d = dir
            when (w) {
                0 -> if (trash) move(f, a.docs) else if (f.isDirectory) go(f) else a.openFile(f)
                1 -> if (trash) f.deleteRecursively() else move(f, a.trash)
                else -> props(f)
            }
            if (w != 2 && !(w == 0 && !trash)) go(d)
        }.show()
    }

    private fun props(f: File) {
        val size = if (f.isDirectory) "Папка (элементов: ${f.listFiles()?.size ?: 0})" else fmtSize(f.length())
        AlertDialog.Builder(a).setTitle("Свойства")
            .setMessage("Путь: ${f.path}\nРазмер: $size\nИзменён: ${dateFmt.format(Date(f.lastModified()))}")
            .setPositiveButton("OK", null).show()
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
        if (!f.renameTo(t)) {
            runCatching { if (f.copyRecursively(t, false)) f.deleteRecursively() }
                .onFailure { a.toast("Не удалось переместить") }
        }
    }

    private fun create(folder: Boolean) {
        val d = dir
        if (d == null) {
            a.toast("Откройте папку")
            return
        }
        a.ask(if (folder) "Имя папки" else "Имя файла") { n ->
            val f = File(d, n)
            runCatching { if (folder) f.mkdirs() else f.createNewFile() }
            go(d, false)
        }
    }

    private fun emptyTrash() {
        if (!inTrash()) {
            a.toast("Откройте «Корзину»")
            return
        }
        AlertDialog.Builder(a).setTitle("Очистить корзину?")
            .setPositiveButton("Да") { _, _ ->
                a.trash.listFiles()?.forEach { it.deleteRecursively() }
                go(a.trash, false)
            }
            .setNegativeButton("Нет", null).show()
    }
}
