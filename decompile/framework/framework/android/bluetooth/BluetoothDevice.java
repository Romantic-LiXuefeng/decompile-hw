package android.bluetooth;

import android.bluetooth.IBluetoothManagerCallback.Stub;
import android.content.Context;
import android.net.ProxyInfo;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.UUID;

public final class BluetoothDevice implements Parcelable {
    public static final int ACCESS_ALLOWED = 1;
    public static final int ACCESS_REJECTED = 2;
    public static final int ACCESS_UNKNOWN = 0;
    public static final String ACTION_ACL_CONNECTED = "android.bluetooth.device.action.ACL_CONNECTED";
    public static final String ACTION_ACL_DISCONNECTED = "android.bluetooth.device.action.ACL_DISCONNECTED";
    public static final String ACTION_ACL_DISCONNECT_REQUESTED = "android.bluetooth.device.action.ACL_DISCONNECT_REQUESTED";
    public static final String ACTION_ALIAS_CHANGED = "android.bluetooth.device.action.ALIAS_CHANGED";
    public static final String ACTION_BOND_STATE_CHANGED = "android.bluetooth.device.action.BOND_STATE_CHANGED";
    public static final String ACTION_CLASS_CHANGED = "android.bluetooth.device.action.CLASS_CHANGED";
    public static final String ACTION_CONNECTION_ACCESS_CANCEL = "android.bluetooth.device.action.CONNECTION_ACCESS_CANCEL";
    public static final String ACTION_CONNECTION_ACCESS_REPLY = "android.bluetooth.device.action.CONNECTION_ACCESS_REPLY";
    public static final String ACTION_CONNECTION_ACCESS_REQUEST = "android.bluetooth.device.action.CONNECTION_ACCESS_REQUEST";
    public static final String ACTION_DISAPPEARED = "android.bluetooth.device.action.DISAPPEARED";
    public static final String ACTION_FOUND = "android.bluetooth.device.action.FOUND";
    public static final String ACTION_MAS_INSTANCE = "android.bluetooth.device.action.MAS_INSTANCE";
    public static final String ACTION_NAME_CHANGED = "android.bluetooth.device.action.NAME_CHANGED";
    public static final String ACTION_NAME_FAILED = "android.bluetooth.device.action.NAME_FAILED";
    public static final String ACTION_PAIRING_CANCEL = "android.bluetooth.device.action.PAIRING_CANCEL";
    public static final String ACTION_PAIRING_REQUEST = "android.bluetooth.device.action.PAIRING_REQUEST";
    public static final String ACTION_SDP_RECORD = "android.bluetooth.device.action.SDP_RECORD";
    public static final String ACTION_UUID = "android.bluetooth.device.action.UUID";
    public static final int BOND_BONDED = 12;
    public static final int BOND_BONDING = 11;
    public static final int BOND_NONE = 10;
    public static final int BOND_SUCCESS = 0;
    public static final int CONNECTION_ACCESS_NO = 2;
    public static final int CONNECTION_ACCESS_YES = 1;
    private static final int CONNECTION_STATE_CONNECTED = 1;
    private static final int CONNECTION_STATE_DISCONNECTED = 0;
    private static final int CONNECTION_STATE_ENCRYPTED_BREDR = 2;
    private static final int CONNECTION_STATE_ENCRYPTED_LE = 4;
    public static final Creator<BluetoothDevice> CREATOR = new Creator<BluetoothDevice>() {
        public BluetoothDevice createFromParcel(Parcel in) {
            return new BluetoothDevice(in.readString());
        }

        public BluetoothDevice[] newArray(int size) {
            return new BluetoothDevice[size];
        }
    };
    private static final boolean DBG = false;
    public static final int DEVICE_TYPE_CLASSIC = 1;
    public static final int DEVICE_TYPE_DUAL = 3;
    public static final int DEVICE_TYPE_LE = 2;
    public static final int DEVICE_TYPE_UNKNOWN = 0;
    public static final int ERROR = Integer.MIN_VALUE;
    public static final String EXTRA_ACCESS_REQUEST_TYPE = "android.bluetooth.device.extra.ACCESS_REQUEST_TYPE";
    public static final String EXTRA_ALWAYS_ALLOWED = "android.bluetooth.device.extra.ALWAYS_ALLOWED";
    public static final String EXTRA_BOND_STATE = "android.bluetooth.device.extra.BOND_STATE";
    public static final String EXTRA_CLASS = "android.bluetooth.device.extra.CLASS";
    public static final String EXTRA_CLASS_NAME = "android.bluetooth.device.extra.CLASS_NAME";
    public static final String EXTRA_CONNECTION_ACCESS_RESULT = "android.bluetooth.device.extra.CONNECTION_ACCESS_RESULT";
    public static final String EXTRA_DEVICE = "android.bluetooth.device.extra.DEVICE";
    public static final String EXTRA_MAS_INSTANCE = "android.bluetooth.device.extra.MAS_INSTANCE";
    public static final String EXTRA_NAME = "android.bluetooth.device.extra.NAME";
    public static final String EXTRA_PACKAGE_NAME = "android.bluetooth.device.extra.PACKAGE_NAME";
    public static final String EXTRA_PAIRING_KEY = "android.bluetooth.device.extra.PAIRING_KEY";
    public static final String EXTRA_PAIRING_VARIANT = "android.bluetooth.device.extra.PAIRING_VARIANT";
    public static final String EXTRA_PREVIOUS_BOND_STATE = "android.bluetooth.device.extra.PREVIOUS_BOND_STATE";
    public static final String EXTRA_REASON = "android.bluetooth.device.extra.REASON";
    public static final String EXTRA_RSSI = "android.bluetooth.device.extra.RSSI";
    public static final String EXTRA_SDP_RECORD = "android.bluetooth.device.extra.SDP_RECORD";
    public static final String EXTRA_SDP_SEARCH_STATUS = "android.bluetooth.device.extra.SDP_SEARCH_STATUS";
    public static final String EXTRA_UUID = "android.bluetooth.device.extra.UUID";
    public static final int PAIRING_VARIANT_CONSENT = 3;
    public static final int PAIRING_VARIANT_DISPLAY_PASSKEY = 4;
    public static final int PAIRING_VARIANT_DISPLAY_PIN = 5;
    public static final int PAIRING_VARIANT_OOB_CONSENT = 6;
    public static final int PAIRING_VARIANT_PASSKEY = 1;
    public static final int PAIRING_VARIANT_PASSKEY_CONFIRMATION = 2;
    public static final int PAIRING_VARIANT_PIN = 0;
    public static final int PAIRING_VARIANT_PIN_16_DIGITS = 7;
    public static final int REQUEST_TYPE_MESSAGE_ACCESS = 3;
    public static final int REQUEST_TYPE_PHONEBOOK_ACCESS = 2;
    public static final int REQUEST_TYPE_PROFILE_CONNECTION = 1;
    public static final int REQUEST_TYPE_SIM_ACCESS = 4;
    private static final String TAG = "BluetoothDevice";
    public static final int TRANSPORT_AUTO = 0;
    public static final int TRANSPORT_BREDR = 1;
    public static final int TRANSPORT_LE = 2;
    public static final int UNBOND_REASON_AUTH_CANCELED = 3;
    public static final int UNBOND_REASON_AUTH_FAILED = 1;
    public static final int UNBOND_REASON_AUTH_REJECTED = 2;
    public static final int UNBOND_REASON_AUTH_TIMEOUT = 6;
    public static final int UNBOND_REASON_DISCOVERY_IN_PROGRESS = 5;
    public static final int UNBOND_REASON_REMOTE_AUTH_CANCELED = 8;
    public static final int UNBOND_REASON_REMOTE_DEVICE_DOWN = 4;
    public static final int UNBOND_REASON_REMOVED = 9;
    public static final int UNBOND_REASON_REPEATED_ATTEMPTS = 7;
    static IBluetoothManagerCallback mStateChangeCallback = new Stub() {
        public void onBluetoothServiceUp(IBluetooth bluetoothService) throws RemoteException {
            synchronized (BluetoothDevice.class) {
                if (BluetoothDevice.sService == null) {
                    BluetoothDevice.sService = bluetoothService;
                }
            }
        }

        public void onBluetoothServiceDown() throws RemoteException {
            synchronized (BluetoothDevice.class) {
                BluetoothDevice.sService = null;
            }
        }

        public void onBrEdrDown() {
        }
    };
    private static IBluetooth sService;
    private final String mAddress;

