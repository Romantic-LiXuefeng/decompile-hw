package android.widget;

import android.content.Context;
import android.util.Log;
import java.lang.reflect.Field;

public class HwSpinner extends Spinner {
    private static final String TAG = "HwSpinner";
    private static Field privatePopUpfield;
    private int preferTopPosition = -1;

    static {
        try {
            privatePopUpfield = Spinner.class.getDeclaredField("mPopup");
            privatePopUpfield.setAccessible(true);
        } catch (NoSuchFieldException e) {
            Log.e(TAG, "not found filed : mPopup in Spinner");
        }
    }

    public HwSpinner(Context context) {
        super(context);
    }

    public boolean performClick() {
        boolean handled = super.performClick();
        if (handled) {
            try {
                Object privatePopupWindow = privatePopUpfield.get(this);
                if (privatePopupWindow != null && (privatePopupWindow instanceof ListPopupWindow)) {
                    ListPopupWindow listPopupWindow = (ListPopupWindow) privatePopupWindow;
                    if (listPopupWindow.isShowing() && this.preferTopPosition >= 0) {
                        listPopupWindow.setSelection(this.preferTopPosition);
                    }
                }
            } catch (IllegalAccessException e) {
                Log.e(TAG, "get IllegalAccessException | IllegalArgumentException when excute filed : mPopup");
            }
        }
        return handled;
    }

    public void setPreferTopPosition(int position) {
        this.preferTopPosition = position;
    }
}
