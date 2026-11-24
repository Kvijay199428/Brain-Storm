package com.vegasol.brainstorm

import android.app.AppOpsManager
import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vegasol.brainstorm.detect.ContentAccessibilityService
import com.vegasol.brainstorm.detect.DebugProbeDetector
import com.vegasol.brainstorm.detect.YouTubeShortsDetector
import com.vegasol.brainstorm.update.UpdateManager
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.TimeZone

// ───────────── Application + service locator (Phase 0) ─────────────
class BrainStormApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Reconcile.schedule(this)
    }
}

object Graph {
    lateinit var app: Context
    lateinit var prefs: Prefs
    lateinit var db: BrainStormDb
    lateinit var engine: UsageEngine
    lateinit var enforcement: EnforcementController
    lateinit var lockouts: LockoutManager
    lateinit var updates: UpdateManager

    @Synchronized
    fun init(c: Context) {
        if (::app.isInitialized) return
        app = c.applicationContext
        prefs = Prefs(app)
        db = BrainStormDb.get(app)
        engine = UsageEngine(app, db, prefs)
        enforcement = EnforcementProvider.create(app)
        lockouts = LockoutManager(db, prefs, enforcement)
        updates = UpdateManager(app)
    }
}

class Prefs(c: Context) {
    private val p = c.getSharedPreferences("brainstorm", Context.MODE_PRIVATE)
    var onboarded: Boolean get() = p.getBoolean("onb", false); set(v) = p.edit().putBoolean("onb", v).apply()
    var lockHours: Int get() = p.getInt("lockH", 24); set(v) = p.edit().putInt("lockH", v).apply()
    var resetHour: Int get() = p.getInt("resetH", 0); set(v) = p.edit().putInt("resetH", v).apply()
    var mascotAnim: Boolean get() = p.getBoolean("mAnim", true); set(v) = p.edit().putBoolean("mAnim", v).apply()
    var heartbeat: Long get() = p.getLong("hb", 0); set(v) = p.edit().putLong("hb", v).apply()

    /** Combined daily budget across every monitored app, in minutes. Frozen after setup. */
    var budgetMin: Int get() = p.getInt("budgetM", 60); set(v) = p.edit().putInt("budgetM", v).apply()

    /** Set once the one-time wizard finishes: no policy field is editable afterwards. */
    var setupLocked: Boolean get() = p.getBoolean("setupL", false); set(v) = p.edit().putBoolean("setupL", v).apply()

    /** Opt-in beta: try to pause a locked app's media session via granted Notification access. */
    var pauseLockedPlayback: Boolean get() = p.getBoolean("pausePlayback", false); set(v) = p.edit().putBoolean("pausePlayback", v).apply()

    /** Opt-in: let the accessibility service try to click Android's PiP close control. */
    var pipAutoDismiss: Boolean get() = p.getBoolean("pipDismiss", false); set(v) = p.edit().putBoolean("pipDismiss", v).apply()

    // ── Brainy Mascot preferences (UI & presentation only) ──
    var mascotQuality: String get() = p.getString("mQual", "normal") ?: "normal"; set(v) = p.edit().putString("mQual", v).apply()
    var mascotExpressiveness: Float get() = p.getFloat("mEx", 0.8f); set(v) = p.edit().putFloat("mEx", v.coerceIn(0f, 1f)).apply()
    var mascotPlayfulness: Float get() = p.getFloat("mPl", 0.65f); set(v) = p.edit().putFloat("mPl", v.coerceIn(0f, 1f)).apply()
    var mascotCalmness: Float get() = p.getFloat("mCa", 0.85f); set(v) = p.edit().putFloat("mCa", v.coerceIn(0f, 1f)).apply()
    var mascotReduceMotion: Boolean get() = p.getBoolean("mRm", false); set(v) = p.edit().putBoolean("mRm", v).apply()
    var mascotTouchEnabled: Boolean get() = p.getBoolean("mTouch", true); set(v) = p.edit().putBoolean("mTouch", v).apply()

    fun resetMascotPersonality() {
        mascotExpressiveness = 0.8f
        mascotPlayfulness = 0.65f
        mascotCalmness = 0.85f
    }
}