    static IBluetooth getService() {
        synchronized (BluetoothDevice.class) {
            if (sService == null) {
                sService = BluetoothAdapter.getDefaultAdapter().getBluetoothService(mStateChangeCallback);
            }
        }
        return sService;
    }

    BluetoothDevice(String address) {
        getService();
        if (BluetoothAdapter.checkBluetoothAddress(address)) {
            this.mAddress = address;
            return;
        }
        throw new IllegalArgumentException(address + " is not a valid Bluetooth address");
    }

    public boolean equals(Object o) {
        if (o instanceof BluetoothDevice) {
            return this.mAddress.equals(((BluetoothDevice) o).getAddress());
        }
        return false;
    }

    public int hashCode() {
        return this.mAddress.hashCode();
    }

    public String toString() {
        return this.mAddress;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeString(this.mAddress);
    }

    public String getAddress() {
        return this.mAddress;
    }

    public String getName() {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot get Remote Device name");
            return null;
        }
        try {
            return sService.getRemoteName(this);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return null;
        }
    }

    public int getType() {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot get Remote Device type");
            return 0;
        }
        try {
            return sService.getRemoteType(this);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return 0;
        }
    }

    public String getAlias() {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot get Remote Device Alias");
            return null;
        }
        try {
            return sService.getRemoteAlias(this);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return null;
        }
    }

    public boolean setAlias(String alias) {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot set Remote Device name");
            return false;
        }
        try {
            return sService.setRemoteAlias(this, alias);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return false;
        }
    }

    public String getAliasName() {
        String name = getAlias();
        if (name == null) {
            return getName();
        }
        return name;
    }

    public boolean createBond() {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot create bond to Remote Device");
            return false;
        }
        try {
            Log.i(TAG, "createBond() for device " + getAddress() + " called by pid: " + Process.myPid() + " tid: " + Process.myTid());
            return sService.createBond(this, 0);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return false;
        }
    }

    public boolean createBond(int transport) {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot create bond to Remote Device");
            return false;
        } else if (transport < 0 || transport > 2) {
            throw new IllegalArgumentException(transport + " is not a valid Bluetooth transport");
        } else {
            try {
                Log.i(TAG, "createBond() for device " + getAddress() + " called by pid: " + Process.myPid() + " tid: " + Process.myTid());
                return sService.createBond(this, transport);
            } catch (RemoteException e) {
                Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
                return false;
            }
        }
    }

    public boolean createBondOutOfBand(int transport, OobData oobData) {
        try {
            return sService.createBondOutOfBand(this, transport, oobData);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return false;
        }
    }

    public boolean setDeviceOutOfBandData(byte[] hash, byte[] randomizer) {
        return false;
    }

    public boolean cancelBondProcess() {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot cancel Remote Device bond");
            return false;
        }
        try {
            Log.i(TAG, "cancelBondProcess() for device " + getAddress() + " called by pid: " + Process.myPid() + " tid: " + Process.myTid());
            return sService.cancelBondProcess(this);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return false;
        }
    }

    public boolean removeBond() {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot remove Remote Device bond");
            return false;
        }
        try {
            Log.i(TAG, "removeBond() for device " + getAddress() + " called by pid: " + Process.myPid() + " tid: " + Process.myTid());
            return sService.removeBond(this);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return false;
        }
    }

    public int getBondState() {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot get bond state");
            return 10;
        }
        try {
            return sService.getBondState(this);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return 10;
        } catch (NullPointerException npe) {
            Log.e(TAG, "NullPointerException for getBondState() of device (" + getAddress() + ")", npe);
            return 10;
        }
    }

    public boolean isConnected() {
        boolean z = false;
        if (sService == null) {
            return false;
        }
        try {
            if (sService.getConnectionState(this) != 0) {
                z = true;
            }
            return z;
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return false;
        }
    }

    public boolean isEncrypted() {
        boolean z = true;
        if (sService == null) {
            return false;
        }
        try {
            if (sService.getConnectionState(this) <= 1) {
                z = false;
            }
            return z;
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return false;
        }
    }

    public BluetoothClass getBluetoothClass() {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot get Bluetooth Class");
            return null;
        }
        try {
            int classInt = sService.getRemoteClass(this);
            if (classInt == -16777216) {
                return null;
            }
            return new BluetoothClass(classInt);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return null;
        }
    }

    public ParcelUuid[] getUuids() {
        if (sService == null || !isBluetoothEnabled()) {
            Log.e(TAG, "BT not enabled. Cannot get remote device Uuids");
            return null;
        }
        try {
            return sService.getRemoteUuids(this);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return null;
        }
    }

    public boolean fetchUuidsWithSdp() {
        IBluetooth service = sService;
        if (service == null || !isBluetoothEnabled()) {
            Log.e(TAG, "BT not enabled. Cannot fetchUuidsWithSdp");
            return false;
        }
        try {
            return service.fetchRemoteUuids(this);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return false;
        }
    }

    public boolean sdpSearch(ParcelUuid uuid) {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot query remote device sdp records");
            return false;
        }
        try {
            return sService.sdpSearch(this, uuid);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return false;
        }
    }

    public boolean setPin(byte[] pin) {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot set Remote Device pin");
            return false;
        }
        try {
            return sService.setPin(this, true, pin.length, pin);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return false;
        }
    }

    public boolean setPasskey(int passkey) {
        return false;
    }

    public boolean setPairingConfirmation(boolean confirm) {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot set pairing confirmation");
            return false;
        }
        try {
            return sService.setPairingConfirmation(this, confirm);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return false;
        }
    }

    public boolean setRemoteOutOfBandData() {
        return false;
    }

    public boolean cancelPairingUserInput() {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot create pairing user input");
            return false;
        }
        try {
            return sService.cancelBondProcess(this);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return false;
        }
    }

    public boolean isBluetoothDock() {
        return false;
    }

    boolean isBluetoothEnabled() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            return false;
        }
        return true;
    }

    public int getPhonebookAccessPermission() {
        if (sService == null) {
            return 0;
        }
        try {
            return sService.getPhonebookAccessPermission(this);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return 0;
        }
    }

    public boolean setPhonebookAccessPermission(int value) {
        if (sService == null) {
            return false;
        }
        try {
            return sService.setPhonebookAccessPermission(this, value);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return false;
        }
    }

    public int getMessageAccessPermission() {
        if (sService == null) {
            return 0;
        }
        try {
            return sService.getMessageAccessPermission(this);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return 0;
        }
    }

    public boolean setMessageAccessPermission(int value) {
        if (sService == null) {
            return false;
        }
        try {
            return sService.setMessageAccessPermission(this, value);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return false;
        }
    }

    public int getSimAccessPermission() {
        if (sService == null) {
            return 0;
        }
        try {
            return sService.getSimAccessPermission(this);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return 0;
        }
    }

    public boolean setSimAccessPermission(int value) {
        if (sService == null) {
            return false;
        }
        try {
            return sService.setSimAccessPermission(this, value);
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return false;
        }
    }

    public BluetoothSocket createRfcommSocket(int channel) throws IOException {
        if (isBluetoothEnabled()) {
            return new BluetoothSocket(1, -1, true, true, this, channel, null);
        }
        Log.e(TAG, "Bluetooth is not enabled");
        throw new IOException();
    }

    public BluetoothSocket createL2capSocket(int channel) throws IOException {
        return new BluetoothSocket(3, -1, true, true, this, channel, null);
    }

    public BluetoothSocket createRfcommSocketToServiceRecord(UUID uuid) throws IOException {
        if (isBluetoothEnabled()) {
            return new BluetoothSocket(1, -1, true, true, this, -1, new ParcelUuid(uuid));
        }
        Log.e(TAG, "Bluetooth is not enabled");
        throw new IOException();
    }

    public BluetoothSocket createInsecureRfcommSocketToServiceRecord(UUID uuid) throws IOException {
        if (isBluetoothEnabled()) {
            return new BluetoothSocket(1, -1, false, false, this, -1, new ParcelUuid(uuid));
        }
        Log.e(TAG, "Bluetooth is not enabled");
        throw new IOException();
    }

    public BluetoothSocket createInsecureRfcommSocket(int port) throws IOException {
        if (isBluetoothEnabled()) {
            return new BluetoothSocket(1, -1, false, false, this, port, null);
        }
        Log.e(TAG, "Bluetooth is not enabled");
        throw new IOException();
    }

    public BluetoothSocket createScoSocket() throws IOException {
        if (isBluetoothEnabled()) {
            return new BluetoothSocket(2, -1, true, true, this, -1, null);
        }
        Log.e(TAG, "Bluetooth is not enabled");
        throw new IOException();
    }

    public static byte[] convertPinToBytes(String pin) {
        if (pin == null) {
            return null;
        }
        try {
            byte[] pinBytes = pin.getBytes("UTF-8");
            if (pinBytes.length <= 0 || pinBytes.length > 16) {
                return null;
            }
            return pinBytes;
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "UTF-8 not supported?!?");
            return null;
        }
    }

    public BluetoothGatt connectGatt(Context context, boolean autoConnect, BluetoothGattCallback callback) {
        Log.i(TAG, "connectGatt");
        return connectGatt(context, autoConnect, callback, 0);
    }

    public BluetoothGatt connectGatt(Context context, boolean autoConnect, BluetoothGattCallback callback, int transport) {
        try {
            IBluetoothGatt iGatt = BluetoothAdapter.getDefaultAdapter().getBluetoothManager().getBluetoothGatt();
            if (iGatt == null) {
                return null;
            }
            BluetoothGatt gatt = new BluetoothGatt(context, iGatt, this, transport);
            gatt.connect(Boolean.valueOf(autoConnect), callback);
            return gatt;
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return null;
        }
    }

    public BluetoothGatt fastConnectGatt(Context context, boolean autoConnect, BluetoothGattCallback callback, int transport) {
        try {
            IBluetoothGatt iGatt = BluetoothAdapter.getDefaultAdapter().getBluetoothManager().getBluetoothGatt();
            if (iGatt == null) {
                return null;
            }
            Log.i(TAG, "fastConnectGatt: " + transport);
            BluetoothGatt gatt = new BluetoothGatt(context, iGatt, this, transport);
            gatt.fastConnect(Boolean.valueOf(autoConnect), callback);
            return gatt;
        } catch (RemoteException e) {
            Log.e(TAG, ProxyInfo.LOCAL_EXCL_LIST, e);
            return null;
        }
    }
}
