package com.nutriquest.app

// ============================================================================
//  F9 — Daily reminder notification (Joe)
//
//  WorkManager runs DailyReminderWorker once a day at the time chosen in
//  Profile. The worker reads today's meals and profile from the phone and
//  posts one notification:
//      no meals yet  → a nudge to log the next meal
//      meals logged  → today's score so far + the goal reminder from
//                      GoalFeedback.kt, in the user's roast language
//  Nothing leaves the phone. Needs the notification permission (Android 13+).
// ============================================================================

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

internal data class ReminderSettings(val enabled: Boolean = false, val time: LocalTime = LocalTime.of(20, 0)) {
    companion object {
        /** Times offered in Profile. */
        val CHOICES: List<LocalTime> = listOf(LocalTime.of(12, 30), LocalTime.of(18, 0), LocalTime.of(20, 0), LocalTime.of(21, 30))
    }
}

internal class ReminderStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nutriquest_reminders", Context.MODE_PRIVATE)

    fun load() = ReminderSettings(
        enabled = prefs.getBoolean("enabled", false),
        time = runCatching { LocalTime.parse(prefs.getString("time", null)) }.getOrDefault(ReminderSettings().time)
    )

    fun save(settings: ReminderSettings) {
        prefs.edit().putBoolean("enabled", settings.enabled).putString("time", settings.time.toString()).apply()
    }
}

// ---------------------------------------------------------------------------
// Message (plain Kotlin, unit-tested)
// ---------------------------------------------------------------------------

internal data class ReminderMessage(val title: String, val body: String)

internal fun reminderMessage(date: LocalDate, meals: List<Meal>, profile: UserProfile): ReminderMessage {
    val today = meals.filter { it.loggedAt.toLocalDate() == date }
    val score = dailyScore(date, meals, profile.scoreTargets(), today = date)
    if (today.isEmpty() || score == null) return emptyDayMessage(profile.language)
    val emoji = when {
        score.score >= 80 -> "🔥"
        score.score >= 50 -> "💪"
        else -> "🍽️"
    }
    return ReminderMessage(
        title = "Today so far: ${score.score}/100 $emoji",
        body = goalFeedback(today, today, profile).reminder
    )
}

private fun emptyDayMessage(language: RoastLanguage): ReminderMessage = when (language) {
    RoastLanguage.ENGLISH -> ReminderMessage(
        "No meals logged yet today 🍽️",
        "Snap your next meal so today's score and streak count."
    )
    RoastLanguage.CANTONESE -> ReminderMessage(
        "今日仲未記低任何一餐 🍽️",
        "影低下一餐，今日嘅分數同連續紀錄先會計到。"
    )
    RoastLanguage.TRADITIONAL -> ReminderMessage(
        "今天還未記錄任何一餐 🍽️",
        "拍下下一餐，今天的分數和連續紀錄才會計算。"
    )
    RoastLanguage.MIXED -> ReminderMessage(
        "今日仲未記低任何一餐 🍽️",
        "影低下一餐，今日嘅分數同連續紀錄先會計到。Let's go!"
    )
}

/** Time from [now] until the next [time] (later today, or tomorrow if it has passed). */
internal fun delayUntil(now: LocalDateTime, time: LocalTime): Duration {
    var next = now.toLocalDate().atTime(time)
    if (!next.isAfter(now)) next = next.plusDays(1)
    return Duration.between(now, next)
}

// ---------------------------------------------------------------------------
// Scheduling
// ---------------------------------------------------------------------------

internal object DailyReminder {
    private const val DAILY_WORK = "nutriquest-daily-reminder"
    private const val TEST_WORK = "nutriquest-reminder-test"

    fun schedule(context: Context, settings: ReminderSettings) {
        val delay = delayUntil(LocalDateTime.now(), settings.time)
        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_WORK)
    }

    /** Runs the reminder once right away (the "Send test now" button). */
    fun runNow(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(TEST_WORK, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<DailyReminderWorker>().build())
    }
}

internal class DailyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val meals = MealRepository(applicationContext).loadMeals()
        val profile = ProfileStore(applicationContext).load()
        ReminderNotifier.show(applicationContext, reminderMessage(LocalDate.now(), meals, profile))
        Result.success()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        Result.failure()   // try again at the next scheduled time rather than retrying all evening
    }
}

// ---------------------------------------------------------------------------
// Notification
// ---------------------------------------------------------------------------

internal object ReminderNotifier {
    const val CHANNEL_ID = "daily_reminder"
    const val NOTIFICATION_ID = 3330

    fun canNotify(context: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Posts the reminder; returns false if notifications are turned off for the app. */
    fun show(context: Context, message: ReminderMessage): Boolean {
        if (!canNotify(context)) return false
        createChannel(context)
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_nutriquest)
            .setContentTitle(message.title)
            .setContentText(message.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Daily reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "One evening nudge with today's diet score"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
