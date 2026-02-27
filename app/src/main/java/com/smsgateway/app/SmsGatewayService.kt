package com.smsgateway.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smsgateway.app.api.ApiClient
import com.smsgateway.app.api.models.UpdateStatusPayload
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import com.google.gson.Gson
import kotlinx.coroutines.*

class SmsGatewayService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var pollingJob: Job? = null
    private lateinit var prefs: AppPreferences

    companion object {
        private const val CHANNEL_ID = "SmsGatewayChannel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        SmsEventBus.emitLog("Service onStartCommand called (startId: $startId)")
        startPolling()

        return START_STICKY
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            SmsEventBus.emitLog("Polling coroutine started. isActive: $isActive, isServiceRunning: ${prefs.isServiceRunning}")
            while (isActive && prefs.isServiceRunning) {
                val apiUrl = prefs.apiUrl
                val apiKey = prefs.apiKey
                val interval = prefs.intervalSeconds * 1000L
                
                SmsEventBus.emitLog("Sync loop starting. URL: $apiUrl, Interval: ${interval/1000}s")
                SmsEventBus.updateCountdown(null) // Show "Syncing..."

                if (apiUrl.isNotBlank() && apiKey.isNotBlank()) {
                    try {
                        val api = ApiClient.createService(apiUrl.trim())
                        val response = api.getPendingSms("Bearer ${apiKey.trim()}")
                        
                        if (response.isSuccessful) {
                            val messages = response.body()?.messages ?: emptyList()
                            SmsEventBus.emitLog("Synced with server. Found ${messages.size} pending SMS.")
                            
                            for (msg in messages) {
                                sendSms(msg.id, msg.receiver, msg.body, apiKey, apiUrl)
                            }
                        } else {
                            val errStr = "Failed to sync pending SMS: HTTP ${response.code()}"
                            Log.e("SmsGateway", errStr)
                            SmsEventBus.emitLog(errStr, true)
                        }
                    } catch (e: Exception) {
                        val errStr = "Error in polling loop: ${e.message}"
                        Log.e("SmsGateway", errStr, e)
                        SmsEventBus.emitLog(errStr, true)
                    }
                }

                val remainingSeconds = (if (interval > 0) interval else 30000L) / 1000
                for (i in remainingSeconds downTo 1) {
                    if (!isActive || !prefs.isServiceRunning) break
                    SmsEventBus.updateCountdown(i.toInt())
                    delay(1000L)
                }
                SmsEventBus.updateCountdown(null)
            }
            if (!prefs.isServiceRunning) {
                SmsEventBus.emitLog("Service stopped by user.")
                stopSelf()
            }
        }
    }

    private suspend fun sendSms(msgId: Long, receiver: String, body: String, apiKey: String, apiUrl: String) {
        val sentAction = "com.smsgateway.app.SMS_SENT_${msgId}"
        val sentIntent = PendingIntent.getBroadcast(
            applicationContext,
            msgId.toInt(),
            Intent(sentAction),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val resultDeferred = CompletableDeferred<Pair<String, String?>>()
        val sentReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val status = when (resultCode) {
                    Activity.RESULT_OK -> "sent" to null
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "failed" to "Generic failure"
                    SmsManager.RESULT_ERROR_NO_SERVICE -> "failed" to "No service"
                    SmsManager.RESULT_ERROR_NULL_PDU -> "failed" to "Null PDU"
                    SmsManager.RESULT_ERROR_RADIO_OFF -> "failed" to "Radio off"
                    else -> "failed" to "Error code: $resultCode"
                }
                resultDeferred.complete(status)
                try {
                    context?.unregisterReceiver(this)
                } catch (e: Exception) {
                    Log.e("SmsGateway", "Error unregistering receiver: ${e.message}")
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sentReceiver, IntentFilter(sentAction), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(sentReceiver, IntentFilter(sentAction))
        }

        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                applicationContext.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            smsManager.sendTextMessage(receiver, null, body, sentIntent, null)
            SmsEventBus.emitLog("SMS requested for $receiver. Waiting for OS confirmation...")

            // Wait for OS confirmation (max 30s)
            val (finalStatus, finalError) = withTimeoutOrNull(30000) {
                resultDeferred.await()
            } ?: ("failed" to "Timeout waiting for OS confirmation")

            if (finalStatus == "sent") {
                SmsEventBus.emitLog("OS confirmed SMS sent to $receiver.")
            } else {
                SmsEventBus.emitLog("OS failure for $receiver: $finalError", true)
            }

            // Update status API
            val payload = UpdateStatusPayload(msgId, finalStatus, finalError)
            val payloadJson = Gson().toJson(payload)
            SmsEventBus.emitLog("Updating status on server... JSON: $payloadJson")
            
            val api = ApiClient.createService(apiUrl.trim())
            val statusResponse = api.updateSmsStatus("Bearer ${apiKey.trim()}", payload)
            
            if (statusResponse.isSuccessful) {
                SmsEventBus.emitLog("Status updated to '$finalStatus' on server.")
            } else {
                val errBody = statusResponse.errorBody()?.string() ?: "Empty error"
                SmsEventBus.emitLog("Server rejected status update: ${statusResponse.code()} ($errBody)", true)
            }

        } catch (e: Exception) {
            val errStr = "Error during SMS flow for $receiver: ${e.message}"
            Log.e("SmsGateway", errStr, e)
            SmsEventBus.emitLog(errStr, true)
            
            try {
                val api = ApiClient.createService(apiUrl.trim())
                val failPayload = UpdateStatusPayload(msgId, "failed", e.message)
                api.updateSmsStatus("Bearer ${apiKey.trim()}", failPayload)
                SmsEventBus.emitLog("Status updated to 'failed' on server.")
            } catch (apiErr: Exception) {
                Log.e("SmsGateway", "Failed to update error status: ${apiErr.message}")
            }
        } finally {
            try {
                unregisterReceiver(sentReceiver)
            } catch (e: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS Gateway Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(
                    this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Gateway Active")
            .setContentText("Listening for API and incoming SMS")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pendingIntent)
            .build()
    }
}
