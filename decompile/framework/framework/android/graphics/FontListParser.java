package android.graphics;

import android.net.ProxyInfo;
import android.os.DropBoxManager;
import android.security.KeyChain;
import android.speech.tts.TextToSpeech.Engine;
import android.util.Xml;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class FontListParser {
    private static final Pattern FILENAME_WHITESPACE_PATTERN = Pattern.compile("^[ \\n\\r\\t]+|[ \\n\\r\\t]+$");
    private static final Pattern STYLE_VALUE_PATTERN = Pattern.compile("-?(([0-9]+(\\.[0-9]+)?)|(\\.[0-9]+))");
    private static final Pattern TAG_PATTERN = Pattern.compile("[\\x00-\\xFF]{4}");

    public static class Alias {
        public String name;
        public String toName;
        public int weight;
    }

    public static class Axis {
        public final float styleValue;
        public final int tag;

        Axis(int tag, float styleValue) {
            this.tag = tag;
            this.styleValue = styleValue;
        }
    }

    public static class Config {
        public List<Alias> aliases = new ArrayList();
        public List<Family> families = new ArrayList();

        Config() {
        }
    }

    public static class Family {
        public List<Font> fonts;
        public String lang;
        public String name;
        public String variant;

        public Family(String name, List<Font> fonts, String lang, String variant) {
            this.name = name;
            this.fonts = fonts;
            this.lang = lang;
            this.variant = variant;
        }
    }

    public static class Font {
        public final List<Axis> axes;
        public String fontName;
        public boolean isItalic;
        public int ttcIndex;
        public int weight;

        Font(String fontName, int ttcIndex, List<Axis> axes, int weight, boolean isItalic) {
            this.fontName = fontName;
            this.ttcIndex = ttcIndex;
            this.axes = axes;
            this.weight = weight;
            this.isItalic = isItalic;
        }
    }

    public static Config parse(InputStream in) throws XmlPullParserException, IOException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(in, null);
            parser.nextTag();
            Config readFamilies = readFamilies(parser);
            return readFamilies;
        } finally {
            in.close();
        }
    }

    private static Config readFamilies(XmlPullParser parser) throws XmlPullParserException, IOException {
        Config config = new Config();
        parser.require(2, null, "familyset");
        while (parser.next() != 3) {
            if (parser.getEventType() == 2) {
                String tag = parser.getName();
                if (tag.equals("family")) {
                    config.families.add(readFamily(parser));
                } else if (tag.equals(KeyChain.EXTRA_ALIAS)) {
                    config.aliases.add(readAlias(parser));
                } else {
                    skip(parser);
                }
            }
        }
        return config;
    }

    private static Family readFamily(XmlPullParser parser) throws XmlPullParserException, IOException {
        String name = parser.getAttributeValue(null, "name");
        String lang = parser.getAttributeValue(null, "lang");
        String variant = parser.getAttributeValue(null, Engine.KEY_PARAM_VARIANT);
        List<Font> fonts = new ArrayList();
        while (parser.next() != 3) {
            if (parser.getEventType() == 2) {
                if (parser.getName().equals("font")) {
                    fonts.add(readFont(parser));
                } else {
                    skip(parser);
                }
            }
        }
        return new Family(name, fonts, lang, variant);
    }

    private static Font readFont(XmlPullParser parser) throws XmlPullParserException, IOException {
        String indexStr = parser.getAttributeValue(null, "index");
        int index = indexStr == null ? 0 : Integer.parseInt(indexStr);
        List<Axis> axes = new ArrayList();
        String weightStr = parser.getAttributeValue(null, "weight");
        int weight = weightStr == null ? 400 : Integer.parseInt(weightStr);
        boolean isItalic = "italic".equals(parser.getAttributeValue(null, "style"));
        StringBuilder filename = new StringBuilder();
        while (parser.next() != 3) {
            if (parser.getEventType() == 4) {
                filename.append(parser.getText());
            }
            if (parser.getEventType() == 2) {
                if (parser.getName().equals("axis")) {
                    axes.add(readAxis(parser));
                } else {
                    skip(parser);
                }
            }
        }
        return new Font("/system/fonts/" + FILENAME_WHITESPACE_PATTERN.matcher(filename).replaceAll(ProxyInfo.LOCAL_EXCL_LIST), index, axes, weight, isItalic);
    }

    private static Axis readAxis(XmlPullParser parser) throws XmlPullParserException, IOException {
        String tagStr = parser.getAttributeValue(null, DropBoxManager.EXTRA_TAG);
        if (tagStr == null || !TAG_PATTERN.matcher(tagStr).matches()) {
            throw new XmlPullParserException("Invalid tag attribute value.", parser, null);
        }
        int tag = (((tagStr.charAt(0) << 24) + (tagStr.charAt(1) << 16)) + (tagStr.charAt(2) << 8)) + tagStr.charAt(3);
        String styleValueStr = parser.getAttributeValue(null, "stylevalue");
        if (styleValueStr == null || !STYLE_VALUE_PATTERN.matcher(styleValueStr).matches()) {
            throw new XmlPullParserException("Invalid styleValue attribute value.", parser, null);
        }
        float styleValue = Float.parseFloat(styleValueStr);
        skip(parser);
        return new Axis(tag, styleValue);
    }

    private static Alias readAlias(XmlPullParser parser) throws XmlPullParserException, IOException {
        Alias alias = new Alias();
        alias.name = parser.getAttributeValue(null, "name");
        alias.toName = parser.getAttributeValue(null, "to");
        String weightStr = parser.getAttributeValue(null, "weight");
        if (weightStr == null) {
            alias.weight = 400;
        } else {
            alias.weight = Integer.parseInt(weightStr);
        }
        skip(parser);
        return alias;
    }

    private static void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        int depth = 1;
        while (depth > 0) {
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
}
