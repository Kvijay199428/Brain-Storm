package com.vegasol.brainstorm

import android.app.AppOpsManager
import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.room.*
import com.vegasol.brainstorm.detect.ContentAccessibilityService
import com.vegasol.brainstorm.detect.DebugProbeDetector
import com.vegasol.brainstorm.detect.YouTubeShortsDetector
import com.vegasol.brainstorm.update.UpdateManager
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

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
}

object Perms {
    fun usage(c: Context): Boolean {
        val a = c.getSystemService(AppOpsManager::class.java)
        return a.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
    }
    fun overlay(c: Context) = Settings.canDrawOverlays(c)
    fun notif(c: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(c, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * True only when the user has enabled *this app's* content-detection service in Android's
     * accessibility settings. Reading the setting (instead of a local flag) keeps the UI honest:
     * revoking access from the system screen immediately reports counting as unavailable.
     */
    fun accessibility(c: Context): Boolean {
        val cr = c.contentResolver
        if (Settings.Secure.getInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false
        val flat = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return flat.split(':').any { entry ->
            val cn = ComponentName.unflattenFromString(entry) ?: return@any false
            cn.packageName == c.packageName && cn.className == ContentAccessibilityService::class.java.name
        }
    }
}

// ───────────── Room database (Phase 1) ─────────────
@Entity(tableName = "monitored_apps", indices = [Index(value = ["packageName"], unique = true)])
data class MonitoredApp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String, val appName: String, val enabled: Boolean = true,
    val timeLimitMin: Int? = 60, val contentLimit: Int? = null, val updatedAt: Long = 0
)

@Entity(tableName = "daily_usage", indices = [Index(value = ["packageName", "usageDate"], unique = true)])
data class DailyUsage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String, val usageDate: String,
    val foregroundSeconds: Long = 0, val contentCount: Int = 0, val updatedAt: Long = 0
)

@Entity(tableName = "lockout_records", indices = [Index(value = ["packageName", "status"]), Index(value = ["expiresAt"])])
data class Lockout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String, val triggerType: String, val triggerValue: Long, val limitValue: Long,
    val activatedAt: Long, val expiresAt: Long, val status: String = "ACTIVE", val enforcementNote: String = ""
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
    @Query("SELECT * FROM daily_usage WHERE usageDate=:d") fun observeDay(d: String): Flow<List<DailyUsage>>
    @Query("SELECT * FROM daily_usage WHERE usageDate>=:from") fun observeSince(from: String): Flow<List<DailyUsage>>
}

@Dao interface LockoutDao {
    @Query("SELECT * FROM lockout_records WHERE packageName=:p AND status='ACTIVE' LIMIT 1") suspend fun active(p: String): Lockout?
    @Query("SELECT * FROM lockout_records WHERE status='ACTIVE'") suspend fun allActive(): List<Lockout>
    @Query("SELECT * FROM lockout_records WHERE status='ACTIVE'") fun observeActive(): Flow<List<Lockout>>
    @Query("SELECT * FROM lockout_records ORDER BY activatedAt DESC LIMIT 400") fun observeHistory(): Flow<List<Lockout>>
    @Insert suspend fun insert(l: Lockout): Long
    @Query("UPDATE lockout_records SET status='EXPIRED' WHERE status='ACTIVE' AND expiresAt<=:now") suspend fun expireDue(now: Long)
    @Query("UPDATE lockout_records SET enforcementNote=:n WHERE id=:id") suspend fun note(id: Long, n: String)
}

@Dao interface ContentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(e: ContentEvent): Long
}

@Database(entities = [MonitoredApp::class, DailyUsage::class, Lockout::class, ContentEvent::class], version = 1, exportSchema = true)
abstract class BrainStormDb : RoomDatabase() {
    abstract fun apps(): AppDao
    abstract fun usage(): UsageDao
    abstract fun lockouts(): LockoutDao
    abstract fun content(): ContentDao
    companion object {
        @Volatile private var i: BrainStormDb? = null
        fun get(c: Context): BrainStormDb = i ?: synchronized(this) {
            i ?: Room.databaseBuilder(c, BrainStormDb::class.java, "brainstorm.db").build().also { i = it }
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
        val totals = secondsByPackage(apps.map { it.packageName }.toSet(), dayStart(now), now)
        val date = dayKey(now)
        for (a in apps) {
            val cur = db.usage().get(a.packageName, date) ?: DailyUsage(packageName = a.packageName, usageDate = date)
            db.usage().upsert(cur.copy(foregroundSeconds = totals[a.packageName] ?: 0L, updatedAt = now))
        }
    }

    private fun secondsByPackage(pk: Set<String>, from: Long, to: Long): Map<String, Long> {
        val ev = usm.queryEvents(from, to)
        val e = UsageEvents.Event()
        val open = HashMap<String, Long>()
        val tot = HashMap<String, Long>()
        fun close(k: String, t: Long) {
            val s = open.remove(k) ?: return
            val p = k.substringBefore('/')
            tot[p] = (tot[p] ?: 0L) + (t - s).coerceAtLeast(0L) / 1000L
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
        return tot
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
    fun process(o: ContentObservation): DetectionOutcome
}
object Detectors {
    /**
     * Shipped adapters. Only the YouTube Shorts detector exists today and it is EXPERIMENTAL:
     * it is validated in debug builds with a probe surface, not against every YouTube release.
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
            val cur = db.usage().get(o.packageName, date) ?: DailyUsage(packageName = o.packageName, usageDate = date)
            db.usage().upsert(cur.copy(contentCount = cur.contentCount + 1, updatedAt = o.timestamp))
        }
    }
}
