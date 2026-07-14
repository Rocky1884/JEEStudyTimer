package com.study.jeestudytimer

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class TimerService : Service() {

    private val binder = TimerBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var tickerJob: Job? = null

    var isRunning = false
    var elapsedTime = 0L
    private var lastTimestamp = 0L

    var startTimeString = ""
    var breakCount = 0
    val breakStringBuilder = StringBuilder()
    private var breakStartTime = 0L

    var onTimeTick: ((Long) -> Unit)? = null

    inner class TimerBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    fun startTimer() {
        if (isRunning) return
        if (elapsedTime == 0L) {
            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            startTimeString = sdf.format(Date()) + " IST"
            breakCount = 0
            breakStringBuilder.clear()
        } else {
            if (breakStartTime > 0L) {
                val breakDuration = (System.currentTimeMillis() - breakStartTime) / 1000
                breakStringBuilder.append("Break #${breakCount}: $breakDuration sec\n")
            }
        }

        isRunning = true
        lastTimestamp = System.currentTimeMillis()
        startForeground(1001, buildNotification())

        tickerJob = serviceScope.launch {
            while (isRunning) {
                val now = System.currentTimeMillis()
                elapsedTime += now - lastTimestamp
                lastTimestamp = now
                onTimeTick?.invoke(elapsedTime)
                updateNotification()
                delay(1000)
            }
        }
    }

    fun pauseTimer() {
        if (!isRunning) return
        isRunning = false
        breakCount++
        breakStartTime = System.currentTimeMillis()
        tickerJob?.cancel()
        updateNotification()
    }

    fun resetTimer(saveToDb: Boolean = false) {
        isRunning = false
        tickerJob?.cancel()

        if (saveToDb && elapsedTime > 1000L) {
            val session = StudySession(
                startTimeFormatted = startTimeString,
                durationMillis = elapsedTime,
                type = "Stopwatch",
                totalBreaksCount = breakCount,
                breakLogs = breakStringBuilder.toString()
            )
            CoroutineScope(Dispatchers.IO).launch {
                StudyDatabase.getDatabase(applicationContext).studyDao().insertSession(session)
            }
        }

        elapsedTime = 0L
        breakCount = 0
        breakStringBuilder.clear()
        onTimeTick?.invoke(0L)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1001, buildNotification())
    }

    private fun buildNotification(): Notification {
        val formattedTime = formatMillis(elapsedTime)
        val statusText = if (isRunning) "Running" else "Paused"

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, "STUDY_TIMER_CHANNEL")
            .setContentTitle("JEE Study Stopwatch")
            .setContentText("$formattedTime ($statusText)")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "STUDY_TIMER_CHANNEL",
            "Study Tracker Status Engine",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun formatMillis(ms: Long): String {
        val hours = (ms / 3600000)
        val minutes = (ms % 3600000) / 60000
        val seconds = (ms % 60000) / 1000
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
