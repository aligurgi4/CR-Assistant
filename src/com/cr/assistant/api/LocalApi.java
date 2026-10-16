package com.cr.assistant.api;

import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;

import com.cr.assistant.Commands;
import com.cr.assistant.openapp.Actions;
import com.cr.assistant.openapp.AppLauncher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;

/** API محلی داخل برنامه: جواب‌های آفلاین دستورات از روی commands.json */
public class LocalApi {
    private static JSONArray intents;

    public static synchronized JSONArray intents(Context c) {
        if (intents != null) return intents;
        try {
            InputStream is = c.getAssets().open("commands.json");
            BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            is.close();
            intents = new JSONObject(sb.toString()).getJSONArray("intents");
        } catch (Exception e) {
            intents = new JSONArray();
        }
        return intents;
    }

    /** true اگر این متن با یکی از intent های آفلاین مچ می‌شه. */
    public static boolean hasIntent(Context c, String raw) {
        String s = Commands.norm(raw);
        if (s.isEmpty()) return false;
        JSONArray list = intents(c);
        for (int i = 0; i < list.length(); i++) {
            JSONObject it = list.optJSONObject(i);
            if (it == null) continue;
            JSONArray pats = it.optJSONArray("patterns");
            if (pats == null) continue;
            for (int j = 0; j < pats.length(); j++) {
                String p = pats.optString(j, "");
                if (p.isEmpty()) continue;
                if (p.contains(".*") ? s.matches(".*(" + p + ").*") : s.contains(Commands.norm(p))) return true;
            }
        }
        return false;
    }

    /** برمی‌گردونه true اگر دستور آفلاین پاسخ داده شد. */
    public static boolean handle(Context c, String raw, Object tts) {
        String s = Commands.norm(raw);
        if (s.isEmpty()) return false;
        JSONArray list = intents(c);
        JSONObject best = null;
        int bestScore = 0;
        for (int i = 0; i < list.length(); i++) {
            JSONObject it = list.optJSONObject(i);
            if (it == null) continue;
            JSONArray pats = it.optJSONArray("patterns");
            if (pats == null) continue;
            int score = 0;
            for (int j = 0; j < pats.length(); j++) {
                String p = pats.optString(j, "");
                if (p.isEmpty()) continue;
                if (p.contains(".*") ? s.matches(".*(" + p + ").*") : s.contains(Commands.norm(p))) {
                    score += p.length();
                }
            }
            if (score > bestScore) { bestScore = score; best = it; }
        }
        // "مرورگر باز کن" also matches open_app; prefer the browser action.
        if (best != null && "open_app".equals(best.optString("action"))
                && (s.contains("مرورگر") || s.contains("وب سایت") || s.contains("وبسایت"))) {
            JSONObject url = findIntent(list, "open_url");
            if (url != null) best = url;
        }
        if (best == null) return false;
        try {
            String action = best.getString("action");
            switch (action) {
                case "say_time":
                    Actions.sayTime(c, tts);
                    return true;
                case "say_date":
                    Actions.sayDate(c, tts);
                    return true;
                case "torch":
                    Actions.toggleTorch(c, tts);
                    return true;
                case "volume_up":
                    Actions.volume(c, true);
                    say(c, tts, "صدا بیشتر شد");
                    return true;
                case "volume_down":
                    Actions.volume(c, false);
                    say(c, tts, "صدا کمتر شد");
                    return true;
                case "set_alarm":
                    Actions.setAlarm(c, s, tts);
                    return true;
                case "dial":
                    String num = extractDigits(s);
                    if (!num.isEmpty()) {
                        AppLauncher.openUrl(c, "tel:" + num);
                        say(c, tts, "شماره " + num + " رو آوردم");
                    } else {
                        AppLauncher.openAction(c, Intent.ACTION_DIAL);
                        say(c, tts, "صفحهٔ شماره‌گیری باز شد");
                    }
                    return true;
                case "call_contact": {
                    String name = extractContact(s);
                    if (name.isEmpty()) { say(c, tts, "اسم مخاطب رو نگفتم نفهمیدم"); return true; }
                    Actions.callContact(c, name, tts);
                    return true;
                }
                case "open_app": {
                    String app = extractApp(s);
                    if (app.isEmpty()) { say(c, tts, "کدوم برنامه؟"); return true; }
                    if (AppLauncher.openApp(c, app)) say(c, tts, "باشه، " + app + " رو باز می‌کنم");
                    else say(c, tts, "برنامه‌ای به اسم «" + app + "» پیدا نکردم");
                    return true;
                }
                case "open_url":
                    String site = extractSite(s);
                    if (site.isEmpty()) {
                        AppLauncher.openUrl(c, "https://www.google.com");
                        say(c, tts, "مرورگر پیش‌فرضت رو باز کردم");
                    } else {
                        String url = site.contains("://") ? site : "https://" + site;
                        AppLauncher.openUrl(c, url);
                        say(c, tts, "دارم " + site + " رو باز می‌کنم");
                    }
                    return true;
                case "search_web":
                    String q = extractQuery(s);
                    if (q.isEmpty()) { say(c, tts, "چه چیزی رو جستجو کنم؟"); return true; }
                    if (NetCheck.isOnline(c)) { GoogleApi.searchDefaultBrowser(c, q, tts); }
                    else say(c, tts, "برای جستجو به اینترنت نیاز دارم و الان آفلاینم");
                    return true;
            }
        } catch (Exception e) {
            say(c, tts, "یه مشکلی پیش آمد");
            return true;
        }
        return false;
    }

