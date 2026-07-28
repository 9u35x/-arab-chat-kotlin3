package com.arabchat.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SearchActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var userAdapter: UserListAdapter
    private lateinit var channelAdapter: ChatListAdapter
    private lateinit var rvUsers: RecyclerView
    private lateinit var rvChannels: RecyclerView
    private lateinit var tvUsersHeader: TextView
    private lateinit var tvChannelsHeader: TextView
    private lateinit var tvEmpty: TextView

    private var allUsers: List<UserProfile> = emptyList()
    private var allChannels: List<Chat> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
        if (uid == null) {
            finish()
            return
        }

        findViewById<TextView>(R.id.tvBack).setOnClickListener { finish() }
        val etSearch: EditText = findViewById(R.id.etSearch)
        rvUsers = findViewById(R.id.rvUsers)
        rvChannels = findViewById(R.id.rvChannels)
        tvUsersHeader = findViewById(R.id.tvUsersHeader)
        tvChannelsHeader = findViewById(R.id.tvChannelsHeader)
        tvEmpty = findViewById(R.id.tvEmptySearch)

        rvUsers.layoutManager = LinearLayoutManager(this)
        userAdapter = UserListAdapter(mutableListOf()) { user ->
            openOrCreateDirect(uid, user)
        }
        rvUsers.adapter = userAdapter

        rvChannels.layoutManager = LinearLayoutManager(this)
        channelAdapter = ChatListAdapter(mutableListOf(), uid) { chat ->
            joinAndOpenChannel(chat, uid)
        }
        rvChannels.adapter = channelAdapter

        loadData(uid)

        etSearch.addTextChangedListener(SimpleTextWatcher { q -> applyFilter(q) })
    }

    private fun loadData(uid: String) {
        db.collection("users").get()
            .addOnSuccessListener { snap ->
                allUsers = snap.documents.mapNotNull { doc ->
                    doc.toObject(UserProfile::class.java)?.also { it.uid = doc.id }
                }.filter { it.uid != uid }
                applyFilter(findViewById<EditText>(R.id.etSearch).text?.toString().orEmpty())
            }

        db.collection("chats").whereEqualTo("type", "channel").get()
            .addOnSuccessListener { snap ->
                allChannels = snap.documents.mapNotNull { doc ->
                    doc.toObject(Chat::class.java)?.also { it.id = doc.id }
                }
                applyFilter(findViewById<EditText>(R.id.etSearch).text?.toString().orEmpty())
            }
    }

    private fun applyFilter(q: String) {
        val users = if (q.isBlank()) allUsers
        else allUsers.filter {
            it.bestName().contains(q, true) ||
                it.username.contains(q, true) ||
                it.bio.contains(q, true)
        }
        val channels = if (q.isBlank()) allChannels
        else allChannels.filter {
            it.name?.contains(q, true) == true ||
                it.description?.contains(q, true) == true
        }

        userAdapter.submitList(users)
        channelAdapter.submitList(channels)

        tvUsersHeader.visibility = if (users.isEmpty()) View.GONE else View.VISIBLE
        rvUsers.visibility = if (users.isEmpty()) View.GONE else View.VISIBLE
        tvChannelsHeader.visibility = if (channels.isEmpty()) View.GONE else View.VISIBLE
        rvChannels.visibility = if (channels.isEmpty()) View.GONE else View.VISIBLE
        tvEmpty.visibility =
            if (users.isEmpty() && channels.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openOrCreateDirect(myUid: String, other: UserProfile) {
        db.collection("chats")
            .whereEqualTo("type", "direct")
            .whereArrayContains("participants", myUid)
            .get()
            .addOnSuccessListener { snap ->
                val existing = snap.documents.firstOrNull { doc ->
                    val parts = doc.get("participants") as? List<*>
                    parts != null && parts.contains(other.uid)
                }
                if (existing != null) {
                    openChat(existing.id, other.bestName())
                } else {
                    val data = hashMapOf(
                        "type" to "direct",
                        "participants" to listOf(myUid, other.uid),
                        "participantNames" to mapOf(
                            myUid to (auth.currentUser?.email?.substringBefore("@") ?: "أنا"),
                            other.uid to other.bestName()
                        ),
                        "lastMessage" to "",
                        "lastMessageTime" to null
                    )
                    db.collection("chats").add(data)
                        .addOnSuccessListener { openChat(it.id, other.bestName()) }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                        }
                }
            }
    }

    private fun joinAndOpenChannel(chat: Chat, uid: String) {
        val participants = chat.participants.toMutableList()
        if (!participants.contains(uid)) {
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
        } else {
            openChat(chat.id, chat.name ?: "قناة")
        }
    }

    private fun openChat(chatId: String, title: String) {
        startActivity(Intent(this, ChatActivity::class.java).apply {
            putExtra("chatId", chatId)
            putExtra("chatTitle", title)
        })
    }
}
