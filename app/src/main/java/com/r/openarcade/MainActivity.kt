package com.r.openarcade

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.r.openarcade.camera.CameraSource
import com.r.openarcade.data.Device
import com.r.openarcade.ml.*

import android.util.Log
import androidx.activity.enableEdgeToEdge

class MainActivity : AppCompatActivity() {
    companion object {
        private const val FRAGMENT_DIALOG = "dialog"
    }

    /** A [SurfaceView] for camera preview.   */
    private lateinit var surfaceView: SurfaceView

    /** Default pose estimation model is 1 (MoveNet Thunder)
     * 0 == MoveNet Lightning model
     * 1 == MoveNet Thunder model
     * 2 == MoveNet MultiPose model
     * 3 == PoseNet model
     **/
    private var modelPos = 0
    private var FPS_OPEN = true
    private var DETECT_SCORE_OPEN = false

    /** Default device is CPU */
    private var device = Device.NNAPI

    private lateinit var tvDebug: TextView
    private lateinit var tvScore: TextView
    private lateinit var tvFPS: TextView
    private lateinit var spnDevice: Spinner
    private lateinit var spnModel: Spinner
    private lateinit var spnTracker: Spinner
    private lateinit var vTrackerOption: View
    private lateinit var tvClassificationValue1: TextView
    private lateinit var tvClassificationValue2: TextView
    private lateinit var tvClassificationValue3: TextView
    private lateinit var swClassification: SwitchCompat
    private lateinit var vClassificationOption: View
    private var cameraSource: CameraSource? = null
    private var isClassifyPose = false
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your
                // app.
                openCamera()
            } else {
                // Explain to the user that the feature is unavailable because the
                // features requires a permission that the user has denied. At the
                // same time, respect the user's decision. Don't link to system
                // settings in an effort to convince the user to change their
                // decision.
                ErrorDialog.newInstance(getString(R.string.tfe_pe_request_permission))
                    .show(supportFragmentManager, FRAGMENT_DIALOG)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        setFullScreen()

        // keep screen on while app is running
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        tvDebug = findViewById(R.id.tvDebug)
        tvScore = findViewById(R.id.tvScore)
        tvFPS = findViewById(R.id.tvFps)
        spnModel = findViewById(R.id.spnModel)
        spnDevice = findViewById(R.id.spnDevice)
        spnTracker = findViewById(R.id.spnTracker)
        vTrackerOption = findViewById(R.id.vTrackerOption)
        surfaceView = findViewById(R.id.surfaceView)
        tvClassificationValue1 = findViewById(R.id.tvClassificationValue1)
        tvClassificationValue2 = findViewById(R.id.tvClassificationValue2)
        tvClassificationValue3 = findViewById(R.id.tvClassificationValue3)
        swClassification = findViewById(R.id.swPoseClassification)
        vClassificationOption = findViewById(R.id.vClassificationOption)

        if (!isCameraPermissionGranted()) {
            requestPermission()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setFullScreen()
        }
    }

    override fun onStart() {
        super.onStart()

        Log.d("MainActivity", "onStart")
        openCamera()
    }

    override fun onResume() {
        cameraSource?.resume()
        super.onResume()
    }

    override fun onPause() {
        cameraSource?.close()
        cameraSource = null
        super.onPause()
    }

    // check if permission is granted or not.
    private fun isCameraPermissionGranted(): Boolean {
        return checkPermission(
            Manifest.permission.CAMERA,
            Process.myPid(),
            Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openCamera() {
        if (!isCameraPermissionGranted()) return
    
        if (cameraSource == null) {
            initializeCameraSource()
            startCameraSource()
        }

        MoveNet.create(this, Device.NNAPI, ModelType.LightningInt8).let { detector ->
            cameraSource?.setDetector(detector)
        }
    }

    private fun initializeCameraSource() {
        cameraSource = CameraSource(surfaceView, object : CameraSource.CameraSourceListener {
            override fun onFPSListener(fps: Int) {
                if (!FPS_OPEN) return

                Handler(Looper.getMainLooper()).post {
                    tvFPS.text = getString(R.string.tfe_pe_tv_fps, fps)
                }
            }
    
            override fun onDetectedInfo(
                personScore: Float?
            ) {
                if (!DETECT_SCORE_OPEN) return

                Handler(Looper.getMainLooper()).post {
                    tvScore.text = getString(R.string.tfe_pe_tv_score, personScore ?: 0f)
                }
            }

            @SuppressLint("DefaultLocale")
            override fun onDebug(vararg info: Any) {
                Handler(Looper.getMainLooper()).post {
                    tvDebug.text = info.joinToString(separator = " ") {
                        when (it) {
                            is Float -> String.format("%.2f", it)
                            is Pair<*, *> -> {
                                if (it.first is Float && it.second is Float) {
                                    String.format("(%.2f, %.2f)", it.first as Float, it.second as Float)
                                } else {
                                    it.toString()
                                }
                            }
                            else -> it.toString()
                        }
                    }
                }
            }
        }).apply {
            prepareCamera()
        }
    }

    private fun startCameraSource() {
        lifecycleScope.launch(Dispatchers.Main) {
            cameraSource?.initCamera()
        }
    }

    private fun convertPoseLabels(pair: Pair<String, Float>?): String {
        if (pair == null) return "empty"
        return "${pair.first} (${String.format("%.2f", pair.second)})"
    }

    private fun requestPermission() {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) -> {
                // You can use the API that requires the permission.
                openCamera()
            }
            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                requestPermissionLauncher.launch(
                    Manifest.permission.CAMERA
                )
            }
        }
    }

    /**
     * Shows an error message dialog.
     */
    class ErrorDialog : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
            AlertDialog.Builder(activity)
                .setMessage(requireArguments().getString(ARG_MESSAGE))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    // do nothing
                }
                .create()

        companion object {

            @JvmStatic
            private val ARG_MESSAGE = "message"

            @JvmStatic
            fun newInstance(message: String): ErrorDialog = ErrorDialog().apply {
                arguments = Bundle().apply { putString(ARG_MESSAGE, message) }
            }
        }
    }
}
