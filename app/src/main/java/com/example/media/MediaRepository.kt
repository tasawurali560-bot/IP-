package com.example.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val durationMs: Long = 0L,
    val isVideo: Boolean = false
)

data class ExifSummary(
    val dimensions: String = "Unknown",
    val cameraModel: String = "Android Camera",
    val iso: String = "Auto",
    val exposure: String = "Auto",
    val aperture: String = "Auto"
)

data class AudioTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
    val durationLabel: String
)

enum class EditorPreset(val label: String) {
    ORIGINAL("Original"),
    TITANIUM("Titanium"),
    VIVID("Vivid"),
    NOIR("Noir"),
    WARM("Desert")
}

class MediaRepository(private val context: Context) {

    suspend fun loadGalleryMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryMediaStoreImages(items)
            queryMediaStoreVideos(items)
        } else {
            queryLegacyAppMediaDir(
                File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "i17ProMaxSim"),
                isVideo = false,
                items = items
            )
            queryLegacyAppMediaDir(
                File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "i17ProMaxSim"),
                isVideo = true,
                items = items
            )
        }

        // Also include imported media stored in app private gallery cache
        val importedDir = File(context.filesDir, "imported_media")
        if (importedDir.exists()) {
            importedDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val isVid = file.extension.lowercase() in setOf("mp4", "mov", "mkv", "webm")
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    items.add(
                        MediaItem(
                            id = file.absolutePath.hashCode().toLong(),
                            uri = uri,
                            displayName = file.name,
                            mimeType = if (isVid) "video/mp4" else "image/jpeg",
                            sizeBytes = file.length(),
                            dateAddedSec = file.lastModified() / 1000L,
                            isVideo = isVid
                        )
                    )
                }
            }
        }

        items.sortedByDescending { it.dateAddedSec }
    }

    private fun queryMediaStoreImages(out: MutableList<MediaItem>) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.OWNER_PACKAGE_NAME
        )
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.OWNER_PACKAGE_NAME} = ?"
        } else null
        val args = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(context.packageName)
        } else null

        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    out.add(
                        MediaItem(
                            id = id,
                            uri = uri,
                            displayName = cursor.getString(nameCol) ?: "Photo_$id.jpg",
                            mimeType = cursor.getString(mimeCol) ?: "image/jpeg",
                            sizeBytes = cursor.getLong(sizeCol),
                            dateAddedSec = cursor.getLong(dateCol),
                            isVideo = false
                        )
                    )
                }
            }
        }
    }

    private fun queryMediaStoreVideos(out: MutableList<MediaItem>) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.OWNER_PACKAGE_NAME
        )
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Video.Media.OWNER_PACKAGE_NAME} = ?"
        } else null
        val args = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(context.packageName)
        } else null

        runCatching {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    out.add(
                        MediaItem(
                            id = id,
                            uri = uri,
                            displayName = cursor.getString(nameCol) ?: "Video_$id.mp4",
                            mimeType = cursor.getString(mimeCol) ?: "video/mp4",
                            sizeBytes = cursor.getLong(sizeCol),
                            dateAddedSec = cursor.getLong(dateCol),
                            durationMs = cursor.getLong(durCol),
                            isVideo = true
                        )
                    )
                }
            }
        }
    }

    private fun queryLegacyAppMediaDir(dir: File, isVideo: Boolean, items: MutableList<MediaItem>) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isFile) {
                items.add(
                    MediaItem(
                        id = file.absolutePath.hashCode().toLong(),
                        uri = Uri.fromFile(file),
                        displayName = file.name,
                        mimeType = if (isVideo) "video/mp4" else "image/jpeg",
                        sizeBytes = file.length(),
                        dateAddedSec = file.lastModified() / 1000L,
                        isVideo = isVideo
                    )
                )
            }
        }
    }

    suspend fun importPickedMedia(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val isVid = mime.startsWith("video/")
            val ext = if (isVid) "mp4" else "jpg"
            val name = "IMP_${System.currentTimeMillis()}.$ext"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = if (isVid) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    val rel = if (isVid) "${Environment.DIRECTORY_MOVIES}/i17ProMaxSim"
                    else "${Environment.DIRECTORY_PICTURES}/i17ProMaxSim"
                    put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
                }
                val destUri = context.contentResolver.insert(collection, values)
                    ?: error("Unable to insert media")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    context.contentResolver.openOutputStream(destUri)?.use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                val sub = if (isVid) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                val dir = File(context.getExternalFilesDir(sub), "i17ProMaxSim")
                if (!dir.exists()) dir.mkdirs()
                val destFile = File(dir, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }
            }
            true
        }.getOrDefault(false)
    }

    suspend fun deleteMediaItem(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (item.uri.scheme == "file") {
                File(item.uri.path ?: "").delete()
            } else {
                context.contentResolver.delete(item.uri, null, null) > 0
            }
        }.getOrDefault(false)
    }

    fun shareMediaItem(item: MediaItem) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${item.displayName}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    suspend fun readExifSummary(uri: Uri): ExifSummary = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val w = exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH) ?: "?"
                val h = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH) ?: "?"
                val model = exif.getAttribute(ExifInterface.TAG_MODEL) ?: "${Build.MANUFACTURER} ${Build.MODEL}"
                val iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY) ?: "Auto"
                val exp = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME) ?: "Auto"
                val fNum = exif.getAttribute(ExifInterface.TAG_F_NUMBER) ?: "Auto"
                ExifSummary(
                    dimensions = "$w × $h",
                    cameraModel = model,
                    iso = iso,
                    exposure = exp,
                    aperture = if (fNum == "Auto") "Auto" else "f/$fNum"
                )
            } ?: ExifSummary()
        }.getOrDefault(ExifSummary())
    }

    suspend fun decodeBitmapForEditor(uri: Uri, maxDim: Int = 1800): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            var sampleSize = 1
            while (boundsOpts.outWidth / sampleSize > maxDim || boundsOpts.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        }.getOrNull()
    }

    fun applyEditsToBitmap(
        source: Bitmap,
        brightness: Float,
        contrast: Float,
        saturation: Float,
        rotationDegrees: Int,
        preset: EditorPreset
    ): Bitmap {
        val rotated = if (rotationDegrees % 360 != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }

        val out = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cm = ColorMatrix()
        when (preset) {
            EditorPreset.ORIGINAL -> {}
            EditorPreset.TITANIUM -> {
                cm.setSaturation(0.72f)
                val cool = ColorMatrix(
                    floatArrayOf(
                        0.96f, 0f, 0f, 0f, 4f,
                        0f, 0.99f, 0f, 0f, 6f,
                        0f, 0f, 1.08f, 0f, 10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(cool)
            }
            EditorPreset.VIVID -> {
                cm.setSaturation(1.38f)
            }
            EditorPreset.NOIR -> {
                cm.setSaturation(0f)
            }
            EditorPreset.WARM -> {
                val warm = ColorMatrix(
                    floatArrayOf(
                        1.10f, 0f, 0f, 0f, 10f,
                        0f, 1.02f, 0f, 0f, 4f,
                        0f, 0f, 0.90f, 0f, -6f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(warm)
            }
        }

        val satMatrix = ColorMatrix().apply { setSaturation(saturation) }
        cm.postConcat(satMatrix)

        val bOffset = brightness * 120f
        val cScale = contrast
        val translate = (-0.5f * cScale + 0.5f) * 255f + bOffset
        val bcMatrix = ColorMatrix(
            floatArrayOf(
                cScale, 0f, 0f, 0f, translate,
                0f, cScale, 0f, 0f, translate,
                0f, 0f, cScale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(bcMatrix)

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(rotated, 0f, 0f, paint)
        return out
    }

    suspend fun saveEditedBitmap(bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "EDIT_i17_$timestamp.jpg"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/i17ProMaxSim")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching null
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                uri
            } else {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "i17ProMaxSim")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, filename)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                Uri.fromFile(file)
            }
        }.getOrNull()
    }

    suspend fun ensureBuiltInAudioTracks(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val audioDir = File(context.filesDir, "audio")
        if (!audioDir.exists()) audioDir.mkdirs()

        val specs = listOf(
            Triple("titanium_pulse.wav", "Titanium Horizon", listOf(220.0, 277.18, 329.63, 440.0)),
            Triple("liquid_glass.wav", "Liquid Glass Ambient", listOf(196.0, 246.94, 293.66, 392.0)),
            Triple("obsidian_chime.wav", "Obsidian Night", listOf(174.61, 220.0, 261.63, 349.23))
        )

        val tracks = mutableListOf<AudioTrack>()
        specs.forEachIndexed { idx, (fileName, title, freqs) ->
            val file = File(audioDir, fileName)
            if (!file.exists()) {
                writeSynthesizedWav(file, freqs, durationSec = 18)
            }
            tracks.add(
                AudioTrack(
                    id = "builtin_$idx",
                    title = title,
                    artist = "i17 Pro Studio Audio",
                    album = "iOS 26 Spatial Collection",
                    uri = Uri.fromFile(file),
                    durationLabel = "0:18"
                )
            )
        }

        // Also include any user-imported audio files in audioDir
        audioDir.listFiles()?.forEach { file ->
            if (file.isFile && specs.none { it.first == file.name }) {
                tracks.add(
                    AudioTrack(
                        id = file.name,
                        title = file.nameWithoutExtension,
                        artist = "Imported Audio",
                        album = "1TB Local Vault",
                        uri = Uri.fromFile(file),
                        durationLabel = "Local"
                    )
                )
            }
        }
        tracks
    }

    suspend fun importAudioTrack(uri: Uri, displayName: String): AudioTrack? = withContext(Dispatchers.IO) {
        runCatching {
            val audioDir = File(context.filesDir, "audio")
            if (!audioDir.exists()) audioDir.mkdirs()
            val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val dest = File(audioDir, safeName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            AudioTrack(
                id = dest.name,
                title = dest.nameWithoutExtension,
                artist = "Imported Audio",
                album = "1TB Local Vault",
                uri = Uri.fromFile(dest),
                durationLabel = "Local"
            )
        }.getOrNull()
    }

    private fun writeSynthesizedWav(file: File, freqs: List<Double>, durationSec: Int) {
        val sampleRate = 22050
        val totalSamples = sampleRate * durationSec
        val dataBytes = totalSamples * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + dataBytes)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1.toShort())
            putShort(1.toShort())
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2.toShort())
            putShort(16.toShort())
            put("data".toByteArray())
            putInt(dataBytes)
        }.array()

        FileOutputStream(file).use { out ->
            out.write(header)
            val pcmBuf = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val envelope = when {
                    t < 0.8 -> t / 0.8
                    t > durationSec - 1.2 -> ((durationSec - t) / 1.2).coerceAtLeast(0.0)
                    else -> 1.0
                }
                val lfo = 0.7 + 0.3 * sin(2.0 * PI * 0.25 * t)
                var sampleSum = 0.0
                freqs.forEachIndexed { idx, f ->
                    val phaseMod = sin(2.0 * PI * 0.12 * t + idx) * 0.15
                    sampleSum += sin(2.0 * PI * f * t + phaseMod)
                }
                val sample = ((sampleSum / freqs.size) * 0.42 * envelope * lfo * Short.MAX_VALUE)
                    .toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
                pcmBuf.putShort(sample)
                if (!pcmBuf.hasRemaining()) {
                    out.write(pcmBuf.array())
                    pcmBuf.clear()
                }
            }
            if (pcmBuf.position() > 0) {
                out.write(pcmBuf.array(), 0, pcmBuf.position())
            }
        }
    }
}
