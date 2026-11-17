package com.example.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.media.MediaRepository
import com.example.media.MusicPlayerViewModel
import com.example.system.HardwareTelemetryRepository
import com.example.system.RamTelemetry
import com.example.system.StorageTelemetry
import com.example.system.SystemStatusSnapshot
import com.example.system.formatBytesHuman
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.SegmentedProgressBar
import com.example.ui.theme.CrimsonRecord
import com.example.ui.theme.CyanGlass
import com.example.ui.theme.DesertTitanium
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.IndigoGlass
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ProAmber
import com.example.ui.theme.SapphireBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SimAppTile(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val gradientStart: Color,
    val gradientEnd: Color,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    musicViewModel: MusicPlayerViewModel,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenMusic: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWebApp: (appId: String, title: String, url: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val telemetryRepo = remember { HardwareTelemetryRepository(context) }
    val mediaRepo = remember { MediaRepository(context) }

    var ram by remember { mutableStateOf(RamTelemetry()) }
    var storage by remember { mutableStateOf(StorageTelemetry()) }
    var status by remember { mutableStateOf(SystemStatusSnapshot()) }
    var clockText by remember { mutableStateOf("") }
    var islandExpanded by remember { mutableStateOf(false) }
    var showControlCenter by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }
    var torchError by remember { mutableStateOf<String?>(null) }
    var spotlightQuery by remember { mutableStateOf("") }

    val musicState by musicViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        ram = telemetryRepo.readRamTelemetry()
        status = telemetryRepo.readSystemStatus()
        val media = mediaRepo.loadGalleryMedia()
        val photos = media.filter { !it.isVideo }
        val videos = media.filter { it.isVideo }
        storage = telemetryRepo.readStorageTelemetry(
            realPhotosCount = photos.size,
            realPhotosBytes = photos.sumOf { it.sizeBytes },
            realVideosCount = videos.size,
            realVideosBytes = videos.sumOf { it.sizeBytes }
        )
        while (true) {
            clockText = SimpleDateFormat("h:mm", Locale.US).format(Date())
            status = telemetryRepo.readSystemStatus()
            delay(15_000)
        }
    }

    val allApps = remember(onOpenCamera, onOpenGallery, onOpenMusic, onOpenFiles, onOpenSettings, onOpenWebApp) {
        listOf(
            SimAppTile("camera", "Camera Pro", Icons.Default.CameraAlt, Color(0xFF2D3342), Color(0xFF141821), onOpenCamera),
            SimAppTile("photos", "Photos", Icons.Default.PhotoLibrary, Color(0xFF4FA8FF), Color(0xFF235EE7), onOpenGallery),
            SimAppTile("music", "Music", Icons.Default.MusicNote, Color(0xFFFF4D6D), Color(0xFFC9184A), onOpenMusic),
            SimAppTile("files", "Files 1TB", Icons.Default.Folder, Color(0xFF3A8BFF), Color(0xFF1C4ED8), onOpenFiles),
            SimAppTile(
                "youtube", "YouTube", Icons.Default.PlayCircleFilled, Color(0xFFFF2E2E), Color(0xFF9E0B0B)
            ) { onOpenWebApp("youtube", "YouTube", "https://m.youtube.com") },
            SimAppTile(
                "tiktok", "TikTok", Icons.Default.VideoLibrary, Color(0xFF1A1E29), Color(0xFF0A0C12)
            ) { onOpenWebApp("tiktok", "TikTok", "https://www.tiktok.com") },
            SimAppTile(
                "google", "Google", Icons.Default.Search, Color(0xFF4285F4), Color(0xFF1B56B8)
            ) { onOpenWebApp("google", "Google Search", "https://www.google.com") },
            SimAppTile(
                "browser", "Safari", Icons.Default.Public, Color(0xFF38BDF8), Color(0xFF0284C7)
            ) { onOpenWebApp("browser", "Safari Browser", "https://www.google.com") },
            SimAppTile(
                "gmail", "Gmail", Icons.Default.Mail, Color(0xFFEA4335), Color(0xFFA51C12)
            ) { onOpenWebApp("gmail", "Gmail", "https://mail.google.com") },
            SimAppTile(
                "maps", "Maps", Icons.Default.Map, Color(0xFF34D399), Color(0xFF059669)
            ) { onOpenWebApp("maps", "Google Maps", "https://www.google.com/maps") },
            SimAppTile("settings", "Settings", Icons.Default.Settings, Color(0xFF6B7280), Color(0xFF374151), onOpenSettings),
            SimAppTile("control", "Controls", Icons.Default.Tune, Color(0xFFC7A882), Color(0xFF7D6344)) {
                showControlCenter = true
            }
        )
    }

    val filteredApps = remember(allApps, spotlightQuery) {
        if (spotlightQuery.isBlank()) allApps
        else allApps.filter { it.label.contains(spotlightQuery.trim(), ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // Wallpaper Background
        Image(
            painter = painterResource(id = R.drawable.img_wallpaper_titanium_1794901151809),
            contentDescription = "Titanium Wallpaper",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.72f)
                        )
                    )
                )
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            val isWide = maxWidth >= 600.dp
            val gridCols = if (isWide) 6 else 4

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
            ) {
                // Status Bar + Dynamic Island
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = clockText.ifEmpty { "9:41" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    // Interactive Dynamic Island
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black.copy(alpha = 0.92f))
                            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                            .animateContentSize(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            )
                            .clickable { islandExpanded = !islandExpanded }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                            .testTag("dynamic_island")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (musicState.isPlaying) EmeraldLive else DesertTitanium)
                                )
                                Text(
                                    text = if (musicState.isPlaying) {
                                        musicState.currentTrack?.title ?: "Playing Audio"
                                    } else {
                                        "iPhone 17 Pro Max Sim"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            AnimatedVisibility(visible = islandExpanded) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Virtual: 16GB RAM • 1TB | Real: ${formatBytesHuman(ram.actualTotalBytes)} RAM",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DesertTitanium,
                                        textAlign = TextAlign.Center
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (musicState.tracks.isNotEmpty()) {
                                            IconButton(
                                                onClick = { musicViewModel.togglePlayPause() },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    if (musicState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = "Play/Pause",
                                                    tint = EmeraldLive
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Control Center",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SapphireBlue,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(SapphireBlue.copy(alpha = 0.18f))
                                                .clickable { showControlCenter = true }
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = status.networkLabel,
                            tint = if (status.isOnline) TextPrimary else CrimsonRecord,
                            modifier = Modifier.size(16.dp)
                        )
                        Icon(
                            imageVector = if (status.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                            contentDescription = "Battery ${status.batteryPercent}%",
                            tint = if (status.isCharging) EmeraldLive else TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${status.batteryPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }
                }

                // Main Scrollable Home Content
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridCols),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Telemetry Widgets Row
                    item(span = { GridItemSpan(gridCols) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 16GB Virtual RAM Widget
                            LiquidGlassCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onOpenSettings() }
                                    .testTag("widget_virtual_ram")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Memory, contentDescription = null, tint = DesertTitanium, modifier = Modifier.size(18.dp))
                                    Text("16GB Virtual RAM", style = MaterialTheme.typography.labelMedium, color = DesertTitanium)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${String.format("%.1f", ram.virtualAllocatedGb)} / 16 GB",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                SegmentedProgressBar(fraction = ram.virtualUsageFraction, color = DesertTitanium, height = 6.dp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Real RAM: ${formatBytesHuman(ram.actualAvailableBytes)} free",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SapphireBlue,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // 1TB Virtual Storage Widget
                            LiquidGlassCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onOpenFiles() }
                                    .testTag("widget_virtual_storage")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Storage, contentDescription = null, tint = SapphireBlue, modifier = Modifier.size(18.dp))
                                    Text("1TB Storage", style = MaterialTheme.typography.labelMedium, color = SapphireBlue)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${String.format("%.0f", storage.virtualUsedGb)} / 1024 GB",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                SegmentedProgressBar(
                                    fraction = (storage.virtualUsedGb / storage.virtualTotalGb).toFloat(),
                                    color = SapphireBlue,
                                    height = 6.dp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Real Free: ${formatBytesHuman(storage.actualAvailableBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldLive,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Spotlight Google Search Bar
                    item(span = { GridItemSpan(gridCols) }) {
                        OutlinedTextField(
                            value = spotlightQuery,
                            onValueChange = { spotlightQuery = it },
                            placeholder = {
                                Text(
                                    "Spotlight & Real Google Search...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DesertTitanium) },
                            singleLine = true,
                            shape = RoundedCornerShape(22.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Black.copy(alpha = 0.45f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.35f),
                                focusedBorderColor = DesertTitanium,
                                unfocusedBorderColor = GlassBorder
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    val q = spotlightQuery.trim()
                                    if (q.isNotEmpty()) {
                                        onOpenWebApp(
                                            "google",
                                            "Google Search",
                                            "https://www.google.com/search?q=${URLEncoder.encode(q, "UTF-8")}"
                                        )
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("spotlight_search_input")
                        )
                    }

                    // App Icons Grid
                    items(filteredApps, key = { it.id }) { tile ->
                        HomeAppTile(tile = tile)
                    }
                }

                // Bottom iOS 26 Liquid Glass Dock
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(1.dp, GlassBorder, RoundedCornerShape(30.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DockIconButton(
                        icon = Icons.Default.CameraAlt,
                        label = "Camera",
                        bg = Color(0xFF2B3140),
                        onClick = onOpenCamera,
                        tag = "dock_camera"
                    )
                    DockIconButton(
                        icon = Icons.Default.Explore,
                        label = "Safari",
                        bg = SapphireBlue,
                        onClick = { onOpenWebApp("browser", "Safari Browser", "https://www.google.com") },
                        tag = "dock_browser"
                    )
                    DockIconButton(
                        icon = Icons.Default.PhotoLibrary,
                        label = "Photos",
                        bg = IndigoGlass,
                        onClick = onOpenGallery,
                        tag = "dock_photos"
                    )
                    DockIconButton(
                        icon = Icons.Default.MusicNote,
                        label = "Music",
                        bg = CrimsonRecord,
                        onClick = onOpenMusic,
                        tag = "dock_music"
                    )
                }
            }
        }
    }

    if (showControlCenter) {
        ModalBottomSheet(
            onDismissRequest = { showControlCenter = false },
            containerColor = ObsidianElevated
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("iOS 26 Control Center", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text(
                    "iPhone 17 Pro Max Virtual Simulator • Host: ${ram.deviceModel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DesertTitanium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LiquidGlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val next = !isTorchOn
                                telemetryRepo.setSystemTorch(next).onSuccess {
                                    isTorchOn = it
                                    torchError = null
                                }.onFailure { err ->
                                    torchError = err.localizedMessage
                                }
                            }
                            .testTag("cc_flashlight_tile")
                    ) {
                        Icon(
                            if (isTorchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                            contentDescription = "Flashlight",
                            tint = if (isTorchOn) ProAmber else TextPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(if (isTorchOn) "Flashlight On" else "Flashlight Off", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    }

                    LiquidGlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                showControlCenter = false
                                onOpenSettings()
                            }
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = "Device Info", tint = SapphireBlue)
                        Spacer(Modifier.height(6.dp))
                        Text("16GB RAM Info", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    }
                }

                torchError?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = CrimsonRecord)
                }

                LiquidGlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Connectivity: ${status.networkLabel}", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                            Text("Battery: ${status.batteryPercent}% • Thermal: ${ram.thermalStatusLabel}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = CyanGlass)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun HomeAppTile(tile: SimAppTile) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = tile.onClick)
            .padding(vertical = 4.dp)
            .testTag("app_tile_${tile.id}")
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(tile.gradientStart, tile.gradientEnd)
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tile.icon,
                contentDescription = tile.label,
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = tile.label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DockIconButton(
    icon: ImageVector,
    label: String,
    bg: Color,
    onClick: () -> Unit,
    tag: String
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}
