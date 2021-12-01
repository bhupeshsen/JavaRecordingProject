package com.kiwitech.javarecordingproject;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private String filePath = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            String[] permissions = new String[]{Manifest.permission.RECORD_AUDIO};
            ActivityCompat.requestPermissions(this, permissions, 10);
            return;
        }

        FlutterAudioRecorder2Plugin plugin = new FlutterAudioRecorder2Plugin(this);

        Button startRecButton = findViewById(R.id.startRecording);
        Button stopRecButton = findViewById(R.id.stopRecording);
        Button playRecButton = findViewById(R.id.playRecording);
        playRecButton.setEnabled(false);
        stopRecButton.setEnabled(false);

        startRecButton.setOnClickListener(view -> {
            startRecButton.setEnabled(false);
            playRecButton.setEnabled(false);
            stopRecButton.setEnabled(true);
            filePath = getExternalFilesDir(Environment.DIRECTORY_MUSIC) + "/f_audio_" + System.currentTimeMillis() + ".wav";
            plugin.handleInit(16000, filePath, ".wav");
            plugin.handleStart();
        });

        stopRecButton.setOnClickListener(view -> {
            startRecButton.setEnabled(true);
            playRecButton.setEnabled(true);
            stopRecButton.setEnabled(false);
            plugin.handleStop();
        });

        playRecButton.setOnClickListener(view -> {
            if (filePath == null)
                Toast.makeText(this, "No File Found", Toast.LENGTH_SHORT).show();
            else {
                MediaPlayer mPlayer = new MediaPlayer();
                try {
                    mPlayer.setDataSource(filePath);
                    mPlayer.prepare();
                } catch (IllegalArgumentException | IOException e) {
                    Toast.makeText(this, "Error while playing audio", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
                mPlayer.start();

                playRecButton.setEnabled(false);
                startRecButton.setEnabled(false);
                mPlayer.setOnCompletionListener(mediaPlayer -> {
                    playRecButton.setEnabled(true);
                    startRecButton.setEnabled(true);
                });
            }
        });
    }
}