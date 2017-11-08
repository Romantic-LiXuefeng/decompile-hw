package android.content.pm;

import android.net.wifi.WifiEnterpriseConfig;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.util.Printer;

public class ActivityInfo extends ComponentInfo implements Parcelable {
    public static final int CONFIG_DENSITY = 4096;
    public static final int CONFIG_FONT_SCALE = 1073741824;
    public static final int CONFIG_KEYBOARD = 16;
    public static final int CONFIG_KEYBOARD_HIDDEN = 32;
    public static final int CONFIG_LAYOUT_DIRECTION = 8192;
    public static final int CONFIG_LOCALE = 4;
    public static final int CONFIG_MCC = 1;
    public static final int CONFIG_MNC = 2;
    public static int[] CONFIG_NATIVE_BITS = new int[]{2, 1, 4, 8, 16, 32, 64, 128, 2048, 4096, 512, 8192, 256, 16384};
    public static final int CONFIG_NAVIGATION = 64;
    public static final int CONFIG_ORIENTATION = 128;
    public static final int CONFIG_SCREEN_LAYOUT = 256;
    public static final int CONFIG_SCREEN_SIZE = 1024;
    public static final int CONFIG_SMALLEST_SCREEN_SIZE = 2048;
    public static final int CONFIG_TOUCHSCREEN = 8;
    public static final int CONFIG_UI_MODE = 512;
    public static final Creator<ActivityInfo> CREATOR = new Creator<ActivityInfo>() {
        public ActivityInfo createFromParcel(Parcel source) {
            return new ActivityInfo(source);
        }

        public ActivityInfo[] newArray(int size) {
            return new ActivityInfo[size];
        }
    };
    public static final int DOCUMENT_LAUNCH_ALWAYS = 2;
    public static final int DOCUMENT_LAUNCH_INTO_EXISTING = 1;
    public static final int DOCUMENT_LAUNCH_NEVER = 3;
    public static final int DOCUMENT_LAUNCH_NONE = 0;
    public static final int FLAG_ALLOW_EMBEDDED = Integer.MIN_VALUE;
    public static final int FLAG_ALLOW_TASK_REPARENTING = 64;
    public static final int FLAG_ALWAYS_FOCUSABLE = 262144;
    public static final int FLAG_ALWAYS_RETAIN_TASK_STATE = 8;
    public static final int FLAG_AUTO_REMOVE_FROM_RECENTS = 8192;
    public static final int FLAG_CLEAR_TASK_ON_LAUNCH = 4;
    public static final int FLAG_ENABLE_VR_MODE = 32768;
    public static final int FLAG_EXCLUDE_FROM_RECENTS = 32;
    public static final int FLAG_FINISH_ON_CLOSE_SYSTEM_DIALOGS = 256;
    public static final int FLAG_FINISH_ON_TASK_LAUNCH = 2;
    public static final int FLAG_HARDWARE_ACCELERATED = 512;
    public static final int FLAG_IMMERSIVE = 2048;
    public static final int FLAG_MULTIPROCESS = 1;
    public static final int FLAG_NO_HISTORY = 128;
    public static final int FLAG_RELINQUISH_TASK_IDENTITY = 4096;
    public static final int FLAG_RESUME_WHILE_PAUSING = 16384;
    public static final int FLAG_SHOW_FOR_ALL_USERS = 1024;
    public static final int FLAG_SINGLE_USER = 1073741824;
    public static final int FLAG_STATE_NOT_NEEDED = 16;
    public static final int FLAG_SYSTEM_USER_ONLY = 536870912;
    public static final int LAUNCH_MULTIPLE = 0;
    public static final int LAUNCH_SINGLE_INSTANCE = 3;
    public static final int LAUNCH_SINGLE_TASK = 2;
    public static final int LAUNCH_SINGLE_TOP = 1;
    public static final int LOCK_TASK_LAUNCH_MODE_ALWAYS = 2;
    public static final int LOCK_TASK_LAUNCH_MODE_DEFAULT = 0;
    public static final int LOCK_TASK_LAUNCH_MODE_IF_WHITELISTED = 3;
    public static final int LOCK_TASK_LAUNCH_MODE_NEVER = 1;
    public static final int PERSIST_ACROSS_REBOOTS = 2;
    public static final int PERSIST_NEVER = 1;
    public static final int PERSIST_ROOT_ONLY = 0;
    public static final int RESIZE_MODE_CROP_WINDOWS = 1;
    public static final int RESIZE_MODE_FORCE_RESIZEABLE = 4;
    public static final int RESIZE_MODE_RESIZEABLE = 2;
    public static final int RESIZE_MODE_RESIZEABLE_AND_PIPABLE = 3;
    public static final int RESIZE_MODE_UNRESIZEABLE = 0;
    public static final int SCREEN_ORIENTATION_BEHIND = 3;
    public static final int SCREEN_ORIENTATION_FULL_SENSOR = 10;
    public static final int SCREEN_ORIENTATION_FULL_USER = 13;
    public static final int SCREEN_ORIENTATION_LANDSCAPE = 0;
    public static final int SCREEN_ORIENTATION_LOCKED = 14;
    public static final int SCREEN_ORIENTATION_NOSENSOR = 5;
    public static final int SCREEN_ORIENTATION_PORTRAIT = 1;
    public static final int SCREEN_ORIENTATION_REVERSE_LANDSCAPE = 8;
    public static final int SCREEN_ORIENTATION_REVERSE_PORTRAIT = 9;
    public static final int SCREEN_ORIENTATION_SENSOR = 4;
    public static final int SCREEN_ORIENTATION_SENSOR_LANDSCAPE = 6;
    public static final int SCREEN_ORIENTATION_SENSOR_PORTRAIT = 7;
    public static final int SCREEN_ORIENTATION_UNSPECIFIED = -1;
    public static final int SCREEN_ORIENTATION_USER = 2;
    public static final int SCREEN_ORIENTATION_USER_LANDSCAPE = 11;
    public static final int SCREEN_ORIENTATION_USER_PORTRAIT = 12;
    public static final int UIOPTION_SPLIT_ACTION_BAR_WHEN_NARROW = 1;
    public int configChanges;
    public int documentLaunchMode;
    public int flags;
    public int launchMode;
    public int lockTaskLaunchMode;
    public int maxRecents;
    public boolean navigationHide;
    public String parentActivityName;
    public String permission;
    public int persistableMode;
    public String requestedVrComponent;
    public int resizeMode;
    public int screenOrientation;
    public int softInputMode;
    public String targetActivity;
    public String taskAffinity;
    public int theme;
    public int uiOptions;
    public WindowLayout windowLayout;

