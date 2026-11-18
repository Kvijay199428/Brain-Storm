package com.vegasol.brainstorm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        runCatching { goForeground() }.onFailure { stopSelf(); return START_NOT_STICKY }
        if (job?.isActive != true) job = scope.launch { loop() }
        return START_STICKY
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    /**
     * Protection must outlive a swipe from the recents list. Best effort by design: Android 12+
     * may refuse a background foreground-service start, so [start] swallows the failure and the
     * user is never told a lock is active when the service died.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        start(this)
        super.onTaskRemoved(rootIntent)
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Monitoring", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, CH)
            .setContentTitle("Brain Storm is protecting your focus")
            .setContentText("Monitoring your selected apps")
            .setSmallIcon(R.drawable.ic_launcher).setOngoing(true).build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, 1, n, type)
    }

    private suspend fun loop() {
        Graph.init(this)
        val pm = getSystemService(PowerManager::class.java)
        var fg: String? = null
        var since = System.currentTimeMillis() - 5000
        var tick = 0
        while (currentCoroutineContext().isActive) {
            val now = System.currentTimeMillis()
            if (!pm.isInteractive) {            // screen off: no usage can accrue, poll slowly
                fg = null; since = now; Graph.prefs.heartbeat = now
                delay(5000); continue
            }
            runCatching {
                fg = Graph.engine.pollForeground(since, now, fg); since = now
                if (tick % 8 == 0) { Graph.engine.reconcile(now); Graph.lockouts.evaluateAll(now) }
                if (tick % 5 == 0) Graph.prefs.heartbeat = now
                val p = fg
                if (p != null && p != packageName) {
                    val l = Graph.db.lockouts().active(p)
                    if (l != null && l.expiresAt > now) Blocker.show(this@MonitorService, l)
                }
            }
            tick++
            delay(1000)
        }
    }

    companion object {
        const val CH = "monitoring"
        fun start(c: Context) {
            runCatching { ContextCompat.startForegroundService(c, Intent(c, MonitorService::class.java)) }
        }
    }
}

/** Periodic recovery + lockout reconciliation. Not used for instant enforcement. */
class ReconcileWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        Graph.init(applicationContext)
        val now = System.currentTimeMillis()
        Graph.engine.reconcile(now)
        Graph.lockouts.evaluateAll(now)
        Graph.lockouts.reapplyActive()
        MonitorService.start(applicationContext)
        return Result.success()
    }
}
object Reconcile {
    fun schedule(c: Context) {
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(
            "reconcile", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReconcileWorker>(15, TimeUnit.MINUTES).build()
        )
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        // Reboot and in-app update both take monitoring down. Both must bring it back, or the app
        // would silently stop protecting until the next launch.
        if (i.action != Intent.ACTION_BOOT_COMPLETED && i.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Graph.init(c)
        val r = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Graph.lockouts.evaluateAll()
                Graph.lockouts.reapplyActive()
                MonitorService.start(c)
            } finally { r.finish() }
        }
    }
}
