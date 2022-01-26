package com.myfreax.webrtc

import android.app.*
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.os.IBinder
import android.util.Log
import android.view.Surface
import java.io.File
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.media.MediaCodec.CodecException
import android.view.Display
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastSession
import java.lang.RuntimeException
import kotlin.properties.Delegates

class CastScreenService : Service() {
    companion object {
        const val VIDEO_MIME_TYPE = "video/avc"
        const val NOTIFICATION_ID = 441823
        const val TAG = "CastScreenService"
        var resultCode by Delegates.notNull<Int>();
        lateinit var intent: Intent
        const val NOTIFICATION_CHANNEL_ID = "com.github.screenLive.app"
        const val NOTIFICATION_CHANNEL_NAME = "com.github.screenLive.app"

        fun start(context: Context, permissionResultCode: Int, permissionIntent: Intent) {
            resultCode = permissionResultCode
            intent = permissionIntent
            val intent = Intent(context, CastScreenService::class.java)
            context.startForegroundService(intent)
        }
    }

    private val mediaProjection by lazy {
        mediaProjectionManager.getMediaProjection(resultCode, intent)
    }

    private val mediaProjectionManager by lazy {
        getSystemService(
            MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager
    }
    private var trackIndex = -1
    private var inputSurface: Surface? = null
    private val mediaCodec by lazy {
        MediaCodec.createEncoderByType(VIDEO_MIME_TYPE)
    }
    private var mediaMuxerStarted = false
    private val mediaMuxer by lazy {
        MediaMuxer(
            File(
                this.getExternalFilesDir(
                    Environment.DIRECTORY_PICTURES + "/grafika"
                ).toString()
            ).canonicalPath + "Screen-record-" +
                    java.lang.Long.toHexString(System.currentTimeMillis()) + ".mp4",
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )
    }

    private val encoderCallback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            Log.d(TAG, "Input Buffer Avail")
        }

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            index: Int,
            info: MediaCodec.BufferInfo
        ) {
            val encodedData = mediaCodec.getOutputBuffer(index)
                ?: throw RuntimeException("couldn't fetch buffer at index $index")

            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                info.size = 0
            }
            if (info.size != 0 && mediaMuxerStarted) {
                encodedData.position(info.offset)
                encodedData.limit(info.offset + info.size)
                mediaMuxer.writeSampleData(trackIndex, encodedData, info)
            }
            mediaCodec.releaseOutputBuffer(index, false)
        }

        override fun onError(codec: MediaCodec, e: CodecException) {
            Log.e(TAG, "MediaCodec " + codec.name + " onError:", e)
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            Log.d(TAG, "Output Format changed")
            if (trackIndex >= 0) {
                throw RuntimeException("format changed twice")
            }
            trackIndex = mediaMuxer.addTrack(mediaCodec.outputFormat)
            if (!mediaMuxerStarted) {
                mediaMuxer.start()
                mediaMuxerStarted = true
            }
        }
    }

    /**
     * create notification
     */
    private val notification by lazy {
        Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_camera)
            .setContentTitle(this.getString(R.string.app_name))
            .setContentText(this.getString(R.string.recording))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setShowWhen(true)
            .build()
    }

    private fun videoFormat(frameRate: Int = 30): MediaFormat {
        val format = MediaFormat.createVideoFormat(
            VIDEO_MIME_TYPE,
            resources.displayMetrics.widthPixels,
            resources.displayMetrics.heightPixels
        )
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, 6000000) // 6Mbps
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        format.setInteger(MediaFormat.KEY_CAPTURE_RATE, frameRate)
        format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / frameRate)
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1 seconds between I-frames
        return format
    }

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val manager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind")
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        createNotification()
        startRecording()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun play(castSession: CastSession) {
        val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
        val mediaInfo =
            MediaInfo.Builder("http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType("videos/mp4")
                .setMetadata(movieMetadata)
                .setStreamDuration(596)
                .build()
        val remoteMediaClient = castSession.remoteMediaClient
        //remoteMediaClient.
        remoteMediaClient?.load(
            MediaLoadRequestData.Builder().setAutoplay(true).setMediaInfo(mediaInfo).build()
        )
    }

    private fun startRecording() {
        (getSystemService(DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
            ?: throw RuntimeException("No display found.")

        //prepare Video Encoder
        mediaCodec.configure(videoFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = mediaCodec.createInputSurface()
        mediaCodec.setCallback(encoderCallback)
        mediaCodec.start()

        // Start the video input.
        mediaProjection.createVirtualDisplay(
            "Recording Display",
            resources.displayMetrics.widthPixels,
            resources.displayMetrics.heightPixels,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR /* flags */,
            inputSurface,
            null /* callback */,
            null /* handler */
        )
    }

    private fun releaseEncoders() {
        if (mediaMuxerStarted) {
            mediaMuxer.stop()
        }
        mediaMuxer.release()
        mediaMuxerStarted = false
        mediaCodec.stop()
        mediaCodec.release()
        inputSurface?.release()
        inputSurface = null
        mediaProjection.stop()
        trackIndex = -1
    }

    private fun createNotification() {
        createNotificationChannel(this)
        val notificationManager = this.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            NOTIFICATION_ID,
            notification
        )
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseEncoders()
        Log.d(TAG, "onDestroy")
    }
}

