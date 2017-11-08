package defpackage;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import com.huawei.android.pushagent.PushService;
import com.huawei.android.pushagent.model.channel.ChannelMgr;
import com.huawei.android.pushagent.model.channel.ChannelMgr.ConnectEntityMode;
import com.huawei.android.pushagent.model.flowcontrol.ReconnectMgr;
import com.huawei.android.pushagent.model.flowcontrol.ReconnectMgr.RECONNECTEVENT;
import com.huawei.android.pushagent.utils.bastet.PushBastet;
import java.io.Serializable;
import java.util.List;

/* renamed from: ah */
public class ah extends o {
    private int aU = -1;

    public ah(Context context) {
        try {
            this.aU = au.G(context);
            if (ao.A(context).size() != 0) {
                onReceive(context, new Intent("com.huawei.action.CONNECT_PUSHSRV_PUSHSRV").setPackage(context.getPackageName()));
            } else {
                onReceive(context, new Intent("com.huawei.action.CONNECT_PUSHSRV").setPackage(context.getPackageName()));
            }
        } catch (Throwable e) {
            aw.d("PushLog2841", "call switchChannel cause Exception", e);
        }
    }

    private boolean a(Context context, Intent intent, String str, String str2) {
        return ("com.huawei.intent.action.PUSH".equals(str) && "com.huawei.android.push.intent.HEARTBEAT_REQ".equals(str2)) || "android.intent.action.TIME_SET".equals(str) || "android.intent.action.TIMEZONE_CHANGED".equals(str);
    }

