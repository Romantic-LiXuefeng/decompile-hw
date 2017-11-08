package com.android.server.display;

import android.graphics.PointF;
import android.os.FileUtils;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;
import huawei.cust.HwCfgFilePolicy;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public final class HwCCTXmlLoader {
    private static final boolean HWDEBUG;
    private static final boolean HWFLOW;
    private static final String LCD_PANEL_TYPE_PATH = "/sys/class/graphics/fb0";
    private static final String TAG = "HwCCTXmlLoader";
    private static final String XML_EXT = ".xml";
    private static final String XML_NAME = "EyeProtectionConfig.xml";
    private static final String XML_NAME_NOEXT = "EyeProtectionConfig";
    private static Data mData = new Data();
    private static HwCCTXmlLoader mLoader;
    private String mLcdPanelName = null;

    public static class Data implements Cloneable {
        public List<PointF> CCTAscendlinePoints = new ArrayList();
        public List<PointF> CCTDescendlinePoints = new ArrayList();
        public int ascendDebounceTime = 1000;
        public long coverModeAscendResponseTime = 1000;
        public long coverModeDescendResponseTime = 1000;
        public float coverModeFirstCCT = 2210.0f;
        public int descendDebounceTime = 4000;
        public boolean lastCloseScreenEnable = false;
        public int lightSensorRateMills = 300;
        public int postMaxMinAvgFilterNoFilterNum = 6;
        public int postMaxMinAvgFilterNum = 5;
        public int postMeanFilterNoFilterNum = 4;
        public int postMeanFilterNum = 3;
        public int postMethodNum = 2;
        public int preMeanFilterNoFilterNum = 7;
        public int preMeanFilterNum = 3;
        public int preMethodNum = 0;
        public float preWeightedMeanFilterAlpha = 0.5f;
        public float preWeightedMeanFilterCCTTh = 12.0f;
        public int preWeightedMeanFilterMaxFuncCCTNum = 3;
        public int preWeightedMeanFilterNoFilterNum = 7;
        public int preWeightedMeanFilterNum = 3;

        protected Object clone() throws CloneNotSupportedException {
            Data newData = (Data) super.clone();
            newData.CCTAscendlinePoints = cloneList(this.CCTAscendlinePoints);
            newData.CCTDescendlinePoints = cloneList(this.CCTDescendlinePoints);
            return newData;
        }

        private List<PointF> cloneList(List<PointF> list) {
            if (list == null) {
                return null;
            }
            List<PointF> newList = new ArrayList();
            for (PointF point : list) {
                newList.add(new PointF(point.x, point.y));
            }
            return newList;
        }

        public void printData() {
            if (HwCCTXmlLoader.HWFLOW) {
                Slog.i(HwCCTXmlLoader.TAG, "printData() lightSensorRateMills=" + this.lightSensorRateMills + ", ascendDebounceTime=" + this.ascendDebounceTime + ", descendDebounceTime=" + this.descendDebounceTime);
                Slog.i(HwCCTXmlLoader.TAG, "printData() coverModeFirstCCT=" + this.coverModeFirstCCT + ", lastCloseScreenEnable=" + this.lastCloseScreenEnable + ", coverModeAscendResponseTime=" + this.coverModeAscendResponseTime + ", coverModeDescendResponseTime=" + this.coverModeDescendResponseTime + ", postMaxMinAvgFilterNoFilterNum=" + this.postMaxMinAvgFilterNoFilterNum + ", postMaxMinAvgFilterNum=" + this.postMaxMinAvgFilterNum);
                Slog.i(HwCCTXmlLoader.TAG, "printData() preMethodNum=" + this.preMethodNum + ", preMeanFilterNoFilterNum=" + this.preMeanFilterNoFilterNum + ", preMeanFilterNum=" + this.preMeanFilterNum + ", postMethodNum=" + this.postMethodNum + ", postMeanFilterNoFilterNum=" + this.postMeanFilterNoFilterNum + ", postMeanFilterNum=" + this.postMeanFilterNum);
                Slog.i(HwCCTXmlLoader.TAG, "printData() preWeightedMeanFilterNoFilterNum=" + this.preWeightedMeanFilterNoFilterNum + ",preWeightedMeanFilterNum=" + this.preWeightedMeanFilterNum + ",preWeightedMeanFilterMaxFuncCCTNum=" + this.preWeightedMeanFilterMaxFuncCCTNum + ",preWeightedMeanFilterAlpha=" + this.preWeightedMeanFilterAlpha + ",preWeightedMeanFilterCCTTh=" + this.preWeightedMeanFilterCCTTh);
                Slog.i(HwCCTXmlLoader.TAG, "printData() CCTAscendlinePoints=" + this.CCTAscendlinePoints);
                Slog.i(HwCCTXmlLoader.TAG, "printData() CCTDescendlinePoints=" + this.CCTDescendlinePoints);
            }
        }

        public void loadDefaultConfig() {
            if (HwCCTXmlLoader.HWFLOW) {
                Slog.i(HwCCTXmlLoader.TAG, "loadDefaultConfig()");
            }
            this.lightSensorRateMills = 300;
            this.ascendDebounceTime = 1000;
            this.descendDebounceTime = 4000;
            this.coverModeFirstCCT = 2210.0f;
            this.lastCloseScreenEnable = false;
            this.coverModeAscendResponseTime = 1000;
            this.coverModeDescendResponseTime = 1000;
            this.postMaxMinAvgFilterNoFilterNum = 6;
            this.postMaxMinAvgFilterNum = 5;
            this.preMethodNum = 0;
            this.preMeanFilterNoFilterNum = 7;
            this.preMeanFilterNum = 3;
            this.postMethodNum = 2;
            this.postMeanFilterNoFilterNum = 4;
            this.postMeanFilterNum = 3;
            this.preWeightedMeanFilterNoFilterNum = 7;
            this.preWeightedMeanFilterNum = 3;
            this.preWeightedMeanFilterMaxFuncCCTNum = 3;
            this.preWeightedMeanFilterAlpha = 0.5f;
            this.preWeightedMeanFilterCCTTh = 12.0f;
            this.CCTAscendlinePoints.clear();
            this.CCTAscendlinePoints.add(new PointF(0.0f, 0.0f));
            this.CCTAscendlinePoints.add(new PointF(2000.0f, 200.0f));
            this.CCTAscendlinePoints.add(new PointF(3000.0f, 300.0f));
            this.CCTAscendlinePoints.add(new PointF(4000.0f, 400.0f));
            this.CCTAscendlinePoints.add(new PointF(5000.0f, 500.0f));
            this.CCTAscendlinePoints.add(new PointF(6000.0f, 600.0f));
            this.CCTAscendlinePoints.add(new PointF(7000.0f, 700.0f));
            this.CCTAscendlinePoints.add(new PointF(10000.0f, 1000.0f));
            this.CCTDescendlinePoints.clear();
            this.CCTDescendlinePoints.add(new PointF(0.0f, 0.0f));
            this.CCTDescendlinePoints.add(new PointF(2000.0f, 200.0f));
            this.CCTDescendlinePoints.add(new PointF(3000.0f, 300.0f));
            this.CCTDescendlinePoints.add(new PointF(4000.0f, 400.0f));
            this.CCTDescendlinePoints.add(new PointF(5000.0f, 500.0f));
            this.CCTDescendlinePoints.add(new PointF(6000.0f, 600.0f));
            this.CCTDescendlinePoints.add(new PointF(7000.0f, 700.0f));
            this.CCTDescendlinePoints.add(new PointF(10000.0f, 1000.0f));
        }
    }

    private static class Element_AscendDebounceTime extends HwXmlElement {
        private Element_AscendDebounceTime() {
        }

        public String getName() {
            return "AscendDebounceTime";
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.ascendDebounceTime = HwXmlElement.string2Int(parser.nextText());
            return true;
        }

        protected boolean checkValue() {
            return HwCCTXmlLoader.mData.ascendDebounceTime > 0;
        }
    }

    private static class Element_CCTAscendlinePoints extends HwXmlElement {
        private Element_CCTAscendlinePoints() {
        }

        public String getName() {
            return "CCTAscendlinePoints";
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            return true;
        }
    }

    private static class Element_CCTAscendlinePoints_Point extends HwXmlElement {
        private Element_CCTAscendlinePoints_Point() {
        }

        public String getName() {
            return "Point";
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.CCTAscendlinePoints = HwXmlElement.parsePointFList(parser, HwCCTXmlLoader.mData.CCTAscendlinePoints);
            return true;
        }

        protected boolean checkValue() {
            return HwCCTXmlLoader.checkPointsListIsOK(HwCCTXmlLoader.mData.CCTAscendlinePoints);
        }
    }

    private static class Element_CCTDescendlinePoints extends HwXmlElement {
        private Element_CCTDescendlinePoints() {
        }

        public String getName() {
            return "CCTDescendlinePoints";
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            return true;
        }
    }

    private static class Element_CCTDescendlinePoints_Point extends HwXmlElement {
        private Element_CCTDescendlinePoints_Point() {
        }

        public String getName() {
            return "Point";
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.CCTDescendlinePoints = HwXmlElement.parsePointFList(parser, HwCCTXmlLoader.mData.CCTDescendlinePoints);
            return true;
        }

        protected boolean checkValue() {
            return HwCCTXmlLoader.checkPointsListIsOK(HwCCTXmlLoader.mData.CCTDescendlinePoints);
        }
    }

    private static class Element_CoverModeAscendResponseTime extends HwXmlElement {
        private Element_CoverModeAscendResponseTime() {
        }

        public String getName() {
            return "CoverModeAscendResponseTime";
        }

        protected boolean isOptional() {
            return true;
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.coverModeAscendResponseTime = HwXmlElement.string2Long(parser.nextText());
            return true;
        }

        protected boolean checkValue() {
            return HwCCTXmlLoader.mData.coverModeAscendResponseTime >= 0;
        }
    }

    private static class Element_CoverModeDescendResponseTime extends HwXmlElement {
        private Element_CoverModeDescendResponseTime() {
        }

        public String getName() {
            return "CoverModeDescendResponseTime";
        }

        protected boolean isOptional() {
            return true;
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.coverModeDescendResponseTime = HwXmlElement.string2Long(parser.nextText());
            return true;
        }

        protected boolean checkValue() {
            return HwCCTXmlLoader.mData.coverModeDescendResponseTime >= 0;
        }
    }

    private static class Element_CoverModeFirstCCT extends HwXmlElement {
        private Element_CoverModeFirstCCT() {
        }

        public String getName() {
            return "CoverModeFirstCCT";
        }

        protected boolean isOptional() {
            return true;
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.coverModeFirstCCT = HwXmlElement.string2Float(parser.nextText());
            return true;
        }
    }

    private static class Element_DescendDebounceTime extends HwXmlElement {
        private Element_DescendDebounceTime() {
        }

        public String getName() {
            return "DescendDebounceTime";
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.descendDebounceTime = HwXmlElement.string2Int(parser.nextText());
            return true;
        }

        protected boolean checkValue() {
            return HwCCTXmlLoader.mData.descendDebounceTime > 0;
        }
    }

    private static class Element_EyeProtectionConfig extends HwXmlElement {
        private Element_EyeProtectionConfig() {
        }

        public String getName() {
            return "EyeProtectionConfig";
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            return true;
        }
    }

    private static class Element_LastCloseScreenEnable extends HwXmlElement {
        private Element_LastCloseScreenEnable() {
        }

        public String getName() {
            return "LastCloseScreenEnable";
        }

        protected boolean isOptional() {
            return true;
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.lastCloseScreenEnable = HwXmlElement.string2Boolean(parser.nextText());
            return true;
        }
    }

    private static class Element_LightSensorRateMills extends HwXmlElement {
        private Element_LightSensorRateMills() {
        }

        public String getName() {
            return "lightSensorRateMills";
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.lightSensorRateMills = HwXmlElement.string2Int(parser.nextText());
            return true;
        }
    }

    private static class Element_PostMaxMinAvgFilterNoFilterNum extends HwXmlElement {
        private Element_PostMaxMinAvgFilterNoFilterNum() {
        }

        public String getName() {
            return "PostMaxMinAvgFilterNoFilterNum";
        }

        protected boolean isOptional() {
            return true;
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.postMaxMinAvgFilterNoFilterNum = HwXmlElement.string2Int(parser.nextText());
            return true;
        }
    }

    private static class Element_PostMaxMinAvgFilterNum extends HwXmlElement {
        private Element_PostMaxMinAvgFilterNum() {
        }

        public String getName() {
            return "PostMaxMinAvgFilterNum";
        }

        protected boolean isOptional() {
            return true;
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.postMaxMinAvgFilterNum = HwXmlElement.string2Int(parser.nextText());
            return true;
        }

        protected boolean checkValue() {
            return HwCCTXmlLoader.mData.postMaxMinAvgFilterNum > 0 && HwCCTXmlLoader.mData.postMaxMinAvgFilterNum <= HwCCTXmlLoader.mData.postMaxMinAvgFilterNoFilterNum;
        }
    }

    private static class Element_PostMeanFilterNoFilterNum extends HwXmlElement {
        private Element_PostMeanFilterNoFilterNum() {
        }

        public String getName() {
            return "PostMeanFilterNoFilterNum";
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.postMeanFilterNoFilterNum = HwXmlElement.string2Int(parser.nextText());
            return true;
        }
    }

    private static class Element_PostMeanFilterNum extends HwXmlElement {
        private Element_PostMeanFilterNum() {
        }

        public String getName() {
            return "PostMeanFilterNum";
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.postMeanFilterNum = HwXmlElement.string2Int(parser.nextText());
            return true;
        }

        protected boolean checkValue() {
            return HwCCTXmlLoader.mData.postMeanFilterNum > 0 && HwCCTXmlLoader.mData.postMeanFilterNum <= HwCCTXmlLoader.mData.postMeanFilterNoFilterNum;
        }
    }

    private static class Element_PostMethodNum extends HwXmlElement {
        private Element_PostMethodNum() {
        }

        public String getName() {
            return "PostMethodNum";
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.postMethodNum = HwXmlElement.string2Int(parser.nextText());
            return true;
        }
    }

    private static class Element_PreMeanFilterNoFilterNum extends HwXmlElement {
        private Element_PreMeanFilterNoFilterNum() {
        }

        public String getName() {
            return "PreMeanFilterNoFilterNum";
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.preMeanFilterNoFilterNum = HwXmlElement.string2Int(parser.nextText());
            return true;
        }
    }

    private static class Element_PreMeanFilterNum extends HwXmlElement {
        private Element_PreMeanFilterNum() {
        }

        public String getName() {
            return "PreMeanFilterNum";
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.preMeanFilterNum = HwXmlElement.string2Int(parser.nextText());
            return true;
        }

        protected boolean checkValue() {
            return HwCCTXmlLoader.mData.preMeanFilterNum > 0 && HwCCTXmlLoader.mData.preMeanFilterNum <= HwCCTXmlLoader.mData.preMeanFilterNoFilterNum;
        }
    }

    private static class Element_PreMethodNum extends HwXmlElement {
        private Element_PreMethodNum() {
        }

        public String getName() {
            return "PreMethodNum";
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.preMethodNum = HwXmlElement.string2Int(parser.nextText());
            return true;
        }
    }

    private static class Element_PreProcessing extends HwXmlElement {
        private Element_PreProcessing() {
        }

        public String getName() {
            return "PreProcessing";
        }

        protected boolean isOptional() {
            return true;
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            return true;
        }
    }

    private static class Element_PreWeightedMeanFilterAlpha extends HwXmlElement {
        private Element_PreWeightedMeanFilterAlpha() {
        }

        public String getName() {
            return "PreWeightedMeanFilterAlpha";
        }

        protected boolean isOptional() {
            return true;
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.preWeightedMeanFilterAlpha = HwXmlElement.string2Float(parser.nextText());
            return true;
        }
    }

    private static class Element_PreWeightedMeanFilterCCTTh extends HwXmlElement {
        private Element_PreWeightedMeanFilterCCTTh() {
        }

        public String getName() {
            return "PreWeightedMeanFilterCCTTh";
        }

        protected boolean isOptional() {
            return true;
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.preWeightedMeanFilterCCTTh = HwXmlElement.string2Float(parser.nextText());
            return true;
        }
    }

    private static class Element_PreWeightedMeanFilterMaxFuncCCTNum extends HwXmlElement {
        private Element_PreWeightedMeanFilterMaxFuncCCTNum() {
        }

        public String getName() {
            return "PreWeightedMeanFilterMaxFuncCCTNum";
        }

        protected boolean isOptional() {
            return true;
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.preWeightedMeanFilterMaxFuncCCTNum = HwXmlElement.string2Int(parser.nextText());
            return true;
        }

        protected boolean checkValue() {
            return HwCCTXmlLoader.mData.preWeightedMeanFilterMaxFuncCCTNum > 0;
        }
    }

    private static class Element_PreWeightedMeanFilterNoFilterNum extends HwXmlElement {
        private Element_PreWeightedMeanFilterNoFilterNum() {
        }

        public String getName() {
            return "PreWeightedMeanFilterNoFilterNum";
        }

        protected boolean isOptional() {
            return true;
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.preWeightedMeanFilterNoFilterNum = HwXmlElement.string2Int(parser.nextText());
            return true;
        }

        protected boolean checkValue() {
            return HwCCTXmlLoader.mData.preWeightedMeanFilterNoFilterNum > 0;
        }
    }

    private static class Element_PreWeightedMeanFilterNum extends HwXmlElement {
        private Element_PreWeightedMeanFilterNum() {
        }

        public String getName() {
            return "PreWeightedMeanFilterNum";
        }

        protected boolean isOptional() {
            return true;
        }

        protected boolean parseValue(XmlPullParser parser) throws XmlPullParserException, IOException {
            HwCCTXmlLoader.mData.preWeightedMeanFilterNum = HwXmlElement.string2Int(parser.nextText());
            return true;
        }

        protected boolean checkValue() {
            return HwCCTXmlLoader.mData.preWeightedMeanFilterNum > 0 && HwCCTXmlLoader.mData.preWeightedMeanFilterNum <= HwCCTXmlLoader.mData.preWeightedMeanFilterNoFilterNum;
        }
    }

    static {
        boolean z = true;
        boolean isLoggable = !Log.HWLog ? Log.HWModuleLog ? Log.isLoggable(TAG, 3) : false : true;
        HWDEBUG = isLoggable;
        if (!Log.HWINFO) {
            z = Log.HWModuleLog ? Log.isLoggable(TAG, 4) : false;
        }
        HWFLOW = z;
    }

    public static synchronized Data getData() {
        Throwable th;
        synchronized (HwCCTXmlLoader.class) {
            Data retData;
            Data retData2;
            try {
                if (mLoader == null) {
                    mLoader = new HwCCTXmlLoader();
                }
                retData = (Data) mData.clone();
                if (retData == null) {
                    retData2 = new Data();
                    retData2.loadDefaultConfig();
                    retData = retData2;
                }
            } catch (Exception e) {
                Slog.e(TAG, "getData() failed! " + e);
                retData2 = new Data();
                retData2.loadDefaultConfig();
                retData = retData2;
            } catch (Throwable th2) {
                th = th2;
                retData = retData2;
                throw th;
            }
        }
        return retData;
    }

    private HwCCTXmlLoader() {
        if (HWDEBUG) {
            Slog.d(TAG, "HwCCTXmlLoader()");
        }
        this.mLcdPanelName = getLcdPanelName();
        if (!parseXml(getXmlPath())) {
            mData.loadDefaultConfig();
        }
        mData.printData();
    }

    private boolean parseXml(String xmlPath) {
        if (xmlPath == null) {
            Slog.e(TAG, "parseXml() error! xmlPath is null");
            return false;
        }
        if (HWFLOW) {
            Slog.i(TAG, "parseXml() getXmlPath = " + xmlPath);
        }
        HwXmlParser xmlParser = new HwXmlParser(xmlPath);
        registerElement(xmlParser);
        if (!xmlParser.parse()) {
            Slog.e(TAG, "parseXml() error! xmlParser.parse() failed!");
            return false;
        } else if (xmlParser.check()) {
            if (HWFLOW) {
                Slog.i(TAG, "parseXml() load success!");
            }
            return true;
        } else {
            Slog.e(TAG, "parseXml() error! xmlParser.check() failed!");
            return false;
        }
    }

    private void registerElement(HwXmlParser parser) {
        HwXmlElement rootElement = parser.registerRootElement(new Element_EyeProtectionConfig());
        rootElement.registerChildElement(new Element_LightSensorRateMills());
        rootElement.registerChildElement(new Element_AscendDebounceTime());
        rootElement.registerChildElement(new Element_DescendDebounceTime());
        rootElement.registerChildElement(new Element_CoverModeFirstCCT());
        rootElement.registerChildElement(new Element_LastCloseScreenEnable());
        rootElement.registerChildElement(new Element_CoverModeAscendResponseTime());
        rootElement.registerChildElement(new Element_CoverModeDescendResponseTime());
        rootElement.registerChildElement(new Element_PostMaxMinAvgFilterNoFilterNum());
        rootElement.registerChildElement(new Element_PostMaxMinAvgFilterNum());
        rootElement.registerChildElement(new Element_PreWeightedMeanFilterNoFilterNum());
        rootElement.registerChildElement(new Element_PreWeightedMeanFilterNum());
        rootElement.registerChildElement(new Element_PreWeightedMeanFilterMaxFuncCCTNum());
        rootElement.registerChildElement(new Element_PreWeightedMeanFilterAlpha());
        rootElement.registerChildElement(new Element_PreWeightedMeanFilterCCTTh());
        HwXmlElement preProcessing = rootElement.registerChildElement(new Element_PreProcessing());
        preProcessing.registerChildElement(new Element_PreMethodNum());
        preProcessing.registerChildElement(new Element_PreMeanFilterNoFilterNum());
        preProcessing.registerChildElement(new Element_PreMeanFilterNum());
        preProcessing.registerChildElement(new Element_PostMethodNum());
        preProcessing.registerChildElement(new Element_PostMeanFilterNoFilterNum());
        preProcessing.registerChildElement(new Element_PostMeanFilterNum());
        rootElement.registerChildElement(new Element_CCTAscendlinePoints()).registerChildElement(new Element_CCTAscendlinePoints_Point());
        rootElement.registerChildElement(new Element_CCTDescendlinePoints()).registerChildElement(new Element_CCTDescendlinePoints_Point());
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

    private String getXmlPath() {
        String version = SystemProperties.get("ro.build.version.emui", null);
        Slog.i(TAG, "HwEyeProtectionControllerImpl getConfig");
        if (TextUtils.isEmpty(version)) {
            Slog.w(TAG, "get ro.build.version.emui failed!");
            return null;
        }
        String[] versionSplited = version.split("EmotionUI_");
        if (versionSplited.length < 2) {
            Slog.w(TAG, "split failed! version = " + version);
            return null;
        }
        if (TextUtils.isEmpty(versionSplited[1])) {
            Slog.w(TAG, "get emuiVersion failed!");
            return null;
        }
        String lcdEyeProtectionConfigFile = "EyeProtectionConfig_" + this.mLcdPanelName + XML_EXT;
        File xmlFile = HwCfgFilePolicy.getCfgFile(String.format("/xml/lcd/%s/%s", new Object[]{emuiVersion, "EyeProtectionConfig.xml"}), 0);
        if (xmlFile == null) {
            xmlFile = HwCfgFilePolicy.getCfgFile(String.format("/xml/lcd/%s", new Object[]{"EyeProtectionConfig.xml"}), 0);
            if (xmlFile == null) {
                xmlFile = HwCfgFilePolicy.getCfgFile(String.format("/xml/lcd/%s/%s", new Object[]{emuiVersion, lcdEyeProtectionConfigFile}), 0);
                if (xmlFile == null) {
                    String xmlPath = String.format("/xml/lcd/%s", new Object[]{lcdEyeProtectionConfigFile});
                    xmlFile = HwCfgFilePolicy.getCfgFile(xmlPath, 0);
                    if (xmlFile == null) {
                        Slog.w(TAG, "get xmlFile :" + xmlPath + " failed!");
                        return null;
                    }
                }
            }
        }
        return xmlFile.getAbsolutePath();
    }

    private static boolean checkPointsListIsOK(List<PointF> list) {
        if (list == null) {
            Slog.e(TAG, "checkPointsListIsOK() error! list is null");
            return false;
        } else if (list.size() < 3 || list.size() >= 100) {
            Slog.e(TAG, "checkPointsListIsOK() error! list size=" + list.size() + " is out of range");
            return false;
        } else {
            PointF lastPoint = null;
            for (PointF point : list) {
                if (lastPoint == null || point.x > lastPoint.x) {
                    lastPoint = point;
                } else {
                    Slog.e(TAG, "checkPointsListIsOK() error! x in list isn't a increasing sequence, " + point.x + "<=" + lastPoint.x);
                    return false;
                }
            }
            return true;
        }
    }
}
