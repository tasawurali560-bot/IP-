package com.example.media

import android.content.Context
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.R
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.SimulatorTopBar
import com.example.ui.theme.DesertTitanium
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.SapphireBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MusicPlayerState(
    val tracks: List<AudioTrack> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val repeatOne: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 1L
) {
    val currentTrack: AudioTrack? get() = tracks.getOrNull(currentIndex)
}

class MusicPlayerViewModel : ViewModel() {

    private val _state = MutableStateFlow(MusicPlayerState())
    val state: StateFlow<MusicPlayerState> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val appCtx = context.applicationContext
        val exo = ExoPlayer.Builder(appCtx).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(isPlaying = isPlaying) }
                }

                override fun onMediaItemTransition(mediaItem: Media3Item?, reason: Int) {
                    _state.update {
                        it.copy(
                            currentIndex = currentMediaItemIndex.coerceAtLeast(0),
                            durationMs = duration.coerceAtLeast(1L)
                        )
                    }
                }
            })
        }
        player = exo

        viewModelScope.launch {
            val tracks = MediaRepository(appCtx).ensureBuiltInAudioTracks()
            _state.update { it.copy(tracks = tracks) }
            exo.setMediaItems(tracks.map { Media3Item.fromUri(it.uri) })
            exo.prepare()
        }

        viewModelScope.launch {
            while (true) {
                val p = player
                if (p != null && p.isPlaying) {
                    _state.update {
                        it.copy(
                            positionMs = p.currentPosition.coerceAtLeast(0L),
                            durationMs = p.duration.coerceAtLeast(1L)
                        )
                    }
                }
                delay(400)
            }
        }
    }

    fun playTrack(index: Int) {
        val exo = player ?: return
        if (index !in _state.value.tracks.indices) return
        exo.seekTo(index, 0L)
        exo.playWhenReady = true
        exo.play()
        _state.update { it.copy(currentIndex = index) }
    }

    fun togglePlayPause() {
        val exo = player ?: return
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    fun nextTrack() {
        val exo = player ?: return
        if (exo.hasNextMediaItem()) exo.seekToNextMediaItem()
    }

    fun previousTrack() {
        val exo = player ?: return
        if (exo.hasPreviousMediaItem()) exo.seekToPreviousMediaItem() else exo.seekTo(0L)
    }

    fun seekTo(ms: Long) {
        player?.seekTo(ms)
        _state.update { it.copy(positionMs = ms) }
    }

    fun toggleRepeat() {
        val next = !_state.value.repeatOne
        player?.repeatMode = if (next) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
        _state.update { it.copy(repeatOne = next) }
    }

    fun importAudio(context: Context, uri: android.net.Uri) {
        viewModelScope.launch {
            val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
            } ?: "Audio_${System.currentTimeMillis()}.mp3"

            val imported = MediaRepository(context).importAudioTrack(uri, name)
            if (imported != null) {
                val updated = _state.value.tracks + imported
                _state.update { it.copy(tracks = updated) }
                player?.addMediaItem(Media3Item.fromUri(imported.uri))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        player?.release()
        player = null
    }
}

@Composable
fun MusicPlayerScreen(
    musicViewModel: MusicPlayerViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val uiState by musicViewModel.state.collectAsStateWithLifecycle()

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) musicViewModel.importAudio(context, uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SimulatorTopBar(
            title = "Music",
            subtitle = "Media3 ExoPlayer • Real Audio Engine",
            onBack = onBack,
            trailingContent = {
                IconButton(
                    onClick = { audioPicker.launch(arrayOf("audio/*")) },
                    modifier = Modifier.testTag("music_import_button")
                ) {
                    Icon(Icons.Default.LibraryAdd, contentDescription = "Import Audio", tint = SapphireBlue)
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LiquidGlassCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_album_midnight_1794901151739),
                        contentDescription = "Album Artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(86.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.currentTrack?.title ?: "Select a Track",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = uiState.currentTrack?.artist ?: "i17 Pro Studio",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DesertTitanium
                        )
                        Text(
                            text = uiState.currentTrack?.album ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val dur = uiState.durationMs.coerceAtLeast(1L)
                val pos = uiState.positionMs.coerceIn(0L, dur)
                Slider(
                    value = pos.toFloat(),
                    onValueChange = { musicViewModel.seekTo(it.toLong()) },
                    valueRange = 0f..dur.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = SapphireBlue, activeTrackColor = SapphireBlue)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatMs(pos), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(formatMs(dur), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { musicViewModel.toggleRepeat() }) {
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = if (uiState.repeatOne) EmeraldLive else TextSecondary
                        )
                    }
                    IconButton(onClick = { musicViewModel.previousTrack() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = TextPrimary, modifier = Modifier.size(32.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(SapphireBlue)
                            .clickable { musicViewModel.togglePlayPause() }
                            .testTag("music_play_pause_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    IconButton(onClick = { musicViewModel.nextTrack() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = TextPrimary, modifier = Modifier.size(32.dp))
                    }
                    IconButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                        Icon(Icons.Default.AudioFile, contentDescription = "Add Track", tint = DesertTitanium)
                    }
                }
            }

            Text("Library (${uiState.tracks.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(uiState.tracks) { index, track ->
                    val active = index == uiState.currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (active) SapphireBlue.copy(alpha = 0.16f) else ObsidianElevated)
                            .border(1.dp, if (active) SapphireBlue else GlassBorder, RoundedCornerShape(16.dp))
                            .clickable { musicViewModel.playTrack(index) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = if (active) SapphireBlue else DesertTitanium
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(track.artist, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Text(track.durationLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    return String.format("%d:%02d", totalSec / 60L, totalSec % 60L)
}
