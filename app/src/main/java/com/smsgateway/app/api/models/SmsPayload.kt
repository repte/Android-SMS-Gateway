package com.smsgateway.app.api.models

import com.google.gson.annotations.SerializedName

data class SmsPayload(
    @SerializedName("sender") val sender: String,
    @SerializedName("body") val body: String,
    @SerializedName("timestamp") val timestamp: Long
)

data class OutboundSmsMessage(
    @SerializedName("id") val id: Long,
    @SerializedName("receiver") val receiver: String,
    @SerializedName("body") val body: String
)

data class OutboundSmsResponse(
    @SerializedName("messages") val messages: List<OutboundSmsMessage>
)

data class UpdateStatusPayload(
    @SerializedName("id") val id: Long,
    @SerializedName("status") val status: String,
    @SerializedName("error") val error: String? = null
)
