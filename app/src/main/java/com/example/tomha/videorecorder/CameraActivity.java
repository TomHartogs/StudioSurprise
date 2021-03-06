package com.example.tomha.videorecorder;

/**
 * Created by tomha on 27-2-2018.
 */

import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.tomha.videorecorder.Preferences.CameraPreferenceReader;
import com.example.tomha.videorecorder.Preferences.SettingsActivity;

import java.io.File;
import java.util.List;

public class CameraActivity extends Activity implements SurfaceHolder.Callback {

    private MediaRecorder mMediaRecorder;
    private Camera mCamera;
    private boolean recording = false;
    private boolean secretMenuTimerExpired = false;
    private TextView resterendeText;
    private SurfaceHolder mHolder;

    private static final int SETTINGS_REQUEST = 0;
    private static final int FIVE_SECONDS = 5 * 1000; // 5s * 1000 ms/s

    private boolean prefMaxRecordingLengthEnabled;
    private int prefMaxRecordingLength;
    private int prefAudioSource;
    private CamcorderProfile prefProfile;
    private boolean prefCountdownTimerEnabled;

    private CameraPreferenceReader pr;
    private CountDownTimer timeLeftTimer;
    private CountDownTimer secretMenuTimer = new CountDownTimer(FIVE_SECONDS, 1000) {
        public void onTick(long millisUntilFinished) {
            //Do nothing
        }
        public void onFinish() {
            secretMenuTimerExpired = true;
            MotionEvent fakeMotionEvent = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis()+100, MotionEvent.ACTION_POINTER_UP, 0.0f, 0.0f, 0);
            onTouchEvent(fakeMotionEvent);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.media_recorder_recipe);

        resterendeText = findViewById(R.id.resterendTextView);

        pr = new CameraPreferenceReader(this);
        updatePreferences();

        mHolder = ((SurfaceView)findViewById(R.id.surfaceView)).getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_HEADSETHOOK:
                onRecordButtonClick();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void onRecordButtonClick(){
        onRecordButtonClick(findViewById(R.id.recordingButton));
    }

