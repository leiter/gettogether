package com.gettogether.app.platform

expect class CallServiceBridge {
    fun startOutgoingCall(contactId: String, contactName: String, isVideo: Boolean)
    fun startIncomingCall(callId: String, contactId: String, contactName: String, isVideo: Boolean)
    fun answerCall()
    fun declineCall()
    fun endCall()
    fun toggleMute()
    fun toggleSpeaker()
    fun toggleVideo()
}
