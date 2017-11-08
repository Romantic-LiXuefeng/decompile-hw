package com.android.contacts.update;

import android.os.Environment;
import android.os.Message;
import com.android.contacts.util.HwLog;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.json.JSONException;

public class DownloadProcessor implements RunnableFuture<Object> {
    private static final String LOG_TAG = DownloadService.class.getSimpleName();
    private volatile boolean mCanceled;
    private volatile boolean mDone;
    private int mFileId;
    private int mJobId;
    private DownloadService mService;
    private IUpdate mUpdater = UpdateHelper.getUpdaterInstance(this.mFileId, this.mService);

    public DownloadProcessor(DownloadService service, int fileId, int jobId) {
        this.mService = service;
        this.mFileId = fileId;
        this.mJobId = jobId;
    }

    public void run() {
        try {
            runInternal();
            synchronized (this) {
                this.mDone = true;
                sendMsg(Message.obtain(null, 2));
            }
        } catch (OutOfMemoryError e) {
            HwLog.e(LOG_TAG, "OutOfMemoryError thrown during download", e);
            throw e;
        } catch (RuntimeException e2) {
            HwLog.e(LOG_TAG, "RuntimeException thrown during download", e2);
            throw e2;
        } catch (Throwable th) {
            synchronized (this) {
                this.mDone = true;
                sendMsg(Message.obtain(null, 2));
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void runInternal() {
        try {
            DownloadResponse response = queryHttpsServerConfig();
            if (HwLog.HWDBG) {
                HwLog.d(LOG_TAG, "queryHttpsServerConfig finish");
            }
            if (response != null && response.checkAvalible()) {
                downloadFile(response);
                if (HwLog.HWDBG) {
                    HwLog.d(LOG_TAG, "downloadFile finish");
                }
                copyFile(response);
                if (HwLog.HWDBG) {
                    HwLog.d(LOG_TAG, "copyFile finish");
                }
                this.mUpdater.handleComplete(response);
            }
            deleteSDcardFile(response);
            this.mService.handleFinishDownload(this.mJobId);
        } catch (IOException e) {
            sendMsg(Message.obtain(null, 0));
            HwLog.e(LOG_TAG, "IOException " + e);
        } catch (JSONException e2) {
            sendMsg(Message.obtain(null, 0));
            HwLog.e(LOG_TAG, "JSONException " + e2);
        } catch (Throwable th) {
            deleteSDcardFile(null);
            this.mService.handleFinishDownload(this.mJobId);
        }
    }

    private DownloadResponse queryHttpsServerConfig() throws JSONException, IOException {
        Throwable th;
        InputStream is = null;
        Closeable closeable = null;
        try {
            DownloadRequest request = this.mUpdater.contructRequest();
            HwLog.d(LOG_TAG, "query config : " + request.toJson());
            is = request.doHttpsPost(this.mService);
            if (is == null) {
                closeStream(is);
                closeStream(null);
                return null;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            try {
                StringBuilder sb = new StringBuilder();
                while (true) {
                    String line = reader.readLine();
                    if (line != null) {
                        sb.append(line);
                    } else {
                        DownloadResponse response = DownloadResponse.fromJson(sb.toString());
                        closeStream(is);
                        closeStream(reader);
                        return response;
                    }
                }
            } catch (Throwable th2) {
                th = th2;
                closeable = reader;
                closeStream(is);
                closeStream(closeable);
                throw th;
            }
        } catch (Throwable th3) {
            th = th3;
            closeStream(is);
            closeStream(closeable);
            throw th;
        }
    }

    private void downloadFile(DownloadResponse response) throws IOException {
        Throwable th;
        FileOutputStream fos = null;
        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection = (HttpURLConnection) new URL(response.getDownloadUrl()).openConnection();
            httpURLConnection.setConnectTimeout(10000);
            httpURLConnection.setReadTimeout(10000);
            if (httpURLConnection.getResponseCode() != 200) {
                throw new ConnectException();
            }
            FileOutputStream fos2 = new FileOutputStream(new File(this.mService.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), response.getFileName()));
            try {
                InputStream is = httpURLConnection.getInputStream();
                byte[] buffer = new byte[102400];
                int size = 0;
                long total = (long) is.available();
                int contentLength = httpURLConnection.getContentLength();
                if (HwLog.HWDBG) {
                    HwLog.d(LOG_TAG, "downloadFile contentLength:" + contentLength);
                    HwLog.d(LOG_TAG, "downloadFile total:" + total);
                }
                while (true) {
                    int len = is.read(buffer);
                    if (len <= 0) {
                        break;
                    }
                    fos2.write(buffer, 0, len);
                    size += len;
                }
                if (HwLog.HWDBG) {
                    HwLog.d(LOG_TAG, "downloadFile size:" + size);
                }
                fos2.flush();
                closeStream(fos2);
                closeStream(is);
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
            } catch (Throwable th2) {
                th = th2;
                fos = fos2;
                closeStream(fos);
                closeStream(null);
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
                throw th;
            }
        } catch (Throwable th3) {
            th = th3;
            closeStream(fos);
            closeStream(null);
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
            throw th;
        }
    }

    private void copyFile(DownloadResponse response) throws IOException {
        Throwable th;
        FileOutputStream fos = null;
        Closeable fis = null;
        try {
            FileInputStream fis2;
            File src = new File(this.mService.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), response.getFileName());
            File orginFilesDir = this.mService.getFilesDir();
            File externalFilesDir = this.mService.createDeviceProtectedStorageContext().getFilesDir();
            if (this.mFileId == 3) {
                externalFilesDir = new File(externalFilesDir.getAbsolutePath().replace("com.android.contacts", "com.huawei.contactscamcard"));
                orginFilesDir = new File(orginFilesDir.getAbsolutePath().replace("com.android.contacts", "com.huawei.contactscamcard"));
            }
            File orginFile = new File(orginFilesDir, response.getFileName());
            if (orginFile.exists() && !orginFile.delete()) {
                HwLog.i(LOG_TAG, "delete orgin file in CE failed");
            }
            FileOutputStream fos2 = new FileOutputStream(new File(externalFilesDir, response.getFileName()));
            try {
                fis2 = new FileInputStream(src);
            } catch (Throwable th2) {
                th = th2;
                fos = fos2;
                closeStream(fos);
                closeStream(fis);
                throw th;
            }
            try {
                byte[] buffer = new byte[102400];
                int size = 0;
                int total = fis2.available();
                if (HwLog.HWDBG) {
                    HwLog.d(LOG_TAG, "copyFile total:" + total);
                }
                while (true) {
                    int len = fis2.read(buffer);
                    if (len <= 0) {
                        break;
                    }
                    fos2.write(buffer, 0, len);
                    size += len;
                }
                if (HwLog.HWDBG) {
                    HwLog.d(LOG_TAG, "copyFile size:" + size);
                }
                fos2.flush();
                closeStream(fos2);
                closeStream(fis2);
            } catch (Throwable th3) {
                th = th3;
                Object fis3 = fis2;
                fos = fos2;
                closeStream(fos);
                closeStream(fis);
                throw th;
            }
        } catch (Throwable th4) {
            th = th4;
            closeStream(fos);
            closeStream(fis);
            throw th;
        }
    }

    private void deleteSDcardFile(DownloadResponse response) {
        if (response != null && response.checkAvalible() && response.getFileName() != null) {
            File src = new File(this.mService.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), response.getFileName());
            if (src != null && src.exists()) {
                boolean delete = src.delete();
                if (HwLog.HWDBG) {
                    HwLog.d(LOG_TAG, "deleteSDcardFile:" + delete);
                }
            }
        }
    }

    private static void closeStream(Closeable io) {
        if (io != null) {
            try {
                io.close();
            } catch (IOException e) {
                HwLog.e(LOG_TAG, "close IOException");
            }
        }
    }

    private void sendMsg(Message msg) {
        this.mService.sendMsg(this.mFileId, msg);
    }

    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        if (HwLog.HWDBG) {
            HwLog.d(LOG_TAG, "DownloadProcessor received cancel request");
        }
        if (this.mDone || this.mCanceled) {
            return false;
        }
        this.mCanceled = true;
        return true;
    }

    public synchronized boolean isCancelled() {
        return this.mCanceled;
    }

    public synchronized boolean isDone() {
        return this.mDone;
    }

    public Object get() throws InterruptedException, ExecutionException {
        throw new UnsupportedOperationException();
    }

    public Object get(long arg0, TimeUnit arg1) throws InterruptedException, ExecutionException, TimeoutException {
        throw new UnsupportedOperationException();
    }
}
