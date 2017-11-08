package com.android.server.connectivity;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.Log;
import com.android.server.rms.iaware.hiber.constant.AppHibernateCst;

public class HwNotificationTetheringImpl implements HwNotificationTethering {
    private static final String ACTION_WIFI_AP_STA_JOIN = "android.net.wifi.WIFI_AP_STA_JOIN";
    private static final String ACTION_WIFI_AP_STA_LEAVE = "android.net.wifi.WIFI_AP_STA_LEAVE";
    private static final boolean DBG = HWFLOW;
    private static final String EXTRA_STA_COUNT = "staCount";
    protected static final boolean HWFLOW;
    private static final String TAG = "HwCustTethering";
    private int mBluetoothUsbNum;
    private Context mContext;
    private boolean mIsATT;
    boolean mIsCMCC;
    private CharSequence mMessage;
    private NotificationManager mNotificationManager;
    private PendingIntent mPi;
    private BroadcastReceiver mStateReceiver;
    private Notification mTetheredNotification;
    private int[] mTetheredRecord = new int[]{0, 0, 0, 0};
    private CharSequence mTitle;
    private int mTotalNum;
    private int mWifiConnectNum;
    private boolean mWifiTetherd;

    static {
        boolean isLoggable = !Log.HWINFO ? Log.HWModuleLog ? Log.isLoggable(TAG, 4) : false : true;
        HWFLOW = isLoggable;
    }

    public HwNotificationTetheringImpl(Context context) {
        this.mContext = context;
        this.mIsCMCC = "CMCC".equalsIgnoreCase(SystemProperties.get("ro.config.operators", AppHibernateCst.INVALID_PKG));
        this.mIsATT = "ATT".equalsIgnoreCase(SystemProperties.get("ro.config.operators", AppHibernateCst.INVALID_PKG));
        if (this.mIsCMCC || this.mIsATT) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_WIFI_AP_STA_JOIN);
            filter.addAction(ACTION_WIFI_AP_STA_LEAVE);
            this.mStateReceiver = new BroadcastReceiver() {
                public void onReceive(Context content, Intent intent) {
                    String action = intent.getAction();
                    if (HwNotificationTetheringImpl.DBG) {
                        Log.d(HwNotificationTetheringImpl.TAG, "onReceive:" + action);
                    }
                    if (HwNotificationTetheringImpl.ACTION_WIFI_AP_STA_JOIN.equals(action) || HwNotificationTetheringImpl.ACTION_WIFI_AP_STA_LEAVE.equals(action)) {
                        int wifiConnectNum = intent.getIntExtra(HwNotificationTetheringImpl.EXTRA_STA_COUNT, 0);
                        if (HwNotificationTetheringImpl.this.mTetheredNotification != null && HwNotificationTetheringImpl.this.mWifiTetherd && HwNotificationTetheringImpl.this.mWifiConnectNum != wifiConnectNum) {
                            HwNotificationTetheringImpl.this.mWifiConnectNum = wifiConnectNum;
                            HwNotificationTetheringImpl.this.sendNotification(true);
                        }
                    }
                }
            };
            this.mContext.registerReceiver(this.mStateReceiver, filter);
            this.mNotificationManager = (NotificationManager) this.mContext.getSystemService("notification");
        }
    }

    private void sendNotification(boolean shouldForceUpdate) {
        int totalNum = this.mBluetoothUsbNum + this.mWifiConnectNum;
        if (DBG) {
            Log.d(TAG, "sendNumberChangeNotification:" + totalNum);
        }
        if (shouldForceUpdate || this.mTotalNum != totalNum) {
            this.mTotalNum = totalNum;
            Notification tempTetheredNotification = this.mTetheredNotification;
            if (tempTetheredNotification != null) {
                CharSequence title;
                CharSequence message;
                this.mNotificationManager.cancelAsUser(null, tempTetheredNotification.icon, UserHandle.ALL);
                if (this.mWifiConnectNum <= 0 || this.mBluetoothUsbNum != 0) {
                    title = this.mTitle;
                    message = this.mMessage;
                } else {
                    Resources r = Resources.getSystem();
                    title = this.mContext.getString(33685833);
                    message = r.getQuantityString(34734086, this.mWifiConnectNum, new Object[]{Integer.valueOf(this.mWifiConnectNum)});
                }
                tempTetheredNotification.tickerText = title;
                tempTetheredNotification.setLatestEventInfo(this.mContext, title, message, this.mPi);
                this.mNotificationManager.notifyAsUser(null, tempTetheredNotification.icon, tempTetheredNotification, UserHandle.ALL);
            }
        }
    }

    public void setTetheringNumber(boolean wifiTethered, boolean usbTethered, boolean bluetoothTethered) {
        if (this.mIsCMCC || this.mIsATT) {
            if (DBG) {
                Log.d(TAG, "wifiTethered:" + wifiTethered + " usbTethered:" + usbTethered + " bluetoothTethered:" + bluetoothTethered);
            }
            this.mBluetoothUsbNum = 0;
            if (usbTethered) {
                this.mBluetoothUsbNum = 1;
            }
            if (bluetoothTethered) {
                this.mBluetoothUsbNum++;
            }
            this.mWifiTetherd = wifiTethered;
            if (!this.mWifiTetherd) {
                this.mWifiConnectNum = 0;
            }
        }
    }

    public boolean sendTetherNotification(Notification tetheredNotification, CharSequence title, CharSequence message, PendingIntent pi) {
        if (!this.mIsCMCC && !this.mIsATT) {
            return false;
        }
        if (DBG) {
            Log.d(TAG, "sendTetherNotification " + tetheredNotification);
        }
        this.mTetheredNotification = tetheredNotification;
        this.mTitle = title;
        this.mMessage = message;
        this.mPi = pi;
        if (this.mWifiConnectNum <= 0 && this.mBluetoothUsbNum <= 0) {
            return false;
        }
        sendNotification(true);
        return true;
    }

    public void sendTetherNotification() {
        sendNotification(false);
    }

    public void clearTetheredNotification() {
        if (DBG) {
            Log.d(TAG, "clearTetheredNotification");
        }
        this.mTetheredNotification = null;
    }

    private void resetTetheredRecord() {
        for (int type = 0; type < 4; type++) {
            this.mTetheredRecord[type] = 0;
        }
    }

    public int getTetheredIcon(boolean usbTethered, boolean wifiTethered, boolean bluetoothTethered, boolean p2pTethered) {
        int icon = 0;
        int tetheredTypes = 0;
        resetTetheredRecord();
        if (wifiTethered) {
            icon = 17303284;
            tetheredTypes = 1;
            this.mTetheredRecord[0] = 1;
        }
        if (usbTethered) {
            icon = 33751322;
            tetheredTypes++;
            this.mTetheredRecord[1] = 1;
        }
        if (bluetoothTethered) {
            icon = 33751320;
            tetheredTypes++;
            this.mTetheredRecord[2] = 1;
        }
        if (p2pTethered) {
            icon = 33751321;
            tetheredTypes++;
            this.mTetheredRecord[3] = 1;
        }
        if (tetheredTypes > 1) {
            return 33751321;
        }
        return icon;
    }

    public void stopTethering() {
        ConnectivityManager cm = (ConnectivityManager) this.mContext.getSystemService("connectivity");
        for (int type = 0; type < 4; type++) {
            if (this.mTetheredRecord[type] == 1) {
                cm.stopTethering(type);
            }
        }
    }
}
