package vn.tri.tv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load

typealias OnPick = (items: List<Channel>, index: Int) -> Unit

class RowAdapter(private val onPick: OnPick) : RecyclerView.Adapter<RowAdapter.VH>() {
    var rows: List<Row> = emptyList()
        set(v) { field = v; notifyDataSetChanged() }

    private val pool = RecyclerView.RecycledViewPool()

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.rowTitle)
        val list: RecyclerView = v.findViewById(R.id.rowList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false)
        return VH(v).also {
            it.list.layoutManager = LinearLayoutManager(parent.context, LinearLayoutManager.HORIZONTAL, false)
            it.list.setRecycledViewPool(pool)
            it.list.itemAnimator = null
        }
    }

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val row = rows[pos]
        h.title.text = "${row.title}  ·  ${row.items.size}"
        h.list.adapter = CardAdapter(row.items, onPick)
    }
}

class CardAdapter(private val items: List<Channel>, private val onPick: OnPick) :
    RecyclerView.Adapter<CardAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val logo: ImageView = v.findViewById(R.id.logo)
        val initials: TextView = v.findViewById(R.id.initials)
        val name: TextView = v.findViewById(R.id.name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        v.setOnFocusChangeListener { view, has ->
            val s = if (has) 1.08f else 1f
            view.animate().scaleX(s).scaleY(s).setDuration(120).start()
            view.findViewById<TextView>(R.id.name).isSelected = has // chạy chữ khi tên dài
        }
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val ch = items[pos]
        h.name.text = ch.name
        h.initials.text = ch.name.trim().take(2).uppercase()
        if (ch.logo.isBlank()) {
            h.logo.dispose()
            h.logo.setImageDrawable(null)
            h.initials.visibility = View.VISIBLE
        } else {
            h.initials.visibility = View.GONE
            h.logo.load(ch.logo) {
                crossfade(true)
                listener(onError = { _, _ -> h.initials.visibility = View.VISIBLE })
            }
        }
        h.itemView.setOnClickListener { onPick(items, h.bindingAdapterPosition) }
    }
}
