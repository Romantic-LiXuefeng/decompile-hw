package huawei.power;

import android.app.ActivityManagerNative;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.util.Log;
import android.util.Xml;
import com.android.internal.os.BackgroundThread;
import com.huawei.hsm.permission.StubController;
import com.huawei.pgmng.plug.PGSdk;
import com.huawei.pgmng.plug.PGSdk.Sink;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class AudioEffectLowPowerTaskImpl implements AudioEffectLowPowerTask {
    private static final boolean DEBUG = false;
    private static final long DELAY_ONE_MINS = 130000;
    private static final String DISPLAY_ACL_SUPPORT = "hw.display.acl_support";
    private static final int ETYPE_CONNECT_WITH_PG_SDK = 1;
    private static String FILE_DIR = "/product/etc/hwpg/";
    private static String FILE_NAME = "power_popular_app.xml";
    private static final String LOW_AUDIO_EFFECT_PROP = "ro.game.low_audio_effect";
    private static final String PRODUCT_NAME = SystemProperties.get("ro.build.product", "unknown");
    private static final String SET_GAMESCENE_UID = "GameSceneUid=";
    private static final String SET_SMARTPA_LOWERPOWER_OFF = "SmartPA_lowpower=off";
    private static final String SET_SMARTPA_LOWERPOWER_ON = "SmartPA_lowpower=on";
    private static final boolean mAclEnabled = SystemProperties.getBoolean(DISPLAY_ACL_SUPPORT, false);
    private static final boolean mGameSceneVolumeEnaled = (!"VTR".equals(PRODUCT_NAME) ? "VKY".equals(PRODUCT_NAME) : true);
    private static final boolean mLowAudioEffectEnabled = SystemProperties.getBoolean(LOW_AUDIO_EFFECT_PROP, false);
    private String TAG = "AudioEffectLowPowerTaskImpl";
    private Handler mAudioEffectLowPowerHandler;
    private AudioManager mAudioManager = null;
    private Context mContext = null;
    private long mDelayTime = 1000;
    private int mGameSceneUid = -1;
    private boolean mLowAudioEffectEnable = false;
    private PGSdk mPGSdk = null;
    private ScreenAclLowerPowerImpl mScrnAclCtrl = null;
    private Sink mStateRecognitionListener = new Sink() {
        public void onStateChanged(int stateType, int eventType, int pid, String pkg, int uid) {
            if (AudioEffectLowPowerTaskImpl.mLowAudioEffectEnabled) {
                AudioEffectLowPowerTaskImpl.this.handleSceneAudioEffect(eventType, pkg);
            }
            Log.w(AudioEffectLowPowerTaskImpl.this.TAG, "onStateChanged, stateType = " + stateType + ", eventType = " + eventType + ", pid = " + pid + ", pkg = " + pkg + ", uid = " + uid);
            if (AudioEffectLowPowerTaskImpl.mAclEnabled && AudioEffectLowPowerTaskImpl.this.mScrnAclCtrl != null) {
                AudioEffectLowPowerTaskImpl.this.mScrnAclCtrl.handlePGScene(stateType, eventType, pid, pkg, uid);
            }
        }
    };
    private ArrayList<String> mTopPopularGameList = null;

    final class AudioEffectLowPowerHandler extends Handler {
        public AudioEffectLowPowerHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            if (msg == null) {
                Log.w(AudioEffectLowPowerTaskImpl.this.TAG, "null == msg");
                return;
            }
            switch (msg.what) {
                case 1:
                    AudioEffectLowPowerTaskImpl.this.getPGSdk();
                    break;
                default:
                    Log.w(AudioEffectLowPowerTaskImpl.this.TAG, "msg.what = " + msg.what + "  is Invalid !");
                    break;
            }
        }
    }

    public AudioEffectLowPowerTaskImpl(Context context) {
        if (Process.myUid() != 1000) {
            Log.e(this.TAG, "AppHibernateTask is only permitted for system user. calling Uid: " + Process.myUid());
            return;
        }
        this.mContext = context;
        if (mAclEnabled) {
            this.mScrnAclCtrl = new ScreenAclLowerPowerImpl(this.mContext);
        }
        if (mLowAudioEffectEnabled) {
            this.mTopPopularGameList = getConfigTopAppList();
        }
        if (mAclEnabled || !(this.mTopPopularGameList == null || this.mTopPopularGameList.size() == 0)) {
            this.mAudioManager = (AudioManager) this.mContext.getSystemService("audio");
            this.mLowAudioEffectEnable = true;
            setLowAudioEffectByScene(false);
            IntentFilter filter = new IntentFilter("android.intent.action.BOOT_COMPLETED");
            filter.addAction("android.intent.action.LOCKED_BOOT_COMPLETED");
            filter.setPriority(1000);
            if (mAclEnabled || mLowAudioEffectEnabled) {
                context.registerReceiver(new BroadcastReceiver() {
                    public void onReceive(Context context, Intent intent) {
                        AudioEffectLowPowerTaskImpl.this.mAudioEffectLowPowerHandler = new AudioEffectLowPowerHandler(BackgroundThread.get().getLooper());
                        AudioEffectLowPowerTaskImpl.this.getPGSdk();
                        context.unregisterReceiver(this);
                    }
                }, filter);
            }
        }
    }

    private void handleSceneAudioEffect(int eventType, String mFrontPkgName) {
        if (this.mTopPopularGameList != null && this.mTopPopularGameList.contains(mFrontPkgName)) {
            if (eventType == 1) {
                setLowAudioEffectByScene(true);
                setGameVolumeByScene(mFrontPkgName);
                return;
            }
            setLowAudioEffectByScene(false);
            setGameVolumeByScene(null);
        }
    }

    private void setLowAudioEffectByScene(boolean smartPAOn) {
        if (this.mAudioManager != null && smartPAOn != this.mLowAudioEffectEnable) {
            this.mLowAudioEffectEnable = smartPAOn;
            Log.w(this.TAG, "mAudioManager.setParameters = " + (smartPAOn ? SET_SMARTPA_LOWERPOWER_ON : SET_SMARTPA_LOWERPOWER_OFF));
            if (smartPAOn) {
                this.mAudioManager.setParameters(SET_SMARTPA_LOWERPOWER_ON);
            } else {
                this.mAudioManager.setParameters(SET_SMARTPA_LOWERPOWER_OFF);
            }
        }
    }

    private int getCurrentUserId() {
        long ident = Binder.clearCallingIdentity();
        try {
            int i = ActivityManagerNative.getDefault().getCurrentUser().id;
            return i;
        } catch (RemoteException e) {
            return 0;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private int getUidByPkg(String pkgName) {
        int uid = -1;
        if (pkgName == null) {
            return uid;
        }
        try {
            uid = this.mContext.getPackageManager().getPackageUidAsUser(pkgName, getCurrentUserId());
        } catch (Exception e) {
            Log.w(this.TAG, "not found uid pkgName:" + pkgName);
        }
        return uid;
    }

    private void setGameVolumeByScene(String pkgName) {
        if (mGameSceneVolumeEnaled && this.mAudioManager != null) {
            int uid = getUidByPkg(pkgName);
            if (this.mGameSceneUid != uid) {
                this.mGameSceneUid = uid;
                this.mAudioManager.setParameters(SET_GAMESCENE_UID + this.mGameSceneUid);
            }
        }
    }

    private ArrayList<String> getConfigTopAppList() {
        Throwable th;
        String POPULAR_GAME = "popular_game";
        String PKG_NAME = "pkg_name";
        FileInputStream fileInputStream = null;
        try {
            File file = new File(FILE_DIR, FILE_NAME);
            try {
                if (!file.exists()) {
                    return null;
                }
                ArrayList<String> mList = new ArrayList();
                XmlPullParser mParser = Xml.newPullParser();
                try {
                    FileInputStream mStream = new FileInputStream(file);
                    try {
                        mParser.setInput(mStream, null);
                        for (int event = mParser.getEventType(); event != 1; event = mParser.next()) {
                            String name = mParser.getName();
                            switch (event) {
                                case 3:
                                    if (!name.equals(POPULAR_GAME)) {
                                        break;
                                    }
                                    mList.add(mParser.getAttributeValue(null, PKG_NAME));
                                    break;
                                default:
                                    break;
                            }
                        }
                        if (mStream != null) {
                            try {
                                mStream.close();
                            } catch (IOException e) {
                                Log.e(this.TAG, "File Stream close IOException!");
                            }
                        }
                    } catch (FileNotFoundException e2) {
                        fileInputStream = mStream;
                        mList = null;
                        if (fileInputStream != null) {
                            try {
                                fileInputStream.close();
                            } catch (IOException e3) {
                                Log.e(this.TAG, "File Stream close IOException!");
                            }
                        }
                        return mList;
                    } catch (IOException e4) {
                        fileInputStream = mStream;
                        mList = null;
                        if (fileInputStream != null) {
                            try {
                                fileInputStream.close();
                            } catch (IOException e5) {
                                Log.e(this.TAG, "File Stream close IOException!");
                            }
                        }
                        return mList;
                    } catch (XmlPullParserException e6) {
                        fileInputStream = mStream;
                        mList = null;
                        if (fileInputStream != null) {
                            try {
                                fileInputStream.close();
                            } catch (IOException e7) {
                                Log.e(this.TAG, "File Stream close IOException!");
                            }
                        }
                        return mList;
                    } catch (Throwable th2) {
                        th = th2;
                        fileInputStream = mStream;
                        if (fileInputStream != null) {
                            try {
                                fileInputStream.close();
                            } catch (IOException e8) {
                                Log.e(this.TAG, "File Stream close IOException!");
                            }
                        }
                        throw th;
                    }
                } catch (FileNotFoundException e9) {
                    mList = null;
                    if (fileInputStream != null) {
                        fileInputStream.close();
                    }
                    return mList;
                } catch (IOException e10) {
                    mList = null;
                    if (fileInputStream != null) {
                        fileInputStream.close();
                    }
                    return mList;
                } catch (XmlPullParserException e11) {
                    mList = null;
                    if (fileInputStream != null) {
                        fileInputStream.close();
                    }
                    return mList;
                } catch (Throwable th3) {
                    th = th3;
                    if (fileInputStream != null) {
                        fileInputStream.close();
                    }
                    throw th;
                }
                return mList;
            } catch (NullPointerException e12) {
                return null;
            }
        } catch (NullPointerException e13) {
            return null;
        }
    }

    public void callPGregisterListener() {
        if (this.mPGSdk != null) {
            try {
                this.mPGSdk.enableStateEvent(this.mStateRecognitionListener, 10002);
                this.mPGSdk.enableStateEvent(this.mStateRecognitionListener, 10011);
                this.mPGSdk.enableStateEvent(this.mStateRecognitionListener, StubController.MIN_APPLICATION_UID);
                this.mPGSdk.enableStateEvent(this.mStateRecognitionListener, 10001);
                this.mPGSdk.enableStateEvent(this.mStateRecognitionListener, 10003);
                this.mPGSdk.enableStateEvent(this.mStateRecognitionListener, 10004);
                this.mPGSdk.enableStateEvent(this.mStateRecognitionListener, 10007);
                this.mPGSdk.enableStateEvent(this.mStateRecognitionListener, 10008);
                this.mPGSdk.enableStateEvent(this.mStateRecognitionListener, 10009);
                this.mPGSdk.enableStateEvent(this.mStateRecognitionListener, 10010);
                this.mPGSdk.enableStateEvent(this.mStateRecognitionListener, 10013);
                this.mPGSdk.enableStateEvent(this.mStateRecognitionListener, 10015);
                this.mPGSdk.enableStateEvent(this.mStateRecognitionListener, 10016);
                this.mPGSdk.enableStateEvent(this.mStateRecognitionListener, 10017);
            } catch (RemoteException e) {
                this.mPGSdk = null;
                Log.e(this.TAG, "mPGSdk registerSink && enableStateEvent happend RemoteException ");
            } catch (NullPointerException e2) {
                Log.e(this.TAG, "mPGSdk registerSink && enableStateEvent happend NullPointerException ");
            }
        }
    }

    public void callPGunRegisterListener() {
        if (this.mPGSdk != null) {
            try {
                this.mPGSdk.disableStateEvent(this.mStateRecognitionListener, 10002);
                this.mPGSdk.disableStateEvent(this.mStateRecognitionListener, 10011);
                this.mPGSdk.disableStateEvent(this.mStateRecognitionListener, StubController.MIN_APPLICATION_UID);
                this.mPGSdk.disableStateEvent(this.mStateRecognitionListener, 10001);
                this.mPGSdk.disableStateEvent(this.mStateRecognitionListener, 10003);
                this.mPGSdk.disableStateEvent(this.mStateRecognitionListener, 10004);
                this.mPGSdk.disableStateEvent(this.mStateRecognitionListener, 10007);
                this.mPGSdk.disableStateEvent(this.mStateRecognitionListener, 10008);
                this.mPGSdk.disableStateEvent(this.mStateRecognitionListener, 10009);
                this.mPGSdk.disableStateEvent(this.mStateRecognitionListener, 10010);
                this.mPGSdk.disableStateEvent(this.mStateRecognitionListener, 10013);
                this.mPGSdk.disableStateEvent(this.mStateRecognitionListener, 10015);
                this.mPGSdk.disableStateEvent(this.mStateRecognitionListener, 10016);
                this.mPGSdk.disableStateEvent(this.mStateRecognitionListener, 10017);
            } catch (RemoteException e) {
                Log.e(this.TAG, "callPG unRegisterListener  happend RemoteException ");
            } catch (NullPointerException e2) {
                Log.e(this.TAG, "callPG unRegisterListener  happend NullPointerException ");
            }
        }
    }

    private boolean getPGSdk() {
        if (this.mPGSdk != null) {
            return true;
        }
        this.mPGSdk = PGSdk.getInstance();
        if (this.mPGSdk != null) {
            callPGregisterListener();
            return true;
        }
        Log.w(this.TAG, "getPGSdk failed, use handle to retry after time = " + this.mDelayTime);
        if (this.mDelayTime <= DELAY_ONE_MINS) {
            sendMsgToHiberEventHandler(1, null, this.mDelayTime);
            this.mDelayTime *= 2;
        }
        return false;
    }

    private void sendMsgToHiberEventHandler(int eventType, String pkg, long delayMillis) {
        if (this.mAudioEffectLowPowerHandler == null) {
            Log.e(this.TAG, "sendMsgToHiberEventHandler     exit  , because  NULL == mHiberEventHandler");
            return;
        }
        this.mAudioEffectLowPowerHandler.removeMessages(eventType);
        Message msg = this.mAudioEffectLowPowerHandler.obtainMessage();
        msg.what = eventType;
        msg.obj = pkg;
        this.mAudioEffectLowPowerHandler.sendMessageDelayed(msg, delayMillis);
    }
}
