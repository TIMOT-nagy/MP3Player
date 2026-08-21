package com.example.mp3player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long>
)

object PlaylistManager {
    private const val PREFS_NAME = "playlist_prefs"
    private const val KEY_PLAYLISTS = "playlists_json"

    fun savePlaylists(context: Context, playlists: List<Playlist>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (pl in playlists) {
            val obj = JSONObject().apply {
                put("id", pl.id)
                put("name", pl.name)
                val songIdsArray = JSONArray()
                pl.songIds.forEach { songIdsArray.put(it) }
                put("songIds", songIdsArray)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_PLAYLISTS, jsonArray.toString()).apply()
    }

    fun loadPlaylists(context: Context): List<Playlist> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        val list = mutableListOf<Playlist>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getLong("id")
                val name = obj.getString("name")
                val songIdsArray = obj.getJSONArray("songIds")
                val songIds = mutableListOf<Long>()
                for (j in 0 until songIdsArray.length()) {
                    songIds.add(songIdsArray.getLong(j))
                }
                list.add(Playlist(id, name, songIds))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}