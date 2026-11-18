package com.custom.redmitvlauncher.adapter

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.custom.redmitvlauncher.R
import com.custom.redmitvlauncher.model.AppItem

class AppRowAdapter(
    private var items: MutableList<AppItem>,
    private val isEditMode: () -> Boolean,
    private val onAppClick: (AppItem) -> Unit,
    private val onAppLongClick: (AppItem) -> Unit,
    private val onMoveLeft: (Int) -> Unit,
    private val onMoveRight: (Int) -> Unit
) : RecyclerView.Adapter<AppRowAdapter.AppViewHolder>() {

    inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgIcon: ImageView = view.findViewById(R.id.imgAppIcon)
        val tvLabel: TextView = view.findViewById(R.id.tvAppLabel)
        val tvBadge: TextView = view.findViewById(R.id.tvEditBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_card, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = items[position]
        holder.imgIcon.setImageDrawable(app.icon)
        holder.tvLabel.text = app.label

        holder.itemView.alpha = if (app.isHidden) 0.4f else 1.0f
        holder.tvBadge.visibility = if (isEditMode()) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            onAppClick(app)
        }

        holder.itemView.setOnLongClickListener {
            onAppLongClick(app)
            true
        }

        // Hỗ trợ dùng phím Trái / Phải trên Remote để đổi vị trí app khi bật chế độ Sắp xếp
        holder.itemView.setOnKeyListener { _, keyCode, event ->
            if (isEditMode() && event.action == KeyEvent.ACTION_DOWN) {
                val currentPos = holder.bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        onMoveLeft(currentPos)
                        return@setOnKeyListener true
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        onMoveRight(currentPos)
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newList: MutableList<AppItem>) {
        items = newList
        notifyDataSetChanged()
    }
}
