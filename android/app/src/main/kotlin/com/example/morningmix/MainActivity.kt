package com.example.morningmix

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.provider.MediaStore
import android.os.Build
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.Calendar

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.example.morningmix/alarm"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "scheduleAlarm" -> {
                    val id = call.argument<Int>("id") ?: return@setMethodCallHandler
                    val time = call.argument<String>("time") ?: return@setMethodCallHandler
                    val days = call.argument<String>("days") ?: ""
                    val isVibrate = call.argument<Boolean>("isVibrate") ?: true
                    val ringtoneType = call.argument<String>("ringtoneType") ?: "default"
                    val ringtoneUri = call.argument<String>("ringtoneUri") ?: ""

                    scheduleAlarm(id, time, days, isVibrate, ringtoneType, ringtoneUri)
                    result.success(null)
                }
                "cancelAlarm" -> {
                    val id = call.argument<Int>("id") ?: return@setMethodCallHandler
                    cancelAlarm(id)
                    result.success(null)
                }
                "getDefaultAlarmTones" -> {
                    result.success(getDefaultAlarmTones())
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun scheduleAlarm(id: Int, time: String, days: String, isVibrate: Boolean, ringtoneType: String, ringtoneUri: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("id", id)
            putExtra("time", time)
            putExtra("days", days)
            putExtra("isVibrate", isVibrate)
            putExtra("ringtoneType", ringtoneType)
            putExtra("ringtoneUri", ringtoneUri)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = calculateNextTriggerMillis(time, days)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun calculateNextTriggerMillis(time: String, days: String): Long {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val now = Calendar.getInstance()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val selectedDays = days.split(',').mapNotNull { it.toIntOrNull() }.toSet()
        if (selectedDays.isEmpty()) {
            if (calendar.timeInMillis <= now.timeInMillis) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            return calendar.timeInMillis
        }

        val todayIndex = now.get(Calendar.DAY_OF_WEEK) - 1
        for (offset in 0..7) {
            val dayIndex = (todayIndex + offset) % 7
            if (selectedDays.contains(dayIndex)) {
                val candidate = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, offset)
                }
                if (candidate.timeInMillis > now.timeInMillis) {
                    return candidate.timeInMillis
                }
            }
        }

        calendar.add(Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis
    }

    private fun cancelAlarm(id: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun getDefaultAlarmTones(): List<Map<String, String>> {
        val ringtoneManager = RingtoneManager(applicationContext)
        ringtoneManager.setType(RingtoneManager.TYPE_ALARM)
        val cursor = ringtoneManager.cursor

        val tones = mutableListOf<Map<String, String>>()
        val titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
        while (cursor.moveToNext()) {
            val position = cursor.position
            val uri = ringtoneManager.getRingtoneUri(position)?.toString() ?: continue
            val title = if (titleColumn >= 0) cursor.getString(titleColumn) else "Alarm tone"
            tones.add(mapOf("title" to title, "uri" to uri))
        }
        // Do not close this cursor manually when sourced from RingtoneManager;
        // it may be tied to lifecycle-managed requery paths on some devices.
        return tones
    }
}
