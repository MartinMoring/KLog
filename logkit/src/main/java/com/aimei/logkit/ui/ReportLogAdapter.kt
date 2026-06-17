package com.aimei.logkit.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aimei.logkit.databinding.ItemLogFileBinding
import java.io.File

internal class ReportLogAdapter(
    private val files: List<File>,
    private val onItemClick: (File) -> Unit
) : RecyclerView.Adapter<ReportLogAdapter.VH>() {

    inner class VH(val binding: ItemLogFileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemLogFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val file = files[position]
        holder.binding.tvFileName.text = file.name
        holder.binding.tvFileSize.text = file.length().toHumanSize()
        holder.itemView.setOnClickListener { onItemClick(file) }
    }

    override fun getItemCount() = files.size

    private fun Long.toHumanSize(): String = when {
        this < 1024 -> "${this}B"
        this < 1024 * 1024 -> "${"%.1f".format(this / 1024.0)}KB"
        else -> "${"%.1f".format(this / 1024.0 / 1024.0)}MB"
    }
}
