package com.digihori.marketpanel.ui.settings

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.digihori.marketpanel.R
import com.digihori.marketpanel.data.settings.WatchInstrument
import java.util.Collections

class InstrumentAdapter(
    private val onEdit: (WatchInstrument) -> Unit,
    private val onEnabledChanged: (WatchInstrument, Boolean) -> Unit,
    private val onOrderChanged: (List<WatchInstrument>) -> Unit,
) : RecyclerView.Adapter<InstrumentAdapter.Holder>() {
    private val items = mutableListOf<WatchInstrument>()
    private var touchHelper: ItemTouchHelper? = null

    fun attachTo(recyclerView: RecyclerView) {
        touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0,
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                source: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = source.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from !in items.indices || to !in items.indices) return false
                Collections.swap(items, from, to)
                notifyItemMoved(from, to)
                notifyItemRangeChanged(minOf(from, to), kotlin.math.abs(from - to) + 1)
                onOrderChanged(items.toList())
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        }).also { it.attachToRecyclerView(recyclerView) }
    }

    fun submit(newItems: List<WatchInstrument>) {
        items.clear()
        items += newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_instrument, parent, false),
    )

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.order.text = (position + 1).toString()
        holder.name.text = item.displayName
        holder.detail.text = "${item.symbol}  •  ${item.assetType.label}  •  ${item.dataSource.label}"
        holder.enabled.setOnCheckedChangeListener(null)
        holder.enabled.isChecked = item.enabled
        holder.enabled.setOnCheckedChangeListener { _, checked -> onEnabledChanged(item, checked) }
        holder.editArea.setOnClickListener { onEdit(item) }
        holder.itemView.setOnClickListener { onEdit(item) }
        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) touchHelper?.startDrag(holder)
            true
        }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val dragHandle: View = view.findViewById(R.id.dragHandle)
        val order: TextView = view.findViewById(R.id.orderNumber)
        val name: TextView = view.findViewById(R.id.instrumentName)
        val detail: TextView = view.findViewById(R.id.instrumentDetail)
        val enabled: CheckBox = view.findViewById(R.id.instrumentEnabled)
        val editArea: View = view.findViewById(R.id.editArea)
    }
}
