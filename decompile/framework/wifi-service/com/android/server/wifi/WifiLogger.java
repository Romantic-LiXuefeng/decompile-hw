package com.android.server.wifi;

import android.util.Base64;
import android.util.Log;
import com.android.server.wifi.WifiNative.RingBufferStatus;
import com.android.server.wifi.WifiNative.RxFateReport;
import com.android.server.wifi.WifiNative.TxFateReport;
import com.android.server.wifi.WifiNative.WifiLoggerEventHandler;
import com.android.server.wifi.util.ByteArrayRingBuffer;
import com.android.server.wifi.util.StringUtil;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Deflater;

class WifiLogger extends BaseWifiLogger {
    private static final boolean DBG = false;
    public static final String DRIVER_DUMP_SECTION_HEADER = "Driver state dump";
    public static final String FIRMWARE_DUMP_SECTION_HEADER = "FW Memory dump";
    public static final int MAX_ALERT_REPORTS = 1;
    public static final int MAX_BUG_REPORTS = 4;
    private static final int[] MinBufferSizes = new int[]{0, 16384, 16384, 65536};
    private static final int[] MinWakeupIntervals = new int[]{0, 3600, 60, 10};
    public static final int REPORT_REASON_ASSOC_FAILURE = 1;
    public static final int REPORT_REASON_AUTH_FAILURE = 2;
    public static final int REPORT_REASON_AUTOROAM_FAILURE = 3;
    public static final int REPORT_REASON_DHCP_FAILURE = 4;
    public static final int REPORT_REASON_NONE = 0;
    public static final int REPORT_REASON_SCAN_FAILURE = 6;
    public static final int REPORT_REASON_UNEXPECTED_DISCONNECT = 5;
    public static final int REPORT_REASON_USER_ACTION = 7;
    public static final int RING_BUFFER_BYTE_LIMIT_LARGE = 1048576;
    public static final int RING_BUFFER_BYTE_LIMIT_SMALL = 32768;
    public static final int RING_BUFFER_FLAG_HAS_ASCII_ENTRIES = 2;
    public static final int RING_BUFFER_FLAG_HAS_BINARY_ENTRIES = 1;
    public static final int RING_BUFFER_FLAG_HAS_PER_PACKET_ENTRIES = 4;
    private static final String TAG = "WifiLogger";
    public static final int VERBOSE_DETAILED_LOG_WITH_WAKEUP = 3;
    public static final int VERBOSE_LOG_WITH_WAKEUP = 2;
    public static final int VERBOSE_NORMAL_LOG = 1;
    public static final int VERBOSE_NO_LOG = 0;
    private AtomicBoolean mBugReportDone = new AtomicBoolean(true);
    private final BuildProperties mBuildProperties;
    private final WifiLoggerEventHandler mHandler = new WifiLoggerEventHandler() {
        public void onRingBufferData(RingBufferStatus status, byte[] buffer) {
            WifiLogger.this.onRingBufferData(status, buffer);
        }

        public void onWifiAlert(int errorCode, byte[] buffer) {
            WifiLogger.this.onWifiAlert(errorCode, buffer);
        }
    };
    private boolean mIsLoggingEventHandlerRegistered;
    private final LimitedCircularArray<BugReport> mLastAlerts = new LimitedCircularArray(1);
    private final LimitedCircularArray<BugReport> mLastBugReports = new LimitedCircularArray(4);
    private int mLogLevel = 0;
    private int mMaxRingBufferSizeBytes = RING_BUFFER_BYTE_LIMIT_SMALL;
    private ArrayList<FateReport> mPacketFatesForLastFailure;
    private RingBufferStatus mPerPacketRingBuffer;
    private final HashMap<String, ByteArrayRingBuffer> mRingBufferData = new HashMap();
    private RingBufferStatus[] mRingBuffers;
    private ThreadPoolExecutor mSingleThread = ((ThreadPoolExecutor) Executors.newFixedThreadPool(1));
    private final WifiNative mWifiNative;
    private WifiStateMachine mWifiStateMachine;

