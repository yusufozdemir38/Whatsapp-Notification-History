package com.example.notificationhistory

import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class NotificationAdapter(
    private val onDeleteClick: (NotificationItem) -> Unit,
    private val onImageClick: (String) -> Unit   // Fotoğrafa tıklanınca tam ekran aç
) : ListAdapter<NotificationItem, NotificationAdapter.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<NotificationItem>() {
            override fun areItemsTheSame(old: NotificationItem, new: NotificationItem) =
                old.id == new.id
            override fun areContentsTheSame(old: NotificationItem, new: NotificationItem) =
                old == new
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSender: TextView    = view.findViewById(R.id.tvSender)
        val tvMessage: TextView   = view.findViewById(R.id.tvMessage)
        val tvTime: TextView      = view.findViewById(R.id.tvTime)
        val tvGroupBadge: TextView= view.findViewById(R.id.tvGroupBadge)
        val ivDelete: ImageView   = view.findViewById(R.id.ivDelete)
        val ivIcon: ImageView     = view.findViewById(R.id.ivMessageIcon)
        val ivThumbnail: ImageView= view.findViewById(R.id.ivThumbnail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        // ── Temel alanlar ─────────────────────────────────────────────
        holder.tvSender.text = item.senderName
        holder.tvTime.text = item.getFormattedTime()

        // ── Silindi durumu ────────────────────────────────────────────
        // NOT: Artık üstü çizili yazı YOK — sadece kırmızı arka plan + badge.
        if (item.isDeleted) {
            holder.tvMessage.text = "🗑 Bu mesaj silindi: ${item.message}"
            holder.tvMessage.setTextColor(Color.parseColor("#C62828"))
            // Üstü çizgi intentionally kaldırıldı — okunabilirlik için
            holder.tvMessage.paintFlags =
                holder.tvMessage.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()

            (holder.itemView as? androidx.cardview.widget.CardView)
                ?.setCardBackgroundColor(Color.parseColor("#FFEBEE"))

            holder.tvGroupBadge.visibility = View.VISIBLE
            holder.tvGroupBadge.text = "SİLİNDİ"
            holder.tvGroupBadge.setBackgroundColor(Color.parseColor("#F44336"))

        } else {
            holder.tvMessage.text = item.message
            holder.tvMessage.setTextColor(Color.parseColor("#666666"))
            holder.tvMessage.paintFlags =
                holder.tvMessage.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()

            (holder.itemView as? androidx.cardview.widget.CardView)
                ?.setCardBackgroundColor(Color.WHITE)

            if (item.isGroup) {
                holder.tvGroupBadge.visibility = View.VISIBLE
                holder.tvGroupBadge.text = "GRUP"
                holder.tvGroupBadge.setBackgroundResource(R.drawable.badge_background)
            } else {
                holder.tvGroupBadge.visibility = View.GONE
            }
        }

        // ── İkon (mesaj tipi) ─────────────────────────────────────────
        val isMedia = item.isMedia ||
            item.message.startsWith("📷") ||
            item.message.startsWith("🎥") ||
            item.message.startsWith("🎵") ||
            item.message.startsWith("📎")

        holder.ivIcon.setImageResource(
            if (isMedia) R.drawable.ic_media else R.drawable.ic_message
        )

        // ── Fotoğraf thumbnail ────────────────────────────────────────
        val imgPath = item.imagePath
        if (imgPath != null) {
            val file = File(imgPath)
            if (file.exists()) {
                val bmp = BitmapFactory.decodeFile(imgPath)
                if (bmp != null) {
                    holder.ivThumbnail.setImageBitmap(bmp)
                    holder.ivThumbnail.visibility = View.VISIBLE
                    holder.ivThumbnail.setOnClickListener { onImageClick(imgPath) }
                } else {
                    holder.ivThumbnail.visibility = View.GONE
                }
            } else {
                holder.ivThumbnail.visibility = View.GONE
            }
        } else {
            holder.ivThumbnail.visibility = View.GONE
        }

        // ── Silme butonu ──────────────────────────────────────────────
        holder.ivDelete.setOnClickListener { onDeleteClick(item) }
    }
}
