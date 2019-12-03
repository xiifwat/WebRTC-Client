package io.openvidu.openvidu_android.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.EglBase;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.io.IOException;
import java.util.Map;

import io.openvidu.openvidu_android.R;
import io.openvidu.openvidu_android.constants.JsonConstants;
import io.openvidu.openvidu_android.openvidu.LocalParticipant;
import io.openvidu.openvidu_android.openvidu.RemoteParticipant;
import io.openvidu.openvidu_android.openvidu.Session;
import io.openvidu.openvidu_android.utils.CallManager;
import io.openvidu.openvidu_android.utils.CustomHttpClient;
import io.openvidu.openvidu_android.utils.MessageEvent;
import io.openvidu.openvidu_android.websocket.CustomWebSocket;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CallFragment extends Fragment {
    private final String TAG = "SessionActivity";
    private TextView tvInfo;
    private View rootView;
    private CustomHttpClient httpClient;
    private AudioManager audioManager;
    private SurfaceViewRenderer localVideoView, remote1, remote2, remote3;

    private String OPENVIDU_URL;
    private String OPENVIDU_SECRET;

    private ImageView btnCall;
    private TextView btnToggleCamera, btnToggleCallMode, btnToggleMic;

    private CallManager mCallManager;
    private Context mContext;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.fragment_call, container, false);
        mContext = getActivity().getApplicationContext();

        initializeView();

        getAudioManager().setSpeakerphoneOn(true);
        getAudioManager().setMicrophoneMute(false);

        if (!CallManager.hasInstance()) {

            mCallManager = CallManager.getInstance();

            String callMode = getArguments() == null ? JsonConstants.MODE_VIDEO_CALL : getArguments().getString("call_mode");
            String SESSION_NAME = getArguments() == null ? "abc" : getArguments().getString("sessionID");
            String PARTICIPANT_NAME = android.os.Build.MODEL;

            mCallManager.setCallMode(callMode);
            mCallManager.setSESSION_NAME(SESSION_NAME);
            mCallManager.setPARTICIPANT_NAME(PARTICIPANT_NAME);
            mCallManager.setOnCall(false);

            mCallManager.setLocalVideoView(localVideoView);

            initializePtt();
        } else {
            // Already a call is ongoing
            mCallManager = CallManager.getInstance();

            if(mCallManager.getSession().remoteParticipantCount()==0) {
                tvInfo.setText(getString(R.string.text_waiting));
                tvInfo.setVisibility(View.VISIBLE);
            } else {
                tvInfo.setVisibility(View.GONE);
                // local participant
                initLocalVideoView();

                // remote participants
                Map<String, RemoteParticipant> remoteParticipants = mCallManager.getSession().getRemoteParticipants();
                //views_container.removeAllViews();

                for (RemoteParticipant remoteParticipant : remoteParticipants.values()) {
                    SurfaceViewRenderer temp = getRemoteVideoView(remoteParticipant);
                    remoteParticipant.swap(temp);
                }

                if (mCallManager.getCallMode().equals(JsonConstants.MODE_VIDEO_CALL)) {
                    mCallManager.getLocalParticipant().showStream(localVideoView);
                }

            }
        }

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        mCallManager.setAppVisible(true);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
        mCallManager.setAppVisible(false);

