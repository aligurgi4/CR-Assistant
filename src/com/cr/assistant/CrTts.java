package com.cr.assistant;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Speech output.
 *
 * The bundled eSpeak-NG engine is the primary voice because it always has a
 * Persian voice and works offline. If its native library cannot be loaded on a
 * device, we fall back to the system TextToSpeech engine.
 *
 * Every failure is swallowed: speech must never stop a command from running.
 */
public class CrTts {

    private static final ExecutorService SPEAKER = Executors.newSingleThreadExecutor();

    // ---- native eSpeak bridge (loaded lazily so a link error cannot kill the app) ----
    private static boolean nativeTried = false;
    private static boolean nativeReady = false;

    private static native int nativeInit(String path);
    private static native void nativeSetVoice(String voice);
    private static native byte[] nativeSynth(String text);

    private static synchronized boolean ensureNative() {
        if (nativeTried) return nativeReady;
        nativeTried = true;
        try {
            System.loadLibrary("crtts");
            nativeReady = true;
        } catch (Throwable t) {
            nativeReady = false;
        }
        return nativeReady;
    }

    private static boolean engineReady = false;
    private static int sampleRate = 22050;

    private static synchronized boolean ensureEngine(Context c) {
        if (engineReady) return true;
        if (!ensureNative()) return false;
        try {
            File dataDir = new File(c.getFilesDir(), "espeak-ng-data");
            if (!dataDir.exists() || !new File(dataDir, "phondata").exists()) {
                VoskEngine.copyAssetDir(c, "espeak-ng-data", dataDir);
            }
            int rate = nativeInit(c.getFilesDir().getAbsolutePath());
            if (rate > 0) sampleRate = rate;
            nativeSetVoice("fa");
            engineReady = true;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---- system TextToSpeech fallback ----
    private static volatile TextToSpeech androidTts;
    private static volatile boolean androidChecked = false;
    private static volatile boolean androidReady = false;

    public static void speak(final Context c, final String text) {
        if (c == null || text == null || text.trim().isEmpty()) return;
        final Context app = c.getApplicationContext();
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                SPEAKER.execute(new Runnable() {
                    public void run() { speakNow(app, text); }
                });
            } else {
                speakNow(app, text);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void speakNow(Context c, String text) {
        try {
            if (speakEspeak(c, text)) return;
        } catch (Throwable ignored) {
        }
        try {
            speakAndroid(c, text);
        } catch (Throwable ignored) {
        }
    }

    // ---- eSpeak ----

    private static boolean speakEspeak(Context c, String text) {
        if (!ensureEngine(c)) return false;
        byte[] pcm = nativeSynth(text);
        if (pcm == null || pcm.length == 0) return false;
        AudioTrack track = null;
        try {
            // STREAM mode: a STATIC track would need one buffer as large as the
            // whole utterance, which fails for long answers (Wikipedia extracts).
            int minBuf = AudioTrack.getMinBufferSize(sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int bufSize = Math.max(minBuf, 4096 * 4 * 4);
            track = new AudioTrack(android.media.AudioManager.STREAM_MUSIC, sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    bufSize, AudioTrack.MODE_STREAM);
            if (track.getState() != AudioTrack.STATE_INITIALIZED) return false;
            track.play();
            int written = 0;
            while (written < pcm.length) {
                int n = Math.min(bufSize, pcm.length - written);
                int w = track.write(pcm, written, n);
                if (w <= 0) break; // track stopped/released
                written += w;
            }
            // Let the sink drain the buffered audio before tearing the track down.
            try {
                long durMs = (long) written * 1000 / (2 * (long) sampleRate);
                Thread.sleep(durMs + 300);
            } catch (InterruptedException ignored) {}
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (track != null) {
                try { track.stop(); } catch (Throwable ignored) {}
                try { track.release(); } catch (Throwable ignored) {}
            }
        }
    }

    // ---- Android TTS ----

    private static void speakAndroid(Context c, String text) {
        if (!androidReady && !initAndroid(c)) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Cannot block the UI thread waiting for speech callbacks.
            try {
                androidTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cr-" + System.nanoTime());
            } catch (Throwable ignored) {}
            return;
        }
        try {
            Locale fa = new Locale("fa", "IR");
            int avail = androidTts.isLanguageAvailable(fa);
            if (avail != TextToSpeech.LANG_AVAILABLE
                    && avail != TextToSpeech.LANG_COUNTRY_AVAILABLE
                    && avail != TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE) return;
            androidTts.setLanguage(fa);
            final CountDownLatch latch = new CountDownLatch(1);
            androidTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                public void onStart(String id) {}
                public void onDone(String id) { latch.countDown(); }
                @Override public void onError(String id) { latch.countDown(); }
            });
            androidTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cr-" + System.nanoTime());
            long waitMs = Math.min(20000L, Math.max(2500L, text.length() * 90L));
            latch.await(waitMs, TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {
        }
    }

    private static boolean initAndroid(final Context c) {
        if (androidChecked) return androidReady;
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] ok = {false};
        try {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    try {
                        androidTts = new TextToSpeech(c, new TextToSpeech.OnInitListener() {
                            public void onInit(int status) {
                                ok[0] = status == TextToSpeech.SUCCESS;
                                latch.countDown();
                            }
                        });
                    } catch (Throwable e) {
                        latch.countDown();
                    }
                }
            });
            if (!latch.await(6, TimeUnit.SECONDS)) return false;
            androidReady = ok[0] && androidTts != null;
        } catch (Throwable e) {
            androidReady = false;
        }
        androidChecked = true;
        return androidReady;
    }

    /** Kept for backward compatibility with older call sites. */
    public static boolean ensureReady(Context c) {
        return ensureEngine(c) || initAndroid(c.getApplicationContext());
    }
}
