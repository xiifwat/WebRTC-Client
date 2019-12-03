package io.openvidu.openvidu_android.activities;

import android.Manifest;
import android.app.PictureInPictureParams;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Rational;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import io.openvidu.openvidu_android.R;
import io.openvidu.openvidu_android.constants.JsonConstants;
import io.openvidu.openvidu_android.fragments.CallFragment;

public class PushToVideoActivity extends AppCompatActivity {
    public static final int MY_PERMISSIONS_REQUEST_CAMERA = 100;
    public static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 101;
    public static final int MY_PERMISSIONS_REQUEST = 102;    
    public static final String FR_TAG = "fr1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_push_to_video);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        String session1 = getIntent().getStringExtra("session1");
        String mode = getIntent().getStringExtra("mode");

        CallFragment f = new CallFragment();

        if (session1 != null && !session1.isEmpty()) {
            Bundle b = new Bundle();
            b.putString("sessionID", session1);
            b.putString("call_mode", mode);
            f.setArguments(b);
        }

        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.fr1, f, FR_TAG)
                .commit();
    }

    public void askForPermissions() {
        if ((ContextCompat.checkSelfPermission (this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission (this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions (this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    MY_PERMISSIONS_REQUEST);
        } else if (ContextCompat.checkSelfPermission (this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions (this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else if (ContextCompat.checkSelfPermission (this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions (this,
                    new String[]{Manifest.permission.CAMERA},
                    MY_PERMISSIONS_REQUEST_CAMERA);
        }
    }


    /*@Override
    public void onBackPressed() {
        if(*//*mCallManager.getCallMode().equals(JsonConstants.MODE_VIDEO_CALL) &&*//*
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                        getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            try {
                enablePictureInPicture();
            } catch (IllegalStateException e) {
                // device doesn't support PictureInPicture
                super.onBackPressed();
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        Log.i("pip", "onUserLeaveHint fired");

        try {
            enablePictureInPicture();
        } catch (IllegalStateException e) {
            // de2vice doesn't support PictureInPicture
            // do nothing
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        Log.i("pip", "onPictureInPictureModeChanged fired. isPip = " + isInPictureInPictureMode);

        if (isInPictureInPictureMode) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void enablePictureInPicture() throws IllegalStateException {
        // pip wont enable for audio call
        *//*if(mCallManager.getCallMode().equals(JsonConstants.MODE_AUDIO_CALL))
            return;*//*

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int width = getWindow().getDecorView().getWidth();
            int height = getWindow().getDecorView().getHeight();

            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(width, height)).build();
            enterPictureInPictureMode(params);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            enterPictureInPictureMode();
        }
    }

    private void showControls() {
        CallFragment fragment = (CallFragment) getSupportFragmentManager().findFragmentByTag(FR_TAG);
        if(fragment!=null) {
            fragment.preparePip(false);
        }
    }

    private void hideControls() {
        CallFragment fragment = (CallFragment) getSupportFragmentManager().findFragmentByTag(FR_TAG);
        if(fragment!=null) {
            fragment.preparePip(true);
        }
    }*/
}
