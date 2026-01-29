#include <jni.h>
#include <string>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_aiexpensetracker_WhisperNative_transcribe(
        JNIEnv* env,
        jobject,
        jstring audioPath,
        jstring modelPath
) {
    // Placeholder: real whisper.cpp call will go here
    return env->NewStringUTF("transcription placeholder");
}
