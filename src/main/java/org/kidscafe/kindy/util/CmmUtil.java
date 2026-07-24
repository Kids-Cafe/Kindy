package org.kidscafe.kindy.util;

public class CmmUtil {

    public static String nvl(String str, String chg_str) {

        return (str == null || str.isEmpty()) ? chg_str : str;
    }

    public static String nvl(String str) {

        return nvl(str, "");
    }
}
