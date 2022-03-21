package nl.eduid.verifai

import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.jsonBody

import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

import com.verifai.core.Verifai
import com.verifai.core.VerifaiConfiguration
import com.verifai.core.exceptions.LicenceNotValidException
import com.verifai.core.listeners.VerifaiResultListener
import com.verifai.core.result.VerifaiResult

import nl.eduid.verifai.databinding.ActivityMainBinding

@Serializable
data class Message(
    val id: String,
    val uid: String,
    val state: String,
    val svs: String
)

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
    private var host: String? = null
    private var scheme: String? = null
    private var server: String? = null
    private var path: String? = null
    private var id: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        binding = ActivityMainBinding.inflate(layoutInflater)

        // Handle app links.
        val appLinkIntent = intent
        if (appLinkIntent.action === Intent.ACTION_VIEW) {
        val appLinkData = appLinkIntent.data!!
            host = appLinkData.host
            scheme = host?.substringBefore('.')
            server = host?.substringAfter('.')
            path = appLinkData.path
            id = appLinkData.getQueryParameter("id")
            Log.i("info", "Started with host $host")
            Log.i("info", "Started with scheme $scheme")
            Log.i("info", "Started with server $server")
            Log.i("info", "Started with path $path")
            Log.i("info", "Started with id $id")
            val uri = "$scheme://$server$path"
            val data = Message(id.toString(), "martin", "started", "SUCCEEDED")
            val msg = Json.encodeToString(Message.serializer(), data)
            Log.i("info", "Generated URL $uri")
            Log.i("info", "Generated message $msg")

            Fuel.post("http://192.168.1.113/saml/module.php/verifai/callback.php")
                .jsonBody(msg)
                .response { request, response, result ->
                    Log.i("info", "Request: $request")
                    Log.i("info", "Response: $response")
                    val (bytes, error) = result
                    Log.i("info", "Result: ${bytes?.let { String(it) }}")
                    Log.i("info", "Error: $error")
                }
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
                verifaiResult = result
                val intent = Intent(this@MainActivity, VerifaiResultActivity::class.java)
                startActivity(intent)
            }

            override fun onCanceled() {
                Log.d("Verifai", "Cancel")
                // Return to the main app
            }

            override fun onError(e: Throwable) {
                // We are sorry, something wrong happened.
                if (e is LicenceNotValidException) {
                    Log.d("Authentication", "Authentication failed")
                }
            }
        })
    }

    private fun getVerifaiConfiguration(): VerifaiConfiguration {
        return VerifaiConfiguration(
            show_instruction_screens = true,
            enableVisualInspection = true,
        )
    }

    companion object {
        var verifaiResult: VerifaiResult? = null
    }
}
