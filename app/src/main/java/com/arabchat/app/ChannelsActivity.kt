package com.arabchat.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ChannelsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: ChatListAdapter
    private lateinit var tvEmpty: TextView
    private var listener: ListenerRegistration? = null
    private var allChannels: List<Chat> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channels)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
        if (uid == null) {
            finish()
            return
        }

        val rv: RecyclerView = findViewById(R.id.rvChannels)
        val tvBack: TextView = findViewById(R.id.tvBack)
        val tvCreate: TextView = findViewById(R.id.tvCreateChannel)
        val etSearch: EditText = findViewById(R.id.etSearchChannels)
        tvEmpty = findViewById(R.id.tvEmptyChannels)

        rv.layoutManager = LinearLayoutManager(this)
        adapter = ChatListAdapter(mutableListOf(), uid) { chat ->
            joinAndOpen(chat, uid)
        }
        rv.adapter = adapter

        tvBack.setOnClickListener { finish() }
        tvCreate.setOnClickListener { showCreateDialog(uid) }

        etSearch.addTextChangedListener(SimpleTextWatcher { q ->
            val filtered = if (q.isBlank()) allChannels
            else allChannels.filter {
                it.name?.contains(q, true) == true ||
                    it.description?.contains(q, true) == true
            }
            adapter.submitList(filtered)
            tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        })
    }

    override fun onStart() {
        super.onStart()
        listener = db.collection("chats")
            .whereEqualTo("type", "channel")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                allChannels = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Chat::class.java)?.also { it.id = doc.id }
                }.sortedByDescending { it.lastMessageTime?.time ?: 0L }
                val q = findViewById<EditText>(R.id.etSearchChannels).text?.toString().orEmpty()
                val filtered = if (q.isBlank()) allChannels
                else allChannels.filter {
                    it.name?.contains(q, true) == true ||
                        it.description?.contains(q, true) == true
                }
                adapter.submitList(filtered)
                tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
    }

    private fun joinAndOpen(chat: Chat, uid: String) {
        val participants = chat.participants.toMutableList()
        if (!participants.contains(uid)) {
            AlertDialog.Builder(this)
                .setTitle(chat.name ?: getString(R.string.channels_title))
                .setMessage(chat.description?.takeIf { it.isNotBlank() } ?: getString(R.string.join_channel_confirm))
                .setPositiveButton(R.string.join_channel) { _, _ ->
                    participants.add(uid)
                    db.collection("chats").document(chat.id)
                        .update("participants", participants)
                        .addOnSuccessListener {
                            Toast.makeText(this, R.string.joined_channel, Toast.LENGTH_SHORT).show()
                            openChat(chat.id, chat.name ?: "قناة")
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                        }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            openChat(chat.id, chat.name ?: "قناة")
        }
    }

    private fun showCreateDialog(uid: String) {
        val view = layoutInflater.inflate(R.layout.dialog_create_channel, null)
        val etName: EditText = view.findViewById(R.id.etChannelName)
        val etDesc: EditText = view.findViewById(R.id.etChannelDesc)

        AlertDialog.Builder(this)
            .setTitle(R.string.create_channel)
            .setView(view)
            .setPositiveButton(R.string.create_channel_button) { _, _ ->
                val name = etName.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.error_channel_name, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val data = hashMapOf(
                    "type" to "channel",
                    "name" to name,
                    "description" to desc,
                    "participants" to listOf(uid),
                    "admins" to listOf(uid),
                    "participantNames" to mapOf(uid to (auth.currentUser?.email?.substringBefore("@") ?: "مشرف")),
                    "lastMessage" to "",
                    "lastMessageTime" to null
                )
                db.collection("chats").add(data)
                    .addOnSuccessListener { openChat(it.id, name) }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openChat(chatId: String, title: String) {
        startActivity(Intent(this, ChatActivity::class.java).apply {
            putExtra("chatId", chatId)
            putExtra("chatTitle", title)
        })
    }
}

/** Tiny helper to avoid adding androidx dependency for TextWatcher. */
class SimpleTextWatcher(private val onChanged: (String) -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: android.text.Editable?) {
        onChanged(s?.toString().orEmpty())
    }
}
