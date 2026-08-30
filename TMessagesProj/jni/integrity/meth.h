//
// Created by xtao on 2024/11/10.
//

#ifndef AXGRAM_METH_H
#define AXGRAM_METH_H

#include <jni.h>
#include "log.h"

#ifdef __cplusplus
extern "C" {
#endif

jobject getApplication(JNIEnv *env);
const char *getApkPath(JNIEnv *env, jobject context);

#ifdef __cplusplus
}
#endif

#endif //AXGRAM_METH_H
