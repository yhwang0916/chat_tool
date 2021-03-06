

package com.pkmdz.chattool;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Random;

import com.pkmdz.chattool.streaming.audio.AudioStream;
import com.pkmdz.chattool.streaming.rtmp.AbstractRTMPSender;
import com.pkmdz.chattool.streaming.video.VideoStream;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

/**
 * A MediaRecorder that streams what it records using a packetizer from the rtp package.
 * You can't use this class directly !
 */
public abstract class MediaStream implements Stream {

	protected static final String TAG = "MediaStream";

	/** Raw audio/video will be encoded using the MediaRecorder API. */
	public static final byte MODE_MEDIARECORDER_API = 0x01;

	/** Raw audio/video will be encoded using the MediaCodec API with buffers. */
	public static final byte MODE_MEDIACODEC_API = 0x02;

	/** Raw audio/video will be encoded using the MediaCode API with a surface. */
	public static final byte MODE_MEDIACODEC_API_2 = 0x05;

	/** Prefix that will be used for all shared preferences saved by libstreaming */
	protected static final String PREF_PREFIX = "libstreaming-";


	protected static byte sSuggestedMode = MODE_MEDIARECORDER_API;
	protected byte mMode, mRequestedMode;

	protected boolean mStreaming = false, mConfigured = false;
	
	protected MediaCodec mMediaCodec;
	
	protected AbstractRTMPSender mRTMPSender;
	
	static {
		// We determine wether or not the MediaCodec API should be used
		try {
			Class.forName("android.media.MediaCodec");
			// Will be set to MODE_MEDIACODEC_API at some point...
			sSuggestedMode = MODE_MEDIACODEC_API;
			Log.i(TAG,"Phone supports the MediaCoded API");
		} catch (ClassNotFoundException e) {
		}
	}

	public MediaStream() {
		mRequestedMode = sSuggestedMode;
		mMode = sSuggestedMode;
	}
	

	/**
	 * Sets the streaming method that will be used.
	 * 
	 * If the mode is set to {@link #MODE_MEDIARECORDER_API}, raw audio/video will be encoded 
	 * using the MediaRecorder API. <br />
	 * 
	 * If the mode is set to {@link #MODE_MEDIACODEC_API} or to {@link #MODE_MEDIACODEC_API_2}, 
	 * audio/video will be encoded with using the MediaCodec. <br />
	 * 
	 * The {@link #MODE_MEDIACODEC_API_2} mode only concerns {@link VideoStream}, it makes 
	 * use of the createInputSurface() method of the MediaCodec API (Android 4.3 is needed there). <br />
	 * 
	 * @param mode Can be {@link #MODE_MEDIARECORDER_API}, {@link #MODE_MEDIACODEC_API} or {@link #MODE_MEDIACODEC_API_2} 
	 */
	public void setStreamingMethod(byte mode) {
		mRequestedMode = mode;
	}

	

	

	/**
	 * Indicates if the {@link MediaStream} is streaming.
	 * @return A boolean indicating if the {@link MediaStream} is streaming
	 */
	public boolean isStreaming() {
		return mStreaming;
	}

	/**
	 * Configures the stream with the settings supplied with 
	 * {@link VideoStream#setVideoQuality(net.majorkernelpanic.streaming.video.VideoQuality)}
	 * for a {@link VideoStream} and {@link AudioStream#setAudioQuality(net.majorkernelpanic.streaming.audio.AudioQuality)}
	 * for a {@link AudioStream}.
	 */
	public synchronized void configure() throws IllegalStateException, IOException {
		if (mStreaming) throw new IllegalStateException("Can't be called while streaming.");
		mMode = mRequestedMode;
		mConfigured = true;
	}
	
	/** Starts the stream. */
	public synchronized void start() throws IllegalStateException, IOException {

		
		if (mMode != MODE_MEDIARECORDER_API) {
			encodeWithMediaCodec();
		} 

	}

	/** Stops the stream. */
	@SuppressLint("NewApi") 
	public synchronized  void stop() {
		if (mStreaming) {
			try {
				
					mMediaCodec.stop();
					mMediaCodec.release();
					mMediaCodec = null;
					mRTMPSender.stop();
				
			} catch (Exception e) {
				e.printStackTrace();
			}	
			mStreaming = false;
		}
	}
 

	protected abstract void encodeWithMediaCodec() throws IOException;
	
	
}
