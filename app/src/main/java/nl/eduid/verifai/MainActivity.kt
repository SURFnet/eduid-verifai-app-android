package nl.eduid.verifai

import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.View
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.jsonBody

import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
//import kotlinx.android.parcel.Parcelize
import kotlinx.parcelize.Parcelize

import com.verifai.core.Verifai
import com.verifai.core.VerifaiConfiguration
import com.verifai.core.exceptions.LicenceNotValidException
import com.verifai.core.listeners.VerifaiResultListener
import com.verifai.core.result.VerifaiResult

import nl.eduid.verifai.databinding.ActivityMainBinding
import java.util.Date


@Serializable
class Message(var state: String? = null) {
    var id: String? = null
    var uid: String? = null
    var cn: String? = null
    var gn: String? = null
    var sn: String? = null
    var dob: String? = null
    var svs: String? = null
}

@Parcelize
class Server(
    private val id: String,
    private val db: String
) : Parcelable {
    fun sendMessage(data: Message) {
        data.id = id
        val msg = Json.encodeToString(Message.serializer(), data)
        Fuel.post(db.toString())
            .jsonBody(msg)
            .response { request, response, result ->
                val (bytes, error) = result
                Log.d("Verifai server", "Request: $request")
                Log.d("Verifai server", "Response: $response")
                Log.d("Verifai server", "Result: ${bytes?.let { String(it) }}")
                Log.d("Verifai server", "Error: $error")
            }

    }
}

/**
 * The MainActivity of this SDK example
 *
 * Starts the Verifai Example App and handles everything. Do not forget to set a valid licence.
 * A valid licence can be obtained from https://dashboard.verifai.com/
 *
 * @see "https://verifai.dev"
 * @author Igor Pidik - Verifai.com
 * @author Jeroen Oomkes - Verifai.com
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var server: Server
    private lateinit var msg: Message

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        binding = ActivityMainBinding.inflate(layoutInflater)
        msg = Message()

        // Handle app links.
        Log.d(TAG, "intent: " + intent.toString())
        val appLinkIntent = intent
        if (appLinkIntent.action === Intent.ACTION_VIEW) {
            val appLinkData = appLinkIntent.data!!
            val cb = appLinkData.getQueryParameter("cb")
            val id = appLinkData.getQueryParameter("id")
            server = Server(id!!, cb!!)

            msg.state = "start_verifai"
            server.sendMessage(msg)

            startVerifai(binding.root)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun startQRCode(view: View) {
        val intent = Intent(this, QRCodeActivity::class.java)
        startActivity(intent)
    }

    /**
     * In this method Verifai will be initialized and started. The steps to start the Verifai SDK are the following:
     *
     * 1. Call Verifai.setLicence(licenceString) where the licenceString is the licence that has been obtained
     *      from https://dashboard.verifai.com
     * 2. Call Verifai.startScan(params) Verifai will startScanning if it has received a valid licence. It will throw
     *      an error when the licence is invalid. Please catch this error.
     */
    @Suppress("UNUSED_PARAMETER")
    fun startVerifai(view: View) {
        val licence = BuildConfig.verifaiLicence
        Verifai.setLicence(this@MainActivity, licence)
        Verifai.configure(getVerifaiConfiguration())
        Verifai.startScan(this@MainActivity, object : VerifaiResultListener {
            override fun onSuccess(result: VerifaiResult) {
                Log.d(TAG, "Success")
                Log.d(TAG, "result: " + result.toString())
                if (result.mrzData != null) {
                    verifaiResult = result
                    Log.d(TAG,"result.document != null" + result.mrzData)
                    val intent = Intent(this@MainActivity, VerifaiResultActivity::class.java)
                    intent.putExtra("SERVER", server)
                    startActivity(intent)
                } else {
                    Log.d(TAG,"result.mrzData == null")
                    msg.state = "mrzData failed"
                    server.sendMessage(msg)
                    val intent = Intent(this@MainActivity, MainActivity::class.java)
                    intent.action = Intent.ACTION_MAIN
                    startActivity(intent)
                }
            }
            override fun onCanceled() {
                Log.d(TAG, "Cancel")
                val msg = Message("canceled")
                msg.svs = "FAILED"
                server.sendMessage(msg)
            }
            override fun onError(e: Throwable) {
                // We are sorry, something wrong happened.
                Log.d(TAG, "Error")
                if (e is LicenceNotValidException) {
                    Log.d(TAG, "Authentication failed")
                }
                msg.state = "error"
                server.sendMessage(msg)
                val intent = Intent(this@MainActivity, MainActivity::class.java)
                intent.action = Intent.ACTION_DEFAULT
                startActivity(intent)
            }

        })
    }

    private fun getVerifaiConfiguration(): VerifaiConfiguration {
        return VerifaiConfiguration(
            show_instruction_screens = true,
            enableVisualInspection = true, //requires document_copy !!
            require_document_copy = true,
            require_mrz_contents = true,
            read_mrz_contents = true,
            require_nfc_when_available = false,
            enable_post_cropping = false,
        )
    }

    companion object {
        private const val TAG = "Verifai"
        var verifaiResult: VerifaiResult? = null
    }

}
