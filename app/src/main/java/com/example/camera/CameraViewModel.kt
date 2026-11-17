package com.example.camera

import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Range
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class CameraProMode(val label: String) {
    PHOTO("PHOTO"),
    VIDEO("VIDEO"),
    PORTRAIT("PORTRAIT"),
    NIGHT("NIGHT"),
    PRO("PRO")
}

enum class FlashOption(val label: String, val cameraXMode: Int) {
    AUTO("AUTO", ImageCapture.FLASH_MODE_AUTO),
    ON("ON", ImageCapture.FLASH_MODE_ON),
    OFF("OFF", ImageCapture.FLASH_MODE_OFF)
}

enum class WhiteBalanceOption(val label: String, val awbMode: Int) {
    AUTO("Auto", CaptureRequest.CONTROL_AWB_MODE_AUTO),
    DAYLIGHT("Day", CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT),
    CLOUDY("Cloudy", CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT),
    TUNGSTEN("Tungsten", CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT),
    FLUORESCENT("Fluor", CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT)
}

enum class VideoResolutionOption(val label: String, val quality: Quality) {
    UHD_4K("4K", Quality.UHD),
    FHD_1080P("1080p", Quality.FHD),
    HD_720P("720p", Quality.HD)
}

data class CameraUiState(
    val mode: CameraProMode = CameraProMode.PHOTO,
    val isFrontCamera: Boolean = false,
    val flashOption: FlashOption = FlashOption.AUTO,
    val isTorchOn: Boolean = false,
    val isHdrRequested: Boolean = true,
    val isMacroRequested: Boolean = false,
    val showGridLines: Boolean = true,
    val timerSeconds: Int = 0,
    val countdownRemaining: Int = 0,
    val zoomRatio: Float = 1.0f,
    val evIndex: Int = 0,
    val evRangeMin: Int = 0,
    val evRangeMax: Int = 0,
    val evStep: Float = 0.5f,
    val whiteBalance: WhiteBalanceOption = WhiteBalanceOption.AUTO,
    val manualIso: Int = 400,
    val manualShutterNs: Long = 16_666_666L,
    val manualFocusDistance: Float = 0f,
    val isManualExposureEnabled: Boolean = false,
    val isManualFocusEnabled: Boolean = false,
    val selectedVideoRes: VideoResolutionOption = VideoResolutionOption.FHD_1080P,
    val targetFps: Int = 30,
    val isStabilizationEnabled: Boolean = true,
    val isRecording: Boolean = false,
    val isRecordingPaused: Boolean = false,
    val recordingDurationSec: Long = 0L,
    val isAudioEnabled: Boolean = true,
    val lastCapturedUri: Uri? = null,
    val statusBanner: String? = null,
    val errorMessage: String? = null,
    val capabilities: CameraHardwareCapabilities = CameraHardwareCapabilities(),
    val isCapabilitiesSheetVisible: Boolean = false,
    val focusPointNormalized: Pair<Float, Float>? = null
)

class CameraViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var extensionsManager: ExtensionsManager? = null
    private var boundCamera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var countdownJob: Job? = null
    private var focusClearJob: Job? = null

    fun initializeAndBind(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                cameraProvider = provider
                val extFuture = ExtensionsManager.getInstanceAsync(context, provider)
                extFuture.addListener({
                    extensionsManager = runCatching { extFuture.get() }.getOrNull()
                    bindCameraUseCases(lifecycleOwner, surfaceProvider)
                }, ContextCompat.getMainExecutor(context))
            }.onFailure { err ->
                _uiState.update {
                    it.copy(errorMessage = "Camera initialization failed: ${err.localizedMessage}")
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        val provider = cameraProvider ?: return
        val state = _uiState.value

        val baseSelector = if (state.isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val hasCamera = runCatching { provider.hasCamera(baseSelector) }.getOrDefault(false)
        if (!hasCamera) {
            _uiState.update {
                it.copy(errorMessage = "Selected ${if (state.isFrontCamera) "front" else "rear"} camera is not available on this hardware.")
            }
            return
        }

        // First inspect raw hardware capabilities using baseSelector
        val rawInfo = runCatching { provider.getCameraInfo(baseSelector) }.getOrNull()
        val inspectedCaps = if (rawInfo != null) {
            CameraCapabilityInspector.inspect(
                cameraProvider = provider,
                extensionsManager = extensionsManager,
                baseSelector = baseSelector,
                cameraInfo = rawInfo,
                facingLabel = if (state.isFrontCamera) "Front" else "Rear"
            )
        } else {
            CameraHardwareCapabilities()
        }

        // Ensure chosen video resolution & FPS are truly supported
        val validVideoRes = when {
            state.selectedVideoRes == VideoResolutionOption.UHD_4K && inspectedCaps.supports4K ->
                VideoResolutionOption.UHD_4K
            inspectedCaps.supports1080p -> VideoResolutionOption.FHD_1080P
            inspectedCaps.supports720p -> VideoResolutionOption.HD_720P
            else -> VideoResolutionOption.FHD_1080P
        }
        val validFps = if (state.targetFps == 60 && inspectedCaps.supports60Fps) 60 else 30

        // Choose ExtensionMode only for photo-capable modes (Extensions do not support VideoCapture)
        val extManager = extensionsManager
        val activeSelector = if (state.mode != CameraProMode.VIDEO && extManager != null) {
            val desiredExt = when (state.mode) {
                CameraProMode.PORTRAIT -> ExtensionMode.BOKEH
                CameraProMode.NIGHT -> ExtensionMode.NIGHT
                CameraProMode.PHOTO -> if (state.isHdrRequested) ExtensionMode.HDR else ExtensionMode.NONE
                CameraProMode.PRO, CameraProMode.VIDEO -> ExtensionMode.NONE
            }
            if (desiredExt != ExtensionMode.NONE && extManager.isExtensionAvailable(baseSelector, desiredExt)) {
                extManager.getExtensionEnabledCameraSelector(baseSelector, desiredExt)
            } else {
                baseSelector
            }
        } else {
            baseSelector
        }

        val previewBuilder = Preview.Builder()

        try {
            provider.unbindAll()

            val camera: Camera = if (state.mode == CameraProMode.VIDEO) {
                val qualitySelector = QualitySelector.fromOrderedList(
                    listOf(validVideoRes.quality, Quality.FHD, Quality.HD, Quality.SD)
                )
                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build()

                val videoBuilder = VideoCapture.Builder(recorder)
                if (inspectedCaps.supportsVideoStabilization && state.isStabilizationEnabled) {
                    videoBuilder.setVideoStabilizationEnabled(true)
                }
                val matchingFpsRange = inspectedCaps.supportedFpsRanges.firstOrNull {
                    it.upper == validFps && it.lower <= validFps
                }
                if (matchingFpsRange != null) {
                    videoBuilder.setTargetFrameRate(matchingFpsRange)
                }

                val preview = previewBuilder.build().apply {
                    setSurfaceProvider(surfaceProvider)
                }
                val vc = videoBuilder.build()
                videoCapture = vc
                imageCapture = null

                provider.bindToLifecycle(lifecycleOwner, activeSelector, preview, vc)
            } else {
                val resSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build()

                val ic = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setResolutionSelector(resSelector)
                    .setFlashMode(state.flashOption.cameraXMode)
                    .build()

                val preview = previewBuilder.build().apply {
                    setSurfaceProvider(surfaceProvider)
                }
                imageCapture = ic
                videoCapture = null

                provider.bindToLifecycle(lifecycleOwner, activeSelector, preview, ic)
            }

            boundCamera = camera
            val exposureState = camera.cameraInfo.exposureState
            val evRange = exposureState.exposureCompensationRange
            val evStep = exposureState.exposureCompensationStep.toFloat()

            _uiState.update {
                it.copy(
                    capabilities = inspectedCaps,
                    selectedVideoRes = validVideoRes,
                    targetFps = validFps,
                    evRangeMin = evRange.lower,
                    evRangeMax = evRange.upper,
                    evStep = evStep,
                    evIndex = it.evIndex.coerceIn(evRange.lower, evRange.upper),
                    zoomRatio = it.zoomRatio.coerceIn(inspectedCaps.minZoomRatio, inspectedCaps.maxZoomRatio),
                    manualIso = inspectedCaps.isoRange?.let { r -> it.manualIso.coerceIn(r.lower, r.upper) }
                        ?: it.manualIso,
                    manualShutterNs = inspectedCaps.exposureTimeRangeNs?.let { r ->
                        it.manualShutterNs.coerceIn(r.lower, r.upper)
                    } ?: it.manualShutterNs,
                    errorMessage = null
                )
            }

            applyCameraControls()
        } catch (e: Exception) {
            _uiState.update {
                it.copy(errorMessage = "Camera bind error: ${e.localizedMessage}")
            }
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun applyCameraControls() {
        val cam = boundCamera ?: return
        val state = _uiState.value
        val caps = state.capabilities

        runCatching {
            cam.cameraControl.setZoomRatio(
                state.zoomRatio.coerceIn(caps.minZoomRatio, caps.maxZoomRatio)
            )
            if (cam.cameraInfo.hasFlashUnit()) {
                cam.cameraControl.enableTorch(state.isTorchOn)
            }
            if (cam.cameraInfo.exposureState.isExposureCompensationSupported && !state.isManualExposureEnabled) {
                cam.cameraControl.setExposureCompensationIndex(
                    state.evIndex.coerceIn(state.evRangeMin, state.evRangeMax)
                )
            }

            val c2Control = Camera2CameraControl.from(cam.cameraControl)
            val optionsBuilder = CaptureRequestOptions.Builder()

            if (caps.supportedAwbModes.contains(state.whiteBalance.awbMode)) {
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE,
                    state.whiteBalance.awbMode
                )
            }

            if (caps.supportsOis && state.isStabilizationEnabled) {
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                )
            }

            if (state.mode == CameraProMode.PRO && state.isManualExposureEnabled && caps.supportsManualSensor) {
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_OFF
                )
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_SENSITIVITY,
                    state.manualIso
                )
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_EXPOSURE_TIME,
                    state.manualShutterNs
                )
            } else {
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON
                )
            }

            if (state.mode == CameraProMode.PRO && state.isManualFocusEnabled && caps.minFocusDistanceDiopters > 0f) {
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF
                )
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.LENS_FOCUS_DISTANCE,
                    state.manualFocusDistance.coerceIn(0f, caps.minFocusDistanceDiopters)
                )
            } else if (state.isMacroRequested && caps.minFocusDistanceDiopters >= 5f) {
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_MACRO
                )
            } else {
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
            }

            c2Control.setCaptureRequestOptions(optionsBuilder.build())
        }
    }

    fun selectMode(mode: CameraProMode, lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        if (_uiState.value.isRecording) return
        _uiState.update { it.copy(mode = mode, statusBanner = null) }
        bindCameraUseCases(lifecycleOwner, surfaceProvider)
    }

    fun toggleFrontRear(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        if (_uiState.value.isRecording) return
        _uiState.update { it.copy(isFrontCamera = !it.isFrontCamera, isTorchOn = false, zoomRatio = 1.0f) }
        bindCameraUseCases(lifecycleOwner, surfaceProvider)
    }

    fun cycleFlash() {
        val next = when (_uiState.value.flashOption) {
            FlashOption.AUTO -> FlashOption.ON
            FlashOption.ON -> FlashOption.OFF
            FlashOption.OFF -> FlashOption.AUTO
        }
        imageCapture?.flashMode = next.cameraXMode
        _uiState.update { it.copy(flashOption = next) }
    }

    fun toggleTorch() {
        val next = !_uiState.value.isTorchOn
        _uiState.update { it.copy(isTorchOn = next) }
        applyCameraControls()
    }

    fun toggleHdr(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        _uiState.update { it.copy(isHdrRequested = !it.isHdrRequested) }
        bindCameraUseCases(lifecycleOwner, surfaceProvider)
    }

    fun toggleMacro() {
        val next = !_uiState.value.isMacroRequested
        _uiState.update {
            it.copy(
                isMacroRequested = next,
                statusBanner = if (next && !it.capabilities.supportsMacroFocus) {
                    "Hardware macro lens unavailable; using closest autofocus"
                } else if (next) {
                    "Macro Focus Active"
                } else null
            )
        }
        applyCameraControls()
    }

    fun toggleGridLines() {
        _uiState.update { it.copy(showGridLines = !it.showGridLines) }
    }

    fun cycleTimer() {
        val next = when (_uiState.value.timerSeconds) {
            0 -> 3
            3 -> 10
            else -> 0
        }
        _uiState.update { it.copy(timerSeconds = next) }
    }

    fun setZoom(ratio: Float) {
        val caps = _uiState.value.capabilities
        val clamped = ratio.coerceIn(caps.minZoomRatio, caps.maxZoomRatio)
        _uiState.update { it.copy(zoomRatio = clamped) }
        boundCamera?.cameraControl?.setZoomRatio(clamped)
    }

    fun setExposureIndex(index: Int) {
        _uiState.update { it.copy(evIndex = index, isManualExposureEnabled = false) }
        applyCameraControls()
    }

    fun setWhiteBalance(option: WhiteBalanceOption) {
        _uiState.update { it.copy(whiteBalance = option) }
        applyCameraControls()
    }

    fun setManualExposure(enabled: Boolean, iso: Int? = null, shutterNs: Long? = null) {
        _uiState.update {
            it.copy(
                isManualExposureEnabled = enabled,
                manualIso = iso ?: it.manualIso,
                manualShutterNs = shutterNs ?: it.manualShutterNs
            )
        }
        applyCameraControls()
    }

    fun setManualFocus(enabled: Boolean, distance: Float? = null) {
        _uiState.update {
            it.copy(
                isManualFocusEnabled = enabled,
                manualFocusDistance = distance ?: it.manualFocusDistance
            )
        }
        applyCameraControls()
    }

    fun selectVideoResolution(
        option: VideoResolutionOption,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        if (_uiState.value.isRecording) return
        _uiState.update { it.copy(selectedVideoRes = option) }
        bindCameraUseCases(lifecycleOwner, surfaceProvider)
    }

    fun toggleFps(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        if (_uiState.value.isRecording || !_uiState.value.capabilities.supports60Fps) return
        val next = if (_uiState.value.targetFps == 30) 60 else 30
        _uiState.update { it.copy(targetFps = next) }
        bindCameraUseCases(lifecycleOwner, surfaceProvider)
    }

    fun toggleStabilization(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        if (_uiState.value.isRecording) return
        _uiState.update { it.copy(isStabilizationEnabled = !it.isStabilizationEnabled) }
        bindCameraUseCases(lifecycleOwner, surfaceProvider)
    }

    fun setCapabilitiesSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(isCapabilitiesSheetVisible = visible) }
    }

    fun triggerTapToFocus(meteringPoint: MeteringPoint, normalizedX: Float, normalizedY: Float) {
        val cam = boundCamera ?: return
        val action = FocusMeteringAction.Builder(meteringPoint, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
        _uiState.update { it.copy(focusPointNormalized = normalizedX to normalizedY, isManualFocusEnabled = false) }
        focusClearJob?.cancel()
        focusClearJob = viewModelScope.launch {
            delay(1800)
            _uiState.update { it.copy(focusPointNormalized = null) }
        }
    }

    fun capturePhoto(context: Context, onSaved: (Uri) -> Unit = {}) {
        val ic = imageCapture ?: return
        val timer = _uiState.value.timerSeconds
        if (timer > 0 && countdownJob?.isActive != true) {
            countdownJob = viewModelScope.launch {
                for (sec in timer downTo 1) {
                    _uiState.update { it.copy(countdownRemaining = sec) }
                    delay(1000)
                }
                _uiState.update { it.copy(countdownRemaining = 0) }
                executeImageCapture(context, ic, onSaved)
            }
        } else {
            executeImageCapture(context, ic, onSaved)
        }
    }

    private fun executeImageCapture(
        context: Context,
        ic: ImageCapture,
        onSaved: (Uri) -> Unit
    ) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "IMG_i17_$timestamp.jpg"

        val outputOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/i17ProMaxSim")
            }
            ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "i17ProMaxSim")
            if (!dir.exists()) dir.mkdirs()
            ImageCapture.OutputFileOptions.Builder(File(dir, filename)).build()
        }

        ic.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val uri = outputFileResults.savedUri
                    _uiState.update {
                        it.copy(
                            lastCapturedUri = uri ?: it.lastCapturedUri,
                            statusBanner = "Photo saved ($filename)",
                            errorMessage = null
                        )
                    }
                    if (uri != null) onSaved(uri)
                }

                override fun onError(exception: ImageCaptureException) {
                    _uiState.update {
                        it.copy(errorMessage = "Photo capture error: ${exception.message}")
                    }
                }
            }
        )
    }

    fun toggleVideoRecording(
        context: Context,
        hasAudioPermission: Boolean,
        onSaved: (Uri) -> Unit = {}
    ) {
        val currentRec = activeRecording
        if (currentRec != null) {
            currentRec.stop()
            activeRecording = null
            return
        }

        val vc = videoCapture ?: return
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "VID_i17_$timestamp.mp4"

        var pendingRecording = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/i17ProMaxSim")
            }
            val mediaStoreOutput = MediaStoreOutputOptions.Builder(
                context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(contentValues).build()
            vc.output.prepareRecording(context, mediaStoreOutput)
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "i17ProMaxSim")
            if (!dir.exists()) dir.mkdirs()
            val fileOutput = FileOutputOptions.Builder(File(dir, filename)).build()
            vc.output.prepareRecording(context, fileOutput)
        }

        if (hasAudioPermission && _uiState.value.isAudioEnabled) {
            pendingRecording = pendingRecording.withAudioEnabled()
        }

        activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    _uiState.update {
                        it.copy(
                            isRecording = true,
                            isRecordingPaused = false,
                            recordingDurationSec = 0L,
                            statusBanner = "Recording ${it.selectedVideoRes.label} • ${it.targetFps}fps"
                        )
                    }
                }
                is VideoRecordEvent.Pause -> {
                    _uiState.update { it.copy(isRecordingPaused = true, statusBanner = "Recording Paused") }
                }
                is VideoRecordEvent.Resume -> {
                    _uiState.update { it.copy(isRecordingPaused = false, statusBanner = "Recording Resumed") }
                }
                is VideoRecordEvent.Status -> {
                    val sec = TimeUnit.NANOSECONDS.toSeconds(event.recordingStats.recordedDurationNanos)
                    _uiState.update { it.copy(recordingDurationSec = sec) }
                }
                is VideoRecordEvent.Finalize -> {
                    activeRecording = null
                    if (event.hasError()) {
                        _uiState.update {
                            it.copy(
                                isRecording = false,
                                isRecordingPaused = false,
                                errorMessage = "Video recording error (${event.error})"
                            )
                        }
                    } else {
                        val savedUri = event.outputResults.outputUri
                        _uiState.update {
                            it.copy(
                                isRecording = false,
                                isRecordingPaused = false,
                                lastCapturedUri = savedUri,
                                statusBanner = "Video saved ($filename)"
                            )
                        }
                        onSaved(savedUri)
                    }
                }
            }
        }
    }

    fun togglePauseResumeRecording() {
        val rec = activeRecording ?: return
        if (_uiState.value.isRecordingPaused) {
            rec.resume()
        } else {
            rec.pause()
        }
    }

    fun toggleAudioEnabled() {
        if (_uiState.value.isRecording) return
        _uiState.update { it.copy(isAudioEnabled = !it.isAudioEnabled) }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(statusBanner = null, errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        activeRecording?.stop()
        countdownJob?.cancel()
        focusClearJob?.cancel()
    }
}
