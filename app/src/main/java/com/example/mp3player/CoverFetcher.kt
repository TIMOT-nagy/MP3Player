package com.example.mp3player

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// 1. Saját borítókép kinyerése közvetlenül az MP3 fájl belsejéből
fun getEmbeddedPicture(context: Context, uri: Uri): ByteArray? {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val picture = retriever.embeddedPicture
        retriever.release()
        picture
    } catch (_: Exception) {
        null
    }
}

// 2. Online borító keresése (ha a fájlban nem volt kép)
suspend fun fetchOnlineCover(artist: String, title: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val cleanArtist = if (artist.contains("ismeretlen", ignoreCase = true)) "" else artist
            val cleanTitle = title.replace("Ismeretlen cím", "")

            val searchQuery = "$cleanArtist $cleanTitle".trim()
            if (searchQuery.isEmpty()) return@withContext null

            val encodedQuery = URLEncoder.encode(searchQuery, "UTF-8")
            val urlString = "https://itunes.apple.com/search?term=$encodedQuery&entity=song&limit=1"

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 4000
            connection.readTimeout = 4000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val results = json.getJSONArray("results")

                if (results.length() > 0) {
                    val rawUrl = results.getJSONObject(0).optString("artworkUrl100", null)
                    return@withContext rawUrl?.replace("100x100bb", "600x600bb")
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}