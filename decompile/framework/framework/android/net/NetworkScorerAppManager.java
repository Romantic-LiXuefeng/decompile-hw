package android.net;

import android.Manifest.permission;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.text.TextUtils;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class NetworkScorerAppManager {
    private static final Intent SCORE_INTENT = new Intent(NetworkScoreManager.ACTION_SCORE_NETWORKS);
    private static final String TAG = "NetworkScorerAppManager";

    public static class NetworkScorerAppData {
        public final String mConfigurationActivityClassName;
        public final String mPackageName;
        public final int mPackageUid;
        public final CharSequence mScorerName;
        public final String mScoringServiceClassName;

        public NetworkScorerAppData(String packageName, int packageUid, CharSequence scorerName, String configurationActivityClassName, String scoringServiceClassName) {
            this.mScorerName = scorerName;
            this.mPackageName = packageName;
            this.mPackageUid = packageUid;
            this.mConfigurationActivityClassName = configurationActivityClassName;
            this.mScoringServiceClassName = scoringServiceClassName;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder("NetworkScorerAppData{");
            sb.append("mPackageName='").append(this.mPackageName).append('\'');
            sb.append(", mPackageUid=").append(this.mPackageUid);
            sb.append(", mScorerName=").append(this.mScorerName);
            sb.append(", mConfigurationActivityClassName='").append(this.mConfigurationActivityClassName).append('\'');
            sb.append(", mScoringServiceClassName='").append(this.mScoringServiceClassName).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }

    private NetworkScorerAppManager() {
    }

    public static Collection<NetworkScorerAppData> getAllValidScorers(Context context) {
        if (UserHandle.getCallingUserId() != 0) {
            return Collections.emptyList();
        }
        List<NetworkScorerAppData> scorers = new ArrayList();
        PackageManager pm = context.getPackageManager();
        for (ResolveInfo receiver : pm.queryBroadcastReceiversAsUser(SCORE_INTENT, 0, 0)) {
            ActivityInfo receiverInfo = receiver.activityInfo;
            if (receiverInfo != null && permission.BROADCAST_NETWORK_PRIVILEGED.equals(receiverInfo.permission) && pm.checkPermission(permission.SCORE_NETWORKS, receiverInfo.packageName) == 0) {
                String configurationActivityClassName = null;
                Intent intent = new Intent(NetworkScoreManager.ACTION_CUSTOM_ENABLE);
                intent.setPackage(receiverInfo.packageName);
                List<ResolveInfo> configActivities = pm.queryIntentActivities(intent, 0);
                if (!(configActivities == null || configActivities.isEmpty())) {
                    ActivityInfo activityInfo = ((ResolveInfo) configActivities.get(0)).activityInfo;
                    if (activityInfo != null) {
                        configurationActivityClassName = activityInfo.name;
                    }
                }
                String scoringServiceClassName = null;
                Intent intent2 = new Intent(NetworkScoreManager.ACTION_SCORE_NETWORKS);
                intent2.setPackage(receiverInfo.packageName);
                ResolveInfo resolveServiceInfo = pm.resolveService(intent2, 0);
                if (!(resolveServiceInfo == null || resolveServiceInfo.serviceInfo == null)) {
                    scoringServiceClassName = resolveServiceInfo.serviceInfo.name;
                }
                scorers.add(new NetworkScorerAppData(receiverInfo.packageName, receiverInfo.applicationInfo.uid, receiverInfo.loadLabel(pm), configurationActivityClassName, scoringServiceClassName));
            }
        }
        return scorers;
    }

    public static NetworkScorerAppData getActiveScorer(Context context) {
        return getScorer(context, Global.getString(context.getContentResolver(), Global.NETWORK_SCORER_APP));
    }

    public static boolean setActiveScorer(Context context, String packageName) {
        String oldPackageName = Global.getString(context.getContentResolver(), Global.NETWORK_SCORER_APP);
        if (TextUtils.equals(oldPackageName, packageName)) {
            return true;
        }
        Log.i(TAG, "Changing network scorer from " + oldPackageName + " to " + packageName);
        if (packageName == null) {
            Global.putString(context.getContentResolver(), Global.NETWORK_SCORER_APP, null);
            return true;
        } else if (getScorer(context, packageName) != null) {
            Global.putString(context.getContentResolver(), Global.NETWORK_SCORER_APP, packageName);
            return true;
        } else {
            Log.w(TAG, "Requested network scorer is not valid: " + packageName);
            return false;
        }
    }

    public static boolean isCallerActiveScorer(Context context, int callingUid) {
        boolean z = false;
        NetworkScorerAppData defaultApp = getActiveScorer(context);
        if (defaultApp == null || callingUid != defaultApp.mPackageUid) {
            return false;
        }
        if (context.checkCallingPermission(permission.SCORE_NETWORKS) == 0) {
            z = true;
        }
        return z;
    }

    public static NetworkScorerAppData getScorer(Context context, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }
        for (NetworkScorerAppData app : getAllValidScorers(context)) {
            if (packageName.equals(app.mPackageName)) {
                return app;
            }
        }
        return null;
    }
}
