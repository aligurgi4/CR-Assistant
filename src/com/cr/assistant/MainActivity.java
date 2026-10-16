package com.cr.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends Activity {

    TextView speechText, status, stateChip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        speechText = findViewById(R.id.speechText);
        status = findViewById(R.id.status);
        stateChip = findViewById(R.id.stateChip);

        findViewById(R.id.btnTalk).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AssistantActivity.class));
            }
        });

        findViewById(R.id.btnHelp).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showHelp(); }
        });

        findViewById(R.id.btnAccess).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    toast("سرویس CR را در لیست پیدا کن و فعال کن");
                } catch (Exception e) {
                    toast("تنظیمات دسترسی روی این گوشی پیدا نشد");
                }
            }
        });

        findViewById(R.id.btnCheck).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showStatus(); }
        });

        findViewById(R.id.btnService).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startListeningFlow(); }
        });

        requestOptionalPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateChip();
    }

    void requestOptionalPermissions() {
        ArrayList<String> need = new ArrayList<String>();
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.READ_CONTACTS);
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.CALL_PHONE);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), 3);
    }

    void updateChip() {
        if (ListenService.isRunning) {
            stateChip.setText("فعال");
            stateChip.setTextColor(getColorCompat(R.color.cr_accent));
        } else {
            stateChip.setText("آماده");
            stateChip.setTextColor(getColorCompat(R.color.cr_text_dim));
        }
    }

    int getColorCompat(int res) {
        if (Build.VERSION.SDK_INT >= 23) return getColor(res);
        return getResources().getColor(res);
    }

    void startListeningFlow() {
        ArrayList<String> need = new ArrayList<String>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), 1);
            return;
        }
        startListenService();
    }

    void startListenService() {
        Intent i = new Intent(this, ListenService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        status.setText("✅ گوش دادن فعال شد. بگو «سی‌آر» تا CR بیدار بشه.");
        speechText.setText("من آماده‌ام! هر وقت خواستی بگو «سی‌آر».");
        stateChip.setText("فعال");
        stateChip.setTextColor(getColorCompat(R.color.cr_accent));
        toast("حالت شنیدن فعال شد");
    }

    void showStatus() {
        StringBuilder b = new StringBuilder();
        b.append("نسخه اندروید: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        b.append("میکروفون: ").append(permText(Manifest.permission.RECORD_AUDIO)).append("\n");
        b.append("مخاطبین: ").append(permText(Manifest.permission.READ_CONTACTS)).append("\n");
        b.append("تماس: ").append(permText(Manifest.permission.CALL_PHONE)).append("\n");
        if (Build.VERSION.SDK_INT >= 33) {
            b.append("اعلان‌ها: ").append(permText(Manifest.permission.POST_NOTIFICATIONS)).append("\n");
        }
        b.append("سرویس شنیدن: ").append(ListenService.isRunning ? "فعال" : "غیرفعال").append("\n");
        b.append("سرویس دسترسی: ").append(
                com.cr.assistant.openapp.CrAccessibilityService.isEnabled() ? "فعال" : "غیرفعال").append("\n");
        b.append("اینترنت: ")
                .append(com.cr.assistant.api.NetCheck.isOnline(this) ? "متصل" : "آفلاین").append("\n\n");
        b.append("اگر «غیرفعال» بود، دکمهٔ مربوطه را بزن و اجازه بده.");
        new android.app.AlertDialog.Builder(this)
                .setTitle("وضعیت نصب و دسترسی‌ها")
                .setMessage(b.toString())
                .setPositiveButton("باشه", null)
                .show();
    }

    String permText(String p) {
        return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED ? "داده شده" : "داده نشده";
    }

    void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        super.onRequestPermissionsResult(req, p, r);
        if (req == 1) {
            boolean mic = true;
            for (int i = 0; i < p.length; i++) {
                if (Manifest.permission.RECORD_AUDIO.equals(p[i]) && r[i] != PackageManager.PERMISSION_GRANTED) {
                    mic = false;
                }
            }
            if (mic) startListenService();
            else status.setText("برای گوش دادن، دسترسی میکروفون لازمه.");
        }
    }

    void showHelp() {
        String helpText = "راهنمای CR\n\n" +
                "۱) «صحبت کن»: CR گوش می‌ده و به جمله‌ات جواب می‌ده.\n" +
                "۲) «شنیدن ۲۴ ساعته»: هر وقت بگی «سی‌آر» ربات بیدار می‌شه.\n\n" +
                "دستورهای آماده:\n" +
                "• ساعت چنده / تاریخ\n" +
                "• باز کن واتساپ / اجرا کن دوربین\n" +
                "• زنگ بزن به مریم / شماره بگیر\n" +
                "• آلارم ساعت ۷\n" +
                "• چراغ قوه / صدا بیشتر / صدا کمتر\n" +
                "• مرورگر / جستجو کن ...\n\n" +
                "سوال‌های عمومی:\n" +
                "• هر سوالی که توی لیست دستورها نباشه، CR اول از دانش داخلی، بعد ویکی‌پدیای فارسی و در نهایت مرورگر پیش‌فرض گوشی استفاده می‌کنه.\n\n" +
                "نکته‌های نصب:\n" +
                "• برای اندروید ۹ به بالا ساخته شده و روی اندروید ۱۱ به بعد با امضای معتبر نصب می‌شه.\n" +
                "• برای تماس خودکار، دسترسی مخاطبین و تماس را بده.\n" +
                "• برای کلیک خودکار روی دکمهٔ تماس، سرویس دسترسی CR را فعال کن.";
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText(helpText);
        tv.setTextSize(15);
        tv.setTextColor(getColorCompat(R.color.cr_text));
        tv.setPadding(40, 30, 40, 30);
        sv.addView(tv);
        new android.app.AlertDialog.Builder(this)
                .setIcon(R.mipmap.ic_launcher)
                .setView(sv)
                .setPositiveButton("باشه", null)
                .show();
    }
}
