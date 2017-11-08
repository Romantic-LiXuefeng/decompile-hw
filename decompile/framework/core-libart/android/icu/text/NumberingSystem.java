package android.icu.text;

import android.icu.impl.ICUCache;
import android.icu.impl.ICUResourceBundle;
import android.icu.impl.SimpleCache;
import android.icu.lang.UCharacter;
import android.icu.util.ULocale;
import android.icu.util.ULocale.Category;
import android.icu.util.UResourceBundle;
import android.icu.util.UResourceBundleIterator;
import java.util.ArrayList;
import java.util.Locale;
import java.util.MissingResourceException;

public class NumberingSystem {
    private static ICUCache<String, NumberingSystem> cachedLocaleData = new SimpleCache();
    private static ICUCache<String, NumberingSystem> cachedStringData = new SimpleCache();
    private boolean algorithmic = false;
    private String desc = "0123456789";
    private String name = "latn";
    private int radix = 10;

    public static NumberingSystem getInstance(int radix_in, boolean isAlgorithmic_in, String desc_in) {
        return getInstance(null, radix_in, isAlgorithmic_in, desc_in);
    }

    private static NumberingSystem getInstance(String name_in, int radix_in, boolean isAlgorithmic_in, String desc_in) {
        if (radix_in < 2) {
            throw new IllegalArgumentException("Invalid radix for numbering system");
        } else if (isAlgorithmic_in || (desc_in.length() == radix_in && isValidDigitString(desc_in))) {
            NumberingSystem ns = new NumberingSystem();
            ns.radix = radix_in;
            ns.algorithmic = isAlgorithmic_in;
            ns.desc = desc_in;
            ns.name = name_in;
            return ns;
        } else {
            throw new IllegalArgumentException("Invalid digit string for numbering system");
        }
    }

    public static NumberingSystem getInstance(Locale inLocale) {
        return getInstance(ULocale.forLocale(inLocale));
    }

    public static NumberingSystem getInstance(ULocale locale) {
        NumberingSystem ns;
        String[] OTHER_NS_KEYWORDS = new String[]{"native", "traditional", "finance"};
        Boolean nsResolved = Boolean.valueOf(true);
        String numbersKeyword = locale.getKeywordValue("numbers");
        if (numbersKeyword != null) {
            for (String keyword : OTHER_NS_KEYWORDS) {
                if (numbersKeyword.equals(keyword)) {
                    nsResolved = Boolean.valueOf(false);
                    break;
                }
            }
        } else {
            numbersKeyword = "default";
            nsResolved = Boolean.valueOf(false);
        }
        if (nsResolved.booleanValue()) {
            ns = getInstanceByName(numbersKeyword);
            if (ns != null) {
                return ns;
            }
            numbersKeyword = "default";
            nsResolved = Boolean.valueOf(false);
        }
        String baseName = locale.getBaseName();
        ns = (NumberingSystem) cachedLocaleData.get(baseName + "@numbers=" + numbersKeyword);
        if (ns != null) {
            return ns;
        }
        String originalNumbersKeyword = numbersKeyword;
        String str = null;
        while (!nsResolved.booleanValue()) {
            try {
                str = ((ICUResourceBundle) UResourceBundle.getBundleInstance("android/icu/impl/data/icudt56b", locale)).getWithFallback("NumberElements").getStringWithFallback(numbersKeyword);
                nsResolved = Boolean.valueOf(true);
            } catch (MissingResourceException e) {
                if (numbersKeyword.equals("native") || numbersKeyword.equals("finance")) {
                    numbersKeyword = "default";
                } else if (numbersKeyword.equals("traditional")) {
                    numbersKeyword = "native";
                } else {
                    nsResolved = Boolean.valueOf(true);
                }
            }
        }
        if (str != null) {
            ns = getInstanceByName(str);
        }
        if (ns == null) {
            ns = new NumberingSystem();
        }
        cachedLocaleData.put(baseName + "@numbers=" + originalNumbersKeyword, ns);
        return ns;
    }

    public static NumberingSystem getInstance() {
        return getInstance(ULocale.getDefault(Category.FORMAT));
    }

    public static NumberingSystem getInstanceByName(String name) {
        NumberingSystem ns = (NumberingSystem) cachedStringData.get(name);
        if (ns != null) {
            return ns;
        }
        try {
            UResourceBundle nsTop = UResourceBundle.getBundleInstance("android/icu/impl/data/icudt56b", "numberingSystems").get("numberingSystems").get(name);
            ns = getInstance(name, nsTop.get("radix").getInt(), nsTop.get("algorithmic").getInt() == 1, nsTop.getString("desc"));
            cachedStringData.put(name, ns);
            return ns;
        } catch (MissingResourceException e) {
            return null;
        }
    }

    public static String[] getAvailableNames() {
        UResourceBundle nsCurrent = UResourceBundle.getBundleInstance("android/icu/impl/data/icudt56b", "numberingSystems").get("numberingSystems");
        ArrayList<String> output = new ArrayList();
        UResourceBundleIterator it = nsCurrent.getIterator();
        while (it.hasNext()) {
            output.add(it.next().getKey());
        }
        return (String[]) output.toArray(new String[output.size()]);
    }

    public static boolean isValidDigitString(String str) {
        int i = 0;
        UCharacterIterator it = UCharacterIterator.getInstance(str);
        it.setToStart();
        while (true) {
            int c = it.nextCodePoint();
            if (c == -1) {
                break;
            } else if (UCharacter.isSupplementary(c)) {
                return false;
            } else {
                i++;
            }
        }
        if (i != 10) {
            return false;
        }
        return true;
    }

    public int getRadix() {
        return this.radix;
    }

    public String getDescription() {
        return this.desc;
    }

    public String getName() {
        return this.name;
    }

    public boolean isAlgorithmic() {
        return this.algorithmic;
    }
}
