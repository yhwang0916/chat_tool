package com.pkmdz.chattool.streaming.rtmp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import com.pkmdz.chattool.rtmpnative.RTMPNative;
import com.pkmdz.chattool.streaming.audio.AACStream;

import android.os.Environment;
import android.util.Log;

public class AACRTMPSender extends AbstractRTMPSender implements Runnable{
	
	private Thread t = null;
	
	private boolean isCardExist = false;
	
	private FileOutputStream mOutStream = null;
	
	private int samplingRate ;
	
	//private int mAACRTMP;
	
	private int tick = 0;
	
	//private RTMPNative mRTMPNative;
	
	//public native void init();
	
	//public native void sendData(byte [] buffer, int length, int tick);
	
	public AACRTMPSender() {
		super();
	}
	
	public AACRTMPSender(int samplingRate){
		this.samplingRate = samplingRate;
	}

	@Override
	public void start() {
		//mRTMPNative = RTMPNative.getInstance();
		if(!RTMPNative.isRTMPInit){
			mRTMPNative.rtmpInit();//
			RTMPNative.isRTMPInit = true;
		}
		//mRTMPNative.sendAMetaData();
		/*
		if(Environment.getExternalStorageState().
				equals(Environment.MEDIA_MOUNTED)){
			isCardExist = true;
		}
		if(isCardExist){
			try {
				File path = Environment.getExternalStorageDirectory();
				File file = new File(path, "audioTest.aac");
				if(file.exists()){
					file.delete();
				}
				mOutStream =new FileOutputStream(file);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}*/
		if(t == null){
			t = new Thread(this);
			t.start();
		}
	}

	@Override
	public void stop() {
		if (t != null) {
			try {
				/*if(mOutStream !=null){
					mOutStream.close();
				}*/
				mRTMPNative.close();
				is.close();
			} catch (Exception e) {}
			t.interrupt();
			try {
				t.join();
			} catch (InterruptedException e) {}
			t = null;
		}
	}

	@Override
	public void run() {
		while (!Thread.interrupted()) {
				try {
					byte [] buffer = is.read();
					tick += 23;
					//RTMPNative.semp.acquire();
					if(buffer !=null){
						//mRTMPNative.sendAudioData(buffer, buffer.length, tick);
						mRTMPNative.putIntoQueue( 0, buffer, buffer.length);
					}
					//RTMPNative.semp.release();

					/*byte [] aacRawBuffer = new byte[buffer.length + 7];
					addADTStoPacket(aacRawBuffer, buffer.length + 7);
					System.arraycopy(buffer, 0, aacRawBuffer, 7, buffer.length);
					if(mOutStream != null){
						mOutStream.write(aacRawBuffer);
					}*/
				} catch (IOException e) {
					e.printStackTrace();
				} //catch (InterruptedException e) {
					//RTMPNative.semp.release();
					//e.printStackTrace();
				//}
			}
		}
	
	/**
	 *  Add ADTS header at the beginning of each and every AAC packet.
	 *  This is needed as MediaCodec encoder generates a packet of raw
	 *  AAC data.
	 *
	 *  Note the packetLen must count in the ADTS header itself.
	 **/
	private void addADTStoPacket(byte[] packet, int packetLen) {
	    int profile = 2;  //AAC LC
	                      //39=MediaCodecInfo.CodecProfileLevel.AACObjectELD;
	    int freqIdx = 0;
	    int chanCfg = 2;  //CPE
	    
	    for(int i = 0; i< AACStream.AUDIO_SAMPLING_RATES.length; i++){
	    	if(AACStream.AUDIO_SAMPLING_RATES[i] == samplingRate){
	    		freqIdx = i;
	    		break;
	    	}
	    }

	    // fill in ADTS data
	    packet[0] = (byte)0xFF;
	    packet[1] = (byte)0xF9;
	    packet[2] = (byte)(((profile-1)<<6) + (freqIdx<<2) +(chanCfg>>2));
	    packet[3] = (byte)(((chanCfg&3)<<6) + (packetLen>>11));
	    packet[4] = (byte)((packetLen&0x7FF) >> 3);
	    packet[5] = (byte)(((packetLen&7)<<5) + 0x1F);
	    packet[6] = (byte)0xFC;
	}

	
}
