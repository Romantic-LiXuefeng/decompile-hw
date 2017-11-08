package com.android.server.GcmFixer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build.VERSION;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.text.format.DateFormat;
import android.util.Slog;
import com.android.server.rms.iaware.hiber.constant.AppHibernateCst;

public class GcmHeartBeatFixer {
    private static final boolean FEATURE_GCM_FIXER = SystemProperties.getBoolean("ro.config.pg_gcm_fixer", true);
    private static final int INTERNAL_POWERGENIE = 300000;
    private static final int INTERNAL_WINDOW = 60000;
    private static final int INTERVAL_MOBILE_HB = SystemProperties.getInt("persist.sys.gcm.mobile", INTERNAL_POWERGENIE);
    private static final int INTERVAL_WIFI_HB = SystemProperties.getInt("persist.sys.gcm.wifi", INTERNAL_POWERGENIE);
    private static final String TAG = "GcmHeartBeatFixer";
    private static NetworkState sNetworkState = NetworkState.UNKNOWN;

    private enum NetworkState {
        UNKNOWN,
        CONNECTED,
        DISCONNECTED
    }

    public static void scheduleHeartbeatRequest(Context context, boolean fromNetworkStateChange, boolean adjust) {
        if (!FEATURE_GCM_FIXER) {
            Slog.i(TAG, "Gcm fixer is close...");
        } else if (isChinaMarket()) {
            Slog.i(TAG, "China market version, just return...");
        } else {
            Slog.i(TAG, "scheduleHeartbeatRequest, fromNetworkStateChange: " + fromNetworkStateChange);
            NetworkInfo activeNetworkInfo = ((ConnectivityManager) context.getSystemService("connectivity")).getActiveNetworkInfo();
            if (activeNetworkInfo == null || !activeNetworkInfo.isConnected()) {
                if (!(fromNetworkStateChange && sNetworkState == NetworkState.DISCONNECTED)) {
                    sNetworkState = NetworkState.DISCONNECTED;
                    cancelHeartbeatRequest(context);
                }
            } else if (!(fromNetworkStateChange && sNetworkState == NetworkState.CONNECTED)) {
                int intervalMillis;
                sNetworkState = NetworkState.CONNECTED;
                if (activeNetworkInfo.getType() == 1) {
                    intervalMillis = INTERVAL_WIFI_HB;
                } else {
                    intervalMillis = INTERVAL_MOBILE_HB;
                }
                setNextHeartbeatRequest(context, intervalMillis, adjust);
            }
        }
    }

    private static void setNextHeartbeatRequest(Context context, int intervalMillis, boolean adjust) {
        long triggerAtMillis;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService("alarm");
        long currentTime = System.currentTimeMillis();
        if (adjust && intervalMillis == INTERNAL_POWERGENIE) {
            long elapsedTime = SystemClock.elapsedRealtime();
            long t_elapsedTime = ((elapsedTime / ((long) intervalMillis)) + 1) * ((long) intervalMillis);
            long diff = t_elapsedTime - elapsedTime;
            triggerAtMillis = currentTime + diff;
            Slog.i(TAG, "currentTime: " + currentTime + " elapsedTime:" + elapsedTime + " t_elapsedTime:" + t_elapsedTime + " triggerAtMillis:" + triggerAtMillis + " diff:" + diff);
        } else {
            triggerAtMillis = currentTime + ((long) intervalMillis);
        }
        Slog.i(TAG, "setNextHeartbeatRequest at: " + DateFormat.format("yyyy-MM-dd hh:mm:ss", triggerAtMillis));
        PendingIntent broadcastPendingIntent = getBroadcastPendingIntent(context);
        if (VERSION.SDK_INT >= 19) {
            alarmManager.setWindow(0, triggerAtMillis, AppHibernateCst.DELAY_ONE_MINS, broadcastPendingIntent);
        } else {
            alarmManager.set(0, triggerAtMillis, broadcastPendingIntent);
        }
    }

    private static void cancelHeartbeatRequest(Context context) {
        Slog.d(TAG, "cancelHeartbeatRequest...");
        ((AlarmManager) context.getSystemService("alarm")).cancel(getBroadcastPendingIntent(context));
    }

    private static PendingIntent getBroadcastPendingIntent(Context context) {
        return PendingIntent.getBroadcast(context, 0, new Intent(HeartbeatReceiver.HEARTBEAT_FIXER_ACTION), 0);
    }

    public static boolean isChinaMarket() {
        if (SystemProperties.getInt("ro.config.hw_optb", 0) == 156) {
            return true;
        }
        return false;
    }
}
