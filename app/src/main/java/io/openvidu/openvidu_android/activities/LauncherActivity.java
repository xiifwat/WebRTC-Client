package io.openvidu.openvidu_android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import io.openvidu.openvidu_android.R;
import io.openvidu.openvidu_android.constants.JsonConstants;
import io.openvidu.openvidu_android.utils.CallManager;

public class LauncherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(CallManager.hasInstance()) {
            findViewById(R.id.button).setEnabled(false);
            findViewById(R.id.button2).setEnabled(false);
            findViewById(R.id.button3).setEnabled(true);
        } else {
            findViewById(R.id.button).setEnabled(true);
            findViewById(R.id.button2).setEnabled(true);
            findViewById(R.id.button3).setEnabled(false);
        }
    }

    public void makeVideoCall(View view) {
        EditText et1 = findViewById(R.id.et1);

        String session1 = et1.getText().toString().trim();
        if (session1.isEmpty()) {
            Toast.makeText(this, "Session name empty", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, PushToVideoActivity.class);
        intent.putExtra("session1", session1);
        intent.putExtra("mode", JsonConstants.MODE_VIDEO_CALL);
        startActivity(intent);
    }

    public void makeAudioCall(View view) {
        EditText et1 = findViewById(R.id.et1);

        String session1 = et1.getText().toString().trim();
        if (session1.isEmpty()) {
            Toast.makeText(this, "Session name empty", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, PushToVideoActivity.class);
        intent.putExtra("session1", session1);
        intent.putExtra("mode", JsonConstants.MODE_AUDIO_CALL);
        startActivity(intent);
    }

    public void joinRunningCall(View view) {
        Intent intent = new Intent(this, PushToVideoActivity.class);
        startActivity(intent);
    }
}
