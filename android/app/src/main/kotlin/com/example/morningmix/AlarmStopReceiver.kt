package com.example.morningmix

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, AlarmService::class.java)
        context.stopService(serviceIntent)
        
        // TODO: Update SQLite playlistIndex +1 here or request Flutter app to do it and schedule next.
    }
}
