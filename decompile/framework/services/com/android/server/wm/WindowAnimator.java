package com.android.server.wm;

import android.content.Context;
import android.os.Trace;
import android.util.Slog;
import android.util.SparseArray;
import android.util.TimeUtils;
import android.view.Choreographer.FrameCallback;
import android.view.SurfaceControl;
import android.view.WindowManagerPolicy;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import java.io.PrintWriter;
import java.util.ArrayList;

public class WindowAnimator {
    static final int KEYGUARD_ANIMATING_OUT = 2;
    private static final long KEYGUARD_ANIM_TIMEOUT_MS = 2000;
    static final int KEYGUARD_NOT_SHOWN = 0;
    static final int KEYGUARD_SHOWN = 1;
    private static final String TAG = "WindowManager";
    private int mAnimTransactionSequence;
    private boolean mAnimating;
    final FrameCallback mAnimationFrameCallback;
    boolean mAppWindowAnimating;
    int mBulkUpdateParams = 0;
    final Context mContext;
    long mCurrentTime;
    SparseArray<DisplayContentsAnimator> mDisplayContentsAnimators = new SparseArray(2);
    int mForceHiding = 0;
    boolean mInitialized = false;
    boolean mKeyguardGoingAway;
    int mKeyguardGoingAwayFlags;
    Object mLastWindowFreezeSource;
    final WindowManagerPolicy mPolicy;
    Animation mPostKeyguardExitAnimation;
    private boolean mRemoveReplacedWindows = false;
    final WindowManagerService mService;
    WindowState mWindowDetachedWallpaper = null;
    private final WindowSurfacePlacer mWindowPlacerLocked;
    int offsetLayer = 0;

    private class DisplayContentsAnimator {
        ScreenRotationAnimation mScreenRotationAnimation;

        private DisplayContentsAnimator() {
            this.mScreenRotationAnimation = null;
        }
    }

    private String forceHidingToString() {
        switch (this.mForceHiding) {
            case 0:
                return "KEYGUARD_NOT_SHOWN";
            case 1:
                return "KEYGUARD_SHOWN";
            case 2:
                return "KEYGUARD_ANIMATING_OUT";
            default:
                return "KEYGUARD STATE UNKNOWN " + this.mForceHiding;
        }
    }

    WindowAnimator(WindowManagerService service) {
        this.mService = service;
        this.mContext = service.mContext;
        this.mPolicy = service.mPolicy;
        this.mWindowPlacerLocked = service.mWindowPlacerLocked;
        this.mAnimationFrameCallback = new FrameCallback() {
            public void doFrame(long frameTimeNs) {
                synchronized (WindowAnimator.this.mService.mWindowMap) {
                    WindowAnimator.this.mService.mAnimationScheduled = false;
                    WindowAnimator.this.animateLocked(frameTimeNs);
                }
            }
        };
    }

    void addDisplayLocked(int displayId) {
        getDisplayContentsAnimatorLocked(displayId);
        if (displayId == 0) {
            this.mInitialized = true;
        }
    }

    void removeDisplayLocked(int displayId) {
        DisplayContentsAnimator displayAnimator = (DisplayContentsAnimator) this.mDisplayContentsAnimators.get(displayId);
        if (!(displayAnimator == null || displayAnimator.mScreenRotationAnimation == null)) {
            displayAnimator.mScreenRotationAnimation.kill();
            displayAnimator.mScreenRotationAnimation = null;
        }
        this.mDisplayContentsAnimators.delete(displayId);
    }

