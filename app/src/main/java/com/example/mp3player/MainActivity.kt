package com.example.mp3player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Értesítési engedély elkérése (Android 13+)
        checkNotificationPermission()

        // Compose felület betöltése
        setContent {
            MainScreen()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    // Ezt a függvényt hívhatod meg a zene indításakor
    fun startMusicService(title: String, artist: String, uriString: String, duration: Long, position: Long) {
        val intent = Intent(this, MusicService::class.java).apply {
            putExtra("TITLE", title)
            putExtra("ARTIST", artist)
            putExtra("URI_STRING", uriString)
            putExtra("DURATION", duration)
            putExtra("POSITION", position)
            putExtra("IS_PLAYING", true)
        }
        ContextCompat.startForegroundService(this, intent)
    }
}