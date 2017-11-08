package com.android.server.rms.io;

import android.util.Log;
import com.android.server.rms.iaware.hiber.constant.AppHibernateCst;
import com.android.server.rms.utils.Utils;
import java.util.Hashtable;
import java.util.List;

public class KernelIOStats {
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CID_FILE_PATH = "/sys/block/mmcblk0/device/cid";
    private static final int ERROR_LIFE_TIME = -1;
    private static final long ERROR_WRITE_BYTES = -1;
    private static final int FILE_BUFFER_SIZE = 1024;
    public static final int HEALTH_TYPE_A = 0;
    public static final int HEALTH_TYPE_B = 1;
    public static final int HEALTH_TYPE_EOL = 2;
    private static final String SPLIT_UID = ",";
    private static final String TAG = "IO.KernelIOStats";
    private static final String UID_ADD_PATH = "uid_iomonitor_list";
    private static final String UID_MONITOR_BASE_PATH = "/proc/uid_iostats/";
    private static final String UID_REMOVE_PATH = "remove_uid_list";
    private static final String UID_SHOW_DATAS_SPLIT = "\n";
    private static final String UID_SHOW_PATH = "show_uid_iostats";
    private static final String UID_SHOW_READ_WRITE_SPLIT = " ";
    private static final String UID_SHOW_UID_STATS_SPLIT = ":";
    private static long mHalObject;

    static native long halOpen();

    static native String native_get_healthinfo(long j, int i);

    static native String native_get_lifetime(long j);

    static native String native_read_file(String str);

    static native int native_write_file(String str, String str2, int i);

