package android.view;

import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;
import huawei.android.view.inputmethod.HwSecImmHelper;
import huawei.com.android.internal.widget.HwFragmentMenuItemView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class HwPointEventFilter implements IHwPointEventFilter {
    private static final String AFT_KEY_BT_EDGE_X = "bt_edge_x";
    private static final String AFT_KEY_BT_EDGE_Y = "bt_edge_y";
    private static final String AFT_KEY_CLICK_TIME_BT = "click_time_bt";
    private static final String AFT_KEY_CLICK_TIME_LIMIT = "click_time_limit";
    private static final String AFT_KEY_EDGE_POSITION = "edge_position";
    private static final String AFT_KEY_EDGE_POSITION_SECOND_LINE = "edge_postion_secondline";
    private static final String AFT_KEY_EDGE_XY_CONFIRM_T = "edge_xy_confirm_t";
    private static final String AFT_KEY_EDGE_XY_DOWN_BT = "edge_xy_down_bt";
    private static final String AFT_KEY_EDGE_Y_AVG_BT = "edge_y_avg_bt";
    private static final String AFT_KEY_EDGE_Y_CONFIRM_T = "edge_y_confirm_t";
    private static final String AFT_KEY_EDGE_Y_DUBIOUS_T = "edge_y_dubious_t";
    private static final String AFT_KEY_FEATURE_ALL = "feature_all";
    private static final String AFT_KEY_FEATURE_SG = "feature_sg";
    private static final String AFT_KEY_LCD_HEIGHT = "lcd_height";
    private static final String AFT_KEY_LCD_WIDTH = "lcd_width";
    private static final String AFT_KEY_MIN_CLICK_TIME = "min_click_time";
    private static final String AFT_KEY_MOVE_LIMIT_X = "move_limit_x";
    private static final String AFT_KEY_MOVE_LIMIT_X_BT = "move_limit_x_bt";
    private static final String AFT_KEY_MOVE_LIMIT_X_T = "move_limit_x_t";
    private static final String AFT_KEY_MOVE_LIMIT_Y = "move_limit_y";
    private static final String AFT_KEY_MOVE_LIMIT_Y_BT = "move_limit_y_bt";
    private static final String AFT_KEY_MOVE_LIMIT_Y_T = "move_limit_y_t";
    private static final String AFT_KEY_SG_MIN_VALUE = "sg_min_value";
    private static final int BOTTOM_THENAR_AVG_EDGE = 3;
    private static final long BOTTOM_THENAR_CHECK_TIME_LIMIT = 200;
    private static final float BOTTOM_THENAR_DISTANCE = 36.0f;
    private static final float BOTTOM_THENAR_MOVE_LIMIT_X = 60.0f;
    private static final float BOTTOM_THENAR_MOVE_LIMIT_Y = 60.0f;
    private static final long CLICK_TIME_LIMIT = 180;
    private static String CONFIG_PATH = "/sys/touchscreen/anti_false_touch_param";
    private static final float DOUBT_THENAR_DISTANCE = 8.0f;
    private static final float DOUBT_THENAR_LENGHT_X = 80.0f;
    private static final float DOUBT_THENAR_LENGHT_Y = 700.0f;
    private static final float EDGE_LINE_SPREAD_DISTANCE = 16.0f;
    private static final boolean ENABLE_SG = false;
    private static final float FIRST_EDGE = 18.0f;
    private static final float INPUT_Y_LIMIT_RATIO = 0.75f;
    private static final String LAUNCHER_PKG_NAME = "com.huawei.android.launcher";
    private static final long MIN_CLICK_TIME = 0;
    private static final float MOVE_LIMIT_X = 10.0f;
    private static final float MOVE_LIMIT_Y = 10.0f;
    private static final float MOVE_SPREAD_X = 18.0f;
    private static final float MOVE_SPREAD_Y = 18.0f;
    private static final float ORIENTATION_0 = 0.0f;
    private static final int POINT_COUNT_MAX = 32;
    private static final float SECOND_EDGE = 24.0f;
    private static final int SG_MIN_VALUE = 64;
    private static final float SPREAD_EDGE = 4.0f;
    private static final String TAG = "HwPointEventFilter";
    private static final float THENAR_DISTANCE = 24.0f;
    private static ArrayList<String> mBasketOnlySecondEdge = new ArrayList<String>() {
        {
            add("com.ss.android.article.news");
            add("com.tencent.news");
        }
    };
    private static ArrayList<String> mInputYZoneLimitWhiteList = new ArrayList<String>() {
        {
            add("com.baidu.input_huawei");
            add("com.nuance.swype.emui");
            add("com.android.inputmethod.latin");
            add(HwSecImmHelper.SECURE_IME_PACKAGENAME);
            add("com.google.android.inputmethod.lati");
            add("com.baidu.input_huawei");
            add("com.baidu.input");
            add("com.sohu.inputmethod.sogou");
            add("com.iflytek.inputmethod");
            add("com.tencent.qqpinyin");
            add("com.google.android.inputmethod.pinyin");
            add("com.jb.emoji.gokeyboard");
            add("com.bingime.ime");
            add("com.cootek.smartinputv5");
            add("com.komoxo.octopusime");
            add("com.xinshuru.inputmethod");
            add("com.qisiemoji.inputmethod");
            add("com.touchtype.swiftkey");
            add("com.cootek.smartinputv5");
            add("com.dotc.ime.latin.flash");
            add("com.sonyericsson.textinput.uxp");
            add("com.aitype.android");
            add("com.emoji.input.gif.theme.keyboard");
            add("com.kitkatandroid.keyboard");
            add("com.mykeyboard.myphotokeyboard");
            add("com.asus.ime");
            add("com.syntellia.fleksy.keyboard");
            add("com.nuance.swype.dtc");
        }
    };
    private static ArrayList<String> mLandscapeWhiteList = new ArrayList<String>() {
        {
            add("com.tencent.qqlive");
            add("com.youku.phone");
            add("com.qiyi.video");
            add("com.storm.smart");
            add("com.huawei.hwvplayer.youku");
        }
    };
    private boolean mAFTTeature;
    private MotionEvent mAdditionalEvent;
    private float mBottomThenarAvgEdge;
    private long mBottomThenarCheckTimeLimit;
    private float mBottomThenarDistance;
    private float mBottomThenarMoveXLimit;
    private float mBottomThenarMoveYLimit;
    private long mClickTimeLimit;
    private boolean mDisableEdgeCheck;
    private int mDoubtAndNotFilterIdBits;
    private float mDoubtThenarDistance;
    private float mDoubtThenarLengthX;
    private float mDoubtThenarLengthY;
    private float mEdgeLengthSpead;
    private boolean mEnableLog;
    private boolean mEnableSG;
    EventPoint[] mEventPoints;
    private int mFilterIdBits;
    private float mFirstEdge;
    private float mFirstEdgeSpread;
    private long mMinClickTime;
    private float mMoveSpreadXLimit;
    private float mMoveSpreadYLimit;
    private float mMoveXLimit;
    private float mMoveYLimit;
    private boolean mOnlySupportSecondEdge;
    private String mPackageName;
    private float mRealFirstEdge;
    private float mRealFirstEdgeSpread;
    private float mRealMoveSpreadXLimit;
    private float mRealMoveSpreadYLimit;
    private float mRealMoveXLimit;
    private float mRealMoveYLimit;
    private float mSGMinValue;
    private float mScreenX;
    private float mScreenY;
    private float mSecondEdge;
    private float mThenarDistance;

    private class EventPoint {
        private static final int BOTTOM_THENAR_CHECK_COUNT_MAX = 10;
        private static final int BOTTOM_THENAR_CHECK_COUNT_MAX_LIMIT = 2000;
        private int bottomThenarCheckCount = 0;
        private float bottomThenarTotalEdgeLength = HwPointEventFilter.ORIENTATION_0;
        private float downDistanceX;
        private float downDistanceY;
        private long downTime;
        private boolean isBottomThenar = false;
        private float lastDistanceX;
        private float lastDistanceY;
        private float lastPointX;
        private float lastPointY;
        private long lastTime;
        private float pointDownX;
        private float pointDownY;
        private int pointId;
        private boolean timeoutCheck = false;

        EventPoint(int id, float x, float y, float distanceX, float distanceY, long time) {
            this.pointId = id;
            this.lastPointX = x;
            this.pointDownX = x;
            this.lastPointY = y;
            this.pointDownY = y;
            this.lastDistanceX = distanceX;
            this.downDistanceX = distanceX;
            this.lastDistanceY = distanceY;
            this.downDistanceY = distanceY;
            this.lastTime = time;
            this.downTime = time;
        }

        void update(float x, float y, float distanceX, float distanceY, long time) {
            this.lastPointX = x;
            this.lastPointY = y;
            this.lastDistanceX = distanceX;
            this.lastDistanceY = distanceY;
            this.lastTime = time;
        }

        void setBottomThenar(boolean flag) {
            this.isBottomThenar = flag;
        }

        void setTimeoutCheck(boolean flag) {
            this.timeoutCheck = flag;
        }

        boolean isBottomThenar() {
            return this.isBottomThenar;
        }

        boolean isCurrentThenar() {
            return HwPointEventFilter.this.isThenar(this.lastDistanceX, this.lastDistanceY);
        }

        boolean isDownThenar() {
            return HwPointEventFilter.this.isThenar(this.downDistanceX, this.downDistanceY) && this.lastDistanceX + this.lastDistanceY > HwPointEventFilter.ORIENTATION_0;
        }

        boolean shouldForword(float x, float y, float distanceX, float distanceY) {
            boolean result = true;
            if (distanceX + distanceY <= HwPointEventFilter.ORIENTATION_0) {
                return true;
            }
            if (HwPointEventFilter.this.isThenar(distanceX, distanceY)) {
                return false;
            }
            float moveXLimit = distanceY >= HwPointEventFilter.this.mEdgeLengthSpead ? HwPointEventFilter.this.mRealMoveSpreadXLimit : HwPointEventFilter.this.mRealMoveXLimit;
            float moveYLimit = distanceY >= HwPointEventFilter.this.mEdgeLengthSpead ? HwPointEventFilter.this.mRealMoveSpreadYLimit : HwPointEventFilter.this.mRealMoveYLimit;
            HwPointEventFilter.this.printfLog("moveXLimit=" + moveXLimit + ",moveYLimit" + moveYLimit + " in shouldForword");
            if (Math.abs(this.pointDownX - x) <= moveXLimit && Math.abs(this.pointDownY - y) <= moveYLimit) {
                result = false;
            }
            return result;
        }

        boolean inShortTime(long time) {
            long clickTime = Math.abs(this.downTime - time);
            if (clickTime < HwPointEventFilter.this.mMinClickTime || clickTime >= HwPointEventFilter.this.mClickTimeLimit) {
                return false;
            }
            return true;
        }

        boolean inMoveLimit(float x, float y, float distanceX, float distanceY) {
            float moveXLimit = distanceY >= HwPointEventFilter.this.mEdgeLengthSpead ? HwPointEventFilter.this.mRealMoveSpreadXLimit : HwPointEventFilter.this.mRealMoveXLimit;
            float moveYLimit = distanceY >= HwPointEventFilter.this.mEdgeLengthSpead ? HwPointEventFilter.this.mRealMoveSpreadYLimit : HwPointEventFilter.this.mRealMoveYLimit;
            HwPointEventFilter.this.printfLog("moveXLimit=" + moveXLimit + ",moveYLimit" + moveYLimit + " in inMoveLimit");
            if (Math.abs(this.pointDownX - x) > moveXLimit || Math.abs(this.pointDownY - y) > moveYLimit) {
                return false;
            }
            return true;
        }

        boolean inBottomThenarLimit(float x, float y) {
            return Math.abs(this.pointDownX - x) <= HwPointEventFilter.this.mBottomThenarMoveXLimit && Math.abs(this.pointDownY - y) <= HwPointEventFilter.this.mBottomThenarMoveYLimit;
        }

        boolean doubtBottomThenar(float x, float y, float distanceX, float distanceY, long time, float curSG, float orientation) {
            if (Math.abs(this.downTime - time) > HwPointEventFilter.this.mBottomThenarCheckTimeLimit && !this.timeoutCheck) {
                this.timeoutCheck = true;
                if (HwPointEventFilter.this.inFirstEdge(this.pointDownX, this.pointDownY, this.downDistanceY, curSG, orientation) && inMoveLimit(x, y, distanceX, distanceY)) {
                    return true;
                }
                Log.d(HwPointEventFilter.TAG, "doubtBottomThenar out of time");
                return false;
            } else if (!inBottomThenarLimit(x, y)) {
                Log.d(HwPointEventFilter.TAG, "out of bottom thenar x:" + x + " y:" + y + " pointDownX:" + this.pointDownX + " pointDownY:" + this.pointDownY + "mBottomThenarMoveXLimit:" + HwPointEventFilter.this.mBottomThenarMoveXLimit + " mBottomThenarMoveYLimit:" + HwPointEventFilter.this.mBottomThenarMoveYLimit);
                this.isBottomThenar = false;
                return false;
            } else if (this.bottomThenarCheckCount >= 2000) {
                Log.d(HwPointEventFilter.TAG, "doubtBottomThenar check time more than 2000 " + this.pointId + " lastPointX:" + this.lastPointX + " lastPointY:" + this.lastPointY + " lastTime:" + this.lastTime);
                return true;
            } else {
                this.bottomThenarCheckCount++;
                this.bottomThenarTotalEdgeLength += distanceY;
                float avgEdgeLength = this.bottomThenarTotalEdgeLength / ((float) this.bottomThenarCheckCount);
                if (this.bottomThenarCheckCount < 10 || avgEdgeLength >= HwPointEventFilter.this.mBottomThenarAvgEdge) {
                    return true;
                }
                Log.d(HwPointEventFilter.TAG, "out if count and avg is too small bottomThenarCheckCount:" + this.bottomThenarCheckCount + " avgEdgeLength:" + avgEdgeLength + " mBottomThenarAvgEdge:" + HwPointEventFilter.this.mBottomThenarAvgEdge);
                this.isBottomThenar = false;
                return false;
            }
        }
    }

    private static class TouchInfo {
        public float SG = HwPointEventFilter.ORIENTATION_0;
        public float distanceX = HwPointEventFilter.ORIENTATION_0;
        public float distanceY = HwPointEventFilter.ORIENTATION_0;

        public TouchInfo(float sg, float distanceX, float distanceY) {
            this.SG = sg;
            this.distanceX = distanceX;
            this.distanceY = distanceY;
        }
    }

    private int clearFilterId(int r1) {
        /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.DecodeException: Load method exception in method: android.view.HwPointEventFilter.clearFilterId(int):int
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:116)
	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:249)
	at jadx.core.ProcessClass.process(ProcessClass.java:34)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
