package defpackage;

import android.content.Context;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/* renamed from: bw */
class bw implements X509TrustManager {
    private X509TrustManager cq;

    public bw(Context context) {
        TrustManagerFactory instance = TrustManagerFactory.getInstance("X509");
        KeyStore instance2 = KeyStore.getInstance("BKS");
        InputStream byteArrayInputStream = new ByteArrayInputStream(br.cn());
        byteArrayInputStream.reset();
        instance2.load(byteArrayInputStream, bj.decrypter(ax.bO()).toCharArray());
        byteArrayInputStream.close();
        instance.init(instance2);
        TrustManager[] trustManagers = instance.getTrustManagers();
        for (int i = 0; i < trustManagers.length; i++) {
            if (trustManagers[i] instanceof X509TrustManager) {
                this.cq = (X509TrustManager) trustManagers[i];
                return;
            }
        }
        throw new Exception("Couldn't initialize");
    }

    public void checkClientTrusted(X509Certificate[] x509CertificateArr, String str) {
        try {
            this.cq.checkClientTrusted(x509CertificateArr, str);
        } catch (Throwable e) {
            aw.d("PushLog2841", "checkClientTrusted,trs certificate exception:" + e.toString(), e);
        }
    }

    public void checkServerTrusted(X509Certificate[] x509CertificateArr, String str) {
        try {
            this.cq.checkServerTrusted(x509CertificateArr, str);
            aw.d("PushLog2841", "checkServerTrusted,trs certificate success.");
        } catch (Throwable e) {
            aw.d("PushLog2841", "checkServerTrusted,trs certificate exception:" + e.toString(), e);
        }
    }

    public X509Certificate[] getAcceptedIssuers() {
        return this.cq.getAcceptedIssuers();
    }
}
