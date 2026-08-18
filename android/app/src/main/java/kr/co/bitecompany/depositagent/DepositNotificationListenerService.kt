package kr.co.bitecompany.depositagent

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class DepositNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(statusBarNotification: StatusBarNotification?) {
        val notification = statusBarNotification?.notification ?: return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString("\n") { it.toString() }
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val body = listOf(bigText, lines, text)
            .firstOrNull { !it.isNullOrBlank() }
            .orEmpty()
        if (body.isBlank()) return

        val preferences = AgentPreferences(applicationContext)
        val event = DepositNotificationParser.parse(
            packageName = statusBarNotification.packageName,
            title = title,
            body = body,
            postedAt = statusBarNotification.postTime,
            deviceId = preferences.deviceId,
        ) ?: return

        val store = EventStore(applicationContext)
        if (!store.add(event)) return

        sendBroadcast(
            android.content.Intent(WebhookSender.ACTION_EVENTS_CHANGED)
                .setPackage(packageName),
        )
        WebhookSender.send(applicationContext, event)
    }
}
