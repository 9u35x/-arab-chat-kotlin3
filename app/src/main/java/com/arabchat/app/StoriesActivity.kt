package com.arabchat.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class StoriesActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var pendingUri: android.net.Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            pendingUri = uri
            askStoryTextAndPublish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stories)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            finish()
            return
        }

        findViewById<TextView>(R.id.tvBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvAddStory).setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        findViewById<RecyclerView>(R.id.rvStories).layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        findViewById<RecyclerView>(R.id.rvPosts).layoutManager = LinearLayoutManager(this)

        expireOldStories()
        listenStories()
        listenPosts()
    }

    private fun askStoryTextAndPublish() {
        val input = EditText(this).apply {
            hint = "اكتب شيئاً (اختياري)"
            setPadding(40, 30, 40, 30)
        }
        AlertDialog.Builder(this)
            .setTitle("نشر قصة")
            .setView(input)
            .setPositiveButton("نشر") { _, _ ->
                publishStory(input.text.toString().trim())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun publishStory(text: String) {
        val user = auth.currentUser ?: return
        val uri = pendingUri ?: return
        val path = "stories/" + user.uid + "_" + System.currentTimeMillis() + ".jpg"
        Toast.makeText(this, "جاري رفع القصة...", Toast.LENGTH_SHORT).show()
        SupabaseStorage.uploadFromUri(this, uri, path, "image/jpeg") { url, err ->
            if (url == null) {
                Toast.makeText(this, err ?: "فشل الرفع", Toast.LENGTH_LONG).show()
                return@uploadFromUri
            }
            val now = System.currentTimeMillis()
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { snap ->
                    val profile = snap.toObject(UserProfile::class.java)
                    val data = hashMapOf(
                        "userId" to user.uid,
                        "userName" to (profile?.bestName() ?: "مستخدم"),
                        "avatarUrl" to (profile?.avatarUrl ?: ""),
                        "mediaUrl" to url,
                        "text" to text,
                        "createdAtMs" to now,
                        "expiresAtMs" to now + 24L * 60L * 60L * 1000L,
                        "status" to "active",
                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                    db.collection("stories").add(data)
                        .addOnSuccessListener {
                            Toast.makeText(this, "تم نشر القصة", Toast.LENGTH_SHORT).show()
                            pendingUri = null
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                        }
                }
        }
    }

    /** قصص منتهية → منشور + status=posted */
    private fun expireOldStories() {
        val now = System.currentTimeMillis()
        db.collection("stories")
            .whereEqualTo("status", "active")
            .whereLessThanOrEqualTo("expiresAtMs", now)
            .get()
            .addOnSuccessListener { snap ->
                for (doc in snap.documents) {
                    val s = doc.toObject(Story::class.java) ?: continue
                    val post = hashMapOf(
                        "userId" to s.userId,
                        "userName" to s.userName,
                        "avatarUrl" to s.avatarUrl,
                        "mediaUrl" to s.mediaUrl,
                        "text" to s.text,
                        "fromStoryId" to doc.id,
                        "createdAtMs" to System.currentTimeMillis(),
                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                    db.collection("posts").add(post)
                    doc.reference.update("status", "posted")
                }
            }
    }

    private fun listenStories() {
        val now = System.currentTimeMillis()
        db.collection("stories")
            .whereEqualTo("status", "active")
            .orderBy("createdAtMs", Query.Direction.DESCENDING)
            .limit(40)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val list = snap.documents.mapNotNull { d ->
                    d.toObject(Story::class.java)?.also { it.id = d.id }
                }.filter { it.expiresAtMs > now }
                findViewById<RecyclerView>(R.id.rvStories).adapter =
                    StoryCircleAdapter(list) { story ->
                        AlertDialog.Builder(this)
                            .setTitle(story.userName)
                            .setMessage(story.text.ifBlank { "قصة" })
                            .setPositiveButton("حسناً", null)
                            .show()
                        // عرض الصورة
                        if (story.mediaUrl.isNotEmpty()) {
                            startActivity(android.content.Intent(this, FullImageActivity::class.java).apply {
                                putExtra(FullImageActivity.EXTRA_URL, story.mediaUrl)
                            })
                        }
                    }
            }
    }

    private fun listenPosts() {
        db.collection("posts")
            .orderBy("createdAtMs", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val list = snap.documents.mapNotNull { d ->
                    d.toObject(Post::class.java)?.also { it.id = d.id }
                }
                findViewById<RecyclerView>(R.id.rvPosts).adapter = PostAdapter(list)
            }
    }
}

class StoryCircleAdapter(
    private val items: List<Story>,
    private val onClick: (Story) -> Unit
) : RecyclerView.Adapter<StoryCircleAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: TextView = v.findViewById(R.id.tvStoryAvatar)
        val name: TextView = v.findViewById(R.id.tvStoryName)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_story_circle, parent, false)
        return VH(v)
    }
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.name.text = s.userName
        holder.avatar.text = s.userName.take(1).ifEmpty { "?" }
        holder.itemView.setOnClickListener { onClick(s) }
    }
}

class PostAdapter(private val items: List<Post>) : RecyclerView.Adapter<PostAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val user: TextView = v.findViewById(R.id.tvPostUser)
        val text: TextView = v.findViewById(R.id.tvPostText)
        val image: ImageView = v.findViewById(R.id.ivPostImage)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false)
        return VH(v)
    }
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.user.text = p.userName
        holder.text.text = p.text
        holder.text.visibility = if (p.text.isBlank()) View.GONE else View.VISIBLE
        if (p.mediaUrl.isNotEmpty()) {
            holder.image.visibility = View.VISIBLE
            Glide.with(holder.image).load(p.mediaUrl).centerCrop().into(holder.image)
        } else {
            holder.image.visibility = View.GONE
        }
    }
}
