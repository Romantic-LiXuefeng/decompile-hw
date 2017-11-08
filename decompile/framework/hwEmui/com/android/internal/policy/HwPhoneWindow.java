package com.android.internal.policy;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.hwcontrol.HwWidgetFactory;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.IWindowManager;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import androidhwext.R;
import com.android.internal.widget.ActionBarOverlayLayout;
import com.android.internal.widget.FloatingToolbar;
import com.huawei.hsm.permission.StubController;
import huawei.android.utils.HwRTBlurUtils;
import huawei.android.utils.HwRTBlurUtils.BlurParams;

public class HwPhoneWindow extends PhoneWindow {
    private static final int FLOATING_MASK = Integer.MIN_VALUE;
    static final String TAG = "HwPhoneWindow";
    private boolean mHwDrawerFeature;
    private boolean mHwFloating;
    private int mHwOverlayActionBar;
    private boolean mIsTranslucentImmersion;
    private boolean mSplitMode;

    public HwPhoneWindow(Context context) {
        super(context);
    }

    public HwPhoneWindow(Context context, Window preservedWindow) {
        super(context, preservedWindow);
    }

    protected boolean isEmuiStyle() {
        return HwWidgetFactory.isHwTheme(getContext());
    }

    protected int getHeightMeasureSpec(int fixh, int heightSize, int defaultHeightMeasureSpec) {
        if (!isEmuiStyle()) {
            return super.getHeightMeasureSpec(fixh, heightSize, defaultHeightMeasureSpec);
        }
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        if (metrics.widthPixels > metrics.heightPixels) {
        }
        if (fixh < metrics.heightPixels) {
            float factor = (((float) fixh) + 0.0f) / ((float) metrics.heightPixels);
            fixh -= (int) (((float) getContext().getResources().getDimensionPixelSize(17104919)) * factor);
        }
        return MeasureSpec.makeMeasureSpec(Math.min(fixh, heightSize), FLOATING_MASK);
    }

    protected int getEmuiActionBarLayout(int layoutResource) {
        if (isEmuiStyle()) {
            return 34013272;
        }
        return super.getEmuiActionBarLayout(layoutResource);
    }

    protected void setEmuiActionModeBar(ViewStub viewStub) {
        if (viewStub != null && isEmuiStyle()) {
            viewStub.setLayoutResource(34013271);
        }
    }

    protected boolean CheckPermanentMenuKey() {
        if (isEmuiStyle()) {
            return true;
        }
        return super.CheckPermanentMenuKey();
    }

    protected void updateLayoutParamsColor() {
        boolean z = false;
        LayoutParams attrs = getAttributes();
        boolean changed = false;
        int statusBarColor = getStatusBarColor();
        if (attrs.statusBarColor != statusBarColor) {
            changed = true;
            attrs.statusBarColor = statusBarColor;
        }
        int navigationBarColor = getNavigationBarColor();
        if (attrs.navigationBarColor != navigationBarColor) {
            changed = true;
            attrs.navigationBarColor = navigationBarColor;
        }
        int emuiValue = -1;
        boolean isEmuiLight = false;
        if ((attrs.privateFlags & StubController.PERMISSION_DELETE_CALENDAR) == 0) {
            emuiValue = isEmuiStyle() ? 1 : 0;
            if (HwWidgetFactory.isHwLightTheme(getContext())) {
                isEmuiLight = true;
            }
            emuiValue = getEmuiValue(emuiValue);
        }
        if (attrs.isEmuiStyle != emuiValue) {
            changed = true;
            attrs.isEmuiStyle = emuiValue;
        }
        if ((attrs.hwFlags & 16) != 0) {
            z = true;
        }
        if (isEmuiLight != z) {
            changed = true;
            if (isEmuiLight) {
                attrs.hwFlags |= 16;
            } else {
                attrs.hwFlags &= -17;
            }
        }
        if (changed) {
            dispatchWindowAttributesChanged(attrs);
        }
    }

