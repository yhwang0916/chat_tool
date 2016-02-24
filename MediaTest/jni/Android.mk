LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := rtmp
LOCAL_SRC_FILES := prebuild/librtmp.so
include $(PREBUILT_SHARED_LIBRARY)


# Program
include $(CLEAR_VARS)
LOCAL_MODULE := RTMPSender
LOCAL_SRC_FILES := com_pkmdz_chattool_rtmpnative_RTMPNative.cpp common.cpp RTMPStream.cpp
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include
LOCAL_LDLIBS := -llog -lz
LOCAL_SHARED_LIBRARIES := rtmp
include $(BUILD_SHARED_LIBRARY)

