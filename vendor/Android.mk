LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_C_INCLUDES += $(LOCAL_PATH)/cdict/libcdict
LOCAL_CFLAGS +=
# -Wall -Wextra -Werror
LOCAL_SRC_FILES := cdict/libcdict/libcdict.c cdict/java/jni/juloo_cdict_Cdict.c
LOCAL_MODULE := libcdict_java
LOCAL_SDK_VERSION := 21

# Android requires 16K pages
LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384
# Disable build id to ensure reproducibility, needed for F-Droid
LOCAL_LDFLAGS += -Wl,--build-id=none

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

HUNSPELL_SRC := $(wildcard $(LOCAL_PATH)/hunspell/src/hunspell/*.cxx)
LOCAL_C_INCLUDES += $(LOCAL_PATH)/hunspell/src/hunspell
LOCAL_CPPFLAGS += -std=c++17 -fexceptions -frtti
LOCAL_SRC_FILES := $(HUNSPELL_SRC:$(LOCAL_PATH)/%=%) hunspell/java/jni/juloo_keyboard2_autocorrect_Hunspell.cxx
LOCAL_MODULE := libhunspell_java
LOCAL_SDK_VERSION := 21
LOCAL_LDLIBS += -lc++_static

# Android requires 16K pages
LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384
# Disable build id to ensure reproducibility, needed for F-Droid
LOCAL_LDFLAGS += -Wl,--build-id=none

include $(BUILD_SHARED_LIBRARY)
