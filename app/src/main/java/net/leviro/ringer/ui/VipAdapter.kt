package net.leviro.ringer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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
        val sw = holder.b.swEnabled
        sw.setOnCheckedChangeListener(null)
        sw.isChecked = vip.enabled
        sw.setOnCheckedChangeListener { _, checked -> onToggle(vip, checked) }
        holder.b.root.setOnClickListener { onClick(vip) }
        holder.b.btnDelete.setOnClickListener { onDelete(vip) }
    }

    fun submit(newItems: List<Vip>) {
        items = newItems
        notifyDataSetChanged()
    }
}
