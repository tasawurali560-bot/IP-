package com.example.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MacroOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.TelemetryKeyValueRow
import com.example.ui.theme.CrimsonRecord
import com.example.ui.theme.DesertTitanium
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ProAmber
import com.example.ui.theme.SapphireBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraProScreen(
    onBack: () -> Unit,
    onOpenGallery: () -> Unit,
    onMediaCaptured: () -> Unit,
    cameraViewModel: CameraViewModel = viewModel()
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by cameraViewModel.uiState.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasCameraPermission = results[Manifest.permission.CAMERA] ?: hasCameraPermission
        hasAudioPermission = results[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            cameraViewModel.initializeAndBind(context, lifecycleOwner, previewView.surfaceProvider)
        }
    }

    var showProControlsDrawer by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        if (!hasCameraPermission) {
            CameraPermissionGate(
                onRequestPermissions = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                    )
                },
                onBack = onBack
            )
        } else {
            // Live CameraX PreviewView with tap-to-focus
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.setOnTouchListener { v, event ->
                        if (event.action == MotionEvent.ACTION_UP) {
                            val factory = view.meteringPointFactory
                            val point = factory.createPoint(event.x, event.y)
                            val nx = (event.x / v.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            val ny = (event.y / v.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                            cameraViewModel.triggerTapToFocus(point, nx, ny)
                        }
                        true
                    }
                }
            )

            // Grid Lines overlay
            if (uiState.showGridLines) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val thirdW = size.width / 3f
                    val thirdH = size.height / 3f
                    val lineColor = Color.White.copy(alpha = 0.25f)
                    drawLine(lineColor, Offset(thirdW, 0f), Offset(thirdW, size.height), strokeWidth = 1.5f)
                    drawLine(lineColor, Offset(thirdW * 2f, 0f), Offset(thirdW * 2f, size.height), strokeWidth = 1.5f)
                    drawLine(lineColor, Offset(0f, thirdH), Offset(size.width, thirdH), strokeWidth = 1.5f)
                    drawLine(lineColor, Offset(0f, thirdH * 2f), Offset(size.width, thirdH * 2f), strokeWidth = 1.5f)
                }
            }

            // Focus reticle
            uiState.focusPointNormalized?.let { (nx, ny) ->
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (nx * 900).roundToInt() - 32,
                                    (ny * 1600).roundToInt() - 32
                                )
                            }
                            .size(64.dp)
                            .border(2.dp, ProAmber, RoundedCornerShape(10.dp))
                    )
                }
            }

            // Countdown Overlay
            if (uiState.countdownRemaining > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${uiState.countdownRemaining}",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 84.sp),
                        color = ProAmber
                    )
                }
            }

            // Top Control Strip
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.56f))
                        .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("camera_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }

                    IconButton(
                        onClick = { cameraViewModel.cycleFlash() },
                        modifier = Modifier.testTag("camera_flash_button")
                    ) {
                        val icon = when (uiState.flashOption) {
                            FlashOption.AUTO -> Icons.Default.FlashAuto
                            FlashOption.ON -> Icons.Default.FlashOn
                            FlashOption.OFF -> Icons.Default.FlashOff
                        }
                        Icon(
                            icon,
                            contentDescription = "Flash ${uiState.flashOption.label}",
                            tint = if (uiState.flashOption == FlashOption.ON) ProAmber else TextPrimary
                        )
                    }

                    IconButton(
                        onClick = { cameraViewModel.toggleTorch() },
                        modifier = Modifier.testTag("camera_torch_button")
                    ) {
                        Icon(
                            if (uiState.isTorchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                            contentDescription = "Torch",
                            tint = if (uiState.isTorchOn) ProAmber else TextPrimary
                        )
                    }

                    if (uiState.mode == CameraProMode.VIDEO) {
                        // Video resolution & FPS chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val caps = uiState.capabilities
                            if (caps.supports4K) {
                                VideoBadgeChip(
                                    text = "4K",
                                    selected = uiState.selectedVideoRes == VideoResolutionOption.UHD_4K,
                                    onClick = {
                                        cameraViewModel.selectVideoResolution(
                                            VideoResolutionOption.UHD_4K,
                                            lifecycleOwner,
                                            previewView.surfaceProvider
                                        )
                                    }
                                )
                            }
                            VideoBadgeChip(
                                text = "1080p",
                                selected = uiState.selectedVideoRes == VideoResolutionOption.FHD_1080P,
                                onClick = {
                                    cameraViewModel.selectVideoResolution(
                                        VideoResolutionOption.FHD_1080P,
                                        lifecycleOwner,
                                        previewView.surfaceProvider
                                    )
                                }
                            )
                            VideoBadgeChip(
                                text = "${uiState.targetFps}fps",
                                selected = uiState.targetFps == 60,
                                enabled = caps.supports60Fps,
                                onClick = {
                                    cameraViewModel.toggleFps(lifecycleOwner, previewView.surfaceProvider)
                                }
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { cameraViewModel.toggleHdr(lifecycleOwner, previewView.surfaceProvider) },
                            modifier = Modifier.testTag("camera_hdr_button")
                        ) {
                            Icon(
                                Icons.Default.HdrOn,
                                contentDescription = "HDR Toggle",
                                tint = if (uiState.isHdrRequested && uiState.capabilities.supportsHdrExtension) {
                                    ProAmber
                                } else {
                                    TextSecondary
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = { cameraViewModel.cycleTimer() },
                        modifier = Modifier.testTag("camera_timer_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = "Timer",
                                tint = if (uiState.timerSeconds > 0) ProAmber else TextPrimary
                            )
                            if (uiState.timerSeconds > 0) {
                                Text(
                                    text = "${uiState.timerSeconds}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ProAmber
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { cameraViewModel.toggleGridLines() },
                        modifier = Modifier.testTag("camera_grid_button")
                    ) {
                        Icon(
                            if (uiState.showGridLines) Icons.Default.GridOn else Icons.Default.GridOff,
                            contentDescription = "Grid Lines",
                            tint = if (uiState.showGridLines) ProAmber else TextPrimary
                        )
                    }

                    IconButton(
                        onClick = { cameraViewModel.setCapabilitiesSheetVisible(true) },
                        modifier = Modifier.testTag("camera_hw_info_button")
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Hardware Capabilities", tint = SapphireBlue)
                    }
                }

                // Recording Timer Pill or Status Banner
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (uiState.isRecording) {
                        val mins = uiState.recordingDurationSec / 60
                        val secs = uiState.recordingDurationSec % 60
                        Surface(
                            shape = CircleShape,
                            color = if (uiState.isRecordingPaused) ProAmber else CrimsonRecord
                        ) {
                            Text(
                                text = (if (uiState.isRecordingPaused) "PAUSED  " else "REC  ") +
                                    String.format("%02d:%02d", mins, secs),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    } else {
                        AnimatedVisibility(
                            visible = uiState.statusBanner != null || uiState.errorMessage != null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            val msg = uiState.errorMessage ?: uiState.statusBanner ?: ""
                            val isErr = uiState.errorMessage != null
                            Surface(
                                shape = CircleShape,
                                color = if (isErr) CrimsonRecord.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.7f),
                                modifier = Modifier.clickable { cameraViewModel.dismissBanner() }
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Control Dock
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.68f))
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Expandable Pro Controls Panel
                AnimatedVisibility(visible = showProControlsDrawer || uiState.mode == CameraProMode.PRO) {
                    ProManualControlsStrip(
                        uiState = uiState,
                        onEvChange = { cameraViewModel.setExposureIndex(it) },
                        onWbSelect = { cameraViewModel.setWhiteBalance(it) },
                        onManualExposureChange = { enabled, iso, shutter ->
                            cameraViewModel.setManualExposure(enabled, iso, shutter)
                        },
                        onManualFocusChange = { enabled, dist ->
                            cameraViewModel.setManualFocus(enabled, dist)
                        },
                        onMacroToggle = { cameraViewModel.toggleMacro() }
                    )
                }

                // Zoom selector row (based strictly on real min/max zoom capabilities)
                val caps = uiState.capabilities
                val zoomStops = buildList {
                    if (caps.minZoomRatio <= 0.65f) add(0.5f)
                    add(1.0f)
                    if (caps.maxZoomRatio >= 2.0f) add(2.0f)
                    if (caps.maxZoomRatio >= 5.0f) add(5.0f)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    zoomStops.forEach { stop ->
                        val active = kotlin.math.abs(uiState.zoomRatio - stop) < 0.15f
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) ProAmber.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.12f)
                                )
                                .border(
                                    1.dp,
                                    if (active) ProAmber else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { cameraViewModel.setZoom(stop) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (stop < 1f) ".5x" else "${stop.toInt()}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) ProAmber else TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (caps.maxZoomRatio > 1.2f) {
                        Slider(
                            value = uiState.zoomRatio.coerceIn(caps.minZoomRatio, caps.maxZoomRatio),
                            onValueChange = { cameraViewModel.setZoom(it) },
                            valueRange = caps.minZoomRatio..caps.maxZoomRatio.coerceAtMost(15f),
                            modifier = Modifier.width(120.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = ProAmber,
                                activeTrackColor = ProAmber
                            )
                        )
                        Text(
                            text = "${String.format("%.1f", uiState.zoomRatio)}×",
                            style = MaterialTheme.typography.labelSmall,
                            color = DesertTitanium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Mode Carousel: PHOTO, VIDEO, PORTRAIT, NIGHT, PRO
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CameraProMode.entries.forEach { mode ->
                        val selected = uiState.mode == mode
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) ProAmber else TextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    cameraViewModel.selectMode(
                                        mode,
                                        lifecycleOwner,
                                        previewView.surfaceProvider
                                    )
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("camera_mode_${mode.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Shutter Row: Gallery preview | Tune / Pause | Shutter | Mic / Flip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Thumbnail Gallery Link
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ObsidianElevated)
                            .border(1.5.dp, GlassBorder, RoundedCornerShape(14.dp))
                            .clickable { onOpenGallery() }
                            .testTag("camera_gallery_thumb"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.lastCapturedUri != null) {
                            AsyncImage(
                                model = uiState.lastCapturedUri,
                                contentDescription = "Last Capture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = "Open Gallery",
                                tint = TextPrimary
                            )
                        }
                    }

                    // Pro Tune / Pause Video button
                    if (uiState.isRecording) {
                        IconButton(
                            onClick = { cameraViewModel.togglePauseResumeRecording() },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .testTag("camera_pause_resume_button")
                        ) {
                            Icon(
                                if (uiState.isRecordingPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (uiState.isRecordingPaused) "Resume" else "Pause",
                                tint = ProAmber
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { showProControlsDrawer = !showProControlsDrawer },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (showProControlsDrawer) ProAmber.copy(alpha = 0.22f)
                                    else Color.White.copy(alpha = 0.12f)
                                )
                                .testTag("camera_pro_tune_button")
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "Pro Controls",
                                tint = if (showProControlsDrawer) ProAmber else TextPrimary
                            )
                        }
                    }

                    // Main Shutter / Record Button
                    val isVideo = uiState.mode == CameraProMode.VIDEO
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(3.5.dp, Color.White, CircleShape)
                            .padding(6.dp)
                            .clip(if (uiState.isRecording) RoundedCornerShape(14.dp) else CircleShape)
                            .background(if (isVideo) CrimsonRecord else Color.White)
                            .clickable {
                                if (isVideo) {
                                    cameraViewModel.toggleVideoRecording(
                                        context = context,
                                        hasAudioPermission = hasAudioPermission,
                                        onSaved = { onMediaCaptured() }
                                    )
                                } else {
                                    cameraViewModel.capturePhoto(context) {
                                        onMediaCaptured()
                                    }
                                }
                            }
                            .testTag("camera_shutter_button")
                    )

                    // Mic Toggle in Video mode, or Macro in Photo mode
                    if (uiState.mode == CameraProMode.VIDEO) {
                        IconButton(
                            onClick = {
                                if (!hasAudioPermission) {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                } else {
                                    cameraViewModel.toggleAudioEnabled()
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .testTag("camera_mic_button")
                        ) {
                            Icon(
                                if (hasAudioPermission && uiState.isAudioEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = "Microphone Toggle",
                                tint = if (hasAudioPermission && uiState.isAudioEnabled) EmeraldLive else TextSecondary
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { cameraViewModel.toggleMacro() },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.isMacroRequested) ProAmber.copy(alpha = 0.22f)
                                    else Color.White.copy(alpha = 0.12f)
                                )
                                .testTag("camera_macro_button")
                        ) {
                            Icon(
                                Icons.Default.MacroOff,
                                contentDescription = "Macro Focus",
                                tint = if (uiState.isMacroRequested) ProAmber else TextPrimary
                            )
                        }
                    }

                    // Switch Front / Rear Camera
                    IconButton(
                        onClick = {
                            cameraViewModel.toggleFrontRear(lifecycleOwner, previewView.surfaceProvider)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .testTag("camera_switch_button")
                    ) {
                        Icon(
                            Icons.Default.Cameraswitch,
                            contentDescription = "Switch Camera",
                            tint = TextPrimary
                        )
                    }
                }
            }
        }
    }

    if (uiState.isCapabilitiesSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { cameraViewModel.setCapabilitiesSheetVisible(false) },
            containerColor = ObsidianElevated
        ) {
            HardwareCapabilitiesSheet(
                caps = uiState.capabilities,
                videoStabilizationEnabled = uiState.isStabilizationEnabled,
                onToggleStabilization = {
                    cameraViewModel.toggleStabilization(lifecycleOwner, previewView.surfaceProvider)
                },
                onClose = { cameraViewModel.setCapabilitiesSheetVisible(false) }
            )
        }
    }
}

@Composable
private fun VideoBadgeChip(
    text: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    !enabled -> Color.White.copy(alpha = 0.05f)
                    selected -> ProAmber.copy(alpha = 0.25f)
                    else -> Color.White.copy(alpha = 0.12f)
                }
            )
            .border(
                1.dp,
                if (selected) ProAmber else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = when {
                !enabled -> TextSecondary.copy(alpha = 0.4f)
                selected -> ProAmber
                else -> TextPrimary
            },
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ProManualControlsStrip(
    uiState: CameraUiState,
    onEvChange: (Int) -> Unit,
    onWbSelect: (WhiteBalanceOption) -> Unit,
    onManualExposureChange: (Boolean, Int?, Long?) -> Unit,
    onManualFocusChange: (Boolean, Float?) -> Unit,
    onMacroToggle: () -> Unit
) {
    val caps = uiState.capabilities
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // White Balance Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("WB", style = MaterialTheme.typography.labelSmall, color = DesertTitanium)
            WhiteBalanceOption.entries.forEach { wb ->
                val selected = uiState.whiteBalance == wb
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) SapphireBlue.copy(alpha = 0.3f) else Color.Transparent)
                        .border(1.dp, if (selected) SapphireBlue else GlassBorder, RoundedCornerShape(8.dp))
                        .clickable { onWbSelect(wb) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(wb.label, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }
            }
        }

        // EV Compensation Slider
        if (uiState.evRangeMax > uiState.evRangeMin) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "EV ${String.format("%+.1f", uiState.evIndex * uiState.evStep)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ProAmber,
                    modifier = Modifier.width(56.dp)
                )
                Slider(
                    value = uiState.evIndex.toFloat(),
                    onValueChange = { onEvChange(it.roundToInt()) },
                    valueRange = uiState.evRangeMin.toFloat()..uiState.evRangeMax.toFloat(),
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = ProAmber, activeTrackColor = ProAmber)
                )
            }
        }

        // Pro ISO, Shutter, Manual Focus (only if hardware exposes Camera2 manual controls)
        if (uiState.mode == CameraProMode.PRO) {
            if (caps.supportsManualSensor && caps.isoRange != null && caps.exposureTimeRangeNs != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Manual ISO / Shutter", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                    Switch(
                        checked = uiState.isManualExposureEnabled,
                        onCheckedChange = { onManualExposureChange(it, null, null) }
                    )
                }
                if (uiState.isManualExposureEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ISO ${uiState.manualIso}", style = MaterialTheme.typography.labelSmall, color = DesertTitanium, modifier = Modifier.width(68.dp))
                        Slider(
                            value = uiState.manualIso.toFloat(),
                            onValueChange = { onManualExposureChange(true, it.roundToInt(), null) },
                            valueRange = caps.isoRange.lower.toFloat()..caps.isoRange.upper.toFloat().coerceAtMost(6400f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    val shutterMs = uiState.manualShutterNs / 1_000_000.0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${String.format("%.1f", shutterMs)}ms", style = MaterialTheme.typography.labelSmall, color = DesertTitanium, modifier = Modifier.width(68.dp))
                        Slider(
                            value = uiState.manualShutterNs.toFloat(),
                            onValueChange = { onManualExposureChange(true, null, it.toLong()) },
                            valueRange = caps.exposureTimeRangeNs.lower.toFloat()..caps.exposureTimeRangeNs.upper.toFloat().coerceAtMost(250_000_000f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Text(
                    text = "Manual ISO/Shutter: Hardware Auto (${caps.hardwareLevelLabel})",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            if (caps.minFocusDistanceDiopters > 0f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MF",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.isManualFocusEnabled) ProAmber else TextSecondary,
                        modifier = Modifier
                            .clickable { onManualFocusChange(!uiState.isManualFocusEnabled, null) }
                            .padding(end = 8.dp)
                    )
                    Slider(
                        value = uiState.manualFocusDistance,
                        onValueChange = { onManualFocusChange(true, it) },
                        valueRange = 0f..caps.minFocusDistanceDiopters,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HardwareCapabilitiesSheet(
    caps: CameraHardwareCapabilities,
    videoStabilizationEnabled: Boolean,
    onToggleStabilization: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Real Camera Hardware Capabilities",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
            }
        }

        Text(
            text = "Reported directly by Android CameraX & Camera2 APIs. Unverified features are never fabricated.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        LiquidGlassCard {
            TelemetryKeyValueRow("Active Camera", caps.cameraFacing)
            TelemetryKeyValueRow("Camera2 Hardware Level", caps.hardwareLevelLabel)
            TelemetryKeyValueRow("Max JPEG Photo Resolution", caps.maxPhotoResolutionLabel)
            TelemetryKeyValueRow("4K UHD Video", if (caps.supports4K) "Supported" else "Not Supported", if (caps.supports4K) EmeraldLive else TextSecondary)
            TelemetryKeyValueRow("1080p FHD Video", if (caps.supports1080p) "Supported" else "Not Supported")
            TelemetryKeyValueRow("60 FPS Video", if (caps.supports60Fps) "Supported" else "30 FPS Max", if (caps.supports60Fps) EmeraldLive else TextSecondary)
            TelemetryKeyValueRow("Zoom Range", "${String.format("%.2f", caps.minZoomRatio)}× – ${String.format("%.1f", caps.maxZoomRatio)}×")
            TelemetryKeyValueRow("Multi-Lens / Optical Zoom", if (caps.hasOpticalZoomLenses) "Hardware Supported" else "Digital Zoom Only")
            TelemetryKeyValueRow("Optical Stabilization (OIS)", if (caps.supportsOis) "Supported" else "Not Supported")
            TelemetryKeyValueRow("Video Stabilization", if (caps.supportsVideoStabilization) "Supported" else "Not Supported")
            TelemetryKeyValueRow("HDR Extension", if (caps.supportsHdrExtension) "Supported" else "Not Supported")
            TelemetryKeyValueRow("Portrait Bokeh Extension", if (caps.supportsPortraitBokehExtension) "Supported" else "Not Supported")
            TelemetryKeyValueRow("Night Extension", if (caps.supportsNightExtension) "Supported" else "Not Supported")
            TelemetryKeyValueRow("Manual ISO / Shutter", if (caps.supportsManualSensor) "Supported (${caps.isoRange})" else "Auto Only")
            TelemetryKeyValueRow("Flash Hardware", if (caps.hasFlash) "Available" else "Unavailable")
        }

        if (caps.supportsVideoStabilization || caps.supportsOis) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable Hardware Stabilization", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                Switch(checked = videoStabilizationEnabled, onCheckedChange = { onToggleStabilization() })
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun CameraPermissionGate(
    onRequestPermissions: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Camera Pro Engine",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Grant Camera and Microphone permissions to capture real high-resolution photos, 4K/1080p video, and inspect your hardware sensor capabilities.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRequestPermissions,
            colors = ButtonDefaults.buttonColors(containerColor = SapphireBlue),
            modifier = Modifier.testTag("grant_camera_permission_button")
        ) {
            Text("Enable Camera & Mic")
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = ObsidianElevated)
        ) {
            Text("Return Home")
        }
    }
}