object Perms {
    fun usage(c: Context): Boolean {
        val a = c.getSystemService(AppOpsManager::class.java)
        return a.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Whether the system battery optimiser may leave this app alone. It is informational: Android
     * still lets aggressive OEM builds kill background work, so the UI never promises otherwise.
     */
    fun batteryUnrestricted(c: Context): Boolean {
        val pm = c.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(c.packageName)
    }
    fun overlay(c: Context) = Settings.canDrawOverlays(c)
    fun notif(c: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(c, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * True only when the user has enabled *this app's* content-detection service in Android's
     * accessibility settings. Reading the setting (instead of a local flag) keeps the UI honest:
     * revoking access from the system screen immediately reports counting as unavailable.
     */
    fun accessibility(c: Context): Boolean = accessibilityService(c, ContentAccessibilityService::class.java.name)

    /** Opt-in PiP/lockout window detector. Separate from content detection so either can be off. */
    fun lockoutAccessibility(c: Context): Boolean = accessibilityService(c, LockoutAccessibilityService::class.java.name)

    private fun accessibilityService(c: Context, className: String): Boolean {
        val cr = c.contentResolver
        if (Settings.Secure.getInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false
        val flat = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return flat.split(':').any { entry ->
            val cn = ComponentName.unflattenFromString(entry) ?: return@any false
            cn.packageName == c.packageName && cn.className == className
        }
    }

    /** True only when the user granted Notification access to our listener (needed for media sessions). */
    fun notificationAccess(c: Context): Boolean {
        val flat = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: return false
        val mine = ComponentName(c, PlaybackNotificationListener::class.java)
        return flat.split(':').any { entry -> ComponentName.unflattenFromString(entry) == mine }
    }
}

// ───────────── Room database (Phase 1) ─────────────
@Entity(tableName = "monitored_apps", indices = [Index(value = ["packageName"], unique = true)])
data class MonitoredApp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String, val appName: String, val enabled: Boolean = true,
    val timeLimitMin: Int? = 60, val contentLimit: Int? = null, val updatedAt: Long = 0
)

/**
 * Per-app per-day totals. Since 2.2.0-beta.24 the table also holds apps that are not monitored, so
 * Reports can show whole-device usage and a ranking. Budget and lock maths still read monitored
 * packages only; see `DashboardViewModel` and `Lockout`. [appName] is the label seen at write time,
 * kept so a ranking row still has a name after an app is uninstalled.
 */
@Entity(tableName = "daily_usage", indices = [Index(value = ["packageName", "usageDate"], unique = true)])
data class DailyUsage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String, val usageDate: String,
    val foregroundSeconds: Long = 0, val contentCount: Int = 0, val updatedAt: Long = 0,
    // Declared default matches MIGRATION_2_3, so moving from v2 to v3 passes Room's schema check.
    @ColumnInfo(defaultValue = "''") val appName: String = ""
)

@Entity(tableName = "lockout_records", indices = [Index(value = ["packageName", "status"]), Index(value = ["expiresAt"])])
data class Lockout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String, val triggerType: String, val triggerValue: Long, val limitValue: Long,
    val activatedAt: Long, val expiresAt: Long, val status: String = "ACTIVE", val enforcementNote: String = "",
    @ColumnInfo(defaultValue = "PENDING") val enforcementState: String = "PENDING",
    @ColumnInfo(defaultValue = "NOT_REQUESTED") val visualBlock: String = "NOT_REQUESTED",
    @ColumnInfo(defaultValue = "NOT_AVAILABLE") val pipDetection: String = "NOT_AVAILABLE",
    @ColumnInfo(defaultValue = "NOT_AVAILABLE") val playbackPause: String = "NOT_AVAILABLE",
    @ColumnInfo(defaultValue = "NOT_AVAILABLE") val processTermination: String = "NOT_AVAILABLE",
    @ColumnInfo(defaultValue = "") val attemptId: String = ""
)

@Entity(tableName = "content_events", indices = [Index(value = ["packageName", "usageDate", "contentKey"], unique = true)])
data class ContentEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String, val usageDate: String, val contentKey: String,
    val method: String, val confidence: Float, val detectedAt: Long
)

@Dao interface AppDao {
    @Query("SELECT * FROM monitored_apps ORDER BY appName") fun observeAll(): Flow<List<MonitoredApp>>
    @Query("SELECT * FROM monitored_apps WHERE enabled=1") suspend fun enabled(): List<MonitoredApp>
    @Query("SELECT * FROM monitored_apps WHERE packageName=:p") suspend fun get(p: String): MonitoredApp?
    @Upsert suspend fun upsert(a: MonitoredApp)
}
suspend fun AppDao.save(a: MonitoredApp) = upsert(a.copy(id = get(a.packageName)?.id ?: 0, updatedAt = System.currentTimeMillis()))

