package com.arabchat.app

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class NewChatActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: UserListAdapter
    private lateinit var etGroupName: EditText
    private lateinit var tvCreateGroup: TextView
    private lateinit var tvToggleGroupMode: TextView

    private var myUsername: String = "مستخدم"
    private var allUsers: List<UserProfile> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_chat)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val currentUser = auth.currentUser
        if (currentUser == null) {
            finish()
            return
        }

        val rvUsers: RecyclerView = findViewById(R.id.rvUsers)
        val tvBack: TextView = findViewById(R.id.tvBack)
        tvToggleGroupMode = findViewById(R.id.tvToggleGroupMode)
        etGroupName = findViewById(R.id.etGroupName)
        tvCreateGroup = findViewById(R.id.tvCreateGroup)

        rvUsers.layoutManager = LinearLayoutManager(this)
        adapter = UserListAdapter(mutableListOf()) { user ->
            startDirectChat(user)
        }
        rvUsers.adapter = adapter

        val etSearchUsers = findViewById<EditText?>(R.id.etSearchUsers)
        etSearchUsers?.addTextChangedListener(SimpleTextWatcher { q ->
            val filtered = if (q.isBlank()) allUsers
            else allUsers.filter {
                it.bestName().contains(q, true) ||
                    it.username.contains(q, true) ||
                    (it.username.contains(q, true))
            }
            adapter.submitList(filtered)
        })

        tvBack.setOnClickListener { finish() }

        tvToggleGroupMode.setOnClickListener {
            val enabling = !adapter.groupMode
            adapter.groupMode = enabling
            etGroupName.visibility = if (enabling) android.view.View.VISIBLE else android.view.View.GONE
            tvCreateGroup.visibility = if (enabling) android.view.View.VISIBLE else android.view.View.GONE
            tvToggleGroupMode.text = if (enabling) "إلغاء" else getString(R.string.create_group)
        }

        tvCreateGroup.setOnClickListener { createGroup() }

        loadMyProfile(currentUser.uid)
        loadUsers(currentUser.uid)
    }

    private fun loadMyProfile(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val profile = doc.toObject(UserProfile::class.java)
                if (profile != null) myUsername = profile.username
            }
    }

    private fun loadUsers(myUid: String) {
        db.collection("users").get()
            .addOnSuccessListener { snapshot ->
                allUsers = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(UserProfile::class.java)?.also { it.uid = doc.id }
                }.filter { it.uid != myUid }
                adapter.submitList(allUsers)
            }
    }

    private fun startDirectChat(otherUser: UserProfile) {
        val myUid = auth.currentUser?.uid ?: return
        val chatId = listOf(myUid, otherUser.uid).sorted().joinToString("_")
        val chatRef = db.collection("chats").document(chatId)

        chatRef.get().addOnSuccessListener { doc ->
            if (!doc.exists()) {
                val data = hashMapOf(
                    "type" to "direct",
                    "participants" to listOf(myUid, otherUser.uid),
                    "participantNames" to mapOf(myUid to myUsername, otherUser.uid to otherUser.username),
                    "lastMessage" to "",
                    "lastMessageTime" to null
                )
                chatRef.set(data).addOnSuccessListener {
                    openChat(chatId, otherUser.username)
                }
            } else {
                openChat(chatId, otherUser.username)
            }
        }
    }

    private fun createGroup() {
        val groupName = etGroupName.text.toString().trim()
        if (groupName.isEmpty()) {
            Toast.makeText(this, R.string.error_group_name, Toast.LENGTH_SHORT).show()
            return
        }
        val selected = adapter.getSelectedUserObjects()
        if (selected.size < 2) {
            Toast.makeText(this, R.string.error_select_members, Toast.LENGTH_SHORT).show()
            return
        }

        val myUid = auth.currentUser?.uid ?: return
        val participants = mutableListOf(myUid)
        participants.addAll(selected.map { it.uid })

        val participantNames = mutableMapOf(myUid to myUsername)
        selected.forEach { participantNames[it.uid] = it.username }

        val data = hashMapOf(
            "type" to "group",
            "name" to groupName,
            "participants" to participants,
            "participantNames" to participantNames,
            "lastMessage" to "",
            "lastMessageTime" to null
        )

        db.collection("chats").add(data)
            .addOnSuccessListener { docRef ->
                openChat(docRef.id, groupName)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun openChat(chatId: String, title: String) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("chatId", chatId)
        intent.putExtra("chatTitle", title)
        startActivity(intent)
        finish()
    }
}
