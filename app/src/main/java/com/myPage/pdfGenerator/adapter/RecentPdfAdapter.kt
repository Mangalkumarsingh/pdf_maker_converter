package com.myPage.pdfGenerator.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.myPage.pdfGenerator.R
import com.myPage.pdfGenerator.databinding.RowRecentPdfBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecentPdfAdapter(
    private val onOpen: (File) -> Unit,
    private val onShare: (File) -> Unit,
    private val onDownload: (File) -> Unit,
    private val onDelete: (File) -> Unit
) : RecyclerView.Adapter<RecentPdfAdapter.VH>() {

    private val items = ArrayList<File>()

    fun submitList(list: List<File>) {
        items.clear()
        items.addAll(list.take(6))
        notifyDataSetChanged()
    }

    inner class VH(val b: RowRecentPdfBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = RowRecentPdfBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val file = items[position]

        holder.b.tvPdfName.text = file.name
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(file.lastModified()))
        holder.b.tvInfo.text = "$formattedDate"

        holder.itemView.setOnClickListener { onOpen(file) }
        val fileSizeInBytes = file.length()
        val formattedSize = when {
            fileSizeInBytes < 1024 -> "$fileSizeInBytes B"
            fileSizeInBytes < 1024 * 1024 -> String.format("%.2f KB", fileSizeInBytes / 1024.0)
            fileSizeInBytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", fileSizeInBytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", fileSizeInBytes / (1024.0 * 1024 * 1024))
        }
        holder.b.fileSize.text = formattedSize

        holder.b.ivMore.setOnClickListener { anchor ->
            val pm = PopupMenu(anchor.context, anchor)
            pm.menuInflater.inflate(R.menu.menu, pm.menu)

            pm.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_share -> { onShare(file); true }
                    R.id.action_download -> { onDownload(file); true }
                    R.id.action_delete -> { onDelete(file); true }
                    else -> false
                }
            }
            pm.show()
        }
    }

    override fun getItemCount(): Int = items.size
}


