package com.example.media

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.components.SimulatorTopBar
import com.example.ui.theme.DesertTitanium
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ProAmber
import com.example.ui.theme.SapphireBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImageEditorScreen(
    uriString: String,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val repo = remember { MediaRepository(context) }
    val scope = rememberCoroutineScope()

    var baseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableIntStateOf(0) }
    var preset by remember { mutableStateOf(EditorPreset.ORIGINAL) }
    var isSaving by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uriString) {
        val decoded = repo.decodeBitmapForEditor(Uri.parse(uriString), maxDim = 1280)
        baseBitmap = decoded
        previewBitmap = decoded
    }

    LaunchedEffect(baseBitmap, brightness, contrast, saturation, rotation, preset) {
        val src = baseBitmap ?: return@LaunchedEffect
        val edited = withContext(Dispatchers.Default) {
            repo.applyEditsToBitmap(src, brightness, contrast, saturation, rotation, preset)
        }
        previewBitmap = edited
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SimulatorTopBar(
            title = "Photo Studio Editor",
            subtitle = statusText ?: "Non-destructive adjustment",
            onBack = onBack,
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = { rotation = (rotation + 90) % 360 }) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate 90", tint = TextPrimary)
                    }
                    Button(
                        onClick = {
                            val bmp = previewBitmap ?: return@Button
                            isSaving = true
                            scope.launch {
                                val fullBase = repo.decodeBitmapForEditor(Uri.parse(uriString), maxDim = 2400) ?: bmp
                                val finalBmp = withContext(Dispatchers.Default) {
                                    repo.applyEditsToBitmap(fullBase, brightness, contrast, saturation, rotation, preset)
                                }
                                val saved = repo.saveEditedBitmap(finalBmp)
                                isSaving = false
                                if (saved != null) {
                                    onSaved()
                                } else {
                                    statusText = "Save failed"
                                }
                            }
                        },
                        enabled = !isSaving && previewBitmap != null,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldLive),
                        modifier = Modifier.testTag("editor_save_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = ObsidianBg)
                        Text(" Save", color = ObsidianBg)
                    }
                }
            }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val bmp = previewBitmap
            if (bmp == null) {
                CircularProgressIndicator(color = SapphireBlue)
            } else {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Edited Image Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(ObsidianElevated)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditorPreset.entries.forEach { p ->
                    val selected = preset == p
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) ProAmber.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
                            .border(1.dp, if (selected) ProAmber else GlassBorder, RoundedCornerShape(12.dp))
                            .clickable { preset = p }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(p.label, style = MaterialTheme.typography.labelMedium, color = if (selected) ProAmber else TextPrimary)
                    }
                }
            }

            EditorSliderRow("Brightness", brightness, -0.5f..0.5f) { brightness = it }
            EditorSliderRow("Contrast", contrast, 0.5f..1.8f) { contrast = it }
            EditorSliderRow("Saturation", saturation, 0f..2f) { saturation = it }
        }
    }
}

@Composable
private fun EditorSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = DesertTitanium,
            modifier = Modifier.weight(0.28f)
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(0.72f),
            colors = SliderDefaults.colors(thumbColor = SapphireBlue, activeTrackColor = SapphireBlue)
        )
    }
}
