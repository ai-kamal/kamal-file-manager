package com.kovak.kamal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileAdapter(
    private val context: Context,
    private val onItemClick: (FileItem) -> Unit,
    private val onItemLongClick: (FileItem, View) -> Boolean,
    private val onSelectionChanged: (count: Int) -> Unit = {}
) : ListAdapter<FileItem, RecyclerView.ViewHolder>(FileDiffCallback()) {

    companion object {
        private const val VIEW_LIST = 0
        private const val VIEW_GRID = 1
    }

    // ── State ─────────────────────────────────────────────────────
    val selectedPaths = mutableSetOf<String>()
    var selectionMode = false

    var isGridMode = false
        set(value) { field = value; notifyDataSetChanged() }

    // ── Thumbnail cache ────────────────────────────────────────────
    private val thumbnailCache = LruCache<String, Bitmap>(40)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── Lifecycle ─────────────────────────────────────────────────
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        scope.cancel()
    }

    // ── View types ────────────────────────────────────────────────
    override fun getItemViewType(position: Int) = if (isGridMode) VIEW_GRID else VIEW_LIST

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_GRID) {
            GridViewHolder(inflater.inflate(R.layout.item_file_grid, parent, false))
        } else {
            FileViewHolder(inflater.inflate(R.layout.item_file, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is FileViewHolder -> holder.bind(item)
            is GridViewHolder -> holder.bind(item)
        }
    }

    // ── Selection helpers ─────────────────────────────────────────
    fun toggleSelection(path: String) {
        if (selectedPaths.contains(path)) selectedPaths.remove(path) else selectedPaths.add(path)
        notifyDataSetChanged()
        onSelectionChanged(selectedPaths.size)
    }

    fun clearSelection() {
        selectedPaths.clear()
        selectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun selectAll() {
        currentList.forEach { selectedPaths.add(it.path) }
        notifyDataSetChanged()
        onSelectionChanged(selectedPaths.size)
    }

    // ── Thumbnail loader ──────────────────────────────────────────
    private fun loadThumbnail(thumbView: ImageView, iconView: ImageView, path: String) {
        val cached = thumbnailCache.get(path)
        if (cached != null) {
            applyThumb(thumbView, iconView, cached)
            return
        }
        // tag used to avoid stale assignment on recycled views
        thumbView.tag = path
        scope.launch {
            val bm = withContext(Dispatchers.IO) {
                runCatching {
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = 4
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    BitmapFactory.decodeFile(path, opts)
                }.getOrNull()
            }
            if (thumbView.tag == path && bm != null) {
                thumbnailCache.put(path, bm)
                applyThumb(thumbView, iconView, bm)
            }
        }
    }

    private fun applyThumb(thumbView: ImageView, iconView: ImageView, bm: Bitmap) {
        thumbView.setImageBitmap(bm)
        thumbView.visibility = View.VISIBLE
        iconView.visibility = View.GONE
    }

    // ── List ViewHolder ────────────────────────────────────────────
    inner class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivIcon: ImageView = view.findViewById(R.id.ivFileIcon)
        private val ivThumb: ImageView = view.findViewById(R.id.ivThumbnail)
        private val tvName: TextView = view.findViewById(R.id.tvFileName)
        private val tvMeta: TextView = view.findViewById(R.id.tvFileMeta)
        private val ivSel: ImageView = view.findViewById(R.id.ivSelected)

        fun bind(item: FileItem) {
            tvName.text = item.name
            tvMeta.text = buildMeta(item)

            // Reset thumbnail state
            ivThumb.visibility = View.GONE
            ivThumb.setImageBitmap(null)
            ivIcon.visibility = View.VISIBLE
            ivIcon.setImageResource(getIconRes(item.fileTypeIcon))
            ivIcon.setColorFilter(getIconTint(item.fileTypeIcon))

            if (item.fileTypeIcon == FileType.IMAGE) {
                loadThumbnail(ivThumb, ivIcon, item.path)
            }

            val sel = selectedPaths.contains(item.path)
            ivSel.visibility = if (selectionMode) View.VISIBLE else View.GONE
            ivSel.setImageResource(if (sel) R.drawable.ic_check_circle else R.drawable.ic_radio_unchecked)
            itemView.isActivated = sel
            itemView.alpha = if (item.isHidden) 0.45f else 1f

            itemView.setOnClickListener {
                if (selectionMode) toggleSelection(item.path) else onItemClick(item)
            }
            itemView.setOnLongClickListener { v -> onItemLongClick(item, v) }
        }
    }

    // ── Grid ViewHolder ────────────────────────────────────────────
    inner class GridViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivIcon: ImageView = view.findViewById(R.id.ivFileIconGrid)
        private val ivThumb: ImageView = view.findViewById(R.id.ivThumbnailGrid)
        private val tvName: TextView = view.findViewById(R.id.tvFileNameGrid)
        private val tvMeta: TextView = view.findViewById(R.id.tvFileMetaGrid)
        private val ivSel: ImageView = view.findViewById(R.id.ivSelectedGrid)

        fun bind(item: FileItem) {
            tvName.text = item.name
            tvMeta.text = buildMeta(item)

            ivThumb.visibility = View.GONE
            ivThumb.setImageBitmap(null)
            ivIcon.visibility = View.VISIBLE
            ivIcon.setImageResource(getIconRes(item.fileTypeIcon))
            ivIcon.setColorFilter(getIconTint(item.fileTypeIcon))

            if (item.fileTypeIcon == FileType.IMAGE) {
                loadThumbnail(ivThumb, ivIcon, item.path)
            }

            val sel = selectedPaths.contains(item.path)
            ivSel.visibility = if (selectionMode) View.VISIBLE else View.GONE
            ivSel.setImageResource(if (sel) R.drawable.ic_check_circle else R.drawable.ic_radio_unchecked)
            itemView.alpha = if (item.isHidden) 0.45f else 1f

            itemView.setOnClickListener {
                if (selectionMode) toggleSelection(item.path) else onItemClick(item)
            }
            itemView.setOnLongClickListener { v -> onItemLongClick(item, v) }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────
    private fun buildMeta(item: FileItem): String {
        val date = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(item.lastModified))
        return if (item.isDirectory) date else "${item.displaySize}  ·  $date"
    }

    private fun getIconRes(type: FileType): Int = when (type) {
        FileType.FOLDER   -> R.drawable.ic_folder
        FileType.IMAGE    -> R.drawable.ic_image
        FileType.VIDEO    -> R.drawable.ic_video
        FileType.AUDIO    -> R.drawable.ic_audio
        FileType.PDF      -> R.drawable.ic_pdf
        FileType.ARCHIVE  -> R.drawable.ic_archive
        FileType.APK      -> R.drawable.ic_apk
        FileType.TEXT     -> R.drawable.ic_text
        FileType.DOCUMENT -> R.drawable.ic_document
        FileType.UNKNOWN  -> R.drawable.ic_file
    }

    private fun getIconTint(type: FileType): Int = ContextCompat.getColor(
        context, when (type) {
            FileType.FOLDER   -> R.color.tint_folder
            FileType.IMAGE    -> R.color.tint_image
            FileType.VIDEO    -> R.color.tint_video
            FileType.AUDIO    -> R.color.tint_audio
            FileType.PDF      -> R.color.tint_pdf
            FileType.ARCHIVE  -> R.color.tint_archive
            FileType.APK      -> R.color.tint_apk
            FileType.TEXT     -> R.color.tint_text
            FileType.DOCUMENT -> R.color.tint_document
            else              -> R.color.tint_default
        }
    )

    class FileDiffCallback : DiffUtil.ItemCallback<FileItem>() {
        override fun areItemsTheSame(a: FileItem, b: FileItem) = a.path == b.path
        override fun areContentsTheSame(a: FileItem, b: FileItem) = a == b
    }
}