    private void updateAppWindowsLocked(int displayId) {
        ArrayList<TaskStack> stacks = this.mService.getDisplayContentLocked(displayId).getStacks();
        for (int stackNdx = stacks.size() - 1; stackNdx >= 0; stackNdx--) {
            TaskStack stack = (TaskStack) stacks.get(stackNdx);
            ArrayList<Task> tasks = stack.getTasks();
            for (int taskNdx = tasks.size() - 1; taskNdx >= 0; taskNdx--) {
                AppTokenList tokens = ((Task) tasks.get(taskNdx)).mAppTokens;
                for (int tokenNdx = tokens.size() - 1; tokenNdx >= 0; tokenNdx--) {
                    AppWindowAnimator appAnimator = ((AppWindowToken) tokens.get(tokenNdx)).mAppAnimator;
                    appAnimator.wasAnimating = appAnimator.animating;
                    if (appAnimator.stepAnimationLocked(this.mCurrentTime, displayId)) {
                        appAnimator.animating = true;
                        setAnimating(true);
                        this.mAppWindowAnimating = true;
                    } else if (appAnimator.wasAnimating) {
                        setAppLayoutChanges(appAnimator, 4, "appToken " + appAnimator.mAppToken + " done", displayId);
                    }
                }
            }
            AppTokenList exitingAppTokens = stack.mExitingAppTokens;
            for (int i = 0; i < exitingAppTokens.size(); i++) {
                appAnimator = ((AppWindowToken) exitingAppTokens.get(i)).mAppAnimator;
                appAnimator.wasAnimating = appAnimator.animating;
                if (appAnimator.stepAnimationLocked(this.mCurrentTime, displayId)) {
                    setAnimating(true);
                    this.mAppWindowAnimating = true;
                } else if (appAnimator.wasAnimating) {
                    setAppLayoutChanges(appAnimator, 4, "exiting appToken " + appAnimator.mAppToken + " done", displayId);
                }
            }
        }
    }

    private boolean shouldForceHide(WindowState win) {
        boolean allowWhenLocked;
        int i;
        int i2 = 0;
        WindowState imeTarget = this.mService.mInputMethodTarget;
        boolean showImeOverKeyguard = (imeTarget == null || !imeTarget.isVisibleNow()) ? false : (imeTarget.getAttrs().flags & DumpState.DUMP_FROZEN) == 0 ? !this.mPolicy.canBeForceHidden(imeTarget, imeTarget.mAttrs) : true;
        WindowState winShowWhenLocked = (WindowState) this.mPolicy.getWinShowWhenLockedLw();
        AppWindowToken appWindowToken = winShowWhenLocked == null ? null : winShowWhenLocked.mAppToken;
        if (win.mIsImWindow || imeTarget == win) {
            allowWhenLocked = showImeOverKeyguard;
        } else {
            allowWhenLocked = false;
        }
        if ((win.mAttrs.flags & DumpState.DUMP_FROZEN) != 0) {
            i = win.mTurnOnScreen;
        } else {
            i = 0;
        }
        allowWhenLocked |= i;
        if (appWindowToken != null) {
            if (appWindowToken == win.mAppToken || (win.mAttrs.flags & DumpState.DUMP_FROZEN) != 0) {
                i2 = 1;
            } else if ((win.mAttrs.privateFlags & 256) != 0) {
                i2 = 1;
            }
            allowWhenLocked |= i2;
        }
        boolean keyguardOn = this.mPolicy.isKeyguardShowingOrOccluded() ? this.mForceHiding != 2 : false;
        boolean hideDockDivider = win.mAttrs.type == 2034 ? win.getDisplayContent().getDockedStackLocked() == null : false;
        if (keyguardOn && !allowWhenLocked && win.getDisplayId() == 0) {
            return true;
        }
        return hideDockDivider;
    }

