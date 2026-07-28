package com.arabchat.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

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
        holder.avatarLetter.text = if (name.isNotEmpty()) name.take(1).uppercase() else "?"
        holder.username.text = name
        holder.checkbox.visibility = if (groupMode) View.VISIBLE else View.GONE
        holder.checkbox.isChecked = selectedUsers.contains(user.uid)

        holder.itemView.setOnClickListener {
            if (groupMode) {
                if (selectedUsers.contains(user.uid)) {
                    selectedUsers.remove(user.uid)
                } else {
                    selectedUsers.add(user.uid)
                }
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
        val username: TextView = view.findViewById(R.id.tvUsername)
        val checkbox: CheckBox = view.findViewById(R.id.checkboxSelect)
    }
}
