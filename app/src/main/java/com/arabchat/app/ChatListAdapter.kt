package com.arabchat.app

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
    private val onChatClick: (Chat) -> Unit,
    private val onChatLongClick: (Chat) -> Unit = {}
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

        holder.avatarLetter.visibility = View.VISIBLE
        holder.avatarLetter.text = if (title.isNotEmpty()) title.take(1).uppercase() else "?"
        holder.ivAvatar.visibility = View.GONE
        holder.chatName.text = title

        val prefix = when (chat.type) {
            "channel" -> "📢 "
            "group" -> "👥 "
            else -> ""
        }
        holder.lastMessage.text = if (chat.lastMessage.isNotEmpty()) prefix + chat.lastMessage
        else prefix + "لا توجد رسائل بعد"
        holder.chatTime.text = chat.lastMessageTime?.let { timeFormat.format(it) } ?: ""

        // Load other user avatar for direct chats
        if (chat.type == "direct") {
            val otherUid = chat.participants.firstOrNull { it != currentUid }
            if (otherUid != null) {
                val cached = avatarCache[otherUid]
                if (cached != null) {
                    bindAvatar(holder, cached)
                } else if (!avatarCache.containsKey(otherUid)) {
                    avatarCache[otherUid] = null // mark loading
                    db.collection("users").document(otherUid).get()
                        .addOnSuccessListener { snap ->
                            val url = snap.getString("avatarUrl")
                            avatarCache[otherUid] = url
                            val pos = holder.adapterPosition
                            if (pos != RecyclerView.NO_POSITION && chats.getOrNull(pos)?.id == chat.id) {
                                bindAvatar(holder, url)
                            } else {
                                notifyDataSetChanged()
                            }
                        }
                }
            }
        }

        holder.itemView.setOnClickListener { onChatClick(chat) }
        holder.itemView.setOnLongClickListener {
            onChatLongClick(chat)
            true
        }
    }

    private fun bindAvatar(holder: ChatViewHolder, url: String?) {
        if (!url.isNullOrEmpty()) {
            holder.ivAvatar.visibility = View.VISIBLE
            holder.avatarLetter.visibility = View.GONE
            Glide.with(holder.ivAvatar.context)
                .load(url)
                .circleCrop()
                .into(holder.ivAvatar)
        }
    }

    override fun getItemCount(): Int = chats.size

    fun submitList(newChats: List<Chat>) {
        chats.clear()
        chats.addAll(newChats)
        notifyDataSetChanged()
    }

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatarLetter: TextView = view.findViewById(R.id.tvAvatarLetter)
        val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
        val chatName: TextView = view.findViewById(R.id.tvChatName)
        val lastMessage: TextView = view.findViewById(R.id.tvLastMessage)
        val chatTime: TextView = view.findViewById(R.id.tvChatTime)
    }
}
