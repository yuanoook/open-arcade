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
//        Width: 1920, Height: 1080 // good for TV
//        Width: 1600, Height: 1200
//        Width: 1600, Height: 720
//        Width: 1440, Height: 1080
//        Width: 1280, Height: 960
//        Width: 1280, Height: 720 // good for TV
//        Width: 960, Height: 720
//        Width: 854, Height: 480
//        Width: 800, Height: 600
//        Width: 720, Height: 480
//        Width: 640, Height: 480
//        Width: 640, Height: 360 // good for TV

    private val PREVIEW_WIDTH: Int = 1280
    private val PREVIEW_HEIGHT: Int = 720

    private val context: Context = surfaceView.context
    private val mediaPlayerPool = MediaPlayerPool(context, 10)

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
    private val triggeredKeys = mutableSetOf<Int>()

    suspend fun initCamera() {
        camera = openCamera(cameraManager, cameraId)
        imageReader =
            ImageReader.newInstance(PREVIEW_WIDTH, PREVIEW_HEIGHT, ImageFormat.YUV_420_888, 3)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                if (!::imageBitmap.isInitialized) {
                    imageBitmap =
                        Bitmap.createBitmap(
                            PREVIEW_WIDTH,
                            PREVIEW_HEIGHT,
                            Bitmap.Config.ARGB_8888
                        )
                }
                yuvConverter.yuvToRgb(image, imageBitmap)

                val context: Context = surfaceView.context
                val isTV = isAndroidTV(context)
                val rotateMatrix = Matrix()
                val isFrontFacing = getIsFrontFacing(cameraManager, cameraId)

                if (isTV || isFrontFacing) {
                    rotateMatrix.postScale(-1.0f, 1.0f)
                }

                if (!isTV) {
                    rotateMatrix.postRotate(90.0f)
                }

                val rotatedBitmap = Bitmap.createBitmap(
                    imageBitmap, 0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT,
                    rotateMatrix, false
                )

                processImage(rotatedBitmap)
                image.close()
            }


