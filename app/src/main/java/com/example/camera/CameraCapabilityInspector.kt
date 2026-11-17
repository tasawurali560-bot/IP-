package com.example.camera

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.util.Range
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.Recorder

data class CameraHardwareCapabilities(
    val cameraFacing: String = "Rear",
    val hardwareLevelLabel: String = "Unknown",
    val maxPhotoResolutionLabel: String = "Unknown",
    val maxMegapixels: Double = 0.0,
    val hasFlash: Boolean = false,
    val supports4K: Boolean = false,
    val supports1080p: Boolean = false,
    val supports720p: Boolean = false,
    val supportedVideoQualities: List<Quality> = emptyList(),
    val supports30Fps: Boolean = true,
    val supports60Fps: Boolean = false,
    val supportedFpsRanges: List<Range<Int>> = emptyList(),
    val minZoomRatio: Float = 1.0f,
    val maxZoomRatio: Float = 1.0f,
    val hasOpticalZoomLenses: Boolean = false,
    val focalLengthsMm: List<Float> = emptyList(),
    val supportsOis: Boolean = false,
    val supportsVideoStabilization: Boolean = false,
    val supportsHdrExtension: Boolean = false,
    val supportsPortraitBokehExtension: Boolean = false,
    val supportsNightExtension: Boolean = false,
    val isoRange: Range<Int>? = null,
    val exposureTimeRangeNs: Range<Long>? = null,
    val minFocusDistanceDiopters: Float = 0f,
    val supportedAwbModes: List<Int> = emptyList(),
    val supportsManualSensor: Boolean = false,
    val supportsMacroFocus: Boolean = false
)

object CameraCapabilityInspector {

    @OptIn(ExperimentalCamera2Interop::class)
    fun inspect(
        cameraProvider: ProcessCameraProvider,
        extensionsManager: ExtensionsManager?,
        baseSelector: CameraSelector,
        cameraInfo: CameraInfo,
        facingLabel: String
    ): CameraHardwareCapabilities {
        val c2Info = runCatching { Camera2CameraInfo.from(cameraInfo) }.getOrNull()

        val hwLevel = c2Info?.getCameraCharacteristic(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        val hwLevelLabel = when (hwLevel) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3 (Full Pro)"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL (Manual Control)"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "STANDARD"
        }

        val streamConfigMap = c2Info?.getCameraCharacteristic(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        )
        val jpegSizes = streamConfigMap?.getOutputSizes(ImageFormat.JPEG)?.toList().orEmpty()
        val maxSize = jpegSizes.maxByOrNull { it.width.toLong() * it.height.toLong() }
        val maxMp = if (maxSize != null) {
            (maxSize.width.toDouble() * maxSize.height.toDouble()) / 1_000_000.0
        } else 0.0
        val maxResLabel = if (maxSize != null) {
            "${maxSize.width} × ${maxSize.height} (${String.format("%.1f", maxMp)} MP)"
        } else {
            "Auto"
        }

        val videoCaps = Recorder.getVideoCapabilities(cameraInfo)
        val supportedQualities = runCatching {
            videoCaps.getSupportedQualities(DynamicRange.SDR)
        }.getOrDefault(emptyList())

        val supports4k = supportedQualities.contains(Quality.UHD)
        val supports1080p = supportedQualities.contains(Quality.FHD)
        val supports720p = supportedQualities.contains(Quality.HD)
        val supportsVideoStab = runCatching { videoCaps.isStabilizationSupported }.getOrDefault(false)

        val fpsRanges = c2Info?.getCameraCharacteristic(
            CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
        )?.toList().orEmpty()
        val supports60 = fpsRanges.any { it.upper >= 60 }
        val supports30 = fpsRanges.isEmpty() || fpsRanges.any { it.upper >= 30 }

        val zoomState = cameraInfo.zoomState.value
        val minZoom = zoomState?.minZoomRatio ?: 1.0f
        val maxZoom = zoomState?.maxZoomRatio ?: 1.0f

        val focalLengths = c2Info?.getCameraCharacteristic(
            CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
        )?.toList().orEmpty()
        val hasOpticalZoom = focalLengths.distinct().size > 1 || minZoom < 0.9f

        val oisModes = c2Info?.getCameraCharacteristic(
            CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION
        )?.toList().orEmpty()
        val supportsOis = oisModes.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)

        val videoStabModes = c2Info?.getCameraCharacteristic(
            CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES
        )?.toList().orEmpty()
        val hasHardwareVideoStab = supportsVideoStab ||
            videoStabModes.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)

        val isoRange = c2Info?.getCameraCharacteristic(
            CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
        )
        val exposureTimeRange = c2Info?.getCameraCharacteristic(
            CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
        )
        val minFocusDist = c2Info?.getCameraCharacteristic(
            CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
        ) ?: 0f

        val awbModes = c2Info?.getCameraCharacteristic(
            CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES
        )?.toList().orEmpty()

        val capabilitiesArray = c2Info?.getCameraCharacteristic(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
        )?.toList().orEmpty()
        val supportsManualSensor = capabilitiesArray.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
        ) && isoRange != null && exposureTimeRange != null

        val supportsHdr = extensionsManager?.isExtensionAvailable(baseSelector, ExtensionMode.HDR) == true
        val supportsBokeh = extensionsManager?.isExtensionAvailable(baseSelector, ExtensionMode.BOKEH) == true
        val supportsNight = extensionsManager?.isExtensionAvailable(baseSelector, ExtensionMode.NIGHT) == true

        return CameraHardwareCapabilities(
            cameraFacing = facingLabel,
            hardwareLevelLabel = hwLevelLabel,
            maxPhotoResolutionLabel = maxResLabel,
            maxMegapixels = maxMp,
            hasFlash = cameraInfo.hasFlashUnit(),
            supports4K = supports4k,
            supports1080p = supports1080p,
            supports720p = supports720p,
            supportedVideoQualities = supportedQualities,
            supports30Fps = supports30,
            supports60Fps = supports60,
            supportedFpsRanges = fpsRanges,
            minZoomRatio = minZoom,
            maxZoomRatio = maxZoom,
            hasOpticalZoomLenses = hasOpticalZoom,
            focalLengthsMm = focalLengths,
            supportsOis = supportsOis,
            supportsVideoStabilization = hasHardwareVideoStab,
            supportsHdrExtension = supportsHdr,
            supportsPortraitBokehExtension = supportsBokeh,
            supportsNightExtension = supportsNight,
            isoRange = isoRange,
            exposureTimeRangeNs = exposureTimeRange,
            minFocusDistanceDiopters = minFocusDist,
            supportedAwbModes = awbModes,
            supportsManualSensor = supportsManualSensor,
            supportsMacroFocus = minFocusDist >= 5.0f || minZoom < 0.7f
        )
    }
}
