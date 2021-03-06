
package com.pkmdz.chattool;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.SurfaceView;

import com.pkmdz.chattool.streaming.audio.AudioQuality;
import com.pkmdz.chattool.streaming.audio.AudioStream;
import com.pkmdz.chattool.streaming.exceptions.CameraInUseException;
import com.pkmdz.chattool.streaming.exceptions.ConfNotSupportedException;
import com.pkmdz.chattool.streaming.exceptions.InvalidSurfaceException;
import com.pkmdz.chattool.streaming.exceptions.StorageUnavailableException;
import com.pkmdz.chattool.streaming.video.VideoQuality;
import com.pkmdz.chattool.streaming.video.VideoStream;


public class Session {

	public final static String TAG = "Session";

	public final static int STREAM_VIDEO = 0x01;

	public final static int STREAM_AUDIO = 0x00;

	/** Some app is already using a camera (Camera.open() has failed). */
	public final static int ERROR_CAMERA_ALREADY_IN_USE = 0x00;

	/** The phone may not support some streaming parameters that you are using (bit rate, frame rate...s). */
	public final static int ERROR_CONFIGURATION_NOT_SUPPORTED = 0x01;

	/** 
	 * The internal storage of the phone is not ready. 
	 * Libstreaming tried to store a test file on the sdcard but couldn't.
	 * See H264Stream and AACStream to find out why libstreaming would want to something like that. 
	 */
	public final static int ERROR_STORAGE_NOT_READY = 0x02;

	/** The phone has no flash. */
	public final static int ERROR_CAMERA_HAS_NO_FLASH = 0x03;

	/** The supplied SurfaceView is not a valid surface, or has not been created yet. */
	public final static int ERROR_INVALID_SURFACE = 0x04;

	/** 
	 * The destination set with {@link Session#setDestination(String)} could not be resolved. 
	 * May mean that the phone has no access to the internet, or that the DNS server could not
	 * resolved the host name.
	 */
	public final static int ERROR_UNKNOWN_HOST = 0x05;

	/**
	 * Some other error occured !
	 */
	public final static int ERROR_OTHER = 0x06;

	private String mDestination;

	private AudioStream mAudioStream = null;
	private VideoStream mVideoStream = null;

	private Callback mCallback;
	private Handler mMainHandler;
	
	private static CountDownLatch sSignal;
	private static Handler sHandler;

	static {
		// Creates the Thread that will be used when asynchronous methods of a Session are called
		sSignal = new CountDownLatch(1);
		new HandlerThread("net.majorkernelpanic.streaming.Session"){
			@Override
			protected void onLooperPrepared() {
				sHandler = new Handler();
				sSignal.countDown();
			}
		}.start();
	}
	
	/** 
	 * Creates a streaming session that can be customized by adding tracks.
	 */
	public Session() {
		mMainHandler = new Handler(Looper.getMainLooper());
		// Me make sure that we won't send Runnables to a non existing thread
		try {
			sSignal.await();
		} catch (InterruptedException e) {}
	}

	/**
	 * The callback interface you need to implement to get some feedback
	 * Those will be called from the UI thread.
	 */
	public interface Callback {

		/** 
		 * Called periodically to inform you on the bandwidth 
		 * consumption of the streams when streaming. 
		 */
		public void onBitrareUpdate(long bitrate);

		/** Called when some error occurs. */
		public void onSessionError(int reason, int streamType, Exception e);

		/** 
		 * Called when the previw of the {@link VideoStream}
		 * has correctly been started.
		 * If an error occurs while starting the preview,
		 * {@link Callback#onSessionError(int, int, Exception)} will be
		 * called instead of {@link Callback#onPreviewStarted()}.
		 */
		public void onPreviewStarted();

		/** 
		 * Called when the session has correctly been configured 
		 * after calling {@link Session#configure()}.
		 * If an error occurs while configuring the {@link Session},
		 * {@link Callback#onSessionError(int, int, Exception)} will be
		 * called instead of  {@link Callback#onSessionConfigured()}.
		 */
		public void onSessionConfigured();

