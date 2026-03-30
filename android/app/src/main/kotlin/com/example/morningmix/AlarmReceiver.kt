package com.example.morningmix

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        if (id == -1) return

        val time = intent.getStringExtra("time") ?: "00:00"
        val days = intent.getStringExtra("days") ?: ""
        val isVibrate = intent.getBooleanExtra("isVibrate", true)
        val ringtoneType = intent.getStringExtra("ringtoneType") ?: "default"
        val currentRingtoneUri = intent.getStringExtra("ringtoneUri")

        val nextRingtoneUri = if (ringtoneType == "playlist") {
            advancePlaylistAndGetNextUri(context, id)
        } else {
            currentRingtoneUri
        }

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("id", id)
            putExtra("isVibrate", isVibrate)
            putExtra("ringtoneType", ringtoneType)
            putExtra("ringtoneUri", currentRingtoneUri)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        if (days.isNotEmpty()) {
            scheduleNext(context, id, time, days, isVibrate, ringtoneType, nextRingtoneUri ?: "")
        }
    }

    private fun scheduleNext(
        context: Context,
        id: Int,
        time: String,
        days: String,
        isVibrate: Boolean,
        ringtoneType: String,
        ringtoneUri: String,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("id", id)
            putExtra("time", time)
            putExtra("days", days)
            putExtra("isVibrate", isVibrate)
            putExtra("ringtoneType", ringtoneType)
            putExtra("ringtoneUri", ringtoneUri)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val triggerAt = calculateNextTriggerMillis(time, days)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun calculateNextTriggerMillis(time: String, days: String): Long {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val now = Calendar.getInstance()
        val selectedDays = days.split(',').mapNotNull { it.toIntOrNull() }.toSet()

        val todayIndex = now.get(Calendar.DAY_OF_WEEK) - 1
        for (offset in 1..7) {
            val dayIndex = (todayIndex + offset) % 7
            if (selectedDays.contains(dayIndex)) {
                val candidate = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, offset)
                }
                return candidate.timeInMillis
            }
        }

        return now.timeInMillis + 24 * 60 * 60 * 1000L
    }

    private fun advancePlaylistAndGetNextUri(context: Context, alarmId: Int): String? {
        val dbFile = context.getDatabasePath("alarms.db")
        if (!dbFile.exists()) return null

        val db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            val alarmCursor = db.rawQuery(
                "SELECT playlistIndex FROM alarms WHERE id = ?",
                arrayOf(alarmId.toString()),
            )
            if (!alarmCursor.moveToFirst()) {
                alarmCursor.close()
                return null
            }
            val currentIndex = alarmCursor.getInt(0)
            alarmCursor.close()

            val tracksCursor = db.rawQuery(
                "SELECT uri FROM playlist_tracks WHERE alarmId = ? ORDER BY orderIndex ASC",
                arrayOf(alarmId.toString()),
            )

            val tracks = mutableListOf<String>()
            while (tracksCursor.moveToNext()) {
                tracks.add(tracksCursor.getString(0))
            }
            tracksCursor.close()

            if (tracks.isEmpty()) return null

            val nextIndex = (currentIndex + 1) % tracks.size
            db.execSQL(
                "UPDATE alarms SET playlistIndex = ? WHERE id = ?",
                arrayOf(nextIndex, alarmId),
            )

            return tracks[nextIndex]
        } finally {
            db.close()
        }
    }
}
