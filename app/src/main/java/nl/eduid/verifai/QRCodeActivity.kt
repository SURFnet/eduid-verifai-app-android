package nl.eduid.verifai

import android.Manifest
import android.util.Log
import android.content.pm.PackageManager
import android.graphics.RectF
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.ViewGroup
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.camera.core.Preview
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import nl.eduid.verifai.databinding.ActivityQrcodeBinding // viewBinding magic!

class QRCodeActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var barcodeBoxView: BarcodeBoxView
    private lateinit var binding: ActivityQrcodeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("QRCodeActivity","onCreate")
        super.onCreate(savedInstanceState)
        binding = ActivityQrcodeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        barcodeBoxView = BarcodeBoxView(this)
        binding.constraintLayout.addView(barcodeBoxView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT)
        )

        checkCameraPermission()
    }

    override fun onResume() {
        Log.d("QRCodeActivity","onResume")
        super.onResume()
        startCamera()
    }

    override fun onDestroy() {
        Log.d("QRCodeActivity","onDestroy")
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    /**
     * This function is executed once the user has granted or denied the missing permission
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        checkIfCameraPermissionIsGranted()
    }

    /**
     * This function is responsible to request the required CAMERA permission
     */
    private fun checkCameraPermission() {
        try {
            val requiredPermissions = arrayOf(Manifest.permission.CAMERA)
            ActivityCompat.requestPermissions(this, requiredPermissions, 0)
        } catch (e: IllegalArgumentException) {
            checkIfCameraPermissionIsGranted()
        }
    }

    /**
     * This function will check if the CAMERA permission has been granted.
     * If so, it will call the function responsible to initialize the camera preview.
     * Otherwise, it will raise an alert.
     */
    private fun checkIfCameraPermissionIsGranted() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            // Permission granted: start the preview
            //startCamera()
        } else {
            // Permission denied
            MaterialAlertDialogBuilder(this)
                .setTitle("Permission required")
                .setMessage("This application needs to access the camera to process barcodes")
                .setPositiveButton("Ok") { _, _ ->
                    // Keep asking for permission until granted
                    checkCameraPermission()
                }
                .setCancelable(false)
                .create()
                .apply {
                    setCanceledOnTouchOutside(false)
                    show()
                }
        }
    }

    /**
     * This function is responsible for the setup of the camera preview and the image analyzer.
     */
    private fun startCamera() {
        Log.d("QRCodeActivity","startCamera")

        val cameraProviderFuture = ProcessCameraProvider
            .getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture
                .get()

            // Preview
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .also {
                    it.setSurfaceProvider(binding.qrPreview.surfaceProvider)
                }

            // Image analyzer
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
            imageAnalyzer
                .setAnalyzer(
                    cameraExecutor,
                    QRCodeAnalyzer(
                        this,
                        cameraProvider,
                        barcodeBoxView,
                        binding.qrPreview.width.toFloat(),
                        binding.qrPreview.height.toFloat()
                    )
                )

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )

            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

}