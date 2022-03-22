package nl.eduid.verifai

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.verifai.core.Verifai
import nl.eduid.verifai.databinding.ActivityVerifaiResultBinding
import com.verifai.liveness.VerifaiLiveness
import com.verifai.liveness.VerifaiLivenessCheckListener
import com.verifai.liveness.checks.CloseEyes
import com.verifai.liveness.checks.FaceMatching
import com.verifai.liveness.checks.Tilt
import com.verifai.liveness.result.VerifaiFaceMatchingCheckResult
import com.verifai.liveness.result.VerifaiLivenessCheckResults
import com.verifai.nfc.VerifaiNfc
import com.verifai.nfc.VerifaiNfcResultListener
import com.verifai.nfc.result.VerifaiNfcResult


class VerifaiResultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVerifaiResultBinding
    private lateinit var server: Server

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        server = intent.getParcelableExtra<Server>("SERVER")!!
        server.sendMessage(
            Message(
                "martin",
                "scanned",
                "FAILED"))

        binding = ActivityVerifaiResultBinding.inflate(layoutInflater)

        val view = binding.root
        setContentView(view)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        /**
         * Start the NFC process based on the scan result.
         */
        binding.contentResult.startNfcButton.setOnClickListener {
            Log.d("Verifai", "Nfc Start")
            val nfcListener = object : VerifaiNfcResultListener {
                override fun onResult(result: VerifaiNfcResult) {
                    server.sendMessage(
                        Message(
                            "martin",
                            "nfc ok",
                            "FAILED"
                        )
                    )
                    Log.d("Verifai", "NFC completed" + result.mrzData.toString())
                }

                override fun onCanceled() {
                    Log.d("Verifai", "NFC Cancel")
                }

                override fun onError(e: Throwable) {
                    e.printStackTrace()
                    Log.d("Verifai", "NFC Error")
                }
            }

            MainActivity.verifaiResult?.let {
                VerifaiNfc.start(this, it, true, nfcListener, true)
            }
        }

        /**
         * Start the Liveness Check. A scan result is only needed for the face match. Without the
         * face match the liveness check can also run separately.
         */
        binding.contentResult.startLivenessButton.setOnClickListener {
            server.sendMessage(
                Message(
                    "martin",
                    "start liveness",
                    "FAILED"
                )
            )
            VerifaiLiveness.clear(this)
            VerifaiLiveness.start(this,
                arrayListOf(
                    CloseEyes(this),
                    //FaceMatching(this, MainActivity.verifaiResult?.frontImage!!),
                    //Tilt(this, -25)
                ), object : VerifaiLivenessCheckListener {
                    override fun onResult(results: VerifaiLivenessCheckResults) {
                        Log.d(TAG, "Done")
                        for (result in results.resultList) {
                            Log.d(TAG, "%s finished".format(result.check.instruction))
                            Log.d(TAG, "%s status".format(result.status))
                            if (result is VerifaiFaceMatchingCheckResult) {
                                Log.d(TAG, "Face match?: ${result.match}")
                                result.confidence?.let {
                                    Log.d(TAG, "Face match confidence ${(it * 100).toInt()}%")
                                }
                            }
                        }
                        server.sendMessage(
                            Message(
                                "martin",
                                "liveness ok",
                                "FAILED")
                        )
                    }

                    override fun onError(e: Throwable) {
                        e.printStackTrace()
                        server.sendMessage(
                            Message(
                                "martin",
                                "liveness bad",
                                "FAILED")
                        )
                    }
                }
            )
        }

        binding.contentResult.mrzValue.text = MainActivity.verifaiResult?.mrzData?.mrzString
        binding.contentResult.firstNameValue.text = MainActivity.verifaiResult?.mrzData?.firstName
        binding.contentResult.lastNameValue.text = MainActivity.verifaiResult?.mrzData?.lastName

        server.sendMessage(
            Message(
                MainActivity.verifaiResult?.mrzData?.firstName.toString(),
                "finished",
                "SUCCESS"
            )
        )
        Log.d("Verifai", "Scan completed firstname: " + MainActivity.verifaiResult?.mrzData?.firstName.toString())

        MainActivity.verifaiResult?.visualInspectionZoneResult.also { map ->
            binding.vizDetailsBtn.setOnClickListener {
                val intent = Intent(this, GeneralResultActivity::class.java)
                intent.putExtra("title", "VIZ result")
                val res: HashMap<String, String> = HashMap()
                map?.forEach {
                    res[it.key] = it.value
                }
                intent.putExtra("result", res)
                startActivity(intent)
            }
        } ?: run {
            binding.vizDetailsBtn.visibility = View.GONE
        }
    }

    companion object {
        private const val TAG = "RESULT_ACTIVITY"
    }
}