@Dao interface UsageDao {
    @Query("SELECT * FROM daily_usage WHERE packageName=:p AND usageDate=:d LIMIT 1") suspend fun get(p: String, d: String): DailyUsage?
    @Upsert suspend fun upsert(u: DailyUsage)
    @Query("SELECT * FROM daily_usage WHERE usageDate=:d") suspend fun day(d: String): List<DailyUsage>
    @Query("SELECT * FROM daily_usage WHERE usageDate=:d") fun observeDay(d: String): Flow<List<DailyUsage>>
    @Query("SELECT * FROM daily_usage WHERE usageDate>=:from") fun observeSince(from: String): Flow<List<DailyUsage>>
    @Query("SELECT * FROM daily_usage WHERE packageName=:p AND usageDate>=:from")
    fun observeSinceFor(p: String, from: String): Flow<List<DailyUsage>>
    @Query("SELECT * FROM daily_usage") fun observeAll(): Flow<List<DailyUsage>>
}

@Dao interface LockoutDao {
    @Query("SELECT * FROM lockout_records WHERE packageName=:p AND status='ACTIVE' LIMIT 1") suspend fun active(p: String): Lockout?
    @Query("SELECT * FROM lockout_records WHERE status='ACTIVE'") suspend fun allActive(): List<Lockout>
    @Query("SELECT * FROM lockout_records WHERE status='ACTIVE'") fun observeActive(): Flow<List<Lockout>>
    @Query("SELECT * FROM lockout_records ORDER BY activatedAt DESC LIMIT 400") fun observeHistory(): Flow<List<Lockout>>
    @Insert suspend fun insert(l: Lockout): Long
    @Query("UPDATE lockout_records SET status='EXPIRED' WHERE status='ACTIVE' AND expiresAt<=:now") suspend fun expireDue(now: Long)
    @Query("UPDATE lockout_records SET enforcementNote=:n WHERE id=:id") suspend fun note(id: Long, n: String)
    @Query("UPDATE lockout_records SET enforcementState=:state, enforcementNote=:note WHERE id=:id")
    suspend fun setEnforcement(id: Long, state: String, note: String)
    @Query("UPDATE lockout_records SET enforcementState=:state WHERE id=:id")
    suspend fun setState(id: Long, state: String)
    @Query("UPDATE lockout_records SET visualBlock=:visual, pipDetection=:pip, playbackPause=:pause, " +
        "processTermination=:process, attemptId=:attempt WHERE id=:id")
    suspend fun setReport(id: Long, visual: String, pip: String, pause: String, process: String, attempt: String)
}

@Dao interface ContentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(e: ContentEvent): Long
}

// Per-hour foreground time, rebuilt from Android's event log on every reconcile. Only the clock
// hour is stored, never an activity title or screen text.
@Entity(tableName = "hourly_usage", indices = [Index(value = ["packageName", "usageDate", "hour"], unique = true)])
data class HourlyUsage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String, val usageDate: String, val hour: Int,
    val foregroundSeconds: Long = 0, val updatedAt: Long = 0
)

