package io.openvidu.openvidu_android.websocket;

import android.os.AsyncTask;
import android.util.Log;

import com.neovisionaries.ws.client.ThreadType;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;
import com.neovisionaries.ws.client.WebSocketListener;
import com.neovisionaries.ws.client.WebSocketState;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.openvidu.openvidu_android.constants.JsonConstants;
import io.openvidu.openvidu_android.observers.CustomPeerConnectionObserver;
import io.openvidu.openvidu_android.observers.CustomSdpObserver;
import io.openvidu.openvidu_android.observers.WsConnectionListener;
import io.openvidu.openvidu_android.openvidu.LocalParticipant;
import io.openvidu.openvidu_android.openvidu.Participant;
import io.openvidu.openvidu_android.openvidu.RemoteParticipant;
import io.openvidu.openvidu_android.openvidu.Session;

public class CustomWebSocket extends AsyncTask<Void, Void, Void> implements WebSocketListener {

    private final String TAG = "CustomWebSocketListener";
    private final int PING_MESSAGE_INTERVAL = 5;
    private final TrustManager[] trustManagers = new TrustManager[]{new X509TrustManager() {
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
            Log.i(TAG, ": authType: " + authType);
        }

        @Override
        public void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
            Log.i(TAG, ": authType: " + authType);
        }
    }};
    private AtomicInteger RPC_ID = new AtomicInteger(0);
    private AtomicInteger ID_PING = new AtomicInteger(-1);
    private AtomicInteger ID_JOINROOM = new AtomicInteger(-1);
    private AtomicInteger ID_LEAVEROOM = new AtomicInteger(-1);
    private AtomicInteger ID_PUBLISHVIDEO = new AtomicInteger(-1);
    private AtomicInteger ID_UNPUBLISHVIDEO = new AtomicInteger(-1);
    private Map<Integer, String> IDS_RECEIVEVIDEO = new ConcurrentHashMap<>();
    private Set<Integer> IDS_ONICECANDIDATE = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Session session;
    private String openviduUrl;
    private WsConnectionListener mListener;
    private WebSocket websocket;
    private boolean websocketCancelled = false;
    private String callMode;

    public CustomWebSocket(Session session, String openviduUrl, WsConnectionListener mListener, String callMode) {
        this.session = session;
        this.openviduUrl = openviduUrl;
        this.mListener = mListener;
        this.callMode = callMode;
    }

    @Override
    public void onTextMessage(WebSocket websocket, String text) throws Exception {
        Log.i(TAG, "Text Message " + text);
        JSONObject json = new JSONObject(text);
        if (json.has(JsonConstants.RESULT)) {
            handleServerResponse(json);
        } else {
            handleServerEvent(json);
        }
    }

    private void handleServerResponse(JSONObject json) throws
            JSONException {
        final int rpcId = json.getInt(JsonConstants.ID);
        JSONObject result = new JSONObject(json.getString(JsonConstants.RESULT));

        if (result.has("value") && result.getString("value").equals("pong")) {
            // Response to ping
            Log.i(TAG, "pong");

        } else if (rpcId == this.ID_JOINROOM.get()) {
            // Response to joinRoom
            mListener.onRoomJoined();

            final LocalParticipant localParticipant = this.session.getLocalParticipant();
            final String localConnectionId = result.getString(JsonConstants.ID);
            localParticipant.setConnectionId(localConnectionId);

            PeerConnection localPeerConnection = session.createLocalPeerConnection();
            localParticipant.setPeerConnection(localPeerConnection);

            MediaStream stream = this.session.getPeerConnectionFactory().createLocalMediaStream("102");
            stream.addTrack(localParticipant.getAudioTrack());
            stream.addTrack(localParticipant.getVideoTrack());
            localParticipant.getPeerConnection().addStream(stream);

            MediaConstraints sdpConstraints = new MediaConstraints();
            sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
            sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"));
            session.createLocalOffer(sdpConstraints);

            if (result.getJSONArray(JsonConstants.VALUE).length() > 0) {
                // There were users already connected to the session
                addRemoteParticipantsAlreadyInRoom(result);
            }

        } else if (rpcId == this.ID_LEAVEROOM.get()) {
            // Response to leaveRoom
            if (websocket.isOpen()) {
                websocket.disconnect();
            }

        } else if (rpcId == this.ID_PUBLISHVIDEO.get()) {
            // Response to publishVideo
            SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, result.getString("sdpAnswer"));
            this.session.getLocalParticipant().getPeerConnection().setRemoteDescription(new CustomSdpObserver("localSetRemoteDesc"), sessionDescription);

        } else if (rpcId == this.ID_UNPUBLISHVIDEO.get()) {
            // Response to unpublishVideo
            //SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, result.getString("sdpAnswer"));
            //this.session.getLocalParticipant().getPeerConnection().setRemoteDescription(new CustomSdpObserver("localSetRemoteDesc"), sessionDescription);

        } else if (this.IDS_RECEIVEVIDEO.containsKey(rpcId)) {
            // Response to receiveVideoFrom
            SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, result.getString("sdpAnswer"));
            session.getRemoteParticipant(IDS_RECEIVEVIDEO.remove(rpcId)).getPeerConnection().setRemoteDescription(new CustomSdpObserver("remoteSetRemoteDesc"), sessionDescription);

        } else if (this.IDS_ONICECANDIDATE.contains(rpcId)) {
            // Response to onIceCandidate
            IDS_ONICECANDIDATE.remove(rpcId);

        } else {
            Log.e(TAG, "Unrecognized server response: " + result);
        }
    }

    public void joinRoom(String mode) {
        Map<String, String> joinRoomParams = new HashMap<>();
        joinRoomParams.put(JsonConstants.METADATA, "{\"clientData\": \"" + this.session.getLocalParticipant().getParticipantName() + "\",\"mode\":\""+mode+"\"}");
        joinRoomParams.put("secret", "");
        joinRoomParams.put("session", this.session.getId());
        joinRoomParams.put("platform", "Android " + android.os.Build.VERSION.SDK_INT);
        joinRoomParams.put("token", this.session.getToken());
        this.ID_JOINROOM.set(this.sendJson(JsonConstants.JOINROOM_METHOD, joinRoomParams));
    }

    public void leaveRoom() {
        this.ID_LEAVEROOM.set(this.sendJson(JsonConstants.LEAVEROOM_METHOD));
    }

    public void publishVideo(SessionDescription sessionDescription) {
        Map<String, String> publishVideoParams = new HashMap<>();
        publishVideoParams.put("audioActive", "true");
        publishVideoParams.put("videoActive", "true");
        publishVideoParams.put("doLoopback", "false");
        publishVideoParams.put("frameRate", "30");
        publishVideoParams.put("hasAudio", "true");
        publishVideoParams.put("hasVideo", "true");
        publishVideoParams.put("typeOfVideo", "CAMERA");
        publishVideoParams.put("videoDimensions", "{\"width\":320, \"height\":240}");
        publishVideoParams.put("sdpOffer", sessionDescription.description);
        this.ID_PUBLISHVIDEO.set(this.sendJson(JsonConstants.PUBLISHVIDEO_METHOD, publishVideoParams));
    }

    public void unpublishVideo() {
        this.ID_UNPUBLISHVIDEO.set(this.sendJson(JsonConstants.UNPUBLISHVIDEO_METHOD));
    }

    public void receiveVideoFrom(SessionDescription sessionDescription, RemoteParticipant remoteParticipant, String streamId) {
        Map<String, String> receiveVideoFromParams = new HashMap<>();
        receiveVideoFromParams.put("sdpOffer", sessionDescription.description);
        receiveVideoFromParams.put("sender", streamId);
        this.IDS_RECEIVEVIDEO.put(this.sendJson(JsonConstants.RECEIVEVIDEO_METHOD, receiveVideoFromParams), remoteParticipant.getConnectionId());
    }

    public void onIceCandidate(IceCandidate iceCandidate, String endpointName) {
        Map<String, String> onIceCandidateParams = new HashMap<>();
        if (endpointName != null) {
            onIceCandidateParams.put("endpointName", endpointName);
        }
        onIceCandidateParams.put("candidate", iceCandidate.sdp);
        onIceCandidateParams.put("sdpMid", iceCandidate.sdpMid);
        onIceCandidateParams.put("sdpMLineIndex", Integer.toString(iceCandidate.sdpMLineIndex));
        this.IDS_ONICECANDIDATE.add(this.sendJson(JsonConstants.ONICECANDIDATE_METHOD, onIceCandidateParams));
    }

    private void handleServerEvent(JSONObject json) throws JSONException {
        if (!json.has(JsonConstants.PARAMS)) {
            Log.e(TAG, "No params " + json.toString());
        } else {
            final JSONObject params = new JSONObject(json.getString(JsonConstants.PARAMS));
            String method = json.getString(JsonConstants.METHOD);
            switch (method) {
                case JsonConstants.ICE_CANDIDATE:
                    iceCandidateEvent(params);
                    break;
                case JsonConstants.PARTICIPANT_JOINED:
                    participantJoinedEvent(params);
                    break;
                case JsonConstants.PARTICIPANT_PUBLISHED:
                    participantPublishedEvent(params);
                    break;
                case JsonConstants.PARTICIPANT_LEFT:
                    participantLeftEvent(params);
                    break;
                default:
                    throw new JSONException("Unknown method: " + method);
            }
        }
    }

    public int sendJson(String method) {
        return this.sendJson(method, new HashMap<>());
    }

    public synchronized int sendJson(String method, Map<String, String> params) {
        final int id = RPC_ID.get();
        JSONObject jsonObject = new JSONObject();
        try {
            JSONObject paramsJson = new JSONObject();
            for (Map.Entry<String, String> param : params.entrySet()) {
                paramsJson.put(param.getKey(), param.getValue());
            }
            jsonObject.put("jsonrpc", JsonConstants.JSON_RPCVERSION);
            jsonObject.put("method", method);
            jsonObject.put("id", id);
            jsonObject.put("params", paramsJson);
        } catch (JSONException e) {
            Log.i(TAG, "JSONException raised on sendJson", e);
            return -1;
        }
        this.websocket.sendText(jsonObject.toString());
        RPC_ID.incrementAndGet();
        return id;
    }

    private void addRemoteParticipantsAlreadyInRoom(JSONObject result) throws
            JSONException {
        for (int i = 0; i < result.getJSONArray(JsonConstants.VALUE).length(); i++) {
            JSONObject participantJson = result.getJSONArray(JsonConstants.VALUE).getJSONObject(i);
            RemoteParticipant remoteParticipant = this.newRemoteParticipantAux(participantJson);
            JSONArray streams = participantJson.getJSONArray("streams");
            for (int j = 0; j < streams.length(); j++) {
                JSONObject stream = streams.getJSONObject(0);
                String streamId = stream.getString("id");
                this.subscribeAux(remoteParticipant, streamId);
            }
        }
    }

    private void iceCandidateEvent(JSONObject params) throws JSONException {
        IceCandidate iceCandidate = new IceCandidate(params.getString("sdpMid"), params.getInt("sdpMLineIndex"), params.getString("candidate"));
        final String connectionId = params.getString("senderConnectionId");
        boolean isRemote = !session.getLocalParticipant().getConnectionId().equals(connectionId);
        final Participant participant = isRemote ? session.getRemoteParticipant(connectionId) : session.getLocalParticipant();
        final PeerConnection pc = participant.getPeerConnection();

        switch (pc.signalingState()) {
            case CLOSED:
                Log.e("saveIceCandidate error", "PeerConnection object is closed");
                break;
            case STABLE:
                if (pc.getRemoteDescription() != null) {
                    participant.getPeerConnection().addIceCandidate(iceCandidate);
                } else {
                    participant.getIceCandidateList().add(iceCandidate);
                }
                break;
            default:
                participant.getIceCandidateList().add(iceCandidate);
        }
    }

    private void participantJoinedEvent(JSONObject params) throws JSONException {
        this.newRemoteParticipantAux(params);
        Log.d("SessionActivity", "participantJoinedEvent");
    }

    private void participantPublishedEvent(JSONObject params) throws
            JSONException {
        String remoteParticipantId = params.getString(JsonConstants.ID);
        final RemoteParticipant remoteParticipant = this.session.getRemoteParticipant(remoteParticipantId);
        final String streamId = params.getJSONArray("streams").getJSONObject(0).getString("id");
        this.subscribeAux(remoteParticipant, streamId);
    }

    private void participantLeftEvent(JSONObject params) throws JSONException {
        Log.d("SessionActivity", "participantLeftEvent");
        final RemoteParticipant remoteParticipant = this.session.removeRemoteParticipant(params.getString("connectionId"));
        remoteParticipant.dispose();

        mListener.onRemoteParticipantLeft(remoteParticipant);
    }

    private RemoteParticipant newRemoteParticipantAux(JSONObject participantJson) throws JSONException {
        final String connectionId = participantJson.getString(JsonConstants.ID);
        final String participantName = new JSONObject(participantJson.getString(JsonConstants.METADATA)).getString("clientData");
        final String mode = new JSONObject (participantJson.getString(JsonConstants.METADATA)).getString("mode");
        final RemoteParticipant remoteParticipant = new RemoteParticipant(connectionId, participantName, this.session, mode);
        //((CallFragment) this.fra).createRemoteParticipantVideo(remoteParticipant);

        mListener.onRemoteParticipantJoined(remoteParticipant);
        createRemotePeerConnection(remoteParticipant.getConnectionId()); // this
        return remoteParticipant;
    }

    public void createRemotePeerConnection(final String connectionId) {
        final List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        PeerConnection.IceServer iceServer = PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer();
        iceServers.add(iceServer);

        PeerConnection peerConnection = session.getPeerConnectionFactory().createPeerConnection(iceServers, new CustomPeerConnectionObserver("remotePeerCreation") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);

                CustomWebSocket.this.onIceCandidate(iceCandidate, connectionId);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                super.onAddStream(mediaStream);
                Log.d("SessionActivity", "onAddStream");

                mListener.onRemoteMediaStream(mediaStream, session.getRemoteParticipant(connectionId));
            }

            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                if (PeerConnection.SignalingState.STABLE.equals(signalingState)) {
                    final RemoteParticipant remoteParticipant = session.getRemoteParticipant(connectionId);
                    Iterator<IceCandidate> it = remoteParticipant.getIceCandidateList().iterator();
                    while (it.hasNext()) {
                        IceCandidate candidate = it.next();
                        remoteParticipant.getPeerConnection().addIceCandidate(candidate);
                        it.remove();
                    }
                }
            }
        });

        MediaStream mediaStream = session.getPeerConnectionFactory().createLocalMediaStream("105");
        mediaStream.addTrack(session.getLocalParticipant().getAudioTrack());
        mediaStream.addTrack(session.getLocalParticipant().getVideoTrack());
        peerConnection.addStream(mediaStream);

        session.getRemoteParticipant(connectionId).setPeerConnection(peerConnection);
    }

    private void subscribeAux(RemoteParticipant remoteParticipant, String streamId) {
        remoteParticipant.getPeerConnection().createOffer(new CustomSdpObserver("remote offer sdp") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                remoteParticipant.getPeerConnection().setLocalDescription(new CustomSdpObserver("remoteSetLocalDesc"), sessionDescription);
                receiveVideoFrom(sessionDescription, remoteParticipant, streamId);
            }

            @Override
            public void onCreateFailure(String s) {
                Log.e("createOffer error", s);
            }
        }, new MediaConstraints());
    }

    public void setWebsocketCancelled(boolean websocketCancelled) {
        this.websocketCancelled = websocketCancelled;
    }

    public void disconnect() {
        this.websocket.disconnect();
    }

    @Override
    public void onStateChanged(WebSocket websocket, WebSocketState newState) throws Exception {
        Log.i(TAG, "State changed: " + newState.name());
    }

    @Override
    public void onConnected(WebSocket ws, Map<String, List<String>> headers) throws
            Exception {
        Log.i(TAG, "Connected");
        pingMessageHandler();
        this.joinRoom(callMode);
    }

    @Override
    public void onConnectError(WebSocket websocket, WebSocketException cause) throws Exception {
        Log.e(TAG, "Connect error: " + cause);
    }

    @Override
    public void onDisconnected(WebSocket websocket, WebSocketFrame
            serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
        Log.e(TAG, "Disconnected " + serverCloseFrame.getCloseReason() + " " + clientCloseFrame.getCloseReason() + " " + closedByServer);
    }

    @Override
    public void onFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Frame");
    }

    @Override
    public void onContinuationFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Continuation Frame");
    }

    @Override
    public void onTextFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Text Frame");
    }

    @Override
    public void onBinaryFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Binary Frame");
    }

    @Override
    public void onCloseFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Close Frame");
    }

    @Override
    public void onPingFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Ping Frame");
    }

    @Override
    public void onPongFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Pong Frame");
    }

    @Override
    public void onTextMessage(WebSocket websocket, byte[] data) throws Exception {

    }

    @Override
    public void onBinaryMessage(WebSocket websocket, byte[] binary) throws Exception {
        Log.i(TAG, "Binary Message");
    }

    @Override
    public void onSendingFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Sending Frame");
    }

    @Override
    public void onFrameSent(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Frame sent");
    }

    @Override
    public void onFrameUnsent(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Frame unsent");
    }

    @Override
    public void onThreadCreated(WebSocket websocket, ThreadType threadType, Thread thread) throws
            Exception {
        Log.i(TAG, "Thread created");
    }

    @Override
    public void onThreadStarted(WebSocket websocket, ThreadType threadType, Thread thread) throws
            Exception {
        Log.i(TAG, "Thread started");
    }

    @Override
    public void onThreadStopping(WebSocket websocket, ThreadType threadType, Thread thread) throws
            Exception {
        Log.i(TAG, "Thread stopping");
    }

    @Override
    public void onError(WebSocket websocket, WebSocketException cause) throws Exception {
        Log.i(TAG, "Error!");
    }

    @Override
    public void onFrameError(WebSocket websocket, WebSocketException cause, WebSocketFrame
            frame) throws Exception {
        Log.i(TAG, "Frame error!");
    }

    @Override
    public void onMessageError(WebSocket websocket, WebSocketException
            cause, List<WebSocketFrame> frames) throws Exception {
        Log.i(TAG, "Message error! " + cause);
    }

    @Override
    public void onMessageDecompressionError(WebSocket websocket, WebSocketException cause,
                                            byte[] compressed) throws Exception {
        Log.i(TAG, "Message decompression error!");
    }

    @Override
    public void onTextMessageError(WebSocket websocket, WebSocketException cause, byte[] data) throws
            Exception {
        Log.i(TAG, "Text message error! " + cause);
    }

    @Override
    public void onSendError(WebSocket websocket, WebSocketException cause, WebSocketFrame frame) throws
            Exception {
        Log.i(TAG, "Send error! " + cause);
    }

    @Override
    public void onUnexpectedError(WebSocket websocket, WebSocketException cause) throws
            Exception {
        Log.i(TAG, "Unexpected error! " + cause);
    }

    @Override
    public void handleCallbackError(WebSocket websocket, Throwable cause) throws Exception {
        Log.e(TAG, "Handle callback error! " + cause);
    }

    @Override
    public void onSendingHandshake(WebSocket websocket, String requestLine, List<String[]>
            headers) throws Exception {
        Log.i(TAG, "Sending Handshake! Hello!");
    }

    private void pingMessageHandler() {
        long initialDelay = 0L;
        ScheduledThreadPoolExecutor executor =
                new ScheduledThreadPoolExecutor(1);
        executor.scheduleWithFixedDelay(() -> {
            Map<String, String> pingParams = new HashMap<>();
            if (ID_PING.get() == -1) {
                // First ping call
                pingParams.put("interval", "5000");
            }
            ID_PING.set(sendJson(JsonConstants.PING_METHOD, pingParams));
        }, initialDelay, PING_MESSAGE_INTERVAL, TimeUnit.SECONDS);
    }

    private String getWebSocketAddress(String openviduUrl) {
        try {
            URL url = new URL(openviduUrl);
            return "wss://" + url.getHost() + ":" + url.getPort() + "/openvidu";
//            return "https://193.147.51.58/openvidu";
        } catch (MalformedURLException e) {
            Log.e(TAG, "Wrong URL", e);
            e.printStackTrace();
            return "";
        }
    }

    @Override
    protected Void doInBackground(Void... sessionActivities) {
        try {
            WebSocketFactory factory = new WebSocketFactory();
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, new java.security.SecureRandom());
            factory.setSSLContext(sslContext);
            factory.setVerifyHostname(false);

            websocket = factory.createSocket(getWebSocketAddress(openviduUrl));
            websocket.addListener(this);
            websocket.connect();

            mListener.onWsConnected();
        } catch (KeyManagementException | NoSuchAlgorithmException | IOException | WebSocketException e) {

            Log.e("WebSocket error", e.getMessage());
            mListener.onWsConnectionFailed(e.getMessage());
            websocketCancelled = true;
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(Void... progress) {
        Log.i(TAG, "PROGRESS " + Arrays.toString(progress));
    }

}
