package nl.eduid.verifai

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.verifai.liveness.LivenessCheckStatus
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

        val nfcListener = object : VerifaiNfcResultListener {
            override fun onResult(result: VerifaiNfcResult) {
                Log.d(TAG, "NFC completed" + result.mrzData.toString())

                binding.contentResult.mrzValue.text = MainActivity.verifaiResult?.mrzData?.mrzString

                msg.uid = result.mrzData?.documentNumber
                msg.gn = result.mrzData?.firstName
                msg.sn = result.mrzData?.lastName
                msg.dob = result.mrzData?.dateOfBirth.toString()
                binding.contentResult.firstNameValue.text = msg.gn
                binding.contentResult.lastNameValue.text = msg.sn

//                msg.state = "finished" // So we can continue without liveness
                msg.state = "nfc_ok"

                //msg.svs = "SUCCESS" // This is too early, for debugging
                server.sendMessage(msg)
            }

            override fun onCanceled() {
                Log.d(TAG, "NFC Cancel")
            }

            override fun onError(e: Throwable) {
                e.printStackTrace()
                Log.d(TAG, "NFC Error")
            }
        }

        val livenessCheckListener = object : VerifaiLivenessCheckListener {
            override fun onResult(results: VerifaiLivenessCheckResults) {
                var success: Boolean = true
                Log.d(TAG, "Done")
                for (result in results.resultList) {
                    if (result.status != LivenessCheckStatus.SUCCESS) success = false
                    Log.d(TAG, "%s finished".format(result.check.instruction))
                    Log.d(TAG, "%s status".format(result.status))
                    if (result is VerifaiFaceMatchingCheckResult) {
                        Log.d(TAG, "Face match?: ${result.match}")
                        result.confidence?.let {
                            Log.d(TAG, "Face match confidence ${(it * 100).toInt()}%")
                        }
                    }
                }
                if (success) {
                    msg.state = "finished"
                    msg.svs = "SUCCESS"
                    server.sendMessage(msg)
                }

            }

            override fun onError(e: Throwable) {
                e.printStackTrace()

                msg.state = "liveness fail"
                server.sendMessage(msg)
            }
        }

        /**
         * Start the NFC process based on the scan result.
         */
        binding.contentResult.startNfcButton.setOnClickListener {
            Log.d("Verifai", "Nfc Start")
            msg.state = "start_nfc"
            server.sendMessage(msg)
            MainActivity.verifaiResult?.let {
                VerifaiNfc.start(this, it, true, nfcListener, true)
            }
        }

        /**
         * Start the Liveness Check. A scan result is only needed for the face match. Without the
         * face match the liveness check can also run separately.
         */
        binding.contentResult.startLivenessButton.setOnClickListener {
            msg.state = "start_liveness"
            server.sendMessage(msg)
            VerifaiLiveness.clear(this)
            VerifaiLiveness.start(this,
                arrayListOf(
                    CloseEyes(this),
                    //FaceMatching(this, MainActivity.verifaiResult?.frontImage!!),
                    //Tilt(this, -25)
                ), livenessCheckListener
            )
        }

        //binding.contentResult.mrzValue.text = MainActivity.verifaiResult?.mrzData?.mrzString
        //msg.dob = MainActivity.verifaiResult?.mrzData?.dateOfBirth.toString()
        //msg.gn = MainActivity.verifaiResult?.mrzData?.firstName.toString()
        //msg.sn = MainActivity.verifaiResult?.mrzData?.lastName.toString()
/*
        MainActivity.verifaiResult?.let {
            msg.state = "start_nfc"
            server.sendMessage(msg)

            VerifaiNfc.start(this, it, true, nfcListener, true)
        }
*/
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
