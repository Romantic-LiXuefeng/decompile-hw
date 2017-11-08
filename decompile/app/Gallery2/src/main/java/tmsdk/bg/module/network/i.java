package tmsdk.bg.module.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import com.android.gallery3d.gadget.XmlUtils;
import com.autonavi.amap.mapcore.ERROR_CODE;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import tmsdk.bg.creator.BaseManagerB;
import tmsdk.common.ErrorCode;
import tmsdk.common.TMSDKContext;
import tmsdk.common.exception.NetWorkException;
import tmsdk.common.module.intelli_sms.SmsCheckResult;
import tmsdk.common.tcc.TrafficSmsParser;
import tmsdk.common.tcc.TrafficSmsParser.MatchRule;
import tmsdk.common.utils.d;
import tmsdk.common.utils.f;
import tmsdk.common.utils.h;
import tmsdk.common.utils.l;
import tmsdkobf.ar;
import tmsdkobf.as;
import tmsdkobf.at;
import tmsdkobf.au;
import tmsdkobf.av;
import tmsdkobf.aw;
import tmsdkobf.ax;
import tmsdkobf.ay;
import tmsdkobf.az;
import tmsdkobf.ba;
import tmsdkobf.bc;
import tmsdkobf.bd;
import tmsdkobf.be;
import tmsdkobf.bf;
import tmsdkobf.bg;
import tmsdkobf.bi;
import tmsdkobf.cz;
import tmsdkobf.fq;
import tmsdkobf.fs;
import tmsdkobf.jq;
import tmsdkobf.ks;
import tmsdkobf.ku;
import tmsdkobf.lg;
import tmsdkobf.li;
import tmsdkobf.lw;
import tmsdkobf.lx;
import tmsdkobf.mu;
import tmsdkobf.pf;
import tmsdkobf.pl;

/* compiled from: Unknown */
class i extends BaseManagerB {
    private Context mContext;
    li oy = new li(this) {
        final /* synthetic */ i yT;

        {
            this.yT = r1;
        }

        public pl<Long, Integer, fs> a(int i, long j, int i2, fs fsVar) {
            d.e("TrafficCorrection", "【push listener收到push消息】--cmdId:[" + i2 + "]pushId:[" + j + "]seqNo:[" + i + "]guid[" + this.yT.yP.c() + "]");
            switch (i2) {
                case 11006:
                    if (fsVar != null) {
                        d.e("TrafficCorrection", "[流量push消息]--启动worker线程跑执行");
                        final int i3 = i2;
                        final int i4 = i;
                        final long j2 = j;
                        final fs fsVar2 = fsVar;
                        Thread thread = new Thread(new Runnable(this) {
                            final /* synthetic */ AnonymousClass2 yY;

                            public void run() {
                                lw lwVar = new lw();
                                lwVar.H = i3;
                                lwVar.dG = i4;
                                lwVar.dF = j2;
                                lwVar.ol = fsVar2;
                                ba baVar = (ba) lwVar.ol;
                                String str = baVar.imsi;
                                int i = baVar.bC;
                                d.e("TrafficCorrection", "[执行push消息]--个数:[" + baVar.bW.size() + "]--cloudimsi:[" + str + "]卡槽:[" + i + "]seqNo:[" + lwVar.dG + "]pushId:[" + lwVar.dF + "]");
                                int i2 = (i == 0 || i == 1) ? i : 0;
                                String a = this.yY.yT.bn(i2);
                                if (str == null) {
                                    str = "";
                                }
                                String str2 = str;
                                if (a == null) {
                                    a = "";
                                }
                                boolean z = ("".equals(str2) || "".equals(a) || !a.equals(str2)) ? false : true;
                                if ("".equals(str2) && "".equals(a)) {
                                    z = true;
                                }
                                d.e("TrafficCorrection", "isImsiOK:[" + z + "]");
                                fs avVar = new av();
                                avVar.bL = new ArrayList();
                                avVar.imsi = a;
                                avVar.bC = i2;
                                for (int i3 = 0; i3 < baVar.bW.size(); i3++) {
                                    ax axVar = (ax) baVar.bW.get(i3);
                                    d.e("TrafficCorrection", "[" + lwVar.dG + "]开始执行第[" + (i3 + 1) + "]条push指令:[" + axVar + "]");
                                    boolean a2 = (z && axVar != null) ? this.yY.yT.a(i2, axVar, str2) : false;
                                    d.e("TrafficCorrection", "]" + lwVar.dG + "]指令执行结果:[" + a2 + "]");
                                    ay ayVar = new ay();
                                    ayVar.bS = axVar;
                                    ayVar.bT = a2;
                                    avVar.bL.add(ayVar);
                                }
                                d.e("TrafficCorrection", "【push消息处理完毕】全部指令执行结束--[upload]业务回包imsi:[" + avVar.imsi + "]卡槽:[" + avVar.bC + "]");
                                this.yY.yT.yP.b(lwVar.dG, lwVar.dF, 11006, avVar);
                            }
                        });
                        thread.setName("pushImpl");
                        thread.start();
                        break;
                    }
                    d.e("TrafficCorrection", "push == null结束");
                    return null;
            }
            return null;
        }
    };
    public final int yI = FragmentTransaction.TRANSIT_FRAGMENT_OPEN;
    public final int yJ = 4098;
    public final int yK = FragmentTransaction.TRANSIT_FRAGMENT_FADE;
    public final int yL = 4100;
    public final int yM = 4101;
    public final int yN = 4102;
    Handler yO;
    private pf yP;
    private ITrafficCorrectionListener yQ = null;
    private int yR = 2;
    private int yS = 3;