    public static final class WindowLayout {
        public final int gravity;
        public final int height;
        public final float heightFraction;
        public final int minHeight;
        public final int minWidth;
        public final int width;
        public final float widthFraction;

        public WindowLayout(int width, float widthFraction, int height, float heightFraction, int gravity, int minWidth, int minHeight) {
            this.width = width;
            this.widthFraction = widthFraction;
            this.height = height;
            this.heightFraction = heightFraction;
            this.gravity = gravity;
            this.minWidth = minWidth;
            this.minHeight = minHeight;
        }

        WindowLayout(Parcel source) {
            this.width = source.readInt();
            this.widthFraction = source.readFloat();
            this.height = source.readInt();
            this.heightFraction = source.readFloat();
            this.gravity = source.readInt();
            this.minWidth = source.readInt();
            this.minHeight = source.readInt();
        }
    }

    public static int activityInfoConfigJavaToNative(int input) {
        int output = 0;
        for (int i = 0; i < CONFIG_NATIVE_BITS.length; i++) {
            if (((1 << i) & input) != 0) {
                output |= CONFIG_NATIVE_BITS[i];
            }
        }
        return output;
    }

    public static int activityInfoConfigNativeToJava(int input) {
        int output = 0;
        for (int i = 0; i < CONFIG_NATIVE_BITS.length; i++) {
            if ((CONFIG_NATIVE_BITS[i] & input) != 0) {
                output |= 1 << i;
            }
        }
        return output;
    }

    public int getRealConfigChanged() {
        if (this.applicationInfo.targetSdkVersion < 13) {
            return (this.configChanges | 1024) | 2048;
        }
        return this.configChanges;
    }

    public static final String lockTaskLaunchModeToString(int lockTaskLaunchMode) {
        switch (lockTaskLaunchMode) {
            case 0:
                return "LOCK_TASK_LAUNCH_MODE_DEFAULT";
            case 1:
                return "LOCK_TASK_LAUNCH_MODE_NEVER";
            case 2:
                return "LOCK_TASK_LAUNCH_MODE_ALWAYS";
            case 3:
                return "LOCK_TASK_LAUNCH_MODE_IF_WHITELISTED";
            default:
                return "unknown=" + lockTaskLaunchMode;
        }
    }

    public ActivityInfo() {
        this.resizeMode = 2;
        this.screenOrientation = -1;
        this.uiOptions = 0;
    }