@Dao interface HourlyDao {
    @Query("SELECT * FROM hourly_usage WHERE usageDate=:d ORDER BY hour") fun observeDay(d: String): Flow<List<HourlyUsage>>
    @Query("SELECT * FROM hourly_usage WHERE packageName=:p AND usageDate=:d ORDER BY hour")
    fun observeDayFor(p: String, d: String): Flow<List<HourlyUsage>>
    @Query("SELECT * FROM hourly_usage WHERE usageDate=:d") suspend fun day(d: String): List<HourlyUsage>
    @Query("DELETE FROM hourly_usage WHERE usageDate=:d") suspend fun clearDay(d: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(rows: List<HourlyUsage>)
}

@Database(
    entities = [MonitoredApp::class, DailyUsage::class, Lockout::class, ContentEvent::class, HourlyUsage::class],
    version = 5, exportSchema = true
)
abstract class BrainStormDb : RoomDatabase() {
    abstract fun apps(): AppDao
    abstract fun usage(): UsageDao
    abstract fun lockouts(): LockoutDao
    abstract fun content(): ContentDao
    abstract fun hourly(): HourlyDao
    companion object {
        /**
         * v1 stores only daily totals. v2 adds the hourly table; no existing row is touched, so an
         * in-place app update keeps today's usage and the frozen policy intact.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `hourly_usage` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`packageName` TEXT NOT NULL, `usageDate` TEXT NOT NULL, " +
                        "`hour` INTEGER NOT NULL, `foregroundSeconds` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_hourly_usage_packageName_usageDate_hour` ON `hourly_usage` " +
                        "(`packageName`, `usageDate`, `hour`)"
                )
            }
        }

        /**
         * v3 only adds the persisted label on `daily_usage`; existing rows keep their package name
         * and get an empty label, which the UI already falls back to the package for. No policy or
         * usage row is rewritten, so an in-place update keeps the frozen setup intact.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `daily_usage` ADD COLUMN `appName` TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v4 records the confirmed enforcement state per lockout. Existing rows start as PENDING,
         * which makes the next enforcement pass verify them and upgrade the state honestly.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `lockout_records` ADD COLUMN `enforcementState` TEXT NOT NULL DEFAULT 'PENDING'")
            }
        }

        /** v5 records per-capability enforcement results for each lockout. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `lockout_records` ADD COLUMN `visualBlock` TEXT NOT NULL DEFAULT 'NOT_REQUESTED'")
                db.execSQL("ALTER TABLE `lockout_records` ADD COLUMN `pipDetection` TEXT NOT NULL DEFAULT 'NOT_AVAILABLE'")
                db.execSQL("ALTER TABLE `lockout_records` ADD COLUMN `playbackPause` TEXT NOT NULL DEFAULT 'NOT_AVAILABLE'")
                db.execSQL("ALTER TABLE `lockout_records` ADD COLUMN `processTermination` TEXT NOT NULL DEFAULT 'NOT_AVAILABLE'")
                db.execSQL("ALTER TABLE `lockout_records` ADD COLUMN `attemptId` TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile private var i: BrainStormDb? = null
        fun get(c: Context): BrainStormDb = i ?: synchronized(this) {
            i ?: Room.databaseBuilder(c, BrainStormDb::class.java, "brainstorm.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                .also { i = it }
        }
    }
}

// ───────────── Usage monitoring engine (Phase 2) ─────────────
class UsageEngine(private val ctx: Context, private val db: BrainStormDb, private val prefs: Prefs) {
    private val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun dayStart(now: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now; set(Calendar.HOUR_OF_DAY, prefs.resetHour)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis > now) c.add(Calendar.DAY_OF_MONTH, -1)
        return c.timeInMillis
    }
    private fun key(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return "%04d-%02d-%02d".format(c[Calendar.YEAR], c[Calendar.MONTH] + 1, c[Calendar.DAY_OF_MONTH])
    }
    fun dayKey(now: Long) = key(dayStart(now))
    fun dayKeys(n: Int, now: Long = System.currentTimeMillis()): List<String> {
        val c = Calendar.getInstance().apply { timeInMillis = dayStart(now) }
        return (0 until n).map { key(c.timeInMillis).also { c.add(Calendar.DAY_OF_MONTH, -1) } }
    }

    /** Idempotent: recomputes today's foreground time from Android's event log, so restarts never double-count. */
    suspend fun reconcile(now: Long = System.currentTimeMillis()) {
        if (!Perms.usage(ctx)) return
        val apps = db.apps().enabled()
        if (apps.isEmpty()) return
        val date = dayKey(now)
        val labels = DeviceInventory.labels(ctx, apps, now)
        val watched = apps.mapTo(HashSet()) { it.packageName }
        val spans = intervals(labels.keys, dayStart(now), now)
        val zone = TimeZone.getDefault()

        // Merge overlapping spans per package: a main activity and a PiP window must not book the
        // same wall-clock second twice. Hourly buckets come from the same merged intervals.
        val totals = HashMap<String, Long>()
        val perHour = HashMap<Pair<String, Int>, Long>()
        for ((pkg, list) in spans.groupBy { it.packageName }) {
            val merged = UsageVisibilityTracker.merge(
                list.map { UsageVisibilityTracker.TimeSpan(it.startMs, it.endMs) }
            )
            var msTotal = 0L
            for (span in merged) {
                val ms = (span.endMs - span.startMs).coerceAtLeast(0L)
                if (ms <= 0L) continue
                msTotal += ms
                for (slice in UsageBuckets.slices(span.startMs, span.endMs, zone)) {
                    val k = pkg to slice.hour
                    perHour[k] = (perHour[k] ?: 0L) + slice.millis
                }
            }
            totals[pkg] = msTotal / 1000L
        }

        // UsageStats visible time is per-package and can cover PiP. Never add it to event time —
        // they overlap. The larger value is the honest lower bound of on-screen time.
        val visible = visibleSeconds(now, dayStart(now))

        // One row per included app. Monitored apps always keep a row (even at zero) so the Apps tab
        // and reports can show them; other apps only get a row once they have been used, and rows
        // are only rewritten when the number or the label changed.
        val existing = db.usage().day(date).associateBy { it.packageName }
        for ((pkg, label) in labels) {
            val seconds = UsageVisibilityTracker.reconcileSeconds(totals[pkg] ?: 0L, visible[pkg] ?: 0L)
            val cur = existing[pkg]
            if (cur == null && pkg !in watched && seconds <= 0L) continue
            if (cur != null && cur.foregroundSeconds == seconds && cur.appName == label) continue
            val row = cur ?: DailyUsage(packageName = pkg, usageDate = date)
            db.usage().upsert(row.copy(foregroundSeconds = seconds, appName = label, updatedAt = now))
        }
        writeHourly(date, perHour, now)
    }

    /** Per-package visible time from Android's own aggregate stats (PiP included where reported). */
    private fun visibleSeconds(now: Long, from: Long): Map<String, Long> {
        val stats = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, from, now + 1_000L)
        }.getOrNull() ?: return emptyMap()
        return stats.associate { it.packageName to (it.totalTimeVisible / 1_000L) }
    }