    /* compiled from: Unknown */
    class a {
        final /* synthetic */ i yT;
        int zk;
        int zl;
        int zm;
        int zn;

        a(i iVar) {
            this.yT = iVar;
        }
    }

    i() {
    }

    private int a(int i, List<MatchRule> list, String str, String str2, boolean z) {
        int i2;
        int i3 = 0;
        d.e("TrafficCorrection", "[开始模块匹配] body：[ " + str2 + "]isUsed:[" + z + "]matchRules:[" + list + "]");
        MatchRule matchRule = (MatchRule) list.get(0);
        MatchRule matchRule2 = new MatchRule(matchRule.unit, matchRule.type, matchRule.prefix, matchRule.postfix);
        if (list.size() > 1) {
            matchRule2.prefix += "&#" + matchRule2.unit + "&#" + matchRule.type;
        }
        for (int i4 = 1; i4 < list.size(); i4++) {
            matchRule2.prefix += ("&#" + ((MatchRule) list.get(i4)).prefix + "&#" + ((MatchRule) list.get(i4)).unit + "&#" + ((MatchRule) list.get(i4)).type);
            matchRule2.postfix += ("&#" + ((MatchRule) list.get(i4)).postfix);
        }
        d.e("TrafficCorrection", "prefix: " + matchRule2.prefix);
        d.e("TrafficCorrection", "postfix: " + matchRule2.postfix);
        AtomicInteger atomicInteger = new AtomicInteger();
        if (TrafficSmsParser.getNumberEntrance(str, str2, matchRule2, atomicInteger) != 0) {
            i2 = 0;
        } else {
            i2 = atomicInteger.get() + 0;
            i3 = 1;
        }
        if (i3 == 0) {
            d.e("TrafficCorrection", "[匹配不成功]");
            return 9;
        }
        d.e("TrafficCorrection", "[匹配成功]isUsed:[" + z + "]数据为：[" + i2 + "]");
        a aVar = new a(this);
        aVar.zk = i;
        aVar.zl = 1;
        aVar.zn = i2;
        if (z) {
            i2 = 6;
            aVar.zm = 258;
        } else {
            i2 = 7;
            aVar.zm = 257;
        }
        this.yO.sendMessage(this.yO.obtainMessage(FragmentTransaction.TRANSIT_FRAGMENT_OPEN, aVar));
        return i2;
    }

    private String a(int i, fs fsVar) {
        switch (i) {
            case ERROR_CODE.CONN_CREATE_FALSE /*1001*/:
                aw awVar = (aw) fsVar;
                return "\nsimcard:" + awVar.bC + " imsi:" + awVar.imsi + "\n sms:" + awVar.sms + "\n startType:" + awVar.bI + "\n time:" + awVar.time + "\n code:" + awVar.bF + "\n vecTraffic:" + awVar.bD;
            case 1002:
                au auVar = (au) fsVar;
                return "\nsimcard:" + auVar.bC + " imsi:" + auVar.imsi + "\n method:" + auVar.bG + "\n tplate:" + auVar.bH + "\n sms:" + auVar.sms + "\n startType:" + auVar.bI + "\n time:" + auVar.time + "\n type:" + auVar.type + "\n code:" + auVar.bF + "\n vecTraffic:" + auVar.bD;
            case 1003:
                as asVar = (as) fsVar;
                return "\n simcard:" + asVar.bC + " imsi:" + asVar.imsi + "\n vecTraffic:" + asVar.bD;
            case ERROR_CODE.CANCEL_ERROR /*1004*/:
                ar arVar = (ar) fsVar;
                return "\n simcard:" + arVar.bC + " imsi:" + arVar.imsi + "\n authenResult:" + arVar.bB + "\n skey:" + arVar.bA;
            case 1007:
                at atVar = (at) fsVar;
                return "\n simcard:" + atVar.bC + " imsi:" + atVar.imsi + "getType:" + atVar.ap;
            case 1008:
                bi biVar = (bi) fsVar;
                String str = "\nsimcard:" + biVar.bC + "\n getParamType:" + biVar.cA + " fixMethod:" + biVar.cv + "\n fixTimeLocal:" + biVar.cz + "\n fixTimes:" + biVar.cu + "\n frequence:" + biVar.cy + "\n imsi:" + biVar.imsi + "\n status:" + biVar.status + "\n timeOutNum:" + biVar.cw + "\n queryCode:" + biVar.cx;
                return biVar.cx != null ? str + "\n port:" + biVar.cx.port + ", code:" + biVar.cx.bV : str;
            default:
                return "";
        }
    }

