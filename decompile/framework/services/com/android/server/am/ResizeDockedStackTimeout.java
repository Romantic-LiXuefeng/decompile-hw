package com.android.server.am;

import android.graphics.Rect;
import android.os.Handler;

class ResizeDockedStackTimeout {
    private static final long TIMEOUT_MS = 10000;
    private final Rect mCurrentDockedBounds = new Rect();
    private final Handler mHandler;
    private final ActivityManagerService mService;
    private final ActivityStackSupervisor mSupervisor;
    private final Runnable mTimeoutRunnable = new Runnable() {
        public void run() {
            synchronized (ResizeDockedStackTimeout.this.mService) {
                try {
                    ActivityManagerService.boostPriorityForLockedSection();
                    ResizeDockedStackTimeout.this.mSupervisor.resizeDockedStackLocked(ResizeDockedStackTimeout.this.mCurrentDockedBounds, null, null, null, null, true);
                } finally {
                    ActivityManagerService.resetPriorityAfterLockedSection();
                }
            }
        }
    };

    ResizeDockedStackTimeout(ActivityManagerService service, ActivityStackSupervisor supervisor, Handler handler) {
        this.mService = service;
        this.mSupervisor = supervisor;
        this.mHandler = handler;
    }

    void notifyResizing(Rect dockedBounds, boolean hasTempBounds) {
        this.mHandler.removeCallbacks(this.mTimeoutRunnable);
        if (hasTempBounds) {
            this.mCurrentDockedBounds.set(dockedBounds);
            this.mHandler.postDelayed(this.mTimeoutRunnable, 10000);
        }
    }
}
