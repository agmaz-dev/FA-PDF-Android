#pragma once
#include <android/log.h>
#include "ggml.h"

#define LOG_TAG "fa-ki"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGw(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static inline void aichat_android_log_callback(enum ggml_log_level level, const char *text, void * /*user*/) {
    int prio = ANDROID_LOG_DEBUG;
    if (level == GGML_LOG_LEVEL_ERROR) prio = ANDROID_LOG_ERROR;
    else if (level == GGML_LOG_LEVEL_WARN) prio = ANDROID_LOG_WARN;
    else if (level == GGML_LOG_LEVEL_INFO) prio = ANDROID_LOG_INFO;
    if (prio >= ANDROID_LOG_INFO) __android_log_write(prio, LOG_TAG, text);
}
