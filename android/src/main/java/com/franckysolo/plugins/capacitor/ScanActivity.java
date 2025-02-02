package com.franckysolo.plugins.capacitor;

import androidx.annotation.NonNull;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.media.AudioManager;
import android.media.Image;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.franckysolo.plugins.capacitor.capacitormlkitbarcodescanner.R;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanActivity extends AppCompatActivity {

    private static final String TAG = "Barcodescanner";
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView mScanPreview;
    private ScanTracker mScanTracker;
    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;
    private InputImage scanImage;
    private String resultCode = "";
    private Button mScanButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        process();
    }

    @Override
    public void finish() {
        Intent data = new Intent();
        data.putExtra("code", resultCode);
        setResult(RESULT_OK, data);
        Log.i(TAG, "Finish scan sent intent rawValue " + resultCode);
        super.finish();
    }

    /**
     * Process scan
     */
    private void process() {
        initResources();
        initCameraX();
        initScanner();
        initUserEvents();
    }

    /**
     * Set event click button to start scanner
     */
    private void initUserEvents() {
        mScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startScan();
            }
        });
    }

    /**
     * Initialise resources xml
     */
    private void initResources() {
        setContentView(R.layout.activity_scan);

        mScanPreview = findViewById(R.id.scan_preview);
        if (mScanPreview == null) {
            Log.d(TAG, "Preview is null");
        }

        mScanButton = findViewById(R.id.scan_button);
        if (mScanButton == null) {
            Log.d(TAG, "scanButton is null");
        }

        mScanTracker = findViewById(R.id.scan_tracker);
        if (mScanTracker == null) {
            Log.d(TAG, "scan tracker is null");
        }
    }

    /**
     * Create the scanner preview & prepare image capture
     */
    private void initScanner() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    cameraProvider.unbindAll();
                    createScannerPreview(cameraProvider);
                } catch (ExecutionException | InterruptedException e) {
                    // No errors need to be handled for this Future.
                    // This should never be reached.
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * Scan bar codes
     */
    private void startScan() {
        BarcodeScannerOptions options =
            new BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build();

        final BarcodeScanner scanner = BarcodeScanning.getClient(options);

        imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
            @SuppressLint("UnsafeExperimentalUsageError")
            public void onCaptureSuccess(final ImageProxy image)  {

                try (Image mediaImage = image.getImage()) {

                    if (mediaImage == null) {
                        return;
                    }

                    scanImage = InputImage.fromMediaImage(
                        mediaImage,
                        image.getImageInfo().getRotationDegrees()
                    );

                    scanner.process(scanImage).addOnSuccessListener(new OnSuccessListener<List<Barcode>>() {
                        @Override
                        public void onSuccess(List<Barcode> codes) {
                            Toast.makeText(getApplicationContext(),
                                    "Start scanning...",
                                    Toast.LENGTH_SHORT).show();
                            for (Barcode barcode: codes) {
                                resultCode = barcode.getRawValue();
                                int valueType = barcode.getValueType();
                                Log.i(TAG, "Barcode rawValue " + resultCode);
                                Log.i(TAG, "Barcode valueType " + valueType);
                                invokeScanTracker(barcode, image);
                                final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_ALARM, 70);
                                tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200);
                                mediaImage.close();

                                image.close();
                            }
                            closeAfterDelay();
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Toast.makeText(getApplicationContext(),
                                    "Cannot scan this code.",
                                    Toast.LENGTH_SHORT).show();
                            mediaImage.close();

                            image.close();
                        }
                    });
                }
            }

            /**
             * Close camera view after decode barcode
             */
            private void closeAfterDelay() {
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    public void run() {
                        finish();
                    }
                }, 3000);
            }
        });
    }

    /**
     * Create the scanner preview & prepare image capture
     *
     * @param cameraProvider cameraProvider
     */
    private void createScannerPreview(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1920, 1080))
                .setImageQueueDepth(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageCapture = new ImageCapture.Builder()
                .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        preview.setSurfaceProvider(mScanPreview.getSurfaceProvider());

        cameraProvider.bindToLifecycle(
            this,
            cameraSelector,
            preview,
            imageCapture,
            imageAnalysis
        );
    }

    private void invokeScanTracker(Barcode barcode, ImageProxy image) {
        mScanTracker.track(barcode, image);
    }

    /**
     * Run CameraX thread executor
     */
    public void initCameraX () {
        cameraExecutor = Executors.newSingleThreadExecutor();
    }
}
