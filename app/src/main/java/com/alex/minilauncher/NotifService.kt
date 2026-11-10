package com.alex.minilauncher

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

object Notifs {
    @Volatile var counts: Map<String, Int> = emptyMap()
    var onChange: (() -> Unit)? = null
}

class NotifService : NotificationListenerService() {
    override fun onListenerConnected() { sync() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { sync() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { sync() }

    private fun sync() {
        val m = HashMap<String, Int>()
        try {
            activeNotifications?.forEach { n ->
                val f = n.notification.flags
                if (!n.isOngoing && (f and Notification.FLAG_GROUP_SUMMARY) == 0) m[n.packageName] = (m[n.packageName] ?: 0) + 1
            }
        } catch (e: Exception) {}
        Notifs.counts = m
        Handler(Looper.getMainLooper()).post { Notifs.onChange?.invoke() }
    }
}
