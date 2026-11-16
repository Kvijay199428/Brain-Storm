package com.vegasol.brainstorm

import android.content.Context

object EnforcementProvider {
    fun create(c: Context): EnforcementController = ConsumerEnforcement(c)
}
