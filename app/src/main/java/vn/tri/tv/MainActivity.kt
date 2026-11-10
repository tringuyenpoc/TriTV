package vn.tri.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var rowsView: RecyclerView
    private val adapter = RowAdapter { items, index -> play(items, index) }
    private var query: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        rowsView = findViewById(R.id.rows)
        rowsView.layoutManager = LinearLayoutManager(this)
        rowsView.adapter = adapter

        findViewById<TextView>(R.id.btnPlaylist).setOnClickListener { openSetup() }
        findViewById<TextView>(R.id.btnReload).setOnClickListener { refresh() }
        findViewById<TextView>(R.id.btnSearch).setOnClickListener { askSearch() }

        if (Store.playlistUrl(this).isBlank()) { openSetup(); return }
        if (Store.channels.isEmpty()) {
            io.execute {
                val cached = Store.loadCached(this)
                main.post {
                    Store.channels = cached
                    render()
                    refresh()
                }
            }
        } else render()
    }

    override fun onResume() {
        super.onResume()
        if (Store.channels.isNotEmpty()) render() // cập nhật hàng "Xem gần đây"
    }

    private fun openSetup() = startActivityForResult(Intent(this, SetupActivity::class.java), 1)

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) refresh()
        else if (Store.playlistUrl(this).isBlank()) finish()
    }

    private fun refresh() {
        val url = Store.playlistUrl(this)
        if (url.isBlank()) return
        status.text = "Đang tải playlist…"
        io.execute {
            val result = runCatching { Store.download(this, url) }
            main.post {
                result.onSuccess {
                    Store.channels = it
                    status.text = "${it.size} kênh/phim"
                    render()
                }.onFailure {
                    status.text = if (Store.channels.isEmpty()) "Lỗi tải playlist: ${it.message}"
                    else "Không cập nhật được, đang dùng bản lưu (${it.message})"
                }
            }
        }
    }

    private fun render() {
        val all = Store.channels
        val rows = ArrayList<Row>()
        if (query.isNotBlank()) {
            val hits = all.filter { it.name.contains(query, ignoreCase = true) }
            rows.add(Row("Kết quả cho \"$query\"", hits))
        } else {
            val recent = Store.recents(this)
            if (recent.isNotEmpty()) rows.add(Row("Xem gần đây", recent))
        }
        all.groupBy { it.group }.forEach { (g, items) -> rows.add(Row(g, items)) }
        adapter.rows = rows
    }

    private fun askSearch() {
        val input = EditText(this).apply {
            setText(query); setSingleLine(); imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        AlertDialog.Builder(this)
            .setTitle("Tìm kênh / phim")
            .setView(input)
            .setPositiveButton("Tìm") { _, _ -> query = input.text.toString().trim(); render() }
            .setNeutralButton("Xóa tìm") { _, _ -> query = ""; render() }
            .show()
    }

    private fun play(items: List<Channel>, index: Int) {
        if (index !in items.indices) return
        PlayerActivity.queue = items
        startActivity(Intent(this, PlayerActivity::class.java).putExtra("index", index))
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }
}
