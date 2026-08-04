package com.arabchat.app

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class VideoPlayerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (url.isEmpty()) {
            Toast.makeText(this, "رابط الفيديو فارغ", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val videoView = findViewById<VideoView>(R.id.videoView)
        val progress = findViewById<ProgressBar>(R.id.progressVideo)
        findViewById<TextView>(R.id.tvCloseVideo).setOnClickListener { finish() }
        videoView.setMediaController(null)
progress.visibility = View.VISIBLE
        videoView.setOnPreparedListener { mp ->
            progress.visibility = View.GONE
            mp.start()
        }
        videoView.setOnErrorListener { _, _, _ ->
            progress.visibility = View.GONE
            Toast.makeText(this, "تعذر تشغيل الفيديو", Toast.LENGTH_SHORT).show()
            true
        }
        videoView.setVideoURI(Uri.parse(url))
        videoView.requestFocus()
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}
