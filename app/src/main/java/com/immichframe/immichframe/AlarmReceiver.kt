package com.immichframe.immichframe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("alarm", "Received intent")
        val actionIntent = Intent("TURN_OFF_ALARM");

        context.sendBroadcast(actionIntent)
    }
}