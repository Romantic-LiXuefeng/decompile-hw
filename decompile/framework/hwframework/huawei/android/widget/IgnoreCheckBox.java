package huawei.android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.CheckBox;

public class IgnoreCheckBox extends CheckBox {
    public IgnoreCheckBox(Context context) {
        this(context, null);
    }

    public IgnoreCheckBox(Context context, AttributeSet attrs) {
        this(context, attrs, 34799625);
    }

    public IgnoreCheckBox(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public IgnoreCheckBox(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setButtonDrawableHeight(context.getResources().getDimensionPixelSize(34472092));
        setButtonDrawableWidth(context.getResources().getDimensionPixelSize(34472093));
    }
}
