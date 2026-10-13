package com.myPage.pdfGenerator.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.myPage.pdfGenerator.databinding.RowSelectedImageBinding

class SelectedImagesAdapter(
    private val onEditClick: (uri: Uri, position: Int) -> Unit
) : RecyclerView.Adapter<SelectedImagesAdapter.VH>() {

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

    inner class VH(val binding: RowSelectedImageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = RowSelectedImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val uri = items[position]
        holder.binding.ivThumb.load(uri)

        holder.binding.root.setOnClickListener {
            onEditClick(uri, holder.bindingAdapterPosition)
        }
    }

    override fun getItemCount(): Int = items.size
}
