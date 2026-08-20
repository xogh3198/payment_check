package kr.co.bitecompany.depositagent

import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Instant
import java.util.concurrent.TimeUnit

class EventDeliveryWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : Worker(appContext, workerParameters) {
    override fun doWork(): Result {
        val eventId = inputData.getString(KEY_EVENT_ID) ?: return Result.failure()
        val preferences = AgentPreferences(applicationContext)
        if (!preferences.isEnrolled) return Result.failure()
        val store = EventStore(applicationContext)
        val event = store.find(eventId) ?: return Result.success()
        if (event.deliveryStatus == "DELIVERED") return Result.success()

        return try {
            val response = AgentApiClient.sendEvent(preferences, event)
            when {
                response.successful -> {
                    store.updateDelivery(
                        eventId = event.id,
                        status = "DELIVERED",
                        httpStatus = response.status,
                        error = null,
                        deliveredAt = Instant.now().toString(),
                    )
                    notifyEventsChanged()
                    Result.success()
                }
                response.retryable && runAttemptCount < MAX_RETRY_ATTEMPTS -> {
                    store.updateDelivery(
                        eventId = event.id,
                        status = "RETRYING",
                        httpStatus = response.status,
                        error = response.responseBody.ifBlank { "HTTP ${response.status}" }.take(500),
                        deliveredAt = null,
                    )
                    notifyEventsChanged()
                    Result.retry()
                }
                else -> {
                    store.updateDelivery(
                        eventId = event.id,
                        status = "REJECTED",
                        httpStatus = response.status,
                        error = response.responseBody.ifBlank { "HTTP ${response.status}" }.take(500),
                        deliveredAt = null,
                    )
                    notifyEventsChanged()
                    Result.failure()
                }
            }
        } catch (error: AgentApiException) {
            store.updateDelivery(
                eventId = event.id,
                status = if (runAttemptCount < MAX_RETRY_ATTEMPTS) "RETRYING" else "FAILED",
                httpStatus = error.status,
                error = error.message?.take(500),
                deliveredAt = null,
            )
            notifyEventsChanged()
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private fun notifyEventsChanged() {
        applicationContext.sendBroadcast(
            Intent(AgentWorkScheduler.ACTION_EVENTS_CHANGED)
                .setPackage(applicationContext.packageName),
        )
    }

    companion object {
        const val KEY_EVENT_ID = "event_id"
        private const val MAX_RETRY_ATTEMPTS = 10
    }
}

class AgentHeartbeatWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : Worker(appContext, workerParameters) {
    override fun doWork(): Result {
        val preferences = AgentPreferences(applicationContext)
        if (!preferences.isEnrolled) return Result.success()

        return try {
            val response = AgentApiClient.sendHeartbeat(
                preferences = preferences,
                notificationAccessEnabled = AgentDeviceStatus.notificationAccessEnabled(applicationContext),
                batteryOptimizationIgnored = AgentDeviceStatus.batteryOptimizationIgnored(applicationContext),
                appVersion = BuildConfig.VERSION_NAME,
            )
            when {
                response.successful -> Result.success()
                response.retryable -> Result.retry()
                else -> Result.failure()
            }
        } catch (_: AgentApiException) {
            Result.retry()
        }
    }
}

object AgentWorkScheduler {
    const val ACTION_EVENTS_CHANGED = "kr.co.bitecompany.depositagent.EVENTS_CHANGED"
    private const val HEARTBEAT_WORK = "payment-agent-heartbeat"

    fun enqueueEvent(context: Context, eventId: String) {
        val request = OneTimeWorkRequestBuilder<EventDeliveryWorker>()
            .setInputData(Data.Builder().putString(EventDeliveryWorker.KEY_EVENT_ID, eventId).build())
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "payment-event-$eventId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun enqueuePending(context: Context) {
        EventStore(context.applicationContext).pending().forEach { enqueueEvent(context, it.id) }
    }

    fun scheduleHeartbeat(context: Context) {
        val request = PeriodicWorkRequestBuilder<AgentHeartbeatWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraint())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            HEARTBEAT_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        enqueueHeartbeatNow(context)
    }

    fun enqueueHeartbeatNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<AgentHeartbeatWorker>()
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "$HEARTBEAT_WORK-now",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancelHeartbeat(context: Context) {
        val manager = WorkManager.getInstance(context.applicationContext)
        manager.cancelUniqueWork(HEARTBEAT_WORK)
        manager.cancelUniqueWork("$HEARTBEAT_WORK-now")
    }

    private fun networkConstraint(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
