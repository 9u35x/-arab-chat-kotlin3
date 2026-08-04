package com.arabchat.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MessageAdapter(
    private val messages: MutableList<Message>,
    private val currentUserId: String,
    private val onPlayVoice: (String) -> Unit,
    private val onViewTemporaryImage: (Message, Int) -> Unit,
    private val onMessageLongClick: (Message) -> Unit = {},
    private val onImageClick: (String) -> Unit = {},
    private val onReact: (Message) -> Unit = {},
    private val senderNames: Map<String, String> = emptyMap()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2
    }

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale("ar"))

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderId == currentUserId) TYPE_SENT else TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SENT) {
            SentViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false))
        } else {
            ReceivedViewHolder(inflater.inflate(R.layout.item_message_received, parent, false))
        }
    }

    private fun formatDuration(ms: Long): String {
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val timeText = message.timestamp?.let { timeFormat.format(it) } ?: ""

        when (holder) {
            is SentViewHolder -> {
                bindCommon(
                    message, timeText,
                    holder.text, holder.time, holder.image, holder.tempOverlay,
                    holder.voiceRow, holder.playVoice, holder.voiceDuration
                )
                holder.status?.let { st ->
                    when (message.status) {
                        "read" -> {
                            st.text = "✓✓"
                            st.setTextColor(0xFFB983FF.toInt())
                        }
                        "delivered" -> {
                            st.text = "✓✓"
                            st.setTextColor(0xCCFFFFFF.toInt())
                        }
                        else -> {
                            st.text = "✓"
                            st.setTextColor(0xCCFFFFFF.toInt())
                        }
                    }
                }
                holder.bindReactions(message)
                holder.itemView.setOnLongClickListener {
                    onReact(message)
                    true
                }
                holder.itemView.setOnClickListener {
                    // ضغط عادي لا شيء؛ الحذف من القائمة داخل onReact
                }
            }
            is ReceivedViewHolder -> {
                holder.name.text = displayNameFor(message)
                bindCommon(
                    message, timeText,
                    holder.text, holder.time, holder.image, holder.tempOverlay,
                    holder.voiceRow, holder.playVoice, holder.voiceDuration
                )
                holder.bindReactions(message)
                holder.itemView.setOnLongClickListener {
                    onReact(message)
                    true
                }
            }
        }
    }

    private fun bindCommon(
        message: Message,
        timeText: String,
        tvText: TextView,
        tvTime: TextView,
        ivImage: ImageView,
        tvTempOverlay: TextView,
        llVoiceRow: View,
        tvPlayVoice: TextView,
        tvVoiceDuration: TextView
    ) {
        tvText.visibility = View.GONE
        ivImage.visibility = View.GONE
        tvTempOverlay.visibility = View.GONE
        llVoiceRow.visibility = View.GONE
        ivImage.setOnClickListener(null)
        tvTime.text = timeText

        when (message.type) {
            "image" -> {
                val url = message.mediaUrl
                if (message.isTemporary && !message.viewed) {
                    tvTempOverlay.visibility = View.VISIBLE
                    tvTempOverlay.text = "صورة مؤقتة - اضغط للمشاهدة"
                    tvTempOverlay.setOnClickListener {
                        if (url.isNotEmpty()) onImageClick(url)
                        onViewTemporaryImage(message, messages.indexOf(message))
                    }
                } else if (message.isTemporary && message.viewed) {
                    tvTempOverlay.visibility = View.VISIBLE
                    tvTempOverlay.text = "تمت مشاهدة الصورة"
                } else {
                    ivImage.visibility = View.VISIBLE
                    if (url.isNotEmpty()) {
                        Glide.with(ivImage.context)
                            .load(url)
                            .centerCrop()
                            .placeholder(android.R.color.darker_gray)
                            .error(android.R.color.holo_red_dark)
                            .into(ivImage)
                        ivImage.setOnClickListener { onImageClick(url) }
                    } else {
                        tvTempOverlay.visibility = View.VISIBLE
                        tvTempOverlay.text = "صورة غير متوفرة"
                    }
                }
            }
            "voice" -> {
                llVoiceRow.visibility = View.VISIBLE
                val dur = if (message.durationMs > 0) message.durationMs else 1000L
                tvVoiceDuration.text = formatDuration(dur)
                tvPlayVoice.setOnClickListener {
                    if (message.mediaUrl.isNotEmpty()) onPlayVoice(message.mediaUrl)
                }
            }
            else -> {
                tvText.visibility = View.VISIBLE
                tvText.text = message.text
            }
        }
    }


    private fun displayNameFor(message: Message): String {
        senderNames[message.senderId]?.let { return it }
        val raw = message.senderName.substringBefore("@").trim()
        if (raw.isNotBlank() && raw != "مستخدم" && "@" !in message.senderName) return raw
        if (raw.isNotBlank() && "@" in message.senderName) {
            // was email — hide it
            return "مستخدم"
        }
        return raw.ifBlank { "مستخدم" }
    }

    override fun getItemCount(): Int = messages.size

    fun submitList(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    class SentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.tvMessageText)
        val time: TextView = view.findViewById(R.id.tvMessageTime)
            val status: TextView? = view.findViewById(R.id.tvMessageStatus)
        val image: ImageView = view.findViewById(R.id.ivMessageImage)
        val tempOverlay: TextView = view.findViewById(R.id.tvTemporaryOverlay)
        val voiceRow: View = view.findViewById(R.id.llVoiceRow)
        val playVoice: TextView = view.findViewById(R.id.tvPlayVoice)
        val voiceDuration: TextView = view.findViewById(R.id.tvVoiceDuration)
    }

    class ReceivedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvSenderName)
        val text: TextView = view.findViewById(R.id.tvMessageText)
        val time: TextView = view.findViewById(R.id.tvMessageTime)
        val image: ImageView = view.findViewById(R.id.ivMessageImage)
        val tempOverlay: TextView = view.findViewById(R.id.tvTemporaryOverlay)
        val voiceRow: View = view.findViewById(R.id.llVoiceRow)
        val playVoice: TextView = view.findViewById(R.id.tvPlayVoice)
        val voiceDuration: TextView = view.findViewById(R.id.tvVoiceDuration)
    }
}
