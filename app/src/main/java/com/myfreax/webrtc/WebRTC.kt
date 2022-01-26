package com.myfreax.webrtc

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import org.webrtc.*
import org.webrtc.DataChannel
import org.webrtc.MediaStream
import org.webrtc.VideoTrack
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection.*
import org.webrtc.PeerConnectionFactory
import org.webrtc.VideoSink
import java.util.ArrayList
import org.webrtc.VideoCapturer
import org.webrtc.SurfaceTextureHelper
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.VideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.VideoEncoderFactory

class WebRTCActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity"
        const val VIDEO_RESOLUTION_WIDTH = 1280
        const val VIDEO_RESOLUTION_HEIGHT = 720
        const val FPS = 30
    }

    private var videoTrackFromCamera: VideoTrack? = null
    private val localPeerConnection: PeerConnection? = null
    private var remotePeerConnection: PeerConnection? = null

    private val localRenderer by lazy {
        findViewById<SurfaceViewRenderer>(R.id.local_renderer)
    }

    private val remoteRenderer by lazy {
        findViewById<SurfaceViewRenderer>(R.id.remote_renderer)
    }

    private val eglBase by lazy {
        EglBase.create()
    }

    private class ProxyVideoSink : VideoSink {
        private var target: VideoSink? = null

        @Synchronized
        override fun onFrame(frame: VideoFrame) {
            if (target == null) {
                Log.d(TAG, "Dropping frame in proxy because target is null.")
                return
            }
            target!!.onFrame(frame)
        }

        @Synchronized
        fun setTarget(target: VideoSink?) {
            this.target = target
        }
    }

    private val localPeerConnectionObserver = object : Observer {
        override fun onSignalingChange(p0: SignalingState?) {
            Log.d(TAG, "onSignalingChange")
        }

        override fun onIceConnectionChange(p0: IceConnectionState?) {
            Log.d(TAG, "onIceConnectionChange")
        }

        override fun onIceConnectionReceivingChange(p0: Boolean) {
            Log.d(TAG, "onIceConnectionReceivingChange")
        }

        override fun onIceGatheringChange(p0: IceGatheringState?) {
            Log.d(TAG, "onIceGatheringChange")
        }

        override fun onIceCandidate(iceCandidate: IceCandidate?) {
            Log.d(TAG, "onIceCandidate")
            remotePeerConnection?.addIceCandidate(iceCandidate)
        }

        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
            Log.d(TAG, "onIceCandidatesRemoved")
        }

        override fun onAddStream(mediaStream: MediaStream?) {
            Log.d(TAG, "onAddStream: " + mediaStream?.videoTracks?.size)
            val remoteVideoTrack: VideoTrack? = mediaStream?.videoTracks?.get(0)
            remoteVideoTrack?.setEnabled(true)
            val remoteVideoSink = ProxyVideoSink()
            videoTrackFromCamera?.addSink(remoteVideoSink)
            remoteVideoSink.setTarget(remoteRenderer)
        }

        override fun onRemoveStream(p0: MediaStream?) {
            Log.d(TAG, "onRemoveStream")
        }

        override fun onDataChannel(p0: DataChannel?) {
            Log.d(TAG, "onDataChannel")
        }

        override fun onRenegotiationNeeded() {
            Log.d(TAG, "onRenegotiationNeeded")
        }

        override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
            Log.d(TAG, "onAddTrack")
        }
    }

    private val remotePeerConnectionObserver = object : Observer {
        override fun onSignalingChange(p0: SignalingState?) {
            Log.d(TAG, "onSignalingChange")
        }

        override fun onIceConnectionChange(p0: IceConnectionState?) {
            Log.d(TAG, "onIceConnectionChange")
        }

        override fun onIceConnectionReceivingChange(p0: Boolean) {
            Log.d(TAG, "onIceConnectionReceivingChange")
        }

        override fun onIceGatheringChange(p0: IceGatheringState?) {
            Log.d(TAG, "onIceGatheringChange")
        }

        override fun onIceCandidate(iceCandidate: IceCandidate?) {
            Log.d(TAG, "onIceCandidate")
            localPeerConnection?.addIceCandidate(iceCandidate)
        }

        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
            Log.d(TAG, "onIceCandidatesRemoved")
        }

        override fun onAddStream(mediaStream: MediaStream?) {
            Log.d(TAG, "onAddStream: " + mediaStream?.videoTracks?.size)
            val remoteVideoTrack: VideoTrack? = mediaStream?.videoTracks?.get(0)
            remoteVideoTrack?.setEnabled(true)
            val remoteVideoSink = ProxyVideoSink()
            videoTrackFromCamera?.addSink(remoteVideoSink)
            remoteVideoSink.setTarget(remoteRenderer)
        }

        override fun onRemoveStream(p0: MediaStream?) {
            Log.d(TAG, "onRemoveStream")
        }

        override fun onDataChannel(p0: DataChannel?) {
            Log.d(TAG, "onDataChannel")
        }

        override fun onRenegotiationNeeded() {
            Log.d(TAG, "onRenegotiationNeeded")
        }

        override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
            Log.d(TAG, "onAddTrack")
        }
    }

    private val simpleSdpObserver = object : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {
            Log.d(TAG, "onCreateSuccess")
        }

        override fun onSetSuccess() {
            Log.d(TAG, "onSetSuccess")
        }

        override fun onCreateFailure(p0: String?) {
            Log.d(TAG, "onCreateFailure")
        }

        override fun onSetFailure(p0: String?) {
            Log.d(TAG, "onSetFailure")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // configure local surfaceView renderer
        localRenderer.init(eglBase.eglBaseContext, null)
        localRenderer.setEnableHardwareScaler(true)
        localRenderer.setMirror(true)

        // configure remote surfaceView renderer
        remoteRenderer.init(eglBase.eglBaseContext, null)
        remoteRenderer.setEnableHardwareScaler(true)
        remoteRenderer.setMirror(true)

        //create peerConnection factory
        val option = PeerConnectionFactory.InitializationOptions.builder(applicationContext)
        PeerConnectionFactory.initialize(option.createInitializationOptions())
        val encoderFactory: VideoEncoderFactory =
            DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory: VideoDecoderFactory =
            DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        val options = PeerConnectionFactory.Options()
        val factory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoDecoderFactory(decoderFactory)
            .setVideoEncoderFactory(encoderFactory)
            .createPeerConnectionFactory()

        //create video track and show local renderer
        val videoCapturer = createVideoCapturer()
        val videoSource: VideoSource = factory.createVideoSource(videoCapturer.isScreencast)

        // CameraCapturer must be initialized before calling startCapture
        val surfaceTextureHelper =
            SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        videoCapturer.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)
        videoCapturer.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS)
        videoTrackFromCamera = factory.createVideoTrack("ARDAMSv0", videoSource)
        videoTrackFromCamera?.setEnabled(true)
        val localVideoSink = ProxyVideoSink()
        videoTrackFromCamera?.addSink(localVideoSink)
        localVideoSink.setTarget(localRenderer)

        // create local remote peerConnections
        val rtcConfig = RTCConfiguration(ArrayList())
        val localPeerConnection =
            factory.createPeerConnection(rtcConfig, localPeerConnectionObserver)
        val remotePeerConnection =
            factory.createPeerConnection(rtcConfig, remotePeerConnectionObserver)

        // start stream video
        val mediaStream = factory.createLocalMediaStream("ARDAMS")
        mediaStream.addTrack(videoTrackFromCamera)
        localPeerConnection?.addStream(mediaStream)
        //Session Description protocol (SDP) [RFC4566] is used for negotiating session capabilities between the peers
        val mediaConstraints = MediaConstraints()

        //localPeerConnection create offer
        localPeerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(offerSessionDescription: SessionDescription?) {
                Log.d(TAG, "localPeerConnection createOffer onCreateSuccess")
                localPeerConnection.setLocalDescription(simpleSdpObserver, offerSessionDescription)
                remotePeerConnection?.setRemoteDescription(
                    simpleSdpObserver,
                    offerSessionDescription
                )

                remotePeerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(anwserSessionDescription: SessionDescription?) {
                        Log.d(TAG, "remotePeerConnection createAnswer onCreateSuccess")
                        localPeerConnection.setRemoteDescription(
                            simpleSdpObserver,
                            anwserSessionDescription
                        )
                        remotePeerConnection.setLocalDescription(
                            simpleSdpObserver,
                            anwserSessionDescription
                        )
                    }

                    override fun onSetSuccess() {
                        Log.d(TAG, "remotePeerConnection createAnswer onSetSuccess")
                    }

                    override fun onCreateFailure(errorMsg: String?) {
                        Log.d(TAG, "remotePeerConnection createAnswer onCreateFailure: $errorMsg")
                    }

                    override fun onSetFailure(p0: String?) {
                        Log.d(TAG, "remotePeerConnection createAnswer onSetFailure")
                    }
                }, mediaConstraints)
            }

            override fun onSetSuccess() {
                Log.d(TAG, "localPeerConnection createOffer onSetSuccess")
            }

            override fun onCreateFailure(p0: String?) {
                Log.d(TAG, "localPeerConnection createOffer onCreateFailure")
            }

            override fun onSetFailure(p0: String?) {
                Log.d(TAG, "localPeerConnection createOffer onSetFailure")
            }
        }, mediaConstraints)
    }

    private fun createVideoCapturer(): VideoCapturer {
        val camera2Enumerator = Camera2Enumerator(this)
        val facingCamera = camera2Enumerator.deviceNames.find {
            camera2Enumerator.isFrontFacing(it)
        }
        return camera2Enumerator.createCapturer(facingCamera, null)
    }

}