    public void onRecordButtonClick(View v){
        final Button mButton = (Button) v;
        if (!recording) {
            try {
                initRecorder(mHolder.getSurface());
            } catch (IOException e) {
                e.printStackTrace();
            }
            mCamera.unlock();
            mMediaRecorder.start();
            recording = true;
            mButton.setVisibility(View.INVISIBLE);
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {

                @Override
                public void run() {
                    mButton.setVisibility(View.VISIBLE);
                    mButton.setText(R.string.stop);
                    mButton.getBackground().setColorFilter(Color.RED, PorterDuff.Mode.MULTIPLY);
                }

            }, 500); // 5000ms delay*/
            if (prefMaxRecordingLengthEnabled && prefCountdownTimerEnabled) {
                setTimeLeftTimer(prefMaxRecordingLength);
                timeLeftTimer.start();
            }
        } else {
            recording = false;
            try {
                mMediaRecorder.stop();
            } catch(RuntimeException e) {

            }
            mMediaRecorder.reset();
            if (prefMaxRecordingLengthEnabled && prefCountdownTimerEnabled) {
                timeLeftTimer.cancel();
                resterendeText.setVisibility(View.INVISIBLE);
            }
            mButton.setText(R.string.start);
            mButton.getBackground().setColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY);
        }
    }


    @Override
    public boolean onTouchEvent(MotionEvent e) {
        final int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int fingerCount = e.getPointerCount();
                if (fingerCount == 2) {
                    // We have four fingers touching, so start the timer
                    secretMenuTimer.cancel();
                    secretMenuTimer.start();
                    secretMenuTimerExpired = false;
                    //twoFingerDownTime = System.currentTimeMillis();
                } else if (fingerCount > 2) {
                    secretMenuTimer.cancel();
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int fingerCount = e.getPointerCount() - 1;
                if (secretMenuTimerExpired) {
                    secretMenuTimerExpired = false;
                    // Two fingers have been down for 5 seconds!
                    if (pr.getSharedPreferenceBooleanValue(getString(R.string.pref_key_passwordEnabled))) {
                        showPasswordPrompt();
                        break;
                    } else {
                        openSettingsMenu();
                    }
                }
                else if (fingerCount < 2) {
                    // Fewer than four fingers, so reset the timer
                    secretMenuTimer.cancel();
                    secretMenuTimerExpired = false;
                    //twoFingerDownTime = -1;
                }
                else if (fingerCount == 2) {
                    secretMenuTimer.cancel();
                    secretMenuTimer.start();
                    secretMenuTimerExpired = false;
                }
            }
        }
        return true;
    }

    private void showPasswordPrompt() {
        LayoutInflater li = LayoutInflater.from(this);
        View promptsView = li.inflate(R.layout.password_prompt, null);

        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

        alertDialogBuilder.setView(promptsView);

        final EditText userInput = promptsView.findViewById(R.id.editTextPasswordInput);
        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                if (userInput.getText().toString().equals(pr.getSharedPreferenceValue(getString(R.string.pref_key_password)))) {
                    openSettingsMenu();
                } else {
                    Toast message = Toast.makeText(getApplicationContext(), "Wrong password entered", Toast.LENGTH_SHORT);
                    message.show();
                    showPasswordPrompt();
                }
            }
        });
        alertDialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });

        alertDialogBuilder.create().show();
    }

    private void openSettingsMenu(){
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivityForResult(intent, SETTINGS_REQUEST);
    }

    private void setTimeLeftTimer(int maxTime){
        timeLeftTimer = new CountDownTimer(maxTime * 1000, 1000) {
            public void onTick(long millisUntilFinished) {
                resterendeText.setText("Seconden resterend: " + millisUntilFinished / 1000);
                resterendeText.setVisibility(View.VISIBLE);
            }

            public void onFinish() {
                resterendeText.setVisibility(View.INVISIBLE);
            }
        };
    }

    private void updatePreferences(){
        prefMaxRecordingLengthEnabled = pr.getMaxLengthEnabled();
        if(prefMaxRecordingLengthEnabled) {
            prefMaxRecordingLength = pr.getMaxRecordingLength();
            prefCountdownTimerEnabled = pr.getCountdownTimerEnabled();
            if(prefCountdownTimerEnabled) {
                setTimeLeftTimer(prefMaxRecordingLength);
            }
        }
        prefAudioSource = pr.getSavedAudioSource();
        prefProfile = pr.getSavedCamcorderProfile();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == SETTINGS_REQUEST) {
            updatePreferences();
        }
    }

    /* Init the MediaRecorder, the order the methods are called is vital to
     * its correct functioning */
    private void initRecorder(Surface surface) throws IOException {
        // It is very important to unlock the camera before doing setCamera
        // or it will results in a black preview

        if (mMediaRecorder == null) mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setPreviewDisplay(surface);
        if(mCamera == null){
            initCamera();
        }
        mMediaRecorder.setCamera(mCamera);
        if(prefMaxRecordingLengthEnabled){
            VideoLimiter vl = new VideoLimiter();
            vl.observeLimit(prefMaxRecordingLength);
        }
        mMediaRecorder.setAudioSource(prefAudioSource);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setProfile(prefProfile);
        File mOutputFile;
        mOutputFile = CameraHelper.getOutputMediaFile(CameraHelper.MEDIA_TYPE_VIDEO, "StudioSurprise");

        if (mOutputFile != null) {
            mMediaRecorder.setOutputFile(mOutputFile.getPath());
        }
        else{
            mMediaRecorder = null;
        }

        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            // This is thrown if the previous calls are not called with the
            // proper order
            e.printStackTrace();
        }
    }

    private void initCamera(){
        mCamera = CameraHelper.getDefaultFrontFacingCameraInstance();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        initCamera();
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        shutdown();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    private void shutdown() {
        // Release MediaRecorder and especially the Camera as it's a shared
        // object that can be used by other applications
        if(mMediaRecorder != null) {
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
        if(mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    public class VideoLimiter extends Activity implements MediaRecorder.OnInfoListener {

        public void observeLimit(int delay) {
            // Normal MediaRecorder Setup
            mMediaRecorder.setMaxDuration(delay * 1000);
            mMediaRecorder.setOnInfoListener(this);
        }

        public void onInfo(MediaRecorder mr, int what, int extra) {
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                Log.v("VIDEOCAPTURE","Maximum Duration Reached");
                onRecordButtonClick();
            }
        }
    }
}