Caused by: jadx.core.utils.exceptions.DecodeException: Unknown instruction: not-int
	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:569)
	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:56)
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:102)
	... 5 more
*/
        /*
        // Can't load method instructions.
        */
        throw new UnsupportedOperationException("Method not decompiled: android.view.HwPointEventFilter.clearFilterId(int):int");
    }

    private static android.view.MotionEvent filterMotionEvent(android.view.MotionEvent r1, int r2) {
        /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.DecodeException: Load method exception in method: android.view.HwPointEventFilter.filterMotionEvent(android.view.MotionEvent, int):android.view.MotionEvent
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:116)
	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:249)
	at jadx.core.ProcessClass.process(ProcessClass.java:34)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
Caused by: jadx.core.utils.exceptions.DecodeException: Unknown instruction: not-int
	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:569)
	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:56)
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:102)
	... 5 more
*/
        /*
        // Can't load method instructions.
        */
        throw new UnsupportedOperationException("Method not decompiled: android.view.HwPointEventFilter.filterMotionEvent(android.view.MotionEvent, int):android.view.MotionEvent");
    }

    private int handleMoveForFilterIds(android.view.MotionEvent r1) {
        /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.DecodeException: Load method exception in method: android.view.HwPointEventFilter.handleMoveForFilterIds(android.view.MotionEvent):int
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:116)
	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:249)
	at jadx.core.ProcessClass.process(ProcessClass.java:34)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