    class BugReport {
        byte[] alertData;
        int errorCode;
        byte[] fwMemoryDump;
        LimitedCircularArray<String> kernelLogLines;
        long kernelTimeNanos;
        ArrayList<String> logcatLines;
        byte[] mDriverStateDump;
        HashMap<String, byte[][]> ringBuffers = new HashMap();
        long systemTimeMs;

        BugReport() {
        }

        void clearVerboseLogs() {
            this.fwMemoryDump = null;
            this.mDriverStateDump = null;
        }

        public String toString() {
            int i;
            StringBuilder builder = new StringBuilder();
            Calendar.getInstance().setTimeInMillis(this.systemTimeMs);
            builder.append("system time = ").append(String.format("%tm-%td %tH:%tM:%tS.%tL", new Object[]{c, c, c, c, c, c})).append("\n");
            long kernelTimeMs = this.kernelTimeNanos / 1000000;
            builder.append("kernel time = ").append(kernelTimeMs / 1000).append(".").append(kernelTimeMs % 1000).append("\n");
            if (this.alertData == null) {
                builder.append("reason = ").append(this.errorCode).append("\n");
            } else {
                builder.append("errorCode = ").append(this.errorCode);
                builder.append("data \n");
                builder.append(WifiLogger.compressToBase64(this.alertData)).append("\n");
            }
            if (this.kernelLogLines != null) {
                builder.append("kernel log: \n");
                for (i = 0; i < this.kernelLogLines.size(); i++) {
                    builder.append((String) this.kernelLogLines.get(i)).append("\n");
                }
                builder.append("\n");
            }
            if (this.logcatLines != null) {
                builder.append("system log: \n");
                for (i = 0; i < this.logcatLines.size(); i++) {
                    builder.append((String) this.logcatLines.get(i)).append("\n");
                }
                builder.append("\n");
            }
            for (Entry<String, byte[][]> e : this.ringBuffers.entrySet()) {
                byte[][] buffers = (byte[][]) e.getValue();
                builder.append("ring-buffer = ").append((String) e.getKey()).append("\n");
                int size = 0;
                for (byte[] length : buffers) {
                    size += length.length;
                }
                byte[] buffer = new byte[size];
                int index = 0;
                for (i = 0; i < buffers.length; i++) {
                    System.arraycopy(buffers[i], 0, buffer, index, buffers[i].length);
                    index += buffers[i].length;
                }
                builder.append(WifiLogger.compressToBase64(buffer));
                builder.append("\n");
            }
            if (this.fwMemoryDump != null) {
                builder.append(WifiLogger.FIRMWARE_DUMP_SECTION_HEADER);
                builder.append("\n");
                builder.append(WifiLogger.compressToBase64(this.fwMemoryDump));
                builder.append("\n");
            }
            if (this.mDriverStateDump != null) {
                builder.append(WifiLogger.DRIVER_DUMP_SECTION_HEADER);
                if (StringUtil.isAsciiPrintable(this.mDriverStateDump)) {
                    builder.append(" (ascii)\n");
                    builder.append(new String(this.mDriverStateDump, Charset.forName("US-ASCII")));
                    builder.append("\n");
                } else {
                    builder.append(" (base64)\n");
                    builder.append(WifiLogger.compressToBase64(this.mDriverStateDump));
                }
            }
            return builder.toString();
        }
    }

    class LimitedCircularArray<E> {
        private ArrayList<E> mArrayList;
        private int mMax;

        LimitedCircularArray(int max) {
            this.mArrayList = new ArrayList(max);
            this.mMax = max;
        }

        public final void addLast(E e) {
            if (this.mArrayList.size() >= this.mMax) {
                this.mArrayList.remove(0);
            }
            this.mArrayList.add(e);
        }

