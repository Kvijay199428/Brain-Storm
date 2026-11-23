package com.vegasol.brainstorm

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.room.withTransaction

// ───────────── Enforcement abstraction (Phase 4) ─────────────
enum class Health { ACTIVE, PARTIALLY_ACTIVE, PERMISSION_REQUIRED, UNAVAILABLE }

/** Confirmed OS-level state of a lockout. A requested restriction is never "enforced". */
enum class EnforcementState { PENDING, ENFORCED, PARTIAL, EXPIRED, RELEASED }

data class EnfResult(val ok: Boolean, val note: String) {
    val state: EnforcementState get() = if (ok) EnforcementState.ENFORCED else EnforcementState.PARTIAL
}

interface EnforcementController {
    val mode: String
    suspend fun apply(l: Lockout): EnfResult
    suspend fun remove(l: Lockout): EnfResult
    fun health(): Health
}

/** Best-effort blocking for Play Store builds. Never claims success when permissions are missing. */
class ConsumerEnforcement(private val c: Context) : EnforcementController {
    override val mode = "CONSUMER"
    override suspend fun apply(l: Lockout) =
        if (!Perms.overlay(c)) EnfResult(false, "overlay permission required")
        // An overlay can cover the screen but cannot stop a PiP/audio session. Report PARTIAL.
        else EnfResult(false, "blocks on next open; playback not terminated")
    override suspend fun remove(l: Lockout) = EnfResult(true, "")
    override fun health(): Health = when {
        !Perms.usage(c) || !Perms.overlay(c) -> Health.PERMISSION_REQUIRED
        System.currentTimeMillis() - Graph.prefs.heartbeat > 60_000 -> Health.PARTIALLY_ACTIVE
        else -> Health.ACTIVE
    }
}

/** Overlay shim (grants background-activity-start exemption) + full-screen BlockingActivity with the mascot. */
object Blocker {
    private var last = 0L
    private var overlay: View? = null
    fun show(c: Context, l: Lockout) {
        val t = SystemClock.elapsedRealtime()
        if (t - last < 2500) return
        last = t
        Handler(Looper.getMainLooper()).post {
            val wm = c.getSystemService(WindowManager::class.java)
            if (Settings.canDrawOverlays(c)) {
                runCatching { overlay?.let { wm.removeView(it) } }
                // The badge shows the icon of the app that was just blocked, not ours, so the
                // message is unmistakably about the app the user was in a second ago.
                val icon = runCatching { c.packageManager.getApplicationIcon(l.packageName) }.getOrNull()
                    ?: runCatching { c.packageManager.getApplicationIcon(c.packageName) }.getOrNull()
                val density = c.resources.displayMetrics.density
                val view = LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setBackgroundColor(0xFF1A1238.toInt())
                    if (icon != null) {
                        addView(ImageView(c).apply {
                            setImageDrawable(icon)
                            layoutParams = LinearLayout.LayoutParams(
                                (96 * density).toInt(), (96 * density).toInt()
                            ).apply { bottomMargin = (24 * density).toInt() }
                        })
                    }
                    addView(TextView(c).apply {
                        text = c.getString(R.string.overlay_locked)
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                        textSize = 26f
                    })
                }
                val lp = WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.OPAQUE
                )
                runCatching { wm.addView(view, lp); overlay = view }
                Handler(Looper.getMainLooper()).postDelayed({
                    runCatching { wm.removeView(view) }; if (overlay === view) overlay = null
                }, 1800)
            }
            runCatching {
                c.startActivity(
                    Intent(c, BlockingActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra("pkg", l.packageName)
                )
            }
        }
    }
}

// ───────────── Lockout manager: the ONLY writer of restriction state ─────────────
object Triggers {
    const val TIME = "TIME"
    const val CONTENT = "CONTENT"
    const val BUDGET = "BUDGET"
}

class LockoutManager(private val db: BrainStormDb, private val prefs: Prefs, private val enf: EnforcementController) {
    private val enforcer = LockoutEnforcer(db, enf)

