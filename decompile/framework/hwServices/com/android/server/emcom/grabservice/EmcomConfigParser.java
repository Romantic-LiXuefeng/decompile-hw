package com.android.server.emcom.grabservice;

import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;
import com.android.server.emcom.grabservice.AppInfo.EventInfo;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class EmcomConfigParser {
    private static final String ATTRI_NAME = "name";
    private static final String ATTRI_VERSION = "version";
    private static String CONFIG_FILE_PATH_1 = "/data/bastet/hw_bastet_partner.xml";
    private static String CONFIG_FILE_PATH_2 = "/system/emui/china/xml/hw_bastet_partner.xml";
    private static final String NAME_SPACE = null;
    private static final String TAG = "GrabService";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_APP = "app";
    private static final String TAG_AUTOGRAB = "autograb";
    private static final String TAG_CLASS_NAME = "className";
    private static final String TAG_DESCRIPTION = "description";
    private static final String TAG_EMCOM_ACCELERATE = "emcom_accelerate";
    private static final String TAG_EVENT = "event";
    private static final String TAG_EVENT_TYPE = "eventType";
    private static final String TAG_LIST = "list";
    private static final String TAG_TEXT = "text";
    private static EmcomConfigParser s_EmcomConfigParser;
    private ArrayList<AppInfo> mAppInfos = new ArrayList();
    private ArrayList<String> mPackageNames = new ArrayList();

    private EmcomConfigParser() {
    }

    public static synchronized EmcomConfigParser getInstance() {
        EmcomConfigParser emcomConfigParser;
        synchronized (EmcomConfigParser.class) {
            if (s_EmcomConfigParser == null) {
                s_EmcomConfigParser = new EmcomConfigParser();
            }
            emcomConfigParser = s_EmcomConfigParser;
        }
        return emcomConfigParser;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean parse() {
        this.mAppInfos.clear();
        this.mPackageNames.clear();
        XmlPullParser parser = Xml.newPullParser();
        FileInputStream in = getFileInputStream();
        if (in == null) {
            Log.e(TAG, "config file is missing.");
            return false;
        }
        boolean result = false;
        try {
            parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", false);
            parser.setInput(in, null);
            parser.nextTag();
            parseList(parser);
            result = true;
            try {
                in.close();
            } catch (IOException e) {
                Log.e(TAG, "IOException occur.", e);
            }
        } catch (XmlPullParserException e2) {
            Log.e(TAG, "parse xml error.", e2);
        } catch (IOException e1) {
            Log.e(TAG, "IOException occur.", e1);
            try {
                in.close();
            } catch (IOException e3) {
                Log.e(TAG, "IOException occur.", e3);
            }
        } catch (Throwable th) {
            try {
                in.close();
            } catch (IOException e32) {
                Log.e(TAG, "IOException occur.", e32);
            }
        }
        return result;
    }

    private FileInputStream getFileInputStream() {
        FileInputStream in = getFileInputStream(CONFIG_FILE_PATH_1);
        if (in == null) {
            return getFileInputStream(CONFIG_FILE_PATH_2);
        }
        return in;
    }

    private FileInputStream getFileInputStream(String filePath) {
        try {
            return new FileInputStream(new File(filePath));
        } catch (FileNotFoundException e) {
            Log.i(TAG, "flie not found in " + filePath);
            return null;
        }
    }

    private void parseList(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(2, NAME_SPACE, TAG_LIST);
        while (parser.next() != 3) {
            if (parser.getEventType() == 2) {
                if (parser.getName().equals(TAG_EMCOM_ACCELERATE)) {
                    parseEmcomAccelerate(parser);
                } else {
                    skip(parser);
                }
            }
        }
    }

    private void parseEmcomAccelerate(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(2, NAME_SPACE, TAG_EMCOM_ACCELERATE);
        while (parser.next() != 3) {
            if (parser.getEventType() == 2) {
                if (parser.getName().equals(TAG_APP)) {
                    parseAppConfig(parser);
                } else {
                    skip(parser);
                }
            }
        }
    }

    private void parseAppConfig(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(2, NAME_SPACE, TAG_APP);
        AppInfo appInfo = new AppInfo();
        appInfo.packageName = parser.getAttributeValue(NAME_SPACE, "name");
        while (parser.next() != 3) {
            if (parser.getEventType() == 2) {
                String name = parser.getName();
                if (name.equals(TAG_EVENT)) {
                    readEvents(parser, appInfo);
                } else if (name.equals(TAG_AUTOGRAB)) {
                    readAutograb(parser, appInfo);
                }
            }
        }
        this.mAppInfos.add(appInfo);
        this.mPackageNames.add(appInfo.packageName);
    }

    private void readAutograb(XmlPullParser parser, AppInfo appInfo) throws XmlPullParserException, IOException {
        parser.require(2, NAME_SPACE, TAG_AUTOGRAB);
        String version = parser.getAttributeValue(NAME_SPACE, ATTRI_VERSION);
        String autoGrabConfig = readVaule(parser);
        if (!TextUtils.isEmpty(autoGrabConfig) && !TextUtils.isEmpty(version)) {
            appInfo.autograbParams.put(version, autoGrabConfig);
        }
    }

    private void readEvents(XmlPullParser parser, AppInfo appInfo) throws XmlPullParserException, IOException {
        parser.require(2, NAME_SPACE, TAG_EVENT);
        EventInfo event = new EventInfo(appInfo.packageName);
        event.version = parser.getAttributeValue(NAME_SPACE, ATTRI_VERSION);
        while (parser.next() != 3) {
            if (parser.getEventType() == 2) {
                String name = parser.getName();
                if (name.equals(TAG_EVENT_TYPE)) {
                    event.eventType = Integer.parseInt(readVaule(parser));
                } else if (name.equals(TAG_CLASS_NAME)) {
                    event.eventClassName = readVaule(parser);
                } else if (name.equals(TAG_ACTIVITY)) {
                    event.activity = readVaule(parser);
                } else if (name.equals(TAG_TEXT)) {
                    event.text = AutoGrabTools.unicode2String(readVaule(parser));
                } else if (name.equals(TAG_DESCRIPTION)) {
                    event.description = AutoGrabTools.unicode2String(readVaule(parser));
                } else {
                    skip(parser);
                }
            }
        }
        appInfo.events.add(event);
    }

    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != 2) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case 2:
                    depth++;
                    break;
                case 3:
                    depth--;
                    break;
                default:
                    break;
            }
        }
    }

    private String readVaule(XmlPullParser parser) throws IOException, XmlPullParserException {
        if (parser.next() != 4) {
            return null;
        }
        String result = parser.getText();
        parser.nextTag();
        return result;
    }

    public ArrayList<AppInfo> getAppInfos() {
        return this.mAppInfos;
    }

    public ArrayList<String> getAppPackageNames() {
        return this.mPackageNames;
    }

    public AppInfo getAppInfoByPackageName(String packageName) {
        for (AppInfo appInfo : this.mAppInfos) {
            if (appInfo.getPackageName().equals(packageName)) {
                return appInfo;
            }
        }
        return null;
    }
}
