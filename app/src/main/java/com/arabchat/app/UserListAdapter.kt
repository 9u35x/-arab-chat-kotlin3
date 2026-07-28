package com.arabchat.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class UserListAdapter(
    private val users: MutableList<UserProfile>,
    private val onUserClick: (UserProfile) -> Unit
) : RecyclerView.Adapter<UserListAdapter.UserViewHolder>() {

    var groupMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    val selectedUsers = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_row, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        val name = user.bestName()
        holder.avatarLetter.visibility = View.VISIBLE
        holder.avatarLetter.text = if (name.isNotEmpty()) name.take(1).uppercase() else "?"
        holder.ivAvatar.visibility = View.GONE

        holder.username.text = name
        val sub = buildString {
            if (user.username.isNotBlank()) append("@${user.username}")
            if (user.bio.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(user.bio)
            }
        }
        holder.subtitle.text = sub
        holder.subtitle.visibility = if (sub.isBlank()) View.GONE else View.VISIBLE

        if (!user.avatarUrl.isNullOrEmpty()) {
            holder.ivAvatar.visibility = View.VISIBLE
            holder.avatarLetter.visibility = View.GONE
            Glide.with(holder.ivAvatar.context)
                .load(user.avatarUrl)
                .circleCrop()
                .into(holder.ivAvatar)
        }

        holder.checkbox.visibility = if (groupMode) View.VISIBLE else View.GONE
        holder.checkbox.isChecked = selectedUsers.contains(user.uid)

        holder.itemView.setOnClickListener {
            if (groupMode) {
                if (selectedUsers.contains(user.uid)) selectedUsers.remove(user.uid)
                else selectedUsers.add(user.uid)
                notifyItemChanged(position)
            } else {
                onUserClick(user)
            }
        }
    }

    override fun getItemCount(): Int = users.size

    fun submitList(newUsers: List<UserProfile>) {
        users.clear()
        users.addAll(newUsers)
        notifyDataSetChanged()
    }

    fun getSelectedUserObjects(): List<UserProfile> {
        return users.filter { selectedUsers.contains(it.uid) }
    }

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatarLetter: TextView = view.findViewById(R.id.tvAvatarLetter)
        val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
        val username: TextView = view.findViewById(R.id.tvUsername)
        val subtitle: TextView = view.findViewById(R.id.tvUserSubtitle)
        val checkbox: CheckBox = view.findViewById(R.id.checkboxSelect)
    }
}
