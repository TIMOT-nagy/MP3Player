package com.example.mp3player

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@SuppressLint("InlinedApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MusicPlayerViewModel = viewModel()) {
    val context = LocalContext.current
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }

    var isFullScreenPlayerOpen by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }

    BackHandler(enabled = isFullScreenPlayerOpen) {
        isFullScreenPlayerOpen = false
    }

    LaunchedEffect(Unit) {
        viewModel.initController()
    }

    val backgroundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.setAndSaveBackgroundImage(uri)
    }

    val permissionsToRequest = mutableListOf<String>().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_AUDIO)
            add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.READ_MEDIA_AUDIO] == true ||
                permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        if (audioGranted) {
            songs = fetchAudioFiles(context)
            viewModel.updateSongsList(songs)
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissionsToRequest)
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false; newPlaylistName = "" },
            containerColor = Color(0xFF1F2833),
            title = { Text("New playlist", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist name", color = Color.LightGray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.Cyan, unfocusedBorderColor = Color.Gray
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        val createdPl = viewModel.createPlaylist(newPlaylistName)
                        songToAddToPlaylist?.let { song ->
                            viewModel.addSongToPlaylist(createdPl.id, song.id)
                            songToAddToPlaylist = null
                        }
                        newPlaylistName = ""
                        showCreatePlaylistDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false; newPlaylistName = "" }) { Text("Cancel", color = Color.LightGray) }
            }
        )
    }

    if (songToAddToPlaylist != null) {
        AlertDialog(
            onDismissRequest = { songToAddToPlaylist = null },
            containerColor = Color(0xFF1F2833),
            title = { Text("Add to playlist", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val currentPlaylists = viewModel.playlists
                    if (currentPlaylists.isEmpty()) {
                        Text("You don't have any playlists yet.", color = Color.LightGray)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(currentPlaylists) { pl ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        songToAddToPlaylist?.let { song -> viewModel.addSongToPlaylist(pl.id, song.id) }
                                        songToAddToPlaylist = null
                                    }.padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.listak),
                                        contentDescription = "Playlist icon",
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(pl.name, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { showCreatePlaylistDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("➕ Create new playlist") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { songToAddToPlaylist = null }) { Text("Cancel", color = Color.LightGray) }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val bgUri = viewModel.backgroundImageUri
        if (bgUri != null) {
            AsyncImage(model = bgUri, contentDescription = "Background image", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color(0xFF1F2833), Color(0xFF0B0C10)))))
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(painter = painterResource(id = R.drawable.logo), contentDescription = "Logo", modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Music Player", color = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { backgroundLauncher.launch("image/*") }) {
                            Image(
                                painter = painterResource(id = R.drawable.wallpaper),
                                contentDescription = "Change background",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color.Black.copy(alpha = 0.4f)) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = {
                            Image(
                                painter = painterResource(id = R.drawable.konyvtar),
                                contentDescription = "Library",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text("Library", color = Color.White) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = {
                            Image(
                                painter = painterResource(id = R.drawable.listak),
                                contentDescription = "Playlists",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text("Playlists", color = Color.White) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = {
                            Image(
                                painter = painterResource(id = R.drawable.dj),
                                contentDescription = "Equalizer",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text("Equalizer", color = Color.White) }
                    )
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        0 -> {
                            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(songs) { song ->
                                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.35f)).border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).clickable { viewModel.playSong(song) }.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                            SongCoverImage(song = song, context = context, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = song.title, color = Color.White, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(text = song.artist, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            IconButton(onClick = { songToAddToPlaylist = song }) { Text("➕", style = MaterialTheme.typography.titleMedium) }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            val activePlaylist = viewModel.playlists.find { it.id == selectedPlaylistId }
                            if (selectedPlaylistId != null && activePlaylist != null) {
                                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        IconButton(onClick = { selectedPlaylistId = null }) { Text("⬅️", style = MaterialTheme.typography.titleLarge) }
                                        Text(text = activePlaylist.name, color = Color.White, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                                        Button(onClick = { viewModel.playPlaylist(activePlaylist) }) { Text("▶️ Play") }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    val playlistSongs = activePlaylist.songIds.mapNotNull { id -> songs.find { it.id == id } }
                                    if (playlistSongs.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("The playlist is empty.", color = Color.LightGray) }
                                    } else {
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            itemsIndexed(playlistSongs) { index, song ->
                                                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.35f)).border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).clickable { viewModel.playPlaylist(activePlaylist, index) }.padding(12.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                        SongCoverImage(song = song, context = context, modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)))
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(song.title, color = Color.White, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            Text(song.artist, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        }
                                                        IconButton(onClick = { viewModel.removeSongFromPlaylist(activePlaylist.id, song.id) }) { Text("🗑️") }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                    Button(onClick = { showCreatePlaylistDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("➕ Create new playlist") }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    val playlists = viewModel.playlists
                                    if (playlists.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("You don't have any playlists yet.", color = Color.LightGray) }
                                    } else {
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            items(playlists) { pl ->
                                                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.35f)).border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).clickable { selectedPlaylistId = pl.id }.padding(16.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                        Image(
                                                            painter = painterResource(id = R.drawable.listak),
                                                            contentDescription = "Playlist icon",
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(pl.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                                                            Text("${pl.songIds.size} songs", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                                        }
                                                        IconButton(onClick = { viewModel.deletePlaylist(pl.id) }) { Text("🗑️") }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF1E1E24)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp)).padding(16.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Equalizer", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Switch(
                                        checked = viewModel.isEqEnabled,
                                        onCheckedChange = { viewModel.toggleEqualizer(it) },
                                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFFF9800))
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))

                                val freqs = viewModel.bandFrequencies
                                if (freqs.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Play a song to activate the Equalizer!", color = Color.Gray)
                                    }
                                } else {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        item {
                                            val isCustom = viewModel.currentPresetIndex == (-1).toShort()
                                            Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (isCustom) Color(0xFFFF9800) else Color.DarkGray).padding(horizontal = 16.dp, vertical = 8.dp)) {
                                                Text("Custom", color = if (isCustom) Color.White else Color.LightGray)
                                            }
                                        }
                                        itemsIndexed(viewModel.eqPresets) { index, presetName ->
                                            val pIndex = index.toShort()
                                            val isSelected = viewModel.currentPresetIndex == pIndex
                                            Box(
                                                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (isSelected) Color(0xFFFF9800) else Color(0xFF2A2A35)).clickable { if (viewModel.isEqEnabled) viewModel.applyPreset(pIndex) }.padding(horizontal = 16.dp, vertical = 8.dp)
                                            ) {
                                                Text(text = presetName, color = if (isSelected) Color.White else Color.LightGray)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))

                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        items(freqs) { eqBand ->
                                            val currentLevel = viewModel.bandLevels[eqBand.index] ?: 0f
                                            Column {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(text = if (eqBand.centerFreqHz >= 1000) "${eqBand.centerFreqHz / 1000}kHz" else "${eqBand.centerFreqHz}Hz", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                                    Text(text = "${if (currentLevel > 0) "+" else ""}${(currentLevel / 100).toInt()} dB", color = Color(0xFFFF9800), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                                }
                                                Slider(
                                                    value = currentLevel,
                                                    onValueChange = { newValue -> viewModel.setBandLevel(eqBand.index, newValue) },
                                                    valueRange = viewModel.minEqLevel..viewModel.maxEqLevel,
                                                    enabled = viewModel.isEqEnabled,
                                                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800), inactiveTrackColor = Color.DarkGray)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // MINI PLAYER
                if (!isFullScreenPlayerOpen && viewModel.currentSong != null) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A2A35)).clickable { isFullScreenPlayerOpen = true }.padding(horizontal = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                            SongCoverImage(song = viewModel.currentSong, context = context, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = viewModel.currentSong?.title ?: "Unknown", color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(text = viewModel.currentSong?.artist ?: "Unknown", color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { viewModel.togglePlayPause() }) {
                                Image(
                                    painter = painterResource(id = R.drawable.startstop),
                                    contentDescription = "Start/Stop",
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isFullScreenPlayerOpen,
            enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }, animationSpec = tween(durationMillis = 400)) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight }, animationSpec = tween(durationMillis = 400)) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            FullScreenPlayerView(viewModel = viewModel, context = context, onClose = { isFullScreenPlayerOpen = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenPlayerView(viewModel: MusicPlayerViewModel, context: Context, onClose: () -> Unit) {
    val song = viewModel.currentSong
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offsetY > 250f) {
                            onClose()
                            offsetY = 0f
                        } else {
                            offsetY = 0f
                        }
                    },
                    onDragCancel = { offsetY = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = offsetY + dragAmount
                        if (newOffset >= 0f) {
                            offsetY = newOffset
                        }
                    }
                )
            }
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.4f))
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Top-left icon was removed; the spacer centers the title
                Spacer(modifier = Modifier.size(48.dp))
                Text(text = "Now Playing", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.weight(0.5f))
            SongCoverImage(song = song, context = context, modifier = Modifier.fillMaxWidth(0.85f).aspectRatio(1f).clip(RoundedCornerShape(32.dp)).shadow(20.dp, RoundedCornerShape(32.dp)))
            Spacer(modifier = Modifier.weight(0.5f))
            Text(text = song?.title ?: "Select a song", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = song?.artist ?: "Unknown artist", color = Color.LightGray, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(32.dp))
            Slider(value = viewModel.progress, onValueChange = { viewModel.seekTo(it) }, modifier = Modifier.fillMaxWidth(), colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.Cyan, inactiveTrackColor = Color.White.copy(alpha = 0.3f)))
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Image(
                        painter = painterResource(id = R.drawable.random),
                        contentDescription = "Shuffle",
                        modifier = Modifier.size(32.dp),
                        colorFilter = ColorFilter.tint(if (viewModel.isShuffleEnabled) Color.Cyan else Color.Gray)
                    )
                }
                IconButton(onClick = { viewModel.playPrevious() }, modifier = Modifier.size(56.dp)) {
                    Image(painter = painterResource(id = R.drawable.back), contentDescription = "Previous", modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { viewModel.togglePlayPause() }, modifier = Modifier.size(72.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.startstop),
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(64.dp)
                    )
                }
                IconButton(onClick = { viewModel.playNext() }, modifier = Modifier.size(56.dp)) {
                    Image(painter = painterResource(id = R.drawable.next), contentDescription = "Next", modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { viewModel.toggleRepeat() }) {
                    Image(
                        painter = painterResource(id = R.drawable.repeat),
                        contentDescription = "Repeat",
                        modifier = Modifier.size(32.dp),
                        colorFilter = ColorFilter.tint(if (viewModel.isRepeatEnabled) Color.Cyan else Color.Gray)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SongCoverImage(song: Song?, context: Context, modifier: Modifier = Modifier) {
    var imageModel by remember(song?.id) { mutableStateOf<Any?>(R.drawable.logo) }
    LaunchedEffect(song?.id) {
        if (song == null) { imageModel = R.drawable.logo; return@LaunchedEffect }
        withContext(Dispatchers.IO) {
            val embedded = getEmbeddedPicture(context, song.contentUri)
            if (embedded != null) { imageModel = embedded }
            else {
                val onlineUrl = fetchOnlineCover(song.artist, song.title)
                if (onlineUrl != null) {
                    imageModel = onlineUrl
                    song.coverUri = onlineUrl
                } else {
                    imageModel = R.drawable.logo
                }
            }
        }
    }
    AsyncImage(
        model = imageModel,
        contentDescription = "Cover image",
        error = painterResource(id = R.drawable.logo),
        placeholder = painterResource(id = R.drawable.logo),
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}