    private void updateWindowsLocked(int displayId) {
        int i;
        WindowState win;
        WindowStateAnimator winAnimator;
        this.mAnimTransactionSequence++;
        WindowList windows = this.mService.getWindowListLocked(displayId);
        boolean keyguardGoingAwayToShade = (this.mKeyguardGoingAwayFlags & 1) != 0;
        boolean keyguardGoingAwayNoAnimation = (this.mKeyguardGoingAwayFlags & 2) != 0;
        boolean keyguardGoingAwayWithWallpaper = (this.mKeyguardGoingAwayFlags & 4) != 0;
        if (this.mKeyguardGoingAway) {
            i = windows.size() - 1;
            while (i >= 0) {
                win = (WindowState) windows.get(i);
                if (this.mPolicy.isKeyguardHostWindow(win.mAttrs)) {
                    winAnimator = win.mWinAnimator;
                    if ((win.mAttrs.privateFlags & 1024) == 0) {
                        this.mKeyguardGoingAway = false;
                        winAnimator.clearAnimation();
                    } else if (!winAnimator.mAnimating) {
                        winAnimator.mAnimation = new AlphaAnimation(1.0f, 1.0f);
                        winAnimator.mAnimation.setDuration(KEYGUARD_ANIM_TIMEOUT_MS);
                        winAnimator.mAnimationIsEntrance = false;
                        winAnimator.mAnimationStartTime = -1;
                        winAnimator.mKeyguardGoingAwayAnimation = true;
                        winAnimator.mKeyguardGoingAwayWithWallpaper = keyguardGoingAwayWithWallpaper;
                    }
                } else {
                    i--;
                }
            }
        }
        this.mForceHiding = 0;
        boolean wallpaperInUnForceHiding = false;
        boolean startingInUnForceHiding = false;
        boolean foundLauncherDrawn = false;
        ArrayList unForceHiding = null;
        WindowState wallpaper = null;
        WallpaperController wallpaperController = this.mService.mWallpaperControllerLocked;
        for (i = windows.size() - 1; i >= 0; i--) {
            win = (WindowState) windows.get(i);
            winAnimator = win.mWinAnimator;
            int flags = win.mAttrs.flags;
            boolean canBeForceHidden = this.mPolicy.canBeForceHidden(win, win.mAttrs);
            boolean shouldBeForceHidden = shouldForceHide(win);
            if (winAnimator.hasSurface()) {
                boolean wasAnimating = winAnimator.mWasAnimating;
                boolean nowAnimating = winAnimator.stepAnimationLocked(this.mCurrentTime);
                winAnimator.mWasAnimating = nowAnimating;
                orAnimating(nowAnimating);
                if (wasAnimating && !winAnimator.mAnimating && wallpaperController.isWallpaperTarget(win)) {
                    this.mBulkUpdateParams |= 2;
                    setPendingLayoutChanges(0, 4);
                }
                if (this.mKeyguardGoingAway && "com.huawei.android.launcher".equals(win.getOwningPackage()) && win.hasDrawnLw()) {
                    foundLauncherDrawn = true;
                }
                if (this.mPolicy.isForceHiding(win.mAttrs)) {
                    if (!wasAnimating && nowAnimating) {
                        this.mBulkUpdateParams |= 4;
                        setPendingLayoutChanges(displayId, 4);
                        this.mService.mFocusMayChange = true;
                    } else if (this.mKeyguardGoingAway && !nowAnimating) {
                        Slog.e(TAG, "Timeout waiting for animation to startup");
                        this.mPolicy.startKeyguardExitAnimation(0, 0);
                        this.mKeyguardGoingAway = false;
                    }
                    if (win.isReadyForDisplay()) {
                        if (nowAnimating && win.mWinAnimator.mKeyguardGoingAwayAnimation) {
                            this.mForceHiding = 2;
                        } else {
                            this.mForceHiding = win.isDrawnLw() ? 1 : 0;
                        }
                    }
                } else if (canBeForceHidden) {
                    if (!shouldBeForceHidden) {
                        boolean applyExistingExitAnimation = (this.mPostKeyguardExitAnimation == null || this.mPostKeyguardExitAnimation.hasEnded() || winAnimator.mKeyguardGoingAwayAnimation || !win.hasDrawnLw() || win.mAttachedWindow != null || win.mIsImWindow) ? false : displayId == 0;
                        if (win.showLw(false, false) || applyExistingExitAnimation) {
                            if (win.isVisibleNow()) {
                                if ((this.mBulkUpdateParams & 4) == 0 || win.mAttachedWindow != null) {
                                    if (applyExistingExitAnimation) {
                                        winAnimator.setAnimation(this.mPolicy.createForceHideEnterAnimation(false, keyguardGoingAwayToShade), this.mPostKeyguardExitAnimation.getStartTime(), 1);
                                        winAnimator.mKeyguardGoingAwayAnimation = true;
                                        winAnimator.mKeyguardGoingAwayWithWallpaper = keyguardGoingAwayWithWallpaper;
                                    }
                                } else if (!win.toString().contains("com.android.settings/com.android.settings.FallbackHome")) {
                                    if (unForceHiding == null) {
                                        unForceHiding = new ArrayList();
                                    }
                                    unForceHiding.add(winAnimator);
                                    if ((DumpState.DUMP_DEXOPT & flags) != 0) {
                                        wallpaperInUnForceHiding = true;
                                    }
                                    if (win.mAttrs.type == 3) {
                                        startingInUnForceHiding = true;
                                    }
                                }
                                WindowState currentFocus = this.mService.mCurrentFocus;
                                if (currentFocus == null || currentFocus.mLayer < win.mLayer) {
                                    this.mService.mFocusMayChange = true;
                                }
                            } else {
                                win.hideLw(false, false);
                            }
                        } else {
                        }
                    } else if (!win.hideLw(false, false)) {
                    }
                    if ((DumpState.DUMP_DEXOPT & flags) != 0) {
                        this.mBulkUpdateParams |= 2;
                        setPendingLayoutChanges(0, 4);
                    }
                }
            } else if (canBeForceHidden) {
                if (shouldBeForceHidden) {
                    win.hideLw(false, false);
                } else {
                    win.showLw(false, false);
                }
            }
            AppWindowToken atoken = win.mAppToken;
            if (winAnimator.mDrawState == 3 && ((atoken == null || atoken.allDrawn) && winAnimator.performShowLocked())) {
                setPendingLayoutChanges(displayId, 8);
            }
            AppWindowAnimator appAnimator = winAnimator.mAppAnimator;
            if (!(appAnimator == null || appAnimator.thumbnail == null)) {
                if (appAnimator.thumbnailTransactionSeq != this.mAnimTransactionSequence) {
                    appAnimator.thumbnailTransactionSeq = this.mAnimTransactionSequence;
                    appAnimator.thumbnailLayer = 0;
                }
                if (appAnimator.thumbnailLayer < winAnimator.mAnimLayer) {
                    appAnimator.thumbnailLayer = winAnimator.mAnimLayer;
                }
            }
            if (win.mIsWallpaper) {
                wallpaper = win;
            }
        }
        if (unForceHiding != null) {
            Animation a;
            if (!keyguardGoingAwayNoAnimation) {
                boolean first = true;
                for (i = unForceHiding.size() - 1; i >= 0; i--) {
                    winAnimator = (WindowStateAnimator) unForceHiding.get(i);
                    WindowManagerPolicy windowManagerPolicy = this.mPolicy;
                    boolean z = wallpaperInUnForceHiding && !startingInUnForceHiding;
                    a = windowManagerPolicy.createForceHideEnterAnimation(z, keyguardGoingAwayToShade);
                    if (a != null) {
                        winAnimator.setAnimation(a, 1);
                        winAnimator.mKeyguardGoingAwayAnimation = true;
                        winAnimator.mKeyguardGoingAwayWithWallpaper = keyguardGoingAwayWithWallpaper;
                        if (first) {
                            this.mPostKeyguardExitAnimation = a;
                            this.mPostKeyguardExitAnimation.setStartTime(this.mCurrentTime);
                            first = false;
                        }
                    }
                }
            } else if (this.mKeyguardGoingAway) {
                this.mPolicy.startKeyguardExitAnimation(this.mCurrentTime, 0);
                this.mKeyguardGoingAway = false;
            }
            if (!(wallpaperInUnForceHiding || wallpaper == null || keyguardGoingAwayNoAnimation)) {
                a = this.mPolicy.createForceHideWallpaperExitAnimation(keyguardGoingAwayToShade);
                if (a != null) {
                    wallpaper.mWinAnimator.setAnimation(a);
                }
            }
        }
        if (this.mPostKeyguardExitAnimation != null) {
            if (this.mKeyguardGoingAway) {
                this.mPolicy.startKeyguardExitAnimation(this.mCurrentTime + this.mPostKeyguardExitAnimation.getStartOffset(), this.mPostKeyguardExitAnimation.getDuration());
                this.mKeyguardGoingAway = false;
            } else if (this.mPostKeyguardExitAnimation.hasEnded() || this.mCurrentTime - this.mPostKeyguardExitAnimation.getStartTime() > this.mPostKeyguardExitAnimation.getDuration()) {
                this.mPostKeyguardExitAnimation = null;
            }
        } else if (unForceHiding == null && this.mKeyguardGoingAway && foundLauncherDrawn) {
            this.mPolicy.startKeyguardExitAnimation(this.mCurrentTime, 100);
            this.mKeyguardGoingAway = false;
        }
    }

