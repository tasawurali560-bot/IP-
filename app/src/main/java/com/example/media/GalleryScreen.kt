package com.example.media

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.system.formatBytesHuman
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.SimulatorTopBar
import com.example.ui.components.TelemetryKeyValueRow
import com.example.ui.theme.CrimsonRecord
import com.example.ui.theme.DesertTitanium
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ProAmber
import com.example.ui.theme.SapphireBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun GalleryScreen(
    initialFilter: String,
    onBack: () -> Unit,
    onOpenCamera: () -> Unit,
    onEditImage: (String) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { MediaRepository(context) }
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var filter by remember { mutableStateOf(initialFilter) }
    var selectedItem by remember { mutableStateOf<MediaItem?>(null) }
    var exifSummary by remember { mutableStateOf<ExifSummary?>(null) }
    var showExifDialog by remember { mutableStateOf(false) }

    BackHandler {
        if (selectedItem != null) {
            selectedItem = null
        } else {
            onBack()
        }
    }

    fun reload() {
        scope.launch {
            items = repo.loadGalleryMedia()
        }
    }

    LaunchedEffect(Unit) { reload() }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                repo.importPickedMedia(uri)
                reload()
            }
        }
    }

    val filteredItems = remember(items, filter) {
        when (filter) {
            "PHOTOS" -> items.filter { !it.isVideo }
            "VIDEOS" -> items.filter { it.isVideo }
            else -> items
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            SimulatorTopBar(
                title = "Photos & Video Gallery",
                subtitle = "${filteredItems.size} items • Real Device Media",
                onBack = onBack,
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                pickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            modifier = Modifier.testTag("gallery_import_button")
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Import Media", tint = SapphireBlue)
                        }
                        IconButton(
                            onClick = onOpenCamera,
                            modifier = Modifier.testTag("gallery_camera_button")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Open Camera", tint = ProAmber)
                        }
                    }
                }
            )

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL" to "All", "PHOTOS" to "Photos", "VIDEOS" to "Videos").forEach { (key, label) ->
                    FilterChip(
                        selected = filter == key,
                        onClick = { filter = key },
                        label = { Text(label) }
                    )
                }
            }

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LiquidGlassCard {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = DesertTitanium,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No Captured Photos or Videos Yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Take photos or record 4K/1080p video with Camera Pro, or import existing media via the zero-permission Android Photo Picker.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = onOpenCamera,
                                    colors = ButtonDefaults.buttonColors(containerColor = SapphireBlue)
                                ) {
                                    Text("Open Camera")
                                }
                                Button(
                                    onClick = {
                                        pickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianElevated)
                                ) {
                                    Text("Import")
                                }
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredItems, key = { "${it.id}_${it.uri}" }) { media ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(ObsidianElevated)
                                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                                .clickable { selectedItem = media }
                                .testTag("gallery_item_${media.id}")
                        ) {
                            AsyncImage(
                                model = media.uri,
                                contentDescription = media.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (media.isVideo) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircleFilled,
                                    contentDescription = "Video",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(36.dp)
                                )
                                if (media.durationMs > 0) {
                                    val sec = media.durationMs / 1000
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.Black.copy(alpha = 0.72f),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = String.format("%d:%02d", sec / 60, sec % 60),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Fullscreen Viewer / Media3 Video Player
        selectedItem?.let { item ->
            FullscreenMediaViewer(
                item = item,
                onClose = { selectedItem = null },
                onShare = { repo.shareMediaItem(item) },
                onEdit = {
                    if (!item.isVideo) {
                        selectedItem = null
                        onEditImage(item.uri.toString())
                    }
                },
                onInspectExif = {
                    scope.launch {
                        exifSummary = repo.readExifSummary(item.uri)
                        showExifDialog = true
                    }
                },
                onDelete = {
                    scope.launch {
                        repo.deleteMediaItem(item)
                        selectedItem = null
                        reload()
                    }
                }
            )
        }

        if (showExifDialog && exifSummary != null) {
            val exif = exifSummary!!
            AlertDialog(
                onDismissRequest = { showExifDialog = false },
                containerColor = ObsidianElevated,
                title = { Text("Media Details", color = TextPrimary) },
                text = {
                    Column {
                        TelemetryKeyValueRow("File", selectedItem?.displayName ?: "")
                        TelemetryKeyValueRow("Size", formatBytesHuman(selectedItem?.sizeBytes ?: 0L))
                        TelemetryKeyValueRow("Dimensions", exif.dimensions)
                        TelemetryKeyValueRow("Camera", exif.cameraModel)
                        TelemetryKeyValueRow("ISO", exif.iso)
                        TelemetryKeyValueRow("Exposure", exif.exposure)
                        TelemetryKeyValueRow("Aperture", exif.aperture)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showExifDialog = false }) { Text("Close") }
                }
            )
        }
    }
}

@Composable
private fun FullscreenMediaViewer(
    item: MediaItem,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onInspectExif: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (item.isVideo) {
            val exoPlayer = remember(item.uri) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(Media3Item.fromUri(item.uri))
                    prepare()
                    playWhenReady = true
                }
            }
            DisposableEffect(exoPlayer) {
                onDispose {
                    exoPlayer.release()
                }
            }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = item.uri,
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top Overlay Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.testTag("viewer_close_button")) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (!item.isVideo) {
                IconButton(onClick = onInspectExif) {
                    Icon(Icons.Default.Info, contentDescription = "EXIF Info", tint = Color.White)
                }
            }
        }

        // Bottom Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.68f))
                .padding(vertical = 10.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onShare, modifier = Modifier.testTag("viewer_share_button")) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = SapphireBlue)
            }
            if (!item.isVideo) {
                IconButton(onClick = onEdit, modifier = Modifier.testTag("viewer_edit_button")) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Photo", tint = ProAmber)
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.testTag("viewer_delete_button")) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonRecord)
            }
        }
    }
}
