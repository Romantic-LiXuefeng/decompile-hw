package com.huawei.systemmanager.rainbow.comm.request;

import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION;
import android.util.Log;
import com.android.internal.util.Preconditions;
import com.huawei.systemmanager.rainbow.comm.request.ICommonRequest.RequestType;
import com.huawei.systemmanager.rainbow.comm.request.util.DeviceUtil;
import com.huawei.systemmanager.rainbow.comm.request.util.RainbowRequestBasic.BasicCloudField;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class AbsServerRequest extends AbsRequest {
    private static final int MAX_RETRY_TIME = 3;
    private static final String TAG = AbsServerRequest.class.getSimpleName();

    protected abstract void addExtPostRequestParam(Context context, JSONObject jSONObject);

    protected abstract int checkResponseCode(Context context, int i);

    protected abstract String getRequestUrl(RequestType requestType);

    protected abstract void parseResponseAndPost(Context context, JSONObject jSONObject) throws JSONException;

    protected void doRequest(Context ctx) {
        Log.d(TAG, "doRequest");
        int retryCount = 0;
        while (retryCount < 3) {
            if (!innerConnectRequest(ctx)) {
                retryCount++;
            } else {
                return;
            }
        }
        setRequestFailed();
    }

    private boolean innerConnectRequest(Context context) {
        try {
            ICommonRequest conn = JointFactory.getRainbowRequest();
            Preconditions.checkNotNull(conn);
            RequestType type = getRequestType();
            String url = getRequestUrl(type);
            Preconditions.checkNotNull(url);
            String response = "";
            preProcess();
            if (RequestType.REQUEST_GET == type) {
                response = conn.doGetRequest(url);
            } else if (RequestType.REQUEST_POST == type) {
                response = conn.doPostRequest(url, getPostRequestParam(context), getZipEncodingStatus(), context);
            } else {
                Log.w(TAG, "unreachable code");
            }
            return processResponse(context, response);
        } catch (RuntimeException ex) {
            Log.e(TAG, "innerRequest catch RuntimeException: " + ex.getMessage());
            ex.printStackTrace();
            return false;
        } catch (Exception ex2) {
            Log.e(TAG, "innerRequest catch Exception: " + ex2.getMessage());
            ex2.printStackTrace();
            return false;
        }
    }

    private JSONObject getPostRequestParam(Context context) {
        try {
            JSONObject obj = new JSONObject();
            if (context == null) {
                return obj;
            }
            if (isNeedDefaultParam()) {
                obj.put("imei", DeviceUtil.getTelephoneIMEIFromSys(context));
                obj.put("model", Build.MODEL);
                obj.put("os", VERSION.RELEASE);
                obj.put("systemid", Build.DISPLAY);
                obj.put("emui", DeviceUtil.getTelephoneEMUIVersion());
            }
            addExtPostRequestParam(context, obj);
            Log.i(TAG, "getPostRequestParam success");
            return obj;
        } catch (JSONException ex) {
            Log.e(TAG, "getPostRequestParam catch JSONException: " + ex.getMessage());
            throw new RuntimeException("Failed to prepair JSON request parameters in [getPostRequestParam].");
        } catch (NullPointerException ex2) {
            Log.e(TAG, "getPostRequestParam catch NullPointerException: " + ex2.getMessage());
            throw new RuntimeException("Failed to prepair JSON request parameters in [getPostRequestParam].");
        } catch (Exception ex3) {
            Log.e(TAG, "getPostRequestParam catch Exception: " + ex3.getMessage());
            throw new RuntimeException("Failed to prepair JSON request parameters in [getPostRequestParam].");
        }
    }

    protected boolean processResponse(Context ctx, String response) {
        try {
            Preconditions.checkNotNull(response, "processResponse input response should not be empty!");
            JSONObject jsonResponse = new JSONObject(response);
            int nCheckResult = checkResponseCode(ctx, jsonResponse.getInt(getResultCodeFiled()));
            switch (nCheckResult) {
                case 0:
                    parseResponseAndPost(ctx, jsonResponse);
                    return true;
                case 1:
                    parseResponseAndPost(ctx, jsonResponse);
                    return false;
                case 2:
                    return true;
                case 3:
                    return false;
                default:
                    Log.w(TAG, "processResponse: Invalid response code check result = " + nCheckResult);
                    return false;
            }
        } catch (JSONException ex) {
            Log.e(TAG, "processResponse catch JSONException: " + ex.getMessage());
            ex.printStackTrace();
            return false;
        } catch (Exception ex2) {
            Log.e(TAG, "processResponse catch Exception: " + ex2.getMessage());
            return false;
        }
    }

    protected String getResultCodeFiled() {
        return BasicCloudField.SERVRE_RESULT_CODE;
    }

    protected void preProcess() {
    }

    protected RequestType getRequestType() {
        return RequestType.REQUEST_POST;
    }

    protected boolean getZipEncodingStatus() {
        return true;
    }
}
