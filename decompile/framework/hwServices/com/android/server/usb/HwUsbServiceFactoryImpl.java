package com.android.server.usb;

import android.content.Context;
import com.android.server.usb.HwUsbServiceFactory.Factory;
import com.android.server.usb.HwUsbServiceFactory.IHwUsbDeviceManager;

public class HwUsbServiceFactoryImpl implements Factory {
    private static final String TAG = "HwUsbServiceFactoryImpl";

    public static class HwUsbDeviceManagerImpl implements IHwUsbDeviceManager {
        public UsbDeviceManager getInstance(Context context, UsbAlsaManager alsaManager) {
            return new HwUsbDeviceManager(context, alsaManager);
        }
    }

    public IHwUsbDeviceManager getHuaweiUsbDeviceManager() {
        return new HwUsbDeviceManagerImpl();
    }
}
