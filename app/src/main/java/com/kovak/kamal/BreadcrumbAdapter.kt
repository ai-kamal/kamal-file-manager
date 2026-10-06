package com.kovak.kamal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class BreadcrumbAdapter(
    private val onCrumbClick: (String) -> Unit
) : ListAdapter<String, BreadcrumbAdapter.CrumbViewHolder>(CrumbDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CrumbViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_breadcrumb, parent, false)
        return CrumbViewHolder(view)
    }

    override fun onBindViewHolder(holder: CrumbViewHolder, position: Int) {
        val path = getItem(position)
        val isLast = position == itemCount - 1
        holder.bind(path, isLast)
    }

    inner class CrumbViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCrumb: TextView = itemView.findViewById(R.id.tvCrumb)
        private val tvSeparator: TextView = itemView.findViewById(R.id.tvSeparator)

        fun bind(path: String, isLast: Boolean) {
            val name = path.substringAfterLast("/").ifEmpty { "Storage" }
            tvCrumb.text = name
            tvSeparator.visibility = if (isLast) View.GONE else View.VISIBLE

            val color = if (isLast)
                ContextCompat.getColor(itemView.context, R.color.crumb_active)
            else
                ContextCompat.getColor(itemView.context, R.color.crumb_inactive)

            tvCrumb.setTextColor(color)

            itemView.setOnClickListener { onCrumbClick(path) }
        }
    }

    class CrumbDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(a: String, b: String) = a == b
        override fun areContentsTheSame(a: String, b: String) = a == b
    }
}
