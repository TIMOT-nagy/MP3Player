package com.example.mp3player

import android.app.Application
import android.content.Intent
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.net.Uri
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.time.Duration.Companion.seconds

data class EqBand(val index: Short, val centerFreqHz: Int)

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    // --- LEJÁTSZÓ ÁLLAPOTOK ---
    var currentSong by mutableStateOf<Song?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set

    // Keverés és ismétlés állapotai
    var isShuffleEnabled by mutableStateOf(false)
        private set
    var isRepeatEnabled by mutableStateOf(false)
        private set

    // --- LISTÁK ÉS HÁTTÉRKÉP ---
    private var allSongs = listOf<Song>()
    private var currentQueue = listOf<Song>()
    private var currentQueueIndex = 0

    var playlists = mutableStateListOf<Playlist>()
        private set
    var backgroundImageUri by mutableStateOf<Uri?>(null)
        private set

    // --- EQUALIZER VÁLTOZÓK ---
    var equalizer: Equalizer? = null
        private set
    var isEqEnabled by mutableStateOf(false)
        private set
    var minEqLevel by mutableFloatStateOf(0f)
        private set
    var maxEqLevel by mutableFloatStateOf(0f)
        private set

    val bandFrequencies = mutableStateListOf<EqBand>()
    val bandLevels = mutableStateMapOf<Short, Float>()

    var eqPresets = mutableStateListOf<String>()
        private set
    var currentPresetIndex by mutableStateOf<Short>(-1)
        private set

    // ==========================================
    // 1. INICIALIZÁLÁS ÉS MENTETT HÁTTÉR BETÖLTÉSE
    // ==========================================
    fun initController() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer()
            mediaPlayer?.setOnCompletionListener {
                playNext()
            }
        }

        MusicActionHandler.onNext = { playNext() }
        MusicActionHandler.onPrevious = { playPrevious() }
        MusicActionHandler.onTogglePlayPause = { togglePlayPause() }

        // Értesítésről érkező tekerés kezelése
        MusicActionHandler.onSeekTo = { positionMs ->
            mediaPlayer?.seekTo(positionMs.toInt())
            updateNotification()
        }

        loadSavedBackground()
    }

    private fun loadSavedBackground() {
        val file = File(getApplication<Application>().filesDir, "custom_bg.jpg")
        if (file.exists()) {
            backgroundImageUri = Uri.fromFile(file)
        }
    }

    fun setAndSaveBackgroundImage(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri)
                val file = File(context.filesDir, "custom_bg.jpg")
                val outputStream = FileOutputStream(file)
                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                withContext(Dispatchers.Main) {
                    backgroundImageUri = Uri.fromFile(file)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateSongsList(songs: List<Song>) {
        allSongs = songs
        if (currentQueue.isEmpty()) {
            currentQueue = songs
        }
    }

    // ==========================================
    // 2. LEJÁTSZÁS VEZÉRLÉS
    // ==========================================
    fun playSong(song: Song) {
        currentQueue = allSongs
        currentQueueIndex = currentQueue.indexOf(song)
        startPlaying(song)
    }

    fun playPlaylist(playlist: Playlist, startIndex: Int = 0) {
        val pSongs = playlist.songIds.mapNotNull { id -> allSongs.find { it.id == id } }
        if (pSongs.isNotEmpty()) {
            currentQueue = pSongs
            currentQueueIndex = if (startIndex in pSongs.indices) startIndex else 0
            startPlaying(currentQueue[currentQueueIndex])
        }
    }

    private fun startPlaying(song: Song) {
        try {
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(getApplication<Application>(), song.contentUri)
            mediaPlayer?.prepare()
            mediaPlayer?.start()

            currentSong = song
            isPlaying = true

            mediaPlayer?.audioSessionId?.let { setupEqualizer(it) }

            updateNotification()
            startProgressTracker()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateNotification() {
        currentSong?.let { song ->
            val intent = Intent(getApplication(), MusicService::class.java).apply {
                putExtra("TITLE", song.title)
                putExtra("ARTIST", song.artist)
                putExtra("IS_PLAYING", isPlaying)
                putExtra("URI_STRING", song.contentUri.toString())
                putExtra("DURATION", mediaPlayer?.duration?.toLong() ?: 0L)
                putExtra("POSITION", mediaPlayer?.currentPosition?.toLong() ?: 0L)
            }
            ContextCompat.startForegroundService(getApplication(), intent)
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                isPlaying = false
            } else {
                mp.start()
                isPlaying = true
                startProgressTracker()
            }
            updateNotification()
        }
    }

    fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
    }

    fun toggleRepeat() {
        isRepeatEnabled = !isRepeatEnabled
    }

    fun playNext() {
        if (currentQueue.isNotEmpty()) {
            if (isShuffleEnabled) {
                currentQueueIndex = currentQueue.indices.random()
            } else {
                if (currentQueueIndex == currentQueue.size - 1 && !isRepeatEnabled) {
                    mediaPlayer?.pause()
                    isPlaying = false
                    updateNotification()
                    return
                }
                currentQueueIndex = (currentQueueIndex + 1) % currentQueue.size
            }
            startPlaying(currentQueue[currentQueueIndex])
        }
    }

    fun playPrevious() {
        if (currentQueue.isNotEmpty()) {
            currentQueueIndex = if (currentQueueIndex - 1 < 0) currentQueue.size - 1 else currentQueueIndex - 1
            startPlaying(currentQueue[currentQueueIndex])
        }
    }

    fun seekTo(newProgress: Float) {
        mediaPlayer?.let { mp ->
            val duration = mp.duration
            val newPosition = (duration * newProgress).toInt()
            mp.seekTo(newPosition)
            progress = newProgress
            updateNotification()
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isPlaying) {
                mediaPlayer?.let { mp ->
                    if (mp.duration > 0) {
                        progress = mp.currentPosition.toFloat() / mp.duration.toFloat()
                    }
                }
                delay(1.seconds)
            }
        }
    }

    // ==========================================
    // 3. LEJÁTSZÁSI LISTA (PLAYLIST) KEZELÉS
    // ==========================================
    fun createPlaylist(name: String): Playlist {
        val newId = System.currentTimeMillis()
        val pl = Playlist(id = newId, name = name, songIds = listOf())
        playlists.add(pl)
        return pl
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        val plIndex = playlists.indexOfFirst { it.id == playlistId }
        if (plIndex != -1) {
            val pl = playlists[plIndex]
            if (!pl.songIds.contains(songId)) {
                val updatedSongs = pl.songIds.toMutableList()
                updatedSongs.add(songId)
                playlists[plIndex] = pl.copy(songIds = updatedSongs)
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        val plIndex = playlists.indexOfFirst { it.id == playlistId }
        if (plIndex != -1) {
            val pl = playlists[plIndex]
            val updatedSongs = pl.songIds.toMutableList()
            updatedSongs.remove(songId)
            playlists[plIndex] = pl.copy(songIds = updatedSongs)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        playlists.removeAll { it.id == playlistId }
    }

    // ==========================================
    // 4. EQUALIZER
    // ==========================================
    fun setupEqualizer(sessionId: Int) {
        try {
            equalizer?.release()
            equalizer = Equalizer(0, sessionId).apply {
                enabled = isEqEnabled

                val range = bandLevelRange
                minEqLevel = range[0].toFloat()
                maxEqLevel = range[1].toFloat()

                bandFrequencies.clear()
                val bands = numberOfBands
                for (i in 0 until bands) {
                    val bandIndex = i.toShort()
                    bandFrequencies.add(EqBand(index = bandIndex, centerFreqHz = getCenterFreq(bandIndex) / 1000))
                    bandLevels[bandIndex] = getBandLevel(bandIndex).toFloat()
                }

                eqPresets.clear()
                val presetCount = numberOfPresets
                for (i in 0 until presetCount) {
                    eqPresets.add(getPresetName(i.toShort()))
                }
            }

            if (currentPresetIndex >= 0) {
                applyPreset(currentPresetIndex)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleEqualizer(enabled: Boolean) {
        isEqEnabled = enabled
        equalizer?.enabled = enabled
    }

    fun setBandLevel(band: Short, level: Float) {
        currentPresetIndex = -1
        bandLevels[band] = level
        equalizer?.setBandLevel(band, level.toInt().toShort())
    }

    fun applyPreset(presetIndex: Short) {
        equalizer?.let { eq ->
            try {
                eq.usePreset(presetIndex)
                currentPresetIndex = presetIndex
                bandFrequencies.forEach { band ->
                    bandLevels[band.index] = eq.getBandLevel(band.index).toFloat()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        mediaPlayer?.release()
        equalizer?.release()
        progressJob?.cancel()
    }
}