package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.files.FilesManagerScreen
import com.example.media.EditorPreset
import com.example.media.MediaRepository
import com.example.settings.DeviceSettingsScreen
import com.example.system.HardwareTelemetryRepository
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app name matches i17 Pro Max Sim`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("i17 Pro Max Sim", appName)
    }

    @Test
    fun `telemetry repository distinguishes 16GB virtual RAM and 1TB virtual storage from physical hardware`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = HardwareTelemetryRepository(context)

        val ram = repo.readRamTelemetry()
        assertEquals(16.0, ram.virtualTotalGb, 0.001)
        assertTrue(ram.cpuCores >= 1)

        val storage = repo.readStorageTelemetry(
            realPhotosCount = 2,
            realPhotosBytes = 2048L,
            realVideosCount = 1,
            realVideosBytes = 8192L
        )
        assertEquals(1024.0, storage.virtualTotalGb, 0.001)
        assertTrue(storage.virtualUsedGb > 0.0)
        assertTrue(storage.virtualFreeGb < 1024.0)
    }

    @Test
    fun `media repository synthesizes playable wav tracks and applies bitmap edits`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = MediaRepository(context)

        val tracks = repo.ensureBuiltInAudioTracks()
        assertTrue(tracks.size >= 3)
        val firstFile = File(tracks.first().uri.path!!)
        assertTrue(firstFile.exists() && firstFile.length() > 44)

        val src = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        val rotated = repo.applyEditsToBitmap(
            source = src,
            brightness = 0.1f,
            contrast = 1.2f,
            saturation = 1.1f,
            rotationDegrees = 90,
            preset = EditorPreset.TITANIUM
        )
        assertEquals(48, rotated.width)
        assertEquals(64, rotated.height)
    }

    @Test
    fun `device settings screen displays 16GB virtual RAM and actual Android RAM`() {
        var backCalled = false
        composeRule.setContent {
            MyApplicationTheme {
                DeviceSettingsScreen(
                    onBack = { backCalled = true },
                    onOpenStorageDashboard = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("optimize_memory_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("RAM & Memory Dashboard").assertIsDisplayed()
        composeRule.onNodeWithTag("virtual_ram_value").assertIsDisplayed()
        composeRule.onNodeWithTag("actual_ram_value").assertIsDisplayed()
        composeRule.onNodeWithTag("top_back_button").performClick()
        assertTrue(backCalled)
    }

    @Test
    fun `files manager screen displays 1TB virtual storage and real file actions`() {
        composeRule.setContent {
            MyApplicationTheme {
                FilesManagerScreen(onBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1 TB Virtual Storage").assertIsDisplayed()
        composeRule.onNodeWithTag("files_new_folder_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("files_new_doc_button").performScrollTo().assertIsDisplayed()
    }
}