    private void updateWallpaperLocked(int displayId) {
        this.mService.getDisplayContentLocked(displayId).resetAnimationBackgroundAnimator();
        WindowList windows = this.mService.getWindowListLocked(displayId);
        WindowState detachedWallpaper = null;
        for (int i = windows.size() - 1; i >= 0; i--) {
            WindowState win = (WindowState) windows.get(i);
            WindowStateAnimator winAnimator = win.mWinAnimator;
            if (winAnimator.mSurfaceController != null && winAnimator.hasSurface()) {
                int color;
                TaskStack stack;
                int flags = win.mAttrs.flags;
                if (winAnimator.mAnimating) {
                    if (winAnimator.mAnimation != null) {
                        if ((flags & DumpState.DUMP_DEXOPT) != 0 && winAnimator.mAnimation.getDetachWallpaper()) {
                            detachedWallpaper = win;
                        }
                        color = winAnimator.mAnimation.getBackgroundColor();
                        if (color != 0) {
                            stack = win.getStack();
                            if (stack != null) {
                                stack.setAnimationBackground(winAnimator, color);
                            }
                        }
                    }
                    setAnimating(true);
                }
                AppWindowAnimator appAnimator = winAnimator.mAppAnimator;
                if (!(appAnimator == null || appAnimator.animation == null || !appAnimator.animating)) {
                    if ((flags & DumpState.DUMP_DEXOPT) != 0 && appAnimator.animation.getDetachWallpaper()) {
                        detachedWallpaper = win;
                    }
                    color = appAnimator.animation.getBackgroundColor();
                    if (color != 0) {
                        stack = win.getStack();
                        if (stack != null) {
                            stack.setAnimationBackground(winAnimator, color);
                        }
                    }
                }
            }
        }
        if (this.mWindowDetachedWallpaper != detachedWallpaper) {
            this.mWindowDetachedWallpaper = detachedWallpaper;
            this.mBulkUpdateParams |= 2;
        }
    }

