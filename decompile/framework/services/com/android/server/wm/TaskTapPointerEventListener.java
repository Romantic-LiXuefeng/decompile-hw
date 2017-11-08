package com.android.server.wm;

import android.graphics.Rect;
import android.graphics.Region;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.WindowManagerPolicy.PointerEventListener;

public class TaskTapPointerEventListener implements PointerEventListener {
    private final DisplayContent mDisplayContent;
    private GestureDetector mGestureDetector;
    private boolean mInGestureDetection;
    private final Region mNonResizeableRegion = new Region();
    private int mPointerIconType = 1;
    private final WindowManagerService mService;
    private final Rect mTmpRect = new Rect();
    private final Region mTouchExcludeRegion = new Region();
    private boolean mTwoFingerScrolling;

    private final class TwoFingerScrollListener extends SimpleOnGestureListener {
        private TwoFingerScrollListener() {
        }

        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (e2.getPointerCount() == 2) {
                TaskTapPointerEventListener.this.onTwoFingerScroll(e2);
                return true;
            }
            TaskTapPointerEventListener.this.stopTwoFingerScroll();
            return false;
        }
    }

    public TaskTapPointerEventListener(WindowManagerService service, DisplayContent displayContent) {
        this.mService = service;
        this.mDisplayContent = displayContent;
    }

    void init() {
        this.mGestureDetector = new GestureDetector(this.mService.mContext, new TwoFingerScrollListener(), this.mService.mH);
    }

    public void onPointerEvent(MotionEvent motionEvent) {
        doGestureDetection(motionEvent);
        int x;
        int y;
        InputDevice inputDevice;
        switch (motionEvent.getAction() & 255) {
            case 0:
                x = (int) motionEvent.getX();
                y = (int) motionEvent.getY();
                synchronized (this) {
                    if (!this.mTouchExcludeRegion.contains(x, y)) {
                        this.mService.mH.obtainMessage(31, x, y, this.mDisplayContent).sendToTarget();
                    }
                }
                return;
            case 1:
            case 6:
                stopTwoFingerScroll();
                return;
            case 2:
                if (motionEvent.getPointerCount() != 2) {
                    stopTwoFingerScroll();
                    return;
                }
                return;
            case 7:
                x = (int) motionEvent.getX();
                y = (int) motionEvent.getY();
                Task task = this.mDisplayContent.findTaskForControlPoint(x, y);
                inputDevice = motionEvent.getDevice();
                if (task == null || inputDevice == null) {
                    this.mPointerIconType = 1;
                    return;
                }
                task.getDimBounds(this.mTmpRect);
                if (this.mTmpRect.isEmpty() || this.mTmpRect.contains(x, y)) {
                    this.mPointerIconType = 1;
                    return;
                }
                int iconType = 1000;
                if (x < this.mTmpRect.left) {
                    if (y < this.mTmpRect.top) {
                        iconType = 1017;
                    } else if (y > this.mTmpRect.bottom) {
                        iconType = 1016;
                    } else {
                        iconType = 1014;
                    }
                } else if (x > this.mTmpRect.right) {
                    if (y < this.mTmpRect.top) {
                        iconType = 1016;
                    } else if (y > this.mTmpRect.bottom) {
                        iconType = 1017;
                    } else {
                        iconType = 1014;
                    }
                } else if (y < this.mTmpRect.top || y > this.mTmpRect.bottom) {
                    iconType = 1015;
                }
                if (this.mPointerIconType != iconType) {
                    this.mPointerIconType = iconType;
                    inputDevice.setPointerType(iconType);
                    return;
                }
                return;
            case 10:
                this.mPointerIconType = 1;
                inputDevice = motionEvent.getDevice();
                if (inputDevice != null) {
                    inputDevice.setPointerType(1000);
                    return;
                }
                return;
            default:
                return;
        }
    }

    private void doGestureDetection(MotionEvent motionEvent) {
        boolean z = true;
        if (this.mGestureDetector != null && !this.mNonResizeableRegion.isEmpty()) {
            int action = motionEvent.getAction() & 255;
            boolean isTouchInside = this.mNonResizeableRegion.contains((int) motionEvent.getX(), (int) motionEvent.getY());
            if (this.mInGestureDetection || (action == 0 && isTouchInside)) {
                if (!isTouchInside || action == 1 || action == 6) {
                    z = false;
                } else if (action == 3) {
                    z = false;
                }
                this.mInGestureDetection = z;
                if (this.mInGestureDetection) {
                    this.mGestureDetector.onTouchEvent(motionEvent);
                } else {
                    MotionEvent cancelEvent = motionEvent.copy();
                    cancelEvent.cancel();
                    this.mGestureDetector.onTouchEvent(cancelEvent);
                    stopTwoFingerScroll();
                }
            }
        }
    }

    private void onTwoFingerScroll(MotionEvent e) {
        int x = (int) e.getX(0);
        int y = (int) e.getY(0);
        if (!this.mTwoFingerScrolling) {
            this.mTwoFingerScrolling = true;
            this.mService.mH.obtainMessage(44, x, y, this.mDisplayContent).sendToTarget();
        }
    }

    private void stopTwoFingerScroll() {
        if (this.mTwoFingerScrolling) {
            this.mTwoFingerScrolling = false;
            this.mService.mH.obtainMessage(40).sendToTarget();
        }
    }

    void setTouchExcludeRegion(Region newRegion, Region nonResizeableRegion) {
        synchronized (this) {
            this.mTouchExcludeRegion.set(newRegion);
            this.mNonResizeableRegion.set(nonResizeableRegion);
        }
    }
}