    private int getEmuiValue(int emuiValue) {
        if (this.mIsFloating || this.mHwFloating || (emuiValue != 0 && windowIsTranslucent() && !isTranslucentImmersion())) {
            return emuiValue | FLOATING_MASK;
        }
        return emuiValue;
    }

    public void setHwFloating(boolean isFloating) {
        if (this.mHwFloating != isFloating) {
            this.mHwFloating = isFloating;
            updateLayoutParamsColor();
        }
    }

    public boolean getHwFloating() {
        return this.mHwFloating;
    }

    protected boolean getTryForcedCloseAnimation(IWindowManager wm, boolean animate, Object tag) {
        if (animate && "TryForcedCloseAnimation".equals(tag)) {
            return true;
        }
        return false;
    }

    public int getStatusBarColor() {
        if (HwWidgetFactory.isHwLightTheme(getContext())) {
            return HwWidgetFactory.getPrimaryColor(getContext());
        }
        return super.getStatusBarColor();
    }

    protected FloatingToolbar getFloatingToolbar(Context context, Window window) {
        return new FloatingToolbar(context, window, true);
    }

    protected void initTranslucentImmersion() {
        boolean z = false;
        Context context = getContext();
        if (context != null) {
            TypedValue tv = new TypedValue();
            context.getTheme().resolveAttribute(34799627, tv, false);
            if (tv.type == 18 && tv.data == -1) {
                z = true;
            }
            this.mIsTranslucentImmersion = z;
        }
    }

    protected void initSplitMode() {
        boolean z = false;
        Context c = getContext();
        if (c != null && (c instanceof Activity)) {
            Intent intent = ((Activity) c).getIntent();
            if (intent != null) {
                if ((intent.getHwFlags() & 4) != 0) {
                    z = true;
                }
                this.mSplitMode = z;
            }
        }
    }

    protected boolean isSplitMode() {
        return this.mSplitMode;
    }

    protected boolean isTranslucentImmersion() {
        return this.mIsTranslucentImmersion;
    }

    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        updateBlurStatus();
        initChildWindowIgnoreParentWindowClipRect();
    }

    public void setContentView(View view) {
        super.setContentView(view);
        updateBlurStatus();
        initChildWindowIgnoreParentWindowClipRect();
    }

    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        updateBlurStatus();
        initChildWindowIgnoreParentWindowClipRect();
    }

    private void updateBlurStatus() {
        LayoutParams lp = getAttributes();
        BlurParams blurParams = HwRTBlurUtils.obtainBlurStyle(getContext(), null, 33619992, 0, "HwPhoneWindow-" + (lp != null ? lp.getTitle().toString() : ""));
        HwRTBlurUtils.updateWindowBgForBlur(blurParams, getDecorView());
        HwRTBlurUtils.updateBlurStatus(getAttributes(), blurParams);
    }

    private void initChildWindowIgnoreParentWindowClipRect() {
        TypedArray ahwext = getContext().obtainStyledAttributes(null, R.styleable.Window, 0, 0);
        boolean ignore = ahwext.getBoolean(0, false);
        ahwext.recycle();
        LayoutParams lp = getAttributes();
        if (ignore) {
            lp.privateFlags |= StubController.PERMISSION_WIFI;
        } else {
            lp.privateFlags &= -2097153;
        }
    }

    public void setHwDrawerFeature(boolean using, int overlayActionBar) {
        this.mHwDrawerFeature = using;
        if (using) {
            this.mHwOverlayActionBar = overlayActionBar;
        } else {
            this.mHwOverlayActionBar = 0;
        }
        initHwDrawerFeature();
    }

    protected void initHwDrawerFeature() {
        if (this.mDecorContentParent instanceof ActionBarOverlayLayout) {
            this.mDecorContentParent.setHwDrawerFeature(this.mHwDrawerFeature, this.mHwOverlayActionBar);
        }
    }

    public void setDrawerOpend(boolean open) {
        if (this.mDecorContentParent != null && (this.mDecorContentParent instanceof ActionBarOverlayLayout)) {
            ((ActionBarOverlayLayout) this.mDecorContentParent).setDrawerOpend(open);
        }
    }
}
