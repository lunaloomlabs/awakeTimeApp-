package com.awaketime.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.io.File;

public class AlarmService extends Service {
    public static final String ACTION_START = "com.awaketime.app.ACTION_START_ALARM";
    public static final String ACTION_STOP = "com.awaketime.app.ACTION_STOP_ALARM";
    public static final String EXTRA_TIME_LABEL = "time_label";
    public static final String EXTRA_USE_CUSTOM = "use_custom_sound";
    private static final String CHANNEL_ID = "awaketime_alarm_channel_v1";
    private static final int NOTIFICATION_ID = 7001;

    public static volatile boolean isRinging = false;
    public static volatile String ringingTimeLabel = "";

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;
        if (ACTION_STOP.equals(action)) {
            stopAlarmAndSelf();
            return START_NOT_STICKY;
        }

        String timeLabel = intent != null ? intent.getStringExtra(EXTRA_TIME_LABEL) : "";
        if (timeLabel == null) timeLabel = "";
        boolean useCustom = intent != null && intent.getBooleanExtra(EXTRA_USE_CUSTOM, false);

        isRinging = true;
        ringingTimeLabel = timeLabel;

        createNotificationChannel();
        Notification notification = buildNotification(timeLabel);
        startForeground(NOTIFICATION_ID, notification);

        acquireWakeLock();
        startAudio(useCustom);
        startVibration();

        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "AwakeTime Alarms",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Sleep cycle wake-up alarms");
            channel.setSound(null, null); // MediaPlayer plays on USAGE_ALARM stream
            channel.enableVibration(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String timeLabel) {
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        openIntent.putExtra("from_alarm", true);
        PendingIntent openPending = PendingIntent.getActivity(
            this,
            100,
            openIntent,
            piFlags
        );

        Intent stopIntent = new Intent(this, AlarmService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
            this,
            101,
            stopIntent,
            piFlags
        );

        String body = timeLabel.isEmpty()
            ? "Time to wake up! You're in light sleep right now."
            : "It's " + timeLabel + "! Time to wake up — you're in light sleep now.";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("AwakeTime Alarm")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPending)
            .setFullScreenIntent(openPending, true)
            .addAction(0, "STOP ALARM", stopPending)
            .build();
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AwakeTime:AlarmWakeLock");
                wakeLock.acquire(10 * 60 * 1000L);
            }
        } catch (Exception ignored) {}
    }

    private void startAudio(boolean useCustom) {
        stopAudio();
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

            File customFile = new File(getFilesDir(), "custom_alarm_audio");
            if (useCustom && customFile.exists() && customFile.length() > 0) {
                try {
                    mediaPlayer = new MediaPlayer();
                    mediaPlayer.setAudioAttributes(attrs);
                    mediaPlayer.setDataSource(customFile.getAbsolutePath());
                    mediaPlayer.setLooping(true);
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                    return;
                } catch (Exception ignored) {
                    stopAudio();
                }
            }

            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }
            if (alarmUri != null) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setAudioAttributes(attrs);
                mediaPlayer.setDataSource(this, alarmUri);
                mediaPlayer.setLooping(true);
                mediaPlayer.prepare();
                mediaPlayer.start();
            }
        } catch (Exception ignored) {}
    }

    private void startVibration() {
        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            long[] pattern = new long[]{0, 600, 400, 600, 800};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        } catch (Exception ignored) {}
    }

    private void stopAudio() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            } catch (Exception ignored) {}
            try {
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    private void stopAlarmAndSelf() {
        isRinging = false;
        ringingTimeLabel = "";
        stopAudio();
        if (vibrator != null) {
            try { vibrator.cancel(); } catch (Exception ignored) {}
            vibrator = null;
        }
        if (wakeLock != null) {
            try {
                if (wakeLock.isHeld()) wakeLock.release();
            } catch (Exception ignored) {}
            wakeLock = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        isRinging = false;
        ringingTimeLabel = "";
        stopAudio();
        if (vibrator != null) {
            try { vibrator.cancel(); } catch (Exception ignored) {}
        }
        if (wakeLock != null) {
            try {
                if (wakeLock.isHeld()) wakeLock.release();
            } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
