package com.example.mp3player

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val contentUri: Uri,
    val albumId: Long,
    var coverUri: String? = null
)