package com.arabchat.app

import com.google.firebase.firestore.SetOptions

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class HomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: ChatListAdapter
    private lateinit var tvEmptyState: TextView
    private var listenerRegistration: ListenerRegistration? = null
    private var lastNotified: MutableMap<String, String> = mutableMapOf()
    private var allChats: List<Chat> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)



        FcmTokenSaver.save(this)
        BanGuard.checkBanned { banned, reason ->
            if (banned) {
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                startActivity(android.content.Intent(this, LoginActivity::class.java))
                finish()
            }
        }
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val user = auth.currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val rvChats: RecyclerView = findViewById(R.id.rvChats)
        val tvProfile: TextView = findViewById(R.id.tvProfile)
        val tvSettings: TextView = findViewById(R.id.tvSettings)
        val tvChannels: TextView = findViewById(R.id.tvChannels)
        val tvLogout: TextView = findViewById(R.id.tvLogout)
        val fabNewChat: TextView = findViewById(R.id.fabNewChat)
        val etSearch: EditText = findViewById(R.id.etSearchChats)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        rvChats.layoutManager = LinearLayoutManager(this)
        adapter = ChatListAdapter(
            mutableListOf(),
            user.uid,
            onChatClick = { chat ->
                val intent = Intent(this, ChatActivity::class.java)
                intent.putExtra("chatId", chat.id)
                intent.putExtra("chatTitle", chat.titleFor(user.uid))
                startActivity(intent)
            },
            onChatLongClick = { chat -> confirmDeleteChat(chat, user.uid) }
        )
        rvChats.adapter = adapter

        fabNewChat.setOnClickListener {
            startActivity(Intent(this, NewChatActivity::class.java))
        }
        tvProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        tvSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        tvChannels.setOnLongClickListener {
            startActivity(Intent(this, StoriesActivity::class.java))
            true
        }
        tvChannels.setOnClickListener {
            startActivity(Intent(this, ChannelsActivity::class.java))
        }
        tvLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        etSearch.isFocusable = false
        etSearch.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        // local filter still works if user pastes somehow
        etSearch.addTextChangedListener(SimpleTextWatcher { q ->
            applyFilter(q)
        })
    }

    private fun applyFilter(query: String) {
        val uid = auth.currentUser?.uid ?: return
        val filtered = if (query.isBlank()) {
            allChats
        } else {
            allChats.filter {
                it.titleFor(uid).contains(query, ignoreCase = true) ||
                    it.lastMessage.contains(query, ignoreCase = true) ||
                    (it.name?.contains(query, ignoreCase = true) == true)
            }
        }
        adapter.submitList(filtered)
        tvEmptyState.visibility =
            if (filtered.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun onStart() {
        super.onStart()
        val uid = auth.currentUser?.uid ?: return
        listenerRegistration = db.collection("chats")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val mappedChats = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Chat::class.java)?.also { c -> c.id = doc.id }
                }
                UnreadStore.syncFromChats(this@HomeActivity, uid, mappedChats)
                allChats = mappedChats.sortedByDescending { it.lastMessageTime?.time ?: 0L }

                // Notify for new last messages
                val me = uid
                for (chat in allChats) {
                    val key = chat.id
                    val last = chat.lastMessage.orEmpty()
                    val prev = lastNotified[key]
                    if (prev != null && prev != last && last.isNotBlank()) {
                        // avoid notifying for my own sends if last message is from me - best effort
                        val title = chat.titleFor(me)
                        ChatNotifier.notifyNewMessage(this@HomeActivity, chat.id, title, last)
                    }
                    lastNotified[key] = last
                }
                val q = findViewById<EditText>(R.id.etSearchChats).text?.toString().orEmpty()
                applyFilter(q)
            }
    }

    override fun onStop() {
        super.onStop()
        listenerRegistration?.remove()
    }

    private fun confirmDeleteChat(chat: Chat, myUid: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.delete_chat)
            .setMessage(R.string.delete_chat_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                leaveOrDeleteChat(chat, myUid)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun leaveOrDeleteChat(chat: Chat, myUid: String) {
        val ref = db.collection("chats").document(chat.id)
        if (chat.type == "direct") {
            // delete messages + chat
            ref.collection("messages").get()
                .addOnSuccessListener { snap ->
                    val batch = db.batch()
                    for (d in snap.documents) batch.delete(d.reference)
                    batch.delete(ref)
                    batch.commit()
                        .addOnSuccessListener {
                            android.widget.Toast.makeText(this@HomeActivity, R.string.chat_deleted, android.widget.Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener {
                    ref.delete()
                }
        } else {
            val participants = chat.participants.toMutableList()
            participants.remove(myUid)
            ref.update("participants", participants)
                .addOnSuccessListener {
                    android.widget.Toast.makeText(this@HomeActivity, R.string.left_chat, android.widget.Toast.LENGTH_SHORT).show()
                }
        }
}
    }
