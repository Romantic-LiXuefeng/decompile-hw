package com.android.server.policy;

import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.IPowerManager;
import android.os.IPowerManager.Stub;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings.Global;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Window;
import android.view.WindowManagerPolicy;
import android.view.WindowManagerPolicy.WindowManagerFuncs;
import com.android.internal.policy.HwPhoneLayoutInflater;
import com.android.internal.policy.HwPhoneWindow;
import com.android.internal.policy.PhoneLayoutInflater;
import com.android.server.policy.GlobalActions.Action;
import com.android.server.policy.GlobalActions.ToggleAction;
import com.android.server.policy.GlobalActions.ToggleAction.State;
import com.android.server.policy.HwPolicyFactory.Factory;
import huawei.com.android.server.policy.BootMessageActions;
import huawei.com.android.server.policy.HwGlobalActions;
import java.util.ArrayList;

public class HwPolicyFactoryImpl implements Factory {
    private static final String ACTION_HWSYSTEMMANAGER_SHUTDOWN_LIMIT_POWERMODE = "huawei.intent.action.HWSYSTEMMANAGER_SHUTDOWN_LIMIT_POWERMODE";
    private static final String ACTION_HWSYSTEMMANAGER_START_SUPER_POWERMODE = "huawei.intent.action.HWSYSTEMMANAGER_START_SUPER_POWERMODE";
    private static final String ACTION_USE_POWER_GENIE_CHANGE_MODE = "huawei.intent.action.PG_EXTREME_MODE_ENABLE_ACTION";
    private static final String TAG = "HwPolicyFactoryImpl";
    private static Dialog mExitDialog;
    BootMessageActions mBootMessageActions;
    HwGlobalActions mHwGlobalActions;
    private ToggleAction mUltraPowerSaveOn;
    private State mUltraPowerState = State.Off;

    public void addUltraPowerSaveImpl(ArrayList mItems, Context context) {
        if (Global.getInt(context.getContentResolver(), "device_provisioned", -1) != 0) {
            final Context context2 = context;
            this.mUltraPowerSaveOn = new ToggleAction(33751334, 33751333, 33685702, 33685703, 33685704) {
                public void onToggle(boolean on) {
                    HwPolicyFactoryImpl.this.changeUltraPowerSaveSetting(on, context2);
                }

                public boolean showDuringKeyguard() {
                    return true;
                }

                public boolean showBeforeProvisioning() {
                    return true;
                }
            };
            mItems.add(this.mUltraPowerSaveOn);
            this.mUltraPowerState = SystemProperties.getBoolean("sys.super_power_save", false) ? State.On : State.Off;
            this.mUltraPowerSaveOn.updateState(this.mUltraPowerState);
        }
    }

    private void changeUltraPowerSaveSetting(boolean on, Context context) {
        if (on) {
            context.sendBroadcast(new Intent(ACTION_HWSYSTEMMANAGER_START_SUPER_POWERMODE));
        } else {
            showExitDialog(context);
        }
    }

    private void sendExitLimitPowerModeBroadcast(Context context) {
        SystemProperties.set("sys.super_power_save", "false");
        Intent intent = new Intent(ACTION_HWSYSTEMMANAGER_SHUTDOWN_LIMIT_POWERMODE);
        intent.putExtra("shutdomn_limit_powermode", 0);
        context.sendBroadcast(intent);
        Intent callPowerGenieIntent = new Intent("huawei.intent.action.PG_EXTREME_MODE_ENABLE_ACTION");
        callPowerGenieIntent.putExtra("enable", false);
        context.sendBroadcast(callPowerGenieIntent);
    }

    private void showExitDialog(Context context) {
        ContextThemeWrapper themeContext = new ContextThemeWrapper(context, context.getResources().getIdentifier("androidhwext:style/Theme.Emui.Dialog.Alert", null, null));
        if (mExitDialog != null) {
            mExitDialog.dismiss();
        }
        mExitDialog = createExitDialog(themeContext);
        mExitDialog.getWindow().setType(2009);
        mExitDialog.show();
    }

    private Dialog createExitDialog(final Context context) {
        return new Builder(context).setTitle(33685700).setMessage(33685701).setPositiveButton(33685699, new OnClickListener() {
            public void onClick(DialogInterface arg0, int arg1) {
                HwPolicyFactoryImpl.this.sendExitLimitPowerModeBroadcast(context);
                if (HwPolicyFactoryImpl.mExitDialog != null) {
                    HwPolicyFactoryImpl.mExitDialog.dismiss();
                }
            }
        }).setNegativeButton(17039360, null).create();
    }

    public void addRebootMenu(ArrayList<Action> items) {
        items.add(new ToggleAction(33751040, 33751040, 33685515, 33685516, 33685516) {
            public void onToggle(boolean on) {
                try {
                    IPowerManager pm = Stub.asInterface(ServiceManager.getService("power"));
                    if (pm != null) {
                        pm.reboot(true, "huawei_reboot", false);
                    }
                } catch (RemoteException e) {
                    Log.e(HwPolicyFactoryImpl.TAG, "PowerManager service died!", e);
                }
            }

            public boolean showDuringKeyguard() {
                return true;
            }

            public boolean showBeforeProvisioning() {
                return true;
            }
        });
    }

    public WindowManagerPolicy getHwPhoneWindowManager() {
        return new HwPhoneWindowManager();
    }

    public Window getHwPhoneWindow(Context context) {
        return new HwPhoneWindow(context);
    }

    public PhoneLayoutInflater getHwPhoneLayoutInflater(Context context) {
        return new HwPhoneLayoutInflater(context);
    }

    public void showHwGlobalActionsFragment(Context tContext, WindowManagerFuncs tWindowManagerFuncs, PowerManager powerManager, boolean keyguardShowing, boolean keyguardSecure, boolean isDeviceProvisioned) {
        if (this.mHwGlobalActions == null) {
            this.mHwGlobalActions = new HwGlobalActions(tContext, tWindowManagerFuncs);
        }
        this.mHwGlobalActions.showDialog(keyguardShowing, keyguardSecure, isDeviceProvisioned);
        if (keyguardShowing) {
            powerManager.userActivity(SystemClock.uptimeMillis(), false);
        }
    }

    public void showBootMessage(Context tContext, int curr, int total) {
        if (this.mBootMessageActions == null) {
            this.mBootMessageActions = new BootMessageActions(tContext);
        }
        this.mBootMessageActions.showBootMessage(curr, total);
    }

    public void hideBootMessage() {
        if (this.mBootMessageActions != null) {
            this.mBootMessageActions.hideBootMessage();
        }
    }

    public boolean isHwGlobalActionsShowing() {
        return this.mHwGlobalActions != null ? this.mHwGlobalActions.isHwGlobalActionsShowing() : false;
    }

    public boolean ifUseHwGlobalActions() {
        return true;
    }
}
