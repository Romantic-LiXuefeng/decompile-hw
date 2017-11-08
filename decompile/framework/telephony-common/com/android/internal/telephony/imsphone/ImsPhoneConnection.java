package com.android.internal.telephony.imsphone;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Registrant;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.ims.ImsCall;
import com.android.ims.ImsCallProfile;
import com.android.ims.ImsException;
import com.android.internal.telephony.Call.State;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Connection.PostDialState;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.UUSInfo;
import java.util.Objects;

public class ImsPhoneConnection extends Connection {
    private static final boolean DBG = true;
    private static final int EVENT_DTMF_DELAY_DONE = 5;
    private static final int EVENT_DTMF_DONE = 1;
    private static final int EVENT_NEXT_POST_DIAL = 3;
    private static final int EVENT_PAUSE_DONE = 2;
    private static final int EVENT_WAKE_LOCK_TIMEOUT = 4;
    private static final String LOG_TAG = "ImsPhoneConnection";
    private static final int PAUSE_DELAY_MILLIS = 3000;
    private static final int WAKE_LOCK_TIMEOUT_MILLIS = 60000;
    private static final boolean isImsAsNormal = SystemProperties.getBoolean("ro.config.hw_ims_as_normal", false);
    private long mConferenceConnectTime = 0;
    private long mDisconnectTime;
    private boolean mDisconnected;
    private int mDtmfToneDelay = 0;
    private Bundle mExtras = new Bundle();
    private Handler mHandler;
    private ImsCall mImsCall;
    private boolean mIsEmergency = false;
    private boolean mIsWifiStateFromExtras = false;
    private ImsPhoneCallTracker mOwner;
    private ImsPhoneCall mParent;
    private WakeLock mPartialWakeLock;
    private UUSInfo mUusInfo;

