package com.example.bacain;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

import java.util.Locale;

public class TextDetectActivity extends AppCompatActivity {

    private MaterialTextView textView;
    private MaterialButton openCamera, speakButton;
    private String result;
    private TextToSpeech textToSpeech;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_detect);

        textView = findViewById(R.id.text_view);
        openCamera = findViewById(R.id.open_camera_button);
        speakButton = findViewById(R.id.speak_button);

        // init text to speech
        textToSpeech = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.ENGLISH);
            }
        });

        handleDataIntent();
        initListener();
    }

    private void initListener() {
        openCamera.setOnClickListener(v -> onBackPressed());
        speakButton.setOnClickListener(v -> {
            String textToConvert = result;
            textToSpeech.speak(textToConvert, TextToSpeech.QUEUE_FLUSH, null, null);
        });
    }

    private void handleDataIntent() {
        result = getIntent().getStringExtra("text");
        textView.setText(result);
    }
}