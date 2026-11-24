package com.awaketime.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.core.content.ContextCompat;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String timeLabel = intent.getStringExtra(AlarmService.EXTRA_TIME_LABEL);
        boolean useCustom = intent.getBooleanExtra(AlarmService.EXTRA_USE_CUSTOM, false);

        SharedPreferences prefs = context.getSharedPreferences("awaketime_prefs", Context.MODE_PRIVATE);
        prefs.edit().remove("trigger_at_millis").remove("time_label").apply();

        Intent serviceIntent = new Intent(context, AlarmService.class);
        serviceIntent.setAction(AlarmService.ACTION_START);
        serviceIntent.putExtra(AlarmService.EXTRA_TIME_LABEL, timeLabel != null ? timeLabel : "");
        serviceIntent.putExtra(AlarmService.EXTRA_USE_CUSTOM, useCustom);
        ContextCompat.startForegroundService(context, serviceIntent);
    }
}
