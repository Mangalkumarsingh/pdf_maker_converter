package com.myPage.pdfGenerator.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.myPage.pdfGenerator.databinding.RowToolCircleBinding
import com.myPage.pdfGenerator.model.ToolItem

class ToolsAdapter(
    private val onClick: (ToolItem) -> Unit
) : RecyclerView.Adapter<ToolsAdapter.VH>() {

    private val items = ArrayList<ToolItem>()

    fun submitList(list: List<ToolItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val b: RowToolCircleBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = RowToolCircleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.b.tvTitle.text = item.title
        holder.b.ivIcon.setImageResource(item.iconRes)
        holder.b.cardCircle.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