        public final int size() {
            return this.mArrayList.size();
        }

        public final E get(int i) {
            return this.mArrayList.get(i);
        }
    }

    public WifiLogger(WifiStateMachine wifiStateMachine, WifiNative wifiNative, BuildProperties buildProperties) {
        this.mWifiStateMachine = wifiStateMachine;
        this.mWifiNative = wifiNative;
        this.mBuildProperties = buildProperties;
        this.mIsLoggingEventHandlerRegistered = false;
    }

    public synchronized void startLogging(boolean verboseEnabled) {
        int i = RING_BUFFER_BYTE_LIMIT_LARGE;
        synchronized (this) {
            this.mFirmwareVersion = this.mWifiNative.getFirmwareVersion();
            this.mDriverVersion = this.mWifiNative.getDriverVersion();
            this.mSupportedFeatureSet = this.mWifiNative.getSupportedLoggerFeatureSet();
            if (!this.mIsLoggingEventHandlerRegistered) {
                this.mIsLoggingEventHandlerRegistered = this.mWifiNative.setLoggingEventHandler(this.mHandler);
            }
            if (verboseEnabled) {
                this.mLogLevel = 2;
                this.mMaxRingBufferSizeBytes = RING_BUFFER_BYTE_LIMIT_LARGE;
            } else {
                this.mLogLevel = 1;
                if (!enableVerboseLoggingForDogfood()) {
                    i = RING_BUFFER_BYTE_LIMIT_SMALL;
                }
                this.mMaxRingBufferSizeBytes = i;
                clearVerboseLogs();
            }
            if (this.mRingBuffers == null) {
                fetchRingBuffers();
            }
            if (this.mRingBuffers != null) {
                stopLoggingAllBuffers();
                resizeRingBuffers();
                startLoggingAllExceptPerPacketBuffers();
            }
            if (!this.mWifiNative.startPktFateMonitoring()) {
                Log.e(TAG, "Failed to start packet fate monitoring");
            }
        }
    }

    public synchronized void startPacketLog() {
        if (this.mPerPacketRingBuffer != null) {
            startLoggingRingBuffer(this.mPerPacketRingBuffer);
        }
    }

    public synchronized void stopPacketLog() {
        if (this.mPerPacketRingBuffer != null) {
            stopLoggingRingBuffer(this.mPerPacketRingBuffer);
        }
    }

    public synchronized void stopLogging() {
        if (this.mIsLoggingEventHandlerRegistered) {
            if (!this.mWifiNative.resetLogHandler()) {
                Log.e(TAG, "Fail to reset log handler");
            }
            this.mIsLoggingEventHandlerRegistered = false;
        }
        if (this.mLogLevel != 0) {
            stopLoggingAllBuffers();
            this.mRingBuffers = null;
            this.mLogLevel = 0;
        }
    }

    synchronized void reportConnectionFailure() {
        this.mPacketFatesForLastFailure = fetchPacketFates();
    }

    public synchronized void captureBugReportData(int reason) {
        this.mLastBugReports.addLast(captureBugreport(reason, isVerboseLoggingEnabled()));
    }

    public synchronized void captureAlertData(int errorCode, byte[] alertData) {
        BugReport report = captureBugreport(errorCode, isVerboseLoggingEnabled());
        report.alertData = alertData;
        this.mLastAlerts.addLast(report);
    }

