package com.android.server.am;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Message;
import android.os.UserManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver.OnWindowShownListener;
import android.view.WindowManager.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;
import com.android.internal.annotations.GuardedBy;
import com.android.server.input.InputManagerService;

final class UserSwitchingDialog extends AlertDialog implements OnWindowShownListener {
    private static final int MSG_START_USER = 1;
    private static final String TAG = "ActivityManagerUserSwitchingDialog";
    private static final int WINDOW_SHOWN_TIMEOUT_MS = 3000;
    private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    UserSwitchingDialog.this.startUser();
                    return;
                default:
                    return;
            }
        }
    };
    private final ActivityManagerService mService;
    @GuardedBy("this")
    private boolean mStartedUser;
    private final int mUserId;

    public UserSwitchingDialog(ActivityManagerService service, Context context, UserInfo oldUser, UserInfo newUser, boolean aboveSystem) {
        String viewMessage;
        super(context, context.getResources().getIdentifier("androidhwext:style/Theme.Emui.Dialog", null, null));
        this.mService = service;
        this.mUserId = newUser.id;
        setCancelable(false);
        Resources res = getContext().getResources();
        View view = LayoutInflater.from(getContext()).inflate(17367300, null);
        if (UserManager.isSplitSystemUser() && newUser.id == 0) {
            viewMessage = res.getString(17040667, new Object[]{oldUser.name});
        } else {
            viewMessage = res.getString(17040666, new Object[]{newUser.name});
        }
        ((TextView) view.findViewById(16908299)).setText(viewMessage);
        setView(view);
        if (aboveSystem) {
            getWindow().setType(2010);
        }
        LayoutParams attrs = getWindow().getAttributes();
        attrs.privateFlags = InputManagerService.BTN_MOUSE;
        getWindow().setAttributes(attrs);
    }

    public void show() {
        super.show();
        View decorView = getWindow().getDecorView();
        if (decorView != null) {
            decorView.getViewTreeObserver().addOnWindowShownListener(this);
        }
        this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(1), 3000);
    }

    public void onWindowShown() {
        startUser();
    }

    void startUser() {
        synchronized (this) {
            if (!this.mStartedUser) {
                String text;
                boolean isSuccess = this.mService.mUserController.startUserInForeground(this.mUserId, this);
                this.mStartedUser = true;
                View decorView = getWindow().getDecorView();
                if (decorView != null) {
                    decorView.getViewTreeObserver().removeOnWindowShownListener(this);
                }
                this.mHandler.removeMessages(1);
                if (isSuccess) {
                    text = getContext().getResources().getString(33685802);
                } else {
                    text = getContext().getResources().getString(33685803);
                }
                Toast.makeText(getContext(), text, 1).show();
            }
        }
    }
}
