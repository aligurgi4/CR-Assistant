package com.cr.assistant.openapp;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.util.List;
import java.util.Locale;

import com.cr.assistant.Commands;

/** (O.A) Open App: find and launch installed apps by name. */
public class AppLauncher {
    public static String alias(String name) {
        if (name.contains("تلگرام")) return "telegram";
        if (name.contains("واتساپ") || name.contains("واتس")) return "whatsapp";
        if (name.contains("اینستاگرام") || name.contains("اینستا")) return "instagram";
        if (name.contains("یوتیوب")) return "youtube";
        if (name.contains("دوربین") || name.contains("عکاسی")) return "camera";
        if (name.contains("تنظیمات")) return "settings";
        if (name.contains("مرورگر") || name.contains("کروم")) return "chrome";
        if (name.contains("مسنجر")) return "messenger";
        if (name.contains("ویچت") || name.contains("وی چت")) return "wechat";
        if (name.contains("ایمیل") || name.contains("جیمیل")) return "gmail";
        if (name.contains("نقشه")) return "maps";
        if (name.contains("موسیقی") || name.contains("آهنگ")) return "music";
        return name;
    }

    public static boolean openApp(Context c, String name) {
        if (name == null || name.trim().isEmpty()) return false;
        name = name.trim();
        String target = alias(name);
        PackageManager pm = c.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo a : apps) {
            CharSequence label = pm.getApplicationLabel(a);
            String pkg = a.packageName.toLowerCase(Locale.ROOT);
            if (label != null) {
                String l = Commands.norm(label.toString());
                if (l.contains(name) || name.contains(l) || pkg.contains(target) || l.contains(target)) {
                    Intent launch = pm.getLaunchIntentForPackage(a.packageName);
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        c.startActivity(launch);
                        return true;
                    }
                }
            }
            if (pkg.contains(target)) {
                Intent launch = pm.getLaunchIntentForPackage(a.packageName);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(launch);
                    return true;
                }
            }
        }
        return false;
    }

    public static void openAction(Context c, String action) {
        try {
            Intent i = new Intent(action);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (ActivityNotFoundException e) {}
    }

    public static void openUrl(Context c, String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (ActivityNotFoundException e) {}
    }
}
