package io.openvidu.openvidu_android.openvidu;

import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;

import org.webrtc.SurfaceViewRenderer;

public class RemoteParticipant extends Participant {

    private View view;
    private SurfaceViewRenderer videoView;
    private TextView participantNameText;
    private String resourceType;

    public RemoteParticipant(String connectionId, String participantName, Session session, String resourceType) {
        super(connectionId, participantName, session, resourceType);
        this.session.addRemoteParticipant(this);
        this.resourceType = resourceType;
    }

    public View getView() {
        return this.view;
    }

    public void setView(View view) {
        this.view = view;
    }

    public SurfaceViewRenderer getVideoView() {
        return this.videoView;
    }

    public void setVideoView(SurfaceViewRenderer videoView) {
        this.videoView = videoView;
    }

    public void swap(SurfaceViewRenderer newVideoView) {
        this.videoTrack.removeSink(this.videoView);

        this.videoView = newVideoView;

        this.videoTrack.addSink(this.videoView);
    }

    public TextView getParticipantNameText() {
        return this.participantNameText;
    }

    public void setParticipantNameText(TextView participantNameText) {
        this.participantNameText = participantNameText;
    }

    @Override
    public void dispose() {
        super.dispose();
    }

    @Override
    public String getResourceType() {
        return resourceType;
    }
}
