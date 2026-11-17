package com.example.files

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.media.MediaRepository
import com.example.system.HardwareTelemetryRepository
import com.example.system.StorageTelemetry
import com.example.system.formatBytesHuman
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.SegmentedProgressBar
import com.example.ui.components.SimulationNoticePill
import com.example.ui.components.SimulatorTopBar
import com.example.ui.components.SpecComparisonBadge
import com.example.ui.components.TelemetryKeyValueRow
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FileSortMode(val label: String) {
    NAME("Name (A–Z)"),
    SIZE("Size (Largest)"),
    DATE("Date (Newest)"),
    TYPE("Type (Folders First)")
}

@Composable
fun FilesManagerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val telemetryRepo = remember { HardwareTelemetryRepository(context) }
    val mediaRepo = remember { MediaRepository(context) }
    val prefs by telemetryRepo.preferencesFlow.collectAsStateWithLifecycle(
        initialValue = com.example.system.SimPreferences()
    )

    val vaultRoot = remember {
        File(context.filesDir, "i17_vault").apply {
            if (!exists()) {
                mkdirs()
                File(this, "Documents").mkdirs()
                File(this, "Downloads").mkdirs()
                File(this, "Notes").mkdirs()
                File(this, "Documents/Welcome_i17_Simulator.txt").writeText(
                    "iPhone 17 Pro Max Virtual Simulator - Real Local File Vault\n" +
                        "Note: 1 TB (1024 GB) is a virtual display specification.\n" +
                        "Files created or imported here are stored in real Android app storage."
                )
            }
        }
    }

    var currentDir by remember { mutableStateOf(vaultRoot) }
    var filesList by remember { mutableStateOf<List<File>>(emptyList()) }
    var storageTelemetry by remember { mutableStateOf(StorageTelemetry()) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(FileSortMode.TYPE) }
    var showSortMenu by remember { mutableStateOf(false) }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewDocDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var viewFileTarget by remember { mutableStateOf<File?>(null) }
    var exportSourceFile by remember { mutableStateOf<File?>(null) }
    var showCloudDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    BackHandler {
        if (currentDir != vaultRoot && currentDir.parentFile != null) {
            currentDir = currentDir.parentFile!!
        } else {
            onBack()
        }
    }

    suspend fun refreshAll() {
        val children = withContext(Dispatchers.IO) {
            currentDir.listFiles()?.toList().orEmpty()
        }
        filesList = children
        val media = mediaRepo.loadGalleryMedia()
        val photos = media.filter { !it.isVideo }
        val videos = media.filter { it.isVideo }
        storageTelemetry = telemetryRepo.readStorageTelemetry(
            realPhotosCount = photos.size,
            realPhotosBytes = photos.sumOf { it.sizeBytes },
            realVideosCount = videos.size,
            realVideosBytes = videos.sumOf { it.sizeBytes }
        )
    }

    LaunchedEffect(currentDir) { refreshAll() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val importedName = withContext(Dispatchers.IO) {
                    val rawName = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
                    } ?: "imported_${System.currentTimeMillis()}"
                    val safe = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val dest = File(currentDir, safe)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(dest).use { out -> input.copyTo(out) }
                    }
                    safe
                }
                statusMessage = "Imported $importedName"
                refreshAll()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { destUri: Uri? ->
        val src = exportSourceFile
        if (destUri != null && src != null && src.exists()) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    src.inputStream().use { input ->
                        context.contentResolver.openOutputStream(destUri)?.use { out ->
                            input.copyTo(out)
                        }
                    }
                }
                statusMessage = "Exported ${src.name}"
                exportSourceFile = null
            }
        }
    }

    val displayedFiles = remember(filesList, searchQuery, sortMode) {
        val filtered = if (searchQuery.isBlank()) {
            filesList
        } else {
            filesList.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
        }
        when (sortMode) {
            FileSortMode.NAME -> filtered.sortedBy { it.name.lowercase() }
            FileSortMode.SIZE -> filtered.sortedByDescending { if (it.isDirectory) 0L else it.length() }
            FileSortMode.DATE -> filtered.sortedByDescending { it.lastModified() }
            FileSortMode.TYPE -> filtered.sortedWith(
                compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
            )
        }
    }

    val virtualUsedPct = ((storageTelemetry.virtualUsedGb / storageTelemetry.virtualTotalGb) * 100.0).toInt()
    val realFreeLow = storageTelemetry.actualAvailableBytes in 1L until (1024L * 1024L * 1024L)
    val showStorageWarning = realFreeLow || virtualUsedPct >= prefs.storageWarningThresholdPercent

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SimulatorTopBar(
            title = "Files & 1TB Storage",
            subtitle = if (currentDir == vaultRoot) "i17 Vault Root" else currentDir.name,
            onBack = {
                if (currentDir != vaultRoot && currentDir.parentFile != null) {
                    currentDir = currentDir.parentFile!!
                } else {
                    onBack()
                }
            },
            trailingContent = {
                Row {
                    IconButton(onClick = { showCloudDialog = true }) {
                        Icon(
                            Icons.Default.CloudQueue,
                            contentDescription = "Optional Cloud Storage",
                            tint = if (prefs.cloudSyncEnabled) EmeraldLive else TextSecondary
                        )
                    }
                    IconButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.testTag("files_import_button")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Import File", tint = SapphireBlue)
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SimulationNoticePill(
                    text = "1 TB (1024 GB) is a virtual simulator capacity. Real files are stored in physical Android storage (${formatBytesHuman(storageTelemetry.actualAvailableBytes)} real free)."
                )
            }

            if (showStorageWarning) {
                item {
                    SimulationNoticePill(
                        text = if (realFreeLow) {
                            "Storage Warning: Real Android device free space is below 1 GB!"
                        } else {
                            "Storage Warning: Virtual usage ($virtualUsedPct%) reached threshold (${prefs.storageWarningThresholdPercent}%)."
                        },
                        accentColor = CrimsonRecord
                    )
                }
            }

            // 1TB Virtual Storage Dashboard + Donut Chart
            item {
                StorageDashboardCard(storage = storageTelemetry)
            }

            // Action & Search Toolbar
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text("Search files in ${currentDir.name}...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("files_search_input"),
                        shape = RoundedCornerShape(16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showNewFolderDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = ObsidianElevated),
                                modifier = Modifier.testTag("files_new_folder_button")
                            ) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = ProAmber, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Folder")
                            }
                            Button(
                                onClick = { showNewDocDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = ObsidianElevated),
                                modifier = Modifier.testTag("files_new_doc_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, tint = SapphireBlue, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Doc")
                            }
                        }

                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort Files", tint = TextPrimary)
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                FileSortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label) },
                                        onClick = {
                                            sortMode = mode
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    statusMessage?.let { msg ->
                        Text(msg, style = MaterialTheme.typography.labelMedium, color = EmeraldLive)
                    }
                }
            }

            if (displayedFiles.isEmpty()) {
                item {
                    LiquidGlassCard {
                        Text(
                            text = if (searchQuery.isBlank()) "Folder is empty. Create a folder, document, or import a file." else "No files match \"$searchQuery\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                items(displayedFiles, key = { it.absolutePath }) { file ->
                    FileItemRow(
                        file = file,
                        onOpen = {
                            if (file.isDirectory) {
                                currentDir = file
                            } else {
                                viewFileTarget = file
                            }
                        },
                        onRename = { renameTarget = file },
                        onExport = {
                            if (!file.isDirectory) {
                                exportSourceFile = file
                                exportLauncher.launch(file.name)
                            }
                        },
                        onShare = {
                            if (!file.isDirectory) {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, "Share ${file.name}"))
                            }
                        },
                        onDelete = { deleteTarget = file }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showNewFolderDialog) {
        TextPromptDialog(
            title = "New Folder",
            initialValue = "",
            placeholder = "Folder name",
            onConfirm = { name ->
                val clean = name.trim().replace("/", "_")
                if (clean.isNotEmpty()) {
                    File(currentDir, clean).mkdirs()
                    scope.launch { refreshAll() }
                }
                showNewFolderDialog = false
            },
            onDismiss = { showNewFolderDialog = false }
        )
    }

    if (showNewDocDialog) {
        NewDocumentDialog(
            onConfirm = { fileName, body ->
                val clean = fileName.trim().ifEmpty { "Note_${System.currentTimeMillis()}.txt" }
                val finalName = if (clean.contains(".")) clean else "$clean.txt"
                File(currentDir, finalName).writeText(body)
                showNewDocDialog = false
                scope.launch { refreshAll() }
            },
            onDismiss = { showNewDocDialog = false }
        )
    }

    renameTarget?.let { target ->
        TextPromptDialog(
            title = "Rename ${target.name}",
            initialValue = target.name,
            placeholder = "New name",
            onConfirm = { newName ->
                val clean = newName.trim().replace("/", "_")
                if (clean.isNotEmpty()) {
                    target.renameTo(File(target.parentFile, clean))
                    scope.launch { refreshAll() }
                }
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = ObsidianElevated,
            title = { Text("Delete ${target.name}?", color = TextPrimary) },
            text = { Text("This permanently removes the item from device storage.", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        target.deleteRecursively()
                        deleteTarget = null
                        scope.launch { refreshAll() }
                    }
                ) { Text("Delete", color = CrimsonRecord) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    viewFileTarget?.let { target ->
        val contentText = remember(target) {
            runCatching {
                if (target.length() <= 65_536) target.readText()
                else "Binary or large file (${formatBytesHuman(target.length())}). Use Export or Share to open externally."
            }.getOrDefault("Unable to preview file.")
        }
        AlertDialog(
            onDismissRequest = { viewFileTarget = null },
            containerColor = ObsidianElevated,
            title = { Text(target.name, color = TextPrimary) },
            text = { Text(contentText, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewFileTarget = null }) { Text("Close") }
            }
        )
    }

    if (showCloudDialog) {
        CloudStorageConfigDialog(
            currentEndpoint = prefs.cloudEndpointUrl,
            currentlyEnabled = prefs.cloudSyncEnabled,
            onSave = { url, enabled ->
                scope.launch {
                    telemetryRepo.updateCloudConfig(url, enabled)
                }
                showCloudDialog = false
            },
            onDismiss = { showCloudDialog = false }
        )
    }
}

@Composable
private fun StorageDashboardCard(storage: StorageTelemetry) {
    val bd = storage.virtualBreakdown
    val categories = listOf(
        Triple("Photos", bd.photosGb, SapphireBlue),
        Triple("Videos", bd.videosGb, CrimsonRecord),
        Triple("Applications", bd.appsGb, IndigoGlass),
        Triple("Downloads", bd.downloadsGb, CyanGlass),
        Triple("Documents", bd.documentsGb, EmeraldLive),
        Triple("System Files", bd.systemGb, DesertTitanium)
    )

    LiquidGlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("1 TB Virtual Storage", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            SpecComparisonBadge(label = "SIMULATED 1024 GB", isSimulated = true)
        }
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Donut Chart
            Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 18f, cap = StrokeCap.Butt)
                    drawArc(
                        color = Color.White.copy(alpha = 0.08f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = stroke
                    )
                    var start = -90f
                    categories.forEach { (_, gb, color) ->
                        val sweep = ((gb / storage.virtualTotalGb) * 360.0).toFloat()
                        if (sweep > 0.5f) {
                            drawArc(
                                color = color,
                                startAngle = start,
                                sweepAngle = sweep,
                                useCenter = false,
                                style = stroke
                            )
                            start += sweep
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${String.format("%.1f", storage.virtualUsedGb)} GB",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text("of 1024 GB", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TelemetryKeyValueRow("Total Virtual", "1024.0 GB", DesertTitanium)
                TelemetryKeyValueRow("Used Virtual", "${String.format("%.2f", storage.virtualUsedGb)} GB")
                TelemetryKeyValueRow("Free Virtual", "${String.format("%.2f", storage.virtualFreeGb)} GB", EmeraldLive)
                Spacer(modifier = Modifier.height(2.dp))
                SpecComparisonBadge(label = "REAL ANDROID STORAGE", isSimulated = false)
                TelemetryKeyValueRow("Physical Total", formatBytesHuman(storage.actualTotalBytes))
                TelemetryKeyValueRow("Physical Free", formatBytesHuman(storage.actualAvailableBytes), SapphireBlue)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        SegmentedProgressBar(
            fraction = (storage.virtualUsedGb / storage.virtualTotalGb).toFloat(),
            color = SapphireBlue
        )
        Spacer(modifier = Modifier.height(10.dp))

        categories.forEach { (label, gb, color) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
                Text("${String.format("%.2f", gb)} GB", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun FileItemRow(
    file: File,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(file.lastModified()) {
        SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(file.lastModified()))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianElevated)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when {
                file.isDirectory -> Icons.Default.Folder
                file.extension.lowercase() in setOf("txt", "md", "json", "csv") -> Icons.Default.Description
                else -> Icons.AutoMirrored.Filled.InsertDriveFile
            },
            contentDescription = null,
            tint = if (file.isDirectory) ProAmber else SapphireBlue,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (file.isDirectory) "Folder • $dateStr" else "${formatBytesHuman(file.length())} • $dateStr",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename", tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
        if (!file.isDirectory) {
            IconButton(onClick = onExport, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.FileUpload, contentDescription = "Export", tint = SapphireBlue, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = EmeraldLive, modifier = Modifier.size(18.dp))
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonRecord, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TextPromptDialog(
    title: String,
    initialValue: String,
    placeholder: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianElevated,
        title = { Text(title, color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(placeholder) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun NewDocumentDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianElevated,
        title = { Text("Create Document", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Filename (e.g. notes.txt)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    placeholder = { Text("Document text...") },
                    minLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, body) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CloudStorageConfigDialog(
    currentEndpoint: String,
    currentlyEnabled: Boolean,
    onSave: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var endpoint by remember { mutableStateOf(currentEndpoint) }
    var enabled by remember { mutableStateOf(currentlyEnabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianElevated,
        title = { Text("Optional Cloud Storage", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Cloud sync is disabled by default. Configure your own HTTPS WebDAV or storage endpoint only if you wish to connect external storage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    placeholder = { Text("https://cloud.example.com/dav") },
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Integration", color = TextPrimary)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(endpoint, enabled) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
