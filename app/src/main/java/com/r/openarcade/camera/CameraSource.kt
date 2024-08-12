/* Copyright 2021 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================
*/

package com.r.openarcade.camera

import PianoKeys
import android.annotation.SuppressLint
import android.content.Context
import android.app.UiModeManager
import android.content.res.Configuration

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import kotlinx.coroutines.suspendCancellableCoroutine
import com.r.openarcade.VisualizationUtils
import com.r.openarcade.YuvToRgbConverter
import com.r.openarcade.data.Person
import com.r.openarcade.ml.MoveNetMultiPose
import com.r.openarcade.ml.PoseClassifier
import com.r.openarcade.ml.PoseDetector
import com.r.openarcade.ml.TrackerType
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.graphics.PointF
import android.media.MediaPlayer
import android.os.Looper
import com.r.openarcade.R
import com.r.openarcade.data.BodyPart
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

import android.graphics.Color
import android.os.Build
import android.view.Display
import android.view.WindowManager
import com.r.openarcade.GridButton

class CameraSource(
    private val surfaceView: SurfaceView,
    private val listener: CameraSourceListener? = null
) {

    companion object {
        private const val MIN_CONFIDENCE = .4f
        private const val TAG = "Camera Source"
    }

    private val SCREEN_WIDTH: Int = surfaceView.context.resources.displayMetrics.widthPixels
    private val SCREEN_HEIGHT: Int = surfaceView.context.resources.displayMetrics.heightPixels

// camera sizes - some example, take picture with wrong width and height will be problematic
//        by printCameraYUV420888Sizes
//                                  // Xiami TV S Pro 65 mini LED Performance
//        Width: 320, Height: 240   // 19fps
//        Width: 640, Height: 360   // 17fps
//        Width: 640, Height: 480   // 17fps
//        Width: 1280, Height: 720  // 12fps
//        Width: 1920, Height: 1080 // 9fps

    private var PREVIEW_WIDTH: Int = 1280
    private var PREVIEW_HEIGHT: Int = 720

    private val context: Context = surfaceView.context

    private val lock = Any()
    private var detector: PoseDetector? = null
    private var classifier: PoseClassifier? = null
    private var isTrackerEnabled = false
    private var yuvConverter: YuvToRgbConverter = YuvToRgbConverter(surfaceView.context)
    private lateinit var imageBitmap: Bitmap

    /** Frame count that have been processed so far in an one second interval to calculate FPS. */
    private var fpsTimer: Timer? = null
    private var frameProcessedInOneSecondInterval = 0
    private var framesPerSecond = 0

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        val context = surfaceView.context
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /** Readers used as buffers for camera still shots */
    private var imageReader: ImageReader? = null

    /** The [CameraDevice] that will be opened in this fragment */
    private var camera: CameraDevice? = null

    /** Internal reference to the ongoing [CameraCaptureSession] configured with our parameters */
    private var session: CameraCaptureSession? = null

    /** [HandlerThread] where all buffer reading operations run */
    private var imageReaderThread: HandlerThread? = null

    /** [Handler] corresponding to [imageReaderThread] */
    private var imageReaderHandler: Handler? = null

    private var cameraId: String = ""

    private val handler = Handler(Looper.getMainLooper())

    private var isTV: Boolean = false
    private var isFrontFacing: Boolean = false
    private var imageFlipped: Boolean = false
    private var imageRotated: Double = 0.0
    private var rotation: Int = 0

    suspend fun initCamera() {
        // Open the camera
        camera = openCamera(cameraManager, cameraId)

        isTV = isAndroidTV(context)
        isFrontFacing = getIsFrontFacing(cameraManager, cameraId)
        imageFlipped = isTV || isFrontFacing
        imageRotated = if (!isTV) -90.0 else 0.0

        rotation = getDeviceRotation(context)
        when (rotation) {
            Surface.ROTATION_0 -> println("Device is in portrait orientation")
            Surface.ROTATION_90 -> println("Device is in landscape orientation")
            Surface.ROTATION_180 -> println("Device is in reverse portrait orientation")
            Surface.ROTATION_270 -> println("Device is in reverse landscape orientation")
        }
        imageRotated += (rotation * 90.0)

        printCameraYUV420888Sizes(context, cameraId)
        printSupportedImageFormats(cameraManager, cameraId)



        // Set up the camera components
        setupCamera()
    }

    fun updateImageReader(newWidth: Int, newHeight: Int) {
        // Update the preview width and height
        PREVIEW_WIDTH = newWidth
        PREVIEW_HEIGHT = newHeight

        // Setup the camera again with the new dimensions
        setupCamera()
    }

    fun setupCamera() {
        // Release the old ImageReader if it exists
        imageReader?.close()

        // Create a new ImageReader with updated dimensions
        imageReader = ImageReader.newInstance(PREVIEW_WIDTH, PREVIEW_HEIGHT, ImageFormat.YUV_420_888, 3)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                if (!::imageBitmap.isInitialized || imageBitmap.width != PREVIEW_WIDTH || imageBitmap.height != PREVIEW_HEIGHT) {
                    imageBitmap = Bitmap.createBitmap(
                        PREVIEW_WIDTH,
                        PREVIEW_HEIGHT,
                        Bitmap.Config.ARGB_8888
                    )
                }
                yuvConverter.yuvToRgb(image, imageBitmap)

                // Rotate image here for better pose estimation
                if (imageRotated != 0.0) {
                    val rotateMatrix = Matrix()
                    rotateMatrix.postRotate(imageRotated.toFloat())
                    processImage(Bitmap.createBitmap(
                        imageBitmap, 0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT,
                        rotateMatrix, false
                    ))
                } else {
                    processImage(imageBitmap)
                }

                image.close()
            }
        }, imageReaderHandler)

        // Initialize the camera session with the new surface
        imageReader?.surface?.let { surface ->
            session?.close()
            camera?.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(captureSession: CameraCaptureSession) {
                    session = captureSession
                    val cameraRequest = camera?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
                        addTarget(surface)
                    }
                    cameraRequest?.build()?.let {
                        session?.setRepeatingRequest(it, null, null)
                    }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }, null)
        }
    }

    private fun getIsFrontFacing(manager: CameraManager, cameraId: String): Boolean {
        return manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
    }

    private fun tryGetFrontCameraId(manager: CameraManager): String {
        for (cameraId in manager.cameraIdList) {
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                return cameraId
            }
        }
        return manager.cameraIdList.first()
    }

    private fun isAndroidTV(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    private fun printCameraYUV420888Sizes(context: Context, cameraId: String) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        map?.getOutputSizes(ImageFormat.YUV_420_888)?.let { sizes ->
            for (size in sizes) {
                println("Width: ${size.width}, Height: ${size.height}")
            }
        }
    }

    private fun printSupportedImageFormats(cameraManager: CameraManager, cameraId: String) {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val streamConfigMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        if (streamConfigMap != null) {
            val supportedFormats = streamConfigMap.outputFormats
            println("Camera ID: $cameraId supports the following formats:")
            supportedFormats.forEach { format ->
                val formatName = when (format) {
                    ImageFormat.JPEG -> "JPEG"
                    ImageFormat.YUV_420_888 -> "YUV_420_888"
                    ImageFormat.RGB_565 -> "RGB_565"
                    ImageFormat.FLEX_RGBA_8888 -> "FLEX_RGBA_8888"
                    ImageFormat.FLEX_RGB_888 -> "FLEX_RGB_888"
                    else -> "Unknown format: $format"
                }
                println(formatName)
            }
        } else {
            println("No stream configuration map available for camera ID: $cameraId")
        }
    }

    fun getDeviceRotation(context: Context): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API level 30 and above
            val display = context.display
            display?.rotation ?: 0
        } else {
            // Older API levels
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun openCamera(manager: CameraManager, cameraId: String): CameraDevice =
        suspendCancellableCoroutine { cont ->
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) = cont.resume(camera)

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    if (cont.isActive) cont.resumeWithException(Exception("Camera error"))
                }
            }, imageReaderHandler)
        }

    fun prepareCamera() {
        this.cameraId = tryGetFrontCameraId(cameraManager)
    }

    fun setDetector(detector: PoseDetector) {
        synchronized(lock) {
            if (this.detector != null) {
                this.detector?.close()
                this.detector = null
            }
            this.detector = detector
        }
    }

    fun setClassifier(classifier: PoseClassifier?) {
        synchronized(lock) {
            if (this.classifier != null) {
                this.classifier?.close()
                this.classifier = null
            }
            this.classifier = classifier
        }
    }

    /**
     * Set Tracker for Movenet MuiltiPose model.
     */
    fun setTracker(trackerType: TrackerType) {
        isTrackerEnabled = trackerType != TrackerType.OFF
        (this.detector as? MoveNetMultiPose)?.setTracker(trackerType)
    }

    fun resume() {
        imageReaderThread = HandlerThread("imageReaderThread").apply { start() }
        imageReaderHandler = Handler(imageReaderThread!!.looper)
        fpsTimer = Timer()
        fpsTimer?.scheduleAtFixedRate(
            object : TimerTask() {
                override fun run() {
                    framesPerSecond = frameProcessedInOneSecondInterval
                    frameProcessedInOneSecondInterval = 0
                }
            },
            0,
            1000
        )
    }

    fun close() {
        session?.close()
        session = null
        camera?.close()
        camera = null
        imageReader?.close()
        imageReader = null
        stopImageReaderThread()
        detector?.close()
        detector = null
        classifier?.close()
        classifier = null
        fpsTimer?.cancel()
        fpsTimer = null
        frameProcessedInOneSecondInterval = 0
        framesPerSecond = 0
    }

    // process image
    private fun processImage(bitmap: Bitmap) {
        val persons = mutableListOf<Person>()
        synchronized(lock) {
            detector?.estimatePoses(bitmap)?.let {
                persons.addAll(it)
            }
        }
        frameProcessedInOneSecondInterval++
        if (frameProcessedInOneSecondInterval == 1) {
            listener?.onFPSListener(framesPerSecond)
        }

        if (persons.isNotEmpty()) {
            listener?.onDetectedInfo(persons[0].score)
        }
        visualize(persons)
    }

    private val HEAD_ROTATION_STABLIZER = 5;

    private val horizontalRotationValues = ArrayDeque<Float>(HEAD_ROTATION_STABLIZER)
    private val verticalRotationValues = ArrayDeque<Float>(HEAD_ROTATION_STABLIZER)

    private fun getSmoothHeadRotation(person: Person): Pair<Float, Float> {
        val (horizontalRotation, verticalRotation) = VisualizationUtils.calculateHeadRotation(person)

        if (horizontalRotationValues.size == HEAD_ROTATION_STABLIZER) {
            horizontalRotationValues.removeFirst()
        }
        if (verticalRotationValues.size == HEAD_ROTATION_STABLIZER) {
            verticalRotationValues.removeFirst()
        }

        horizontalRotationValues.addLast(horizontalRotation)
        verticalRotationValues.addLast(verticalRotation)

        val smoothHorizontalRotation = horizontalRotationValues.average().toFloat()
        val smoothVerticalRotation = verticalRotationValues.average().toFloat()

        return Pair(
            round(smoothHorizontalRotation * 6) / 6,
            round(smoothVerticalRotation * 3) / 3
        )
    }

    private fun visualize(rawPersons: List<Person>) {
        val persons = rawPersons
            .filter { it.score > MIN_CONFIDENCE }
            .map{ VisualizationUtils.remapPerson(it,
                Pair(PREVIEW_WIDTH.toFloat(), PREVIEW_HEIGHT.toFloat()),
                Pair(SCREEN_WIDTH.toFloat(), SCREEN_HEIGHT.toFloat()),
                imageFlipped,
                imageRotated
            ) }

        val holder = surfaceView.holder
        val surfaceCanvas = holder.lockCanvas()

        surfaceCanvas?.let { canvas ->
            canvas.drawColor(Color.BLACK) // reset canvas
            updateGridButton(canvas, persons)
            drawBodyPointsAndInfo(canvas, persons)
            surfaceView.holder.unlockCanvasAndPost(canvas)
        }
    }

    private val pianoKeys: PianoKeys = PianoKeys(context)
    private var lastPersons = mutableListOf<Person>()

    private fun updateGridButton(canvas: Canvas, persons: List<Person>) {
        pianoKeys.update(canvas)

        if (persons.isEmpty()) {
            pianoKeys.draw()
            return
        }

        if (lastPersons.isNotEmpty()) {
            val lastPerson = lastPersons[0]
            val person = persons[0]
            val oldLeftWrist = lastPerson.keyPoints.firstOrNull { it.bodyPart == BodyPart.LEFT_WRIST }?.coordinate
            val newLeftWrist = person.keyPoints.firstOrNull { it.bodyPart == BodyPart.LEFT_WRIST }?.coordinate

            val oldRightWrist = lastPerson.keyPoints.firstOrNull { it.bodyPart == BodyPart.RIGHT_WRIST }?.coordinate
            val newRightWrist = person.keyPoints.firstOrNull { it.bodyPart == BodyPart.RIGHT_WRIST }?.coordinate

            listener?.onDebug(
                SCREEN_WIDTH,
                SCREEN_HEIGHT,
                PREVIEW_WIDTH,
                PREVIEW_HEIGHT,
                rotation,
                " - XX - ",
                imageRotated,
                imageFlipped,
            )

            pianoKeys.stroke(oldLeftWrist!!, newLeftWrist!!)
            pianoKeys.stroke(oldRightWrist!!, newRightWrist!!)

            pianoKeys.draw()
        }

        lastPersons = persons.toMutableList()
    }

    private val leftWristPoints = LinkedList<PointF>()

    private fun addWristPoint(keyPerson: Person) {
        val leftWristPoint =
            keyPerson.keyPoints.find { it.bodyPart == BodyPart.LEFT_WRIST }?.coordinate
        leftWristPoint?.let {
            if (leftWristPoints.size >= 5) {
                leftWristPoints.removeFirst()
            }
            leftWristPoints.add(it)
        }
    }

    private val lastStokes: MutableList<String> = mutableListOf()

    private fun analysisStroke(keyPerson: Person) {
        val strokeConfidence = 0.7
        var horizontalStoke: String = ""
        var isHorizontalStroke: Float = 0f
        var verticalStoke: String = ""
        var isVerticalStroke: Float = 0f
        var diagonalStokeDownLeft: String = ""
        var isDiagonalStrokeDownLeft: Float = 0f
        var diagonalStokeUpLeft: String = ""
        var isDiagonalStrokeUpLeft: Float = 0f

        addWristPoint(keyPerson)

        val stokeThreshold = VisualizationUtils.getStrokeThreshold(keyPerson.keyPoints)
        if (leftWristPoints.size > 2) {
            isHorizontalStroke =
                VisualizationUtils.isHorizontalStrokeInList(leftWristPoints, stokeThreshold)
            if (abs(isHorizontalStroke) > strokeConfidence) {
                horizontalStoke = if (isHorizontalStroke > 0) "➡\uFE0F" else "⬅\uFE0F"
                lastStokes.add(horizontalStoke)
                VisualizationUtils.keepLastPoint(leftWristPoints)
                return
            }
            isVerticalStroke =
                VisualizationUtils.isVerticalStrokeInList(leftWristPoints, stokeThreshold)
            if (abs(isVerticalStroke) > strokeConfidence) {
                verticalStoke = if (isVerticalStroke > 0) "⬇\uFE0F" else "⬆\uFE0F"
                lastStokes.add(verticalStoke)
                VisualizationUtils.keepLastPoint(leftWristPoints)
                return
            }
            isDiagonalStrokeDownLeft = VisualizationUtils.isDiagonalStrokeInList(leftWristPoints, stokeThreshold, -45.0)
            if (abs(isDiagonalStrokeDownLeft) > strokeConfidence) {
                diagonalStokeDownLeft = if (isDiagonalStrokeDownLeft > 0) "↘\uFE0F" else "↖\uFE0F"
                lastStokes.add(diagonalStokeDownLeft)
                return
            }
            isDiagonalStrokeUpLeft = VisualizationUtils.isDiagonalStrokeInList(leftWristPoints, stokeThreshold, 45.0)
            if (abs(isDiagonalStrokeUpLeft) > strokeConfidence) {
                diagonalStokeUpLeft = if (isDiagonalStrokeUpLeft > 0) "↗\uFE0F" else "↙\uFE0F"
                lastStokes.add(diagonalStokeUpLeft)
                return
            }
        }
    }

    private fun drawBodyPointsAndInfo(
        canvas: Canvas,
        keyPersons: List<Person>
    ) {
        if (keyPersons.isEmpty()) return

        val keyPerson = keyPersons[0]

        analysisStroke(keyPerson)

        VisualizationUtils.drawBodyKeypoints(
            canvas,
            keyPersons,
            isTrackerEnabled
        )

        keepWristTrace(keyPerson)
        VisualizationUtils.drawHandTrace(
            canvas,
            leftWristTrace,
            rightWristTrace)

        return

        val headRotation = getSmoothHeadRotation(keyPerson)

        VisualizationUtils.drawHeadRotationLineOnBitmap(canvas, headRotation)
    }

    private val leftWristTrace = mutableListOf<PointF>()
    private val rightWristTrace = mutableListOf<PointF>()
    private val maxWristTraceSize = 5 // Maximum number of history points to keep

    private fun keepWristTrace(person: Person) {
        val leftWrist = person.keyPoints.firstOrNull { it.bodyPart == BodyPart.LEFT_WRIST }?.coordinate
        val rightWrist = person.keyPoints.firstOrNull { it.bodyPart == BodyPart.RIGHT_WRIST }?.coordinate
        leftWrist?.let {
            leftWristTrace.add(it)
            if (leftWristTrace.size > maxWristTraceSize) {
                leftWristTrace.removeAt(0)
            }
        }
        rightWrist?.let {
            rightWristTrace.add(it)
            if (rightWristTrace.size > maxWristTraceSize) {
                rightWristTrace.removeAt(0)
            }
        }
    }

    private val musicNotes = listOf(
        1, 1, 5, 5, 6, 6, 5, 0, 4, 4, 3, 3, 2, 2, 1, 0,
        5, 5, 4, 4, 3, 3, 2, 0, 5, 5, 4, 4, 3, 3, 2, 0,
        1, 1, 5, 5, 6, 6, 5, 0, 4, 4, 3, 3, 2, 2, 1, 0
    )

    private fun stopImageReaderThread() {
        imageReaderThread?.quitSafely()
        try {
            imageReaderThread?.join()
            imageReaderThread = null
            imageReaderHandler = null
        } catch (e: InterruptedException) {
            Log.d(TAG, e.message.toString())
        }
    }

    interface CameraSourceListener {
        fun onFPSListener(fps: Int)

        fun onDetectedInfo(personScore: Float?)

        fun onDebug(vararg info: Any)
    }
}
