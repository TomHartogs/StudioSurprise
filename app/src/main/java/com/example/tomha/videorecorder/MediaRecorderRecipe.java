package com.example.tomha.videorecorder;

/**
 * Created by tomha on 27-2-2018.
 */

import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Parameter;
import java.util.List;

import static android.content.ContentValues.TAG;

public class MediaRecorderRecipe extends Activity implements SurfaceHolder.Callback {

    private MediaRecorder mMediaRecorder;
    private Camera mCamera;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mHolder;
    private Button mButton;
    private boolean mInitSuccesful;
    private File mOutputFile;
    private boolean recording = false;
    private CountDownTimer timer;
    //private TextView resterendeText;

    private static final int FIVE_SECONDS = 5 * 1000; // 5s * 1000 ms/s
    private long twoFingerDownTime = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.media_recorder_recipe);

        //resterendeText = findViewById(R.id.resterendTextView);

        mButton = findViewById(R.id.recordingButton);
        mButton.setOnClickListener(new OnClickListener() {
            @Override
            // toggle video recording
            public void onClick(View v) {
                if(!recording){
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
                            mButton.setText("Stop");
                            //mButton.setBackgroundColor(Color.RED);
                            mButton.getBackground().setColorFilter(Color.RED, PorterDuff.Mode.MULTIPLY);
                        }

                    }, 500); // 5000ms delay
                    //timer.start();
                }
                else{
                    mMediaRecorder.stop();
                    mMediaRecorder.reset();
                    recording = false;
                    //timer.cancel();
                    //resterendeText.setVisibility(View.INVISIBLE);
                    mButton.setText("Start");
                    mButton.getBackground().setColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY);
                }
            }
        });

        /*timer = new CountDownTimer((getIntent().getIntExtra("delay", 10)*1000), 1000) {

            public void onTick(long millisUntilFinished) {
                resterendeText.setText("Seconden resterend: " + millisUntilFinished / 1000);
                resterendeText.setVisibility(View.VISIBLE);
            }

            public void onFinish() {
                resterendeText.setVisibility(View.INVISIBLE);
            }
        };*/

        // we shall take the video in landscape orientation

        mSurfaceView = findViewById(R.id.surfaceView);
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }


    @Override
    public boolean onTouchEvent(MotionEvent e)
    {
        final int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (e.getPointerCount() == 2) {
                    // We have four fingers touching, so start the timer
                    twoFingerDownTime = System.currentTimeMillis();
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                final long now = System.currentTimeMillis();
                if (now - twoFingerDownTime > FIVE_SECONDS && twoFingerDownTime != -1) {
                    // Two fingers have been down for 5 seconds!
                    // TODO Do something

                }
                if (e.getPointerCount() < 2) {
                    // Fewer than four fingers, so reset the timer
                    twoFingerDownTime = -1;
                }
                break;
            }
        }
        return true;
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
        VideoCapture t = new VideoCapture();
        t.startVideoRecording(getIntent().getIntExtra("delay", 10)); //seconds
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        //mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
        mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_720P));
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
        mInitSuccesful = true;
    }

    private void initCamera(){
        List<Camera.Size> mSupportedPreviewSizes;
        Camera.Size mPreviewSize;
        mCamera = CameraHelper.getDefaultFrontFacingCameraInstance();
        mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;
        mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        mCamera.setParameters(parameters);
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio=(double)h / w;

        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
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

    public class VideoCapture extends Activity implements MediaRecorder.OnInfoListener {

        public void startVideoRecording(int delay) {
            // Normal MediaRecorder Setup
            mMediaRecorder.setMaxDuration(delay * 1000);
            mMediaRecorder.setOnInfoListener(this);
        }

        public void onInfo(MediaRecorder mr, int what, int extra) {
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                Log.v("VIDEOCAPTURE","Maximum Duration Reached");
                mButton.callOnClick();
            }
        }
    }
}