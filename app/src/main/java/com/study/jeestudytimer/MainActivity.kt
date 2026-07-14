package com.study.jeestudytimer

import android.Manifest
import android.content.*
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var timerService: TimerService? = null
    private var isBound by mutableStateOf(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TimerService.TimerBinder
            timerService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            timerService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val intent = Intent(this, TimerService::class.java)
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F0F0F)) {
                MainAppScreen(timerService, isBound)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}

@Composable
fun MainAppScreen(service: TimerService?, isBound: Boolean) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF1A1A1A)) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Timer, contentDescription = "Stopwatch") },
                    label = { Text("Stopwatch") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.HourglassBottom, contentDescription = "Timer") },
                    label = { Text("Timer") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History") }
                )
            }
        },
        containerColor = Color(0xFF0F0F0F)
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> StopwatchTab(service, isBound)
                1 -> TimerPlaceholderTab()
                2 -> HistoryTab()
            }
        }
    }
}

@Composable
fun StopwatchTab(service: TimerService?, isBound: Boolean) {
    var displayTime by remember { mutableStateOf("00:00:00") }
    var isRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isBound, service) {
        if (isBound && service != null) {
            isRunning = service.isRunning
            displayTime = service.formatMillis(service.elapsedTime)
            service.onTimeTick = { time ->
                displayTime = service.formatMillis(time)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(280.dp)
                .background(Color(0xFF1E1E1E), shape = CircleShape)
        ) {
            Text(
                text = displayTime,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64B5F6)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    service?.resetTimer(saveToDb = true)
                    isRunning = false
                    displayTime = "00:00:00"
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text("Save & Reset", color = Color.White)
            }

            Button(
                onClick = {
                    if (isRunning) {
                        service?.pauseTimer()
                    } else {
                        service?.startTimer()
                    }
                    isRunning = !isRunning
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                modifier = Modifier.size(72.dp),
                shape = CircleShape
            ) {
                Text(if (isRunning) "⏸" else "▶", fontSize = 24.sp, color = Color.White)
            }
        }
    }
}

@Composable
fun TimerPlaceholderTab() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Countdown Timer View", color = Color.Gray, fontSize = 18.sp)
    }
}

@Composable
fun HistoryTab() {
    val context = LocalContext.current
    val db = remember { StudyDatabase.getDatabase(context) }
    val historyList by db.studyDao().getAllSessions().collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("JEE Study History Logs", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))

        if (historyList.isEmpty()) {
            Text("No logged sessions yet. Keep up the grind! 🎯", color = Color.Gray)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(historyList) { session ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Started: ${session.startTimeFormatted}", color = Color(0xFF64B5F6), fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))

                            val hours = (session.durationMillis / 3600000)
                            val minutes = (session.durationMillis % 3600000) / 60000
                            val seconds = (session.durationMillis % 60000) / 1000

                            Text("Total Time: ${String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)}", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Breaks Taken: ${session.totalBreaksCount}", color = Color.LightGray)

                            if (session.breakLogs.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Break Records:\n${session.breakLogs.trim()}", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}
