package com.vegasol.brainstorm

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide launch signal, owned by [MainActivity] and set by [feature.dashboard.DashboardViewModel].
 *
 * The launch splash stays on screen until the dashboard has something true to show. Room needs a
 * beat to open and query on a cold start; without this the splash dropped on the first Compose frame
 * and left an empty screen with a progress bar underneath it, which read as the app being broken.
 */
object Startup {
    /** True once the dashboard has received its first database emission. */
    val dashboardReady = AtomicBoolean(false)
}
