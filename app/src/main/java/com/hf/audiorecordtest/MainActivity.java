package com.hf.audiorecordtest;

import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.LinkedList;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static String PATH = "/sdcard/audio.wav";
    private static int SAMPLE_RATE = 44100;
    private static int BUFFER_SIZE = 882; // 20 ms
    private static int BUFFER_DURATION_MS = BUFFER_SIZE * 1000 / SAMPLE_RATE;
    private static int PLAY_DELAY_MS = 1000; // 1 sec
    private static int PLAY_DELAY_BUFFERS = PLAY_DELAY_MS / BUFFER_DURATION_MS;

    private static final int FUNC_RECORD = 0;
    private static final int FUNC_ARRAY_RECORD = 1;
    private static final int FUNC_ARRAY0_RECORD = 2;
    private static final int FUNC_PLAYBACK = 3;

    private static String[] PERMISSIONS = new String[]{
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
    };

    private Button mBtnRecordStart;
    private Button mBtnRecordStop;
    private Button mBtnArrayRecordStart;
    private Button mBtnArrayRecordStop;
    private Button mBtnArray0RecordStart;
    private Button mBtnArray0RecordStop;
    private Button mBtnPlaybackStart;
    private Button mBtnPlaybackStop;

    private Thread mRecordThread = null;
    private Thread mArrayRecordThread = null;
    private Thread mArray0RecordThread = null;
    private Thread mPlaybackThread = null;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    private final LinkedList<ByteBuffer> mAudioQueue = new LinkedList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()# begin");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // function with safe direct ByteBuffer API usage
        mBtnRecordStart = findViewById(R.id.without_array_record_start);
        mBtnRecordStop = findViewById(R.id.without_array_record_stop);

        // function with direct ByteBuffer and copy from array() start with index 0
        mBtnArray0RecordStart = findViewById(R.id.index0_record_start);
        mBtnArray0RecordStop = findViewById(R.id.index0_record_stop);

        // function with direct ByteBuffer and copy from array() start with right offset
        mBtnArrayRecordStart = findViewById(R.id.offset_record_start);
        mBtnArrayRecordStop = findViewById(R.id.offset_record_stop);

        // audio playback
        mBtnPlaybackStart = findViewById(R.id.playback_start);
        mBtnPlaybackStop = findViewById(R.id.playback_stop);

        // bind onClickListener
        mBtnRecordStart.setOnClickListener(this::onStartRecord);
        mBtnRecordStop.setOnClickListener(this::onStopRecord);
        mBtnArrayRecordStart.setOnClickListener(this::onStartArrayRecord);
        mBtnArrayRecordStop.setOnClickListener(this::onStopArrayRecord);
        mBtnArray0RecordStart.setOnClickListener(this::onStartArray0Record);
        mBtnArray0RecordStop.setOnClickListener(this::onStopArray0Record);
        mBtnPlaybackStart.setOnClickListener(this::onStartPlayback);
        mBtnPlaybackStop.setOnClickListener(this::onStopPlayback);

        uiReset();

        Log.d(TAG, "onCreate()# end");
    }

    private void uiReset() {
        mBtnRecordStart.setEnabled(true);
        mBtnRecordStop.setEnabled(false);
        mBtnArray0RecordStart.setEnabled(true);
        mBtnArray0RecordStop.setEnabled(false);
        mBtnArrayRecordStart.setEnabled(true);
        mBtnArrayRecordStop.setEnabled(false);
        mBtnPlaybackStart.setEnabled(true);
        mBtnPlaybackStop.setEnabled(false);
    }

    private void uiPlay(int func) {
        boolean recordStart = false;
        boolean recordStop = false;
        boolean arrayRecordStart = false;
        boolean arrayRecordStop = false;
        boolean array0RecordStart = false;
        boolean array0RecordStop = false;
        boolean playbackStart = false;
        boolean playbackStop = false;

        switch (func) {
            case FUNC_RECORD:
                recordStop = true;
                break;
            case FUNC_ARRAY_RECORD:
                arrayRecordStop = true;
                break;
            case FUNC_ARRAY0_RECORD:
                array0RecordStop = true;
                break;
            case FUNC_PLAYBACK:
            default:
                playbackStop = true;
        }

        mBtnRecordStart.setEnabled(recordStart);
        mBtnRecordStop.setEnabled(recordStop);
        mBtnArray0RecordStart.setEnabled(array0RecordStart);
        mBtnArray0RecordStop.setEnabled(array0RecordStop);
        mBtnArrayRecordStart.setEnabled(arrayRecordStart);
        mBtnArrayRecordStop.setEnabled(arrayRecordStop);
        mBtnPlaybackStart.setEnabled(playbackStart);
        mBtnPlaybackStop.setEnabled(playbackStop);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        for (int result : grantResults) {
            if (PackageManager.PERMISSION_GRANTED != result) {
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume()# begin");
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> toCheck = new ArrayList<>();
            for (String permission : PERMISSIONS) {
                if (PackageManager.PERMISSION_GRANTED != checkSelfPermission(permission)) {
                    toCheck.add(permission);
                }
            }

            if (toCheck.size() > 0) {
                requestPermissions(toCheck.toArray(new String[0]), 0);
            }
        }
        Log.d(TAG, "onResume()# end");
    }

    private void onStartRecord(View v) {
        Log.d(TAG, "onStartRecord()# begin");
        if (mRecordThread != null) {
            Log.d(TAG, "onStartRecord()# already started");
            return;
        }

        // start record thread
        mRecordThread = new Thread(this::onRecord);
        mRecordThread.start();

        // update UI
        uiPlay(FUNC_RECORD);

        Log.d(TAG, "onStartRecord()# end");
    }

    private void onStopRecord(View v) {
        if (mRecordThread == null) {
            return;
        }
        mRecordThread = null;
    }

    private void onRecord() {
        Process.setThreadPriority(-19);

        // start recording
        AudioRecord audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE);
        audioRecord.startRecording();

        // read
        while (mRecordThread != null) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
            int nRead = audioRecord.read(buffer, BUFFER_SIZE);
            buffer.limit(nRead);

            synchronized (mAudioQueue) {
                mAudioQueue.addLast(buffer); // enqueue
                //Log.d(TAG, "onRecord()# add a buffer. size: " + buffer.remaining() + ", queue: " + mAudioQueue.size());
                mAudioQueue.notifyAll();

                if (mPlaybackThread == null && mAudioQueue.size() >= PLAY_DELAY_BUFFERS) {
                    // start playback
                    mPlaybackThread = new Thread(()->{
                        // init audio track
                        AudioTrack audioTrack = new AudioTrack(
                                AudioManager.STREAM_MUSIC,
                                SAMPLE_RATE,
                                AudioFormat.CHANNEL_OUT_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                                BUFFER_SIZE,
                                AudioTrack.MODE_STREAM
                        );
                        audioTrack.play();

                        ByteBuffer audioData;
                        do {
                            synchronized (mAudioQueue) {
                                // wait for audio data
                                while (mAudioQueue.isEmpty()) {
                                    try {
                                        mAudioQueue.wait();
                                    } catch (InterruptedException e) {
                                        Log.w(TAG, "wait for mAudioQueue interrupted.", e);
                                    }
                                }

                                // process data
                                audioData = mAudioQueue.pollFirst(); // dequeue
                            }

                            if (audioData != null) {
                                audioData.position(0);
                                //Log.d(TAG, "onRecord()# play a buffer. size: " + audioData.remaining() + ", queue: " + mAudioQueue.size());
                                audioTrack.write(audioData, audioData.remaining(), AudioTrack.WRITE_BLOCKING);
                                audioTrack.flush();
                            }

                        } while (audioData != null);

                        // stop
                        audioTrack.stop();
                        audioTrack.release();

                        // update UI
                        mHandler.post(this::uiReset);

                        mPlaybackThread = null;
                    });
                    mPlaybackThread.start();
                }
            }
        }

        synchronized (mAudioQueue) {
            mAudioQueue.addLast(null);
            mAudioQueue.notifyAll();
        }

        // stop
        audioRecord.stop();
        audioRecord.release();

        // update UI
        mHandler.post(this::uiReset);
    }

    private void onStartArrayRecord(View v) {
        Log.d(TAG, "onStartRecord()# begin");
        if (mRecordThread != null) {
            Log.d(TAG, "onStartRecord()# already started");
            return;
        }

        // start record thread
        mArrayRecordThread = new Thread(this::onArrayRecord);
        mArrayRecordThread.start();

        // update UI
        uiPlay(FUNC_ARRAY_RECORD);

        Log.d(TAG, "onStartRecord()# end");
    }

    private void onStopArrayRecord(View v) {
        if (mArrayRecordThread == null) {
            return;
        }
        mArrayRecordThread = null;
    }

    private void onArrayRecord() {
        Process.setThreadPriority(-19);

        // start recording
        AudioRecord audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE);
        ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        audioRecord.startRecording();

        // remove previous file
        File outputFile = new File(PATH);
        if (outputFile.exists()) {
            outputFile.delete();
        }

        // read
        while (mArrayRecordThread != null) {
            buffer.position(0);
            int nRead = audioRecord.read(buffer, BUFFER_SIZE);
            try (FileOutputStream fos = new FileOutputStream(outputFile, true)) {
                Log.d(TAG, "onArrayRecord()# hasArray: " + buffer.hasArray() + ", arrayOffset: " + buffer.arrayOffset() + ", length: " + buffer.array().length);
                fos.write(buffer.array(), buffer.arrayOffset() , nRead);
            } catch (IOException e) {

            }
        }

        // stop
        audioRecord.stop();
        audioRecord.release();

        // update UI
        mHandler.post(this::uiReset);
    }

    private void onStartArray0Record(View v) {
        Log.d(TAG, "onStartRecord()# begin");
        if (mArray0RecordThread != null) {
            Log.d(TAG, "onStartRecord()# already started");
            return;
        }

        // start record thread
        mArray0RecordThread = new Thread(this::onArray0Record);
        mArray0RecordThread.start();

        // update UI
        uiPlay(FUNC_ARRAY0_RECORD);

        Log.d(TAG, "onStartRecord()# end");
    }

    private void onStopArray0Record(View v) {
        if (mArray0RecordThread == null) {
            return;
        }
        mArray0RecordThread = null;
    }

    private void onArray0Record() {
        Process.setThreadPriority(-19);

        // start recording
        AudioRecord audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE);
        ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        audioRecord.startRecording();

        // remove previous file
        File outputFile = new File(PATH);
        if (outputFile.exists()) {
            outputFile.delete();
        }

        // read
        while (mArray0RecordThread != null) {
            buffer.position(0);
            int nRead = audioRecord.read(buffer, BUFFER_SIZE);
            try (FileOutputStream fos = new FileOutputStream(outputFile, true)) {
                Log.d(TAG, "onRecord()# hasArray: " + buffer.hasArray() + ", arrayOffset: " + buffer.arrayOffset() + ", length: " + buffer.array().length);
                fos.write(buffer.array(), 0 , nRead);
            } catch (IOException e) {

            }
        }

        // stop
        audioRecord.stop();
        audioRecord.release();

        // update UI
        mHandler.post(this::uiReset);
    }

    private void onStartPlayback(View v) {
        Log.d(TAG, "onStartPlayback()# begin");
        if (mPlaybackThread != null) {
            return;
        }

        // start playback thread
        mPlaybackThread = new Thread(()->{
            onPlayback();
            mPlaybackThread = null;
        });
        mPlaybackThread.start();

        // update UI
        uiPlay(FUNC_PLAYBACK);

        Log.d(TAG, "onStartPlayback()# end");
    }

    private void onStopPlayback(View v) {
        if (mPlaybackThread == null) {
            return;
        }
        mPlaybackThread = null;
    }

    private void onPlayback() {
        Log.d(TAG, "onPlayback()# begin");
        AudioTrack audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE,
                AudioTrack.MODE_STREAM
        );
        audioTrack.play();
        ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        buffer.position(0);

        // check file
        File inputFile = new File(PATH);
        if (!inputFile.exists()) {
            mHandler.post(()-> Toast.makeText(this, "Please record first.", Toast.LENGTH_SHORT).show());
            Log.d(TAG, "onPlayback()# file not found");
            return;
        }

        // play
        try (FileInputStream fis = new FileInputStream(PATH)) {
            try (FileChannel fc = fis.getChannel()) {
                int nRead = 0;
                while (mPlaybackThread != null && (nRead = fc.read(buffer)) > 0) {
                    //Log.d(TAG, "onPlayback()# read: " + nRead);
                    buffer.position(0);
                    audioTrack.write(buffer, nRead, AudioTrack.WRITE_BLOCKING);
                    audioTrack.flush();
                    buffer.position(0);
                }
                Log.d(TAG, "onPlayback()# read done");
            }
        } catch (IOException e) {

        }

        // stop
        audioTrack.stop();
        audioTrack.release();

        // update UI
        mHandler.post(this::uiReset);

        Log.d(TAG, "onPlayback()# end");
    }
}