package com.example.bacain;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "READ IT FOR ME";
    private final int REQUEST_CODE_PERMISSIONS = 10;
    private final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
    private TextureView viewFinder;
    private AppCompatImageView previewImage;
    private MaterialButton captureButton;
    private Bitmap imageBitmap;
    private ProgressBar progressBar;

    private boolean isPreview = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // initialize views
        MaterialButton detectTextButton = findViewById(R.id.detect_button);
        viewFinder = findViewById(R.id.view_finder);
        previewImage = findViewById(R.id.previewImage);
        captureButton = findViewById(R.id.capture_button);
        progressBar = findViewById(R.id.progress);

        // Request camera permissions
        if (allPermissionsGranted()) viewFinder.post(startCamera);
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);

        // Every time the provided texture view changes, recompute layout
        viewFinder.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateTransform());
        detectTextButton.setOnClickListener(v -> detectTextFromImage());
    }

    private void handleNavigationToTextDetectActivity(String text) {
        // hide loading
        progressBar.setVisibility(View.GONE);

        Intent intent = new Intent(MainActivity.this, TextDetectActivity.class);
        intent.putExtra("text", text);
        startActivity(intent);
    }

    private void detectTextFromImage() {
        if (imageBitmap == null) {
            Toast.makeText(this, "Please capture an image first", Toast.LENGTH_SHORT).show();
            return;
        }

        // show loading
        progressBar.setVisibility(View.VISIBLE);

        FirebaseVisionImage firebaseVisionImage = FirebaseVisionImage.fromBitmap(imageBitmap);
        FirebaseVisionTextRecognizer firebaseVisionTextDetector = FirebaseVision.getInstance().getOnDeviceTextRecognizer();
        firebaseVisionTextDetector.processImage(firebaseVisionImage)
                .addOnSuccessListener(this::displayTextFromImage)
                .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Error occurred: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void displayTextFromImage(FirebaseVisionText firebaseVisionText) {
        List<FirebaseVisionText.TextBlock> blockList = firebaseVisionText.getTextBlocks();
        if (blockList.size() == 0) {
            String text = "There is no text to detect";
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
        } else {
            StringBuilder text = new StringBuilder();
            for (FirebaseVisionText.TextBlock block : firebaseVisionText.getTextBlocks()) {
                text.append(block.getText()).append("\n");
            }
            handleNavigationToTextDetectActivity(text.toString());
        }
    }

    private final Runnable startCamera = new Runnable() {
        @Override
        public void run() {
            // Create configuration object for the viewfinder use case
            PreviewConfig previewConfig = new PreviewConfig.Builder()
                    .setLensFacing(CameraX.LensFacing.BACK)
                    //.setTargetAspectRatio(new Rational(400, 580))
                    //.setTargetResolution(new Size(400, 580))
                    .build();

            // Build the viewfinder use case
            Preview preview = new Preview(previewConfig);

            // Every time the viewfinder is updated, recompute layout
            preview.setOnPreviewOutputUpdateListener(
                    previewOutput -> {
                        // To update the SurfaceTexture, we have to remove it and re-add it
                        ViewGroup parent = (ViewGroup) viewFinder.getParent();
                        parent.removeView(viewFinder);
                        parent.addView(viewFinder, 0);

                        viewFinder.setSurfaceTexture(previewOutput.getSurfaceTexture());
                        updateTransform();
                    });

            // Create configuration object for the image capture use case
            ImageCaptureConfig imageCaptureConfig = new ImageCaptureConfig.Builder()
                    //.setTargetAspectRatio(new Rational(400, 580))
                    // We don't set a resolution for image capture; instead, we
                    // select a capture mode which will infer the appropriate
                    // resolution based on aspect ration and requested mode
                    .setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
                    .build();

            // Build the image capture use case and attach button click listener
            ImageCapture imageCapture = new ImageCapture(imageCaptureConfig);


            captureButton.setOnClickListener(view -> {

                if (isPreview) {
                    progressBar.setVisibility(View.GONE);
                    isPreview = false;
                    handleCameraAndPreview();
                    return;
                }

                File file = new File(getExternalMediaDirs()[0], System.currentTimeMillis() + ".jpg");
                imageCapture.takePicture(file, new ImageCapture.OnImageSavedListener() {
                    @Override
                    public void onError(
                            @NonNull ImageCapture.UseCaseError error,
                            @NonNull String message,
                            @Nullable Throwable exc
                    ) {
                        String msg = "Photo capture failed: " + message;
                        Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();
                        Log.e(TAG, msg);
                        if (exc != null) {
                            exc.printStackTrace();
                        }
                    }

                    @Override
                    public void onImageSaved(@NonNull File file) {
                        String msg = "Photo capture succeeded: " + file.getAbsolutePath();
                        Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();
                        Log.d(TAG, msg);

                        if (file.exists()) {
                            imageBitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                            previewImage.setImageBitmap(rotateBitmap(imageBitmap, 90));

                            isPreview = !isPreview;
                            handleCameraAndPreview();
                        }
                    }
                });
            });

            // Setup image analysis pipeline that computes average pixel luminance
            HandlerThread analyzerThread = new HandlerThread("LuminosityAnalysis");
            analyzerThread.start();
            ImageAnalysisConfig analyzerConfig =
                    new ImageAnalysisConfig.Builder()
                            .setCallbackHandler(new Handler(analyzerThread.getLooper()))
                            // In our analysis, we care more about the latest image than
                            // analyzing *every* image
                            .setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
                            .build();

            ImageAnalysis analyzerUseCase = new ImageAnalysis(analyzerConfig);
            analyzerUseCase.setAnalyzer(new LuminosityAnalyzer());

            // Bind use cases to lifecycle
            CameraX.bindToLifecycle((LifecycleOwner) MainActivity.this, preview, imageCapture,
                    analyzerUseCase);

            //uncomment if you want to turn on the torch
            //preview.enableTorch(true);
        }
    };

    public static Bitmap rotateBitmap(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private void updateTransform() {
        android.graphics.Matrix matrix = new Matrix();

        float centerX = viewFinder.getWidth() / 2f;
        float centerY = viewFinder.getHeight() / 2f;

        // Correct preview output to account for display rotation
        float rotationDegrees;
        switch (viewFinder.getDisplay().getRotation()) {
            case Surface.ROTATION_0:
                rotationDegrees = 0f;
                break;
            case Surface.ROTATION_90:
                rotationDegrees = 90f;
                break;
            case Surface.ROTATION_180:
                rotationDegrees = 180f;
                break;
            case Surface.ROTATION_270:
                rotationDegrees = 270f;
                break;
            default:
                return;
        }

        matrix.postRotate(-rotationDegrees, centerX, centerY);

        // Finally, apply transformations to our TextureView
        viewFinder.setTransform(matrix);
    }

    /**
     * Process result from permission request dialog box, has the request
     * been granted? If yes, start Camera. Otherwise display a toast
     */
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                viewFinder.post(startCamera);
            }
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(getBaseContext(), permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private static byte[] toByteArray(ByteBuffer buffer) {
        buffer.rewind();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        return data;
    }

    private static class LuminosityAnalyzer implements ImageAnalysis.Analyzer {
        private long lastAnalyzedTimestamp = 0L;

        @Override
        public void analyze(ImageProxy image, int rotationDegrees) {
            long currentTimestamp = System.currentTimeMillis();
            if (currentTimestamp - lastAnalyzedTimestamp >=
                    TimeUnit.SECONDS.toMillis(1)) {
                // Since format in ImageAnalysis is YUV, image.planes[0]
                // contains the Y (luminance) plane
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                // Extract image data from callback object
                byte[] data = toByteArray(buffer);

                // Convert the data into an array of pixel values
                int sum = 0;
                for (byte val : data) {
                    // Add pixel value
                    sum += (((int) val) & 0xFF);
                }

                // Compute average luminance for the image
                double luma = sum / ((double) data.length);
                Log.d(TAG, "Average Luminosity " + luma);
                // Update timestamp of last analyzed frame
                lastAnalyzedTimestamp = currentTimestamp;
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void handleCameraAndPreview() {
        if (isPreview) {
            captureButton.setText("Recapture");
            previewImage.setVisibility(View.VISIBLE);
            viewFinder.setVisibility(View.GONE);
        } else {
            captureButton.setText("Capture");
            previewImage.setVisibility(View.GONE);
            viewFinder.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isPreview = false;
        handleCameraAndPreview();
    }
}

