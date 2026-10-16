package com.cr.assistant;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import java.util.concurrent.atomic.AtomicBoolean;

public class AssistantActivity extends Activity {

    TextView heard, ask;
    AtomicBoolean listening = new AtomicBoolean(false);
    Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assistant);
        heard = findViewById(R.id.heard);
        ask = findViewById(R.id.ask);

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 5);
        }

        findViewById(R.id.btnTalk).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { listen(); }
        });
    }

    void listen() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            heard.setText("برای گوش دادن باید دسترسی میکروفون رو بدی");
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 5);
            return;
        }
        if (!listening.compareAndSet(false, true)) return;

        heard.setText("🎧 گوش می‌کنم…");
        ask.setText("بگو، چی می‌خوای؟");
        final AtomicBoolean once = new AtomicBoolean(true);

        new Thread(new Runnable() {
            public void run() {
                VoskEngine.loop(AssistantActivity.this, new VoskEngine.Listener() {
                    public void onPartial(String text) {
                        final String t = text;
                        ui.post(new Runnable() { public void run() { heard.setText("🎧 " + t); } });
                    }

                    public void onFinal(String text) {
                        if (!once.get()) return;
                        if (text != null && !text.isEmpty() && !text.startsWith("__ERROR__")) {
                            once.set(false);
                            final String t = text;
                            ui.post(new Runnable() {
                                public void run() {
                                    heard.setText("🗣 " + t);
                                    ask.setText("چطور می‌تونم کمکت کنم؟");
                                }
                            });
                            new Thread(new Runnable() {
                                public void run() {
                                    Commands.execute(AssistantActivity.this, t, null);
                                    listening.set(false);
                                }
                            }).start();
                        } else if (text != null && text.startsWith("__ERROR__")) {
                            once.set(false);
                            listening.set(false);
                            ui.post(new Runnable() {
                                public void run() {
                                    heard.setText("میکروفون آماده نشد. دوباره امتحان کن.");
                                }
                            });
                        }
                    }
                }, once);
                if (once.get()) {
                    listening.set(false);
                    ui.post(new Runnable() {
                        public void run() { heard.setText("چیزی نشنیدم. دوباره بزن."); }
                    });
                }
            }
        }).start();
    }
}