    suspend fun evaluateAll(now: Long = System.currentTimeMillis()) {
        expireDue(now)
        evaluateBudget(now)
        val date = Graph.engine.dayKey(now)
        for (a in db.apps().enabled()) {
            if (db.lockouts().active(a.packageName) != null) continue
            val u = db.usage().get(a.packageName, date)
            val ev = LimitEvaluator.eval(a, u, Detectors.support(a.packageName) == Support.SUPPORTED)
            if (ev.shouldLock) activate(a, ev, u, now)
        }
    }

    /**
     * Combined-budget rule. When total foreground time across all monitored apps reaches the
     * daily budget, every monitored app locks for the fixed duration — the umbrella rule runs
     * before the per-app rules so a budget lockout is never half-applied.
     */
    suspend fun evaluateBudget(now: Long = System.currentTimeMillis()) {
        val budgetSec = prefs.budgetMin * 60L
        if (budgetSec <= 0) return
        val apps = db.apps().enabled()
        if (apps.isEmpty()) return
        val date = Graph.engine.dayKey(now)
        val used = apps.sumOf { db.usage().get(it.packageName, date)?.foregroundSeconds ?: 0L }
        if (!BudgetEvaluator.eval(used, prefs.budgetMin).shouldLock) return
        val open = apps.filter { db.lockouts().active(it.packageName) == null }
        if (open.isEmpty()) return
        val expiresAt = now + prefs.lockHours * 3_600_000L
        val recs = open.map {
            Lockout(
                packageName = it.packageName, triggerType = Triggers.BUDGET,
                triggerValue = used, limitValue = budgetSec,
                activatedAt = now, expiresAt = expiresAt
            )
        }
        val saved = db.withTransaction {
            recs.mapNotNull { r ->
                if (db.lockouts().active(r.packageName) != null) null
                else r.copy(id = db.lockouts().insert(r))
            }
        }
        applyAll(saved)
    }

    private suspend fun activate(a: MonitoredApp, ev: LimitEval, u: DailyUsage?, now: Long) {
        val time = ev.trigger == Triggers.TIME
        val rec = Lockout(
            packageName = a.packageName, triggerType = ev.trigger,
            triggerValue = if (time) (u?.foregroundSeconds ?: 0L) else (u?.contentCount ?: 0).toLong(),
            limitValue = if (time) (a.timeLimitMin ?: 0) * 60L else (a.contentLimit ?: 0).toLong(),
            activatedAt = now, expiresAt = now + prefs.lockHours * 3_600_000L   // fixed duration from activation, not midnight
        )
        val saved = db.withTransaction {
            if (db.lockouts().active(a.packageName) != null) null else rec.copy(id = db.lockouts().insert(rec))
        } ?: return
        applyAll(listOf(saved))
    }

    private suspend fun applyAll(rows: List<Lockout>) {
        rows.forEach { enforcer.enforce(it) }
    }

    /**
     * Re-check every active lockout independently of which app is in the foreground. This repairs
     * PENDING/PARTIAL rows and keeps Device Owner suspension alive after process death.
     */
    suspend fun enforceActive(now: Long = System.currentTimeMillis()) {
        for (l in db.lockouts().allActive()) {
            if (l.expiresAt <= now) continue
            if (LockoutEnforcer.stateOf(l) != EnforcementState.ENFORCED) enforcer.enforce(l)
        }
    }

    suspend fun expireDue(now: Long) {
        val due = db.lockouts().allActive().filter { it.expiresAt <= now }
        if (due.isEmpty()) return
        db.withTransaction { db.lockouts().expireDue(now) }
        due.forEach { l ->
            db.lockouts().setState(l.id, EnforcementState.EXPIRED.name)
            enforcer.release(l)
        }
    }

    /** After reboot / process death: repair OS-level enforcement from the database source of truth. */
    suspend fun reapplyActive() {
        db.lockouts().allActive().forEach { enforcer.enforce(it) }
    }
}
