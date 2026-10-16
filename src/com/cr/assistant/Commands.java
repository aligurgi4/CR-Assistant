package com.cr.assistant;

import android.content.Context;

import java.util.Locale;

/** سازگاری با نسخهٔ قدیمی: کار اصلی به CrCore منتقل شده. */
public class Commands {
    public static String norm(String s) {
        if (s == null) return "";
        s = s.toLowerCase(Locale.ROOT);
        s = s.replace('ي', 'ی').replace('ك', 'ک');
        s = s.replace('‌', ' '); // ZWNJ
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    public static boolean hasIntent(Context c, String raw) {
        return com.cr.assistant.api.LocalApi.hasIntent(c, raw);
    }

    public static void execute(Context c, String raw, Object tts) {
        CrCore.process(c, raw, tts);
    }
}
