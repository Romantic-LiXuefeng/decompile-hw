package com.huawei.g11n.tmr.datetime.utils.digit;

import java.util.regex.Pattern;

public abstract class LocaleDigit {
    protected String pattern;

    public abstract String convert(String str);

    public boolean isDigit(String str) {
        if (Pattern.matches(this.pattern, str)) {
            return true;
        }
        return false;
    }
}
