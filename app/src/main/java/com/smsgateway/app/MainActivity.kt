package com.smsgateway.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.smsgateway.app.api.ApiClient
import com.smsgateway.app.api.InboundMessage
import com.smsgateway.app.api.OutboundMessage
import android.util.Log
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var prefs: AppPreferences

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                startGatewayService()
            } else {
                Toast.makeText(this, "Permissions denied. Cannot start service.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPreferences(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppScreen(
                        prefs = prefs,
                        onStartService = { checkPermissionsAndStart() },
                        onStopService = { stopGatewayService() }
                    )
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val requiredPermissions = mutableListOf(
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.SEND_SMS
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startGatewayService()
        } else {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startGatewayService() {
        prefs.isServiceRunning = true
        val intent = Intent(this, SmsGatewayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopGatewayService() {
        val intent = Intent(this, SmsGatewayService::class.java)
        stopService(intent)
        prefs.isServiceRunning = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    prefs: AppPreferences,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    var isRunning by remember { mutableStateOf(prefs.isServiceRunning) }
    var currentTab by remember { mutableStateOf("Gateway") }
    
    // Listen to preferences changes in a real app, but for now we'll update it when buttons are clicked
    LaunchedEffect(prefs.isServiceRunning) {
        isRunning = prefs.isServiceRunning
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMS Gateway", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            if (isRunning) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Gateway") },
                        label = { Text("Gateway") },
                        selected = currentTab == "Gateway",
                        onClick = { currentTab = "Gateway" }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.List, contentDescription = "Inbound") },
                        label = { Text("Inbound") },
                        selected = currentTab == "Inbound",
                        onClick = { currentTab = "Inbound" }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Send, contentDescription = "Outbound") },
                        label = { Text("Outbound") },
                        selected = currentTab == "Outbound",
                        onClick = { currentTab = "Outbound" }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentTab) {
                "Gateway" -> GatewayTab(prefs, isRunning, {
                    isRunning = true
                    onStartService()
                }, {
                    isRunning = false
                    onStopService()
                })
                "Inbound" -> InboundTab(prefs)
                "Outbound" -> OutboundTab(prefs)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatewayTab(
    prefs: AppPreferences,
    isRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    var apiUrl by remember { mutableStateOf(prefs.apiUrl) }
    var apiKey by remember { mutableStateOf(prefs.apiKey) }
    var interval by remember { mutableStateOf(prefs.intervalSeconds.toString()) }
    var isVerifying by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Console Logs State
    val logHistory by SmsEventBus.logHistory.collectAsState()
    val listState = rememberLazyListState()

    val countdown by SmsEventBus.syncCountdown.collectAsState()

    LaunchedEffect(logHistory.size) {
        if (logHistory.isNotEmpty()) {
            listState.animateScrollToItem(logHistory.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
            
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = "Status Icon",
                        tint = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (isRunning) "Service is Active" else "Service is Stopped",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isRunning) "Gateway is listening for SMS" else "Configure and start the gateway",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Configuration",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = apiUrl,
                onValueChange = { apiUrl = it; prefs.apiUrl = it },
                label = { Text("API Base URL") },
                enabled = !isRunning,
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; prefs.apiKey = it },
                label = { Text("API Key") },
                enabled = !isRunning,
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = interval,
                onValueChange = { 
                    interval = it
                    it.toIntOrNull()?.let { intVal -> prefs.intervalSeconds = intVal }
                },
                label = { Text("Sync Interval (seconds)") },
                enabled = !isRunning,
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Button(
                onClick = {
                    if (isRunning) {
                        onStopService()
                    } else {
                        coroutineScope.launch {
                            isVerifying = true
                            try {
                                val service = ApiClient.createService(apiUrl.trim())
                                val response = withContext(Dispatchers.IO) {
                                    service.verifyApiKey("Bearer ${apiKey.trim()}")
                                }
                                
                                if (response.isSuccessful) {
                                    onStartService()
                                    Toast.makeText(context, "API Key Verified. Gateway Started!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Invalid API Key! Connection Refused.", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                val errorMsg = "Network Error: ${e.message}"
                                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                android.util.Log.e("SMSGateway", "Verification failed", e)
                            } finally {
                                isVerifying = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isVerifying,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp), 
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Verifying...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRunning) "Stop Gateway Service" else "Start Gateway Service",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // Live Console Window
            if (isRunning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live Console",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    if (countdown != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Next Sync: ${countdown}s",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            text = "Syncing...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth().height(250.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    ) {
                        items(logHistory) { log ->
                            Text(
                                text = "[${log.formattedTime}] ${log.message}",
                                color = if (log.isError) Color(0xFFEF4444) else Color(0xFF10B981),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboundTab(prefs: AppPreferences) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<InboundMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val service = ApiClient.createService(prefs.apiUrl.trim())
            val response = service.getInboundHistory("Bearer ${prefs.apiKey.trim()}")
            if (response.isSuccessful) {
                messages = response.body()?.messages ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e("SMSGateway", "Error fetching inbound", e)
        } finally {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Inbound SMS History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No inbound messages found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages) { msg ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(msg.sender, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(msg.created_at, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(msg.body)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutboundTab(prefs: AppPreferences) {
    val context = LocalContext.current
    var messages by remember { mutableStateOf<List<OutboundMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val service = ApiClient.createService(prefs.apiUrl.trim())
            val response = service.getOutboundHistory("Bearer ${prefs.apiKey.trim()}")
            if (response.isSuccessful) {
                messages = response.body()?.messages ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e("SMSGateway", "Error fetching outbound", e)
        } finally {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Outbound SMS History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No outbound messages found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages) { msg ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(msg.receiver, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Badge(
                                    containerColor = when(msg.status.uppercase()) {
                                        "SENT" -> Color(0xFF10B981)
                                        "FAILED" -> Color(0xFFEF4444)
                                        else -> Color(0xFFF59E0B)
                                    }
                                ) {
                                    Text(msg.status, color = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(msg.body)
                            if (!msg.error_message.isNullOrEmpty()) {
                                Text("Error: ${msg.error_message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Sent: ${msg.created_at}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