    private void a(int i, int i2, int i3, int i4) {
        d.e("TrafficCorrection", "[开始短信校正]");
        if (bm(i)) {
            this.yR = i2;
            this.yS = i3;
            if (this.yQ != null) {
                d.e("TrafficCorrection", "[通知使用者去发生查询短信]");
                this.yO.sendMessage(this.yO.obtainMessage(4098, i, 0));
            }
            return;
        }
        j(i, i4);
    }

    private void a(int i, String str, String str2) {
        int i2 = 1;
        int i3 = 0;
        String str3 = "";
        cz iw = f.iw();
        boolean z = iw == cz.gF || iw == cz.gE;
        d.e("TrafficCorrection", "doValify--skey:[" + str + "]url:[" + str2 + "]isGPRS:[" + z + "]");
        if (z) {
            try {
                mu cA = mu.cA(str2);
                cA.fc();
                if (cA.getResponseCode() != SmsCheckResult.ESCT_200) {
                    str = str3;
                    i3 = 2;
                }
                str3 = str;
                i2 = i3;
            } catch (NetWorkException e) {
                d.e("TrafficCorrection", "doValify--networkException:" + e.getMessage());
                i2 = 2;
            }
        }
        d.e("TrafficCorrection", "doValify--resultSkey:[" + str3 + "]errorcode:[" + i2 + "]");
        if (str3 == null) {
            str3 = "";
        }
        fs arVar = new ar();
        arVar.imsi = bn(i);
        arVar.bB = i2;
        arVar.bA = str3;
        arVar.bC = i;
        d.e("TrafficCorrection", "[upload]-[" + bp(ERROR_CODE.CANCEL_ERROR) + "]内容:[" + a((int) ERROR_CODE.CANCEL_ERROR, arVar) + "]");
        this.yP.a(ERROR_CODE.CANCEL_ERROR, arVar, null, 2, null);
    }

    private void a(int i, ax axVar, String str, int i2) {
        a aVar = null;
        if (this.yQ != null) {
            if (axVar.bO == 4) {
                aVar = new a(this);
                aVar.zm = 257;
            } else if (axVar.bO == 3) {
                aVar = new a(this);
                aVar.zm = 258;
            } else if (axVar.bO == 6) {
                aVar = new a(this);
                aVar.zm = 259;
            }
            if (aVar != null) {
                aVar.zk = i;
                aVar.zl = i2;
                aVar.zn = Integer.valueOf(str).intValue();
                this.yO.sendMessage(this.yO.obtainMessage(FragmentTransaction.TRANSIT_FRAGMENT_OPEN, aVar));
            }
        }
    }

