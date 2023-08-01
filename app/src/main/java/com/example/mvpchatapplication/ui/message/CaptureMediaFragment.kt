package com.example.mvpchatapplication.ui.message

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MirrorMode.MIRROR_MODE_ON_FRONT_ONLY
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.viewbinding.ViewBinding
import com.example.mvpchatapplication.BindingFragment
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.data.models.Media
import com.example.mvpchatapplication.databinding.FragmentCaptureMediaBinding
import com.example.mvpchatapplication.utils.MessageType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


@AndroidEntryPoint
class CaptureMediaFragment : BindingFragment<FragmentCaptureMediaBinding>() {

    override val bindingInflater: (LayoutInflater) -> ViewBinding
        get() = FragmentCaptureMediaBinding::inflate

    private var imageCapture: ImageCapture? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

    private lateinit var cameraExecutor: ExecutorService

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(
                    requireContext(),
                    "Permission request denied",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                startCamera()
            }
        }

    lateinit var videoCaptureCounter: CountDownTimer

    private var density: Float = 0f

    private var isLongPress = false

    private lateinit var mDetector: GestureDetectorCompat

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        density = resources.displayMetrics.density
        mDetector = GestureDetectorCompat(requireContext(), MyGestureListener())
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        videoCaptureCounter = object : CountDownTimer(6000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.videoProgressBar.setProgress(
                    (((6000 - millisUntilFinished) * 100) / 6000).toInt(),
                    true
                )
                Log.d(TAG, "onTick: ${(((6000 - millisUntilFinished) * 100) / 6000).toInt()}")
            }

            override fun onFinish() {
                binding.videoProgressBar.setProgress(100, true)
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(500)
                    stopVideoRecording()
                }
            }

        }

        // Set up the listeners for take photo and video capture button

        binding.switchCamera.setOnClickListener {
            switchCamera()
        }
        binding.captureLayout.setOnTouchListener { v, event ->
            Log.d(TAG, "onViewCreated: ${event.action}")
            mDetector.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    Log.d("DEBUG_TAG", "ACTION_UP: ")
                    if (isLongPress) {
                        stopVideoRecording();
                        isLongPress = false
                    }
                }
            }
            return@setOnTouchListener true
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

    }

    private fun switchCamera() {
        if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) cameraSelector =
            CameraSelector.DEFAULT_BACK_CAMERA;
        else if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) cameraSelector =
            CameraSelector.DEFAULT_FRONT_CAMERA;
        startCamera();
    }

    private fun scaleCaptureLayout() {
        val sizePixels = (80 * density).toInt()
        val layoutParams = binding.captureLayout.layoutParams
        layoutParams.height = sizePixels
        layoutParams.width = sizePixels
        binding.captureLayout.layoutParams = layoutParams
        binding.videoProgressBar.indicatorSize = (80 * density).toInt()
        binding.imageCaptureIcon.animate().scaleY(.5F).duration = 300
        binding.imageCaptureIcon.animate().scaleX(.5F).duration = 300
    }

    private fun scaleResetCaptureLayout() {
        binding.imageCaptureIcon.animate().scaleY(1F).duration = 300
        binding.imageCaptureIcon.animate().scaleX(1F).duration = 300
        val sizePixels = (64 * density).toInt()
        val layoutParams = binding.captureLayout.layoutParams
        layoutParams.height = sizePixels
        layoutParams.width = sizePixels
        binding.captureLayout.layoutParams = layoutParams
        binding.videoProgressBar.indicatorSize = 64 * density.toInt()
    }


    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault())
            .format(System.currentTimeMillis())
        val extension = ".jpg"

        val fileOutputOptions = ImageCapture.OutputFileOptions
            .Builder(File.createTempFile("IMG_$name", extension, requireContext().cacheDir))
            .setMetadata(ImageCapture.Metadata().also {
                it.isReversedHorizontal = CameraSelector.DEFAULT_FRONT_CAMERA == cameraSelector
            }).build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            fileOutputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    output.savedUri?.let {
                        val bundle = Bundle()
                        bundle.putParcelable(
                            "media",
                            Media(
                                type = MessageType.IMAGE,
                                name = "IMG_$name$extension",
                                uri = it.toString()
                            )
                        )
                        findNavController().navigate(
                            R.id.action_navigation_capture_media_to_navigation_send_media,
                            bundle
                        )
                    }
                    Log.d(TAG, msg)
                }
            }
        )
    }

    private fun stopVideoRecording() {
        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            videoCaptureCounter.cancel()
            binding.videoProgressBar.progress = 0
            scaleResetCaptureLayout()
            recording = null
        }
    }

    // Implements VideoCapture use case, including start and stop capturing.
    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return
        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            videoCaptureCounter.cancel()
            binding.videoProgressBar.progress = 0
            scaleResetCaptureLayout()
            recording = null
            return
        }

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault())
            .format(System.currentTimeMillis())
        val extension = ".mp4"
        // create and start a new recording session
        val fileOutputOptions =
            FileOutputOptions.Builder(File.createTempFile("VID_$name", extension, requireContext().cacheDir))
                .build()
        recording = videoCapture.output
            .prepareRecording(requireContext(), fileOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(
                        requireContext(),
                        android.Manifest.permission.RECORD_AUDIO
                    ) ==
                    PermissionChecker.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(requireContext())) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        videoCaptureCounter.start()
                    }

                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(TAG, msg)
                            val bundle = Bundle()
                            bundle.putParcelable(
                                "media",
                                Media(
                                    type = MessageType.VIDEO,
                                    name = "VID_$name",
                                    uri = recordEvent.outputResults.outputUri.toString()
                                )
                            )
                            findNavController().navigate(
                                R.id.action_navigation_capture_media_to_navigation_send_media,
                                bundle
                            )
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(
                                TAG, "Video capture ends with error: " +
                                        "${recordEvent.error}"
                            )
                        }
                    }
                }
            }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.HIGHEST,
                        FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                    )
                )
                .build()
            videoCapture = VideoCapture.Builder(recorder)
                .setMirrorMode(MIRROR_MODE_ON_FRONT_ONLY)
                .build()

            imageCapture = ImageCapture.Builder().build()

            // Select back camera as a default

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, videoCapture,
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }


    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
    }

    inner class MyGestureListener : GestureDetector.SimpleOnGestureListener() {

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            Log.d("DEBUG_TAG", "onSingleTapConfirmed: $e")
            if (!isLongPress) {
                takePhoto()
            }
            return super.onSingleTapConfirmed(e)
        }

        override fun onLongPress(e: MotionEvent) {
            Log.d("DEBUG_TAG", "onLongPress: $e")
            scaleCaptureLayout()
            captureVideo()
            isLongPress = true
            super.onLongPress(e)
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            Log.d("DEBUG_TAG", "onSingleTapUp: $e")
            return super.onSingleTapUp(e)
        }
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyyMMddHHmmss"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}