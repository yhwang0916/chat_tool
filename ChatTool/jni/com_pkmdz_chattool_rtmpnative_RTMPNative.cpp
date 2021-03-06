#include<com_pkmdz_chattool_rtmpnative_RTMPNative.h>
#include<common.h>
#include<Android_Log.h>
/* Class:     com_pkmdz_chattool_rtmpnative_RTMPNative
 * Method:    rtmpInit
 * Signature: ()V
 */
void Java_com_pkmdz_chattool_rtmpnative_RTMPNative_rtmpInit
  (JNIEnv *env, jobject thiz){
	CRTMPStream *rtmpStream = get_stream_field(env,thiz,class_path_name,
					"mRTMPSender");
	if(!rtmpStream){
		rtmpStream = new CRTMPStream();
		jfieldID field_id = get_field_ID(env, class_path_name, "mRTMPSender", "I");
		env->SetIntField(thiz,field_id, (int)rtmpStream);
	}
	rtmpStream->queueInit(rtmpStream->mQueue);
	rtmpStream->queueInit(rtmpStream->AudioQueue);
	//rtmpStream->Connect("rtmp://139.196.175.109/oflaDemo/streams1010");
	pthread_t tid;
	pthread_create(&tid, NULL, RTMPLive_Thread, (void *)rtmpStream);
}

/*
 * Class:     com_pkmdz_chattool_rtmpnative_RTMPNative
 * Method:    sendVMetaData
 * Signature: ([BI[BI)V
 */
void Java_com_pkmdz_chattool_rtmpnative_RTMPNative_sendVMetaData
  (JNIEnv *env, jobject thiz, jbyteArray spsArray, jint spsLen,
		  jbyteArray ppsArray, jint ppsLen){

	jbyte *spsByte = env->GetByteArrayElements(spsArray, NULL);
	jbyte  *ppsByte = env->GetByteArrayElements(ppsArray, NULL);
	unsigned char *sps =(unsigned char *)malloc(spsLen + 1);
	unsigned char *pps =(unsigned char *)malloc(ppsLen + 1);

	CRTMPStream *rtmpStream = get_stream_field(env,thiz,class_path_name,
				"mRTMPSender");

	memcpy(sps, spsByte, spsLen);
	memcpy(pps, ppsByte, ppsLen);
	sps[spsLen] = '\0';
	pps[ppsLen] = '\0';
	env->ReleaseByteArrayElements( spsArray, spsByte, 0);
	env->ReleaseByteArrayElements( ppsArray, ppsByte, 0);
	rtmpStream->RTMP_Send_MetaData(sps, spsLen, pps, ppsLen);
	free(sps);
	free(pps);
}

/*
 * Class:     com_pkmdz_chattool_rtmpnative_RTMPNative
 * Method:    sendVideoData
 * Signature: ([BII)V
 */
void Java_com_pkmdz_chattool_rtmpnative_RTMPNative_sendVideoData
  (JNIEnv *env, jobject thiz, jbyteArray bufferArray,
		  jint bufferLen, jint tick){
	CRTMPStream *rtmpStream = get_stream_field(env,thiz,class_path_name,
					"mRTMPSender");
	jbyte *bufferByte = env->GetByteArrayElements(bufferArray, NULL);
		unsigned char *buffer =(unsigned char *)malloc(bufferLen + 1);

	memcpy(buffer, bufferByte, bufferLen);
	buffer[bufferLen] = '\0';
	env->ReleaseByteArrayElements( bufferArray, bufferByte, 0);
	rtmpStream->RTMP_Send_H264(buffer, bufferLen, tick);
	free(buffer);
}

/*
 * Class:     com_pkmdz_chattool_rtmpnative_RTMPNative
 * Method:    sendAMetaData
 * Signature: ()V
 */