    class MyHandler extends Handler {
        MyHandler(Looper l) {
            super(l);
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    ImsPhoneConnection.this.mHandler.sendMessageDelayed(ImsPhoneConnection.this.mHandler.obtainMessage(5), (long) ImsPhoneConnection.this.mDtmfToneDelay);
                    return;
                case 2:
                case 3:
                case 5:
                    ImsPhoneConnection.this.processNextPostDialChar();
                    return;
                case 4:
                    ImsPhoneConnection.this.releaseWakeLock();
                    return;
                default:
                    return;
            }
        }
    }

    public ImsPhoneConnection(Phone phone, ImsCall imsCall, ImsPhoneCallTracker ct, ImsPhoneCall parent, boolean isUnknown) {
        boolean z = false;
        super(5);
        createWakeLock(phone.getContext());
        acquireWakeLock();
        this.mOwner = ct;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        this.mImsCall = imsCall;
        if (imsCall == null || imsCall.getCallProfile() == null) {
            this.mNumberPresentation = 3;
            this.mCnapNamePresentation = 3;
        } else {
            this.mAddress = imsCall.getCallProfile().getCallExtra("oi");
            this.mCnapName = imsCall.getCallProfile().getCallExtra("cna");
            this.mNumberPresentation = ImsCallProfile.OIRToPresentation(imsCall.getCallProfile().getCallExtraInt("oir"));
            this.mCnapNamePresentation = ImsCallProfile.OIRToPresentation(imsCall.getCallProfile().getCallExtraInt("cnap"));
            updateMediaCapabilities(imsCall);
        }
        if (!isUnknown) {
            z = true;
        }
        this.mIsIncoming = z;
        this.mCreateTime = System.currentTimeMillis();
        this.mUusInfo = null;
        updateWifiState();
        updateExtras(imsCall);
        this.mParent = parent;
        this.mParent.attach(this, this.mIsIncoming ? State.INCOMING : State.DIALING);
        fetchDtmfToneDelay(phone);
    }

    public ImsPhoneConnection(Phone phone, String dialString, ImsPhoneCallTracker ct, ImsPhoneCall parent, boolean isEmergency) {
        super(5);
        createWakeLock(phone.getContext());
        acquireWakeLock();
        this.mOwner = ct;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        this.mDialString = dialString;
        this.mAddress = PhoneNumberUtils.extractNetworkPortionAlt(dialString);
        this.mPostDialString = PhoneNumberUtils.extractPostDialPortion(dialString);
        this.mIsIncoming = false;
        this.mCnapName = null;
        this.mCnapNamePresentation = 1;
        this.mNumberPresentation = 1;
        this.mCreateTime = System.currentTimeMillis();
        this.mParent = parent;
        parent.attachFake(this, State.DIALING);
        this.mIsEmergency = isEmergency;
        fetchDtmfToneDelay(phone);
    }

    public void dispose() {
    }

    static boolean equalsHandlesNulls(Object a, Object b) {
        if (a == null) {
            return b == null;
        } else {
            return a.equals(b);
        }
    }

    private static int applyLocalCallCapabilities(ImsCallProfile localProfile, int capabilities) {
        capabilities = Connection.removeCapability(capabilities, 5);
        switch (localProfile.mCallType) {
            case 3:
                return Connection.addCapability(capabilities, 5);
            case 4:
                return Connection.addCapability(capabilities, 4);
            default:
                return capabilities;
        }
    }

    private static int applyRemoteCallCapabilities(ImsCallProfile remoteProfile, int capabilities) {
        capabilities = Connection.removeCapability(capabilities, 10);
        switch (remoteProfile.mCallType) {
            case 3:
                return Connection.addCapability(capabilities, 10);
            case 4:
                return Connection.addCapability(capabilities, 8);
            default:
                return capabilities;
        }
    }

    public String getOrigDialString() {
        return this.mDialString;
    }

    public ImsPhoneCall getCall() {
        return this.mParent;
    }

    public long getDisconnectTime() {
        return this.mDisconnectTime;
    }

    public long getHoldingStartTime() {
        return this.mHoldingStartTime;
    }

    public long getHoldDurationMillis() {
        if (getState() != State.HOLDING) {
            return 0;
        }
        return SystemClock.elapsedRealtime() - this.mHoldingStartTime;
    }

    public void setDisconnectCause(int cause) {
        this.mCause = cause;
    }

    public String getVendorDisconnectCause() {
        return null;
    }

    public ImsPhoneCallTracker getOwner() {
        return this.mOwner;
    }

    public State getState() {
        if (this.mDisconnected) {
            return State.DISCONNECTED;
        }
        return super.getState();
    }

    public void hangup() throws CallStateException {
        if (this.mDisconnected) {
            throw new CallStateException("disconnected");
        }
        this.mOwner.hangup(this);
    }

    public void separate() throws CallStateException {
        throw new CallStateException("not supported");
    }

    public void proceedAfterWaitChar() {
        if (this.mPostDialState != PostDialState.WAIT) {
            Rlog.w(LOG_TAG, "ImsPhoneConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WAIT but was " + this.mPostDialState);
            return;
        }
        setPostDialState(PostDialState.STARTED);
        processNextPostDialChar();
    }

    public void proceedAfterWildChar(String str) {
        if (this.mPostDialState != PostDialState.WILD) {
            Rlog.w(LOG_TAG, "ImsPhoneConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WILD but was " + this.mPostDialState);
            return;
        }
        setPostDialState(PostDialState.STARTED);
        StringBuilder buf = new StringBuilder(str);
        buf.append(this.mPostDialString.substring(this.mNextPostDialChar));
        this.mPostDialString = buf.toString();
        this.mNextPostDialChar = 0;
        Rlog.d(LOG_TAG, "proceedAfterWildChar: new postDialString is " + this.mPostDialString);
        processNextPostDialChar();
    }

    public void cancelPostDial() {
        setPostDialState(PostDialState.CANCELLED);
    }

    void onHangupLocal() {
        this.mCause = 3;
    }

    public boolean onDisconnect(int cause) {
        Rlog.d(LOG_TAG, "onDisconnect: cause=" + cause);
        this.mCause = cause;
        return onDisconnect();
    }

    int getImsIndex() throws CallStateException {
        String sIndex = this.mImsCall.getCallSession().getCallId();
        if (sIndex == null || sIndex.equals("")) {
            throw new CallStateException("ISM index not yet assigned");
        }
        int iIndex = Integer.parseInt(sIndex);
        if (iIndex >= 0) {
            return iIndex;
        }
        throw new CallStateException("ISM index not yet assigned");
    }

    public boolean onDisconnect() {
        boolean z = false;
        if (!this.mDisconnected) {
            this.mDisconnectTime = System.currentTimeMillis();
            this.mDuration = SystemClock.elapsedRealtime() - this.mConnectTimeReal;
            this.mDisconnected = true;
            this.mOwner.mPhone.notifyDisconnect(this);
            this.mOwner.updateCallLog(this, this.mOwner.mPhone);
            if (this.mParent != null) {
                z = this.mParent.connectionDisconnected(this);
            } else {
                Rlog.d(LOG_TAG, "onDisconnect: no parent");
            }
            if (this.mImsCall != null) {
                this.mImsCall.close();
            }
            this.mImsCall = null;
        }
        releaseWakeLock();
        return z;
    }

    void onConnectedInOrOut() {
        this.mConnectTime = System.currentTimeMillis();
        this.mConnectTimeReal = SystemClock.elapsedRealtime();
        this.mDuration = 0;
        Rlog.d(LOG_TAG, "onConnectedInOrOut: connectTime=" + this.mConnectTime);
        if (!this.mIsIncoming) {
            processNextPostDialChar();
        }
        releaseWakeLock();
    }

    void onStartedHolding() {
        this.mHoldingStartTime = SystemClock.elapsedRealtime();
    }

    private boolean processPostDialChar(char c) {
        if (PhoneNumberUtils.is12Key(c)) {
            this.mOwner.sendDtmf(c, this.mHandler.obtainMessage(1));
        } else if (c == ',') {
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(2), 3000);
        } else if (c == ';') {
            setPostDialState(PostDialState.WAIT);
        } else if (c != 'N') {
            return false;
        } else {
            setPostDialState(PostDialState.WILD);
        }
        return true;
    }

    protected void finalize() {
        releaseWakeLock();
    }

    private void processNextPostDialChar() {
        if (this.mPostDialState != PostDialState.CANCELLED) {
            char c;
            if (this.mPostDialString == null || this.mPostDialString.length() <= this.mNextPostDialChar) {
                setPostDialState(PostDialState.COMPLETE);
                c = '\u0000';
            } else {
                setPostDialState(PostDialState.STARTED);
                String str = this.mPostDialString;
                int i = this.mNextPostDialChar;
                this.mNextPostDialChar = i + 1;
                c = str.charAt(i);
                if (!processPostDialChar(c)) {
                    this.mHandler.obtainMessage(3).sendToTarget();
                    Rlog.e(LOG_TAG, "processNextPostDialChar: c=" + c + " isn't valid!");
                    return;
                }
            }
            notifyPostDialListenersNextChar(c);
            Registrant postDialHandler = this.mOwner.mPhone.getPostDialHandler();
            if (postDialHandler != null) {
                Message notifyMessage = postDialHandler.messageForRegistrant();
                if (notifyMessage != null) {
                    PostDialState state = this.mPostDialState;
                    AsyncResult ar = AsyncResult.forMessage(notifyMessage);
                    ar.result = this;
                    ar.userObj = state;
                    notifyMessage.arg1 = c;
                    notifyMessage.sendToTarget();
                }
            }
        }
    }

    private void setPostDialState(PostDialState s) {
        if (this.mPostDialState != PostDialState.STARTED && s == PostDialState.STARTED) {
            acquireWakeLock();
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(4), 60000);
        } else if (this.mPostDialState == PostDialState.STARTED && s != PostDialState.STARTED) {
            this.mHandler.removeMessages(4);
            releaseWakeLock();
        }
        this.mPostDialState = s;
        notifyPostDialListeners();
    }

    private void createWakeLock(Context context) {
        this.mPartialWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, LOG_TAG);
    }

    private void acquireWakeLock() {
        Rlog.d(LOG_TAG, "acquireWakeLock");
        this.mPartialWakeLock.acquire();
    }

    void releaseWakeLock() {
        synchronized (this.mPartialWakeLock) {
            if (this.mPartialWakeLock.isHeld()) {
                Rlog.d(LOG_TAG, "releaseWakeLock");
                this.mPartialWakeLock.release();
            }
        }
    }

    private void fetchDtmfToneDelay(Phone phone) {
        PersistableBundle b = ((CarrierConfigManager) phone.getContext().getSystemService("carrier_config")).getConfigForSubId(phone.getSubId());
        if (b != null) {
            this.mDtmfToneDelay = b.getInt("ims_dtmf_tone_delay_int");
        }
    }

    public int getNumberPresentation() {
        return this.mNumberPresentation;
    }

    public UUSInfo getUUSInfo() {
        return this.mUusInfo;
    }

    public Connection getOrigConnection() {
        return null;
    }

    public boolean isMultiparty() {
        return this.mImsCall != null ? this.mImsCall.isMultiparty() : false;
    }

    public boolean isConferenceHost() {
        if (this.mImsCall == null) {
            return false;
        }
        return this.mImsCall.isConferenceHost();
    }

    public boolean isMemberOfPeerConference() {
        return !isConferenceHost();
    }

    public ImsCall getImsCall() {
        return this.mImsCall;
    }

    public void setImsCall(ImsCall imsCall) {
        this.mImsCall = imsCall;
    }

    public void changeParent(ImsPhoneCall parent) {
        this.mParent = parent;
    }

    public boolean update(ImsCall imsCall, State state) {
        if (state == State.ACTIVE) {
            if (imsCall.isPendingHold()) {
                Rlog.w(LOG_TAG, "update : state is ACTIVE, but call is pending hold, skipping");
                return false;
            }
            if (this.mParent.getState().isRinging() || this.mParent.getState().isDialing()) {
                onConnectedInOrOut();
            }
            if (!(this.mParent.getState().isRinging() || this.mParent == this.mOwner.mBackgroundCall)) {
                if (this.mParent == this.mOwner.mRingingCall) {
                }
            }
            this.mParent.detach(this);
            this.mParent = this.mOwner.mForegroundCall;
            this.mParent.attach(this);
        } else if (state == State.HOLDING) {
            onStartedHolding();
            if (isImsAsNormal && (this.mParent == this.mOwner.mForegroundCall || this.mParent == this.mOwner.mRingingCall)) {
                this.mParent.detach(this);
                this.mParent = this.mOwner.mBackgroundCall;
                this.mParent.attach(this);
            }
        }
        boolean updateParent = this.mParent.update(this, imsCall, state);
        boolean updateWifiState = updateWifiState();
        boolean updateAddressDisplay = updateAddressDisplay(imsCall);
        boolean updateMediaCapabilities = updateMediaCapabilities(imsCall);
        boolean updateExtras = updateExtras(imsCall);
        if (updateParent || updateWifiState || updateAddressDisplay || updateMediaCapabilities) {
            updateExtras = true;
        }
        return updateExtras;
    }

    public int getPreciseDisconnectCause() {
        return 0;
    }

    public void onDisconnectConferenceParticipant(Uri endpoint) {
        ImsCall imsCall = getImsCall();
        if (imsCall != null) {
            try {
                imsCall.removeParticipants(new String[]{endpoint.toString()});
            } catch (ImsException e) {
                Rlog.e(LOG_TAG, "onDisconnectConferenceParticipant: no session in place. Failed to disconnect endpoint = " + endpoint);
            }
        }
    }

    public void setConferenceConnectTime(long conferenceConnectTime) {
        this.mConferenceConnectTime = conferenceConnectTime;
    }

    public long getConferenceConnectTime() {
        return this.mConferenceConnectTime;
    }

    public boolean updateAddressDisplay(ImsCall imsCall) {
        if (imsCall == null) {
            return false;
        }
        boolean changed = false;
        ImsCallProfile callProfile = imsCall.getCallProfile();
        if (callProfile != null) {
            String address = callProfile.getCallExtra("oi");
            String name = callProfile.getCallExtra("cna");
            int nump = ImsCallProfile.OIRToPresentation(callProfile.getCallExtraInt("oir"));
            int namep = ImsCallProfile.OIRToPresentation(callProfile.getCallExtraInt("cnap"));
            Rlog.d(LOG_TAG, "name = xxxx nump = xxxx namep = xxxx");
            if (equalsHandlesNulls(this.mAddress, address)) {
                this.mAddress = address;
                changed = true;
            }
            if (TextUtils.isEmpty(name)) {
                if (!TextUtils.isEmpty(this.mCnapName)) {
                    this.mCnapName = "";
                    changed = true;
                }
            } else if (!name.equals(this.mCnapName)) {
                this.mCnapName = name;
                changed = true;
            }
            if (this.mNumberPresentation != nump) {
                this.mNumberPresentation = nump;
                changed = true;
            }
            if (this.mCnapNamePresentation != namep) {
                this.mCnapNamePresentation = namep;
                changed = true;
            }
        }
        return changed;
    }

    public boolean updateMediaCapabilities(ImsCall imsCall) {
        if (imsCall == null) {
            return false;
        }
        boolean changed = false;
        try {
            ImsCallProfile negotiatedCallProfile = imsCall.getCallProfile();
            if (negotiatedCallProfile != null) {
                int oldVideoState = getVideoState();
                int newVideoState = ImsCallProfile.getVideoStateFromImsCallProfile(negotiatedCallProfile);
                if (oldVideoState != newVideoState) {
                    setVideoState(newVideoState);
                    changed = true;
                }
            }
            int capabilities = getConnectionCapabilities();
            ImsCallProfile localCallProfile = imsCall.getLocalCallProfile();
            Rlog.v(LOG_TAG, "update localCallProfile=" + localCallProfile);
            if (localCallProfile != null) {
                capabilities = applyLocalCallCapabilities(localCallProfile, capabilities);
            }
            ImsCallProfile remoteCallProfile = imsCall.getRemoteCallProfile();
            Rlog.v(LOG_TAG, "update remoteCallProfile=" + remoteCallProfile);
            if (remoteCallProfile != null) {
                capabilities = applyRemoteCallCapabilities(remoteCallProfile, capabilities);
            }
            if (getConnectionCapabilities() != capabilities) {
                setConnectionCapabilities(capabilities);
                changed = true;
            }
            int newAudioQuality = getAudioQualityFromCallProfile(localCallProfile, remoteCallProfile);
            if (getAudioQuality() != newAudioQuality) {
                setAudioQuality(newAudioQuality);
                changed = true;
            }
        } catch (ImsException e) {
        }
        return changed;
    }

    public boolean updateWifiState() {
        if (this.mIsWifiStateFromExtras) {
            return false;
        }
        Rlog.d(LOG_TAG, "updateWifiState: " + this.mOwner.isVowifiEnabled());
        if (isWifi() == this.mOwner.isVowifiEnabled()) {
            return false;
        }
        setWifi(this.mOwner.isVowifiEnabled());
        return true;
    }

    private void updateWifiStateFromExtras(Bundle extras) {
        if (extras.containsKey("CallRadioTech")) {
            int radioTechnology;
            try {
                radioTechnology = Integer.parseInt(extras.getString("CallRadioTech"));
            } catch (NumberFormatException e) {
                radioTechnology = 0;
            }
            this.mIsWifiStateFromExtras = true;
            boolean isWifi = radioTechnology == 18;
            if (isWifi() != isWifi) {
                setWifi(isWifi);
            }
        }
    }

    boolean updateExtras(ImsCall imsCall) {
        boolean changed = false;
        Bundle extras = null;
        if (imsCall == null) {
            return false;
        }
        ImsCallProfile callProfile = imsCall.getCallProfile();
        if (callProfile != null) {
            extras = callProfile.mCallExtras;
        }
        if (extras == null) {
            Rlog.d(LOG_TAG, "Call profile extras are null.");
        }
        if (!areBundlesEqual(extras, this.mExtras)) {
            changed = true;
        }
        if (changed) {
            updateWifiStateFromExtras(extras);
            this.mExtras.clear();
            this.mExtras.putAll(extras);
            setConnectionExtras(this.mExtras);
        }
        return changed;
    }

    private static boolean areBundlesEqual(Bundle extras, Bundle newExtras) {
        boolean z = true;
        if (extras == null || newExtras == null) {
            if (extras != newExtras) {
                z = false;
            }
            return z;
        } else if (extras.size() != newExtras.size()) {
            return false;
        } else {
            for (String key : extras.keySet()) {
                if (key != null && !Objects.equals(extras.get(key), newExtras.get(key))) {
                    return false;
                }
            }
            return true;
        }
    }

    private int getAudioQualityFromCallProfile(ImsCallProfile localCallProfile, ImsCallProfile remoteCallProfile) {
        int i = 1;
        boolean z = false;
        if (localCallProfile == null || remoteCallProfile == null || localCallProfile.mMediaProfile == null) {
            return 1;
        }
        boolean isHighDef;
        if (localCallProfile.mMediaProfile.mAudioQuality == 2 || localCallProfile.mMediaProfile.mAudioQuality == 6) {
            if (remoteCallProfile.mRestrictCause == 0) {
                z = true;
            }
            isHighDef = z;
        } else {
            isHighDef = false;
        }
        if (isHighDef) {
            i = 2;
        }
        return i;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ImsPhoneConnection objId: ");
        sb.append(System.identityHashCode(this));
        sb.append(" telecomCallID: ");
        sb.append(getTelecomCallId());
        sb.append(" ImsCall: ");
        if (this.mImsCall == null) {
            sb.append("null");
        } else {
            sb.append(this.mImsCall);
        }
        sb.append("]");
        return sb.toString();
    }

    protected boolean isEmergency() {
        return this.mIsEmergency;
    }
}
