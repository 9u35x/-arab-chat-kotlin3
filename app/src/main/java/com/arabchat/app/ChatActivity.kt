package com.arabchat.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.io.File

class ChatActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: MessageAdapter
    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var chatId: String
    private lateinit var chatTitle: String
    private var chatType: String = "direct"
    private var otherUserId: String? = null
    private var isAdmin: Boolean = false
    private var isMember: Boolean = true

    private var listenerRegistration: ListenerRegistration? = null
    private var isRecording = false
    private var recorder: MediaRecorder? = null
    private var recordFilePath: String = ""
    private var recordStartTime: Long = 0
    private var mediaPlayer: MediaPlayer? = null
    private var nextImageIsTemporary = false

    private val messagesRef by lazy {
        db.collection("chats").document(chatId).collection("messages")
    }
    private val chatRef by lazy {
        db.collection("chats").document(chatId)
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            uploadAndSendImage(uri, nextImageIsTemporary)
        }
        nextImageIsTemporary = false
    }

    private val requestMicPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording() else Toast.makeText(this, "نحتاج إذن الميكروفون", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val currentUser = auth.currentUser
        val chatIdExtra = intent.getStringExtra("chatId")
        if (currentUser == null || chatIdExtra == null) {
            finish()
            return
        }
        chatId = chatIdExtra
        chatTitle = intent.getStringExtra("chatTitle") ?: getString(R.string.general_chat_title)

        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessage)
        val tvSend: TextView = findViewById(R.id.tvSend)
        val tvBack: TextView = findViewById(R.id.tvBack)
        val tvTitle: TextView = findViewById(R.id.tvChatTitle)
        val tvPickImage: TextView = findViewById(R.id.tvPickImage)
        val tvRecordVoice: TextView = findViewById(R.id.tvRecordVoice)
        tvTitle.text = chatTitle

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        rvMessages.layoutManager = layoutManager

        adapter = MessageAdapter(
            mutableListOf(),
            currentUser.uid,
            onPlayVoice = { url -> playVoice(url) },
            onViewTemporaryImage = { message, _ -> markTemporaryViewed(message) },
            onMessageLongClick = { message -> confirmDeleteMessage(message) },
            onImageClick = { url -> showFullImage(url) }
        )
        rvMessages.adapter = adapter

        rvMessages.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom && adapter.itemCount > 0) {
                rvMessages.post { rvMessages.scrollToPosition(adapter.itemCount - 1) }
            }
        }

        tvBack.setOnClickListener { finish() }
        val tvDeleteChat: TextView? = findViewById(R.id.tvDeleteChat)
        tvDeleteChat?.setOnClickListener { confirmDeleteChat() }
        tvSend.setOnClickListener { sendTextMessage() }

        tvTitle.setOnClickListener {
            val profileIntent = android.content.Intent(this, ProfileActivity::class.java)
            profileIntent.putExtra("name", chatTitle)
            val isGroupLike = chatType == "group" || chatType == "channel"
            profileIntent.putExtra("isGroup", isGroupLike)
            if (!isGroupLike && otherUserId != null) {
                profileIntent.putExtra("userId", otherUserId)
            }
            startActivity(profileIntent)
        }
        chatRef.get().addOnSuccessListener { doc ->
            chatType = doc.getString("type") ?: "direct"
            val participants = (doc.get("participants") as? List<*>)?.map { it.toString() } ?: emptyList()
            val admins = (doc.get("admins") as? List<*>)?.map { it.toString() } ?: emptyList()
            val myUid = currentUser.uid
            isMember = myUid in participants
            isAdmin = myUid in admins
            if (chatType == "channel") {
                isAdmin = myUid in admins || participants.firstOrNull() == myUid
            } else {
                isAdmin = true
            }
            otherUserId = participants.firstOrNull { it != myUid }
            applyChannelPermissions()
        }

        tvPickImage.setOnClickListener {
            nextImageIsTemporary = false
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        tvPickImage.setOnLongClickListener {
            nextImageIsTemporary = true
            Toast.makeText(this, "الصورة الجاية راح تكون مؤقتة (تختفي بعد المشاهدة)", Toast.LENGTH_SHORT).show()
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
            true
        }

        tvRecordVoice.setOnClickListener {
            if (!isRecording) {
                val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                if (permission == PackageManager.PERMISSION_GRANTED) {
                    startRecording()
                } else {
                    requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            } else {
                stopRecordingAndSend()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        listenerRegistration = messagesRef
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val messages = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Message::class.java)?.also { it.id = doc.id }
                }
                adapter.submitList(messages)
                if (messages.isNotEmpty()) {
                    rvMessages.scrollToPosition(messages.size - 1)
                }
            }
    }

    override fun onStop() {
        super.onStop()
        listenerRegistration?.remove()
        mediaPlayer?.release()
        mediaPlayer = null
    }


    private var cachedSenderName: String? = null

    private fun currentSenderName(): String {
        val user = auth.currentUser ?: return "مستخدم"
        if (user.isAnonymous) return "ضيف"
        cachedSenderName?.let { return it }
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { snap ->
                val profile = snap.toObject(UserProfile::class.java)
                val name = profile?.bestName()?.takeIf { it.isNotBlank() && it != "مستخدم" }
                if (name != null) cachedSenderName = name
            }
        return cachedSenderName ?: "مستخدم"
    }

    private fun sendTextMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty()) return

        val user = auth.currentUser ?: return

        val message = hashMapOf(
            "senderId" to user.uid,
            "senderName" to currentSenderName(),
            "text" to text,
            "type" to "text",
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        messagesRef.add(message)
            .addOnFailureListener { e ->
                Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
            }

        chatRef.update(
            mapOf(
                "lastMessage" to text,
                "lastMessageTime" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
        )

        etMessage.setText("")
    }

    private fun uploadAndSendImage(uri: Uri, temporary: Boolean) {
        val user = auth.currentUser ?: return
        val senderName = currentSenderName()
        val remotePath = "$chatId/${System.currentTimeMillis()}.jpg"

        Toast.makeText(this, "جاري رفع الصورة...", Toast.LENGTH_SHORT).show()

        SupabaseStorage.uploadFromUri(this, uri, remotePath, "image/jpeg") { publicUrl, error ->
            if (publicUrl == null) {
                Toast.makeText(this, "فشل رفع الصورة: $error", Toast.LENGTH_LONG).show()
                return@uploadFromUri
            }
            val message = hashMapOf(
                "senderId" to user.uid,
                "senderName" to senderName,
                "type" to "image",
                "mediaUrl" to publicUrl,
                "isTemporary" to temporary,
                "viewed" to false,
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            messagesRef.add(message)
            chatRef.update(
                mapOf(
                    "lastMessage" to if (temporary) "📷 صورة مؤقتة" else "📷 صورة",
                    "lastMessageTime" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            )
        }
    }

    private fun startRecording() {
        val fileName = "voice_${System.currentTimeMillis()}.m4a"
        recordFilePath = File(cacheDir, fileName).absolutePath

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(recordFilePath)
            try {
                prepare()
                start()
                isRecording = true
                recordStartTime = System.currentTimeMillis()
                Toast.makeText(this@ChatActivity, "🎙 جاري التسجيل... اضغط مرة ثانية للإيقاف", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "تعذر بدء التسجيل", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopRecordingAndSend() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
            // ignore
        }
        recorder = null
        isRecording = false
        val durationMs = System.currentTimeMillis() - recordStartTime

        if (durationMs < 800) {
            Toast.makeText(this, "التسجيل قصير جداً", Toast.LENGTH_SHORT).show()
            File(recordFilePath).delete()
            return
        }

        val user = auth.currentUser ?: return
        val senderName = currentSenderName()
        val remotePath = "$chatId/${System.currentTimeMillis()}.m4a"
        val localFile = File(recordFilePath)

        Toast.makeText(this, "جاري رفع الرسالة الصوتية...", Toast.LENGTH_SHORT).show()

        SupabaseStorage.uploadFromFile(localFile, remotePath, "audio/mp4") { publicUrl, error ->
            if (publicUrl == null) {
                Toast.makeText(this, "فشل رفع الرسالة الصوتية: $error", Toast.LENGTH_LONG).show()
                return@uploadFromFile
            }
            val message = hashMapOf(
                "senderId" to user.uid,
                "senderName" to senderName,
                "type" to "voice",
                "mediaUrl" to publicUrl,
                "durationMs" to durationMs,
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            messagesRef.add(message)
            chatRef.update(
                mapOf(
                    "lastMessage" to "🎤 رسالة صوتية",
                    "lastMessageTime" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            )
            localFile.delete()
        }
    }

    private fun playVoice(url: String) {
        if (url.isEmpty()) {
            Toast.makeText(this, "لا يوجد ملف صوتي", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, _, _ ->
                    Toast.makeText(this@ChatActivity, "فشل تشغيل الصوت", Toast.LENGTH_SHORT).show()
                    true
                }
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "فشل تشغيل الصوت: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showFullImage(url: String) {
        if (url.isEmpty()) {
            Toast.makeText(this, "رابط الصورة فارغ", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = android.content.Intent(this, FullImageActivity::class.java)
        intent.putExtra(FullImageActivity.EXTRA_URL, url)
        startActivity(intent)
    }

    private fun markTemporaryViewed(message: Message) {
        if (message.id.isEmpty()) return
        messagesRef.document(message.id).update("viewed", true)
    }

    private fun applyChannelPermissions() {
        if (chatType != "channel") return
        val etMessage = findViewById<android.widget.EditText>(R.id.etMessage)
        val tvSend = findViewById<android.view.View>(R.id.tvSend)
        val tvPick = findViewById<android.view.View>(R.id.tvPickImage)
        val canPost = isAdmin
        etMessage?.isEnabled = canPost
        etMessage?.hint = if (canPost) getString(R.string.message_hint) else getString(R.string.channel_readonly_hint)
        tvSend?.isEnabled = canPost
        tvPick?.isEnabled = canPost
        if (!canPost) etMessage?.setText("")
    }


    private fun confirmDeleteMessage(message: Message) {
        val me = auth.currentUser?.uid ?: return
        // Own messages: always. Channel/group admin: any message.
        // يمكن حذف رسائلك فقط (المشرف يحذف أيضاً في القناة/المجموعة)
        val canDelete = message.senderId == me || (isAdmin && (chatType == "channel" || chatType == "group"))
        if (!canDelete) {
            Toast.makeText(this, R.string.cannot_delete_message, Toast.LENGTH_SHORT).show()
            return
        }
        if (message.id.isEmpty()) return
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.delete_message)
            .setMessage(R.string.delete_message_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                messagesRef.document(message.id).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, R.string.message_deleted, Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteChat() {
        val me = auth.currentUser?.uid ?: return
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.delete_chat)
            .setMessage(R.string.delete_chat_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteOrLeaveChat(me)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteOrLeaveChat(myUid: String) {
        chatRef.get().addOnSuccessListener { doc ->
            if (!doc.exists()) {
                finish()
                return@addOnSuccessListener
            }
            val type = doc.getString("type") ?: "direct"
            val participants = (doc.get("participants") as? List<*>)?.map { it.toString() }?.toMutableList()
                ?: mutableListOf()
            val admins = (doc.get("admins") as? List<*>)?.map { it.toString() } ?: emptyList()

            when {
                type == "direct" -> {
                    // Delete chat document for both (simple 1:1)
                    deleteChatFully()
                }
                type == "channel" || type == "group" -> {
                    val isOwner = myUid in admins || participants.firstOrNull() == myUid
                    if (isOwner && participants.size <= 1) {
                        deleteChatFully()
                    } else {
                        // Leave: remove from participants/admins
                        participants.remove(myUid)
                        val newAdmins = admins.filter { it != myUid }
                        chatRef.update(
                            mapOf(
                                "participants" to participants,
                                "admins" to newAdmins
                            )
                        ).addOnSuccessListener {
                            Toast.makeText(this, R.string.left_chat, Toast.LENGTH_SHORT).show()
                            finish()
                        }.addOnFailureListener { e ->
                            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                else -> deleteChatFully()
            }
        }
    }

    private fun deleteChatFully() {
        // Delete messages then chat
        messagesRef.get().addOnSuccessListener { snap ->
            val batch = db.batch()
            for (d in snap.documents) {
                batch.delete(d.reference)
            }
            batch.delete(chatRef)
            batch.commit()
                .addOnSuccessListener {
                    Toast.makeText(this, R.string.chat_deleted, Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                }
        }.addOnFailureListener {
            chatRef.delete().addOnCompleteListener { finish() }
        }
    }

}