    /**
     * Rewrites today's hourly rows only when the numbers changed. Reconcile runs every few seconds
     * while an app is open, so an unconditional delete + insert would churn the disk for nothing.
     */
    private suspend fun writeHourly(date: String, perHour: Map<Pair<String, Int>, Long>, now: Long) {
        val next = perHour
            .filterValues { it >= 1000L }
            .map { (k, ms) ->
                HourlyUsage(
                    packageName = k.first, usageDate = date, hour = k.second,
                    foregroundSeconds = ms / 1000L, updatedAt = now
                )
            }
        val current = db.hourly().day(date)
        val unchanged = current.size == next.size && current.all { c ->
            next.any { it.packageName == c.packageName && it.hour == c.hour && it.foregroundSeconds == c.foregroundSeconds }
        }
        if (unchanged) return
        db.hourly().clearDay(date)
        if (next.isNotEmpty()) db.hourly().insertAll(next)
    }

    private data class Span(val packageName: String, val startMs: Long, val endMs: Long)

    /** Foreground spans from Android's event log, each closed at pause/stop/screen-off or at [to]. */
    private fun intervals(pk: Set<String>, from: Long, to: Long): List<Span> {
        val ev = usm.queryEvents(from, to)
        val e = UsageEvents.Event()
        val open = HashMap<String, Long>()
        val out = ArrayList<Span>()
        fun close(k: String, t: Long) {
            val s = open.remove(k) ?: return
            out.add(Span(k.substringBefore('/'), s, t))
        }
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            val p = e.packageName ?: continue
            val k = p + "/" + e.className
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> if (p in pk) open[k] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> close(k, e.timeStamp)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN, UsageEvents.Event.DEVICE_SHUTDOWN ->
                    open.keys.toList().forEach { close(it, e.timeStamp) }
            }
        }
        open.keys.toList().forEach { close(it, to) }
        return out
    }

    /** Tracks the current foreground package from events in (from, to]. */
    fun pollForeground(from: Long, to: Long, cur: String?): String? {
        var fg = cur
        val ev = usm.queryEvents(from, to)
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> fg = e.packageName
                UsageEvents.Event.ACTIVITY_PAUSED -> if (e.packageName == fg) fg = null
            }
        }
        return fg
    }
}

// ───────────── Limit evaluation (Phase 3) ─────────────
data class LimitEval(val timeExceeded: Boolean, val contentExceeded: Boolean) {
    val shouldLock get() = timeExceeded || contentExceeded          // OR-based: first limit reached wins
    val trigger get() = if (timeExceeded) Triggers.TIME else Triggers.CONTENT
}
object LimitEvaluator {
    fun eval(a: MonitoredApp, u: DailyUsage?, contentSupported: Boolean): LimitEval {
        if (!a.enabled) return LimitEval(false, false)
        val t = a.timeLimitMin?.let { (u?.foregroundSeconds ?: 0L) >= it * 60L } ?: false
        val c = if (contentSupported) a.contentLimit?.let { (u?.contentCount ?: 0) >= it } ?: false else false
        return LimitEval(t, c)
    }
}

