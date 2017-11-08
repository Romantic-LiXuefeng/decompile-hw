package android.icu.impl;

import android.icu.impl.CurrencyData.CurrencyDisplayInfo;
import android.icu.impl.CurrencyData.CurrencyDisplayInfoProvider;
import android.icu.impl.CurrencyData.CurrencyFormatInfo;
import android.icu.impl.CurrencyData.CurrencySpacingInfo;
import android.icu.text.PluralRules;
import android.icu.util.ULocale;
import android.icu.util.UResourceBundle;
import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class ICUCurrencyDisplayInfoProvider implements CurrencyDisplayInfoProvider {

    static class ICUCurrencyDisplayInfo extends CurrencyDisplayInfo {
        private SoftReference<Map<String, String>> _nameMapRef;
        private SoftReference<Map<String, String>> _symbolMapRef;
        private final ICUResourceBundle currencies;
        private final boolean fallback;
        private final ICUResourceBundle plurals;
        private final ICUResourceBundle rb;

        public ICUCurrencyDisplayInfo(ICUResourceBundle rb, boolean fallback) {
            this.fallback = fallback;
            this.rb = rb;
            this.currencies = rb.findTopLevel("Currencies");
            this.plurals = rb.findTopLevel("CurrencyPlurals");
        }

        public ULocale getULocale() {
            return this.rb.getULocale();
        }

        public String getName(String isoCode) {
            return getName(isoCode, false);
        }

        public String getSymbol(String isoCode) {
            return getName(isoCode, true);
        }

        private String getName(String isoCode, boolean symbolName) {
            if (this.currencies != null) {
                ICUResourceBundle result = this.currencies.findWithFallback(isoCode);
                if (result != null) {
                    if (!this.fallback) {
                        int status = result.getLoadingStatus();
                        if (status == 3 || status == 2) {
                            return null;
                        }
                    }
                    return result.getString(symbolName ? 0 : 1);
                }
            }
            if (!this.fallback) {
                isoCode = null;
            }
            return isoCode;
        }

        public String getPluralName(String isoCode, String pluralKey) {
            String str = null;
            if (this.plurals != null) {
                ICUResourceBundle pluralsBundle = this.plurals.findWithFallback(isoCode);
                if (pluralsBundle != null) {
                    String pluralName = pluralsBundle.findStringWithFallback(pluralKey);
                    if (pluralName == null) {
                        if (!this.fallback) {
                            return null;
                        }
                        pluralName = pluralsBundle.findStringWithFallback(PluralRules.KEYWORD_OTHER);
                        if (pluralName == null) {
                            return getName(isoCode);
                        }
                    }
                    return pluralName;
                }
            }
            if (this.fallback) {
                str = getName(isoCode);
            }
            return str;
        }

        public Map<String, String> symbolMap() {
            Map<String, String> map = null;
            if (this._symbolMapRef != null) {
                map = (Map) this._symbolMapRef.get();
            }
            if (map != null) {
                return map;
            }
            map = _createSymbolMap();
            this._symbolMapRef = new SoftReference(map);
            return map;
        }

        public Map<String, String> nameMap() {
            Map<String, String> map = null;
            if (this._nameMapRef != null) {
                map = (Map) this._nameMapRef.get();
            }
            if (map != null) {
                return map;
            }
            map = _createNameMap();
            this._nameMapRef = new SoftReference(map);
            return map;
        }

        public Map<String, String> getUnitPatterns() {
            Map<String, String> result = new HashMap();
            for (ULocale locale = this.rb.getULocale(); locale != null; locale = locale.getFallback()) {
                ICUResourceBundle r = (ICUResourceBundle) UResourceBundle.getBundleInstance("android/icu/impl/data/icudt56b/curr", locale);
                if (r != null) {
                    ICUResourceBundle cr = r.findWithFallback("CurrencyUnitPatterns");
                    if (cr != null) {
                        int size = cr.getSize();
                        for (int index = 0; index < size; index++) {
                            ICUResourceBundle b = (ICUResourceBundle) cr.get(index);
                            String key = b.getKey();
                            if (!result.containsKey(key)) {
                                result.put(key, b.getString());
                            }
                        }
                    }
                }
            }
            return Collections.unmodifiableMap(result);
        }

        public CurrencyFormatInfo getFormatInfo(String isoCode) {
            ICUResourceBundle crb = this.currencies.findWithFallback(isoCode);
            if (crb != null && crb.getSize() > 2) {
                crb = crb.at(2);
                if (crb != null) {
                    return new CurrencyFormatInfo(crb.getString(0), crb.getString(1).charAt(0), crb.getString(2).charAt(0));
                }
            }
            return null;
        }

        public CurrencySpacingInfo getSpacingInfo() {
            CurrencySpacingInfo currencySpacingInfo = null;
            ICUResourceBundle srb = this.rb.findWithFallback("currencySpacing");
            if (srb != null) {
                ICUResourceBundle brb = srb.findWithFallback("beforeCurrency");
                ICUResourceBundle arb = srb.findWithFallback("afterCurrency");
                if (!(arb == null || brb == null)) {
                    return new CurrencySpacingInfo(brb.findStringWithFallback("currencyMatch"), brb.findStringWithFallback("surroundingMatch"), brb.findStringWithFallback("insertBetween"), arb.findStringWithFallback("currencyMatch"), arb.findStringWithFallback("surroundingMatch"), arb.findStringWithFallback("insertBetween"));
                }
            }
            if (this.fallback) {
                currencySpacingInfo = CurrencySpacingInfo.DEFAULT;
            }
            return currencySpacingInfo;
        }

        private Map<String, String> _createSymbolMap() {
            Map<String, String> result = new HashMap();
            for (ULocale locale = this.rb.getULocale(); locale != null; locale = locale.getFallback()) {
                ICUResourceBundle curr = ((ICUResourceBundle) UResourceBundle.getBundleInstance("android/icu/impl/data/icudt56b/curr", locale)).findTopLevel("Currencies");
                if (curr != null) {
                    for (int i = 0; i < curr.getSize(); i++) {
                        ICUResourceBundle item = curr.at(i);
                        String isoCode = item.getKey();
                        if (!result.containsKey(isoCode)) {
                            result.put(isoCode, isoCode);
                            result.put(item.getString(0), isoCode);
                        }
                    }
                }
            }
            return Collections.unmodifiableMap(result);
        }

        private Map<String, String> _createNameMap() {
            Map<String, String> result = new TreeMap(String.CASE_INSENSITIVE_ORDER);
            Set<String> visited = new HashSet();
            Map<String, Set<String>> visitedPlurals = new HashMap();
            for (ULocale locale = this.rb.getULocale(); locale != null; locale = locale.getFallback()) {
                int i;
                ICUResourceBundle item;
                String isoCode;
                ICUResourceBundle bundle = (ICUResourceBundle) UResourceBundle.getBundleInstance("android/icu/impl/data/icudt56b/curr", locale);
                ICUResourceBundle curr = bundle.findTopLevel("Currencies");
                if (curr != null) {
                    for (i = 0; i < curr.getSize(); i++) {
                        item = curr.at(i);
                        isoCode = item.getKey();
                        if (!visited.contains(isoCode)) {
                            visited.add(isoCode);
                            result.put(item.getString(1), isoCode);
                        }
                    }
                }
                ICUResourceBundle plurals = bundle.findTopLevel("CurrencyPlurals");
                if (plurals != null) {
                    for (i = 0; i < plurals.getSize(); i++) {
                        item = plurals.at(i);
                        isoCode = item.getKey();
                        Set<String> pluralSet = (Set) visitedPlurals.get(isoCode);
                        if (pluralSet == null) {
                            pluralSet = new HashSet();
                            visitedPlurals.put(isoCode, pluralSet);
                        }
                        for (int j = 0; j < item.getSize(); j++) {
                            ICUResourceBundle plural = item.at(j);
                            String pluralType = plural.getKey();
                            if (!pluralSet.contains(pluralType)) {
                                result.put(plural.getString(), isoCode);
                                pluralSet.add(pluralType);
                            }
                        }
                    }
                }
            }
            return Collections.unmodifiableMap(result);
        }
    }

    public CurrencyDisplayInfo getInstance(ULocale locale, boolean withFallback) {
        ICUResourceBundle rb = (ICUResourceBundle) UResourceBundle.getBundleInstance("android/icu/impl/data/icudt56b/curr", locale);
        if (!withFallback) {
            int status = rb.getLoadingStatus();
            if (status == 3 || status == 2) {
                return null;
            }
        }
        return new ICUCurrencyDisplayInfo(rb, withFallback);
    }

    public boolean hasData() {
        return true;
    }
}
