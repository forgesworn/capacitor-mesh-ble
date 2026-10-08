package dev.forgesworn.meshble;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/** Optional process anchor for products that need BLE activity while backgrounded. */
public final class MeshBleForegroundService extends Service {

    private static final String CHANNEL_ID = "mesh-ble";
    private static final int NOTIFICATION_ID = 0x4d42;
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_TEXT = "text";

    static void start(Context context, String title, String text) {
        Intent intent = new Intent(context, MeshBleForegroundService.class).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, MeshBleForegroundService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String title = intent == null ? null : intent.getStringExtra(EXTRA_TITLE);
        String text = intent == null ? null : intent.getStringExtra(EXTRA_TEXT);
        Notification notification = buildNotification(
            blank(title) ? "Nearby connection active" : title,
            blank(text) ? "Connected over Bluetooth. Tap to open." : text
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        return START_NOT_STICKY;
    }

    private Notification buildNotification(String title, String text) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Nearby Bluetooth connection",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shown while an app uses nearby Bluetooth in the background.");
            manager.createNotificationChannel(channel);
        }

        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent tap =
            open == null
                ? null
                : PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        if (tap != null) builder.setContentIntent(tap);
        return builder.build();
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