//        mCallManager.getSession().zzz();
    }

    private void initializeView() {
        localVideoView = rootView.findViewById(R.id.local_participant);
        remote1 = rootView.findViewById(R.id.remote_participant_1);
        remote2 = rootView.findViewById(R.id.remote_participant_2);
        remote3 = rootView.findViewById(R.id.remote_participant_3);
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

    @Override
    public void onDestroy() {
        super.onDestroy();

        localVideoView.release ();
    }

    private void initializePtt() {
        if (arePermissionGranted()) {
            initLocalVideoView();

            OPENVIDU_URL = getString(R.string.default_openvidu_url);
            OPENVIDU_SECRET = getString(R.string.default_openvidu_secret);
            httpClient = new CustomHttpClient(OPENVIDU_URL, "Basic " + android.util.Base64.encodeToString(("OPENVIDUAPP:" + OPENVIDU_SECRET).getBytes(), android.util.Base64.DEFAULT).trim());

            String sessionId = mCallManager.getSESSION_NAME();
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
        Session session = new Session(sessionId, token, mContext);
        mCallManager.setSession(session);

        // Initialize our local participant and createRemoteParticipantVideostart local camera
        String participantName = mCallManager.getPARTICIPANT_NAME();
        mCallManager.setSessionLive(true);

        LocalParticipant localParticipant;

        if (mCallManager.getCallMode().equals(JsonConstants.MODE_VIDEO_CALL)) {
            localParticipant = new LocalParticipant(participantName, session, mContext, localVideoView, mCallManager.getCallMode());
            localParticipant.startCamera(true);
            localParticipant.showStream(localVideoView);
            localParticipant.toggleCapture(true);
        } else {
            localParticipant = new LocalParticipant(participantName, session, mContext, null, mCallManager.getCallMode());
            localParticipant.startCamera(true);
            localParticipant.toggleCapture(false);
        }
        localParticipant.enableAudioInput(true);

        mCallManager.setLocalParticipant(localParticipant);

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
        CustomWebSocket webSocket = new CustomWebSocket(mCallManager.getSession(),
                OPENVIDU_URL, mCallManager, mCallManager.getCallMode());
        webSocket.execute();
        mCallManager.getSession().setWebSocket(webSocket);
    }

    private void getTokenFailed() {
        Runnable myRunnable = () -> {
            Toast.makeText(
                    requireContext(),
                    "Error connecting to " + OPENVIDU_URL,
                    Toast.LENGTH_LONG).show();
            mCallManager.setSessionLive(false);

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
        if (audioManager == null)
            audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        return audioManager;
    }

    private void onClick(View view) {
        switch (view.getId()) {
            case R.id.iv_call:
                if (!mCallManager.isSessionLive()) {
                    Toast.makeText(requireContext(), "Session destroyed", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (mCallManager.isOnCall()) {
                    mCallManager.leaveSession();
                    mCallManager.setOnCall(false);
                } else {
                    mCallManager.switchViewToDisconnectedState();
                }
                break;

            case R.id.btn_toggle_av:
                Log.d(TAG, "toggle a/v");
                boolean isVideoStreaming = mCallManager.isVideoStreaming();

                String msg = String.format("Turned %s video streaming", isVideoStreaming ? "off" : "on");

                mCallManager.getLocalParticipant().toggleCapture(!isVideoStreaming);
                isVideoStreaming = !isVideoStreaming;

                mCallManager.setVideoStreaming(isVideoStreaming);

                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                break;

            case R.id.btn_toggle_mic:
                Log.d(TAG, "toggle mic");
                boolean isAudioStreaming = mCallManager.isAudioStreaming();

                msg = String.format("Turned %s audio streaming", isAudioStreaming ? "off" : "on");

                mCallManager.getLocalParticipant().enableAudioInput(!isAudioStreaming);
                isAudioStreaming = !isAudioStreaming;

                mCallManager.setAudioStreaming(isAudioStreaming);

                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                break;

            case R.id.btn_toggle_camera:
                Log.d(TAG, "toggle speaker");
                boolean isFrontCamera = mCallManager.isFrontCamera();

                msg = String.format("Turned %s loud speaker", isFrontCamera ? "off" : "on");

                getAudioManager().setSpeakerphoneOn(!isFrontCamera);
                isFrontCamera = !isFrontCamera;

                mCallManager.setFrontCamera(isFrontCamera);

                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private SurfaceViewRenderer getRemoteVideoView(RemoteParticipant remoteParticipant) {
        SurfaceViewRenderer videoView;

        switch (mCallManager.getSession().remoteParticipantCount()) {
            case 1:
                videoView = remote1;
                remote1.setVisibility(View.VISIBLE);
                break;

            case 2:
                videoView = remote2;
                remote2.setVisibility(View.VISIBLE);
                break;

            default:
                videoView = remote3;
                remote3.setVisibility(View.VISIBLE);
        }
        remoteParticipant.setVideoView(videoView);
        videoView.setMirror(false);

        EglBase rootEglBase = EglBase.create();
        videoView.init(rootEglBase.getEglBaseContext(), null);
        videoView.setZOrderMediaOverlay(true);

        return videoView;
    }

    public void preparePip(boolean pipEntered) {
        if(pipEntered) {
            // hide controls
            rootView.findViewById(R.id.ll_controller).setVisibility(View.GONE);
            btnCall.setVisibility(View.GONE);
        } else {
            // show controls
            rootView.findViewById(R.id.ll_controller).setVisibility(View.VISIBLE);
            btnCall.setVisibility(View.VISIBLE);
        }
    }

    // ---- EventBus event handling ----

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageEvent event) {
        int message = event.getCode();
        Log.d(TAG, "Got message: " + message);

        switch (message) {
            case -1:
                Log.d(TAG, "switchViewToDisconnectedState");
                requireActivity().finish();
                break;

            case 0:
                Log.d(TAG, "roomJoined");

                tvInfo.setText(getString(R.string.text_waiting));
                mCallManager.setOnCall(true);
                break;

            case 1:
                RemoteParticipant remoteParticipant = event.getRemoteParticipant();
                getRemoteVideoView(remoteParticipant);

                if (tvInfo.getVisibility() == View.VISIBLE)
                    tvInfo.setVisibility(View.GONE);

                break;

            case 2:
                Log.d(TAG, "rp left");

                Handler mainHandler = new Handler(Looper.getMainLooper());
                Runnable myRunnable = () -> {

                    int participantCount = mCallManager.getSession().remoteParticipantCount();

                    if (participantCount == 0) {
                        LocalParticipant localParticipant = mCallManager.getLocalParticipant();

                        if (mCallManager.getCallMode().equals(JsonConstants.MODE_VIDEO_CALL))
                            localParticipant.removeStream(localParticipant.getVideoView());
                        mCallManager.setOnCall(false);
                        mCallManager.leaveSession();
                    } else {
                        SurfaceViewRenderer view = event.getRemoteParticipant().getVideoView();
                        view.setVisibility(View.GONE);
                        view.release();
                    }
                };
                mainHandler.post(myRunnable);
                break;

            case 3:

                VideoTrack videoTrack = event.getRemoteMediaStream().videoTracks.get(0);
                videoTrack.addSink(event.getRemoteParticipant().getVideoView());

                event.getRemoteParticipant().setVideoTrack(videoTrack);
                // zzz
                event.getRemoteParticipant().setMediaStream(event.getRemoteMediaStream());

                new Handler(Looper.getMainLooper()).post(() -> {
                    Log.d(TAG, "adding video...");
                    event.getRemoteParticipant().getVideoView().setVisibility(View.VISIBLE);
                });
                break;

            case 4:
                // connection failed
                Toast.makeText(requireContext(), "Connection failed", Toast.LENGTH_LONG).show();
                mCallManager.leaveSession();
                break;
        }
    }

}