package com.arabchat.app

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class ChatListAdapter(
    private val chats: MutableList<Chat>,
    private val currentUid: String,
    private val onChatLongClick: (Chat) -> Unit = {},
    private val onChatClick: (Chat) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ChatViewHolder>() {

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale("ar"))
    private val avatarCache = mutableMapOf<String, String?>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_row, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chats[position]
        val title = chat.titleFor(currentUid)
        val unread = chat.unreadFor(currentUid)

        holder.avatarLetter.visibility = View.VISIBLE
        holder.avatarLetter.text = if (title.isNotEmpty()) title.take(1).uppercase() else "?"
        holder.ivAvatar.visibility = View.GONE
        holder.chatName.text = title

        val prefix = when (chat.type) {
            "channel" -> "📢 "
            "group" -> "👥 "
            else -> ""
        }
        holder.lastMessage.text = prefix + chat.lastMessage

        holder.time.text = chat.lastMessageTime?.let { timeFormat.format(it) } ?: ""

        // Unread badge like WhatsApp
        if (unread > 0) {
            holder.unreadBadge.visibility = View.VISIBLE
            holder.unreadBadge.text = if (unread > 99) "99+" else unread.toString()
            holder.chatName.setTypeface(null, Typeface.BOLD)
            holder.lastMessage.setTypeface(null, Typeface.BOLD)
            holder.time.setTextColor(0xFF25D366.toInt())
        } else {
            holder.unreadBadge.visibility = View.GONE
            holder.chatName.setTypeface(null, Typeface.NORMAL)
            holder.lastMessage.setTypeface(null, Typeface.NORMAL)
            holder.time.setTextColor(holder.itemView.context.getColor(R.color.textTertiaryDark))
        }

        // Avatar for direct / channel
        when (chat.type) {
            "direct" -> {
                val other = chat.participants.firstOrNull { it != currentUid }
                if (other != null) loadUserAvatar(other, holder)
            }
            "channel", "group" -> {
                if (!chat.avatarUrl.isNullOrEmpty()) {
                    holder.ivAvatar.visibility = View.VISIBLE
                    holder.avatarLetter.visibility = View.GONE
                    Glide.with(holder.itemView).load(chat.avatarUrl).circleCrop().into(holder.ivAvatar)
                }
            }
        }

        holder.itemView.setOnClickListener { onChatClick(chat) }
        holder.itemView.setOnLongClickListener {
            onChatLongClick(chat)
            true
        }
    }

    private fun loadUserAvatar(uid: String, holder: ChatViewHolder) {
        val cached = avatarCache[uid]
        if (cached != null) {
            if (cached.isNotEmpty()) {
                holder.ivAvatar.visibility = View.VISIBLE
                holder.avatarLetter.visibility = View.GONE
                Glide.with(holder.itemView).load(cached).circleCrop().into(holder.ivAvatar)
            }
            return
        }
        db.collection("users").document(uid).get()
            .addOnSuccessListener { snap ->
                val url = snap.getString("avatarUrl").orEmpty()
                avatarCache[uid] = url
                if (url.isNotEmpty() && holder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    holder.ivAvatar.visibility = View.VISIBLE
                    holder.avatarLetter.visibility = View.GONE
                    Glide.with(holder.itemView).load(url).circleCrop().into(holder.ivAvatar)
                }
            }
    }

    override fun getItemCount(): Int = chats.size

    fun submitList(newList: List<Chat>) {
        chats.clear()
        chats.addAll(newList)
        notifyDataSetChanged()
    }

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatarLetter: TextView = view.findViewById(R.id.tvAvatarLetter)
        val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
        val chatName: TextView = view.findViewById(R.id.tvChatName)
        val lastMessage: TextView = view.findViewById(R.id.tvLastMessage)
        val time: TextView = view.findViewById(R.id.tvChatTime)
        val unreadBadge: TextView = view.findViewById(R.id.tvUnreadBadge)
    }
}
