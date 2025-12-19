package com.gettogether.app.domain.model

import kotlin.time.Instant

data class Call(
    val id: String,
    val accountId: String,
    val participants: List<Contact>,
    val state: CallState,
    val isVideo: Boolean,
    val isConference: Boolean,
    val startedAt: Instant? = null
)

enum class CallState {
    IDLE,
    INCOMING,
    OUTGOING,
    CONNECTING,
    RINGING,
    CURRENT,
    HOLD,
    ENDED
}
