package io.openvidu.openvidu_android.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.nex3z.flowlayout.FlowLayout;

import org.greenrobot.eventbus.EventBus;
import org.webrtc.MediaStream;
import org.webrtc.SurfaceViewRenderer;

import java.lang.ref.WeakReference;

import io.openvidu.openvidu_android.observers.WsConnectionListener;
import io.openvidu.openvidu_android.openvidu.LocalParticipant;
import io.openvidu.openvidu_android.openvidu.RemoteParticipant;
import io.openvidu.openvidu_android.openvidu.Session;

public class CallManager implements WsConnectionListener {
    private static WeakReference<CallManager> instance = null;
    private static WeakReference<Context> mContext;

    private String PARTICIPANT_NAME;
    private String SESSION_NAME;
    private Session session;
    private FlowLayout views_container;
    private SurfaceViewRenderer localVideoView;
    private LocalParticipant localParticipant;

    private boolean isSessionLive = false;

    private boolean isVideoStreaming = true;
    private boolean isAudioStreaming = true;
    private boolean isFrontCamera = true;

    private boolean onCall = false;
    private boolean isAppVisible = false;
    private String callMode;
    private String TAG = "ov~CallManager";

    public static CallManager getInstance(Context context) {
        if (instance == null) {
            instance = new WeakReference<>(new CallManager());
            mContext = new WeakReference<>(context);
        }
        return instance.get();
    }

    private void clearInstance() {
        mContext = null;
        instance.clear();
        instance = null;
    }

    public static boolean hasInstance() {
        return instance != null;
    }

    public String getPARTICIPANT_NAME() {
        return PARTICIPANT_NAME;
    }

    public void setPARTICIPANT_NAME(String PARTICIPANT_NAME) {
        this.PARTICIPANT_NAME = PARTICIPANT_NAME;
    }

    public String getSESSION_NAME() {
        return SESSION_NAME;
    }

    public void setSESSION_NAME(String SESSION_NAME) {
        this.SESSION_NAME = SESSION_NAME;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public FlowLayout getViews_container() {
        return views_container;
    }

    public void setViews_container(FlowLayout views_container) {
        this.views_container = views_container;
    }

    public SurfaceViewRenderer getLocalVideoView() {
        return localVideoView;
    }

    public void setLocalVideoView(SurfaceViewRenderer localVideoView) {
        this.localVideoView = localVideoView;
    }

    public LocalParticipant getLocalParticipant() {
        return localParticipant;
    }

    public void setLocalParticipant(LocalParticipant localParticipant) {
        this.localParticipant = localParticipant;
    }

    public boolean isSessionLive() {
        return isSessionLive;
    }

    public void setSessionLive(boolean sessionLive) {
        isSessionLive = sessionLive;
    }

    public boolean isVideoStreaming() {
        return isVideoStreaming;
    }

    public void setVideoStreaming(boolean videoStreaming) {
        isVideoStreaming = videoStreaming;
    }

    public boolean isAudioStreaming() {
        return isAudioStreaming;
    }

    public void setAudioStreaming(boolean audioStreaming) {
        isAudioStreaming = audioStreaming;
    }

    public boolean isFrontCamera() {
        return isFrontCamera;
    }

    public void setFrontCamera(boolean frontCamera) {
        isFrontCamera = frontCamera;
    }

    public String getCallMode() {
        return callMode;
    }

    public void setCallMode(String callMode) {
        this.callMode = callMode;
    }

    public boolean isOnCall() {
        return onCall;
    }

    public void setOnCall(boolean onCall) {
        this.onCall = onCall;
    }

    public boolean isAppVisible() {
        return isAppVisible;
    }

    public void setAppVisible(boolean appVisible) {
        isAppVisible = appVisible;
    }

    public void switchViewToDisconnectedState() {

        new Handler(Looper.getMainLooper()).post(() -> {
            localVideoView.clearImage();
            localVideoView.release();

            Log.i(TAG, "disconnected");

            EventBus.getDefault().post(new MessageEvent(-1, null, null));

            instance.get().clearInstance();
        });
    }

    public void leaveSession() {

        new Handler(Looper.getMainLooper()).post(() -> {
            if (instance.get().isSessionLive()) {
                instance.get().getSession().leaveSession();
                //httpClient.dispose(); //TODO
                instance.get().setSessionLive(false);

                Log.d(TAG, "session left, http disposed");
            }

            instance.get().switchViewToDisconnectedState();
        });
    }

    @Override
    public void onRoomJoined() {
        Log.i(TAG, "onRoomJoined");

        EventBus.getDefault().post(new MessageEvent(0, null, null));
    }

    @Override
    public void onRemoteParticipantLeft(RemoteParticipant remoteParticipant) {
        Log.d(TAG, "onRemoteParticipantLeft: ");

        EventBus.getDefault().post(new MessageEvent(2, remoteParticipant, null));

        int participantCount = instance.get().getSession().remoteParticipantCount();
        if(!instance.get().isAppVisible() && participantCount==0) {
            //instance.leaveSession();
            new Handler(Looper.getMainLooper()).post(() -> {
                instance.get().getSession().leaveSessionMinimal();
                instance.get().clearInstance();
            });
        }
    }

    @Override
    public void onRemoteParticipantJoined(RemoteParticipant remoteParticipant) {
        Log.i(TAG, "onRemoteParticipantJoined. Mode: " + remoteParticipant.getResourceType());

        EventBus.getDefault().post(new MessageEvent(1, remoteParticipant, null));
    }

    @Override
    public void onRemoteMediaStream(MediaStream remoteMediaStream, RemoteParticipant remoteParticipant) {
        Log.i(TAG, "onRemoteMediaStream. Mode: " + remoteParticipant.getResourceType());

        EventBus.getDefault().post(new MessageEvent(3, remoteParticipant, remoteMediaStream));
    }

    @Override
    public void onWsConnected() {
        //
    }

    @Override
    public void onWsConnectionFailed(String msg) {
        Log.i(TAG, "onWsConnectionFailed");

        EventBus.getDefault().post(new MessageEvent(4, null, null));

        if(!instance.get().isAppVisible()) {
            new Handler(Looper.getMainLooper()).post(() -> {
                instance.get().getSession().leaveSessionMinimal();
                instance.get().clearInstance();
            });
        }
    }
}