//            getCameraYUV420888Sizes(surfaceView.context, cameraId)
        }, imageReaderHandler)

        imageReader?.surface?.let { surface ->
            session = createSession(listOf(surface))
            val cameraRequest = camera?.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW
            )?.apply {
                addTarget(surface)
            }
            cameraRequest?.build()?.let {
                session?.setRepeatingRequest(it, null, null)
            }
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

    private fun getCameraYUV420888Sizes(context: Context, cameraId: String) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        map?.getOutputSizes(ImageFormat.YUV_420_888)?.let { sizes ->
            for (size in sizes) {
                println("Width: ${size.width}, Height: ${size.height}")
            }
        }
    }

    private suspend fun createSession(targets: List<Surface>): CameraCaptureSession =
        suspendCancellableCoroutine { cont ->
            camera?.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(captureSession: CameraCaptureSession) =
                    cont.resume(captureSession)

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    cont.resumeWithException(Exception("Session error"))
                }
            }, null)
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
        visualize(persons, bitmap)
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

    private fun visualize(persons: List<Person>, bitmap: Bitmap) {
        val keyPersons = persons.filter { it.score > MIN_CONFIDENCE }
        val outputBitmap: Bitmap = drawBodyPointsAndInfo(keyPersons, bitmap)

        val holder = surfaceView.holder
        val surfaceCanvas = holder.lockCanvas()

        surfaceCanvas?.let { canvas ->
            val canvasWidth = canvas.width
            val canvasHeight = canvas.height

            val bitmapWidth = outputBitmap.width
            val bitmapHeight = outputBitmap.height

            val canvasAspectRatio = canvasWidth.toFloat() / canvasHeight.toFloat()
            val bitmapAspectRatio = bitmapWidth.toFloat() / bitmapHeight.toFloat()

            val destRect: Rect

            if (bitmapAspectRatio > canvasAspectRatio) {
                // Bitmap is wider than canvas, scale by height
                val scaledWidth = (canvasHeight * bitmapAspectRatio).toInt()
                val left = (canvasWidth - scaledWidth) / 2
                destRect = Rect(left, 0, left + scaledWidth, canvasHeight)
            } else {
                // Bitmap is taller than canvas, scale by width
                val scaledHeight = (canvasWidth / bitmapAspectRatio).toInt()
                val top = (canvasHeight - scaledHeight) / 2
                destRect = Rect(0, top, canvasWidth, top + scaledHeight)
            }

            canvas.drawBitmap(
                outputBitmap,
                Rect(0, 0, bitmapWidth, bitmapHeight),
                destRect,
                null
            )

            surfaceView.holder.unlockCanvasAndPost(canvas)
        }
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
        keyPersons: List<Person>,
        bitmap: Bitmap
    ): Bitmap {
        if (keyPersons.isEmpty()) return bitmap

        val keyPerson = keyPersons[0]

        analysisStroke(keyPerson)
        var outputBitmap = VisualizationUtils.drawBodyKeypoints(
            bitmap,
            keyPersons,
            isTrackerEnabled
        )

        playPiano(keyPerson)

        keepWristTrace(keyPerson)
        outputBitmap = VisualizationUtils.drawPianoKeys(outputBitmap, triggeredKeys)

        VisualizationUtils.drawHandTrace(outputBitmap, leftWristTrace, rightWristTrace)

        val headRotation = getSmoothHeadRotation(keyPerson)

//        listener?.onDebug(
//            SCREEN_WIDTH,
//            SCREEN_HEIGHT,
//            PREVIEW_WIDTH,
//            PREVIEW_HEIGHT,
//            lastStokes,
//            headRotation
//        )

        return VisualizationUtils.drawHeadRotationLineOnBitmap(headRotation, outputBitmap)
    }

    private val leftWristTrace = mutableListOf<PointF>()
    private val rightWristTrace = mutableListOf<PointF>()
    private val maxWristTraceSize = 20 // Maximum number of history points to keep

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


    private val previousLeftWristY = mutableListOf<Float>()
    private val previousRightWristY = mutableListOf<Float>()
    private val maxHistorySize = 4

    private fun playPiano(person: Person) {
        val halfScreenHeight = PREVIEW_HEIGHT / 2
        val minDistance = PREVIEW_HEIGHT / 6

        val leftWrist = person.keyPoints.firstOrNull { it.bodyPart == BodyPart.LEFT_WRIST }?.coordinate
        val rightWrist = person.keyPoints.firstOrNull { it.bodyPart == BodyPart.RIGHT_WRIST }?.coordinate

        listener?.onDebug(
//            SCREEN_WIDTH,
//            SCREEN_HEIGHT,
//            PREVIEW_WIDTH,
//            PREVIEW_HEIGHT,
//            lastStokes,
            halfScreenHeight,
            minDistance,
            leftWrist?.y!!,
            rightWrist?.y!!
        )

        leftWrist?.let {
            val currY = it.y
            if (currY > halfScreenHeight) {
                for (prevY in previousLeftWristY) {
                    if (
                            isValidKeyPosition(it.x) &&
                            (prevY <= halfScreenHeight &&
                            (currY - prevY) > minDistance)
                        ) {
                        playKey(it.x)
                        previousLeftWristY.clear()
                        break
                    }
                }
            }
            previousLeftWristY.add(currY)
            if (previousLeftWristY.size > maxHistorySize) {
                previousLeftWristY.removeAt(0)
            }
        }

        rightWrist?.let {
            val currY = it.y
            if (currY > halfScreenHeight) {
                for (prevY in previousRightWristY) {
                    if (
                            isValidKeyPosition(it.x) &&
                            (prevY <= halfScreenHeight &&
                            (currY - prevY) > minDistance)
                        ) {
                        playKey(it.x)
                        previousRightWristY.clear()
                        break
                    }
                }
            }
            previousRightWristY.add(currY)
            if (previousRightWristY.size > maxHistorySize) {
                previousRightWristY.removeAt(0)
            }
        }
    }

    private fun isValidKeyPosition(x: Float): Boolean {
        val keyWidth = PREVIEW_WIDTH / 7
        val positionInKey = x % keyWidth
        return positionInKey >= 20 && positionInKey <= (keyWidth - 10)
    }

    private fun playKey(x: Float) {
        val screenWidth = PREVIEW_WIDTH
        val keyWidth = screenWidth / 7

        val keyIndex = (x / keyWidth).toInt()

        val soundFile = when (keyIndex) {
            0 -> R.raw.c4
            1 -> R.raw.d4
            2 -> R.raw.e4
            3 -> R.raw.f4
            4 -> R.raw.g4
            5 -> R.raw.a5
            6 -> R.raw.b5
            else -> null
        }

        soundFile?.let {
            mediaPlayerPool.playSound(it)
            triggeredKeys.add(keyIndex)
            handler.postDelayed({
                triggeredKeys.remove(keyIndex)
            }, 500)
        }
    }

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
