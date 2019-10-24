package io.openvidu.openvidu_android.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.nex3z.flowlayout.FlowLayout;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.EglBase;
import org.webrtc.MediaStream;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.io.IOException;

import io.openvidu.openvidu_android.OpenViduApp;
import io.openvidu.openvidu_android.R;
import io.openvidu.openvidu_android.observers.WsConnectionListener;
import io.openvidu.openvidu_android.openvidu.LocalParticipant;
import io.openvidu.openvidu_android.openvidu.RemoteParticipant;
import io.openvidu.openvidu_android.openvidu.Session;
import io.openvidu.openvidu_android.utils.CustomHttpClient;
import io.openvidu.openvidu_android.websocket.CustomWebSocket;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CallFragment extends Fragment implements WsConnectionListener {
    private final String TAG = "SessionActivity";
    private FlowLayout views_container;
    private SurfaceViewRenderer localVideoView;
    private TextView tvInfo;
    private View rootView;

    private String OPENVIDU_URL;
    private String OPENVIDU_SECRET;
    private String PARTICIPANT_NAME;
    private String SESSION_NAME;
    private Session session;
    private CustomHttpClient httpClient;
    private AudioManager audioManager;
    private LocalParticipant localParticipant;

    private ImageView btnCall;
    private TextView btnToggleCamera, btnToggleCallMode, btnToggleMic;
    private boolean isSessionLive = false;

    private boolean isVideoStreaming = true;
    private boolean isAudioStreaming = true;
    private boolean isFrontCamera = true;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.fragment_call, container, false);

        initializeView();

        SESSION_NAME = getArguments()==null ? "abc" : getArguments().getString("data");
        PARTICIPANT_NAME = android.os.Build.MODEL;


        getAudioManager().setSpeakerphoneOn(true);
        getAudioManager().setMicrophoneMute(false);

        initializePtt();

        return rootView;
    }

    private void initializeView() {
        views_container = rootView.findViewById(R.id.views_container);
        localVideoView = rootView.findViewById(R.id.local_participant);
        tvInfo = rootView.findViewById(R.id.textView2);

        btnCall = rootView.findViewById(R.id.iv_call);
        btnToggleCallMode = rootView.findViewById(R.id.btn_toggle_av);
        btnToggleCamera = rootView.findViewById(R.id.btn_toggle_camera);
        btnToggleMic = rootView.findViewById(R.id.btn_toggle_mic);

        btnCall.setOnClickListener(this::onClick);
        btnToggleCallMode.setOnClickListener(this::onClick);
        btnToggleCamera.setOnClickListener(this::onClick);
        btnToggleMic.setOnClickListener(this::onClick);
    }

    /*private void startPtt() {
        audioManager.setMicrophoneMute(false);
    }

    private void stopPtt() {
        audioManager.setMicrophoneMute(true);
    }

    private void startPtv() {
        audioManager.setMicrophoneMute(false);
        localParticipant.toggleCapture(false);
    }

    private void stopPtv() {
        audioManager.setMicrophoneMute(true);
        localParticipant.toggleCapture(true);
    }*/

    private void initializePtt() {
        if (arePermissionGranted()) {
            initLocalVideoView();

            OPENVIDU_URL = getString(R.string.default_openvidu_url);
            OPENVIDU_SECRET = getString(R.string.default_openvidu_secret);
            httpClient = new CustomHttpClient(OPENVIDU_URL, "Basic " + android.util.Base64.encodeToString(("OPENVIDUAPP:" + OPENVIDU_SECRET).getBytes(), android.util.Base64.DEFAULT).trim());

            String sessionId = SESSION_NAME;
            getToken(sessionId);
        } else {
            DialogFragment permissionsFragment = new PermissionsDialogFragment();
            permissionsFragment.show(getChildFragmentManager(), "Permissions Fragment");
        }
    }

    private void getToken(String sessionId) {
        try {
            // Session Request
            RequestBody sessionBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "{\"customSessionId\": \"" + sessionId + "\"}");
            this.httpClient.httpCall("/api/sessions", "POST", "application/json", sessionBody, new Callback() {

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                    Log.d(TAG, "responseString: " + response.body().string());

                    // Token Request
                    RequestBody tokenBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "{\"session\": \"" + sessionId + "\"}");
                    httpClient.httpCall("/api/tokens", "POST", "application/json", tokenBody, new Callback() {

                        @Override
                        public void onResponse(@NotNull Call call, @NotNull Response response) {
                            String responseString = null;
                            try {
                                responseString = response.body().string();
                            } catch (IOException e) {
                                Log.e(TAG, "Error getting body", e);
                            }
                            Log.d(TAG, "responseString2: " + responseString);
                            JSONObject tokenJsonObject;
                            String token = null;
                            try {
                                tokenJsonObject = new JSONObject(responseString);
                                token = tokenJsonObject.getString("token");
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            getTokenSuccess(token, sessionId);
                        }

                        @Override
                        public void onFailure(@NotNull Call call, @NotNull IOException e) {
                            Log.e(TAG, "Error POST /api/tokens", e);
                            getTokenFailed();
                        }
                    });
                }

                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    Log.e(TAG, "Error POST /api/sessions", e);
                    getTokenFailed();
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "Error getting token", e);
            e.printStackTrace();
            getTokenFailed();
        }
    }

    private void getTokenSuccess(String token, String sessionId) {
        // Initialize our session
        session = new Session(sessionId, token, views_container, requireContext());

        // Initialize our local participant and createRemoteParticipantVideostart local camera
        String participantName = PARTICIPANT_NAME;
        localParticipant = new LocalParticipant(participantName, session, requireContext(), localVideoView);
        localParticipant.startCamera(true);
        localParticipant.toggleCapture(true);
        isSessionLive = true;

        requireActivity().runOnUiThread(() -> {
            // Update local participant view
//            main_participant.setText (PARTICIPANT_NAME);
//            main_participant.setPadding (20, 3, 20, 3);
            tvInfo.setText(token);
        });

        // Initialize and connect the websocket to OpenVidu Server
        startWebSocket();
    }

    private void startWebSocket() {
        CustomWebSocket webSocket = new CustomWebSocket(session, OPENVIDU_URL, this);
        webSocket.execute();
        session.setWebSocket(webSocket);
    }

    private void getTokenFailed() {
        Runnable myRunnable = () -> {
            Toast.makeText(
                    requireContext(),
                    "Error connecting to " + OPENVIDU_URL,
                    Toast.LENGTH_LONG).show();
            isSessionLive = false;

            //switchViewToDisconnectedState();
            requireActivity().finish();
        };
        new Handler(requireContext().getMainLooper()).post(myRunnable);
    }

    private void initLocalVideoView() {
        EglBase rootEglBase = EglBase.create();
        localVideoView.init(rootEglBase.getEglBaseContext(), null);
        localVideoView.setMirror(true);
        localVideoView.setEnableHardwareScaler(true);
        localVideoView.setZOrderMediaOverlay(false);
    }

    public void switchViewToDisconnectedState() {
        requireActivity().runOnUiThread(() -> {
            localVideoView.clearImage();
            localVideoView.release();

            Log.i(TAG, "disconnected");
            requireActivity().finish();
        });
    }


    public void leaveSession() {
        /*Thread thread = new Thread("leaveSession__Thread") {
            @Override
            public void run() {

            }
        };
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.setDaemon(true);
        thread.start();*/

        new Handler(requireContext().getMainLooper()).post(() -> {
            if (isSessionLive) {
                session.leaveSession();
                httpClient.dispose();
                isSessionLive = false;

                Log.d(TAG, "session left, http disposed");
            }

            switchViewToDisconnectedState();
        });
    }

    private boolean arePermissionGranted() {
        return (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_DENIED) &&
                (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_DENIED);
    }

    @Override
    public void onDestroyView() {
        //leaveSession();
        super.onDestroyView();
    }

    private AudioManager getAudioManager() {
        if(audioManager==null)
            audioManager = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);

        return audioManager;
    }

    private boolean onCall = false;

    private void onClick(View view) {
        switch (view.getId()) {
            case R.id.iv_call:
                if (!isSessionLive) {
                    Toast.makeText(requireContext(), "Session destroyed", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (onCall) {
                    leaveSession();
                    onCall = false;
                } else {
                    switchViewToDisconnectedState();
                }
                break;

            case R.id.btn_toggle_av:
                Log.d(TAG, "toggle a/v");
                String msg = String.format("Turned %s video streaming", isVideoStreaming ? "off" : "on");

                localParticipant.toggleCapture(!isVideoStreaming);
                isVideoStreaming = !isVideoStreaming;

                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                break;

            case R.id.btn_toggle_mic:
                Log.d(TAG, "toggle mic");
                msg = String.format("Turned %s audio streaming", isAudioStreaming ? "off" : "on");

                localParticipant.muteUnmuteMic(!isAudioStreaming);
                isAudioStreaming = !isAudioStreaming;

                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                break;

            case R.id.btn_toggle_camera:
                Log.d(TAG, "toggle speaker");
                msg = String.format("Turned %s loud speaker", isFrontCamera ? "off" : "on");

                getAudioManager().setSpeakerphoneOn(!isFrontCamera);
                isFrontCamera = !isFrontCamera;

                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    @Override
    public void onRoomJoined() {
        Log.i(TAG, "onRoomJoined");

        requireActivity().runOnUiThread(() -> {
            tvInfo.setText("Ringing...");
            onCall = true;
        });
    }

    @Override
    public void onRemoteParticipantLeft(RemoteParticipant remoteParticipant) {

        Handler mainHandler = new Handler(requireContext().getMainLooper());
        Runnable myRunnable = () -> {
            int participantCount = session.remoteParticipantCount();
            Log.d(TAG, "onRemoteParticipantLeft: " + participantCount);

            session.removeView(remoteParticipant.getView());

            if(participantCount==0) {
                leaveSession();
                onCall = false;
            }
        };
        mainHandler.post(myRunnable);
    }

    @Override
    public void onRemoteParticipantJoined(RemoteParticipant remoteParticipant) {
        Log.i(TAG, "onRemoteParticipantJoined");

        Handler mainHandler = new Handler(requireContext().getMainLooper());
        Runnable myRunnable = () -> {
            View rowView = this.getLayoutInflater().inflate(R.layout.peer_video, null);
            FlowLayout.LayoutParams lp = new LinearLayout.LayoutParams(OpenViduApp.cellSize, OpenViduApp.cellSize);
            rowView.setLayoutParams(lp);
            int rowId = View.generateViewId();
            rowView.setId(rowId);
            views_container.addView(rowView);
            SurfaceViewRenderer videoView = (SurfaceViewRenderer) ((ViewGroup) rowView).getChildAt(0);
            remoteParticipant.setVideoView(videoView);
            videoView.setMirror(false);
            EglBase rootEglBase = EglBase.create();
            videoView.init(rootEglBase.getEglBaseContext(), null);
            videoView.setZOrderMediaOverlay(true);
            View textView = ((ViewGroup) rowView).getChildAt(1);
            remoteParticipant.setParticipantNameText((TextView) textView);
            remoteParticipant.setView(rowView);

            rowView.setOnClickListener(view -> {
                Toast.makeText(requireContext(), "" + remoteParticipant.getParticipantName(), Toast.LENGTH_SHORT).show();
            });

            remoteParticipant.getParticipantNameText().setText(remoteParticipant.getParticipantName());
            remoteParticipant.getParticipantNameText().setPadding(20, 3, 20, 3);

            if(tvInfo.getVisibility()==View.VISIBLE)
                tvInfo.setVisibility(View.GONE);
        };
        mainHandler.post(myRunnable);
    }

    @Override
    public void onRemoteMediaStream(MediaStream remoteMediaStream, RemoteParticipant remoteParticipant) {
        Log.i(TAG, "onRemoteMediaStream");

        VideoTrack videoTrack = remoteMediaStream.videoTracks.get(0);
        videoTrack.addSink(remoteParticipant.getVideoView());

        new Handler(requireContext().getMainLooper()).post(() -> {
            Log.d(TAG, "adding video...");
            remoteParticipant.getVideoView().setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void onWsConnected() {
        //
    }

    @Override
    public void onWsConnectionFailed(String msg) {
        Log.i(TAG, "onWsConnectionFailed");

        Handler mainHandler = new Handler(requireContext().getMainLooper());
        Runnable myRunnable = () -> {
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
            leaveSession();
        };
        mainHandler.post(myRunnable);
    }
}
