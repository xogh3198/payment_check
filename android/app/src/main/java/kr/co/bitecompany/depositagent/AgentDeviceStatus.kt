package kr.co.bitecompany.depositagent

import android.content.Context
import android.os.PowerManager
import android.provider.Settings

object AgentDeviceStatus {
    fun notificationAccessEnabled(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        return enabledListeners.contains(context.packageName)
    }

    fun batteryOptimizationIgnored(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