    public ActivityInfo(ActivityInfo orig) {
        super((ComponentInfo) orig);
        this.resizeMode = 2;
        this.screenOrientation = -1;
        this.uiOptions = 0;
        this.theme = orig.theme;
        this.launchMode = orig.launchMode;
        this.documentLaunchMode = orig.documentLaunchMode;
        this.permission = orig.permission;
        this.taskAffinity = orig.taskAffinity;
        this.targetActivity = orig.targetActivity;
        this.flags = orig.flags;
        this.screenOrientation = orig.screenOrientation;
        this.configChanges = orig.configChanges;
        this.softInputMode = orig.softInputMode;
        this.uiOptions = orig.uiOptions;
        this.parentActivityName = orig.parentActivityName;
        this.maxRecents = orig.maxRecents;
        this.lockTaskLaunchMode = orig.lockTaskLaunchMode;
        this.windowLayout = orig.windowLayout;
        this.resizeMode = orig.resizeMode;
        this.navigationHide = orig.navigationHide;
        this.requestedVrComponent = orig.requestedVrComponent;
    }

    public final int getThemeResource() {
        return this.theme != 0 ? this.theme : this.applicationInfo.theme;
    }

    private String persistableModeToString() {
        switch (this.persistableMode) {
            case 0:
                return "PERSIST_ROOT_ONLY";
            case 1:
                return "PERSIST_NEVER";
            case 2:
                return "PERSIST_ACROSS_REBOOTS";
            default:
                return "UNKNOWN=" + this.persistableMode;
        }
    }

    boolean isFixedOrientation() {
        if (this.screenOrientation == 0 || this.screenOrientation == 1 || this.screenOrientation == 6 || this.screenOrientation == 7 || this.screenOrientation == 8 || this.screenOrientation == 9 || this.screenOrientation == 11 || this.screenOrientation == 12 || this.screenOrientation == 14) {
            return true;
        }
        return false;
    }

    public static boolean isResizeableMode(int mode) {
        if (mode == 2 || mode == 3 || mode == 4) {
            return true;
        }
        return false;
    }

    public static String resizeModeToString(int mode) {
        switch (mode) {
            case 0:
                return "RESIZE_MODE_UNRESIZEABLE";
            case 1:
                return "RESIZE_MODE_CROP_WINDOWS";
            case 2:
                return "RESIZE_MODE_RESIZEABLE";
            case 3:
                return "RESIZE_MODE_RESIZEABLE_AND_PIPABLE";
            case 4:
                return "RESIZE_MODE_FORCE_RESIZEABLE";
            default:
                return "unknown=" + mode;
        }
    }

    public void dump(Printer pw, String prefix) {
        dump(pw, prefix, 3);
    }

    public void dump(Printer pw, String prefix, int flags) {
        super.dumpFront(pw, prefix);
        if (this.permission != null) {
            pw.println(prefix + "permission=" + this.permission);
        }
        if ((flags & 1) != 0) {
            pw.println(prefix + "taskAffinity=" + this.taskAffinity + " targetActivity=" + this.targetActivity + " persistableMode=" + persistableModeToString());
        }
        if (this.launchMode == 0 && flags == 0) {
            if (this.theme != 0) {
            }
            if (this.screenOrientation == -1 && this.configChanges == 0) {
                if (this.softInputMode != 0) {
                }
                if (this.uiOptions != 0) {
                    pw.println(prefix + " uiOptions=0x" + Integer.toHexString(this.uiOptions));
                }
                if ((flags & 1) != 0) {
                    pw.println(prefix + "lockTaskLaunchMode=" + lockTaskLaunchModeToString(this.lockTaskLaunchMode));
                }
                if (this.windowLayout != null) {
                    pw.println(prefix + "windowLayout=" + this.windowLayout.width + "|" + this.windowLayout.widthFraction + ", " + this.windowLayout.height + "|" + this.windowLayout.heightFraction + ", " + this.windowLayout.gravity);
                }
                pw.println(prefix + "resizeMode=" + resizeModeToString(this.resizeMode));
                if (this.requestedVrComponent != null) {
                    pw.println(prefix + "requestedVrComponent=" + this.requestedVrComponent);
                }
                super.dumpBack(pw, prefix, flags);
            }
            pw.println(prefix + "screenOrientation=" + this.screenOrientation + " configChanges=0x" + Integer.toHexString(this.configChanges) + " softInputMode=0x" + Integer.toHexString(this.softInputMode));
            if (this.uiOptions != 0) {
                pw.println(prefix + " uiOptions=0x" + Integer.toHexString(this.uiOptions));
            }
            if ((flags & 1) != 0) {
                pw.println(prefix + "lockTaskLaunchMode=" + lockTaskLaunchModeToString(this.lockTaskLaunchMode));
            }
            if (this.windowLayout != null) {
                pw.println(prefix + "windowLayout=" + this.windowLayout.width + "|" + this.windowLayout.widthFraction + ", " + this.windowLayout.height + "|" + this.windowLayout.heightFraction + ", " + this.windowLayout.gravity);
            }
            pw.println(prefix + "resizeMode=" + resizeModeToString(this.resizeMode));
            if (this.requestedVrComponent != null) {
                pw.println(prefix + "requestedVrComponent=" + this.requestedVrComponent);
            }
            super.dumpBack(pw, prefix, flags);
        }
        pw.println(prefix + "launchMode=" + this.launchMode + " flags=0x" + Integer.toHexString(flags) + " theme=0x" + Integer.toHexString(this.theme));
        if (this.softInputMode != 0) {
            pw.println(prefix + "screenOrientation=" + this.screenOrientation + " configChanges=0x" + Integer.toHexString(this.configChanges) + " softInputMode=0x" + Integer.toHexString(this.softInputMode));
        }
        if (this.uiOptions != 0) {
            pw.println(prefix + " uiOptions=0x" + Integer.toHexString(this.uiOptions));
        }
        if ((flags & 1) != 0) {
            pw.println(prefix + "lockTaskLaunchMode=" + lockTaskLaunchModeToString(this.lockTaskLaunchMode));
        }
        if (this.windowLayout != null) {
            pw.println(prefix + "windowLayout=" + this.windowLayout.width + "|" + this.windowLayout.widthFraction + ", " + this.windowLayout.height + "|" + this.windowLayout.heightFraction + ", " + this.windowLayout.gravity);
        }
        pw.println(prefix + "resizeMode=" + resizeModeToString(this.resizeMode));
        if (this.requestedVrComponent != null) {
            pw.println(prefix + "requestedVrComponent=" + this.requestedVrComponent);
        }
        super.dumpBack(pw, prefix, flags);
    }

