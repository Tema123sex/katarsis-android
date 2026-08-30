//
// Created by xtao on 2024/11/10.
//

#ifndef AXGRAM_LOG_H
#define AXGRAM_LOG_H

#include <android/log.h>

#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, "integrity", __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, "integrity", __VA_ARGS__))

#endif //AXGRAM_LOG_H
