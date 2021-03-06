/*
 * Copyright (C) 2011-2014 GUIGUI Simon, fyhertz@gmail.com
 * 
 * This file is part of Spydroid (http://code.google.com/p/spydroid-ipcamera/)
 * 
 * Spydroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.pkmdz.chattool.streaming.hw;

import java.nio.ByteBuffer;

import android.media.MediaCodecInfo;

/**
 * Converts from NV21 to YUV420 semi planar or planar.
 */		
public class NV21Convertor {

	private int mSliceHeight, mHeight;
	private int mStride, mWidth;
	private int mSize;
	private boolean mPlanar, mPanesReversed = false;
	private int mYPadding;
	private byte[] mBuffer; 
	ByteBuffer mCopy;
	
	public void setSize(int width, int height) {
		mHeight = height;
		mWidth = width;
		mSliceHeight = height;
		mStride = width;
		mSize = mWidth*mHeight;
	}
	
	public void setStride(int width) {
		mStride = width;
	}
	
	public void setSliceHeigth(int height) {
		mSliceHeight = height;
	}
	
	public void setPlanar(boolean planar) {
		mPlanar = planar;
	}
	
	public void setYPadding(int padding) {
		mYPadding = padding;
	}
	
	public int getBufferSize() {
		return 3*mSize/2;
	}
	
	public void setEncoderColorFormat(int colorFormat) {
		switch (colorFormat) {
		case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
		case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
		case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
			setPlanar(false);
			break;	
		case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
		case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
			setPlanar(true);
			break;
		}
	}
	
	public void setColorPanesReversed(boolean b) {
		mPanesReversed = b;
	}
	
	public int getStride() {
		return mStride;
	}

	public int getSliceHeigth() {
		return mSliceHeight;
	}

	public int getYPadding() {
		return mYPadding;
	}
	
	
	public boolean getPlanar() {
		return mPlanar;
	}
	
	public boolean getUVPanesReversed() {
		return mPanesReversed;
	}
	
	public void convert(byte[] data, ByteBuffer buffer) {
		byte[] result = convert(data);
		//byte[] rotate_result = yuv_rotate90(result);
		buffer.put(result, 0, result.length);
		//buffer.put(rotate_result, 0, rotate_result.length);
	}
	
	//顺时针旋转90
	public byte[] yuv_rotate90(byte[] data){
		byte [] dst = new byte[data.length];
		//旋转Y
		/*int k = 0;
		for(int i =0 ; i< mWidth; i++){
			for(int j = mHeight -1; j>= 0; j--){
				dst[k++] = data[i + j * mWidth];
			}
		}
		//旋转uv
		for(int i =0 ; i< mWidth; i += 2){
			for(int j = mHeight/2 -1; j>= 0; j--){
				dst[k++] = data[mSize + i + j * mWidth];
				dst[k++] = data[mSize + i + j * mWidth + 1];
			}
		}*/	
	        //旋转Y  
	        int k = 0;  
	        for(int i=0;i<mWidth;i++) {  
	            for(int j=0;j<mHeight;j++)   
	            {  
	            	 dst[k] = data[mWidth*j + i];              
	                  k++;  
	            }  
	        }  
	          
	        for(int i=0;i<mWidth;i+=2) {  
	            for(int j=0;j<mHeight/2;j++)   
	            {     
	            	 dst[k] = data[mSize+ mWidth*j + i];      
	            	 dst[k+1]=data[mSize + mWidth*j + i+1];  
	                  k+=2;  
	            }  
	        }  
		return dst;
	}
	
	public byte[] convert(byte[] data) {

		// A buffer large enough for every case
		if (mBuffer==null || mBuffer.length != 3*mSliceHeight*mStride/2+mYPadding) {
			mBuffer = new byte[3*mSliceHeight*mStride/2+mYPadding];
		}
		
		if (!mPlanar) {
			if (mSliceHeight==mHeight && mStride==mWidth) {
				// Swaps U and V
				if (!mPanesReversed) {
					for (int i = mSize; i < mSize+mSize/2; i += 2) {
						mBuffer[0] = data[i+1];
						data[i+1] = data[i];
						data[i] = mBuffer[0]; 
					}
				}
				if (mYPadding>0) {
					System.arraycopy(data, 0, mBuffer, 0, mSize);
					System.arraycopy(data, mSize, mBuffer, mSize+mYPadding, mSize/2);
					return mBuffer;
				}
				return data;
			}
		} else {
			if (mSliceHeight==mHeight && mStride==mWidth) {
				// De-interleave U and V
				if (!mPanesReversed) {
					for (int i = 0; i < mSize/4; i+=1) {
						mBuffer[i] = data[mSize+2*i+1];
						mBuffer[mSize/4+i] = data[mSize+2*i];
					}
				} else {
					for (int i = 0; i < mSize/4; i+=1) {
						mBuffer[i] = data[mSize+2*i];
						mBuffer[mSize/4+i] = data[mSize+2*i+1];
					}
				}
				if (mYPadding == 0) {
					System.arraycopy(mBuffer, 0, data, mSize, mSize/2);
				} else {
					System.arraycopy(data, 0, mBuffer, 0, mSize);
					System.arraycopy(mBuffer, 0, mBuffer, mSize+mYPadding, mSize/2);
					return mBuffer;
				}
				return data;
			}
		}
		
		return data;
	}	
	
}
