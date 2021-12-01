package com.kiwitech.javarecordingproject;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {
    private static final String LOG_NAME = "AndroidAudioRecorder";
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 200;
    private static final byte RECORDER_BPP = 16; // we use 16bit

    private int mSampleRate = 16000; // 16Khz
    private AudioRecord mRecorder = null;
    private String mFilePath;
    private String mExtension;
    private int bufferSize = 1024;
    private FileOutputStream mFileOutputStream = null;
    private String mStatus = "unset";
    private double mPeakPower = -120;
    private double mAveragePower = -120;
    private Thread mRecordingThread = null;
    private long mDataSize = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, mSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

    }

    private void handleStart() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, mSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        try {
            mFileOutputStream = new FileOutputStream(getTempFilename());
        } catch (FileNotFoundException e) {

            return;
        }
        mRecorder.startRecording();
        startThread();

    }
    private void handleStop() {
        if(mStatus.equals("stopped")) {

        } else {
            mStatus = "stopped";

            // Return Recording Object
            HashMap<String, Object> currentResult = new HashMap<>();
            currentResult.put("duration", getDuration() * 1000);
            currentResult.put("path", mFilePath);
            currentResult.put("audioFormat", mExtension);
            currentResult.put("peakPower", mPeakPower);
            currentResult.put("averagePower", mAveragePower);
            currentResult.put("isMeteringEnabled", true);
            currentResult.put("status", mStatus);
            resetRecorder();
            mRecordingThread = null;
            mRecorder.stop();
            mRecorder.release();
            try {
                mFileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.d(LOG_NAME, "before adding the wav header");
            copyWaveFile(getTempFilename(), mFilePath);
            deleteTempFile();

            // Log.d(LOG_NAME, currentResult.toString());

        }

    }
    private void startThread(){
        mRecordingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                processAudioStream();
            }
        }, "Audio Processing Thread");
        mRecordingThread.start();
    }

    private void processAudioStream() {
        Log.d(LOG_NAME, "processing the stream: " + mStatus);
        int size = bufferSize;
        byte bData[] = new byte[size];

        while (mStatus == "recording"){
            Log.d(LOG_NAME, "reading audio data");
            mRecorder.read(bData, 0, bData.length);
            mDataSize += bData.length;
            updatePowers(bData);
            try {
                mFileOutputStream.write(bData);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private void deleteTempFile() {
        File file = new File(getTempFilename());
        if(file.exists()) {
            file.delete();
        }
    }
  private String getTempFilename() {
    String filepath = mFilePath + ".temp";
    return filepath;
}

    private void copyWaveFile(String inFilename, String outFilename) {
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = mSampleRate;
        int channels = 1;
        long byteRate = RECORDER_BPP * mSampleRate * channels / 8;

        byte[] data = new byte[bufferSize];

        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;

            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);

            while (in.read(data) != -1) {
                out.write(data);
            }

            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void WriteWaveFileHeader(FileOutputStream out, long totalAudioLen,
                                     long totalDataLen, long longSampleRate, int channels, long byteRate)
            throws IOException {
        byte[] header = new byte[44];

        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) ((channels * (int) RECORDER_BPP) >>  3);
        header[33] = (byte) ((channels * (int) RECORDER_BPP) >>  11);
        header[34] = RECORDER_BPP; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }

    private short[] byte2short(byte[] bData) {
        short[] out = new short[bData.length/2];
        ByteBuffer.wrap(bData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out);
        return out;
    }

    private void resetRecorder(){
        mPeakPower = -120;
        mAveragePower = -120;
        mDataSize = 0;
    }

    private void updatePowers(byte[] bdata){
        short[] data = byte2short(bdata);
        short sampleVal = data[data.length - 1];
        String[] escapeStatusList = new String[]{"paused", "stopped", "initialized", "unset"};

        if(sampleVal == 0 || Arrays.asList(escapeStatusList).contains(mStatus)){
            mAveragePower = -120; // to match iOS silent case
        }
        else {
            // iOS factor : to match iOS power level
            double iOSFactor = 0.25;
            mAveragePower = 20 * Math.log(Math.abs(sampleVal) / 32768.0)  * iOSFactor;
        }

        mPeakPower = mAveragePower;
        // Log.d(LOG_NAME, "Peak: " + mPeakPower + " average: "+ mAveragePower);
    }

    private int getDuration(){
        long duration = mDataSize / (mSampleRate * 2 * 1);
        return (int)duration;
    }
}