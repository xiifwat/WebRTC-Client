package io.openvidu.openvidu_android.openvidu;

import android.content.Context;
import android.os.Build;

import androidx.annotation.Nullable;

import org.webrtc.AudioSource;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;

import java.util.ArrayList;
import java.util.Collection;

public class LocalParticipant extends Participant {

    private Context context;
    private SurfaceViewRenderer localVideoView;
    private SurfaceTextureHelper surfaceTextureHelper;
    private VideoCapturer videoCapturer;

    private Collection<IceCandidate> localIceCandidates;
    private SessionDescription localSessionDescription;
    private String resourceType;

    public LocalParticipant(String participantName, Session session, Context context, @Nullable SurfaceViewRenderer localVideoView, String resourceType) {
        super(participantName, session, resourceType);
        //this.localVideoView = localVideoView;
        this.context = context;
        this.participantName = participantName;
        this.localIceCandidates = new ArrayList<>();
        this.resourceType = resourceType;
        session.setLocalParticipant(this);
    }

    public void startCamera(boolean isFrontCamera) {

        final EglBase.Context eglBaseContext = EglBase.create().getEglBaseContext();
        PeerConnectionFactory peerConnectionFactory = this.session.getPeerConnectionFactory();

        // create AudioSource
        AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        this.audioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);
        this.audioTrack.setEnabled(false);

        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext);
        // create VideoCapturer
        VideoCapturer videoCapturer = createCameraCapturer(isFrontCamera);
        VideoSource videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
//        videoCapturer.startCapture(480, 640, 30);
        try {
            videoCapturer.stopCapture ();
//            audioSource.state ();
        } catch (InterruptedException e) {
            e.printStackTrace ();
        }

        // create VideoTrack
        this.videoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);
    }

    public void showStream(SurfaceViewRenderer videoView) {
        this.localVideoView = videoView;
        this.videoTrack.addSink(videoView);
    }

    public void removeStream(SurfaceViewRenderer videoView) {
        this.videoTrack.removeSink(videoView);
    }

    public void swap(SurfaceViewRenderer newVideoView) {
        this.videoTrack.removeSink(this.localVideoView);

        this.localVideoView = newVideoView;

        this.videoTrack.addSink(this.localVideoView);
    }

    private VideoCapturer createCameraCapturer(boolean isFrontCamera) {
        CameraEnumerator enumerator;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            enumerator = new Camera2Enumerator(this.context);
        } else {
            enumerator = new Camera1Enumerator(false);
        }
        final String[] deviceNames = enumerator.getDeviceNames();

        // Try to find front facing camera
        if(isFrontCamera) {
            for (String deviceName : deviceNames) {
                if (enumerator.isFrontFacing(deviceName)) {
                    videoCapturer = enumerator.createCapturer(deviceName, null);
                    if (videoCapturer != null) {
                        return videoCapturer;
                    }
                }
            }
        }
        // Front facing camera not found, try something else
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
    }

    public void storeIceCandidate(IceCandidate iceCandidate) {
        localIceCandidates.add(iceCandidate);
    }

    public Collection<IceCandidate> getLocalIceCandidates() {
        return this.localIceCandidates;
    }

    public void storeLocalSessionDescription(SessionDescription sessionDescription) {
        localSessionDescription = sessionDescription;
    }

    public SessionDescription getLocalSessionDescription() {
        return this.localSessionDescription;
    }

    @Override
    public void dispose() {
        super.dispose();
        if (videoTrack != null) {
            videoCapturer.dispose();
            videoCapturer = null;
        }
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }
    }

    public void toggleCapture(boolean allowCapturing){
        if(allowCapturing) {
            videoCapturer.startCapture (480, 640, 30);
        } else {
            try {
                videoCapturer.stopCapture ();
            } catch (InterruptedException e) {
                e.printStackTrace ();
            }
        }
    }

    public void enableAudioInput(boolean enable) {
        /*if(flag && !audioTrack.enabled())
            audioTrack.setEnabled(true);
        else if(!flag && audioTrack.enabled())
            audioTrack.setEnabled(false);*/
        audioTrack.setEnabled(enable);
    }

    @Override
    public String getResourceType() {
        return resourceType;
    }

    public SurfaceViewRenderer getVideoView() {
        return this.localVideoView;
    }
}