void Java_com_pkmdz_chattool_rtmpnative_RTMPNative_sendAMetaData
  (JNIEnv *env, jobject thiz){
	CRTMPStream *rtmpStream = get_stream_field(env,thiz,class_path_name,
					"mRTMPSender");
	rtmpStream->SendAudioMetadata();
}

/*
 * Class:     com_pkmdz_chattool_rtmpnative_RTMPNative
 * Method:    sendAudioData
 * Signature: ([BII)V
 */
void Java_com_pkmdz_chattool_rtmpnative_RTMPNative_sendAudioData
  (JNIEnv *env, jobject thiz, jbyteArray bufferArray, jint length, jint tick){
	CRTMPStream *rtmpStream = get_stream_field(env,thiz,class_path_name,
					"mRTMPSender");
	jbyte *bufferBytes = env->GetByteArrayElements(bufferArray, NULL);
	unsigned char *buffer = (unsigned char *)malloc(length + 1);

	memcpy(buffer, bufferBytes, length);
	buffer[length] = '\0';
	env->ReleaseByteArrayElements(bufferArray, bufferBytes, 0);
	rtmpStream->SendAACData(buffer, length, tick);
	free(buffer);
}

void Java_com_pkmdz_chattool_rtmpnative_RTMPNative_close
  (JNIEnv *env, jobject thiz){
	CRTMPStream *rtmpStream = get_stream_field(env,thiz,class_path_name,
						"mRTMPSender");
	delete rtmpStream;
}

void Java_com_pkmdz_chattool_rtmpnative_RTMPNative_putIntoQueue
  (JNIEnv *env, jobject thiz, jint type, jbyteArray bufferArray, jint length){
	CRTMPStream *rtmpStream = get_stream_field(env,thiz,class_path_name,
						"mRTMPSender");
	jbyte *bufferBytes = env->GetByteArrayElements(bufferArray, NULL);
	unsigned char *buffer = (unsigned char *)malloc(length + 1);

	memcpy(buffer, bufferBytes, length);
	buffer[length] = '\0';
	env->ReleaseByteArrayElements(bufferArray, bufferBytes, 0);
	//char packet[7] = {0};
	//addADTStoPacket(packet, length+7);
	//fwrite(packet, sizeof(char), strlen(packet), rtmpStream->fp);
	//fwrite(buffer, sizeof(unsigned char), length, rtmpStream->fp);
	if(FLV_TAG_TYPE_VIDEO == type){
		rtmpStream->QueuePut(rtmpStream->mQueue, type, length, buffer);
	}else{
		rtmpStream->QueuePut(rtmpStream->AudioQueue, type, length, buffer);
	}
	free(buffer);
}

void Java_com_pkmdz_chattool_rtmpnative_RTMPNative_setSPSAndPPS
  (JNIEnv *env, jobject thiz, jbyteArray spsArray, jint spsLen,
		  jbyteArray ppsArray, jint ppsLen){
	jbyte *spsByte = env->GetByteArrayElements(spsArray, NULL);
	jbyte  *ppsByte = env->GetByteArrayElements(ppsArray, NULL);
	unsigned char *sps =(unsigned char *)malloc(spsLen + 1);
	unsigned char *pps =(unsigned char *)malloc(ppsLen + 1);

	CRTMPStream *rtmpStream = get_stream_field(env,thiz,class_path_name,
				"mRTMPSender");

	if(!rtmpStream){
		rtmpStream = new CRTMPStream();
		jfieldID field_id = get_field_ID(env, class_path_name, "mRTMPSender", "I");
		env->SetIntField(thiz,field_id, (int)rtmpStream);
	}

	memcpy(sps, spsByte, spsLen);
	memcpy(pps, ppsByte, ppsLen);
	sps[spsLen] = '\0';
	pps[ppsLen] = '\0';
	env->ReleaseByteArrayElements( spsArray, spsByte, 0);
	env->ReleaseByteArrayElements( ppsArray, ppsByte, 0);
	rtmpStream->setSPSAndPPS(sps, spsLen, pps, ppsLen);
}
