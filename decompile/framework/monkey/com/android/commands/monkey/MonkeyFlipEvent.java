package com.android.commands.monkey;

import android.app.IActivityManager;
import android.view.IWindowManager;
import java.io.FileOutputStream;
import java.io.IOException;

public class MonkeyFlipEvent extends MonkeyEvent {
    private static final byte[] FLIP_0 = new byte[]{Byte.MAX_VALUE, (byte) 6, (byte) 0, (byte) 0, (byte) -32, (byte) 57, (byte) 1, (byte) 0, (byte) 5, (byte) 0, (byte) 0, (byte) 0, (byte) 1, (byte) 0, (byte) 0, (byte) 0};
    private static final byte[] FLIP_1 = new byte[]{(byte) -123, (byte) 6, (byte) 0, (byte) 0, (byte) -97, (byte) -91, (byte) 12, (byte) 0, (byte) 5, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0};
    private final boolean mKeyboardOpen;

    public MonkeyFlipEvent(boolean keyboardOpen) {
        super(5);
        this.mKeyboardOpen = keyboardOpen;
    }

    public int injectEvent(IWindowManager iwm, IActivityManager iam, int verbose) {
        IOException e;
        if (verbose > 0) {
            System.out.println(":Sending Flip keyboardOpen=" + this.mKeyboardOpen);
        }
        FileOutputStream f = null;
        try {
            FileOutputStream f2 = new FileOutputStream("/dev/input/event0");
            try {
                f2.write(this.mKeyboardOpen ? FLIP_0 : FLIP_1);
                f2.close();
                return 1;
            } catch (IOException e2) {
                e = e2;
                f = f2;
                System.out.println("Got IOException performing flip" + e);
                if (f != null) {
                    try {
                        f.close();
                    } catch (IOException e3) {
                        System.out.println("FileOutputStream close error");
                    }
                }
                return 0;
            }
        } catch (IOException e4) {
            e = e4;
            System.out.println("Got IOException performing flip" + e);
            if (f != null) {
                f.close();
            }
            return 0;
        }
    }
}
