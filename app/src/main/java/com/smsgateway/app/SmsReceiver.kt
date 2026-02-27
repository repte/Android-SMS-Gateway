package com.smsgateway.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.smsgateway.app.api.ApiClient
import com.smsgateway.app.api.models.SmsPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.google.gson.Gson
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val prefs = AppPreferences(context)
            
            val apiUrl = prefs.apiUrl
            val apiKey = prefs.apiKey

            // Only forward if configured and service is running
            if (apiUrl.isBlank() || apiKey.isBlank() || !prefs.isServiceRunning) {
                return
            }

            for (msg in messages) {
                val sender = msg.originatingAddress ?: "Unknown"
                val body = msg.messageBody
                val timestamp = msg.timestampMillis
                SmsEventBus.emitLog("Receiving SMS from $sender...")
                sendLog(apiUrl.trim(), apiKey.trim(), sender, body, timestamp)
            }
        }
    }

    private fun sendLog(apiUrl: String, apiKey: String, sender: String, body: String, timestamp: Long) {
        val pendingResult = goAsync() // keep receiver alive for coroutine
        scope.launch {
            try {
                val api = ApiClient.createService(apiUrl)
                val payload = SmsPayload(sender, body, timestamp)
                val payloadJson = Gson().toJson(payload)
                Log.d("SmsReceiver", "Forwarding SMS: $payloadJson")
                
                val response = api.receiveSms(
                    apiKey = "Bearer $apiKey",
                    payload = payload
                )
                if (response.isSuccessful) {
                    SmsEventBus.emitLog("Successfully forwarded SMS from $sender to server.")
                } else {
                    val errStr = "Failed to upload SMS from $sender: HTTP ${response.code()}"
                    Log.e("SmsReceiver", errStr)
                    SmsEventBus.emitLog(errStr, true)
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error uploading SMS: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
