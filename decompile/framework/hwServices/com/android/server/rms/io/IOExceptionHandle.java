package com.android.server.rms.io;

import android.util.Log;
import android.util.SparseArray;
import com.android.server.rms.record.JankLogProxy;
import com.android.server.rms.record.ResourceUtils;
import com.android.server.rms.utils.Interrupt;
import com.android.server.rms.utils.Utils;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class IOExceptionHandle {
    private static final int BIG_LOG_FACTOR_APP_VALUE = 1;
    private static final int BIG_LOG_FACTOR_DEV_RSV_BLK = 250;
    private static final int BIG_LOG_FACTOR_LIFE_VALUE = 125;
    private static final int BIG_LOG_FACTOR_TOTAL_WRITE_TYPES = 10;
    private static final String BIG_LOG_PKG_NAME_LIFE_A = "huawei.io.device.type_a";
    private static final String BIG_LOG_PKG_NAME_LIFE_B = "huawei.io.device.type_b";
    private static final String BIG_LOG_PKG_NAME_WRITE = "huawei.io.write.total";
    private static final String BIG_LOG_PKG_NAME_WRITE_EOL = "huawei.io.device.rsvblk";
    public static final long BYTES_SIZE_1G = 1073741824;
    private static final String DEVICE_STATUS_PATH = "log";
    private static final int EOL_MAX_VALUE = 1;
    public static final int EXCEPTION_TYPE_APP_EXCEPTION = 6;
    public static final int EXCEPTION_TYPE_DEVICE = 5;
    public static final int EXCEPTION_TYPE_LIFE_TIME_A = 2;
    public static final int EXCEPTION_TYPE_LIFE_TIME_B = 3;
    public static final int EXCEPTION_TYPE_RSV_BLK = 4;
    public static final int EXCEPTION_TYPE_WRITE_TYPES = 1;
    private static final String HEALTH_FILE_PREFIX_A = "emmc_health_a";
    private static final String HEALTH_FILE_PREFIX_B = "emmc_health_b";
    public static final int IO_EXCEPTION_DAYS_LIMIT = 7;
    private static final String TAG = "IO.IOExceptionHandle";
    private static final long TIME_VALUES_MONTH = 2592000000L;
    private static final String WRITE_BYTES_FILE_PREFIX = "total_writebytes";
    private static final int WRITTEN_BYTES_EXCEPTION_DAYS_LIMIT = 7;
    private static final long WRITTEN_BYTES_EXCEPTION_LIMIT = 12884901888L;
    private static final int WRITTEN_EXP_APP_NUM = 10;
    private IOFileRotator mFileLifeTimeA = null;
    private IOFileRotator mFileLifeTimeB = null;
    private IOFileRotator mFileWriteBytes = null;
    private IOStatsService mIOStatsService = null;
    private final Interrupt mInterrupt = new Interrupt();
    private final JankLogProxy mJankLogProxy = JankLogProxy.getInstance();
    private LifeTimeCollection mLifeTimeCollection = new LifeTimeCollection();
    private LifeTimeRewriter mLifeTimeRewriter = null;
    private WriteBytesCollection mWriteBytesList = new WriteBytesCollection();
    private TotalWrittenRewriter mWriteBytesRewriter = null;

    static class AppExceptionComparator implements Comparator<AppExceptionData>, Serializable {
        private static final long serialVersionUID = 1;

        AppExceptionComparator() {
        }

        public int compare(AppExceptionData appExp1, AppExceptionData appExp2) {
            return Long.compare(appExp1.mTotalWrittenBytes, appExp2.mTotalWrittenBytes) * -1;
        }
    }

    static class AppExceptionData {
        public String mPkgName;
        public long mStartTime;
        public long mTotalWrittenBytes;

        AppExceptionData() {
        }
    }

    interface CheckHandler {
        ExceptionData check();
    }

    static class CheckAppHandler implements CheckHandler {
        private IOExceptionHandle mIOExceptionHandle = null;

        public CheckAppHandler(IOExceptionHandle iOExceptionHandle) {
            this.mIOExceptionHandle = iOExceptionHandle;
        }

        public ExceptionData check() {
            List<AppExceptionData> appExceptionList = this.mIOExceptionHandle.checkExceptionOnApp();
            if (appExceptionList == null || appExceptionList.size() <= 0) {
                return null;
            }
            return new ExceptionData(6, appExceptionList);
        }
    }

    static class CheckDeviceHandler implements CheckHandler {
        private IOExceptionHandle mIOExceptionHandle = null;

        public CheckDeviceHandler(IOExceptionHandle iOExceptionHandle) {
            this.mIOExceptionHandle = iOExceptionHandle;
        }

        public ExceptionData check() {
            int[] isExceptionArray = this.mIOExceptionHandle.checkExceptionOnDevice();
            if (isExceptionArray.length > 0) {
                return new ExceptionData(5, isExceptionArray);
            }
            return null;
        }
    }

    static class CheckTotalWriteBytesHandler implements CheckHandler {
        private IOExceptionHandle mIOExceptionHandle = null;

        public CheckTotalWriteBytesHandler(IOExceptionHandle iOExceptionHandle) {
            this.mIOExceptionHandle = iOExceptionHandle;
        }

        public ExceptionData check() {
            long[] resultArray = this.mIOExceptionHandle.checkExceptionOnTotalWrittenBytes();
            if (resultArray.length != 0) {
                return new ExceptionData(1, resultArray);
            }
            return null;
        }
    }

    static class ExceptionData {
        public Object mData;
        public int mExceptionType;

        public ExceptionData(int exceptionType, Object data) {
            this.mExceptionType = exceptionType;
            this.mData = data;
        }
    }

    static class LifeTimeData {
        public int mLifeTime;
        public long mTime;

        public LifeTimeData(long time, int lifeTime) {
            this.mTime = time;
            this.mLifeTime = lifeTime;
        }
    }

    static class WriteBytesData {
        public long mTime;
        public long mWriteBytes;

        public WriteBytesData(long time, long writeBytes) {
            this.mTime = time;
            this.mWriteBytes = writeBytes;
        }
    }

    public IOExceptionHandle(IOStatsService iOStatsService) {
        this.mIOStatsService = iOStatsService;
        this.mFileLifeTimeA = new IOFileRotator(new File(DEVICE_STATUS_PATH), HEALTH_FILE_PREFIX_A, Long.MAX_VALUE, Long.MAX_VALUE);
        this.mFileLifeTimeB = new IOFileRotator(new File(DEVICE_STATUS_PATH), HEALTH_FILE_PREFIX_B, Long.MAX_VALUE, Long.MAX_VALUE);
        this.mLifeTimeRewriter = new LifeTimeRewriter(this.mLifeTimeCollection);
        this.mFileWriteBytes = new IOFileRotator(new File(DEVICE_STATUS_PATH), WRITE_BYTES_FILE_PREFIX, Long.MAX_VALUE, Long.MAX_VALUE);
        this.mWriteBytesRewriter = new TotalWrittenRewriter(this.mWriteBytesList);
        this.mInterrupt.reset();
    }

    public void saveTotalWrittenBytes(List<WriteBytesData> writtenPendingList) {
        this.mWriteBytesList.reset();
        if (writtenPendingList == null || writtenPendingList.size() == 0) {
            Log.e(TAG, "saveTotalWrittenBytes,the writtenPendingList is empty");
            return;
        }
        try {
            this.mWriteBytesList.addAll(writtenPendingList);
            this.mFileWriteBytes.rewriteActive(this.mWriteBytesRewriter, System.currentTimeMillis());
        } catch (IOException ex) {
            Log.e(TAG, "saveLifeTime,an IOException occurs:" + ex.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "saveLifeTime,an Exception occurs:" + e.getMessage());
        }
    }

    public void saveLifeTime(List<LifeTimeData> lifeTimeDataList, int type) {
        this.mLifeTimeCollection.reset();
        if (lifeTimeDataList == null || lifeTimeDataList.size() == 0) {
            Log.e(TAG, "saveLifeTime,the lifeTimeDataList is empty");
            return;
        }
        IOFileRotator fileRotator = null;
        if (type == 2) {
            try {
                fileRotator = this.mFileLifeTimeA;
            } catch (IOException ex) {
                Log.e(TAG, "saveLifeTime,an IOException occurs:" + ex.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "saveLifeTime,an Exception occurs:" + e.getMessage());
            }
        } else if (type == 3) {
            fileRotator = this.mFileLifeTimeB;
        }
        if (fileRotator == null) {
            Log.e(TAG, "saveLifeTime,type:" + type + " is invalid");
            return;
        }
        this.mLifeTimeCollection.addAll(lifeTimeDataList);
        fileRotator.rewriteActive(this.mLifeTimeRewriter, System.currentTimeMillis());
    }

    public long getLastWrittenBytes() {
        List<WriteBytesData> dataList = getAllWrittenBytes();
        if (dataList.size() > 0) {
            return ((WriteBytesData) dataList.get(dataList.size() - 1)).mWriteBytes;
        }
        return 0;
    }

    public List<WriteBytesData> getAllWrittenBytes() {
        this.mWriteBytesList.reset();
        try {
            this.mFileWriteBytes.readMatching(this.mWriteBytesList, Long.MIN_VALUE, Long.MAX_VALUE);
        } catch (IOException ex) {
            Log.e(TAG, "getAllWrittenBytes,an IOException occurs:" + ex.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "getAllWrittenBytes,an Exception occurs:" + e.getMessage());
        }
        List<WriteBytesData> entryReadList = this.mWriteBytesList.getAllDataList();
        if (entryReadList.size() == 0) {
            Log.e(TAG, "getAllWrittenBytes ,the content of the total_writebytes file is empty");
            return new ArrayList();
        }
        List<WriteBytesData> entryList = new ArrayList();
        for (WriteBytesData data : entryReadList) {
            if (entryList.size() == 0) {
                entryList.add(new WriteBytesData(data.mTime, data.mWriteBytes));
            } else {
                WriteBytesData dataSaved = (WriteBytesData) entryList.get(entryList.size() - 1);
                if (dataSaved.mTime == data.mTime) {
                    dataSaved.mWriteBytes += data.mWriteBytes;
                } else {
                    entryList.add(new WriteBytesData(data.mTime, data.mWriteBytes));
                }
            }
        }
        return entryList;
    }

    private long[] checkExceptionOnTotalWrittenBytes() {
        Log.i(TAG, "do the checking on the totalWriteBytes");
        List<WriteBytesData> writtenBytes = getAllWrittenBytes();
        if (writtenBytes.size() < 7) {
            Log.i(TAG, "checkExceptionOnTotalWrittenBytes,the number of the writtenBytes is too small");
            return new long[0];
        }
        int count = writtenBytes.size() - 7;
        long[] resultArray = new long[0];
        for (int index = 0; index <= count; index++) {
            resultArray = checkIfMuchWritting(index, writtenBytes);
            if (resultArray.length > 0) {
                break;
            }
        }
        if (resultArray.length > 1) {
            Log.i(TAG, "An Exception about the total writeBytes occurs,time:" + resultArray[1] + ",totalWriteBytes:" + resultArray[2]);
        }
        if (resultArray.length == 0) {
            Log.i(TAG, "no error on the totalWriteBytes");
        }
        if (resultArray.length <= 0) {
            resultArray = new long[0];
        }
        return resultArray;
    }

    private long[] checkIfMuchWritting(int index, List<WriteBytesData> writtenBytes) {
        int ioExceptionCount = 0;
        long[] resultArray = new long[3];
        long totalWriteBytes = 0;
        long lastDayValue = 0;
        boolean isExistExp = false;
        for (int recordIndex = (writtenBytes.size() - 1) - index; recordIndex >= 0; recordIndex--) {
            WriteBytesData data = (WriteBytesData) writtenBytes.get(recordIndex);
            if (data.mWriteBytes < WRITTEN_BYTES_EXCEPTION_LIMIT || (ioExceptionCount > 0 && Utils.getDifferencesByDay(lastDayValue, data.mTime) != 1)) {
                if (Utils.DEBUG) {
                    Log.d(TAG, "checkExceptionOnTotalWrittenBytes,overtime or not reach to the limit:lastDayValue:" + lastDayValue + ",time:" + data.mTime);
                }
                ioExceptionCount = 0;
                lastDayValue = 0;
                resultArray = new long[3];
            } else {
                if (ioExceptionCount == 0) {
                    resultArray[0] = data.mTime;
                }
                if (Utils.DEBUG) {
                    Log.d(TAG, "checkExceptionOnTotalWrittenBytes,reach to the limit:lastDayValue:" + lastDayValue + ",time:" + data.mTime);
                }
                ioExceptionCount++;
                totalWriteBytes += data.mWriteBytes;
                lastDayValue = data.mTime;
                if (ioExceptionCount >= 7) {
                    resultArray[1] = data.mTime;
                    resultArray[2] = totalWriteBytes;
                    isExistExp = true;
                    break;
                }
            }
        }
        return isExistExp ? resultArray : new long[0];
    }

    private int[] checkExceptionOnLifeTime() {
        LifeTimeData lifeTimeA = getLastLifeTimeData(2);
        LifeTimeData lifeTimeB = getLastLifeTimeData(3);
        int currentLifeTimeA = KernelIOStats.getHealthInformation(0);
        int currentLifeTimeB = KernelIOStats.getHealthInformation(1);
        long currentTime = System.currentTimeMillis();
        int lifeA = 0;
        int lifeB = 0;
        if (lifeTimeA != null) {
            lifeA = checkExceptionOnLifeTime(lifeTimeA, currentLifeTimeA, currentTime) ? currentLifeTimeA : 0;
        }
        if (lifeTimeB != null) {
            lifeB = checkExceptionOnLifeTime(lifeTimeB, currentLifeTimeB, currentTime) ? currentLifeTimeB : 0;
        }
        if ((lifeA | lifeB) == 0) {
            return new int[0];
        }
        Log.i(TAG, "An Exception about the LifeTime occurs, the saved lifeTimeA:" + lifeA + ",the saved lifeTimeB:" + lifeB);
        return new int[]{lifeA, lifeB};
    }

    private boolean checkExceptionOnLifeTime(LifeTimeData lastLifeTime, int currentLifeTime, long currentUpdateTime) {
        if (currentLifeTime == lastLifeTime.mLifeTime) {
            Log.e(TAG, "checkExceptionOnLifeTime,the life time isn't changed");
            return false;
        }
        boolean isException = currentUpdateTime - lastLifeTime.mTime < TIME_VALUES_MONTH;
        if (isException) {
            Log.i(TAG, "checkExceptionOnLifeTime,An Exception,lastLifeTime:" + lastLifeTime.mLifeTime + ",last update time:" + lastLifeTime.mTime);
        }
        return isException;
    }

    private int[] checkExceptionOnDevice() {
        int EOLValue;
        Log.i(TAG, "do the checking on the device status");
        int[] healthArray = checkExceptionOnLifeTime();
        int healthA = 0;
        int healthB = 0;
        if (healthArray.length > 1) {
            healthA = healthArray[0];
            healthB = healthArray[1];
        }
        int EOL = KernelIOStats.getHealthInformation(2);
        if (EOL > 1) {
            EOLValue = EOL;
        } else {
            EOLValue = 0;
        }
        if (((healthA | healthB) | EOLValue) == 0) {
            Log.w(TAG, "no error on the device status");
            return new int[0];
        }
        Log.i(TAG, "An Exception about the deivce,healthA:" + healthA + ",healthB:" + healthB + ",current EOL:" + EOL);
        return new int[]{healthA, healthB, EOLValue};
    }

    private List<AppExceptionData> checkExceptionOnApp() {
        Log.i(TAG, "do the checking on the App");
        SparseArray<IOStatsHistory> historyList = this.mIOStatsService.getAllIOStatsCollection();
        if (historyList == null || historyList.size() == 0) {
            Log.e(TAG, "checkExceptionOnApp, the historyList is empty");
            return null;
        }
        List<AppExceptionData> exceptionList = new ArrayList();
        for (int index = 0; index < historyList.size(); index++) {
            AppExceptionData appExceptionData = ((IOStatsHistory) historyList.get(historyList.keyAt(index))).checkIfAppIOException();
            if (appExceptionData != null) {
                exceptionList.add(appExceptionData);
            }
        }
        if (exceptionList.size() > 0) {
            Log.i(TAG, "An App Exception occurs");
        } else if (exceptionList.size() == 0) {
            Log.i(TAG, "no error on the app");
        }
        return exceptionList;
    }

    public LifeTimeData getLastLifeTimeData(int lifeTimeType) {
        List<LifeTimeData> dataList = getAllLifeTimeInfo(lifeTimeType);
        if (dataList != null && dataList.size() != 0) {
            return (LifeTimeData) dataList.get(dataList.size() - 1);
        }
        Log.e(TAG, "getLastLifeTimeData,the lifetime information is empty,type:" + lifeTimeType);
        return null;
    }

    public void interrupt() {
        this.mInterrupt.trigger();
    }

    public ExceptionData checkIfExistException() {
        List<CheckHandler> checkList = new ArrayList();
        checkList.add(new CheckTotalWriteBytesHandler(this));
        checkList.add(new CheckDeviceHandler(this));
        checkList.add(new CheckAppHandler(this));
        ExceptionData result = null;
        for (CheckHandler checkHandler : checkList) {
            if (!this.mInterrupt.checkInterruptAndReset()) {
                result = checkHandler.check();
                if (result != null) {
                    break;
                }
            }
            Log.e(TAG, "checkIfExistException,interrupted by user");
            break;
        }
        return result;
    }

    public void handleIOException(ExceptionData exceptionData) {
        if (exceptionData == null) {
            Log.e(TAG, "handleIOException,the exceptionData is null");
            return;
        }
        Log.i(TAG, "handleIOException,the exception type:" + exceptionData.mExceptionType);
        if (this.mInterrupt.checkInterruptAndReset()) {
            Log.e(TAG, "handleIOException,interrupted by user");
            return;
        }
        switch (exceptionData.mExceptionType) {
            case 1:
                uploadTotalWrittenBytes(exceptionData.mData);
                break;
            case 5:
                uploadDeviceException(exceptionData.mData);
                break;
            case 6:
                uploadAppException(exceptionData.mData);
                break;
        }
    }

    private void uploadTotalWrittenBytes(Object exceptionData) {
        if (this.mIOStatsService == null) {
            Log.e(TAG, "uploadTotalWrittenBytes, the IOStatsService is null");
            return;
        }
        int resourceValue = 0;
        boolean isInvalid = false;
        long expStartTime = 0;
        long expEndTime = 0;
        try {
            long[] exceptionDataArray = (long[]) exceptionData;
            expEndTime = exceptionDataArray[0];
            expStartTime = exceptionDataArray[1];
            if (Utils.DEBUG) {
                Log.d(TAG, "uploadTotalWrittenBytes,writeBytes:" + exceptionDataArray[2]);
            }
            resourceValue = Integer.parseInt(String.valueOf(exceptionDataArray[2] / BYTES_SIZE_1G)) * 10;
        } catch (Exception ex) {
            Log.e(TAG, "uploadTotalWrittenBytes,the exceptionData is invalid:" + ex.getMessage());
            isInvalid = true;
        }
        if (!isInvalid) {
            SparseArray<IOStatsHistory> historyList = this.mIOStatsService.getAllIOStatsCollection();
            if (historyList == null || historyList.size() == 0) {
                Log.e(TAG, "uploadTotalWrittenBytes, the historyList is empty");
                return;
            }
            String resourceLog = getTopAppInformation(calculateTotalWrittenBytes(historyList, expStartTime, expEndTime));
            if (resourceLog.length() == 0) {
                Log.e(TAG, "uploadTotalWrittenBytes,no app datas exist");
            } else {
                uploadBigDataLog(BIG_LOG_PKG_NAME_WRITE, 28, resourceValue, resourceLog);
            }
        }
    }

    private String getTopAppInformation(List<AppExceptionData> dataList) {
        Collections.sort(dataList, new AppExceptionComparator());
        int count = 0;
        StringBuilder resourceLogBuilder = new StringBuilder();
        for (AppExceptionData data : dataList) {
            if (count > 10) {
                break;
            }
            resourceLogBuilder.append("Total written Exception,");
            resourceLogBuilder.append("pkgName:").append(data.mPkgName);
            resourceLogBuilder.append(",totalWriteBytes:").append(data.mTotalWrittenBytes);
            count++;
        }
        return resourceLogBuilder.toString();
    }

    private List<AppExceptionData> calculateTotalWrittenBytes(SparseArray<IOStatsHistory> historyList, long expStartTime, long expEndTime) {
        List<AppExceptionData> dataList = new ArrayList();
        for (int index = 0; index < historyList.size(); index++) {
            IOStatsHistory history = (IOStatsHistory) historyList.get(historyList.keyAt(index));
            long totalWriteBytes = history.calculateTotalWrittenBytes(expStartTime, expEndTime);
            if (totalWriteBytes != 0) {
                AppExceptionData appExceptionData = new AppExceptionData();
                appExceptionData.mPkgName = history.getPkgName();
                appExceptionData.mTotalWrittenBytes = totalWriteBytes;
                dataList.add(appExceptionData);
            }
        }
        return dataList;
    }

    private void uploadAppException(Object exceptionData) {
        Iterable appExceptionList = null;
        boolean isInvalid = false;
        try {
            appExceptionList = (List) exceptionData;
        } catch (Exception ex) {
            Log.e(TAG, "uploadAppException,the exceptionData is invalid:" + ex.getMessage());
            isInvalid = true;
        }
        if (!isInvalid) {
            StringBuilder resourceBuilder = new StringBuilder();
            for (AppExceptionData appExceptionData : r4) {
                resourceBuilder.delete(0, resourceBuilder.length());
                resourceBuilder.append("app Exception,pkg:").append(appExceptionData.mPkgName);
                resourceBuilder.append(",writeBytes:").append(appExceptionData.mTotalWrittenBytes);
                resourceBuilder.append(",start_time:").append(appExceptionData.mStartTime);
                uploadBigDataLog(appExceptionData.mPkgName, 27, 1, resourceBuilder.toString());
            }
        }
    }

    private void uploadDeviceException(Object exceptionData) {
        int healthAResource = 0;
        int healthBResource = 0;
        int EOLValueResource = 0;
        int healthA = 0;
        int healthB = 0;
        int EOL = 0;
        boolean isInvalid = false;
        try {
            int[] exceptionDataArray = (int[]) exceptionData;
            healthA = exceptionDataArray[0];
            healthB = exceptionDataArray[1];
            EOL = exceptionDataArray[2];
            healthAResource = healthA * 125;
            healthBResource = healthB * 125;
            EOLValueResource = EOL * BIG_LOG_FACTOR_DEV_RSV_BLK;
        } catch (Exception ex) {
            Log.e(TAG, "uploadDeviceException,the exceptionData is invalid:" + ex.getMessage());
            isInvalid = true;
        }
        if (!isInvalid) {
            String CIDValue = KernelIOStats.getCIDNodeInformation();
            if (healthA != 0) {
                uploadDeviceException(new int[]{healthAResource, 29}, BIG_LOG_PKG_NAME_LIFE_A, (long) healthA, CIDValue);
            }
            if (healthB != 0) {
                uploadDeviceException(new int[]{healthBResource, 30}, BIG_LOG_PKG_NAME_LIFE_B, (long) healthB, CIDValue);
            }
            if (EOL != 0) {
                uploadDeviceException(new int[]{EOLValueResource, 31}, BIG_LOG_PKG_NAME_WRITE_EOL, (long) EOL, CIDValue);
            }
        }
    }

    private void uploadDeviceException(int[] healthArray, String pkgName, long resourceData, String CID) {
        int healthType;
        String resourceLog = "device exception,CID:" + CID;
        switch (healthArray[1]) {
            case 29:
                healthType = 2;
                break;
            case 30:
                healthType = 3;
                break;
            default:
                healthType = 0;
                break;
        }
        if (healthArray[1] == 31) {
            resourceLog = resourceLog + " EOL value:" + resourceData;
        } else {
            resourceLog = (resourceLog + ",healthInfo:" + resourceData) + "," + getLifeTimeFileInfo(healthType);
        }
        uploadBigDataLog(pkgName, healthArray[1], healthArray[0], resourceLog);
    }

    private String getLifeTimeFileInfo(int type) {
        List<LifeTimeData> dataList = getAllLifeTimeInfo(type);
        StringBuilder resultBuilder = new StringBuilder("emmc_health:");
        if (dataList == null || dataList.size() == 0) {
            resultBuilder.append("empty");
            return resultBuilder.toString();
        }
        for (LifeTimeData data : dataList) {
            resultBuilder.append("time:").append(data.mTime);
            resultBuilder.append(",health:").append(data.mLifeTime).append("\n");
        }
        return resultBuilder.toString();
    }

    public List<LifeTimeData> getAllLifeTimeInfo(int type) {
        this.mLifeTimeCollection.reset();
        IOFileRotator fileRotator = null;
        if (type == 2) {
            try {
                fileRotator = this.mFileLifeTimeA;
            } catch (IOException ex) {
                Log.e(TAG, "getAllLifeTimeInfo,an IOException occurs:" + ex.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "getAllLifeTimeInfo,an Exception occurs:" + e.getMessage());
            }
        } else if (type == 3) {
            fileRotator = this.mFileLifeTimeB;
        }
        if (fileRotator == null) {
            Log.e(TAG, "getAllLifeTimeInfo,type:" + type + " is invalid");
            return null;
        }
        fileRotator.readMatching(this.mLifeTimeCollection, Long.MIN_VALUE, Long.MAX_VALUE);
        return this.mLifeTimeCollection.getAllDataList();
    }

    private void uploadBigDataLog(String packageName, int resourceType, int resourceValue, String resourceLog) {
        if (Utils.DEBUG) {
            Log.d(TAG, "uploadBigDataLog,packageName:" + packageName + ",resourceType:" + resourceType);
        }
        String arg1 = ResourceUtils.composeName(packageName, resourceType);
        Log.i(TAG, "uploadBigDataLog,arg1:" + arg1 + ",arg2:" + resourceValue);
        Log.i(TAG, "uploadBigDataLog,the log :" + resourceLog);
        this.mJankLogProxy.jlog_d(arg1, resourceValue, resourceLog);
    }
}
