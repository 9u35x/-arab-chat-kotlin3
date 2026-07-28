package com.arabchat.app

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
    private var allChats: List<Chat> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

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
        adapter = ChatListAdapter(mutableListOf(), user.uid) { chat ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("chatId", chat.id)
            intent.putExtra("chatTitle", chat.titleFor(user.uid))
            startActivity(intent)
        }
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
                allChats = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Chat::class.java)?.also { it.id = doc.id }
                }.sortedByDescending { it.lastMessageTime?.time ?: 0L }

                val q = findViewById<EditText>(R.id.etSearchChats).text?.toString().orEmpty()
                applyFilter(q)
            }
    }

    override fun onStop() {
        super.onStop()
        listenerRegistration?.remove()
    }
}
