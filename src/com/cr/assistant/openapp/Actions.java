package com.cr.assistant.openapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Actions {
    static boolean torchOn = false;

    public static void say(Context c, Object tts, String msg) {
        // Answers are no longer spoken: the app reports the result as a text
        // message at the bottom of the screen instead of running a TTS engine.
        showToast(c, msg);
    }

    /** Bottom text must run on the main thread; commands are processed on workers. */
    static void showToast(final Context c, final String msg) {
        if (msg == null || msg.trim().isEmpty()) return;
        final String text = msg.trim();
        try {
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(new Runnable() {
                public void run() {
                    try {
                        Toast t = Toast.makeText(c, text,
                                text.length() > 60 ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
                        t.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL,
                                0, 140);
                        t.show();
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    public static void sayTime(Context c, Object tts) {
        String t = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        say(c, tts, "ساعت الان " + t + " هست");
    }

    public static void sayDate(Context c, Object tts) {
        String d = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(new Date());
        say(c, tts, "امروز " + d);
    }

    public static void toggleTorch(Context c, Object tts) throws CameraAccessException {
        CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
        String id = findTorchCamera(cm);
        if (id == null) {
            say(c, tts, "این گوشی چراغ‌قوه نداره");
            return;
        }
        boolean next = !torchOn;
        cm.setTorchMode(id, next);
        torchOn = next;
        say(c, tts, next ? "چراغ روشن شد" : "چراغ خاموش شد");
    }

    /** Returns the id of a back camera that actually has a flash unit, or null. */
    static String findTorchCamera(CameraManager cm) throws CameraAccessException {
        for (String id : cm.getCameraIdList()) {
            try {
                android.hardware.camera2.CameraCharacteristics ch = cm.getCameraCharacteristics(id);
                Boolean hasFlash = ch.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = ch.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING);
                if (hasFlash != null && hasFlash
                        && facing != null
                        && facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) {
                    return id;
                }
            } catch (Exception ignored) {}
        }
        // Fall back to any camera with a flash (some devices expose it differently).
        for (String id : cm.getCameraIdList()) {
            try {
                Boolean hasFlash = cm.getCameraCharacteristics(id)
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (hasFlash != null && hasFlash) return id;
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static void volume(Context c, boolean up) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI);
    }

    /** آلارم ساعت X (و Y دقیقه) صبح/ظهر/عصر/شب — با Intent سیستمی، بدون نیاز به دسترسی خاص. */
    public static boolean setAlarm(Context c, String text, Object tts) {
        int[] hm = parseAlarmTime(text);
        try {
            Intent i = new Intent(AlarmClock.ACTION_SET_ALARM);
            i.putExtra(AlarmClock.EXTRA_HOUR, hm[0]);
            i.putExtra(AlarmClock.EXTRA_MINUTES, hm[1]);
            i.putExtra(AlarmClock.EXTRA_MESSAGE, "CR");
            i.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            say(c, tts, "آلارم برای ساعت " + hm[0] + ":" + String.format(Locale.US, "%02d", hm[1]) + " تنظیم شد");
            return true;
        } catch (Exception e) {
            AppLauncher.openAction(c, AlarmClock.ACTION_SHOW_ALARMS);
            say(c, tts, "برنامهٔ آلارم باز شد");
            return false;
        }
    }

    public static int[] parseAlarmTime(String s) {
        String t = s.replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3')
                .replace('۴','4').replace('۵','5').replace('۶','6').replace('۷','7')
                .replace('۸','8').replace('۹','9')
                .replace('٠','0').replace('١','1').replace('٢','2').replace('٣','3')
                .replace('٤','4').replace('٥','5').replace('٦','6').replace('٧','7')
                .replace('٨','8').replace('٩','9');
        int hour = 8, minute = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2})\\s*(:|و|ساعت)?\\s*(\\d{1,2})?").matcher(t);
        if (m.find()) {
            hour = Integer.parseInt(m.group(1));
            if (m.group(3) != null && !m.group(3).isEmpty()) {
                int v = Integer.parseInt(m.group(3));
                if (v < 60) minute = v;
            }
        }
        boolean pm = t.contains("عصر") || t.contains("بعدازظهر") || t.contains("ظهر");
        boolean night = t.contains("شب");
        if (t.contains("ظهر") && hour == 12) hour = 12;
        if ((pm || night) && hour < 12) hour += 12;
        if (hour >= 24) hour = hour % 24;
        return new int[]{hour, minute};
    }

    /** زنگ به مخاطب: تماس مستقیم اگر دسترسی داشت، وگرنه برنامهٔ تلفن + سرویس دسترسی. */
    public static boolean callContact(Context c, String name, Object tts) {
        String number = findContactNumber(c, name);
        if (number == null) {
            say(c, tts, "مخاطبی به اسم «" + name + "» پیدا نکردم");
            return false;
        }
        if (c.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            try {
                Intent i = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                say(c, tts, "دارم به «" + name + "» زنگ می‌زنم");
                return true;
            } catch (Exception ignored) {}
        }
        // fallback: باز کردن صفحهٔ تماس و کلیک روی دکمهٔ تماس با سرویس Accessibility
        try {
            Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            CrAccessibilityService.clickSoon("تماس|call|Call", 8);
            say(c, tts, "شمارهٔ " + name + " رو باز کردم، تماس وصل شد");
            return true;
        } catch (Exception e) {
            say(c, tts, "نتونستم تماس بگیرم، دسترسی تماس رو بده");
            return false;
        }
    }

    public static String findContactNumber(Context c, String name) {
        if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                    new String[]{"%" + name + "%"}, null);
            if (cur != null && cur.moveToFirst()) {
                return cur.getString(0);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return null;
    }
}
