package com.arabchat.app

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class StoryViewerActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var storyId: String = ""
    private var ownerId: String = ""
    private var mediaUrl: String = ""
    private var isVideo: Boolean = false
    private var liked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_story_viewer)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        val me = auth.currentUser?.uid
        if (me == null) {
            finish(); return
        }

        storyId = intent.getStringExtra(EXTRA_STORY_ID).orEmpty()
        ownerId = intent.getStringExtra(EXTRA_OWNER_ID).orEmpty()
        mediaUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        val userName = intent.getStringExtra(EXTRA_USER_NAME).orEmpty()
        val caption = intent.getStringExtra(EXTRA_CAPTION).orEmpty()
        isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false) ||
            mediaUrl.lowercase().let {
                it.contains(".mp4") || it.contains(".mov") || it.contains("video")
            }

        findViewById<TextView>(R.id.tvStoryUser).text = userName.ifBlank { "قصة" }
        findViewById<TextView>(R.id.tvStoryCaption).text = caption
        findViewById<TextView>(R.id.tvCloseStory).setOnClickListener { finish() }

        val iv = findViewById<ImageView>(R.id.ivStory)
        val vv = findViewById<VideoView>(R.id.vvStory)

        if (mediaUrl.isEmpty()) {
            Toast.makeText(this, "لا يوجد محتوى", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        if (isVideo) {
            vv.visibility = View.VISIBLE
            iv.visibility = View.GONE
            // بدون MediaController — تشغيل تلقائي صامت للتحكم
            vv.setOnPreparedListener { it.start() }
            vv.setOnCompletionListener { /* يبقى آخر إطار */ }
            vv.setVideoURI(Uri.parse(mediaUrl))
            vv.setOnClickListener {
                if (vv.isPlaying) vv.pause() else vv.start()
            }
        } else {
            iv.visibility = View.VISIBLE
            vv.visibility = View.GONE
            Glide.with(this).load(mediaUrl).into(iv)
        }

        // تسجيل مشاهدة
        recordView(me)
        loadCounts(me)

        findViewById<TextView>(R.id.tvHeart).setOnClickListener { toggleLike(me) }
        findViewById<TextView>(R.id.tvSendReply).setOnClickListener { sendReply(me) }
        findViewById<TextView>(R.id.tvStoryMenu).setOnClickListener { showMenu(me) }
    }

    private fun recordView(me: String) {
        if (storyId.isEmpty()) return
        val ref = db.collection("stories").document(storyId)
        ref.update("viewers", FieldValue.arrayUnion(me))
            .addOnFailureListener {
                // أنشئ الحقل إن ما موجود
                ref.get().addOnSuccessListener { doc ->
                    if (!doc.exists()) return@addOnSuccessListener
                    val viewers = (doc.get("viewers") as? List<*>)?.mapNotNull { it as? String }?.toMutableList()
                        ?: mutableListOf()
                    if (me !in viewers) {
                        viewers.add(me)
                        ref.update("viewers", viewers)
                    }
                }
            }
    }

    private fun loadCounts(me: String) {
        if (storyId.isEmpty()) return
        db.collection("stories").document(storyId)
            .addSnapshotListener { doc, _ ->
                if (doc == null || !doc.exists()) return@addSnapshotListener
                val viewers = (doc.get("viewers") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                val likes = (doc.get("likes") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                val vCount = viewers.size
                val vText = if (vCount == 1) "👁 مشاهدة واحدة" else "👁 $vCount مشاهدة"
                findViewById<TextView>(R.id.tvStoryViews).text = vText
                findViewById<TextView?>(R.id.tvViewsBig)?.text = vText
                findViewById<TextView>(R.id.tvHeartCount).text =
                    if (likes.isEmpty()) "" else likes.size.toString()
                liked = me in likes
                findViewById<TextView>(R.id.tvHeart).text = if (liked) "❤️" else "🤍"
                // صاحب القصة: القائمة تعرض المشاهدين
                if (me == ownerId) {
                    findViewById<TextView>(R.id.tvStoryViews).setOnClickListener { showViewers() }
                    findViewById<TextView?>(R.id.tvViewsBig)?.setOnClickListener { showViewers() }
                }
            }
    }

    private fun toggleLike(me: String) {
        if (storyId.isEmpty()) return
        val ref = db.collection("stories").document(storyId)
        if (liked) {
            ref.update("likes", FieldValue.arrayRemove(me))
        } else {
            ref.update("likes", FieldValue.arrayUnion(me))
                .addOnFailureListener {
                    ref.get().addOnSuccessListener { doc ->
                        val likes = (doc.get("likes") as? List<*>)?.mapNotNull { it as? String }?.toMutableList()
                            ?: mutableListOf()
                        if (me !in likes) likes.add(me)
                        ref.update("likes", likes)
                    }
                }
        }
    }

    private fun sendReply(me: String) {
        val text = findViewById<EditText>(R.id.etStoryReply).text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "اكتب رداً", Toast.LENGTH_SHORT).show()
            return
        }
        if (ownerId.isEmpty()) {
            Toast.makeText(this, "لا يمكن الرد", Toast.LENGTH_SHORT).show()
            return
        }
        // رد كرسالة خاصة لصاحب القصة
        val participants = listOf(me, ownerId).sorted()
        db.collection("chats")
            .whereEqualTo("participants", participants)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                val chatId = if (!snap.isEmpty) snap.documents[0].id else null
                fun sendInChat(cid: String) {
                    db.collection("chats").document(cid).collection("messages").add(
                        hashMapOf(
                            "senderId" to me,
                            "type" to "text",
                            "status" to "sent",
                            "text" to "↩️ رد على قصتك: $text",
                            "timestamp" to FieldValue.serverTimestamp()
                        )
                    )
                    db.collection("chats").document(cid).update(
                        mapOf(
                            "lastMessage" to "↩️ رد على قصة",
                            "lastMessageTime" to FieldValue.serverTimestamp()
                        )
                    )
                    findViewById<EditText>(R.id.etStoryReply).setText("")
                    Toast.makeText(this, "تم إرسال الرد", Toast.LENGTH_SHORT).show()
                }
                if (chatId != null) {
                    sendInChat(chatId)
                } else {
                    val data = hashMapOf(
                        "participants" to participants,
                        "type" to "direct",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "lastMessage" to "↩️ رد على قصة",
                        "lastMessageTime" to FieldValue.serverTimestamp()
                    )
                    db.collection("chats").add(data).addOnSuccessListener { sendInChat(it.id) }
                }
            }
    }

    private fun showMenu(me: String) {
        val isOwner = me == ownerId
        val items = if (isOwner) {
            arrayOf("حذف القصة", "أرشفة القصة", "المشاهدون")
        } else {
            arrayOf("الإبلاغ", "المشاهدات مخفية")
        }
        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                if (!isOwner) {
                    Toast.makeText(this, "تم استلام البلاغ", Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                when (which) {
                    0 -> deleteStory()
                    1 -> archiveStory()
                    2 -> showViewers()
                }
            }
            .show()
    }

    private fun deleteStory() {
        if (storyId.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("حذف القصة؟")
            .setPositiveButton("حذف") { _, _ ->
                db.collection("stories").document(storyId).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "تم الحذف", Toast.LENGTH_SHORT).show()
                        finish()
                    }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun archiveStory() {
        if (storyId.isEmpty()) return
        db.collection("stories").document(storyId)
            .update(mapOf("status" to "archived", "expiresAtMs" to System.currentTimeMillis()))
            .addOnSuccessListener {
                Toast.makeText(this, "تمت الأرشفة", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun showViewers() {
        if (storyId.isEmpty()) return
        db.collection("stories").document(storyId).get()
            .addOnSuccessListener { doc ->
                val viewers = (doc.get("viewers") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                if (viewers.isEmpty()) {
                    Toast.makeText(this, "لا مشاهدات بعد", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                // جلب الأسماء
                db.collection("users").whereIn("uid", viewers.take(10)).get()
                    .addOnSuccessListener { us ->
                        val names = us.documents.map {
                            it.getString("displayName")
                                ?: it.getString("username")
                                ?: it.id.take(6)
                        }
                        val msg = if (names.isEmpty()) {
                            "عدد المشاهدات: ${viewers.size}"
                        } else {
                            "المشاهدات (${viewers.size}):
" + names.joinToString("
")
                        }
                        AlertDialog.Builder(this)
                            .setTitle("المشاهدون")
                            .setMessage(msg)
                            .setPositiveButton("حسناً", null)
                            .show()
                    }
                    .addOnFailureListener {
                        AlertDialog.Builder(this)
                            .setTitle("المشاهدون")
                            .setMessage("عدد المشاهدات: ${viewers.size}")
                            .setPositiveButton("حسناً", null)
                            .show()
                    }
            }
    }

    companion object {
        const val EXTRA_STORY_ID = "story_id"
        const val EXTRA_OWNER_ID = "owner_id"
        const val EXTRA_URL = "url"
        const val EXTRA_USER_NAME = "user_name"
        const val EXTRA_CAPTION = "caption"
        const val EXTRA_IS_VIDEO = "is_video"
    }
}