    private boolean a(final int i, ax axVar, String str) {
        String str2 = axVar.bP;
        d.e("TrafficCorrection", "处理push卡槽:[" + i + "] order.orderType:[" + axVar.bN + "](" + bq(axVar.bN) + ") content:[" + str2 + "]");
        j jVar = new j(i);
        final fs lxVar;
        int i2;
        switch (axVar.bN) {
            case 1:
                if (str2 != null && !"".equals(str2)) {
                    jVar.ck(str2);
                    break;
                }
                return false;
            case 2:
                if (str2 == null || "".equals(str2)) {
                    return false;
                }
                try {
                    jVar.bt(Integer.valueOf(str2).intValue());
                    break;
                } catch (NumberFormatException e) {
                    d.c("TrafficCorrection", "[Error]EOrder.EO_ChangeFrequncy" + e.getMessage());
                    return false;
                }
                break;
            case 3:
                jVar.A(false);
                break;
            case 4:
                if (str2 != null && !"".equals(str2)) {
                    jVar.ci(str2);
                    break;
                }
                return false;
            case 5:
                jVar.A(true);
                break;
            case 6:
                if (str2 != null && !"".equals(str2)) {
                    fs bfVar = new bf();
                    if (a(str2.getBytes(), bfVar) && bfVar.cn != null) {
                        jVar.o(bfVar.cn);
                        break;
                    }
                }
                return false;
                break;
            case 7:
                if (str2 != null && !"".equals(str2)) {
                    jVar.cl(str2);
                    break;
                }
                return false;
            case 8:
                if (str2 == null || "".equals(str2)) {
                    return false;
                }
                try {
                    jVar.bs(Integer.valueOf(str2).intValue());
                    break;
                } catch (NumberFormatException e2) {
                    d.c("TrafficCorrection", "[Error]EO_ChangeTimeOut: " + e2.getMessage());
                    return false;
                }
                break;
            case 9:
                if (str2 != null && !"".equals(str2)) {
                    jVar.cj(str2);
                    break;
                }
                return false;
            case 10:
                if (str2 != null && !"".equals(str2)) {
                    a(i, axVar, str2, 1);
                    break;
                }
                return false;
                break;
            case 11:
                a(i, 1, 0, 7);
                break;
            case 12:
                if (str2 == null || "".equals(str2)) {
                    return false;
                }
                lxVar = new lx();
                if (a(str2.getBytes(), lxVar)) {
                    jq.ct().a(new Runnable(this) {
                        final /* synthetic */ i yT;

                        public void run() {
                            this.yT.a(i, lxVar.bA, lxVar.url);
                        }
                    }, "AuthenticationInfo_Check");
                    break;
                }
                return false;
                break;
            case 14:
                if (str2 != null && !"".equals(str2)) {
                    a(i, axVar, str2, 2);
                    break;
                }
                return false;
            case 16:
                if (str2 != null && !"".equals(str2)) {
                    jVar.cm(str2);
                    break;
                }
                return false;
            case 19:
                if (str2 != null && !"".equals(str2)) {
                    a(i, axVar, str2, 3);
                    break;
                }
                return false;
            case 20:
                if (axVar.bQ != null && !"".equals(axVar.bQ)) {
                    lxVar = new bg();
                    if (a(axVar.bQ, lxVar)) {
                        d.e("TrafficCorrection", "push详情信息timeNow:[" + lxVar.cp + "]");
                        if (lxVar.cq != null) {
                            Iterator it = lxVar.cq.iterator();
                            int i3 = 0;
                            while (it.hasNext()) {
                                bd bdVar = (bd) it.next();
                                if (bdVar.cd != null) {
                                    bc bcVar = bdVar.cd;
                                    d.e("TrafficCorrection", "[" + i3 + "]father.parDesc[" + j(bcVar.bY) + "]father.useNum[" + j(bcVar.bZ) + "]father.usePer[" + bcVar.ca + "]");
                                }
                                if (bdVar.ce != null) {
                                    Iterator it2 = bdVar.ce.iterator();
                                    i2 = 0;
                                    while (it2.hasNext()) {
                                        bc bcVar2 = (bc) it2.next();
                                        d.e("TrafficCorrection", "[" + i3 + "][" + i2 + "]son.parDesc[" + j(bcVar2.bY) + "]son.useNum[" + j(bcVar2.bZ) + "]son.usePer[" + bcVar2.ca + "]");
                                        i2++;
                                    }
                                }
                                i3++;
                            }
                            break;
                        }
                    }
                }
                return false;
                break;
            case 21:
                d.e("TrafficCorrection", "下发profile");
                if (axVar.bQ == null || "".equals(axVar.bQ)) {
                    return false;
                }
                fs beVar = new be();
                if (a(axVar.bQ, beVar)) {
                    i2 = beVar.province;
                    int i4 = beVar.city;
                    String bo = bo(beVar.ch);
                    int i5 = beVar.ci;
                    d.e("TrafficCorrection", "province:[" + i2 + "]city[" + i4 + "]carry[" + bo + "]brand[" + i5 + "]payDay[" + beVar.ck + "]");
                    jVar.a(str, i2, i4, bo, i5);
                    ProfileInfo profileInfo = new ProfileInfo();
                    profileInfo.imsi = str;
                    profileInfo.province = i2;
                    profileInfo.city = i4;
                    profileInfo.carry = bo;
                    profileInfo.brand = i5;
                    Message obtainMessage = this.yO.obtainMessage(4102, i, 0);
                    obtainMessage.obj = profileInfo;
                    this.yO.sendMessage(obtainMessage);
                    break;
                }
                return false;
                break;
        }
        return true;
    }

