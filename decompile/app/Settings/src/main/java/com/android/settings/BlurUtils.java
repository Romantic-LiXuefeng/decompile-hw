package com.android.settings;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.renderscript.Allocation;
import android.renderscript.Allocation.MipmapControl;
import android.renderscript.Element;
import android.renderscript.RSInvalidStateException;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceControl;
import android.view.WindowManager;

public class BlurUtils {
    public static Bitmap blurImage(Context ctx, Bitmap input, Bitmap output, int radius) {
        if (ctx == null || input == null || output == null || radius <= 0 || radius > 25) {
            Log.w("BlurUtils", "blurImage() parameter is incorrect:" + ctx + "," + input + "," + output + "," + radius);
            return null;
        }
        Context c = ctx.getApplicationContext();
        if (c != null) {
            ctx = c;
        }
        RenderScript mRs = RenderScript.create(ctx);
        if (mRs == null) {
            Log.w("BlurUtils", "blurImage() mRs consturct error");
            return null;
        }
        Allocation tmpIn = Allocation.createFromBitmap(mRs, input, MipmapControl.MIPMAP_NONE, 1);
        Allocation tmpOut = Allocation.createTyped(mRs, tmpIn.getType());
        if (output.getConfig() == Config.ARGB_8888) {
            Element elementIn = tmpIn.getType().getElement();
            Element elementOut = tmpOut.getType().getElement();
            if (!(elementIn.isCompatible(Element.RGBA_8888(mRs)) && elementOut.isCompatible(Element.RGBA_8888(mRs)))) {
                Log.w("BlurUtils", "Temp input Allocation kind is " + elementIn.getDataKind() + ", type " + elementIn.getDataType() + " of " + elementIn.getBytesSize() + " bytes." + "And Temp output Allocation kind is " + elementOut.getDataKind() + ", type " + elementOut.getDataType() + " of " + elementOut.getBytesSize() + " bytes." + " output bitmap was ARGB_8888.");
                return null;
            }
        }
        ScriptIntrinsicBlur mScriptIntrinsic = ScriptIntrinsicBlur.create(mRs, Element.U8_4(mRs));
        mScriptIntrinsic.setRadius((float) radius);
        mScriptIntrinsic.setInput(tmpIn);
        mScriptIntrinsic.forEach(tmpOut);
        tmpOut.copyTo(output);
        try {
            tmpIn.destroy();
        } catch (RSInvalidStateException e) {
            e.printStackTrace();
        }
        try {
            tmpOut.destroy();
        } catch (RSInvalidStateException e2) {
            e2.printStackTrace();
        }
        mRs.destroy();
        return output;
    }

    public static Bitmap screenShotBitmap(Context ctx, boolean withNavigation) {
        DisplayMetrics displayMetricsFull = new DisplayMetrics();
        DisplayMetrics displayMetricsBody = new DisplayMetrics();
        Display display = ((WindowManager) ctx.getSystemService("window")).getDefaultDisplay();
        display.getRealMetrics(displayMetricsFull);
        display.getMetrics(displayMetricsBody);
        Log.d("terigle", "width pixel is:" + displayMetricsFull.widthPixels);
        Log.d("terigle", "height pixel is:" + displayMetricsFull.heightPixels);
        int[] dims = new int[]{(displayMetricsFull.widthPixels / 2) * 2, (displayMetricsFull.heightPixels / 2) * 2};
        Bitmap bitmap = SurfaceControl.screenshot(dims[0], dims[1]);
        if (bitmap == null) {
            Log.e("BlurUtils", "screenShotBitmap error bitmap is null");
            return null;
        }
        if (!withNavigation) {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, displayMetricsBody.widthPixels, displayMetricsBody.heightPixels);
        }
        bitmap.prepareToDraw();
        if (!(bitmap.isMutable() && bitmap.getConfig() == Config.ARGB_8888)) {
            Bitmap tmp = bitmap.copy(Config.ARGB_8888, true);
            bitmap.recycle();
            bitmap = tmp;
        }
        return bitmap;
    }
}