    private void testTokenMayBeDrawnLocked(int displayId) {
        ArrayList<Task> tasks = this.mService.getDisplayContentLocked(displayId).getTasks();
        int numTasks = tasks.size();
        for (int taskNdx = 0; taskNdx < numTasks; taskNdx++) {
            AppTokenList tokens = ((Task) tasks.get(taskNdx)).mAppTokens;
            int numTokens = tokens.size();
            for (int tokenNdx = 0; tokenNdx < numTokens; tokenNdx++) {
                AppWindowToken wtoken = (AppWindowToken) tokens.get(tokenNdx);
                AppWindowAnimator appAnimator = wtoken.mAppAnimator;
                boolean allDrawn = wtoken.allDrawn;
                if (allDrawn != appAnimator.allDrawn) {
                    appAnimator.allDrawn = allDrawn;
                    if (allDrawn) {
                        if (appAnimator.freezingScreen) {
                            appAnimator.showAllWindowsLocked();
                            this.mService.unsetAppFreezingScreenLocked(wtoken, false, true);
                            setAppLayoutChanges(appAnimator, 4, "testTokenMayBeDrawnLocked: freezingScreen", displayId);
                        } else {
                            setAppLayoutChanges(appAnimator, 8, "testTokenMayBeDrawnLocked", displayId);
                            if (!this.mService.mOpeningApps.contains(wtoken)) {
                                orAnimating(appAnimator.showAllWindowsLocked());
                            }
                        }
                    }
                }
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void animateLocked(long frameTimeNs) {
        if (this.mInitialized) {
            int numDisplays;
            this.mCurrentTime = frameTimeNs / 1000000;
            this.mBulkUpdateParams = 8;
            boolean wasAnimating = this.mAnimating;
            setAnimating(false);
            this.mAppWindowAnimating = false;
            boolean isLazying = false;
            SurfaceControl.openTransaction();
            SurfaceControl.setAnimationTransaction();
            try {
                int i;
                int displayId;
                ScreenRotationAnimation screenRotationAnimation;
                numDisplays = this.mDisplayContentsAnimators.size();
                for (i = 0; i < numDisplays; i++) {
                    displayId = this.mDisplayContentsAnimators.keyAt(i);
                    updateAppWindowsLocked(displayId);
                    DisplayContentsAnimator displayAnimator = (DisplayContentsAnimator) this.mDisplayContentsAnimators.valueAt(i);
                    screenRotationAnimation = displayAnimator.mScreenRotationAnimation;
                    if (screenRotationAnimation != null && screenRotationAnimation.isAnimating()) {
                        if (screenRotationAnimation.stepAnimationLocked(this.mCurrentTime)) {
                            setAnimating(true);
                        } else {
                            this.mBulkUpdateParams |= 1;
                            screenRotationAnimation.kill();
                            displayAnimator.mScreenRotationAnimation = null;
                            if (this.mService.mAccessibilityController != null && displayId == 0) {
                                this.mService.mAccessibilityController.onRotationChangedLocked(this.mService.getDefaultDisplayContentLocked(), this.mService.mRotation);
                            }
                        }
                    }
                    updateWindowsLocked(displayId);
                    updateWallpaperLocked(displayId);
                    WindowList windows = this.mService.getWindowListLocked(displayId);
                    int N = windows.size();
                    int j = 0;
                    while (j < N) {
                        ((WindowState) windows.get(j)).mWinAnimator.prepareSurfaceLocked(true);
                        if (((WindowState) windows.get(j)).mWinAnimator.mLazyIsExiting || ((WindowState) windows.get(j)).mWinAnimator.mLazyIsEntering) {
                            isLazying = true;
                        }
                        j++;
                    }
                }
                for (i = 0; i < numDisplays; i++) {
                    displayId = this.mDisplayContentsAnimators.keyAt(i);
                    testTokenMayBeDrawnLocked(displayId);
                    screenRotationAnimation = ((DisplayContentsAnimator) this.mDisplayContentsAnimators.valueAt(i)).mScreenRotationAnimation;
                    if (screenRotationAnimation != null) {
                        screenRotationAnimation.updateSurfacesInTransaction();
                    }
                    orAnimating(this.mService.getDisplayContentLocked(displayId).animateDimLayers());
                    orAnimating(this.mService.getDisplayContentLocked(displayId).getDockedDividerController().animate(this.mCurrentTime));
                    updateBlurLayers(displayId);
                    if (this.mService.mAccessibilityController != null && displayId == 0) {
                        this.mService.mAccessibilityController.drawMagnifiedRegionBorderIfNeededLocked();
                    }
                }
                if (this.mService.mDragState != null) {
                    this.mAnimating |= this.mService.mDragState.stepAnimationLocked(this.mCurrentTime);
                }
                if (this.mAnimating || isLazying) {
                    this.mService.scheduleAnimationLocked();
                }
                if (this.mService.mWatermark != null) {
                    this.mService.mWatermark.drawIfNeeded();
                }
                SurfaceControl.closeTransaction();
            } catch (RuntimeException e) {
                Slog.wtf(TAG, "Unhandled exception in Window Manager", e);
            } catch (Throwable th) {
                SurfaceControl.closeTransaction();
            }
            boolean hasPendingLayoutChanges = false;
            numDisplays = this.mService.mDisplayContents.size();
            for (int displayNdx = 0; displayNdx < numDisplays; displayNdx++) {
                int pendingChanges = getPendingLayoutChanges(((DisplayContent) this.mService.mDisplayContents.valueAt(displayNdx)).getDisplayId());
                if ((pendingChanges & 4) != 0) {
                    this.mBulkUpdateParams |= 32;
                }
                if (pendingChanges != 0) {
                    hasPendingLayoutChanges = true;
                }
            }
            boolean doRequest = false;
            if (this.mBulkUpdateParams != 0) {
                doRequest = this.mWindowPlacerLocked.copyAnimToLayoutParamsLocked();
            }
            if (hasPendingLayoutChanges || r9) {
                this.mWindowPlacerLocked.requestTraversal();
            }
            if (this.mAnimating && !wasAnimating && Trace.isTagEnabled(32)) {
                Trace.asyncTraceBegin(32, "animating", 0);
            }
            if (!this.mAnimating && wasAnimating) {
                this.mWindowPlacerLocked.requestTraversal();
                if (Trace.isTagEnabled(32)) {
                    Trace.asyncTraceEnd(32, "animating", 0);
                }
            }
            if (this.mRemoveReplacedWindows) {
                removeReplacedWindowsLocked();
            }
            this.mService.stopUsingSavedSurfaceLocked();
            this.mService.destroyPreservedSurfaceLocked();
            this.mService.mWindowPlacerLocked.destroyPendingSurfaces();
        }
    }

    private void removeReplacedWindowsLocked() {
        SurfaceControl.openTransaction();
        try {
            for (int i = this.mService.mDisplayContents.size() - 1; i >= 0; i--) {
                WindowList windows = this.mService.getWindowListLocked(((DisplayContent) this.mService.mDisplayContents.valueAt(i)).getDisplayId());
                for (int j = windows.size() - 1; j >= 0; j--) {
                    ((WindowState) windows.get(j)).maybeRemoveReplacedWindow();
                }
            }
            this.mRemoveReplacedWindows = false;
        } finally {
            SurfaceControl.closeTransaction();
        }
    }

    private static String bulkUpdateParamsToString(int bulkUpdateParams) {
        StringBuilder builder = new StringBuilder(128);
        if ((bulkUpdateParams & 1) != 0) {
            builder.append(" UPDATE_ROTATION");
        }
        if ((bulkUpdateParams & 2) != 0) {
            builder.append(" WALLPAPER_MAY_CHANGE");
        }
        if ((bulkUpdateParams & 4) != 0) {
            builder.append(" FORCE_HIDING_CHANGED");
        }
        if ((bulkUpdateParams & 8) != 0) {
            builder.append(" ORIENTATION_CHANGE_COMPLETE");
        }
        if ((bulkUpdateParams & 16) != 0) {
            builder.append(" TURN_ON_SCREEN");
        }
        return builder.toString();
    }

    public void dumpLocked(PrintWriter pw, String prefix, boolean dumpAll) {
        String subPrefix = "  " + prefix;
        String subSubPrefix = "  " + subPrefix;
        for (int i = 0; i < this.mDisplayContentsAnimators.size(); i++) {
            pw.print(prefix);
            pw.print("DisplayContentsAnimator #");
            pw.print(this.mDisplayContentsAnimators.keyAt(i));
            pw.println(":");
            DisplayContentsAnimator displayAnimator = (DisplayContentsAnimator) this.mDisplayContentsAnimators.valueAt(i);
            WindowList windows = this.mService.getWindowListLocked(this.mDisplayContentsAnimators.keyAt(i));
            int N = windows.size();
            for (int j = 0; j < N; j++) {
                if (!((WindowState) windows.get(j)).toString().contains("hwSingleMode_window")) {
                    WindowStateAnimator wanim = ((WindowState) windows.get(j)).mWinAnimator;
                    pw.print(subPrefix);
                    pw.print("Window #");
                    pw.print(j);
                    pw.print(": ");
                    pw.println(wanim);
                }
            }
            if (displayAnimator.mScreenRotationAnimation != null) {
                pw.print(subPrefix);
                pw.println("mScreenRotationAnimation:");
                displayAnimator.mScreenRotationAnimation.printTo(subSubPrefix, pw);
            } else if (dumpAll) {
                pw.print(subPrefix);
                pw.println("no ScreenRotationAnimation ");
            }
            pw.println();
        }
        pw.println();
        if (dumpAll) {
            pw.print(prefix);
            pw.print("mAnimTransactionSequence=");
            pw.print(this.mAnimTransactionSequence);
            pw.print(" mForceHiding=");
            pw.println(forceHidingToString());
            pw.print(prefix);
            pw.print("mCurrentTime=");
            pw.println(TimeUtils.formatUptime(this.mCurrentTime));
        }
        if (this.mBulkUpdateParams != 0) {
            pw.print(prefix);
            pw.print("mBulkUpdateParams=0x");
            pw.print(Integer.toHexString(this.mBulkUpdateParams));
            pw.println(bulkUpdateParamsToString(this.mBulkUpdateParams));
        }
        if (this.mWindowDetachedWallpaper != null) {
            pw.print(prefix);
            pw.print("mWindowDetachedWallpaper=");
            pw.println(this.mWindowDetachedWallpaper);
        }
    }

    int getPendingLayoutChanges(int displayId) {
        int i = 0;
        if (displayId < 0) {
            return 0;
        }
        DisplayContent displayContent = this.mService.getDisplayContentLocked(displayId);
        if (displayContent != null) {
            i = displayContent.pendingLayoutChanges;
        }
        return i;
    }

    void setPendingLayoutChanges(int displayId, int changes) {
        if (displayId >= 0) {
            DisplayContent displayContent = this.mService.getDisplayContentLocked(displayId);
            if (displayContent != null) {
                displayContent.pendingLayoutChanges |= changes;
            }
        }
    }

    void setAppLayoutChanges(AppWindowAnimator appAnimator, int changes, String reason, int displayId) {
        WindowList windows = appAnimator.mAppToken.allAppWindows;
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (displayId == ((WindowState) windows.get(i)).getDisplayId()) {
                setPendingLayoutChanges(displayId, changes);
                return;
            }
        }
    }

    private DisplayContentsAnimator getDisplayContentsAnimatorLocked(int displayId) {
        DisplayContentsAnimator displayAnimator = (DisplayContentsAnimator) this.mDisplayContentsAnimators.get(displayId);
        if (displayAnimator != null) {
            return displayAnimator;
        }
        displayAnimator = new DisplayContentsAnimator();
        this.mDisplayContentsAnimators.put(displayId, displayAnimator);
        return displayAnimator;
    }

    void setScreenRotationAnimationLocked(int displayId, ScreenRotationAnimation animation) {
        if (displayId >= 0) {
            getDisplayContentsAnimatorLocked(displayId).mScreenRotationAnimation = animation;
        }
    }

    ScreenRotationAnimation getScreenRotationAnimationLocked(int displayId) {
        if (displayId < 0) {
            return null;
        }
        return getDisplayContentsAnimatorLocked(displayId).mScreenRotationAnimation;
    }

    void requestRemovalOfReplacedWindows(WindowState win) {
        this.mRemoveReplacedWindows = true;
    }

    boolean isAnimating() {
        return this.mAnimating;
    }

    void setAnimating(boolean animating) {
        this.mAnimating = animating;
    }

    void orAnimating(boolean animating) {
        this.mAnimating |= animating;
    }

    private void updateBlurLayers(int displayId) {
        WindowList windows = this.mService.getWindowListLocked(displayId);
        for (int i = windows.size() - 1; i >= 0; i--) {
            WindowState win = (WindowState) windows.get(i);
            if (!((win.mAttrs.flags & 4) == 0 || !win.isDisplayedLw() || win.mAnimatingExit)) {
                win.mWinAnimator.updateBlurLayer(win.mAttrs);
            }
        }
    }
}
