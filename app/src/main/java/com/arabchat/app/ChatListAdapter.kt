package com.arabchat.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class ChatListAdapter(
    private val chats: MutableList<Chat>,
    private val currentUid: String,
    private val onChatClick: (Chat) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ChatViewHolder>() {

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale("ar"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_row, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chats[position]
        val title = chat.titleFor(currentUid)

        holder.avatarLetter.text = if (title.isNotEmpty()) title.take(1).uppercase() else "?"
        holder.chatName.text = title
        holder.lastMessage.text = if (chat.lastMessage.isNotEmpty()) chat.lastMessage else "لا توجد رسائل بعد"
        holder.chatTime.text = chat.lastMessageTime?.let { timeFormat.format(it) } ?: ""

        holder.itemView.setOnClickListener { onChatClick(chat) }
    }

    override fun getItemCount(): Int = chats.size

    fun submitList(newChats: List<Chat>) {
        chats.clear()
        chats.addAll(newChats)
        notifyDataSetChanged()
    }

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatarLetter: TextView = view.findViewById(R.id.tvAvatarLetter)
        val chatName: TextView = view.findViewById(R.id.tvChatName)
        val lastMessage: TextView = view.findViewById(R.id.tvLastMessage)
        val chatTime: TextView = view.findViewById(R.id.tvChatTime)
    }
}