		/** 
		 * Called when the streams of the session have correctly been started.
		 * If an error occurs while starting the {@link Session},
		 * {@link Callback#onSessionError(int, int, Exception)} will be
		 * called instead of  {@link Callback#onSessionStarted()}. 
		 */
		public void onSessionStarted();

		/** Called when the stream of the session have been stopped. */
		public void onSessionStopped();

	}

	/** You probably don't need to use that directly, use the {@link SessionBuilder}. */
	void addAudioTrack(AudioStream track) {
		removeAudioTrack();
		mAudioStream = track;
	}

	/** You probably don't need to use that directly, use the {@link SessionBuilder}. */
	void addVideoTrack(VideoStream track) {
		removeVideoTrack();
		mVideoStream = track;
	}

	/** You probably don't need to use that directly, use the {@link SessionBuilder}. */
	void removeAudioTrack() {
		if (mAudioStream != null) {
			mAudioStream.stop();
			mAudioStream = null;
		}
	}

	/** You probably don't need to use that directly, use the {@link SessionBuilder}. */
	void removeVideoTrack() {
		if (mVideoStream != null) {
			mVideoStream.stopPreview();
			mVideoStream = null;
		}
	}

	/** Returns the underlying {@link AudioStream} used by the {@link Session}. */
	public AudioStream getAudioTrack() {
		return mAudioStream;
	}

	/** Returns the underlying {@link VideoStream} used by the {@link Session}. */
	public VideoStream getVideoTrack() {
		return mVideoStream;
	}	

	/**
	 * Sets the callback interface that will be called by the {@link Session}.
	 * @param callback The implementation of the {@link Callback} interface
	 */
	public void setCallback(Callback callback) {
		mCallback = callback;
	}	


	/** 
	 * The destination address for all the streams of the session.
	 * Changes will be taken into account the next time you start the session.
	 * @param destination The destination address
	 */
	public void setDestination(String destination) {
		mDestination =  destination;
	}


	/** 
	 * Sets the configuration of the stream. You can call this method at any time 
	 * and changes will take effect next time you call {@link #configure()}.
	 * @param quality Quality of the stream
	 */
	public void setVideoQuality(VideoQuality quality) {
		if (mVideoStream != null) {
			mVideoStream.setVideoQuality(quality);
		}
	}

