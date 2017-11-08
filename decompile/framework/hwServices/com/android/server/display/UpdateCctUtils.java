package com.android.server.display;

import android.os.FileUtils;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;
import android.util.Xml;
import com.android.server.input.HwCircleAnimation;
import huawei.cust.HwCfgFilePolicy;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class UpdateCctUtils {
    private static final float[] CoordinateDefaultPoints = new float[]{0.6379f, 0.3455f, 0.3106f, 0.5872f, 0.1475f, 0.058f, 0.2948f, 0.3117f};
    private static final boolean DEBUG;
    private static final String LCD_PANEL_TYPE_PATH = "/sys/class/graphics/fb0";
    private static final float[] OriginRgb = new float[]{HwCircleAnimation.SMALL_ALPHA, HwCircleAnimation.SMALL_ALPHA, HwCircleAnimation.SMALL_ALPHA};
    private static final String TAG = "UpdateCctUtils";
    private static final int eyepro_colortemperature_min = 5000;
    private boolean isRgbGamma;
    private String mConfigFilePath;
    private ArrayList<Float> mCoordinate;
    private String mLcdPanelName;
    private double[][] mM_inverse;
    private int originCct;
    private double x_gain;
    private double xb;
    private double xg;
    private double xr;
    private double xw;
    private double y_gain;
    private double yb;
    private double yg;
    private double yr;
    private double yw;

    static {
        boolean isLoggable = !Log.HWINFO ? Log.HWModuleLog ? Log.isLoggable(TAG, 4) : false : true;
        DEBUG = isLoggable;
    }

    private String getLcdPanelName() {
        String str = null;
        try {
            str = FileUtils.readTextFile(new File(String.format("%s/lcd_model", new Object[]{LCD_PANEL_TYPE_PATH})), 0, null).trim().replace(' ', '_');
            Slog.d(TAG, "panelName is:" + str);
            return str;
        } catch (IOException e) {
            Slog.e(TAG, "Error reading lcd panel name", e);
            return str;
        }
    }

    private boolean getConfig() throws IOException {
        RuntimeException e;
        Throwable th;
        String version = SystemProperties.get("ro.build.version.emui", null);
        Slog.i(TAG, "HwEyeProtectionControllerImpl getConfig");
        if (TextUtils.isEmpty(version)) {
            Slog.w(TAG, "get ro.build.version.emui failed!");
            return false;
        }
        String[] versionSplited = version.split("EmotionUI_");
        if (versionSplited.length < 2) {
            Slog.w(TAG, "split failed! version = " + version);
            return false;
        }
        if (TextUtils.isEmpty(versionSplited[1])) {
            Slog.w(TAG, "get emuiVersion failed!");
            return false;
        }
        String lcdEyeProtectionConfigFile = "EyeProtectionConfig_" + this.mLcdPanelName + ".xml";
        File xmlFile = HwCfgFilePolicy.getCfgFile(String.format("/xml/lcd/%s/%s", new Object[]{emuiVersion, Utils.HW_EYEPROTECTION_CONFIG_FILE}), 0);
        if (xmlFile == null) {
            xmlFile = HwCfgFilePolicy.getCfgFile(String.format("/xml/lcd/%s", new Object[]{Utils.HW_EYEPROTECTION_CONFIG_FILE}), 0);
            if (xmlFile == null) {
                xmlFile = HwCfgFilePolicy.getCfgFile(String.format("/xml/lcd/%s/%s", new Object[]{emuiVersion, lcdEyeProtectionConfigFile}), 0);
                if (xmlFile == null) {
                    String xmlPath = String.format("/xml/lcd/%s", new Object[]{lcdEyeProtectionConfigFile});
                    xmlFile = HwCfgFilePolicy.getCfgFile(xmlPath, 0);
                    if (xmlFile == null) {
                        Slog.w(TAG, "get xmlFile :" + xmlPath + " failed!");
                        return false;
                    }
                }
            }
        }
        FileInputStream fileInputStream = null;
        try {
            FileInputStream inputStream = new FileInputStream(xmlFile);
            try {
                if (getConfigFromXML(inputStream)) {
                    this.mConfigFilePath = xmlFile.getAbsolutePath();
                    if (DEBUG) {
                        Slog.i(TAG, "get xmlFile :" + this.mConfigFilePath);
                    }
                }
                inputStream.close();
                if (inputStream != null) {
                    inputStream.close();
                }
                return true;
            } catch (FileNotFoundException e2) {
                fileInputStream = inputStream;
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
                return false;
            } catch (IOException e3) {
                fileInputStream = inputStream;
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
                return false;
            } catch (RuntimeException e4) {
                e = e4;
                fileInputStream = inputStream;
                try {
                    throw e;
                } catch (Throwable th2) {
                    th = th2;
                }
            } catch (Exception e5) {
                fileInputStream = inputStream;
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
                return false;
            } catch (Throwable th3) {
                th = th3;
                fileInputStream = inputStream;
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
                throw th;
            }
        } catch (FileNotFoundException e6) {
            if (fileInputStream != null) {
                fileInputStream.close();
            }
            return false;
        } catch (IOException e7) {
            if (fileInputStream != null) {
                fileInputStream.close();
            }
            return false;
        } catch (RuntimeException e8) {
            e = e8;
            throw e;
        } catch (Exception e9) {
            if (fileInputStream != null) {
                fileInputStream.close();
            }
            return false;
        }
    }

    private boolean getConfigFromXML(InputStream inStream) {
        if (DEBUG) {
            Slog.i(TAG, "getConfigFromeXML");
        }
        boolean configGroupLoadStarted = false;
        boolean CoordinateLoadStarted = false;
        boolean loadFinished = false;
        XmlPullParser parser = Xml.newPullParser();
        try {
            parser.setInput(inStream, "UTF-8");
            for (int eventType = parser.getEventType(); eventType != 1; eventType = parser.next()) {
                String name;
                switch (eventType) {
                    case 2:
                        name = parser.getName();
                        if (!name.equals(Utils.HW_EYEPROTECTION_CONFIG_FILE_NAME)) {
                            if (!name.equals("ColorTemperatureOrigin")) {
                                if (!name.equals("isRgbGamma")) {
                                    if (!name.equals("ColorCoordinate")) {
                                        if (name.equals("Value") && CoordinateLoadStarted) {
                                            if (this.mCoordinate == null) {
                                                this.mCoordinate = new ArrayList();
                                            }
                                            this.mCoordinate.add(Float.valueOf(Float.parseFloat(parser.nextText())));
                                            break;
                                        }
                                    }
                                    CoordinateLoadStarted = true;
                                    break;
                                }
                                this.isRgbGamma = Boolean.parseBoolean(parser.nextText());
                                Slog.i(TAG, "is RgbGamma = " + this.isRgbGamma);
                                break;
                            }
                            this.originCct = Integer.parseInt(parser.nextText());
                            break;
                        }
                        configGroupLoadStarted = true;
                        break;
                    case 3:
                        name = parser.getName();
                        if (name.equals(Utils.HW_EYEPROTECTION_CONFIG_FILE_NAME) && configGroupLoadStarted) {
                            loadFinished = true;
                            configGroupLoadStarted = false;
                            break;
                        } else if (name.equals("ColorCoordinate")) {
                            CoordinateLoadStarted = false;
                            if (this.mCoordinate != null) {
                                break;
                            }
                            Slog.e(TAG, "no ColorCoordinate  loaded!");
                            return false;
                        }
                        break;
                }
                if (loadFinished) {
                    if (loadFinished) {
                        Slog.i(TAG, "getConfigFromeXML success!");
                        return true;
                    }
                    Slog.e(TAG, "getConfigFromeXML false!");
                    return false;
                }
            }
            if (loadFinished) {
                Slog.i(TAG, "getConfigFromeXML success!");
                return true;
            }
        } catch (XmlPullParserException e) {
        } catch (IOException e2) {
        } catch (NumberFormatException e3) {
        } catch (RuntimeException e4) {
            throw e4;
        } catch (Exception e5) {
        }
        Slog.e(TAG, "getConfigFromeXML false!");
        return false;
    }

    private void setDefaultConfigValue() {
        this.originCct = 6500;
        this.isRgbGamma = true;
        if (this.mCoordinate == null) {
            this.mCoordinate = new ArrayList();
        } else {
            this.mCoordinate.clear();
        }
        for (float valueOf : CoordinateDefaultPoints) {
            this.mCoordinate.add(Float.valueOf(valueOf));
        }
    }

    public float[] getRGBM(float cct) {
        return calcRGB(refineXY(cctToxy(cct), cct));
    }

    private float[] calcRGB(double[] xy) {
        double x = xy[0];
        double y = xy[1];
        Slog.i(TAG, "calcRGB: mM_inverse is : " + this.mM_inverse[0][0] + " , " + this.mM_inverse[0][1] + " , " + this.mM_inverse[0][2] + " , " + this.mM_inverse[1][0] + " , " + this.mM_inverse[1][1] + " , " + this.mM_inverse[1][2] + " , " + this.mM_inverse[2][0] + " , " + this.mM_inverse[2][1] + " , " + this.mM_inverse[2][2]);
        double[] XYZ = new double[]{x / y, 1.0d, ((1.0d - x) - y) / y};
        double[] newRgb = new double[]{((this.mM_inverse[0][0] * XYZ[0]) + (this.mM_inverse[1][0] * XYZ[1])) + (this.mM_inverse[2][0] * XYZ[2]), ((this.mM_inverse[0][1] * XYZ[0]) + (this.mM_inverse[1][1] * XYZ[1])) + (this.mM_inverse[2][1] * XYZ[2]), ((this.mM_inverse[0][2] * XYZ[0]) + (this.mM_inverse[1][2] * XYZ[1])) + (this.mM_inverse[2][2] * XYZ[2])};
        if (this.isRgbGamma) {
            newRgb[0] = (double) Math.round(Math.pow(newRgb[0], 0.45454545454545453d) * 255.0d);
            newRgb[1] = (double) Math.round(Math.pow(newRgb[1], 0.45454545454545453d) * 255.0d);
            newRgb[2] = (double) Math.round(Math.pow(newRgb[2], 0.45454545454545453d) * 255.0d);
        }
        double MaxRgb = Math.max(Math.max(newRgb[0], newRgb[1]), newRgb[2]);
        float g = (float) (newRgb[1] / MaxRgb);
        float b = (float) (newRgb[2] / MaxRgb);
        Log.i(TAG, " after convert  r =" + ((float) (newRgb[0] / MaxRgb)) + " g =" + g + " b =" + b);
        return new float[]{r, g, b};
    }

    private double[] cctToxy(float cct) {
        double xc;
        double yc;
        double invKiloK = (double) (1000.0f / cct);
        if (cct <= 4000.0f) {
            xc = (((((-0.2661239d * invKiloK) * invKiloK) * invKiloK) + ((-0.234358d * invKiloK) * invKiloK)) + (0.8776956d * invKiloK)) + 0.17991d;
        } else {
            xc = (((((-3.0258469d * invKiloK) * invKiloK) * invKiloK) + ((2.1070379d * invKiloK) * invKiloK)) + (0.2226347d * invKiloK)) + 0.24039d;
        }
        if (cct <= 2222.0f) {
            yc = (((((-1.1063814d * xc) * xc) * xc) + ((-1.3481102d * xc) * xc)) + (2.18555832d * xc)) - 22.11880576d;
        } else if (cct <= 4000.0f) {
            yc = (((((-0.9549476d * xc) * xc) * xc) + ((-1.37418593d * xc) * xc)) + (2.09137015d * xc)) - 26.56145024d;
        } else {
            yc = (((((3.081758d * xc) * xc) * xc) + ((-5.8733867d * xc) * xc)) + (3.75112997d * xc)) - 12.15952544d;
        }
        double[] xytemp = new double[]{xc, yc};
        Slog.i(TAG, "before convert x,y =" + Arrays.toString(xytemp));
        return xytemp;
    }

    private double[] refineXY(double[] xytemp, float cct) {
        double x_refine = xytemp[0] * (((this.x_gain * ((double) (cct - 5000.0f))) / ((double) (this.originCct - 5000))) + 1.0d);
        double y_refine = xytemp[1] * (((this.y_gain * ((double) (cct - 5000.0f))) / ((double) (this.originCct - 5000))) + 1.0d);
        double[] xy_refine = new double[]{x_refine, y_refine};
        Slog.i(TAG, "before refine xy gain = " + this.x_gain + " , " + this.y_gain + " ; cct = " + cct + " ; originCct = " + this.originCct + " ; eyepro_colortemperature_min = " + eyepro_colortemperature_min);
        Slog.i(TAG, "after refine xy = " + xy_refine[0] + " , " + xy_refine[1]);
        return xy_refine;
    }

    private double[][] getM(double[] srgb, double[][] xyZrgbM) {
        double[][] M1 = (double[][]) Array.newInstance(Double.TYPE, new int[]{3, 3});
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                M1[i][j] = srgb[i] * xyZrgbM[i][j];
            }
        }
        return M1;
    }

    private double[][] getMInverse(double[][] M) {
        int i;
        double[][] MInverse_bs = (double[][]) Array.newInstance(Double.TYPE, new int[]{3, 3});
        double[][] MInverse = (double[][]) Array.newInstance(Double.TYPE, new int[]{3, 3});
        for (i = 0; i < 3; i++) {
            int j;
            for (j = 0; j < 3; j++) {
                MInverse_bs[i][j] = (M[(j + 1) % 3][(i + 1) % 3] * M[(j + 2) % 3][(i + 2) % 3]) - (M[(j + 1) % 3][(i + 2) % 3] * M[(j + 2) % 3][(i + 4) % 3]);
            }
        }
        double temp = Math.abs(((M[0][0] * MInverse_bs[0][0]) + (M[1][0] * MInverse_bs[0][1])) + (M[2][0] * MInverse_bs[0][2]));
        for (i = 0; i < 3; i++) {
            for (j = 0; j < 3; j++) {
                MInverse[i][j] = MInverse_bs[i][j] / temp;
            }
        }
        return MInverse;
    }

    private void initLcdMatrix() {
        int i;
        int j;
        this.xr = (double) ((Float) this.mCoordinate.get(0)).floatValue();
        this.yr = (double) ((Float) this.mCoordinate.get(1)).floatValue();
        this.xg = (double) ((Float) this.mCoordinate.get(2)).floatValue();
        this.yg = (double) ((Float) this.mCoordinate.get(3)).floatValue();
        this.xb = (double) ((Float) this.mCoordinate.get(4)).floatValue();
        this.yb = (double) ((Float) this.mCoordinate.get(5)).floatValue();
        this.xw = (double) ((Float) this.mCoordinate.get(6)).floatValue();
        this.yw = (double) ((Float) this.mCoordinate.get(7)).floatValue();
        XYZrgbM = new double[3][];
        XYZrgbM[0] = new double[]{this.xr / this.yr, 1.0d, ((1.0d - this.xr) - this.yr) / this.yr};
        XYZrgbM[1] = new double[]{this.xg / this.yg, 1.0d, ((1.0d - this.xg) - this.yg) / this.yg};
        XYZrgbM[2] = new double[]{this.xb / this.yb, 1.0d, ((1.0d - this.xb) - this.yb) / this.yb};
        double[] XYZw = new double[]{this.xw / this.yw, 1.0d, ((1.0d - this.xw) - this.yw) / this.yw};
        double[][] XYZrgbM_inverse = getMInverse(XYZrgbM);
        for (i = 0; i < 3; i++) {
            for (j = 0; j < 3; j++) {
                Slog.i(TAG, "[truetone_debug]XYZrgbM_inverse is " + XYZrgbM_inverse[i][j]);
            }
        }
        double[] Srgb = new double[]{((XYZrgbM_inverse[0][0] * XYZw[0]) + (XYZrgbM_inverse[1][0] * XYZw[1])) + (XYZrgbM_inverse[2][0] * XYZw[2]), ((XYZrgbM_inverse[0][1] * XYZw[0]) + (XYZrgbM_inverse[1][1] * XYZw[1])) + (XYZrgbM_inverse[2][1] * XYZw[2]), ((XYZrgbM_inverse[0][2] * XYZw[0]) + (XYZrgbM_inverse[1][2] * XYZw[1])) + (XYZrgbM_inverse[2][2] * XYZw[2])};
        for (i = 0; i < 3; i++) {
            Slog.i(TAG, "[truetone_debug]Srgb is " + Srgb[i]);
        }
        this.mM_inverse = getMInverse(getM(Srgb, XYZrgbM));
        for (i = 0; i < 3; i++) {
            for (j = 0; j < 3; j++) {
                Slog.i(TAG, "[truetone_debug]mM_inverse is " + this.mM_inverse[i][j]);
            }
        }
        double[] origin_idle_xy = cctToxy((float) this.originCct);
        this.x_gain = (this.xw / origin_idle_xy[0]) - 1.0d;
        this.y_gain = (this.yw / origin_idle_xy[1]) - 1.0d;
    }

    public UpdateCctUtils() {
        this.mCoordinate = null;
        this.originCct = -1;
        this.mLcdPanelName = null;
        this.mConfigFilePath = null;
        this.isRgbGamma = false;
        this.x_gain = 1.0d;
        this.y_gain = 1.0d;
        this.mLcdPanelName = getLcdPanelName();
        try {
            if (!getConfig()) {
                Slog.e(TAG, "getConfig failed!");
                setDefaultConfigValue();
            }
        } catch (Exception e) {
        }
        initLcdMatrix();
    }
}
