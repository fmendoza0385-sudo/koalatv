package com.koalatv.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChannelAdapter(
    private var items: List<Channel>,
    private val onSelect: (Int) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    private var current = -1

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val number: TextView = view.findViewById(R.id.number)
        val name: TextView = view.findViewById(R.id.name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ch = items[position]
        holder.number.text = ch.number.toString()
        holder.name.text = ch.name
        holder.itemView.isActivated = position == current
        holder.itemView.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onSelect(p)
        }
    }

    override fun getItemCount(): Int = items.size

    fun setCurrent(position: Int) {
        val old = current
        current = position
        if (old in items.indices) notifyItemChanged(old)
        if (current in items.indices) notifyItemChanged(current)
    }

    fun submit(newItems: List<Channel>, currentPos: Int) {
        items = newItems
        current = currentPos
        notifyDataSetChanged()
    }
}
