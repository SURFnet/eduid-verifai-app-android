package nl.eduid.verifai

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.verifai.liveness.LivenessCheckStatus
import com.verifai.liveness.VerifaiLiveness
import com.verifai.liveness.VerifaiLivenessCheckListener
import com.verifai.liveness.checks.FaceMatching
import com.verifai.liveness.checks.Tilt
import com.verifai.liveness.result.VerifaiFaceMatchingCheckResult
import com.verifai.liveness.result.VerifaiLivenessCheckResults
import com.verifai.nfc.VerifaiNfc
import com.verifai.nfc.VerifaiNfcResultListener
import com.verifai.nfc.result.VerifaiNfcResult
import nl.eduid.verifai.databinding.ActivityVerifaiResultBinding


class VerifaiResultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVerifaiResultBinding
    private lateinit var server: Server
    private lateinit var msg: Message

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        server = intent.getParcelableExtra<Server>("SERVER")!!

        msg = Message()

        binding = ActivityVerifaiResultBinding.inflate(layoutInflater)

        val view = binding.root
        setContentView(view)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val livenessCheckListener = object : VerifaiLivenessCheckListener {
            override fun onResult(results: VerifaiLivenessCheckResults) {
                Log.d(TAG, "Done")
                var alive: Byte = 1
                for (result in results.resultList) {
                    if (result.status != LivenessCheckStatus.SUCCESS) alive = 0
                    Log.d(TAG, "%s finished".format(result.check.instruction))
                    Log.d(TAG, "%s status".format(result.status))
                    if (result is VerifaiFaceMatchingCheckResult) {
                        Log.d(TAG, "Face match?: ${result.match}")
                        result.confidence?.let {
                            Log.d(TAG, "Face match confidence ${(it * 100).toInt()}%")
                            msg.cfd = result.confidence
                        }
                    }
                }
                msg.alive = alive
                msg.state = "finished"
                msg.result = "SUCCESS"
                server.sendMessage(msg)

            }

            override fun onError(e: Throwable) {
                e.printStackTrace()

                msg.state = "liveness fail"
                server.sendMessage(msg)
            }
        }

        val nfcListener = object : VerifaiNfcResultListener {
            override fun onResult(result: VerifaiNfcResult) {
                Log.d(TAG, "NFC completed:\n" + result.mrzData.toString())

                msg.nfc = 1

                if (result.mrzData != null && // This is weird, NFC may check valid but not contain mrzData!?
                    result.originality() &&
                    result.authenticity() &&
                    result.confidentiality()) {
                    Log.d(TAG, "NFC VALID")
                    msg.valid=1
                    msg.number = result.mrzData?.documentNumber
                    msg.gn = result.mrzData?.firstName
                    msg.sn = result.mrzData?.lastName
                    msg.dob = result.mrzData?.dateOfBirth.toString()
                    msg.doctype = result.mrzData?.documentType
                    msg.country = result.mrzData?.countryCode
                } else {
                    Log.d(TAG, "NFC INVALID")
                }

                msg.state = "start_liveness_nfc"
                server.sendMessage(msg)

                if (VerifaiLiveness.isLivenessCheckSupported(this@VerifaiResultActivity)) {
                    Log.d(TAG, "Start Liveness NFC")
                    // Liveness check is supported
                    VerifaiLiveness.clear(this@VerifaiResultActivity)
                        VerifaiLiveness.start(
                            this@VerifaiResultActivity,
                            arrayListOf(
                                //CloseEyes(this),
                                Tilt(this@VerifaiResultActivity, -25),
                                //FaceMatching(this@VerifaiResultActivity, MainActivity.verifaiResult?.frontImage!!),
                                FaceMatching(this@VerifaiResultActivity, result.photo!!),
                            ), livenessCheckListener
                        )
                } else {
                    // Sorry, the Liveness check is not supported by this device
                    Log.d(TAG, "Liveness not supported")
                }

            }

            override fun onCanceled() {
                Log.d(TAG, "NFC Cancel")
                // TODO deduplicate this from no-NFC case!!
                MainActivity.verifaiResult?.let {
                    Log.d(TAG, "Start Liveness VIZ")
                    msg.state = "start_liveness_viz"
                    server.sendMessage(msg)

                    if (VerifaiLiveness.isLivenessCheckSupported(this@VerifaiResultActivity)) {
                        // Liveness check is supported
                        VerifaiLiveness.clear(this@VerifaiResultActivity)
                        VerifaiLiveness.start(
                            this@VerifaiResultActivity,
                            arrayListOf(
                                //CloseEyes(this),
                                Tilt(this@VerifaiResultActivity, -25),
                                FaceMatching(this@VerifaiResultActivity, it.frontImage!!),
                            ), livenessCheckListener
                        )
                    } else {
                        // Sorry, the Liveness check is not supported by this device
                        Log.d(TAG, "Liveness not supported")
                    }
                }
            }

            override fun onError(e: Throwable) {
                e.printStackTrace()
                Log.d(TAG, "NFC Error")
            }
        }

        MainActivity.verifaiResult?.let {
            Log.d(TAG, "verifaiResult mrzData:" + it.mrzData.toString())
            msg.number = it.mrzData?.documentNumber
            msg.gn = it.mrzData?.firstName
            msg.sn = it.mrzData?.lastName
            msg.dob = it.mrzData?.dateOfBirth.toString()
            msg.doctype = it.mrzData?.documentType
            msg.country = it.mrzData?.countryCode
            msg.nfc = 0
            msg.valid = 0

            val nfcAdapter = NfcAdapter.getDefaultAdapter(this)

            if (nfcAdapter?.isEnabled == true &&
                VerifaiNfc.isCapable(this) &&
                it.document?.nfcType != null &&
                it.mrzData?.isNfcKeyValid == true) {
                Log.d(TAG, "Document is NFC capable")
                Log.d(TAG, "Device is NFC capable, it's enabled and the document supports NFC")

                msg.state = "start_nfc"
                server.sendMessage(msg)

                VerifaiNfc.start(this, it, true, nfcListener, true)
            } else {
                Log.d(TAG, "Start Liveness VIZ")
                msg.state = "start_liveness_viz"
                server.sendMessage(msg)

                if (VerifaiLiveness.isLivenessCheckSupported(this)) {
                    // Liveness check is supported
                    VerifaiLiveness.clear(this)
                    VerifaiLiveness.start(this,
                        arrayListOf(
                            //CloseEyes(this),
                            Tilt(this, -25),
                            FaceMatching(this, it.frontImage!!),
                        ), livenessCheckListener
                    )
                } else {
                    // Sorry, the Liveness check is not supported by this device
                    Log.d(TAG, "Liveness not supported")
                }

            }

        }

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
        private const val TAG = "Verifai result"
    }

}