    static JSONObject findIntent(JSONArray list, String action) {
        for (int i = 0; i < list.length(); i++) {
            JSONObject it = list.optJSONObject(i);
            if (it != null && action.equals(it.optString("action"))) return it;
        }
        return null;
    }

    static String extractApp(String s) {
        String[] strip = {"باز کن", "بازکن", "اجرا کن", "بیار", "باز بشه", "برنامه", "برنامهٔ",
                "اپلیکیشن", "رو", "را", "لطفا", "لطفاً"};
        return stripWords(s, strip);
    }

    static String extractDigits(String s) {
        String t = s.replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3')
                .replace('۴','4').replace('۵','5').replace('۶','6').replace('۷','7')
                .replace('۸','8').replace('۹','9')
                .replace('٠','0').replace('١','1').replace('٢','2').replace('٣','3')
                .replace('٤','4').replace('٥','5').replace('٦','6').replace('٧','7')
                .replace('٨','8').replace('٩','9');
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (Character.isDigit(ch)) b.append(ch);
            else if (ch == '+' && b.length() == 0) b.append(ch);
        }
        return b.toString();
    }

    static String extractSite(String s) {
        String[] strip = {"وب سایت", "وبسایت", "سایت", "مرورگر", "باز کن", "وب باز کن", "رو", "را", "در"};
        s = stripWords(s, strip);
        String[] words = s.split(" ");
        for (String w : words) {
            if (w.contains(".") && w.length() > 3) return w;
        }
        return "";
    }

    static String extractQuery(String s) {
        String[] strip = {"جستجو کن", "جستجو در گوگل", "توی گوگل بگرد", "برام بگرد", "گوگل کن", "سرچ کن",
                "رو", "را", "در", "لطفا", "لطفاً"};
        return stripWords(s, strip);
    }

    /**
     * Removes whole words/phrases only. Using String.replace on substrings used to
     * corrupt names (e.g. "رادیو" became "دیو"), which made app lookups fail.
     */
    static String stripWords(String s, String[] strip) {
        String joined = " " + s.replaceAll("\\s+", " ").trim() + " ";
        for (String phrase : strip) {
            if (phrase.isEmpty()) continue;
            joined = joined.replace(" " + phrase + " ", " ");
        }
        return joined.replaceAll("\\s+", " ").trim();
    }

    static String extractContact(String s) {
        String[] strip = {"زنگ بزن", "زنگ بز", "تماس بگیر", "تماس با", "تماس", "بگیر", "بزن",
                "به", "با", "لطفاً", "لطفا"};
        return stripWords(s, strip);
    }

    public static void say(Context c, Object tts, String msg) {
        Actions.say(c, tts, msg);
    }
}
