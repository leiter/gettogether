package com.gettogether.app.platform

actual class CallServiceBridge {

    actual fun startOutgoingCall(contactId: String, contactName: String, isVideo: Boolean) {
        // TODO: Implement iOS CallKit integration
        println("iOS: Starting outgoing call to $contactName")
    }

    actual fun startIncomingCall(callId: String, contactId: String, contactName: String, isVideo: Boolean) {
        // TODO: Implement iOS CallKit integration for incoming calls
        println("iOS: Incoming call from $contactName")
    }

    actual fun answerCall() {
        // TODO: Implement iOS CallKit answer
        println("iOS: Answering call")
    }

    actual fun declineCall() {
        // TODO: Implement iOS CallKit decline
        println("iOS: Declining call")
    }

    actual fun endCall() {
        // TODO: Implement iOS CallKit end
        println("iOS: Ending call")
    }

    actual fun toggleMute() {
        // TODO: Implement iOS audio mute
        println("iOS: Toggling mute")
    }

    actual fun toggleSpeaker() {
        // TODO: Implement iOS audio speaker
        println("iOS: Toggling speaker")
    }

    actual fun toggleVideo() {
        // TODO: Implement iOS video toggle
        println("iOS: Toggling video")
    }
}
