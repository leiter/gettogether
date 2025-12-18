package com.gettogether.app.domain.repository

import com.gettogether.app.domain.model.Call
import kotlinx.coroutines.flow.Flow

interface CallRepository {
    fun getCurrentCall(): Flow<Call?>
    fun getCallHistory(accountId: String): Flow<List<Call>>
    suspend fun startCall(accountId: String, contactId: String, withVideo: Boolean): Result<Call>
    suspend fun startConferenceCall(accountId: String, participantIds: List<String>, withVideo: Boolean): Result<Call>
    suspend fun acceptCall(callId: String): Result<Unit>
    suspend fun rejectCall(callId: String): Result<Unit>
    suspend fun hangUp(callId: String): Result<Unit>
    suspend fun toggleMute(callId: String): Result<Boolean>
    suspend fun toggleVideo(callId: String): Result<Boolean>
    suspend fun toggleSpeaker(callId: String): Result<Boolean>
    suspend fun switchCamera(callId: String): Result<Unit>
}