// ───────────── Global daily budget (combined foreground time) ─────────────
data class BudgetEval(val usedSeconds: Long, val budgetSeconds: Long) {
    val shouldLock get() = budgetSeconds > 0 && usedSeconds >= budgetSeconds
    val remainingSeconds get() = (budgetSeconds - usedSeconds).coerceAtLeast(0L)
    val percent get() = if (budgetSeconds <= 0) 0f else (usedSeconds * 100f / budgetSeconds)
}

object BudgetEvaluator {
    fun eval(usedSeconds: Long, budgetMin: Int): BudgetEval = BudgetEval(usedSeconds, budgetMin * 60L)
}

/**
 * A budget can only ever fire when it is strictly smaller than the sum of per-app limits:
 * every app is already stopped at its own limit, so total usage can never exceed that sum.
 */
enum class BudgetCheck { VALID, REDUNDANT, UNREACHABLE }

fun budgetCheck(budgetSeconds: Long, perAppLimitsTotalSeconds: Long): BudgetCheck = when {
    budgetSeconds > perAppLimitsTotalSeconds -> BudgetCheck.UNREACHABLE
    budgetSeconds == perAppLimitsTotalSeconds -> BudgetCheck.REDUNDANT
    else -> BudgetCheck.VALID
}

// ───────────── Content detection framework (Phase 5) ─────────────
enum class Support { TIME_ONLY, EXPERIMENTAL, SUPPORTED }
data class ContentObservation(
    val packageName: String,
    val timestamp: Long,
    val contentId: String?,
    /** Normalised resource ids / labels from the accessibility snapshot; never persisted. */
    val markers: Set<String> = emptySet()
)
data class DetectionOutcome(val detected: Boolean, val contentKey: String?, val confidence: Float)
interface ContentDetector {
    val packages: Set<String>
    val support: Support
    /**
     * Lower-case fragments of resource ids and content descriptions that only occur on this app's
     * short-form surface. Kept per detector because every app names its player differently.
     */
    val markers: List<String>
    fun process(o: ContentObservation): DetectionOutcome
}
object Detectors {
    /**
     * Shipped adapters. Every one is EXPERIMENTAL: the labels are not a public contract, so only
     * detectors validated against a real device build are listed here.
     */
    val all: List<ContentDetector> = listOfNotNull(
        YouTubeShortsDetector(),
        if (BuildConfig.DEBUG) DebugProbeDetector() else null
    )
    fun forPackage(pkg: String) = all.filter { pkg in it.packages }
    fun support(pkg: String) = all.firstOrNull { pkg in it.packages }?.support ?: Support.TIME_ONLY
    fun label(pkg: String) = when (support(pkg)) {
        Support.SUPPORTED -> "counting supported"; Support.EXPERIMENTAL -> "counting experimental"
        Support.TIME_ONLY -> "time limit only (counting unavailable)"
    }
}

/** Content counting is opt-in: a detector alone is not enough, the accessibility service must be on. */
object ContentCounting {
    fun enabled(c: Context) = Perms.accessibility(c)
    fun hasDetector(pkg: String) = Detectors.support(pkg) != Support.TIME_ONLY
    fun active(c: Context, pkg: String) = enabled(c) && hasDetector(pkg)
}
object ContentRepository {
    suspend fun record(o: ContentObservation, out: DetectionOutcome, method: String) {
        val key = out.contentKey ?: return
        if (!out.detected) return
        val db = Graph.db
        val date = Graph.engine.dayKey(o.timestamp)
        val id = db.content().insert(ContentEvent(packageName = o.packageName, usageDate = date, contentKey = key, method = method, confidence = out.confidence, detectedAt = o.timestamp))
        if (id != -1L) {
            val name = db.apps().get(o.packageName)?.appName ?: ""
            val cur = db.usage().get(o.packageName, date)
                ?: DailyUsage(packageName = o.packageName, usageDate = date, appName = name)
            db.usage().upsert(cur.copy(contentCount = cur.contentCount + 1, appName = cur.appName.ifBlank { name }, updatedAt = o.timestamp))
        }
    }
}
