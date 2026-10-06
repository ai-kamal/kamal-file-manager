package com.kovak.kamal

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileAdapter(
    private val context: Context,
    private val onItemClick: (FileItem) -> Unit,
    private val onItemLongClick: (FileItem, View) -> Boolean
) : ListAdapter<FileItem, FileAdapter.FileViewHolder>(FileDiffCallback()) {

    private val selectedPaths = mutableSetOf<String>()
    var selectionMode = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun toggleSelection(path: String) {
        if (selectedPaths.contains(path)) selectedPaths.remove(path)
        else selectedPaths.add(path)
        notifyDataSetChanged()
    }

    fun getSelectedPaths(): Set<String> = selectedPaths.toSet()

    fun clearSelection() {
        selectedPaths.clear()
        selectionMode = false
        notifyDataSetChanged()
    }

    fun selectAll() {
        currentList.forEach { selectedPaths.add(it.path) }
        notifyDataSetChanged()
    }

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivFileIcon)
        private val tvName: TextView = itemView.findViewById(R.id.tvFileName)
        private val tvMeta: TextView = itemView.findViewById(R.id.tvFileMeta)
        private val ivSelected: ImageView = itemView.findViewById(R.id.ivSelected)

        fun bind(item: FileItem) {
            tvName.text = item.name
            tvMeta.text = buildMeta(item)

            // Set icon based on file type
            ivIcon.setImageResource(getIconRes(item.fileTypeIcon))
            ivIcon.setColorFilter(getIconTint(item.fileTypeIcon, context))

            // Selection state
            val isSelected = selectedPaths.contains(item.path)
            ivSelected.visibility = if (selectionMode) View.VISIBLE else View.GONE
            ivSelected.setImageResource(
                if (isSelected) R.drawable.ic_check_circle
                else R.drawable.ic_radio_unchecked
            )

            itemView.isActivated = isSelected
            itemView.alpha = if (item.isHidden) 0.5f else 1.0f

            itemView.setOnClickListener {
                if (selectionMode) {
                    toggleSelection(item.path)
                } else {
                    onItemClick(item)
                }
            }

            itemView.setOnLongClickListener { v ->
                selectionMode = true
                toggleSelection(item.path)
                onItemLongClick(item, v)
            }
        }

        private fun buildMeta(item: FileItem): String {
            val date = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                .format(Date(item.lastModified))
            return if (item.isDirectory) date
            else "${item.displaySize}  ·  $date"
        }
    }

    private fun getIconRes(type: FileType): Int = when (type) {
        FileType.FOLDER -> R.drawable.ic_folder
        FileType.IMAGE -> R.drawable.ic_image
        FileType.VIDEO -> R.drawable.ic_video
        FileType.AUDIO -> R.drawable.ic_audio
        FileType.PDF -> R.drawable.ic_pdf
        FileType.ARCHIVE -> R.drawable.ic_archive
        FileType.APK -> R.drawable.ic_apk
        FileType.TEXT -> R.drawable.ic_text
        FileType.DOCUMENT -> R.drawable.ic_document
        FileType.UNKNOWN -> R.drawable.ic_file
    }

    private fun getIconTint(type: FileType, context: Context): Int = when (type) {
        FileType.FOLDER -> ContextCompat.getColor(context, R.color.tint_folder)
        FileType.IMAGE -> ContextCompat.getColor(context, R.color.tint_image)
        FileType.VIDEO -> ContextCompat.getColor(context, R.color.tint_video)
        FileType.AUDIO -> ContextCompat.getColor(context, R.color.tint_audio)
        FileType.PDF -> ContextCompat.getColor(context, R.color.tint_pdf)
        FileType.ARCHIVE -> ContextCompat.getColor(context, R.color.tint_archive)
        FileType.APK -> ContextCompat.getColor(context, R.color.tint_apk)
        else -> ContextCompat.getColor(context, R.color.tint_default)
    }

    class FileDiffCallback : DiffUtil.ItemCallback<FileItem>() {
        override fun areItemsTheSame(a: FileItem, b: FileItem) = a.path == b.path
        override fun areContentsTheSame(a: FileItem, b: FileItem) = a == b
    }
}
