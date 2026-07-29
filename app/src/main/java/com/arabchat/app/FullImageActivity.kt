package com.arabchat.app

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import android.graphics.drawable.Drawable

class FullImageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_image)

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val iv = findViewById<ImageView>(R.id.ivFullImage)
        val progress = findViewById<ProgressBar>(R.id.progressFullImage)
        val tvClose = findViewById<TextView>(R.id.tvCloseFullImage)
        val root = findViewById<View>(R.id.rootFullImage)

        tvClose.setOnClickListener { finish() }
        root.setOnClickListener { finish() }
        iv.setOnClickListener { finish() }

        if (url.isEmpty()) {
            Toast.makeText(this, "رابط الصورة فارغ", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        progress.visibility = View.VISIBLE
        Glide.with(this)
            .load(url)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    progress.visibility = View.GONE
                    Toast.makeText(this@FullImageActivity, "فشل تحميل الصورة", Toast.LENGTH_LONG).show()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    progress.visibility = View.GONE
                    return false
                }
            })
            .into(iv)
    }

    companion object {
        const val EXTRA_URL = "image_url"
    }
}
