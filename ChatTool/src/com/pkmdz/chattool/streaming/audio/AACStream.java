package com.pkmdz.chattool.streaming.audio;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.service.textservice.SpellCheckerService.Session;
import android.util.Log;

import com.pkmdz.chattool.SessionBuilder;
import com.pkmdz.chattool.streaming.rtmp.AACRTMPSender;
import com.pkmdz.chattool.streaming.rtmp.MediaCodecInputStream;

/**
 * A class for streaming AAC from the camera of an android device using RTP.
 * You should use a {@link Session} instantiated with {@link SessionBuilder} instead of using this class directly.
 * Call {@link #setDestinationAddress(InetAddress)}, {@link #setDestinationPorts(int)} and {@link #setAudioQuality(AudioQuality)}
 * to configure the stream. You can then call {@link #start()} to start the RTP stream.
 * Call {@link #stop()} to stop the stream.
 */
public class AACStream extends AudioStream {

	public final static String TAG = "AACStream";

	/** MPEG-4 Audio Object Types supported by ADTS. **/
	private static final String[] AUDIO_OBJECT_TYPES = {
		"NULL",							  // 0
		"AAC Main",						  // 1
		"AAC LC (Low Complexity)",		  // 2
		"AAC SSR (Scalable Sample Rate)", // 3
		"AAC LTP (Long Term Prediction)"  // 4	
	};

	/** There are 13 supported frequencies by ADTS. **/
	public static final int[] AUDIO_SAMPLING_RATES = {
		96000, // 0
		88200, // 1
		64000, // 2
		48000, // 3
		44100, // 4
		32000, // 5
		24000, // 6
		22050, // 7
		16000, // 8
		12000, // 9
		11025, // 10
		8000,  // 11
		7350,  // 12
		-1,   // 13
		-1,   // 14
		-1,   // 15
	};

	private String mSessionDescription = null;
	private int mProfile, mSamplingRateIndex, mChannel, mConfig;
	private SharedPreferences mSettings = null;
	private AudioRecord mAudioRecord = null;
	private Thread mThread = null;

	public AACStream() {
		super();

		if (!AACStreamingSupported()) {
			Log.e(TAG,"AAC not supported on this phone");
			throw new RuntimeException("AAC not supported by this phone !");
		} else {
			Log.d(TAG,"AAC supported on this phone");
		}

	}

