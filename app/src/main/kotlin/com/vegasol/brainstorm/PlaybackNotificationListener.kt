package com.vegasol.brainstorm

import android.service.notification.NotificationListenerService

/**
 * No notification content is read or stored. This listener exists only so MediaSessionManager can
 * report which packages have an active media session when the user grants Notification access.
 */
class PlaybackNotificationListener : NotificationListenerService()
