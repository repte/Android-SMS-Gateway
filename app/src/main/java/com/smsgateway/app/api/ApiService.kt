package com.smsgateway.app.api

import com.google.gson.annotations.SerializedName
import com.smsgateway.app.api.models.OutboundSmsResponse
import com.smsgateway.app.api.models.SmsPayload
import com.smsgateway.app.api.models.UpdateStatusPayload
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {

    @GET("sms/verify")
    suspend fun verifyApiKey(
        @Header("Authorization") apiKey: String
    ): Response<Unit>

    @POST("sms/receive")
    suspend fun receiveSms(
        @Header("Authorization") apiKey: String,
        @Body payload: SmsPayload
    ): Response<Unit>

    @GET("sms/pending")
    suspend fun getPendingSms(
        @Header("Authorization") apiKey: String
    ): Response<OutboundSmsResponse>

    @POST("sms/status")
    suspend fun updateSmsStatus(
        @Header("Authorization") apiKey: String,
        @Body payload: UpdateStatusPayload
    ): Response<Unit>

    @GET("sms/inbound")
    suspend fun getInboundHistory(
        @Header("Authorization") apiKey: String
    ): Response<InboundHistoryResponse>

    @GET("sms/outbound/all")
    suspend fun getOutboundHistory(
        @Header("Authorization") apiKey: String
    ): Response<OutboundHistoryResponse>
}

data class InboundHistoryResponse(
    @SerializedName("messages") val messages: List<InboundMessage>
)

data class InboundMessage(
    @SerializedName("id") val id: Long,
    @SerializedName("sender") val sender: String,
    @SerializedName("body") val body: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("created_at") val created_at: String
)

data class OutboundHistoryResponse(
    @SerializedName("messages") val messages: List<OutboundMessage>
)

data class OutboundMessage(
    @SerializedName("id") val id: Long,
    @SerializedName("receiver") val receiver: String,
    @SerializedName("body") val body: String,
    @SerializedName("status") val status: String,
    @SerializedName("error_message") val error_message: String?,
    @SerializedName("created_at") val created_at: String,
    @SerializedName("updated_at") val updated_at: String
)
