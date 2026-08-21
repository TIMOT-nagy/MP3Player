package com.example.mp3player

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

fun fetchAudioFiles(context: Context): List<Song> {
    val songList = mutableListOf<Song>()
    val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DISPLAY_NAME
    )

    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

    context.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val rawTitle = cursor.getString(titleColumn) ?: ""
            val rawArtist = cursor.getString(artistColumn) ?: ""
            val albumId = cursor.getLong(albumIdColumn)
            val fileName = cursor.getString(displayNameColumn) ?: ""

            val (parsedArtist, parsedTitle) = parseFileNameAndClean(rawArtist, rawTitle, fileName)
            val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

            songList.add(
                Song(
                    id = id,
                    title = parsedTitle,
                    artist = parsedArtist,
                    contentUri = contentUri,
                    albumId = albumId,
                    coverUri = null // Ezt mostantól az okos borítóbetöltő intézi!
                )
            )
        }
    }
    return songList
}

private fun parseFileNameAndClean(rawArtist: String, rawTitle: String, fileName: String): Pair<String, String> {
    var artist = rawArtist.trim()
    var title = rawTitle.trim()

    val isUnknownArtist = artist.isEmpty() || artist.contains("<unknown>", ignoreCase = true) || artist.contains("ismeretlen", ignoreCase = true)
    val isUnknownTitle = title.isEmpty() || title.startsWith("track", ignoreCase = true) || title.contains("aud-", ignoreCase = true)

    if (isUnknownArtist || isUnknownTitle) {
        val cleanFileName = fileName.substringBeforeLast(".")
        if (cleanFileName.contains("-")) {
            val parts = cleanFileName.split("-", limit = 2)
            if (isUnknownArtist) artist = parts[0].trim()
            if (isUnknownTitle) title = parts[1].trim()
        } else {
            if (isUnknownTitle) title = cleanFileName.trim()
            if (isUnknownArtist) artist = "Ismeretlen előadó"
        }
    }

    val filterRegex = Regex("(?i)\\b(official video|official audio|lyric video|lyrics|video|hd|hq|4k|320kbps|128kbps|remastered|full song)\\b|\\[.*?\\]|\\(.*?\\)")
    title = title.replace(filterRegex, "").replace("\\s+".toRegex(), " ").trim()
    artist = artist.replace(filterRegex, "").replace("\\s+".toRegex(), " ").trim()

    if (artist.isEmpty()) artist = "Ismeretlen előadó"
    if (title.isEmpty()) title = "Ismeretlen cím"

    return Pair(artist, title)
}