    public String toString() {
        return "ActivityInfo{" + Integer.toHexString(System.identityHashCode(this)) + WifiEnterpriseConfig.CA_CERT_ALIAS_DELIMITER + this.name + "}";
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int parcelableFlags) {
        int i = 1;
        super.writeToParcel(dest, parcelableFlags);
        dest.writeInt(this.theme);
        dest.writeInt(this.launchMode);
        dest.writeInt(this.documentLaunchMode);
        dest.writeString(this.permission);
        dest.writeString(this.taskAffinity);
        dest.writeString(this.targetActivity);
        dest.writeInt(this.flags);
        dest.writeInt(this.screenOrientation);
        dest.writeInt(this.configChanges);
        dest.writeInt(this.softInputMode);
        dest.writeInt(this.uiOptions);
        dest.writeString(this.parentActivityName);
        dest.writeInt(this.persistableMode);
        dest.writeInt(this.maxRecents);
        dest.writeInt(this.lockTaskLaunchMode);
        if (this.windowLayout != null) {
            dest.writeInt(1);
            dest.writeInt(this.windowLayout.width);
            dest.writeFloat(this.windowLayout.widthFraction);
            dest.writeInt(this.windowLayout.height);
            dest.writeFloat(this.windowLayout.heightFraction);
            dest.writeInt(this.windowLayout.gravity);
            dest.writeInt(this.windowLayout.minWidth);
            dest.writeInt(this.windowLayout.minHeight);
        } else {
            dest.writeInt(0);
        }
        dest.writeInt(this.resizeMode);
        if (!this.navigationHide) {
            i = 0;
        }
        dest.writeInt(i);
        dest.writeString(this.requestedVrComponent);
    }

    private ActivityInfo(Parcel source) {
        boolean z = true;
        super(source);
        this.resizeMode = 2;
        this.screenOrientation = -1;
        this.uiOptions = 0;
        this.theme = source.readInt();
        this.launchMode = source.readInt();
        this.documentLaunchMode = source.readInt();
        this.permission = source.readString();
        this.taskAffinity = source.readString();
        this.targetActivity = source.readString();
        this.flags = source.readInt();
        this.screenOrientation = source.readInt();
        this.configChanges = source.readInt();
        this.softInputMode = source.readInt();
        this.uiOptions = source.readInt();
        this.parentActivityName = source.readString();
        this.persistableMode = source.readInt();
        this.maxRecents = source.readInt();
        this.lockTaskLaunchMode = source.readInt();
        if (source.readInt() == 1) {
            this.windowLayout = new WindowLayout(source);
        }
        this.resizeMode = source.readInt();
        if (1 != source.readInt()) {
            z = false;
        }
        this.navigationHide = z;
        this.requestedVrComponent = source.readString();
    }
}
