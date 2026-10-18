package net.leviro.ringer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import net.leviro.ringer.R
import net.leviro.ringer.databinding.ItemVipBinding
import net.leviro.ringer.db.Vip

class VipAdapter(
    private var items: List<Vip>,
    private val onClick: (Vip) -> Unit,
    private val onToggle: (Vip, Boolean) -> Unit,
    private val onDelete: (Vip) -> Unit
) : RecyclerView.Adapter<VipAdapter.VH>() {

    inner class VH(val b: ItemVipBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemVipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val vip = items[position]
        holder.b.txtName.text = vip.name
        holder.b.txtNumber.text = vip.number
        val details = detailsLine(holder.b.root.context, vip)
        holder.b.txtDetails.text = details
        holder.b.txtDetails.visibility = if (details.isEmpty()) View.GONE else View.VISIBLE
        val sw = holder.b.swEnabled
        sw.setOnCheckedChangeListener(null)
        sw.isChecked = vip.enabled
        sw.setOnCheckedChangeListener { _, checked -> onToggle(vip, checked) }
        holder.b.root.setOnClickListener { onClick(vip) }
        holder.b.btnDelete.setOnClickListener { onDelete(vip) }
    }

    /** Secondary line summarizing vibrate-only and the per-contact time window. */
    private fun detailsLine(context: android.content.Context, vip: Vip): String {
        val parts = ArrayList<String>()
        if (vip.vibrateOnly) parts.add(context.getString(R.string.item_vibrate_only))
        if (vip.startTime != null && vip.endTime != null) {
            parts.add(context.getString(R.string.item_window_fmt, vip.startTime, vip.endTime))
        }
        return parts.joinToString(" · ")
    }

    fun submit(newItems: List<Vip>) {
        items = newItems
        notifyDataSetChanged()
    }
}