	private static boolean AACStreamingSupported() {
		if (Build.VERSION.SDK_INT<14) return false;
		try {
			MediaRecorder.OutputFormat.class.getField("AAC_ADTS");
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Some data (the actual sampling rate used by the phone and the AAC profile) needs to be stored once {@link #getSessionDescription()} is called.
	 * @param prefs The SharedPreferences that will be used to store the sampling rate 
	 */
	public void setPreferences(SharedPreferences prefs) {
		mSettings = prefs;
	}

	@Override
	public synchronized void start() throws IllegalStateException, IOException {
		configure();
		if (!mStreaming) {
			super.start();
		}
	}

	public synchronized void configure() throws IllegalStateException, IOException {
		super.configure();
		mQuality = mRequestedQuality.clone();

		// Checks if the user has supplied an exotic sampling rate
		int i=0;
		for (;i<AUDIO_SAMPLING_RATES.length;i++) {
			if (AUDIO_SAMPLING_RATES[i] == mQuality.samplingRate) {
				mSamplingRateIndex = i;
				break;
			}
		}
		// If he did, we force a reasonable one: 16 kHz
		if (i>12) mQuality.samplingRate = 16000;
		mRTMPSender = new AACRTMPSender(mQuality.samplingRate);
	}


	@Override
	@SuppressLint({ "InlinedApi", "NewApi" })
	protected void encodeWithMediaCodec() throws IOException {

		final int bufferSize = AudioRecord.getMinBufferSize(mQuality.samplingRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)*2;

		//((AACLATMPacketizer)mPacketizer).setSamplingRate(mQuality.samplingRate);

		mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, mQuality.samplingRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
		mMediaCodec = MediaCodec.createEncoderByType("audio/mp4a-latm");
		MediaFormat format = new MediaFormat();
		format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
		format.setInteger(MediaFormat.KEY_BIT_RATE, mQuality.bitRate);
		format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
		format.setInteger(MediaFormat.KEY_SAMPLE_RATE, mQuality.samplingRate);
		format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
		format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize);
		mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mAudioRecord.startRecording();
		mMediaCodec.start();

		//final MediaCodecInputStream inputStream = new MediaCodecInputStream(mMediaCodec);
		final ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();

		mThread = new Thread(new Runnable() {
			//BufferInfo mBufferInfo = new BufferInfo();
			//boolean isSDCardExist = Environment.getExternalStorageState()
     		//		.equals(Environment.MEDIA_MOUNTED);
			//FileOutputStream outputStream =null;
			//boolean fileExists = true;
			//ByteBuffer[] mBuffers = mMediaCodec.getOutputBuffers();
			@Override
			public void run() {
				int len = 0, bufferIndex = 0;
				try {
					while (!Thread.interrupted()) {
						bufferIndex = mMediaCodec.dequeueInputBuffer(10000);
						if (bufferIndex>=0) {
							inputBuffers[bufferIndex].clear();
							len = mAudioRecord.read(inputBuffers[bufferIndex], bufferSize);
							if (len ==  AudioRecord.ERROR_INVALID_OPERATION || len == AudioRecord.ERROR_BAD_VALUE) {
								Log.e(TAG,"An error occured with the AudioRecord API !");
							} else {
								//Log.v(TAG,"Pushing raw audio to the decoder: len="+len+" bs: "+inputBuffers[bufferIndex].capacity());
								mMediaCodec.queueInputBuffer(bufferIndex, 0, len, System.nanoTime()/1000, 0);
							}
						}
						/*
						ByteBuffer outputBuffer = null;
						int mIndex = -1;
						while(true){
							mIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 500000);
							if(mIndex >= 0){
								outputBuffer = mBuffers[mIndex];
								outputBuffer.position(0);
								break;
							}
						}
						 int outBitsSize   = mBufferInfo.size;
						 int outPacketSize = outBitsSize + 7;    // 7 is ADTS size
						byte[] outData = new byte[outPacketSize];  
						addADTStoPacket(outData, outPacketSize);
				        outputBuffer.get(outData, 7, outBitsSize);
				             
				        if(isSDCardExist){
				        File file = new File(Environment.getExternalStorageDirectory(),
				            			 "audioTest.aac");
				        if(fileExists){
				        	fileExists = false;
				        	if(file.exists()){
				        		file.delete();
				        	}
				        }
				        try {
				        	outputStream = new FileOutputStream(file, true);
							outputStream.write(outData);
							outputStream.flush();
							} catch (IOException e) {
								e.printStackTrace();
							} finally{
								try {
									outputStream.close();
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
				           }
				     mMediaCodec.releaseOutputBuffer(mIndex, false);  
						
						*/
					}
				} catch (RuntimeException e) {
					e.printStackTrace();
				}
			}
		});

		mThread.start();
		
		
		mRTMPSender.setInputStream(new MediaCodecInputStream(mMediaCodec));
		mRTMPSender.start();
		// The packetizer encapsulates this stream in an RTP stream and send it over the network
		//mPacketizer.setDestination(mDestination, mRtpPort, mRtcpPort);
		//mPacketizer.setInputStream(inputStream);
		//mPacketizer.start();

		mStreaming = true;

	}

	/** Stops the stream. */
	public synchronized void stop() {
		if (mStreaming) {
			if (mMode==MODE_MEDIACODEC_API) {
				Log.d(TAG, "Interrupting threads...");
				mThread.interrupt();
				mAudioRecord.stop();
				mAudioRecord.release();
				mAudioRecord = null;
			}
			super.stop();
		}
	}

	/**
	 * Returns a description of the stream using SDP. It can then be included in an SDP file.
	 * Will fail if called when streaming.
	 */
	public String getSessionDescription() throws IllegalStateException {
		if (mSessionDescription == null) throw new IllegalStateException("You need to call configure() first !");
		return mSessionDescription;
	}


}
