package com.arabchat.app

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object SupabaseStorage {

    private const val SUPABASE_URL = "https://qpmvaexlxqkxrlgzbryw.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_wRcqwkUhz1j9xNVNPTtWTA_Z6LCBDxB"
    private const val BUCKET = "chat-media"

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Uploads a file (from a content Uri, e.g. picked image) to Supabase Storage.
     * Calls back with the public URL on success, or an error message on failure.
     */
    fun uploadFromUri(
        context: Context,
        uri: Uri,
        remotePath: String,
        mimeType: String,
        onResult: (publicUrl: String?, error: String?) -> Unit
    ) {
        Thread {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    postResult(onResult, null, "تعذرت قراءة الملف")
                    return@Thread
                }
                upload(bytes, remotePath, mimeType, onResult)
            } catch (e: Exception) {
                postResult(onResult, null, e.message ?: "خطأ غير معروف")
            }
        }.start()
    }

    /**
     * Uploads a local file (e.g. a recorded voice note) to Supabase Storage.
     */
    fun uploadFromFile(
        file: File,
        remotePath: String,
        mimeType: String,
        onResult: (publicUrl: String?, error: String?) -> Unit
    ) {
        Thread {
            try {
                val bytes = file.readBytes()
                upload(bytes, remotePath, mimeType, onResult)
            } catch (e: Exception) {
                postResult(onResult, null, e.message ?: "خطأ غير معروف")
            }
        }.start()
    }

    private fun upload(
        bytes: ByteArray,
        remotePath: String,
        mimeType: String,
        onResult: (String?, String?) -> Unit
    ) {
        try {
            val uploadUrl = URL("$SUPABASE_URL/storage/v1/object/$BUCKET/$remotePath")
            val connection = uploadUrl.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("apikey", SUPABASE_KEY)
            connection.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
            connection.setRequestProperty("Content-Type", mimeType)
            connection.setRequestProperty("x-upsert", "true")
            connection.outputStream.use { it.write(bytes) }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val publicUrl = "$SUPABASE_URL/storage/v1/object/public/$BUCKET/$remotePath"
                postResult(onResult, publicUrl, null)
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                postResult(onResult, null, "فشل الرفع (رمز $responseCode): $errorBody")
            }
            connection.disconnect()
        } catch (e: Exception) {
            postResult(onResult, null, e.message ?: "خطأ بالاتصال")
        }
    }

    private fun postResult(
        onResult: (String?, String?) -> Unit,
        url: String?,
        error: String?
    ) {
        mainHandler.post { onResult(url, error) }
    }
}
