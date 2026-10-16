package com.cr.assistant;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import org.json.JSONObject;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class VoskEngine {
    public interface Listener {
        void onPartial(String text);
        void onFinal(String text);
    }

    public static Model model;
    static boolean modelLoaded = false;

    /**
     * Files every usable model must contain. Used to detect a partially
     * extracted/copied model: a half-written directory used to make Model()
     * fail on every later start with no way to recover.
     */
    private static final String[] REQUIRED_MODEL_FILES = {
            "am/final.mdl", "conf/mfcc.conf", "graph/Gr.fst", "graph/HCLr.fst"
    };

    static boolean modelReady(File dir) {
        if (dir == null) return false;
        for (String f : REQUIRED_MODEL_FILES) {
            File x = new File(dir, f);
            if (!x.isFile() || x.length() == 0) return false;
        }
        return true;
    }

    static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }

    /** Moves tmp over dest, falling back to copy+delete when rename fails. */
    static boolean replaceDir(File tmp, File dest) throws IOException {
        deleteRecursive(dest);
        if (!tmp.renameTo(dest)) {
            copyDir(tmp, dest);
            deleteRecursive(tmp);
        }
        return modelReady(dest);
    }

    public static synchronized String ensureModel(Context c) throws Exception {
        File dest = new File(c.getFilesDir(), "vosk-model");
        if (modelReady(dest)) return dest.getAbsolutePath();
        // A previous extraction may have been interrupted; start clean.
        deleteRecursive(dest);

        File src = new File(c.getExternalFilesDir(null), "vosk-model-small-fa-0.5");
        if (!src.exists()) src = new File("/sdcard/Android/data/com.cr.assistant/files/vosk-model-small-fa-0.5");
        if (!src.exists()) src = new File("/sdcard/vosk-model-small-fa-0.5");
        if (!src.exists()) src = new File("/storage/emulated/0/Download/vosk-model-small-fa-0.5");
        if (!src.exists()) {
            // search SD card roots for our app-specific folder
            File[] roots = new File("/storage").listFiles();
            if (roots != null) {
                for (File r : roots) {
                    File cand = new File(r, "Android/data/com.cr.assistant/files/vosk-model-small-fa-0.5");
                    if (cand.exists()) { src = cand; break; }
                    cand = new File(r, "vosk-model-small-fa-0.5");
                    if (cand.exists()) { src = cand; break; }
                }
            }
        }

        File tmp = new File(c.getFilesDir(), "vosk-model.tmp");
        deleteRecursive(tmp);

        if (modelReady(src)) {
            copyDir(src, tmp);
            if (replaceDir(tmp, dest)) return dest.getAbsolutePath();
        }

        // مدل در حافظه نیست یا ناقصه؛ از داخل assets خود برنامه استخراجش کن
        deleteRecursive(tmp);
        copyAssetDir(c, "vosk-model-small-fa-0.5", tmp);
        if (!replaceDir(tmp, dest)) {
            deleteRecursive(dest);
            throw new IOException("مدل صوتی استخراج نشد");
        }
        return dest.getAbsolutePath();
    }

    static void copyDir(File s, File d) throws IOException {
        if (s.isDirectory()) {
            d.mkdirs();
            File[] kids = s.listFiles();
            if (kids == null) return;
            for (File f : kids) copyDir(f, new File(d, f.getName()));
        } else {
            d.getParentFile().mkdirs();
            InputStream in = new java.io.FileInputStream(s);
            OutputStream out = new FileOutputStream(d);
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close(); out.close();
        }
    }

    static void copyAssetDir(Context c, String assetPath, File destDir) throws IOException {
        destDir.mkdirs();
        String[] kids = c.getAssets().list(assetPath);
        if (kids == null || kids.length == 0) {
            InputStream in = c.getAssets().open(assetPath);
            OutputStream out = new FileOutputStream(destDir);
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close(); out.close();
            return;
        }
        for (String k : kids) {
            File out = new File(destDir, k);
            String[] sub = c.getAssets().list(assetPath + "/" + k);
            if (sub != null && sub.length > 0) copyAssetDir(c, assetPath + "/" + k, out);
            else {
                out.getParentFile().mkdirs();
                InputStream in = c.getAssets().open(assetPath + "/" + k);
                OutputStream o = new FileOutputStream(out);
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                in.close(); o.close();
            }
        }
    }

    public static synchronized void loadModel(String path) throws IOException {
        if (modelLoaded) return;
        LibVosk.setLogLevel(LogLevel.WARNINGS);
        model = new Model(path);
        modelLoaded = true;
    }

    // حلقهٔ پیوسته: تا وقتی stop نشه اجرا می‌شه
    public static void loop(Context c, Listener listener, java.util.concurrent.atomic.AtomicBoolean running) {
        try {
            ensureModel(c);
            loadModel(new File(c.getFilesDir(), "vosk-model").getAbsolutePath());
            Recognizer rec = new Recognizer(model, 16000.0f);
            rec.setWords(false);
            int bufSize = 4096;
            AudioRecord ar = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize * 4);
            ar.startRecording();
            short[] buf = new short[bufSize];

            // Silence-based finalization: Vosk only returns a final result after it
            // detects an end of utterance. With background noise or short clipped
            // commands that never happens, so we also finalize after a quiet pause.
            String lastPartial = "";
            long lastVoiceAt = System.currentTimeMillis();
            long lastPartialAt = 0L;
            final long SILENCE_MS = 900L;
            final int VOICE_RMS = 900;

            while (running.get()) {
                int n = ar.read(buf, 0, buf.length);
                if (n > 0) {
                    double rms = rms(buf, n);
                    long now = System.currentTimeMillis();
                    if (rms >= VOICE_RMS) {
                        lastVoiceAt = now;
                    }
                    boolean hasFinal = rec.acceptWaveForm(buf, n);
                    if (hasFinal) {
                        String text = parseText(rec.getResult());
                        if (!text.isEmpty()) {
                            listener.onFinal(text);
                        }
                        lastPartial = "";
                        rec.reset();
                    } else {
                        String partial = parsePartial(rec.getPartialResult());
                        if (!partial.isEmpty()) {
                            if (!partial.equals(lastPartial)) {
                                lastPartial = partial;
                                lastPartialAt = now;
                            }
                            listener.onPartial(partial);
                        }
                        // No new speech for a while -> treat the last partial as the final command.
                        if (!lastPartial.isEmpty()
                                && now - lastVoiceAt > SILENCE_MS
                                && lastPartialAt > 0
                                && now - lastPartialAt > SILENCE_MS / 2) {
                            String text = lastPartial;
                            lastPartial = "";
                            listener.onFinal(text);
                            rec.reset();
                        }
                    }
                }
            }
            ar.stop(); ar.release(); rec.close();
        } catch (Exception e) {
            android.util.Log.e("CR-Vosk", "loop failed", e);
            listener.onFinal("__ERROR__" + e.getMessage());
        }
    }

    static double rms(short[] buf, int n) {
        long sum = 0;
        for (int i = 0; i < n; i++) {
            sum += (long) buf[i] * buf[i];
        }
        return Math.sqrt((double) sum / Math.max(1, n));
    }

    static String parseText(String json) {
        try { return new JSONObject(json).optString("text", ""); } catch (Exception e) { return ""; }
    }
    static String parsePartial(String json) {
        try { return new JSONObject(json).optString("partial", ""); } catch (Exception e) { return ""; }
    }
}
