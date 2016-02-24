package com.pkmdz.chattool.streaming.rtmp;

public abstract class AbstractRTMPSender {
	protected MediaCodecInputStream is;
	
	public void setInputStream(MediaCodecInputStream is){
		this.is = is;
	}
	
	public abstract void start();
	
	public abstract void stop();
}