Caused by: jadx.core.utils.exceptions.DecodeException: Unknown instruction: not-int
	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:569)
	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:56)
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:102)
	... 5 more
*/
        /*
        // Can't load method instructions.
        */
        throw new UnsupportedOperationException("Method not decompiled: android.view.HwPointEventFilter.handleMoveForFilterIds(android.view.MotionEvent):int");
    }

    private void printfLog(String log) {
        if (this.mEnableLog) {
            Log.d(TAG, log);
        }
    }

    public HwPointEventFilter(String pkgName) {
        this.mAFTTeature = false;
        this.mPackageName = "";
        this.mEnableLog = false;
        this.mOnlySupportSecondEdge = false;
        this.mDisableEdgeCheck = false;
        this.mFilterIdBits = 0;
        this.mDoubtAndNotFilterIdBits = 0;
        this.mEventPoints = new EventPoint[32];
        this.mAdditionalEvent = null;
        this.mPackageName = pkgName;
        if (mInputYZoneLimitWhiteList == null || !mInputYZoneLimitWhiteList.contains(this.mPackageName)) {
            this.mDisableEdgeCheck = false;
        } else {
            this.mDisableEdgeCheck = true;
        }
        initConfig();
    }

    public HwPointEventFilter() {
        this.mAFTTeature = false;
        this.mPackageName = "";
        this.mEnableLog = false;
        this.mOnlySupportSecondEdge = false;
        this.mDisableEdgeCheck = false;
        this.mFilterIdBits = 0;
        this.mDoubtAndNotFilterIdBits = 0;
        this.mEventPoints = new EventPoint[32];
        this.mAdditionalEvent = null;
        initConfig();
    }

    private String getConfigPara() {
        BufferedReader reader;
        Throwable th;
        String prop = null;
        BufferedReader bufferedReader = null;
        FileInputStream fileInputStream = null;
        try {
            FileInputStream fis = new FileInputStream(new File(CONFIG_PATH));
            try {
                reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            } catch (IOException e) {
                fileInputStream = fis;
                try {
                    Log.i(TAG, "do not support AFT because of no config");
                    if (bufferedReader != null) {
                        try {
                            bufferedReader.close();
                        } catch (IOException e2) {
                            Log.e(TAG, "IOException e=" + e2);
                        }
                    }
                    if (fileInputStream != null) {
                        try {
                            fileInputStream.close();
                        } catch (IOException e22) {
                            Log.e(TAG, "IOException e2=" + e22);
                        }
                    }
                    return prop;
                } catch (Throwable th2) {
                    th = th2;
                    if (bufferedReader != null) {
                        try {
                            bufferedReader.close();
                        } catch (IOException e23) {
                            Log.e(TAG, "IOException e=" + e23);
                        }
                    }
                    if (fileInputStream != null) {
                        try {
                            fileInputStream.close();
                        } catch (IOException e222) {
                            Log.e(TAG, "IOException e2=" + e222);
                        }
                    }
                    throw th;
                }
            } catch (Throwable th3) {
                th = th3;
                fileInputStream = fis;
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
                throw th;
            }
            try {
                prop = reader.readLine();
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e232) {
                        Log.e(TAG, "IOException e=" + e232);
                    }
                }
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException e2222) {
                        Log.e(TAG, "IOException e2=" + e2222);
                    }
                }
                bufferedReader = reader;
            } catch (IOException e3) {
                fileInputStream = fis;
                bufferedReader = reader;
                Log.i(TAG, "do not support AFT because of no config");
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
                return prop;
            } catch (Throwable th4) {
                th = th4;
                fileInputStream = fis;
                bufferedReader = reader;
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
                throw th;
            }
        } catch (IOException e4) {
            Log.i(TAG, "do not support AFT because of no config");
            if (bufferedReader != null) {
                bufferedReader.close();
            }
            if (fileInputStream != null) {
                fileInputStream.close();
            }
            return prop;
        }
        return prop;
    }

    private void initParaFromConfigFile() {
        String parameter = getConfigPara();
        if (parameter != null) {
            for (String item : parameter.split(",")) {
                if (item != null) {
                    String[] keyAndValue = item.split("=");
                    if (keyAndValue.length == 2) {
                        String key = keyAndValue[0];
                        String value = keyAndValue[1];
                        if (AFT_KEY_FEATURE_ALL.equals(key)) {
                            if (1 == Integer.parseInt(value)) {
                                this.mAFTTeature = true;
                                Log.i(TAG, "support AFT");
                            }
                        } else if (AFT_KEY_LCD_WIDTH.equals(key)) {
                            this.mScreenX = Float.parseFloat(value);
                        } else if (AFT_KEY_LCD_HEIGHT.equals(key)) {
                            this.mScreenY = Float.parseFloat(value);
                        } else if (AFT_KEY_CLICK_TIME_LIMIT.equals(key)) {
                            this.mClickTimeLimit = Long.parseLong(value);
                        } else if (AFT_KEY_CLICK_TIME_BT.equals(key)) {
                            this.mBottomThenarCheckTimeLimit = Long.parseLong(value);
                        } else if (AFT_KEY_EDGE_POSITION.equals(key)) {
                            this.mFirstEdge = Float.parseFloat(value);
                        } else if (AFT_KEY_EDGE_POSITION_SECOND_LINE.equals(key)) {
                            this.mFirstEdgeSpread = Float.parseFloat(value);
                        } else if (AFT_KEY_BT_EDGE_X.equals(key)) {
                            this.mDoubtThenarLengthX = Float.parseFloat(value);
                        } else if (AFT_KEY_BT_EDGE_Y.equals(key)) {
                            this.mDoubtThenarLengthY = Float.parseFloat(value);
                        } else if (AFT_KEY_MOVE_LIMIT_X.equals(key)) {
                            this.mMoveXLimit = Float.parseFloat(value);
                        } else if (AFT_KEY_MOVE_LIMIT_Y.equals(key)) {
                            this.mMoveYLimit = Float.parseFloat(value);
                        } else if (AFT_KEY_MOVE_LIMIT_X_T.equals(key)) {
                            this.mMoveSpreadXLimit = Float.parseFloat(value);
                        } else if (AFT_KEY_MOVE_LIMIT_Y_T.equals(key)) {
                            this.mMoveSpreadYLimit = Float.parseFloat(value);
                        } else if (AFT_KEY_MOVE_LIMIT_X_BT.equals(key)) {
                            this.mBottomThenarMoveXLimit = Float.parseFloat(value);
                        } else if (AFT_KEY_MOVE_LIMIT_Y_BT.equals(key)) {
                            this.mBottomThenarMoveYLimit = Float.parseFloat(value);
                        } else if (AFT_KEY_EDGE_Y_CONFIRM_T.equals(key)) {
                            this.mThenarDistance = Float.parseFloat(value);
                        } else if (AFT_KEY_EDGE_Y_DUBIOUS_T.equals(key)) {
                            this.mEdgeLengthSpead = Float.parseFloat(value);
                        } else if (AFT_KEY_EDGE_Y_AVG_BT.equals(key)) {
                            this.mBottomThenarAvgEdge = Float.parseFloat(value);
                        } else if (AFT_KEY_EDGE_XY_DOWN_BT.equals(key)) {
                            this.mDoubtThenarDistance = Float.parseFloat(value);
                        } else if (AFT_KEY_EDGE_XY_CONFIRM_T.equals(key)) {
                            this.mBottomThenarDistance = Float.parseFloat(value);
                        } else if (AFT_KEY_MIN_CLICK_TIME.equals(key)) {
                            this.mMinClickTime = Long.parseLong(value);
                        } else if (AFT_KEY_FEATURE_SG.equals(key)) {
                            if (Float.parseFloat(value) == HwFragmentMenuItemView.ALPHA_NORMAL) {
                                this.mEnableSG = true;
                            } else {
                                this.mEnableSG = false;
                            }
                        } else if (AFT_KEY_SG_MIN_VALUE.equals(key)) {
                            this.mSGMinValue = Float.parseFloat(value);
                        }
                    }
                }
            }
        }
    }

    private void initConfig() {
        this.mFirstEdge = 18.0f;
        this.mFirstEdgeSpread = 22.0f;
        this.mSecondEdge = 24.0f;
        this.mThenarDistance = 24.0f;
        this.mBottomThenarDistance = BOTTOM_THENAR_DISTANCE;
        this.mDoubtThenarDistance = DOUBT_THENAR_DISTANCE;
        this.mDoubtThenarLengthX = DOUBT_THENAR_LENGHT_X;
        this.mDoubtThenarLengthY = DOUBT_THENAR_LENGHT_Y;
        this.mClickTimeLimit = CLICK_TIME_LIMIT;
        this.mMoveXLimit = 10.0f;
        this.mMoveYLimit = 10.0f;
        this.mMoveSpreadXLimit = 18.0f;
        this.mMoveSpreadYLimit = 18.0f;
        this.mBottomThenarMoveXLimit = 60.0f;
        this.mBottomThenarMoveYLimit = 60.0f;
        this.mBottomThenarCheckTimeLimit = BOTTOM_THENAR_CHECK_TIME_LIMIT;
        this.mBottomThenarAvgEdge = 3.0f;
        this.mEdgeLengthSpead = EDGE_LINE_SPREAD_DISTANCE;
        this.mEnableSG = false;
        this.mSGMinValue = 64.0f;
        this.mClickTimeLimit = MIN_CLICK_TIME;
        try {
            initParaFromConfigFile();
        } catch (Exception e) {
            Log.i(TAG, "do not support AFT");
            this.mAFTTeature = false;
        }
        if (mBasketOnlySecondEdge == null || !mBasketOnlySecondEdge.contains(this.mPackageName)) {
            this.mOnlySupportSecondEdge = false;
            return;
        }
        Log.d(TAG, "Only support second edge!");
        this.mOnlySupportSecondEdge = true;
    }

    private boolean isThenar(float distanceX, float distanceY) {
        boolean z = true;
        if (this.mDisableEdgeCheck) {
            return false;
        }
        if (distanceX < this.mThenarDistance && distanceY < this.mThenarDistance && distanceX + distanceY < this.mBottomThenarDistance) {
            z = false;
        }
        return z;
    }

    private boolean inFirstEdge(float x, float y, float distanceY, float curSG, float orientation) {
        boolean z = false;
        if (distanceY <= ORIENTATION_0) {
            return false;
        }
        float firstEdge = distanceY >= this.mEdgeLengthSpead ? this.mRealFirstEdgeSpread : this.mRealFirstEdge;
        if (orientation != ORIENTATION_0) {
            if (y < ORIENTATION_0 || y > firstEdge) {
                if (y <= this.mScreenX && y >= this.mScreenX - firstEdge) {
                }
                return z;
            }
            z = isDoubtSG(curSG);
            return z;
        } else if (this.mDisableEdgeCheck) {
            if (y >= this.mScreenY * INPUT_Y_LIMIT_RATIO && y <= this.mScreenY) {
                if (x < ORIENTATION_0 || x > firstEdge) {
                    if (x <= this.mScreenX && x >= this.mScreenX - firstEdge) {
                    }
                }
                z = isDoubtSG(curSG);
            }
            return z;
        } else {
            if (x < ORIENTATION_0 || x > firstEdge) {
                if (x <= this.mScreenX && x >= this.mScreenX - firstEdge) {
                }
                return z;
            }
            z = isDoubtSG(curSG);
            return z;
        }
    }

    private boolean inSecondEdge(float x, float y, float distanceY, float curSG, float orientation) {
        boolean z = false;
        if (orientation != ORIENTATION_0 || distanceY <= ORIENTATION_0) {
            return false;
        }
        float firstEdge = distanceY >= this.mEdgeLengthSpead ? this.mFirstEdgeSpread : this.mFirstEdge;
        float secondEdge = distanceY >= this.mEdgeLengthSpead ? this.mSecondEdge + SPREAD_EDGE : this.mSecondEdge;
        if (this.mOnlySupportSecondEdge) {
            if (x <= firstEdge || x >= this.mScreenX - firstEdge) {
                z = isDoubtSG(curSG);
            }
            return z;
        } else if (this.mDisableEdgeCheck) {
            if (y >= this.mScreenY * INPUT_Y_LIMIT_RATIO && y <= this.mScreenY) {
                if (x <= firstEdge || x > secondEdge) {
                    if (x >= this.mScreenX - secondEdge && x < this.mScreenX - firstEdge) {
                    }
                }
                z = isDoubtSG(curSG);
            }
            return z;
        } else {
            if (x <= firstEdge || x > secondEdge) {
                if (x >= this.mScreenX - secondEdge && x < this.mScreenX - firstEdge) {
                }
                return z;
            }
            z = isDoubtSG(curSG);
            return z;
        }
    }

    private boolean isEdgePoint(float x, float y, float distanceX, float distanceY, float curSG, float orientation) {
        if (orientation != ORIENTATION_0 && !mLandscapeWhiteList.contains(this.mPackageName)) {
            return false;
        }
        if (isThenar(distanceX, distanceY)) {
            Log.d(TAG, "find T:   " + distanceX + "," + distanceY + ", ( " + x + "," + y + "), " + this.mPackageName);
            return true;
        } else if (this.mOnlySupportSecondEdge || !inFirstEdge(x, y, distanceY, curSG, orientation)) {
            return false;
        } else {
            Log.d(TAG, "find E:   " + distanceX + "," + distanceY + ", ( " + x + "," + y + "), " + this.mPackageName);
            return true;
        }
    }

    private boolean inDoubtThenarRect(float x, float y) {
        if (y < this.mScreenY - this.mDoubtThenarLengthY || (x > this.mDoubtThenarLengthX && x < this.mScreenX - this.mDoubtThenarLengthX)) {
            return false;
        }
        return true;
    }

    private boolean isDoubtSG(float curSG) {
        printfLog("curSG=" + curSG + " mSGMinValue=" + this.mSGMinValue + " mEnableSG is " + this.mEnableSG);
        if (!this.mEnableSG || curSG >= this.mSGMinValue) {
            return true;
        }
        return false;
    }

    private TouchInfo parseAxisToolValue(float majorValue, float minorValue) {
        printfLog("majorValue=" + majorValue + ",minorValue=" + minorValue);
        if (minorValue == ORIENTATION_0 || majorValue == ORIENTATION_0) {
            printfLog("set distanceX, distanceY, SG to 0 because of minorValue or majorValue is 0");
            this.mRealMoveSpreadXLimit = this.mMoveSpreadXLimit;
            this.mRealMoveXLimit = this.mMoveXLimit;
            this.mRealMoveSpreadYLimit = this.mMoveSpreadYLimit;
            this.mRealMoveYLimit = this.mMoveYLimit;
            this.mRealFirstEdge = this.mFirstEdge;
            this.mRealFirstEdgeSpread = this.mFirstEdgeSpread;
            return new TouchInfo(ORIENTATION_0, ORIENTATION_0, ORIENTATION_0);
        }
        float realmajorValue = majorValue / minorValue;
        printfLog("realmajorValue=" + realmajorValue);
        float[] majorArray = parseAxisToolMajor(realmajorValue);
        if (majorArray[3] == HwFragmentMenuItemView.ALPHA_NORMAL) {
            printfLog("version=" + majorArray[3] + ", SG=" + majorArray[2] + ", edgeY=" + majorArray[1] + ",edgeX=" + majorArray[0]);
            float sg = (majorArray[2] < ORIENTATION_0 || majorArray[2] > 255.0f) ? ORIENTATION_0 : majorArray[2];
            float edgeY = (majorArray[1] < ORIENTATION_0 || majorArray[1] > 100.0f) ? ORIENTATION_0 : majorArray[1];
            float edgeX = (majorArray[0] < ORIENTATION_0 || majorArray[0] > 100.0f) ? ORIENTATION_0 : majorArray[0];
            printfLog("version=1, SG=" + sg + ", edgeY=" + edgeY + ",edgeX=" + edgeX);
            printfLog("before mMoveXLimit=" + this.mMoveXLimit + ",mMoveYLimit" + this.mMoveYLimit + ",mMoveSpreadXLimit=" + this.mMoveSpreadXLimit + ",mMoveSpreadYLimit=" + this.mMoveSpreadYLimit);
            this.mRealMoveSpreadXLimit = this.mMoveSpreadXLimit * minorValue;
            this.mRealMoveXLimit = this.mMoveXLimit * minorValue;
            this.mRealMoveSpreadYLimit = this.mMoveSpreadYLimit * minorValue;
            this.mRealMoveYLimit = this.mMoveYLimit * minorValue;
            this.mRealFirstEdge = this.mFirstEdge * minorValue;
            this.mRealFirstEdgeSpread = this.mFirstEdgeSpread * minorValue;
            printfLog("mMoveXLimit=" + this.mRealMoveXLimit + ",mMoveYLimit" + this.mRealMoveYLimit + ",mMoveSpreadXLimit=" + this.mRealMoveSpreadXLimit + ",mMoveSpreadYLimit=" + this.mRealMoveSpreadYLimit);
            return new TouchInfo(sg, edgeX, edgeY);
        }
        printfLog("version=0,+ edgeX= " + majorValue + ",edgeY=" + minorValue);
        this.mRealMoveSpreadXLimit = this.mMoveSpreadXLimit;
        this.mRealMoveXLimit = this.mMoveXLimit;
        this.mRealMoveSpreadYLimit = this.mMoveSpreadYLimit;
        this.mRealMoveYLimit = this.mMoveYLimit;
        this.mRealFirstEdge = this.mFirstEdge;
        this.mRealFirstEdgeSpread = this.mFirstEdgeSpread;
        return new TouchInfo(ORIENTATION_0, ORIENTATION_0, ORIENTATION_0);
    }

    private float[] parseAxisToolMajor(float value) {
        printfLog("parseAxisToolMajor=" + value);
        parseFloatValue = new float[4];
        int bits = (int) value;
        parseFloatValue[0] = (float) (bits & 255);
        parseFloatValue[1] = (float) ((bits >> 8) & 255);
        parseFloatValue[2] = (float) ((bits >> 16) & 255);
        parseFloatValue[3] = (float) ((bits >> 24) & 255);
        return parseFloatValue;
    }

    private boolean isDoubtThenar(float x, float y, float distanceX, float distanceY, float curSG, float orientation) {
        boolean z = false;
        if (orientation != ORIENTATION_0) {
            return false;
        }
        if (distanceX > ORIENTATION_0 && distanceX + distanceY >= this.mDoubtThenarDistance && inDoubtThenarRect(x, y)) {
            z = isDoubtSG(curSG);
        }
        return z;
    }

    private int handleDownForFilterIds(MotionEvent event) {
        int index = event.getActionIndex();
        int currentPointId = event.getPointerId(index);
        float rawX0 = event.getRawX();
        float rawY0 = event.getRawY();
        float offsetX = rawX0 - event.getX();
        float offsetY = rawY0 - event.getY();
        float currentRawX = event.getX(index) + offsetX;
        float currentRawY = event.getY(index) + offsetY;
        float orientation = event.getOrientation();
        TouchInfo touchInfo = parseAxisToolValue(event.getAxisValue(6, index), event.getAxisValue(7, index));
        float currentDistanceX = touchInfo.distanceX;
        float currentDistanceY = touchInfo.distanceY;
        float curSG = touchInfo.SG;
        long time = event.getEventTime();
        int pointId;
        if (isDoubtThenar(currentRawX, currentRawY, currentDistanceX, currentDistanceY, curSG, orientation)) {
            Log.d(TAG, "find TP:   " + currentDistanceX + "," + currentDistanceY + ", ( " + currentRawX + "," + currentRawY + ")," + this.mPackageName);
            pointId = addFilterId(currentPointId, currentRawX, currentRawY, currentDistanceX, currentDistanceY, time);
            this.mEventPoints[currentPointId].setBottomThenar(true);
            return pointId;
        } else if (isEdgePoint(currentRawX, currentRawY, currentDistanceX, currentDistanceY, curSG, orientation)) {
            return addFilterId(currentPointId, currentRawX, currentRawY, currentDistanceX, currentDistanceY, time);
        } else {
            if (inSecondEdge(currentRawX, currentRawY, currentDistanceY, curSG, orientation)) {
                addDoubtId(currentPointId, currentRawX, currentRawY, currentDistanceX, currentDistanceY, time);
                Log.d(TAG, "find SE:   " + currentDistanceX + "," + currentDistanceY + ", ( " + currentRawX + "," + currentRawY + "), " + this.mPackageName);
            } else if (!(this.mDoubtAndNotFilterIdBits == 0 && this.mFilterIdBits == 0)) {
                int count = event.getPointerCount();
                for (int i = 0; i < count; i++) {
                    pointId = event.getPointerId(i);
                    int idBitsToAssign = 1 << pointId;
                    EventPoint eventPoint = this.mEventPoints[pointId];
                    if (eventPoint != null) {
                        float rawX = event.getX(i) + offsetX;
                        float rawY = event.getY(i) + offsetY;
                        TouchInfo newTouchInfo = parseAxisToolValue(event.getAxisValue(6, i), event.getAxisValue(7, i));
                        eventPoint.update(rawX, rawY, newTouchInfo.distanceX, newTouchInfo.distanceY, time);
                        if ((this.mFilterIdBits & idBitsToAssign) != 0) {
                            eventPoint.setTimeoutCheck(true);
                        }
                    }
                }
                this.mFilterIdBits |= this.mDoubtAndNotFilterIdBits;
                this.mDoubtAndNotFilterIdBits = 0;
            }
            return -1;
        }
    }

    private int clearCurrentFilterId(MotionEvent event) {
        return clearFilterId(event.getPointerId(event.getActionIndex()));
    }

    private int addDoubtId(int pointId, float rawX, float rawY, float distanceX, float distanceY, long time) {
        this.mDoubtAndNotFilterIdBits |= 1 << pointId;
        this.mEventPoints[pointId] = new EventPoint(pointId, rawX, rawY, distanceX, distanceY, time);
        return pointId;
    }

    private int addFilterId(int pointId, float rawX, float rawY, float distanceX, float distanceY, long time) {
        this.mFilterIdBits |= 1 << pointId;
        this.mEventPoints[pointId] = new EventPoint(pointId, rawX, rawY, distanceX, distanceY, time);
        return pointId;
    }

    private void clearAllFilterIds() {
        this.mFilterIdBits = 0;
        this.mDoubtAndNotFilterIdBits = 0;
        for (int i = 0; i < 32; i++) {
            this.mEventPoints[i] = null;
        }
    }

    private boolean isCurrentPointInshortTime(MotionEvent event) {
        int currentPointId = event.getPointerId(event.getActionIndex());
        int idBitsToAssign = 1 << currentPointId;
        long time = event.getEventTime();
        if ((this.mFilterIdBits & idBitsToAssign) != 0) {
            EventPoint eventPoint = this.mEventPoints[currentPointId];
            if (eventPoint != null) {
                return eventPoint.inShortTime(time);
            }
        }
        return false;
    }

    private String getPackageName() {
        return this.mPackageName;
    }

    private MotionEvent convertToFirstPointDownEvent(MotionEvent event, int pointId) {
        printfLog("enter ConvertToFirstPointDownEvent");
        if (event == null) {
            return null;
        }
        EventPoint eventPoint = this.mEventPoints[pointId];
        if (eventPoint == null) {
            printfLog("eventPoint is null in ConvertToFirstPointDownEvent " + pointId);
            return null;
        }
        int newAction;
        float rawX0 = event.getRawX();
        float rawY0 = event.getRawY();
        float offsetX = event.getX() - rawX0;
        float offsetY = event.getY() - rawY0;
        printfLog("offsetX=" + offsetX + ",offsetY=" + offsetY);
        float newX = eventPoint.pointDownX;
        float newY = eventPoint.pointDownY;
        printfLog("pointDownX=" + newX + ",pointDownY=" + newY + ",pointId=" + pointId);
        int count = event.getPointerCount();
        PointerCoords[] pc = PointerCoords.createArray(count);
        PointerProperties[] pp = PointerProperties.createArray(count);
        int localIndex = 0;
        int i = 0;
        while (i < count) {
            int id = event.getPointerId(i);
            event.getPointerProperties(i, pp[i]);
            event.getPointerCoords(i, pc[i]);
            if (pointId == id) {
                localIndex = i;
                printfLog("old x=" + pc[i].x + ",old y=" + pc[i].y + ",pointId=" + id);
                pc[i].x = newX;
                pc[i].y = newY;
            } else {
                pc[i].x -= offsetX;
                pc[i].y -= offsetY;
            }
            printfLog("x=" + pc[i].x + ",y=" + pc[i].y + ",pointId=" + id);
            i++;
        }
        long downTime = event.getDownTime();
        long eventTime = SystemClock.uptimeMillis();
        if (count > 1) {
            newAction = (localIndex << 8) | 5;
        } else {
            newAction = 0;
            downTime = eventTime;
        }
        MotionEvent newMotionEvent = MotionEvent.obtain(downTime, eventTime, newAction, count, pp, pc, event.getMetaState(), event.getButtonState(), event.getXPrecision(), event.getYPrecision(), event.getDeviceId(), event.getEdgeFlags(), event.getSource(), event.getFlags());
        newMotionEvent.offsetLocation(offsetX, offsetY);
        printfLog("leave ConvertToFirstPointDownEvent2");
        return newMotionEvent;
    }

    private static MotionEvent convertToLastPointDownEvent(MotionEvent event, int pointId) {
        if (event == null) {
            return null;
        }
        int count = event.getPointerCount();
        if (count > 1) {
            int localIndex = 0;
            for (int i = 0; i < count; i++) {
                if (pointId == event.getPointerId(i)) {
                    localIndex = i;
                    break;
                }
            }
            event.setAction((localIndex << 8) | 5);
        } else {
            event.setAction(0);
        }
        return event;
    }

    private static MotionEvent copyUpEvent(MotionEvent event) {
        int oldAction = event.getAction();
        int oldActionMasked = oldAction & 255;
        MotionEvent upEvent = event.copy();
        if (oldActionMasked == 0) {
            upEvent.setAction(1);
        } else if (oldActionMasked == 5) {
            upEvent.setAction(oldAction + 1);
        }
        return upEvent;
    }

    public MotionEvent getAdditionalEvent() {
        if (!this.mAFTTeature) {
            return null;
        }
        MotionEvent event = this.mAdditionalEvent;
        this.mAdditionalEvent = null;
        return event;
    }

    public void handleDownResult(MotionEvent event, boolean result) {
        if (this.mAFTTeature && event.getActionMasked() == 0 && !result) {
            Log.i(TAG, "ACTION_DOWN has not handle");
        }
    }

    private MotionEvent processActionDown(MotionEvent event, int action) {
        printfLog("receive down event, x = " + event.getX() + " y=" + event.getY() + " rawX=" + event.getRawX() + " rawY=" + event.getRawY());
        handleDownForFilterIds(event);
        MotionEvent mv = filterMotionEvent(event, this.mFilterIdBits);
        if (action == 5 && mv != null && mv.getPointerCount() == 1) {
            mv.setDownTime(mv.getEventTime());
        }
        return mv;
    }

    private MotionEvent processActionMove(MotionEvent event) {
        int modifiedPointId = -1;
        if (!(this.mFilterIdBits == 0 && this.mDoubtAndNotFilterIdBits == 0)) {
            modifiedPointId = handleMoveForFilterIds(event);
        }
        if (modifiedPointId == -1) {
            return filterMotionEvent(event, this.mFilterIdBits);
        }
        MotionEvent downEvent;
        MotionEvent modifyEvent = filterMotionEvent(event, this.mFilterIdBits);
        printfLog("before convert to down event and send out in action_move. x=" + modifyEvent.getX() + " y=" + modifyEvent.getY());
        String curRunningPackageName = getPackageName();
        printfLog("current package name is " + curRunningPackageName);
        if (LAUNCHER_PKG_NAME.equals(curRunningPackageName)) {
            printfLog("to use the old method convert to down event");
            downEvent = convertToLastPointDownEvent(modifyEvent, modifiedPointId);
        } else {
            printfLog("to use the new method convert to down event");
            downEvent = convertToFirstPointDownEvent(modifyEvent, modifiedPointId);
        }
        int idBitsToAssign = 1 << modifiedPointId;
        if ((this.mDoubtAndNotFilterIdBits & idBitsToAssign) == 0 && (this.mFilterIdBits & idBitsToAssign) == 0) {
            this.mEventPoints[modifiedPointId] = null;
        }
        printfLog("after convert to down event and send out in action_move. x=" + downEvent.getX() + " y=" + downEvent.getY());
        return downEvent;
    }

    private MotionEvent processActionUp(MotionEvent event, int action) {
        MotionEvent modifyEvent;
        boolean inShortTime = isCurrentPointInshortTime(event);
        int oldFilterIdBits = this.mFilterIdBits;
        int clearPointId = clearCurrentFilterId(event);
        if (clearPointId == -1) {
            modifyEvent = filterMotionEvent(event, this.mFilterIdBits);
        } else if (inShortTime) {
            modifyEvent = convertToLastPointDownEvent(filterMotionEvent(event, this.mFilterIdBits), clearPointId);
            if (modifyEvent != null) {
                this.mAdditionalEvent = copyUpEvent(modifyEvent);
            }
        } else {
            modifyEvent = filterMotionEvent(event, oldFilterIdBits);
            if (modifyEvent != null) {
                modifyEvent.setAction(2);
            }
        }
        if (action == 1) {
            clearAllFilterIds();
        }
        return modifyEvent;
    }

    public MotionEvent convertPointEvent(MotionEvent event) {
        if (!this.mAFTTeature) {
            return event;
        }
        int action = event.getActionMasked();
        if (action == 0 || action == 5) {
            return processActionDown(event, action);
        }
        if (action == 2) {
            return processActionMove(event);
        }
        if (action == 6 || action == 1) {
            return processActionUp(event, action);
        }
        if (action != 3) {
            return event;
        }
        MotionEvent modifyEvent = filterMotionEvent(event, this.mFilterIdBits);
        if (modifyEvent != null) {
            modifyEvent.setAction(3);
        }
        clearAllFilterIds();
        return modifyEvent;
    }
}
