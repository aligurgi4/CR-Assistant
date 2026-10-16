package com.cr.assistant.api;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.cr.assistant.openapp.AppLauncher;

public class GoogleApi {
    /** Opens a Google search in the user's default browser. */
    public static void search(Context c, String text, Object tts) {
        try {
            String url = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(text, "UTF-8");
            AppLauncher.openUrl(c, url);
            LocalApi.say(c, tts, "نتایج جستجو رو برات باز کردم");
        } catch (Exception e) {
            LocalApi.say(c, tts, "برای جواب دادن به این سوال به اینترنت نیاز دارم");
        }
    }

    /**
     * Searches using the system WEB_SEARCH action so the user's default browser
     * opens, with an HTTPS URL fallback if nothing handles the intent.
     */
    public static boolean searchDefaultBrowser(Context c, String text, Object tts) {
        try {
            Intent i = new Intent(Intent.ACTION_WEB_SEARCH);
            i.putExtra(android.app.SearchManager.QUERY, text);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            LocalApi.say(c, tts, "جواب این یکی رو نمی‌دونستم، توی مرورگر پیش‌فرضت جستجو می‌کنم");
            return true;
        } catch (Exception e) {
            try {
                String url = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(text, "UTF-8");
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                LocalApi.say(c, tts, "جواب این یکی رو نمی‌دونستم، توی مرورگر جستجو کردم");
                return true;
            } catch (Exception e2) {
                LocalApi.say(c, tts, "مرورگری پیدا نکردم که این سوال رو برات باز کنه");
                return false;
            }
        }
    }
}