	/**
	 * Sets a Surface to show a preview of recorded media (video). 
	 * You can call this method at any time and changes will take effect next time you call {@link #start()} or {@link #startPreview()}.
	 */
	public void setSurfaceView(final SurfaceView view) {
		sHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mVideoStream != null) {
					mVideoStream.setSurfaceView(view);
				}
			}				
		});
	}

	/** 
	 * Sets the orientation of the preview. You can call this method at any time 
	 * and changes will take effect next time you call {@link #configure()}.
	 * @param orientation The orientation of the preview
	 */
	public void setPreviewOrientation(int orientation) {
		if (mVideoStream != null) {
			mVideoStream.setPreviewOrientation(orientation);
		}
	}	
	
	/** 
	 * Sets the configuration of the stream. You can call this method at any time 
	 * and changes will take effect next time you call {@link #configure()}.
	 * @param quality Quality of the stream
	 */
	public void setAudioQuality(AudioQuality quality) {
		if (mAudioStream != null) {
			mAudioStream.setAudioQuality(quality);
		}
	}

	/**
	 * Returns the {@link Callback} interface that was set with 
	 * {@link #setCallback(Callback)} or null if none was set.
	 */
	public Callback getCallback() {
		return mCallback;
	}	

	

	/** Indicates if a track is currently running. */
	public boolean isStreaming() {
		if ( (mAudioStream!=null && mAudioStream.isStreaming()) || (mVideoStream!=null && mVideoStream.isStreaming()) )
			return true;
		else 
			return false;
	}

	/** 
	 * Configures all streams of the session.
	 **/
	public void configure() {
		sHandler.post(new Runnable() {
			@Override
			public void run() {
				try {
					syncConfigure();
				} catch (Exception e) {};
			}
		});
	}	

	/** 
	 * Does the same thing as {@link #configure()}, but in a syncronous manner.
	 * Throws exceptions in addition to calling a callback 
	 * {@link Callback#onSessionError(int, int, Exception)} when
	 * an error occurs.	
	 **/
	public void syncConfigure()  
			throws CameraInUseException, 
			StorageUnavailableException,
			ConfNotSupportedException, 
			InvalidSurfaceException, 
			RuntimeException,
			IOException {

		for (int id=0;id<2;id++) {
			Stream stream = id==0 ? mAudioStream : mVideoStream;
			if (stream!=null && !stream.isStreaming()) {
				try {
					stream.configure();
				} catch (CameraInUseException e) {
					postError(ERROR_CAMERA_ALREADY_IN_USE , id, e);
					throw e;
				} catch (StorageUnavailableException e) {
					postError(ERROR_STORAGE_NOT_READY , id, e);
					throw e;
				} catch (ConfNotSupportedException e) {
					postError(ERROR_CONFIGURATION_NOT_SUPPORTED , id, e);
					throw e;
				} catch (InvalidSurfaceException e) {
					postError(ERROR_INVALID_SURFACE , id, e);
					throw e;
				} catch (IOException e) {
					postError(ERROR_OTHER, id, e);
					throw e;
				} catch (RuntimeException e) {
					postError(ERROR_OTHER, id, e);
					throw e;
				}
			}
		}
		postSessionConfigured();
	}

	/** 
	 * Asyncronously starts all streams of the session.
	 **/
	public void start() {
		sHandler.post(new Runnable() {
			@Override
			public void run() {
				try {
					syncStart();
				} catch (Exception e) {}
			}				
		});
	}

	/** 
	 * Starts a stream in a syncronous manner. 
	 * Throws exceptions in addition to calling a callback.
	 * @param id The id of the stream to start
	 **/
	public void syncStart(int id) 			
			throws CameraInUseException, 
			StorageUnavailableException,
			ConfNotSupportedException, 
			InvalidSurfaceException, 
			UnknownHostException,
			IOException {
		
		Stream stream = id==0 ? mAudioStream : mVideoStream;
		if (stream!=null && !stream.isStreaming()) {
			try {
				InetAddress destination =  InetAddress.getByName(mDestination);
				stream.start();
				if (getTrack(1-id) == null || getTrack(1-id).isStreaming()) {
					postSessionStarted();
				}
				if (getTrack(1-id) == null || !getTrack(1-id).isStreaming()) {
					sHandler.post(mUpdateBitrate);
				}
			} catch (UnknownHostException e) {
				postError(ERROR_UNKNOWN_HOST, id, e);
				throw e;
			} catch (CameraInUseException e) {
				postError(ERROR_CAMERA_ALREADY_IN_USE , id, e);
				throw e;
			} catch (StorageUnavailableException e) {
				postError(ERROR_STORAGE_NOT_READY , id, e);
				throw e;
			} catch (ConfNotSupportedException e) {
				postError(ERROR_CONFIGURATION_NOT_SUPPORTED , id, e);
				throw e;
			} catch (InvalidSurfaceException e) {
				postError(ERROR_INVALID_SURFACE , id, e);
				throw e;
			} catch (IOException e) {
				postError(ERROR_OTHER, id, e);
				throw e;
			} catch (RuntimeException e) {
				postError(ERROR_OTHER, id, e);
				throw e;
			}
		}

	}	

	/** 
	 * Does the same thing as {@link #start()}, but in a syncronous manner. 
	 * Throws exceptions in addition to calling a callback.
	 **/
	public void syncStart() 			
			throws CameraInUseException, 
			StorageUnavailableException,
			ConfNotSupportedException, 
			InvalidSurfaceException, 
			UnknownHostException,
			IOException {

		syncStart(1);
		try {
			syncStart(0);
		} catch (RuntimeException e) {
			syncStop(1);
			throw e;
		} catch (IOException e) {
			syncStop(1);
			throw e;
		}

	}	

	/** Stops all existing streams. */
	public void stop() {
		sHandler.post(new Runnable() {
			@Override
			public void run() {
				syncStop();
			}
		});
	}

	/** 
	 * Stops one stream in a syncronous manner.
	 * @param id The id of the stream to stop
	 **/	
	private void syncStop(final int id) {
		Stream stream = id==0 ? mAudioStream : mVideoStream;
		if (stream!=null) {
			stream.stop();
		}
	}		
	
	/** Stops all existing streams in a syncronous manner. */
	public void syncStop() {
		syncStop(0);
		syncStop(1);
		postSessionStopped();
	}	

	public void startPreview() {
		sHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mVideoStream != null) {
					try {
						mVideoStream.configure();
						mVideoStream.startPreview();
						postPreviewStarted();
					} catch (CameraInUseException e) {
						postError(ERROR_CAMERA_ALREADY_IN_USE , STREAM_VIDEO, e);
					} catch (ConfNotSupportedException e) {
						postError(ERROR_CONFIGURATION_NOT_SUPPORTED , STREAM_VIDEO, e);
					} catch (InvalidSurfaceException e) {
						postError(ERROR_INVALID_SURFACE , STREAM_VIDEO, e);
					} catch (RuntimeException e) {
						postError(ERROR_OTHER, STREAM_VIDEO, e);
					} catch (StorageUnavailableException e) {
						postError(ERROR_STORAGE_NOT_READY, STREAM_VIDEO, e);
					} catch (IOException e) {
						postError(ERROR_OTHER, STREAM_VIDEO, e);
					}
				}
			}
		});
	}

	public void stopPreview() {
		sHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mVideoStream != null) {
					mVideoStream.stopPreview();
				}
			}
		});
	}	

	public void switchCamera() {
		sHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mVideoStream != null) {
					try {
						mVideoStream.switchCamera();
						postPreviewStarted();
					} catch (CameraInUseException e) {
						postError(ERROR_CAMERA_ALREADY_IN_USE , STREAM_VIDEO, e);
					} catch (ConfNotSupportedException e) {
						postError(ERROR_CONFIGURATION_NOT_SUPPORTED , STREAM_VIDEO, e);
					} catch (InvalidSurfaceException e) {
						postError(ERROR_INVALID_SURFACE , STREAM_VIDEO, e);
					} catch (IOException e) {
						postError(ERROR_OTHER, STREAM_VIDEO, e);
					} catch (RuntimeException e) {
						postError(ERROR_OTHER, STREAM_VIDEO, e);
					}
				}
			}
		});
	}

	public int getCamera() {
		return mVideoStream != null ? mVideoStream.getCamera() : 0;

	}

	public void toggleFlash() {
		sHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mVideoStream != null) {
					try {
						mVideoStream.toggleFlash();
					} catch (RuntimeException e) {
						postError(ERROR_CAMERA_HAS_NO_FLASH, STREAM_VIDEO, e);
					}
				}
			}
		});
	}	

	/** Deletes all existing tracks & release associated resources. */
	public void release() {
		removeAudioTrack();
		removeVideoTrack();
		sHandler.getLooper().quit();
	}

	private void postPreviewStarted() {
		mMainHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mCallback != null) {
					mCallback.onPreviewStarted(); 
				}
			}
		});
	}

	private void postSessionConfigured() {
		mMainHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mCallback != null) {
					mCallback.onSessionConfigured(); 
				}
			}
		});
	}

	private void postSessionStarted() {
		mMainHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mCallback != null) {
					mCallback.onSessionStarted(); 
				}
			}
		});
	}		

	private void postSessionStopped() {
		mMainHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mCallback != null) {
					mCallback.onSessionStopped(); 
				}
			}
		});
	}	

	private void postError(final int reason, final int streamType,final Exception e) {
		mMainHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mCallback != null) {
					mCallback.onSessionError(reason, streamType, e); 
				}
			}
		});
	}	

	private void postBitRate(final long bitrate) {
		mMainHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mCallback != null) {
					mCallback.onBitrareUpdate(bitrate);
				}
			}
		});
	}		

	private Runnable mUpdateBitrate = new Runnable() {
		@Override
		public void run() {
			if (isStreaming()) { 
				//postBitRate(getBitrate());
				sHandler.postDelayed(mUpdateBitrate, 500);
			} else {
				postBitRate(0);
			}
		}
	};


	public boolean trackExists(int id) {
		if (id==0) 
			return mAudioStream!=null;
		else
			return mVideoStream!=null;
	}

	public Stream getTrack(int id) {
		if (id==0)
			return mAudioStream;
		else
			return mVideoStream;
	}

}
