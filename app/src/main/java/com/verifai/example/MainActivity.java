package com.verifai.example;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.verifai.core.Verifai;
import com.verifai.core.VerifaiConfiguration;
import com.verifai.core.VerifaiInstructionScreenConfiguration;
import com.verifai.core.VerifaiLogger;
import com.verifai.core.listeners.VerifaiResultListener;
import com.verifai.core.result.VerifaiResult;
import com.verifai.liveness.VerifaiLiveness;
import com.verifai.liveness.VerifaiLivenessCheckListener;
import com.verifai.liveness.checks.CloseEyes;
import com.verifai.liveness.checks.FaceMatching;
import com.verifai.liveness.checks.Tilt;
import com.verifai.liveness.checks.VerifaiLivenessCheck;
import com.verifai.liveness.result.VerifaiLivenessCheckResults;
import com.verifai.nfc.VerifaiNfc;
import com.verifai.nfc.VerifaiNfcResultListener;
import com.verifai.nfc.result.VerifaiNfcResult;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Objects;


public class MainActivity extends Activity {
    private VerifaiResult result;
    private String token;
    private String givenname;

    public MainActivity() {
        result = null;
    }

    /**
     * Start the activity and initialize
     *
     * @param savedInstanceState savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String licence = BuildConfig.verifaiLicence;
        Verifai.setLicence(this, licence); // The licence string that has been obtained from the dashboard.

        // Optional: Attach a Logger
        Verifai.logger = new VerifaiLogger() {
            @Override
            public void log(@NotNull Throwable throwable) {

            }

            @Override
            public void log(@NotNull String s) {

            }
        };

        this.findViewById(R.id.start_verifai).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start();
            }
        });

        this.findViewById(R.id.start_liveness).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLiveness();
            }
        });

        // Handle app links.
        Intent appLinkIntent = getIntent();
        if (appLinkIntent.getAction() != null && appLinkIntent.getAction()==appLinkIntent.ACTION_VIEW ) {
            String appLinkAction = appLinkIntent.getAction();
            Uri appLinkData = appLinkIntent.getData();
            if (appLinkData.getQueryParameter("scan").equals("true")) {
                token=appLinkData.getQueryParameter("token");
                Log.i("info","Started with token " +token);
                start();
            }
        }
    }


    /**
     * Start the Verifai scan process
     */
    private void start() {
        VerifaiConfiguration configuration = new VerifaiConfiguration(
                true, // require_document_copy: default true
                true, // enable_post_cropping: default true
                true, // enable_manual: default true
                false, // require_mrz_contents: default false
                false, // require_nfc_when_available: default false
                true, // read_mrz_contents: default true
                5.0, // scanDuration: default 5.0
                true, // show_instruction_screens: deprecated
                new ArrayList<>(), // extraValidators
                new ArrayList<>(), // document_filters
                true, // document_filters_auto_create_validators: default true
                true, // is_scan_help_enabled: default true
                true, // require_cropped_image: default true
                new VerifaiInstructionScreenConfiguration(), // instructionScreenConfiguration
                true // enableVisualInspection: default false
        );
        Verifai.configure(configuration);
        VerifaiResultListener resultListener = new VerifaiResultListener() {
            @Override
            public void onCanceled() {

            }

            @Override
            public void onSuccess(@NonNull VerifaiResult verifaiResult) {
                result = verifaiResult;
                Log.i("info",result.component5().getMrzString());
                showNfcButton();
            }

            @Override
            public void onError(@NotNull Throwable throwable) {
                Log.d("error", throwable.getMessage());
            }
        };
        Verifai.startScan(this, resultListener);
    }

    /**
     * Start the Verifai NFC process
     *
     * @param context The current context
     */
    private void startNfc(Context context) {
        VerifaiNfcResultListener nfcResultListener = new VerifaiNfcResultListener() {
            @Override
            public void onResult(@NotNull VerifaiNfcResult verifaiNfcResult) {
                Log.i("info","Voornaam: "+verifaiNfcResult.getMrzData().toString());
                givenname = verifaiNfcResult.getMrzData().getFirstName();
                updateDataDisplay();
            }

            @Override
            public void onCanceled() {

            }

            @Override
            public void onError(@NotNull Throwable throwable) {

            }
        };
        if (result != null) {
            VerifaiNfc.start(context, result, true, nfcResultListener, true);
        }
    }

    /**
     * Start the Verifai Liveness Check process
     */
    private void startLiveness() {
        if (VerifaiLiveness.isLivenessCheckSupported(getBaseContext())) {
            VerifaiLivenessCheckListener livenessResultListener = new VerifaiLivenessCheckListener() {
                @Override
                public void onResult(@NotNull VerifaiLivenessCheckResults verifaiLivenessCheckResults) {

                }

                @Override
                public void onError(@NotNull Throwable throwable) {

                }
            };

            ArrayList<VerifaiLivenessCheck> checks = new ArrayList<>();
            if (result != null) {
                checks.add(new FaceMatching(this, Objects.requireNonNull(result.getFrontImage())));
            }
            checks.add(new Tilt(this, -25));
            checks.add(new CloseEyes(this, 2));

            VerifaiLiveness.start(this, checks, livenessResultListener);
        } else {
            // Sorry, the Liveness check is not supported by this device
        }
    }


    /**
     * Show the NFC button when needed
     */
    private void showNfcButton() {
        this.findViewById(R.id.start_nfc).setVisibility(View.VISIBLE);
        this.findViewById(R.id.start_nfc).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startNfc(getBaseContext());
            }
        });
    }

    /**
     * Show the data found
     */
    private void updateDataDisplay() {
        final TextView givennameTextView = (TextView) findViewById(R.id.givennametextView);
        givennameTextView.setVisibility(View.VISIBLE);
        givennameTextView.setText(this.givenname);
    }


}
