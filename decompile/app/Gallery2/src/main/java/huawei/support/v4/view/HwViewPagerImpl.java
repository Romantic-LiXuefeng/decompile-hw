package huawei.support.v4.view;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.support.v4.interfaces.HwControlFactory.HwViewPager;
import android.util.Log;
import android.view.animation.PathInterpolator;
import android.widget.Scroller;
import com.huawei.watermark.manager.parse.WMElement;
import dalvik.system.PathClassLoader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class HwViewPagerImpl implements HwViewPager {
    private String TAG = "HwViewPagerImpl";
    private ActionBar mActionBar;
    private Context mContext;
    private float mQuarterWidth;
    private Method mgetTabContainerMethod = null;

    public HwViewPagerImpl(Context context) {
        this.mQuarterWidth = ((float) context.getResources().getDisplayMetrics().widthPixels) * 0.33333334f;
        createTabScrollingMethod(context);
        this.mContext = context;
    }

    public Scroller createScroller(Context context) {
        return new Scroller(context, new PathInterpolator(0.325f, 0.63f, 0.05f, WMElement.CAMERASIZEVALUE1B1));
    }

    public void tabScrollerFollowed(int position, float offset) {
        if (this.mActionBar == null) {
            this.mActionBar = getActionBar();
        }
        if (this.mActionBar != null && offset >= 0.0f && this.mgetTabContainerMethod != null) {
            try {
                this.mgetTabContainerMethod.invoke(null, new Object[]{this.mActionBar, Integer.valueOf(position), Float.valueOf(offset)});
            } catch (IllegalArgumentException e) {
                Log.w(this.TAG, "mgetTabContainerMethod invoke catch IllegalArgumentException");
            } catch (IllegalAccessException e2) {
                Log.w(this.TAG, "mgetTabContainerMethod invoke catch IllegalAccessException");
            } catch (InvocationTargetException e3) {
                Log.w(this.TAG, "mgetTabContainerMethod invoke catch InvocationTargetException");
            }
        }
    }

    public float scrollEdgeBound(boolean left, float oldScroller, float deltax, float bound) {
        float scroller = oldScroller + (0.33333334f * deltax);
        if (left) {
            return Math.max(scroller, bound - this.mQuarterWidth);
        }
        return Math.min(scroller, this.mQuarterWidth + bound);
    }

    private void createTabScrollingMethod(Context context) {
        try {
            Class<?> actionBarExUtilclazz = new PathClassLoader("/system/framework/hwframework.jar", context.getClassLoader()).loadClass("com.huawei.android.app.ActionBarEx");
            if (actionBarExUtilclazz != null) {
                this.mgetTabContainerMethod = actionBarExUtilclazz.getDeclaredMethod("setTabScrollingOffsets", new Class[]{ActionBar.class, Integer.TYPE, Float.TYPE});
            }
        } catch (ClassNotFoundException e) {
            Log.w(this.TAG, "create Tab Scrolling Method catch ClassNotFoundException");
        } catch (NoSuchMethodException e2) {
            Log.w(this.TAG, "create Tab Scrolling Method catch NoSuchMethodException");
        }
    }

    private ActionBar getActionBar() {
        Context context = this.mContext;
        Activity activity = null;
        while (activity == null && context != null) {
            if (context instanceof Activity) {
                activity = (Activity) context;
            } else {
                context = context instanceof ContextWrapper ? ((ContextWrapper) context).getBaseContext() : null;
            }
        }
        if (activity == null) {
            return null;
        }
        return activity.getActionBar();
    }
}
