package com.awaketime.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.AlarmClock;
import android.util.Base64;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;

@CapacitorPlugin(name = "AwakeTimeAlarm")
public class AwakeTimeAlarmPlugin extends Plugin {
    private static final int ALARM_REQUEST_CODE = 7000;
    private static final int NOTIF_PERM_CODE = 7002;

    @PluginMethod
    public void scheduleAlarm(PluginCall call) {
        Integer hour = call.getInt("hour");
        Integer minute = call.getInt("minute");
        String timeLabel = call.getString("timeLabel", "");
        Boolean useCustom = call.getBoolean("useCustomSound", false);
        String customSoundBase64 = call.getString("customSoundBase64", null);

        if (hour == null || minute == null) {
            call.reject("Missing hour or minute");
            return;
        }

        Context ctx = getContext();

        // Request Android 13+ notification permission if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED && getActivity() != null) {
                ActivityCompat.requestPermissions(
                    getActivity(),
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIF_PERM_CODE
                );
            }
        }

        // Save custom audio file into internal storage if provided
        boolean savedCustom = false;
        File customFile = new File(ctx.getFilesDir(), "custom_alarm_audio");
        if (Boolean.TRUE.equals(useCustom) && customSoundBase64 != null && !customSoundBase64.isEmpty()) {
            try {
                String cleanBase64 = customSoundBase64;
                int commaIdx = cleanBase64.indexOf(',');
                if (commaIdx >= 0) {
                    cleanBase64 = cleanBase64.substring(commaIdx + 1);
                }
                byte[] audioBytes = Base64.decode(cleanBase64, Base64.DEFAULT);
                try (FileOutputStream fos = new FileOutputStream(customFile)) {
                    fos.write(audioBytes);
                }
                savedCustom = true;
            } catch (Exception ignored) {
                savedCustom = false;
            }
        }

        // Compute next occurrence of hour:minute
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        long triggerAtMillis = cal.getTimeInMillis();

        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            call.reject("AlarmManager unavailable");
            return;
        }

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        Intent receiverIntent = new Intent(ctx, AlarmReceiver.class);
        receiverIntent.putExtra(AlarmService.EXTRA_TIME_LABEL, timeLabel);
        receiverIntent.putExtra(AlarmService.EXTRA_USE_CUSTOM, savedCustom);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            ctx,
            ALARM_REQUEST_CODE,
            receiverIntent,
            piFlags
        );

        Intent showIntent = new Intent(ctx, MainActivity.class);
        PendingIntent showPending = PendingIntent.getActivity(
            ctx,
            ALARM_REQUEST_CODE + 1,
            showIntent,
            piFlags
        );

        try {
            AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(triggerAtMillis, showPending);
            alarmManager.setAlarmClock(info, pendingIntent);
        } catch (SecurityException se) {
            // Fallback for Android 12 devices where exact alarm permission is toggled off
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        }

        SharedPreferences prefs = ctx.getSharedPreferences("awaketime_prefs", Context.MODE_PRIVATE);
        prefs.edit()
            .putLong("trigger_at_millis", triggerAtMillis)
            .putString("time_label", timeLabel)
            .apply();

        JSObject ret = new JSObject();
        ret.put("scheduled", true);
        ret.put("triggerAtMillis", triggerAtMillis);
        ret.put("usedCustomSound", savedCustom);
        call.resolve(ret);
    }

    @PluginMethod
    public void cancelAlarm(PluginCall call) {
        Context ctx = getContext();
        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            Intent receiverIntent = new Intent(ctx, AlarmReceiver.class);
            int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                piFlags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                ctx,
                ALARM_REQUEST_CODE,
                receiverIntent,
                piFlags
            );
            alarmManager.cancel(pendingIntent);
        }

        stopServiceIfRunning(ctx);

        SharedPreferences prefs = ctx.getSharedPreferences("awaketime_prefs", Context.MODE_PRIVATE);
        prefs.edit().remove("trigger_at_millis").remove("time_label").apply();

        JSObject ret = new JSObject();
        ret.put("cancelled", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void stopRinging(PluginCall call) {
        stopServiceIfRunning(getContext());
        JSObject ret = new JSObject();
        ret.put("stopped", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        Context ctx = getContext();
        SharedPreferences prefs = ctx.getSharedPreferences("awaketime_prefs", Context.MODE_PRIVATE);
        long triggerAt = prefs.getLong("trigger_at_millis", 0L);
        String label = prefs.getString("time_label", "");
        boolean active = triggerAt > System.currentTimeMillis();
        if (!active && triggerAt > 0L) {
            prefs.edit().remove("trigger_at_millis").remove("time_label").apply();
        }

        JSObject ret = new JSObject();
        ret.put("isRinging", AlarmService.isRinging);
        ret.put("ringingTimeLabel", AlarmService.ringingTimeLabel);
        ret.put("hasScheduledAlarm", active);
        ret.put("scheduledTimeLabel", active ? label : "");
        ret.put("triggerAtMillis", active ? triggerAt : 0L);
        call.resolve(ret);
    }

    @PluginMethod
    public void openSystemClock(PluginCall call) {
        Integer hour = call.getInt("hour");
        Integer minute = call.getInt("minute");
        String label = call.getString("label", "AwakeTime Wake-Up");
        if (hour == null || minute == null) {
            call.reject("Missing hour or minute");
            return;
        }
        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
        intent.putExtra(AlarmClock.EXTRA_HOUR, hour);
        intent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
        intent.putExtra(AlarmClock.EXTRA_MESSAGE, label);
        intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(intent);
            JSObject ret = new JSObject();
            ret.put("opened", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Could not open Clock app: " + e.getMessage());
        }
    }

    private void stopServiceIfRunning(Context ctx) {
        if (AlarmService.isRinging) {
            Intent stopIntent = new Intent(ctx, AlarmService.class);
            stopIntent.setAction(AlarmService.ACTION_STOP);
            ctx.startService(stopIntent);
        }
    }
}
