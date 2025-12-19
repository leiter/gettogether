/**
 * JNI Stub Implementation for Get-Together App
 *
 * This file provides stub implementations of the JNI native methods declared in
 * AndroidJamiBridge.kt. When the full jami-daemon is built and linked, these
 * stubs will be replaced by the actual SWIG-generated JNI wrapper.
 *
 * The stub implementation allows the app to compile and run without the native
 * Jami library, returning placeholder/empty values for all methods.
 */

#include <jni.h>
#include <string>
#include <android/log.h>
#include <map>
#include <vector>

#define LOG_TAG "JamiBridge-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// JNI class path for AndroidJamiBridge
static const char* JAMI_BRIDGE_CLASS = "com/gettogether/app/jami/AndroidJamiBridge";

// Flag to track daemon state (stub)
static bool g_daemonRunning = false;

#ifdef JAMI_STUB_ONLY

extern "C" {

// ============================================================================
// Daemon Lifecycle
// ============================================================================

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeInit(
    JNIEnv* env, jobject thiz, jstring dataPath) {
    const char* path = env->GetStringUTFChars(dataPath, nullptr);
    LOGI("nativeInit called with path: %s (STUB)", path);
    env->ReleaseStringUTFChars(dataPath, path);
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeStart(JNIEnv* env, jobject thiz) {
    LOGI("nativeStart called (STUB)");
    g_daemonRunning = true;
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeStop(JNIEnv* env, jobject thiz) {
    LOGI("nativeStop called (STUB)");
    g_daemonRunning = false;
}

JNIEXPORT jboolean JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeIsRunning(JNIEnv* env, jobject thiz) {
    return g_daemonRunning ? JNI_TRUE : JNI_FALSE;
}

// ============================================================================
// Account Management
// ============================================================================

JNIEXPORT jstring JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeAddAccount(
    JNIEnv* env, jobject thiz, jobject details) {
    LOGI("nativeAddAccount called (STUB)");
    return env->NewStringUTF("stub-account-id");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeRemoveAccount(
    JNIEnv* env, jobject thiz, jstring accountId) {
    LOGI("nativeRemoveAccount called (STUB)");
}

JNIEXPORT jobjectArray JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeGetAccountList(JNIEnv* env, jobject thiz) {
    LOGI("nativeGetAccountList called (STUB)");
    jclass stringClass = env->FindClass("java/lang/String");
    return env->NewObjectArray(0, stringClass, nullptr);
}

JNIEXPORT jobject JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeGetAccountDetails(
    JNIEnv* env, jobject thiz, jstring accountId) {
    LOGI("nativeGetAccountDetails called (STUB)");
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "()V");
    return env->NewObject(hashMapClass, hashMapInit);
}

JNIEXPORT jobject JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeGetVolatileAccountDetails(
    JNIEnv* env, jobject thiz, jstring accountId) {
    LOGI("nativeGetVolatileAccountDetails called (STUB)");
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "()V");
    return env->NewObject(hashMapClass, hashMapInit);
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeSetAccountDetails(
    JNIEnv* env, jobject thiz, jstring accountId, jobject details) {
    LOGI("nativeSetAccountDetails called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeSetAccountActive(
    JNIEnv* env, jobject thiz, jstring accountId, jboolean active) {
    LOGI("nativeSetAccountActive called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeUpdateProfile(
    JNIEnv* env, jobject thiz, jstring accountId, jstring displayName,
    jstring avatar, jstring fileType, jint flag) {
    LOGI("nativeUpdateProfile called (STUB)");
}

JNIEXPORT jboolean JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeRegisterName(
    JNIEnv* env, jobject thiz, jstring accountId, jstring name,
    jstring scheme, jstring password) {
    LOGI("nativeRegisterName called (STUB)");
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeLookupName(
    JNIEnv* env, jobject thiz, jstring accountId, jstring nameserver, jstring name) {
    LOGI("nativeLookupName called (STUB)");
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeLookupAddress(
    JNIEnv* env, jobject thiz, jstring accountId, jstring nameserver, jstring address) {
    LOGI("nativeLookupAddress called (STUB)");
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeExportToFile(
    JNIEnv* env, jobject thiz, jstring accountId, jstring destPath,
    jstring scheme, jstring password) {
    LOGI("nativeExportToFile called (STUB)");
    return JNI_FALSE;
}

// ============================================================================
// Contacts
// ============================================================================

JNIEXPORT jobjectArray JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeGetContacts(
    JNIEnv* env, jobject thiz, jstring accountId) {
    LOGI("nativeGetContacts called (STUB)");
    jclass mapClass = env->FindClass("java/util/HashMap");
    return env->NewObjectArray(0, mapClass, nullptr);
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeAddContact(
    JNIEnv* env, jobject thiz, jstring accountId, jstring uri) {
    LOGI("nativeAddContact called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeRemoveContact(
    JNIEnv* env, jobject thiz, jstring accountId, jstring uri, jboolean ban) {
    LOGI("nativeRemoveContact called (STUB)");
}

JNIEXPORT jobject JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeGetContactDetails(
    JNIEnv* env, jobject thiz, jstring accountId, jstring uri) {
    LOGI("nativeGetContactDetails called (STUB)");
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "()V");
    return env->NewObject(hashMapClass, hashMapInit);
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeAcceptTrustRequest(
    JNIEnv* env, jobject thiz, jstring accountId, jstring from) {
    LOGI("nativeAcceptTrustRequest called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeDiscardTrustRequest(
    JNIEnv* env, jobject thiz, jstring accountId, jstring from) {
    LOGI("nativeDiscardTrustRequest called (STUB)");
}

JNIEXPORT jobjectArray JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeGetTrustRequests(
    JNIEnv* env, jobject thiz, jstring accountId) {
    LOGI("nativeGetTrustRequests called (STUB)");
    jclass mapClass = env->FindClass("java/util/HashMap");
    return env->NewObjectArray(0, mapClass, nullptr);
}

// ============================================================================
// Conversations
// ============================================================================

JNIEXPORT jobjectArray JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeGetConversations(
    JNIEnv* env, jobject thiz, jstring accountId) {
    LOGI("nativeGetConversations called (STUB)");
    jclass stringClass = env->FindClass("java/lang/String");
    return env->NewObjectArray(0, stringClass, nullptr);
}

JNIEXPORT jstring JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeStartConversation(
    JNIEnv* env, jobject thiz, jstring accountId) {
    LOGI("nativeStartConversation called (STUB)");
    return env->NewStringUTF("stub-conversation-id");
}

JNIEXPORT jboolean JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeRemoveConversation(
    JNIEnv* env, jobject thiz, jstring accountId, jstring conversationId) {
    LOGI("nativeRemoveConversation called (STUB)");
    return JNI_TRUE;
}

JNIEXPORT jobject JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeConversationInfos(
    JNIEnv* env, jobject thiz, jstring accountId, jstring conversationId) {
    LOGI("nativeConversationInfos called (STUB)");
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "()V");
    return env->NewObject(hashMapClass, hashMapInit);
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeUpdateConversationInfos(
    JNIEnv* env, jobject thiz, jstring accountId, jstring conversationId, jobject infos) {
    LOGI("nativeUpdateConversationInfos called (STUB)");
}

JNIEXPORT jobjectArray JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeGetConversationMembers(
    JNIEnv* env, jobject thiz, jstring accountId, jstring conversationId) {
    LOGI("nativeGetConversationMembers called (STUB)");
    jclass mapClass = env->FindClass("java/util/HashMap");
    return env->NewObjectArray(0, mapClass, nullptr);
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeAddConversationMember(
    JNIEnv* env, jobject thiz, jstring accountId, jstring conversationId, jstring contactUri) {
    LOGI("nativeAddConversationMember called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeRemoveConversationMember(
    JNIEnv* env, jobject thiz, jstring accountId, jstring conversationId, jstring contactUri) {
    LOGI("nativeRemoveConversationMember called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeAcceptConversationRequest(
    JNIEnv* env, jobject thiz, jstring accountId, jstring conversationId) {
    LOGI("nativeAcceptConversationRequest called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeDeclineConversationRequest(
    JNIEnv* env, jobject thiz, jstring accountId, jstring conversationId) {
    LOGI("nativeDeclineConversationRequest called (STUB)");
}

JNIEXPORT jobjectArray JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeGetConversationRequests(
    JNIEnv* env, jobject thiz, jstring accountId) {
    LOGI("nativeGetConversationRequests called (STUB)");
    jclass mapClass = env->FindClass("java/util/HashMap");
    return env->NewObjectArray(0, mapClass, nullptr);
}

// ============================================================================
// Messaging
// ============================================================================

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeSendMessage(
    JNIEnv* env, jobject thiz, jstring accountId, jstring conversationId,
    jstring message, jstring replyTo, jint flag) {
    LOGI("nativeSendMessage called (STUB)");
}

JNIEXPORT jint JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeLoadConversation(
    JNIEnv* env, jobject thiz, jstring accountId, jstring conversationId,
    jstring fromMessage, jint n) {
    LOGI("nativeLoadConversation called (STUB)");
    return 0;
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeSetIsComposing(
    JNIEnv* env, jobject thiz, jstring accountId, jstring conversationUri, jboolean isWriting) {
    LOGI("nativeSetIsComposing called (STUB)");
}

JNIEXPORT jboolean JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeSetMessageDisplayed(
    JNIEnv* env, jobject thiz, jstring accountId, jstring conversationUri,
    jstring messageId, jint status) {
    LOGI("nativeSetMessageDisplayed called (STUB)");
    return JNI_TRUE;
}

// ============================================================================
// Calls
// ============================================================================

JNIEXPORT jstring JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativePlaceCallWithMedia(
    JNIEnv* env, jobject thiz, jstring accountId, jstring to, jobjectArray mediaList) {
    LOGI("nativePlaceCallWithMedia called (STUB)");
    return env->NewStringUTF("stub-call-id");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeAccept(
    JNIEnv* env, jobject thiz, jstring accountId, jstring callId) {
    LOGI("nativeAccept called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeAcceptWithMedia(
    JNIEnv* env, jobject thiz, jstring accountId, jstring callId, jobjectArray mediaList) {
    LOGI("nativeAcceptWithMedia called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeRefuse(
    JNIEnv* env, jobject thiz, jstring accountId, jstring callId) {
    LOGI("nativeRefuse called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeHangUp(
    JNIEnv* env, jobject thiz, jstring accountId, jstring callId) {
    LOGI("nativeHangUp called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeHold(
    JNIEnv* env, jobject thiz, jstring accountId, jstring callId) {
    LOGI("nativeHold called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeUnhold(
    JNIEnv* env, jobject thiz, jstring accountId, jstring callId) {
    LOGI("nativeUnhold called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeMuteLocalMedia(
    JNIEnv* env, jobject thiz, jstring accountId, jstring callId,
    jstring mediaType, jboolean mute) {
    LOGI("nativeMuteLocalMedia called (STUB)");
}

JNIEXPORT jobject JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeGetCallDetails(
    JNIEnv* env, jobject thiz, jstring accountId, jstring callId) {
    LOGI("nativeGetCallDetails called (STUB)");
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "()V");
    return env->NewObject(hashMapClass, hashMapInit);
}

JNIEXPORT jobjectArray JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeGetCallList(
    JNIEnv* env, jobject thiz, jstring accountId) {
    LOGI("nativeGetCallList called (STUB)");
    jclass stringClass = env->FindClass("java/lang/String");
    return env->NewObjectArray(0, stringClass, nullptr);
}

// ============================================================================
// Conference
// ============================================================================

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeCreateConfFromParticipantList(
    JNIEnv* env, jobject thiz, jstring accountId, jobjectArray participants) {
    LOGI("nativeCreateConfFromParticipantList called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeJoinParticipant(
    JNIEnv* env, jobject thiz, jstring accountId, jstring callId1,
    jstring accountId2, jstring callId2) {
    LOGI("nativeJoinParticipant called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeAddParticipant(
    JNIEnv* env, jobject thiz, jstring accountId, jstring callId,
    jstring account2Id, jstring confId) {
    LOGI("nativeAddParticipant called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeHangUpConference(
    JNIEnv* env, jobject thiz, jstring accountId, jstring confId) {
    LOGI("nativeHangUpConference called (STUB)");
}

JNIEXPORT jobject JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeGetConferenceDetails(
    JNIEnv* env, jobject thiz, jstring accountId, jstring confId) {
    LOGI("nativeGetConferenceDetails called (STUB)");
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "()V");
    return env->NewObject(hashMapClass, hashMapInit);
}

JNIEXPORT jobjectArray JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeGetParticipantList(
    JNIEnv* env, jobject thiz, jstring accountId, jstring confId) {
    LOGI("nativeGetParticipantList called (STUB)");
    jclass stringClass = env->FindClass("java/lang/String");
    return env->NewObjectArray(0, stringClass, nullptr);
}

JNIEXPORT jobjectArray JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeGetConferenceInfos(
    JNIEnv* env, jobject thiz, jstring accountId, jstring confId) {
    LOGI("nativeGetConferenceInfos called (STUB)");
    jclass mapClass = env->FindClass("java/util/HashMap");
    return env->NewObjectArray(0, mapClass, nullptr);
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeSetConferenceLayout(
    JNIEnv* env, jobject thiz, jstring accountId, jstring confId, jint layout) {
    LOGI("nativeSetConferenceLayout called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeMuteParticipant(
    JNIEnv* env, jobject thiz, jstring accountId, jstring confId,
    jstring peerId, jboolean state) {
    LOGI("nativeMuteParticipant called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeHangupParticipant(
    JNIEnv* env, jobject thiz, jstring accountId, jstring confId,
    jstring accountUri, jstring deviceId) {
    LOGI("nativeHangupParticipant called (STUB)");
}

// ============================================================================
// Video
// ============================================================================

JNIEXPORT jobjectArray JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeGetVideoDeviceList(JNIEnv* env, jobject thiz) {
    LOGI("nativeGetVideoDeviceList called (STUB)");
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(2, stringClass, nullptr);
    env->SetObjectArrayElement(result, 0, env->NewStringUTF("camera://0"));
    env->SetObjectArrayElement(result, 1, env->NewStringUTF("camera://1"));
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeGetCurrentVideoDevice(JNIEnv* env, jobject thiz) {
    LOGI("nativeGetCurrentVideoDevice called (STUB)");
    return env->NewStringUTF("camera://0");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeSetVideoDevice(
    JNIEnv* env, jobject thiz, jstring deviceId) {
    LOGI("nativeSetVideoDevice called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeStartVideo(JNIEnv* env, jobject thiz) {
    LOGI("nativeStartVideo called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeStopVideo(JNIEnv* env, jobject thiz) {
    LOGI("nativeStopVideo called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeSwitchInput(
    JNIEnv* env, jobject thiz, jstring accountId, jstring callId, jstring resource) {
    LOGI("nativeSwitchInput called (STUB)");
}

// ============================================================================
// Audio
// ============================================================================

JNIEXPORT jobjectArray JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeGetAudioOutputDeviceList(JNIEnv* env, jobject thiz) {
    LOGI("nativeGetAudioOutputDeviceList called (STUB)");
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(2, stringClass, nullptr);
    env->SetObjectArrayElement(result, 0, env->NewStringUTF("Speaker"));
    env->SetObjectArrayElement(result, 1, env->NewStringUTF("Earpiece"));
    return result;
}

JNIEXPORT jobjectArray JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeGetAudioInputDeviceList(JNIEnv* env, jobject thiz) {
    LOGI("nativeGetAudioInputDeviceList called (STUB)");
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(1, stringClass, nullptr);
    env->SetObjectArrayElement(result, 0, env->NewStringUTF("Microphone"));
    return result;
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeSetAudioOutputDevice(
    JNIEnv* env, jobject thiz, jint index) {
    LOGI("nativeSetAudioOutputDevice called (STUB)");
}

JNIEXPORT void JNICALL
Java_com_gettogether_app_jami_AndroidJamiBridge_nativeSetAudioInputDevice(
    JNIEnv* env, jobject thiz, jint index) {
    LOGI("nativeSetAudioInputDevice called (STUB)");
}

} // extern "C"

#endif // JAMI_STUB_ONLY
