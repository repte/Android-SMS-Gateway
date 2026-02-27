package com.smsgateway.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SmsEventBus {
    
    private val _logs = MutableSharedFlow<LogEvent>(extraBufferCapacity = 100)
    val logs: SharedFlow<LogEvent> = _logs
    
    data class LogEvent(val timestamp: Long, val message: String, val isError: Boolean = false) {
        val formattedTime: String
            get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }

    private val _logHistory = MutableStateFlow<List<LogEvent>>(listOf(LogEvent(System.currentTimeMillis(), "System Initialized")))
    val logHistory: StateFlow<List<LogEvent>> = _logHistory.asStateFlow()

    private val _syncCountdown = MutableStateFlow<Int?>(null)
    val syncCountdown: StateFlow<Int?> = _syncCountdown.asStateFlow()

    fun emitLog(message: String, isError: Boolean = false) {
        val event = LogEvent(System.currentTimeMillis(), message, isError)
        _logs.tryEmit(event)
        
        val current = _logHistory.value.toMutableList()
        current.add(event)
        if (current.size > 200) current.removeAt(0)
        _logHistory.value = current
    }

    fun updateCountdown(seconds: Int?) {
        _syncCountdown.value = seconds
    }
}
