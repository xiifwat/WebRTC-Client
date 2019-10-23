package io.openvidu.openvidu_android.observers;

import org.webrtc.MediaStream;

import io.openvidu.openvidu_android.openvidu.RemoteParticipant;

public interface WsConnectionListener {
    void onRoomJoined();
    void onRemoteParticipantLeft(RemoteParticipant remoteParticipant);
    void onRemoteParticipantJoined(RemoteParticipant remoteParticipant);
    void onRemoteMediaStream(MediaStream remoteMediaStream, RemoteParticipant remoteParticipant);

    void onWsConnected();
    void onWsConnectionFailed(String msg);
}