    static {
        mHalObject = 0;
        try {
            System.loadLibrary("iostats_jni");
            mHalObject = halOpen();
            if (mHalObject == 0) {
                Log.e(TAG, "IO flowmeter Module, HAL not loaded");
            }
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "iostats_jni not found");
        } catch (Exception e2) {
            Log.e(TAG, "an Exception occurs:" + e2.getMessage());
        }
    }

    public static IOStatsCollection readUidIOStatsFromKernel(Hashtable<Integer, String> uidPkgTable) {
        IOStatsCollection ioStatsResult = new IOStatsCollection();
        if (uidPkgTable == null || uidPkgTable.size() == 0) {
            Log.e(TAG, "readUidIOStatsFromKernel:the uidPkgTable is empty");
            return ioStatsResult;
        }
        try {
            String ioStatsBuffer = native_read_file("/proc/uid_iostats/show_uid_iostats");
            if (ioStatsBuffer == null || ioStatsBuffer.length() == 0) {
                Log.e(TAG, "readUidIOStatsFromKernel io_stats file is empty");
                return ioStatsResult;
            }
            if (Utils.DEBUG) {
                Log.d(TAG, "readUidIOStatsFromKernel,ioStatsBuffer:" + ioStatsBuffer);
            }
            recordHistoryByKernelNodeInfor(ioStatsBuffer, uidPkgTable, ioStatsResult);
            return ioStatsResult;
        } catch (RuntimeException ex) {
            Log.e(TAG, "readUidIOStats:an RuntimeException occurs:" + ex.getMessage());
        } catch (Exception ex2) {
            Log.e(TAG, "readUidIOStats:an Exception occurs:" + ex2.getMessage());
        }
    }

    private static void recordHistoryByKernelNodeInfor(String ioStatsBuffer, Hashtable<Integer, String> uidPkgTable, IOStatsCollection ioStatsResult) {
        long currentTime = Utils.getShortDateFormatValue(System.currentTimeMillis());
        for (String ioStats : ioStatsBuffer.split(UID_SHOW_DATAS_SPLIT)) {
            String[] uidSplitArray = ioStats.split(UID_SHOW_UID_STATS_SPLIT);
            if (uidSplitArray.length < 2) {
                Log.e(TAG, "uidSplitArray's length is invalid:" + ioStats);
            } else {
                String[] readWriteArray = uidSplitArray[1].trim().split(UID_SHOW_READ_WRITE_SPLIT);
                if (readWriteArray.length < 2) {
                    Log.e(TAG, "readWriteArray's length is invalid :" + uidSplitArray[1]);
                } else {
                    int uid = Integer.parseInt(uidSplitArray[0]);
                    long readNum = Long.parseLong(readWriteArray[0]);
                    long writeNum = Long.parseLong(readWriteArray[1]);
                    if (readNum != 0 || writeNum != 0) {
                        ioStatsResult.recordHistory(new IOStatsHistory(uid, (String) uidPkgTable.get(Integer.valueOf(uid)), currentTime, readNum, writeNum));
                    }
                }
            }
        }
    }

    public static long getTotalWrittenBytes() {
        long totalWrittenBytes = -1;
        try {
            String totalWriteBytesValue = native_get_lifetime(mHalObject);
            if (totalWriteBytesValue == null) {
                Log.e(TAG, "getTotalWrittenBytes,fail to get the lifetime");
                return -1;
            }
            totalWrittenBytes = Long.parseLong(totalWriteBytesValue.trim());
            if (Utils.DEBUG) {
                Log.d(TAG, "getTotalWrittenBytes:" + totalWrittenBytes);
            }
            return totalWrittenBytes;
        } catch (Exception ex) {
            Log.e(TAG, "getTotalWrittenBytes Exception occurs:" + ex.getMessage());
        }
    }

    public static int getHealthInformation(int healthType) {
        try {
            String healthValue = native_get_healthinfo(mHalObject, healthType);
            if (healthValue == null) {
                Log.e(TAG, "getHealthInformation,fail to get the healthinfo");
                return -1;
            }
            String healthTAG = AppHibernateCst.INVALID_PKG;
            switch (healthType) {
                case 0:
                    healthTAG = "health_type_a";
                    break;
                case 1:
                    healthTAG = "health_type_b";
                    break;
                case 2:
                    healthTAG = "health_type_eol";
                    break;
            }
            Log.i(TAG, "getHealthInformation," + healthTAG + " healthValue:" + healthValue);
            return Integer.decode(healthValue.trim()).intValue();
        } catch (RuntimeException ex) {
            Log.e(TAG, "an RuntimeException occurs:" + ex.getMessage());
            return -1;
        } catch (Exception e) {
            Log.e(TAG, "an Exception occurs:" + e.getMessage());
            return -1;
        }
    }

    public static String getCIDNodeInformation() {
        String totalWriteBytes = AppHibernateCst.INVALID_PKG;
        try {
            totalWriteBytes = native_read_file(CID_FILE_PATH);
        } catch (RuntimeException e) {
            Log.e(TAG, "getCIDNodeInformation,the RuntimeException occurs");
        } catch (Exception e2) {
            Log.e(TAG, "getCIDNodeInformation Exception occurs");
        }
        return totalWriteBytes;
    }

    public static void writeUidList(List<Integer> removeUidList, List<Integer> addUidList) {
        if ((removeUidList == null || removeUidList.size() == 0) && (addUidList == null || addUidList.size() == 0)) {
            Log.e(TAG, "writeUidList,both the removeUidList and addUidList are empty");
            return;
        }
        String removeUidConnection = convert(removeUidList);
        String addUidConnection = convert(addUidList);
        if (Utils.DEBUG) {
            Log.d(TAG, String.format("writeUidList,removeUidConnection:%s,addUidConnection:%s", new Object[]{removeUidConnection, addUidConnection}));
        }
        if (removeUidConnection != null) {
            writeToUidMonitorNode(removeUidConnection, "/proc/uid_iostats/remove_uid_list");
        }
        if (addUidConnection != null) {
            writeToUidMonitorNode(addUidConnection, "/proc/uid_iostats/uid_iomonitor_list");
        }
    }

    private static String convert(List<Integer> uidList) {
        if (uidList == null || uidList.size() == 0) {
            if (Utils.DEBUG) {
                Log.d(TAG, "convert, the uidList is empty");
            }
            return null;
        }
        StringBuilder resultBuilder = new StringBuilder();
        for (Integer intValue : uidList) {
            resultBuilder.append(intValue.intValue()).append(SPLIT_UID);
        }
        return resultBuilder.toString().substring(0, resultBuilder.length() - 1);
    }

    private static void writeToUidMonitorNode(String uidConnection, String path) {
        try {
            if (Utils.DEBUG) {
                Log.d(TAG, "writeToUidMonitorNode,filePath:" + path);
            }
            if (uidConnection == null || uidConnection.length() == 0) {
                Log.e(TAG, "writeToUidMonitorNode,the uidConnection is empty");
            } else {
                native_write_file(path, uidConnection, uidConnection.length());
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "fail to writeToUidMonitorNode:the RuntimeException");
        } catch (Exception e2) {
            Log.e(TAG, "fail to writeToUidMonitorNode:the other exception");
        }
    }
}
