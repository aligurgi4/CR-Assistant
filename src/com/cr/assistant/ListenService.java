package com.cr.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Handler;
import android.os.Looper;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class ListenService extends Service {
    public static final String CH = "cr";
    public static volatile boolean isRunning = false;
    
    Handler handler = new Handler(Looper.getMainLooper());
    PowerManager.WakeLock wakeLock;
    AtomicBoolean micOn = new AtomicBoolean(false);
    AtomicBoolean running = new AtomicBoolean(true);
    AtomicBoolean wakeRun = new AtomicBoolean(false);
    boolean listeningForCommand = false;

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, "CR", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
        Notification n = new Notification.Builder(this, CH)
                .setContentTitle("CR")
                .setContentText("در حال گوش دادن به «سی‌آر»…")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(1, n);
        }
        
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cr:wake");
        wakeLock.acquire();
        startWakeLoop();
    }

    void startWakeLoop() {
        if (listeningForCommand || micOn.get()) return;
        micOn.set(true);
        wakeRun.set(true);
        new Thread(new Runnable() {
            public void run() {
                try {
                    VoskEngine.loop(ListenService.this, new VoskEngine.Listener() {
                        public void onPartial(String text) { checkWake(text); }
                        public void onFinal(String text) { checkWake(text); }
                    }, wakeRun);
                } catch (Throwable ignored) {}
                micOn.set(false);
                if (running.get() && !listeningForCommand) {
                    handler.postDelayed(new Runnable() { public void run() { startWakeLoop(); } }, 1000);
                }
            }
        }).start();
    }

    void checkWake(String s) {
        String n = Commands.norm(s);
        if (hasWakeWord(n)) {
            if (!listeningForCommand) {
                // اگر کاربر «CR» و دستور رو با هم گفته، همون‌جا اجرا کن؛ بدون باز شدن برنامه
                String rest = stripWakeWord(n);
                if (rest.length() >= 4 && Commands.hasIntent(ListenService.this, rest)) {
                    listeningForCommand = true;
                    wakeRun.set(false);
                    final String cmd = rest;
                    handler.post(new Runnable() { public void run() {
                        Commands.execute(ListenService.this, cmd, null);
                        listeningForCommand = false;
                        handler.postDelayed(new Runnable() { public void run() { startWakeLoop(); } }, 3000);
                    } });
                    return;
                }
                listeningForCommand = true;
                wakeRun.set(false); // حلقهٔ شنیدن رو متوقف کن
                handler.post(new Runnable() { public void run() { wakeUp(); } });
            }
        }
    }

    /**
     * Vosk rarely transcribes the wake word exactly as «سی‌آر»: after
     * normalization the common spellings are «سی آر», «سی ار» and «سی ر».
     * Keep them all in one place so the wake word is detected reliably.
     */
    static boolean hasWakeWord(String n) {
        return n.contains("سی آر") || n.contains("سی ار") || n.contains("سی ر")
                || n.contains("cr") || n.contains("c r");
    }

    static String stripWakeWord(String n) {
        return n.replace("سی آر", " ").replace("سی ار", " ").replace("سی ر", " ")
                .replace("cr", " ").replace("c r", " ")
                .replaceAll("\\s+", " ").trim();
    }

    void wakeUp() {
        // بدون باز کردن برنامه: فقط بپرس و دستور رو بشنو و اجرا کن
        com.cr.assistant.openapp.Actions.say(this, null, "بله، بگو");
        handler.postDelayed(new Runnable() { public void run() { listenCommand(); } }, 1500);
    }

    void listenCommand() {
        final AtomicBoolean once = new AtomicBoolean(true);
        new Thread(new Runnable() {
            public void run() {
                try {
                    VoskEngine.loop(ListenService.this, new VoskEngine.Listener() {
                        public void onPartial(String text) {}
                        public void onFinal(String text) {
                            if (text == null) return;
                            if (once.get() && !text.startsWith("__ERROR__")) {
                                once.set(false);
                                finishCommand(text);
                            } else if (once.get() && text.startsWith("__ERROR__")) {
                                once.set(false);
                                finishCommand(null);
                            }
                        }
                    }, once);
                } catch (Throwable ignored) {
                    once.set(false);
                    finishCommand(null);
                }
            }
        }).start();
        handler.postDelayed(new Runnable() { public void run() { if (once.get()) { once.set(false); finishCommand(null); } } }, 20000);
    }

    void finishCommand(String cmd) {
        listeningForCommand = false;
        if (cmd != null && !cmd.isEmpty()) {
            Commands.execute(this, cmd, null);
        } else {
            com.cr.assistant.openapp.Actions.say(this, null, "دستور رو نگرفتم، دوباره بگو سی‌آر");
        }
        handler.postDelayed(new Runnable() { public void run() { startWakeLoop(); } }, 3000);
    }


    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onDestroy() {
        running.set(false);
        isRunning = false;
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
}
