package com.example.mp3player

object MusicActionHandler {
    var onNext: (() -> Unit)? = null
    var onPrevious: (() -> Unit)? = null
    var onTogglePlayPause: (() -> Unit)? = null
    var onSeekTo: ((Long) -> Unit)? = null
}