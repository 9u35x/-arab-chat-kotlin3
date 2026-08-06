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
    private var storiesListener: ListenerRegistration? = null

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

        // شريط القصص
        val layoutStoriesBar = findViewById<android.view.View?>(R.id.layoutStoriesBar)
        layoutStoriesBar?.visibility = android.view.View.VISIBLE
        val rvHomeStories = findViewById<RecyclerView?>(R.id.rvHomeStories)
        rvHomeStories?.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        loadHomeStories(rvHomeStories)

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
        storiesListener?.remove()
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
    

    private fun loadHomeStories(rv: RecyclerView?) {
        if (rv == null) return
        val me = auth.currentUser?.uid ?: return
        val now = System.currentTimeMillis()
        storiesListener?.remove()
        storiesListener = db.collection("stories")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val active = snap.documents.mapNotNull { d ->
                    d.toObject(Story::class.java)?.also { it.id = d.id }
                }.filter { (it.status == "active" || it.status.isBlank()) && it.expiresAtMs > now }

                // قصتي أولاً (حتى لو ما عندي قصة — عنصر "إضافة")
                val mine = active.filter { it.userId == me }
                val others = active.filter { it.userId != me }
                    .groupBy { it.userId }
                    .map { (_, list) -> list.maxByOrNull { it.createdAtMs }!! }

                val rows = mutableListOf<HomeStoryRow>()
                rows.add(
                    HomeStoryRow(
                        isAdd = mine.isEmpty(),
                        userId = me,
                        name = "قصتي",
                        story = mine.maxByOrNull { it.createdAtMs }
                    )
                )
                for (s in others) {
                    rows.add(HomeStoryRow(false, s.userId, s.userName, s))
                }
                rv.adapter = HomeStoryBarAdapter(rows) { row ->
                    if (row.isAdd || (row.userId == me && row.story == null)) {
                        startActivity(android.content.Intent(this, StoriesActivity::class.java))
                    } else if (row.story != null) {
                        val url = row.story!!.mediaUrl
                        if (url.isEmpty()) {
                            startActivity(android.content.Intent(this, StoriesActivity::class.java))
                        } else {
                            val lower = url.lowercase()
                            val isVideo = lower.contains(".mp4") || lower.contains(".mov") ||
                                lower.contains(".webm") || lower.contains("video")
                            if (isVideo) {
                                startActivity(android.content.Intent(this, StoryViewerActivity::class.java).apply {
                putExtra(StoryViewerActivity.EXTRA_URL, url)
            })
                            } else {
                                startActivity(android.content.Intent(this, FullImageActivity::class.java).apply {
                                    putExtra(FullImageActivity.EXTRA_URL, url)
                                })
                            }
                        }
                    }
                }
            }
    }

}


data class HomeStoryRow(
    val isAdd: Boolean,
    val userId: String,
    val name: String,
    val story: Story?
)

class HomeStoryBarAdapter(
    private val rows: List<HomeStoryRow>,
    private val onClick: (HomeStoryRow) -> Unit
) : RecyclerView.Adapter<HomeStoryBarAdapter.VH>() {
    class VH(v: android.view.View) : RecyclerView.ViewHolder(v) {
        val avatar: android.widget.TextView = v.findViewById(R.id.tvStoryAvatar)
        val name: android.widget.TextView = v.findViewById(R.id.tvStoryName)
    }
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_story_circle, parent, false)
        return VH(v)
    }
    override fun getItemCount() = rows.size
    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = rows[position]
        holder.name.text = if (row.isAdd) "إضافة" else row.name
        holder.avatar.text = if (row.isAdd) "+" else row.name.take(1).ifEmpty { "?" }
        holder.itemView.setOnClickListener { onClick(row) }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

}
