package com.myPage.pdfGenerator.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.myPage.pdfGenerator.databinding.RowSelectedImageBinding

class SelectedImagesDialogAdapter(
    private val onEdit: (Uri, Int) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<SelectedImagesDialogAdapter.VH>() {

    private val items = ArrayList<Uri>()

    fun submitList(list: List<Uri>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun updateItem(position: Int, newUri: Uri) {
        if (position in items.indices) {
            items[position] = newUri
            notifyItemChanged(position)
        }
    }

    fun removeAt(position: Int) {
        if (position in items.indices) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }
    }
    fun addItems(newItems: List<Uri>) {
        val start = items.size
        items.addAll(newItems)
        notifyItemRangeInserted(start, newItems.size)
    }
    fun currentList(): List<Uri> = items.toList()

    inner class VH(val b: RowSelectedImageBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = RowSelectedImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val uri = items[position]
        holder.b.ivThumb.load(uri)

        holder.b.btnEdit.setOnClickListener { onEdit(uri, holder.bindingAdapterPosition) }
        holder.b.btnDelete.setOnClickListener { onDelete(holder.bindingAdapterPosition) }
    }

    override fun getItemCount(): Int = items.size
}