    private boolean b(Context context, String str, String str2) {
        return "com.huawei.push.action.NET_CHANGED".equals(str) || "com.huawei.action.CONNECT_PUSHSRV".equals(str) || "com.huawei.action.CONNECT_PUSHSRV_PUSHSRV".equals(str) || "com.huawei.action.CONNECT_PUSHSRV_POLLINGSRV".equals(str) || "com.huawei.android.push.intent.TRS_QUERY_SUCCESS".equals(str) || ("com.huawei.intent.action.PUSH".equals(str) && "com.huawei.intent.action.PUSH_ON".equals(str2));
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void e(Context context, String str) {
        boolean a = ag.a(context, "cloudpush_isNoDelayConnect", false);
        List A = ao.A(context);
        if (!a && A.size() == 0 && ao.z(context).bw.size() == 0) {
            ChannelMgr.g(context).aU();
            aw.i("PushLog2841", "no push client, stop push apk service");
            bq.b(context, new Intent("com.huawei.intent.action.PUSH_OFF").setPackage(context.getPackageName()).putExtra("Remote_Package_Name", context.getPackageName()), ae.l(context).ad() * 1000);
            return;
        }
        if (!ae.l(context).isValid()) {
            aw.i("PushLog2841", "TRS is invalid, so need to query TRS");
            ae.l(context).i(false);
        }
        if ("com.huawei.android.push.intent.TRS_QUERY_SUCCESS".equals(str)) {
            ChannelMgr.aY().T.bh();
            ChannelMgr.aX().T.bh();
            ai.r(context);
        }
        int G = au.G(context);
        if (-1 == G || G != this.aU) {
            if (-1 == G) {
                if (ag.a(context, "cloudpush_isSupportCollectSocketInfo", false) && !ChannelMgr.aX().hasConnection()) {
                    context.sendBroadcast(new Intent("com.huawei.android.push.intent.SOCKET_INFO").putExtra("socket_add_info", "no network.").setPackage(context.getPackageName()));
                }
                aw.d("PushLog2841", "no network in ConnectMgrProcessor:connect, so close socket");
            } else {
                if (ag.a(context, "cloudpush_isSupportCollectSocketInfo", false) && !ChannelMgr.aX().hasConnection()) {
                    context.sendBroadcast(new Intent("com.huawei.android.push.intent.SOCKET_INFO").putExtra("socket_add_info", "network switch.").setPackage(context.getPackageName()));
                }
                aw.d("PushLog2841", "net work switch from:" + this.aU + " to " + G);
            }
            try {
                ChannelMgr.g(context).aU();
            } catch (Exception e) {
                aw.e("PushLog2841", "call switchChannel cause Exceptino:" + e.toString());
                return;
            }
        }
        Object obj = this.aU != G ? 1 : null;
        aw.i("PushLog2841", "lastnetWorkType:" + this.aU + " " + "curNetWorkType:" + G);
        this.aU = G;
        if (a) {
            ChannelMgr.g(context).a(ConnectEntityMode.ConnectEntity_Push);
            ChannelMgr.aX().a(true);
        } else if (("com.huawei.push.action.NET_CHANGED".equals(str) || "com.huawei.android.push.intent.TRS_QUERY_SUCCESS".equals(str)) && !ChannelMgr.aX().hasConnection() && ChannelMgr.aV() != ConnectEntityMode.ConnectEntity_Push && A.size() != 0) {
            aw.i("PushLog2841", "received " + str + ", cur ConType:" + ChannelMgr.aV() + ", but have need depose size:" + A.size());
            onReceive(context, new Intent("com.huawei.action.CONNECT_PUSHSRV_PUSHSRV"));
        } else if ("com.huawei.action.CONNECT_PUSHSRV_PUSHSRV".equals(str)) {
            aw.d("PushLog2841", "get " + str + " so get a pushSrv to connect");
            if (A.size() != 0) {
                ChannelMgr.g(context).a(ConnectEntityMode.ConnectEntity_Push);
            }
            ChannelMgr.aX().a(true);
        } else if ("com.huawei.action.CONNECT_PUSHSRV_POLLINGSRV".equals(str)) {
            aw.d("PushLog2841", "get " + str + " so get a pollingSrv to connect");
            ChannelMgr.aY().a(true);
        } else if (ChannelMgr.g(context).aW().hasConnection()) {
            aw.d("PushLog2841", "pushChannel already connect, so needn't handle, nextSendHearBeatTime:" + au.a(ChannelMgr.h(context).bg(), "yyyy-MM-dd HH:mm:ss SSS"));
        } else {
            aw.d("PushLog2841", "get " + str + " so get a srv to connect");
            if (obj != null) {
                ReconnectMgr.s(context).a(context, RECONNECTEVENT.NETWORK_CHANGE, new Bundle());
            }
            ChannelMgr.g(context).aW().a(false);
        }
    }

    public void onReceive(Context context, Intent intent) {
        try {
            aw.d("PushLog2841", "enter ConnectMgrProcessor:onReceive(intent:" + intent + " context:" + context);
            String action = intent.getAction();
            String stringExtra = intent.getStringExtra("EXTRA_INTENT_TYPE");
            if ("com.huawei.android.push.intent.HEARTBEAT_RSP_TIMEOUT".equals(action)) {
                if (ag.a(context, "cloudpush_isSupportCollectSocketInfo", false)) {
                    context.sendBroadcast(new Intent("com.huawei.android.push.intent.SOCKET_INFO").putExtra("socket_add_info", "heart beat time out.").setPackage(context.getPackageName()));
                }
                ChannelMgr.g(context).c(intent);
            } else if (a(context, intent, action, stringExtra)) {
                ChannelMgr.g(context).c(intent);
            } else if (b(context, action, stringExtra)) {
                e(context, action);
            } else if ("com.huawei.android.push.intent.CHANNEL_CLOSED".equals(action)) {
                aw.i("PushLog2841", "receive the channal closed action.");
                stringExtra = "";
                action = "";
                Serializable serializableExtra = intent.getSerializableExtra("push_exception");
                if (serializableExtra != null) {
                    action = serializableExtra.toString();
                }
                context.sendBroadcast(new Intent("com.huawei.android.push.intent.SOCKET_INFO").putExtra("socket_event_type", 0).putExtra("socket_next_connect_time", stringExtra).putExtra("socket_exception", action).setPackage(context.getPackageName()));
            } else if ("com.huawei.android.push.intent.CONNECTING".equals(action)) {
                context.sendBroadcast(new Intent("com.huawei.android.push.intent.SOCKET_INFO").putExtra("socket_event_type", 2).setPackage(context.getPackageName()));
            } else if ("com.huawei.android.push.intent.CONNECTED".equals(action)) {
                context.sendBroadcast(new Intent("com.huawei.android.push.intent.SOCKET_INFO").putExtra("socket_event_type", 1).setPackage(context.getPackageName()));
            } else if ("com.huawei.intent.action.PUSH_OFF".equals(action) || "com.huawei.android.push.intent.inner.STOP_SERVICE".equals(action)) {
                stringExtra = intent.getStringExtra("Remote_Package_Name");
                if (stringExtra == null || !stringExtra.equals(context.getPackageName())) {
                    aw.d("PushLog2841", "need stop PkgName:" + stringExtra + " is not me, need not stop!");
                    return;
                }
                if (ag.a(context, "cloudpush_isSupportCollectSocketInfo", false)) {
                    context.sendBroadcast(new Intent("com.huawei.android.push.intent.SOCKET_INFO").putExtra("socket_add_info", "receive push off action.").setPackage(context.getPackageName()));
                }
                ChannelMgr.g(context).aU();
                if ("com.huawei.intent.action.PUSH_OFF".equals(action)) {
                    if (PushBastet.ac(context).ca()) {
                        PushBastet.ac(context).cb();
                    }
                    PushService.d();
                }
            } else if ("android.intent.action.BATTERY_CHANGED".equals(action)) {
                int intExtra = intent.getIntExtra("status", 1);
                ChannelMgr.h(context).a(intExtra);
                if (2 == intExtra || 5 == intExtra) {
                    aw.d("PushLog2841", "current battery is charging!");
                } else {
                    aw.d("PushLog2841", "current battery no charging :" + intExtra);
                }
            }
        } catch (Throwable e) {
            aw.a("PushLog2841", e.toString(), e);
        }
    }
}