    public synchronized void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        int i;
        super.dump(pw);
        for (i = 0; i < this.mLastAlerts.size(); i++) {
            pw.println("--------------------------------------------------------------------");
            pw.println("Alert dump " + i);
            pw.print(this.mLastAlerts.get(i));
            pw.println("--------------------------------------------------------------------");
        }
        for (i = 0; i < this.mLastBugReports.size(); i++) {
            pw.println("--------------------------------------------------------------------");
            pw.println("Bug dump " + i);
            pw.print(this.mLastBugReports.get(i));
            pw.println("--------------------------------------------------------------------");
        }
        dumpPacketFates(pw);
        pw.println("--------------------------------------------------------------------");
    }

    synchronized void onRingBufferData(RingBufferStatus status, byte[] buffer) {
        ByteArrayRingBuffer ring = (ByteArrayRingBuffer) this.mRingBufferData.get(status.name);
        if (ring != null) {
            ring.appendBuffer(buffer);
        }
    }

    synchronized void onWifiAlert(int errorCode, byte[] buffer) {
        if (this.mWifiStateMachine != null) {
            this.mWifiStateMachine.sendMessage(131172, errorCode, 0, buffer);
        }
    }

    private boolean isVerboseLoggingEnabled() {
        return this.mLogLevel > 1;
    }

    private void clearVerboseLogs() {
        int i;
        this.mPacketFatesForLastFailure = null;
        for (i = 0; i < this.mLastAlerts.size(); i++) {
            ((BugReport) this.mLastAlerts.get(i)).clearVerboseLogs();
        }
        for (i = 0; i < this.mLastBugReports.size(); i++) {
            ((BugReport) this.mLastBugReports.get(i)).clearVerboseLogs();
        }
    }

    private boolean fetchRingBuffers() {
        boolean z = true;
        if (this.mRingBuffers != null) {
            return true;
        }
        this.mRingBuffers = this.mWifiNative.getRingBufferStatus();
        if (this.mRingBuffers != null) {
            for (RingBufferStatus buffer : this.mRingBuffers) {
                if (!this.mRingBufferData.containsKey(buffer.name)) {
                    this.mRingBufferData.put(buffer.name, new ByteArrayRingBuffer(this.mMaxRingBufferSizeBytes));
                }
                if ((buffer.flag & 4) != 0) {
                    this.mPerPacketRingBuffer = buffer;
                }
            }
        } else {
            Log.e(TAG, "no ring buffers found");
        }
        if (this.mRingBuffers == null) {
            z = false;
        }
        return z;
    }

    private void resizeRingBuffers() {
        for (ByteArrayRingBuffer byteArrayRingBuffer : this.mRingBufferData.values()) {
            byteArrayRingBuffer.resize(this.mMaxRingBufferSizeBytes);
        }
    }

    private boolean startLoggingAllExceptPerPacketBuffers() {
        int i = 0;
        if (this.mRingBuffers == null) {
            return false;
        }
        RingBufferStatus[] ringBufferStatusArr = this.mRingBuffers;
        int length = ringBufferStatusArr.length;
        while (i < length) {
            RingBufferStatus buffer = ringBufferStatusArr[i];
            if ((buffer.flag & 4) == 0) {
                startLoggingRingBuffer(buffer);
            }
            i++;
        }
        return true;
    }

    private boolean startLoggingRingBuffer(RingBufferStatus buffer) {
        if (this.mWifiNative.startLoggingRingBuffer(this.mLogLevel, 0, MinWakeupIntervals[this.mLogLevel], MinBufferSizes[this.mLogLevel], buffer.name)) {
            return true;
        }
        return false;
    }

    private boolean stopLoggingRingBuffer(RingBufferStatus buffer) {
        if (this.mWifiNative.startLoggingRingBuffer(0, 0, 0, 0, buffer.name)) {
        }
        return true;
    }

    private boolean stopLoggingAllBuffers() {
        if (this.mRingBuffers != null) {
            for (RingBufferStatus buffer : this.mRingBuffers) {
                stopLoggingRingBuffer(buffer);
            }
        }
        return true;
    }

    private boolean getAllRingBufferData() {
        if (this.mRingBuffers == null) {
            Log.e(TAG, "Not ring buffers available to collect data!");
            return false;
        }
        RingBufferStatus[] ringBufferStatusArr = this.mRingBuffers;
        int length = ringBufferStatusArr.length;
        int i = 0;
        while (i < length) {
            RingBufferStatus element = ringBufferStatusArr[i];
            if (this.mWifiNative.getRingBufferData(element.name)) {
                i++;
            } else {
                Log.e(TAG, "Fail to get ring buffer data of: " + element.name);
                return false;
            }
        }
        Log.d(TAG, "getAllRingBufferData Successfully!");
        return true;
    }

    private boolean enableVerboseLoggingForDogfood() {
        return false;
    }

    private BugReport captureBugreport(int errorCode, boolean captureFWDump) {
        final BugReport report = new BugReport();
        report.errorCode = errorCode;
        report.systemTimeMs = System.currentTimeMillis();
        report.kernelTimeNanos = System.nanoTime();
        if (this.mRingBuffers != null) {
            for (RingBufferStatus buffer : this.mRingBuffers) {
                this.mWifiNative.getRingBufferData(buffer.name);
                ByteArrayRingBuffer data = (ByteArrayRingBuffer) this.mRingBufferData.get(buffer.name);
                byte[][] buffers = new byte[data.getNumBuffers()][];
                for (int i = 0; i < data.getNumBuffers(); i++) {
                    buffers[i] = (byte[]) data.getBuffer(i).clone();
                }
                report.ringBuffers.put(buffer.name, buffers);
            }
        }
        this.mBugReportDone.set(false);
        FutureTask futureTask = null;
        if (this.mSingleThread.getActiveCount() == 0) {
            Log.d(TAG, "Thread Poll is free, execute task.");
            futureTask = new FutureTask(new Runnable() {
                public void run() {
                    Log.d(WifiLogger.TAG, "set mBugReportDone false");
                    report.logcatLines = WifiLogger.this.getLogcat(127);
                    report.kernelLogLines = WifiLogger.this.getKernelLog(127);
                    Log.d(WifiLogger.TAG, "set mBugReportDone true");
                    WifiLogger.this.mBugReportDone.set(true);
                }
            }, "getLogTask");
            this.mSingleThread.execute(futureTask);
            try {
                Log.d(TAG, "execute getLogTask wait 1000 ms)");
                futureTask.get(1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Log.w(TAG, "execute getLogTask catch InterruptedException.");
                futureTask.cancel(true);
            } catch (ExecutionException e2) {
                Log.w(TAG, "execute getLogTask catch ExecutionException.");
                futureTask.cancel(true);
            } catch (TimeoutException e3) {
                Log.w(TAG, "execute getLogTask catch TimeoutException.");
                futureTask.cancel(true);
            } catch (Exception e4) {
                Log.w(TAG, "execute getLogTask catch Exception.");
                futureTask.cancel(true);
            }
        }
        if (!this.mBugReportDone.get()) {
            if (futureTask != null) {
                futureTask.cancel(true);
            }
            ArrayList<String> logcatLines = new ArrayList();
            logcatLines.add("get logcat timeout!");
            report.logcatLines = logcatLines;
            LimitedCircularArray<String> kernelLogLines = new LimitedCircularArray(1);
            kernelLogLines.addLast("get kernel log timeout!");
            report.kernelLogLines = kernelLogLines;
            Log.w(TAG, "get logcat&kernel log timeout!");
            this.mBugReportDone.set(true);
        }
        if (this.mSingleThread.getActiveCount() > 0) {
            Log.w(TAG, "There are still some threads running in the Thread Pool.");
        }
        if (captureFWDump) {
            report.fwMemoryDump = this.mWifiNative.getFwMemoryDump();
            report.mDriverStateDump = this.mWifiNative.getDriverStateDump();
        }
        return report;
    }

    LimitedCircularArray<BugReport> getBugReports() {
        return this.mLastBugReports;
    }

    private static String compressToBase64(byte[] input) {
        Deflater compressor = new Deflater();
        compressor.setLevel(9);
        compressor.setInput(input);
        compressor.finish();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(input.length);
        byte[] buf = new byte[1024];
        while (!compressor.finished()) {
            bos.write(buf, 0, compressor.deflate(buf));
        }
        try {
            compressor.end();
            bos.close();
            byte[] compressed = bos.toByteArray();
            if (compressed.length >= input.length) {
                compressed = input;
            }
            return Base64.encodeToString(compressed, 0);
        } catch (IOException e) {
            Log.e(TAG, "ByteArrayOutputStream close error");
            return Base64.encodeToString(input, 0);
        }
    }

    private ArrayList<String> getLogcat(int maxLines) {
        ArrayList<String> lines = new ArrayList(maxLines);
        try {
            String line;
            Process process = Runtime.getRuntime().exec(String.format("logcat -t %d", new Object[]{Integer.valueOf(maxLines)}));
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while (true) {
                line = reader.readLine();
                if (line == null) {
                    break;
                }
                lines.add(line);
            }
            reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while (true) {
                line = reader.readLine();
                if (line == null) {
                    break;
                }
                lines.add(line);
            }
            process.waitFor();
        } catch (Exception e) {
            Log.e(TAG, "Exception while capturing logcat" + e);
        }
        return lines;
    }

    private LimitedCircularArray<String> getKernelLog(int maxLines) {
        LimitedCircularArray<String> lines = new LimitedCircularArray(maxLines);
        String[] logLines = this.mWifiNative.readKernelLog().split("\n");
        for (Object addLast : logLines) {
            lines.addLast(addLast);
        }
        return lines;
    }

    private ArrayList<FateReport> fetchPacketFates() {
        int i;
        ArrayList<FateReport> mergedFates = new ArrayList();
        TxFateReport[] txFates = new TxFateReport[32];
        if (this.mWifiNative.getTxPktFates(txFates)) {
            i = 0;
            while (i < txFates.length && txFates[i] != null) {
                mergedFates.add(txFates[i]);
                i++;
            }
        }
        RxFateReport[] rxFates = new RxFateReport[32];
        if (this.mWifiNative.getRxPktFates(rxFates)) {
            i = 0;
            while (i < rxFates.length && rxFates[i] != null) {
                mergedFates.add(rxFates[i]);
                i++;
            }
        }
        Collections.sort(mergedFates, new Comparator<FateReport>() {
            public int compare(FateReport lhs, FateReport rhs) {
                return Long.compare(lhs.mDriverTimestampUSec, rhs.mDriverTimestampUSec);
            }
        });
        return mergedFates;
    }

    private void dumpPacketFates(PrintWriter pw) {
        dumpPacketFatesInternal(pw, "Last failed connection fates", this.mPacketFatesForLastFailure, isVerboseLoggingEnabled());
        dumpPacketFatesInternal(pw, "Latest fates", fetchPacketFates(), isVerboseLoggingEnabled());
    }

    private static void dumpPacketFatesInternal(PrintWriter pw, String description, ArrayList<FateReport> fates, boolean verbose) {
        if (fates == null) {
            pw.format("No fates fetched for \"%s\"\n", new Object[]{description});
        } else if (fates.size() == 0) {
            pw.format("HAL provided zero fates for \"%s\"\n", new Object[]{description});
        } else {
            pw.format("--------------------- %s ----------------------\n", new Object[]{description});
            StringBuilder verboseOutput = new StringBuilder();
            pw.print(FateReport.getTableHeader());
            for (FateReport fate : fates) {
                pw.print(fate.toTableRowString());
                if (verbose) {
                    verboseOutput.append(fate.toVerboseStringWithPiiAllowed());
                    verboseOutput.append("\n");
                }
            }
            if (verbose) {
                pw.format("\n>>> VERBOSE PACKET FATE DUMP <<<\n\n", new Object[0]);
                pw.print(verboseOutput.toString());
            }
            pw.println("--------------------------------------------------------------------");
        }
    }
}