    private boolean a(byte[] bArr, fs fsVar) {
        boolean z = false;
        if (bArr == null || fsVar == null) {
            return false;
        }
        fq fqVar = new fq(bArr);
        fqVar.ae(XmlUtils.INPUT_ENCODING);
        try {
            fsVar.readFrom(fqVar);
            z = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return z;
    }

    private synchronized void aK() {
        d.e("TrafficCorrection", "[注册push listener]");
        this.yP.u(11006, 2);
        this.yP.a(11006, new ba(), 2, this.oy);
    }

    private int b(int i, String str, String str2) {
        d.e("TrafficCorrection", "本地模板分析短信");
        if (TrafficSmsParser.getWrongSmsType(str, str2) == 0) {
            j jVar = new j(i);
            List bu = jVar.bu(2);
            List bu2 = jVar.bu(1);
            if (bu.isEmpty() && bu2.isEmpty()) {
                d.e("TrafficCorrection", "模板为空");
                l(i, ErrorCode.ERR_CORRECTION_LOCAL_NO_TEMPLATE);
                b(i, 3, str2);
                return 0;
            }
            int i2;
            boolean z;
            if (bu2.isEmpty()) {
                d.e("TrafficCorrection", "剩余模板为空");
                i2 = 9;
                z = true;
            } else {
                i2 = a(i, bu2, str, str2, false);
                z = 9 == i2;
            }
            if (z && !bu.isEmpty()) {
                i2 = a(i, bu, str, str2, true);
            }
            if (i2 == 6 || i2 == 7) {
                d.e("TrafficCorrection", "匹配成功");
                l(i, 0);
            } else {
                l(i, ErrorCode.ERR_CORRECTION_LOCAL_TEMPLATE_UNMATCH);
                b(i, i2, str2);
                d.e("TrafficCorrection", "匹配失败");
            }
            return 0;
        }
        d.e("TrafficCorrection", "[error]TrafficSmsParser.getWrongSmsType异常");
        l(i, ErrorCode.ERR_CORRECTION_BAD_SMS);
        return ErrorCode.ERR_CORRECTION_BAD_SMS;
    }

    private void b(final int i, int i2, String str) {
        d.e("TrafficCorrection", "uploadLocalCorrectionState-simIndex:[" + i + "]fixType:[" + i2 + "]smsBody:[" + str + "]");
        j jVar = new j(i);
        ArrayList arrayList = new ArrayList();
        bf bfVar = new bf();
        bfVar.cn = arrayList;
        ArrayList arrayList2 = new ArrayList();
        az azVar = new az();
        if (azVar != null) {
            azVar.bV = jVar.ed();
            azVar.port = jVar.ee();
        }
        fs auVar = new au();
        auVar.imsi = bn(i);
        auVar.bF = azVar;
        auVar.bG = this.yS;
        auVar.sms = str;
        auVar.bI = this.yR;
        auVar.bH = bfVar;
        auVar.type = i2;
        auVar.bD = arrayList2;
        auVar.bC = i;
        d.e("TrafficCorrection", "[upload]-[" + bp(1002) + "],内容：[" + a(1002, auVar) + "]");
        this.yP.a(1002, auVar, null, 2, new lg(this) {
            final /* synthetic */ i yT;

            public void onFinish(int i, int i2, int i3, int i4, fs fsVar) {
                if (i3 != 0) {
                    this.yT.l(i, ErrorCode.ERR_CORRECTION_FEEDBACK_UPLOAD_FAIL);
                }
            }
        });
    }

    private int bj(int i) {
        d.e("TrafficCorrection", "[profile上报][Beg]");
        j jVar = new j(i);
        final String dW = jVar.dW();
        final String dX = jVar.dX();
        final String dY = jVar.dY();
        final String dZ = jVar.dZ();
        final String bn = bn(i);
        final int ea = jVar.ea();
        try {
            int intValue = Integer.valueOf(dW).intValue();
            int intValue2 = Integer.valueOf(dX).intValue();
            int intValue3 = Integer.valueOf(dZ).intValue();
            int cc = cc(dY);
            if (cc != -1) {
                int i2;
                int i3;
                int i4;
                int i5;
                int i6;
                final int i7 = i;
                ku.dq().a(new ks(this) {
                    final /* synthetic */ i yT;

                    public void a(ArrayList<fs> arrayList, int i) {
                        this.yT.yO.sendMessage(this.yT.yO.obtainMessage(FragmentTransaction.TRANSIT_FRAGMENT_FADE, i7, 0, this));
                        Object obj = "";
                        if (i == 0) {
                            obj = this.yT.yP.c() + "$" + bn + "$" + dW + dX + dY + dZ + "$" + ea;
                        }
                        this.yT.yO.sendMessage(this.yT.yO.obtainMessage(4100, i7, 0, obj));
                        d.e("TrafficCorrection", "profile上报结果[" + i + "]guid:[" + this.yT.yP.c() + "]");
                    }
                });
                if (1 != i) {
                    i2 = 2003;
                    i3 = 2002;
                    i4 = 2004;
                    i7 = 2005;
                    i5 = 2007;
                    i6 = 2008;
                } else {
                    i2 = 2011;
                    i3 = 2010;
                    i4 = 2012;
                    i7 = 2013;
                    i5 = 2015;
                    i6 = 2016;
                }
                ku.dq().i(i3, Integer.valueOf(intValue).intValue());
                ku.dq().i(i2, Integer.valueOf(intValue2).intValue());
                ku.dq().i(i4, cc);
                ku.dq().i(i7, Integer.valueOf(intValue3).intValue());
                ku.dq().i(i5, ea);
                ku.dq().c(i6, true);
                d.e("TrafficCorrection", "[profile上报][End]");
                return 0;
            }
            d.e("TrafficCorrection", "[error] upload profile Operator error");
            return ErrorCode.ERR_CORRECTION_PROFILE_ILLEGAL;
        } catch (NumberFormatException e) {
            d.e("TrafficCorrection", "[error] upload profile NumberFormatException:" + e.getMessage());
            return ErrorCode.ERR_CORRECTION_PROFILE_ILLEGAL;
        }
    }

    private boolean bk(int i) {
        j jVar = new j(i);
        String dW = jVar.dW();
        String dX = jVar.dX();
        String dY = jVar.dY();
        String dZ = jVar.dZ();
        d.e("TrafficCorrection", "[检查省、市、运营商、品牌代码]province:[" + dW + "]city:[" + dX + "]carry:[" + dY + "]brand:[" + dZ + "]");
        if (!l.dm(dW) && !l.dm(dX) && !l.dm(dY) && !l.dm(dZ)) {
            return true;
        }
        d.e("TrafficCorrection", "[error]省、市、运营商、品牌代码存在为空");
        return false;
    }

    private boolean bl(int i) {
        j jVar = new j(i);
        String str = this.yP.c() + "$" + bn(i) + "$" + jVar.dW() + jVar.dX() + jVar.dY() + jVar.dZ() + "$" + jVar.ea();
        String eb = jVar.eb();
        d.g("TrafficCorrection", "currentInfo:[" + str + "]lastSuccessInfo:[" + eb + "]");
        return str.compareTo(eb) != 0;
    }

    private boolean bm(int i) {
        j jVar = new j(i);
        CharSequence ed = jVar.ed();
        CharSequence ee = jVar.ee();
        d.e("TrafficCorrection", "[检查查询码与端口号]queryCode:[" + ed + "]queryPort:[" + ee + "]");
        if (!TextUtils.isEmpty(ed) && !TextUtils.isEmpty(ee)) {
            return true;
        }
        d.e("TrafficCorrection", "[error]查询码或端口号不合法");
        return false;
    }

    private String bn(int i) {
        String str = "";
        if (jq.cx() != null) {
            str = jq.cx().getIMSI(i);
        } else if (i == 0) {
            str = h.D(TMSDKContext.getApplicaionContext());
        }
        d.e("TrafficCorrection", "getIMSIBySimSlot:[" + i + "][" + str + "");
        return str;
    }

    private String bo(int i) {
        return i != 2 ? i != 1 ? i != 3 ? "" : "TELECOM" : "UNICOM" : "CMCC";
    }

    private String bp(int i) {
        switch (i) {
            case ERROR_CODE.CONN_CREATE_FALSE /*1001*/:
                return "通过查询码获取到流量短信处理";
            case 1002:
                return "本地校正后上报";
            case 1003:
                return "手动修改上报";
            case ERROR_CODE.CANCEL_ERROR /*1004*/:
                return "身份验证";
            case 1007:
                return "手动获取云端数据";
            case 1008:
                return "纠错上报";
            default:
                return "";
        }
    }

    private String bq(int i) {
        switch (i) {
            case 1:
                return "校正类型";
            case 2:
                return "调整校正频率：例如一天校正一次调整为3天校正一次";
            case 3:
                return "复活指令：关闭校正的用户复活。";
            case 4:
                return "直接替换终端当前使用的查询码：换查询码时使用";
            case 5:
                return "暂停校正";
            case 6:
                return "下发模板";
            case 7:
                return "调整校正时机:允许server校正的时间段调整";
            case 8:
                return "替换超时时间";
            case 9:
                return "更换监听运营商端口。";
            case 10:
                return "下发GPRS流量值";
            case 11:
                return "立即执行一次校正";
            case 12:
                return "下发身份认证信息(url+sky)，终端收到该信息后进行省份认证";
            case 13:
                return "下发TD流量值";
            case 14:
                return "下发闲时流量值";
            case 15:
                return "下发一串内容，这串内容需要终端展示给用户看；";
            case 16:
                return "调整校正时机:允许Local校正的时间段调整";
            case 17:
                return "下发推广链接";
            case 20:
                return "流量详情";
            case 21:
                return "下发profile";
            default:
                return "";
        }
    }

    private int cc(String str) {
        return !"CMCC".equals(str) ? !"UNICOM".equals(str) ? !"TELECOM".equals(str) ? -1 : 3 : 1 : 2;
    }

    private String j(byte[] bArr) {
        return bArr != null ? new String(bArr) : "";
    }

    private void j(final int i, int i2) {
        int i3 = 0;
        d.e("TrafficCorrection", "[uploadParam]simIndex:[" + i + "]");
        j jVar = new j(i);
        az azVar = new az();
        azVar.bV = jVar.ed();
        azVar.port = jVar.ee();
        fs biVar = new bi();
        biVar.imsi = bn(i);
        biVar.cv = jVar.ef();
        biVar.cz = jVar.eg();
        biVar.cu = jVar.eh();
        biVar.cy = jVar.ei();
        biVar.cx = azVar;
        if (jVar.ej()) {
            i3 = 2;
        }
        biVar.status = i3;
        biVar.cA = i2;
        biVar.cw = jVar.ec();
        biVar.bC = i;
        d.e("TrafficCorrection", "[upload]-[" + bp(1008) + "],内容：[" + a(1008, biVar) + "]");
        this.yP.a(1008, biVar, null, 2, new lg(this) {
            final /* synthetic */ i yT;

            public void onFinish(int i, int i2, int i3, int i4, fs fsVar) {
                if (i3 != 0) {
                    this.yT.l(i, ErrorCode.ERR_CORRECTION_FEEDBACK_UPLOAD_FAIL);
                }
            }
        });
    }

    private void l(int i, int i2) {
        if (i2 != 0) {
            this.yO.sendMessage(this.yO.obtainMessage(4101, i, i2));
        }
        d.e("TrafficCorrection", "[本次校正流程结束]--重置状态");
    }

    public int a(final int i, String str, final String str2, final String str3, int i2) {
        d.e("TrafficCorrection", "[分析短信]analysisSMS--simIndex:[" + i + "]queryCode:[" + str + "]queryPort:" + str2 + "]smsBody:[" + str3 + "]");
        if (i == 0 || i == 1) {
            if (!(l.dm(str) || l.dm(str2) || l.dm(str3))) {
                az azVar = new az();
                if (azVar != null) {
                    azVar.bV = str;
                    azVar.port = str2;
                }
                if (!f.hv()) {
                    return b(i, str2, str3);
                }
                d.e("TrafficCorrection", "有网络，走云短信");
                fs awVar = new aw();
                awVar.imsi = bn(i);
                awVar.bF = azVar;
                awVar.sms = str3;
                awVar.bI = this.yR;
                awVar.time = i2;
                awVar.bD = new ArrayList();
                awVar.bC = i;
                d.e("TrafficCorrection", "[upload]-[" + bp(ERROR_CODE.CONN_CREATE_FALSE) + "]内容:[" + a((int) ERROR_CODE.CONN_CREATE_FALSE, awVar) + "]");
                this.yP.a(ERROR_CODE.CONN_CREATE_FALSE, awVar, null, 2, new lg(this) {
                    final /* synthetic */ i yT;

                    public void onFinish(int i, int i2, int i3, int i4, fs fsVar) {
                        if (i3 != 0) {
                            d.e("TrafficCorrection", "有网络，上报短信流程失败，走本地短信分析流程");
                            this.yT.b(i, str2, str3);
                        }
                    }
                });
                return 0;
            }
        }
        l(i, -6);
        d.e("TrafficCorrection", "参数错误");
        return -6;
    }

    public int getSingletonType() {
        return 1;
    }

    public boolean k(int i, int i2) {
        Object ef = new j(i).ef();
        if (TextUtils.isEmpty(ef)) {
            return false;
        }
        String[] split = ef.replace("||", "*").split("\\*");
        int i3 = 0;
        while (i3 < split.length) {
            try {
                if (Integer.valueOf(split[i3]).intValue() == i2) {
                    return true;
                }
                i3++;
            } catch (NumberFormatException e) {
            }
        }
        return false;
    }

    public void onCreate(Context context) {
        d.e("TrafficCorrection", "TrafficCorrectionManagerImpl-OnCreate-context:[" + context + "]");
        this.mContext = context;
        this.yP = jq.cu();
        aK();
        this.yO = new Handler(this, Looper.getMainLooper()) {
            final /* synthetic */ i yT;

            public void handleMessage(Message message) {
                if (message.what == FragmentTransaction.TRANSIT_FRAGMENT_OPEN) {
                    a aVar = (a) message.obj;
                    if (this.yT.yQ != null && aVar != null) {
                        d.e("TrafficCorrection", "onTrafficInfoNotify--simIndex:[" + aVar.zk + "]trafficClass:[" + aVar.zl + "]" + "]subClass:[" + aVar.zm + "]" + "]kBytes:[" + aVar.zn + "]");
                        this.yT.yQ.onTrafficInfoNotify(aVar.zk, aVar.zl, aVar.zm, aVar.zn);
                    }
                } else if (message.what != 4098) {
                    if (message.what == FragmentTransaction.TRANSIT_FRAGMENT_FADE) {
                        ku.dq().b((ks) message.obj);
                    } else if (message.what == 4100) {
                        if (TextUtils.isEmpty((String) message.obj)) {
                            d.e("TrafficCorrection", "onError--simIndex:[" + message.arg1 + "]ERR_CORRECTION_PROFILE_UPLOAD_FAIL");
                            if (this.yT.yQ != null) {
                                this.yT.yQ.onError(message.arg1, ErrorCode.ERR_CORRECTION_PROFILE_UPLOAD_FAIL);
                            }
                        }
                        new j(message.arg1).ch((String) message.obj);
                    } else if (message.what != 4101) {
                        if (message.what == 4102 && this.yT.yQ != null) {
                            d.e("TrafficCorrection", "onProfileNotify--simIndex:[" + message.arg1 + "]ProfileInfo:[" + ((ProfileInfo) message.obj).toString() + "]");
                            this.yT.yQ.onProfileNotify(message.arg1, (ProfileInfo) message.obj);
                        }
                    } else if (this.yT.yQ != null) {
                        d.e("TrafficCorrection", "onError--simIndex:[" + message.arg1 + "]errorCode:[" + message.arg2 + "]");
                        this.yT.yQ.onError(message.arg1, message.arg2);
                    }
                } else if (this.yT.yQ != null) {
                    j jVar = new j(message.arg1);
                    String ed = jVar.ed();
                    String ee = jVar.ee();
                    d.e("TrafficCorrection", "onNeedSmsCorrection--simIndex:[" + message.arg1 + "]queryCode:[" + ed + "]queryPort:[" + ee + "]");
                    this.yT.yQ.onNeedSmsCorrection(message.arg1, ed, ee);
                }
            }
        };
    }

    public int requestProfile(int i) {
        d.e("TrafficCorrection", "requestProfile--simIndex:[" + i + "]");
        ProfileInfo c = new j(i).c(i, bn(i));
        if (c.province != -1) {
            Message obtainMessage = this.yO.obtainMessage(4102, i, 0);
            obtainMessage.obj = c;
            this.yO.sendMessage(obtainMessage);
        } else {
            d.e("TrafficCorrection", "本地没有profile信息");
            j(i, 5);
        }
        return 0;
    }

    public int setConfig(int i, String str, String str2, String str3, String str4, int i2) {
        d.e("TrafficCorrection", "[设置省、市、运营商、品牌代码]simIndex:[" + i + "]provinceId:[" + str + "]cityId:[" + str2 + "]carryId:[" + str3 + "]brandId:[" + str4 + "]closingDay:[" + i2 + "]");
        if (i == 0 || i == 1) {
            if (!(l.dm(str) || l.dm(str2) || l.dm(str3) || l.dm(str4))) {
                j jVar = new j(i);
                jVar.cd(str);
                jVar.ce(str2);
                jVar.cf(str3);
                jVar.cg(str4);
                jVar.br(i2);
                return 0;
            }
        }
        d.e("TrafficCorrection", "[error]设置信息有的为空");
        return -6;
    }

    public int setTrafficCorrectionListener(ITrafficCorrectionListener iTrafficCorrectionListener) {
        d.e("TrafficCorrection", "[设置流量校正监听]listener:[" + iTrafficCorrectionListener + "]");
        if (iTrafficCorrectionListener == null) {
            return -6;
        }
        this.yQ = iTrafficCorrectionListener;
        return 0;
    }

    public int startCorrection(int i) {
        d.e("TrafficCorrection", "[开始校正]simIndex:[ " + i + "]");
        if (i != 0 && i != 1) {
            d.e("TrafficCorrection", "[error]simIndex 不合法");
            return -6;
        } else if (bk(i)) {
            j jVar = new j(i);
            if (bl(i)) {
                d.e("TrafficCorrection", "[需要上报profile][上报profile触发后续校正流程]");
                if (!f.hv()) {
                    d.e("TrafficCorrection", "没有网络-[profile上报]结束");
                    return ErrorCode.ERR_CORRECTION_FEEDBACK_UPLOAD_FAIL;
                } else if (bj(i) != 0) {
                    return ErrorCode.ERR_CORRECTION_PROFILE_ILLEGAL;
                }
            }
            boolean hv = f.hv();
            if (!hv) {
                d.e("TrafficCorrection", "[本地模板匹配]simIndex:[" + i + "]");
                a(i, 2, 2, 5);
                return 0;
            } else if (hv && l.dm(jVar.ef())) {
                d.e("TrafficCorrection", "[无校正方式，纠错上报]simIndex:[" + i + "]");
                j(i, 5);
                return 0;
            } else if (k(i, 0)) {
                d.e("TrafficCorrection", "[运营商云端合作校正]simIndex:[" + i + "]");
                fs atVar = new at();
                atVar.ap = 0;
                atVar.bC = i;
                atVar.imsi = bn(i);
                d.e("TrafficCorrection", "[upload]-[" + bp(1007) + "]内容:[" + a(1007, atVar));
                this.yP.a(1007, atVar, null, 2, null);
            } else if (k(i, 3)) {
                d.e("TrafficCorrection", "[短信云端校正]simIndex:[" + i + "]");
                a(i, 2, 3, 5);
            }
            return 0;
        } else {
            d.e("TrafficCorrection", "[error]ErrorCode.ERR_CORRECTION_PROFILE_ILLEGAL");
            return ErrorCode.ERR_CORRECTION_PROFILE_ILLEGAL;
        }
    }
}
