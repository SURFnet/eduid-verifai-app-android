package nl.eduid.verifai

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log

import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.verifai.core.result.VerifaiResult

class QRCodeAnalyzer (
    private val context: Context,
    private val barcodeBoxView: BarcodeBoxView,
    private val previewViewWidth: Float,
    private val previewViewHeight: Float

): ImageAnalysis.Analyzer {
    private var scaleX = 1f
    private var scaleY = 1f

    private fun translateX(x: Float) = x * scaleX
    private fun translateY(y: Float) = y * scaleY

    private fun adjustBoundingRect(rect: Rect) = RectF(
        translateX(rect.left.toFloat()),
        translateY(rect.top.toFloat()),
        translateX(rect.right.toFloat()),
        translateY(rect.bottom.toFloat())
    )

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        val img = image.image!!

        // Update scale factors
        scaleX = previewViewWidth / img.height.toFloat()
        scaleY = previewViewHeight / img.width.toFloat()

        val inputImage = InputImage.fromMediaImage(
            img,
            image.imageInfo.rotationDegrees
        )

        // Process image searching for barcodes
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()

        val scanner = BarcodeScanning.getClient(options)

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    for (barcode in barcodes) {
                        // Update bounding rect
                        barcode.boundingBox?.let { rect ->
                            barcodeBoxView.setRect(
                                adjustBoundingRect(
                                    rect
                                )
                            )
                        }
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(barcode.rawValue))
                        context.startActivity(intent)
                    }
                } else {
                    // Remove bounding rect
                    barcodeBoxView.setRect(RectF())
                }
                image.close()
            }
            .addOnFailureListener { e ->
                image.close()
                Log.d(QRCodeAnalyzer.TAG, "Failure!! " + e)
            }
    }

    companion object {
        private const val TAG = "Analyzer"
    }

}