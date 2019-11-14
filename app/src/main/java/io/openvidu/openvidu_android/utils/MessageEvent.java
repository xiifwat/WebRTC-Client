package io.openvidu.openvidu_android.utils;

import org.webrtc.MediaStream;

import io.openvidu.openvidu_android.openvidu.RemoteParticipant;

public class MessageEvent {
    private int code;
    private RemoteParticipant remoteParticipant;
    private MediaStream remoteMediaStream;

    public MessageEvent(int code, RemoteParticipant remoteParticipant, MediaStream remoteMediaStream) {
        this.code = code;
        this.remoteParticipant = remoteParticipant;
        this.remoteMediaStream = remoteMediaStream;
    }

    public int getCode() {
        return code;
    }

    public RemoteParticipant getRemoteParticipant() {
        return remoteParticipant;
    }

    public MediaStream getRemoteMediaStream() {
        return remoteMediaStream;
    }
}
