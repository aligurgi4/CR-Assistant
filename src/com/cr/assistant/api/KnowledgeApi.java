package com.cr.assistant.api;

import android.content.Context;

import com.cr.assistant.Commands;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Answers questions that are not part of the fixed command list.
 * Uses a small local knowledge base, then Persian Wikipedia, then the user's
 * default browser as the final fallback.
 */
public class KnowledgeApi {

    private static JSONArray facts;

    private static synchronized JSONArray facts(Context c) {
        if (facts != null) return facts;
        try {
            InputStream is = c.getAssets().open("knowledge.json");
            BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            is.close();
            facts = new JSONObject(sb.toString()).getJSONArray("facts");
        } catch (Exception e) {
            facts = new JSONArray();
        }
        return facts;
    }

    /** Returns a short canned answer for common questions, or null. */
    public static String localAnswer(Context c, String raw) {
        String s = Commands.norm(raw);
        if (s.isEmpty()) return null;
        JSONArray list = facts(c);
        String bestAnswer = null;
        int best = 0;
        for (int i = 0; i < list.length(); i++) {
            JSONObject it = list.optJSONObject(i);
            if (it == null) continue;
            JSONArray keys = it.optJSONArray("keys");
            if (keys == null) continue;
            int score = 0;
            for (int j = 0; j < keys.length(); j++) {
                String k = Commands.norm(keys.optString(j, ""));
                if (!k.isEmpty() && s.contains(k)) score += k.length();
            }
            if (score > best) { best = score; bestAnswer = it.optString("answer", null); }
        }
        return bestAnswer;
    }

    /** Guesses whether a sentence is a question that deserves an answer attempt. */
    public static boolean looksLikeQuestion(String raw) {
        String s = Commands.norm(raw);
        if (s.isEmpty()) return false;
        if (s.endsWith("?") || s.endsWith("؟")) return true;
        String[] markers = {"چیست", "چیه", "کیست", "کیه", "چرا", "چطور", "چگونه", "کجا",
                "چند", "کدام", "معنی", "یعنی چی", "یعنی چه", "چقدر", "کدوم", "آیا", "تفاوت",
                "چه ", "چی ", "چند تا", "چطوری"};
        for (String m : markers) if (s.contains(m)) return true;
        return false;
    }

    /** Queries Persian Wikipedia and returns a short extract, or null. */
    public static String wikipedia(Context c, String raw) {
        if (!NetCheck.isOnline(c)) return null;
        try {
            String title = searchTitle(raw);
            if (title == null || title.isEmpty()) return null;
            String extract = extract(title);
            if (extract == null || extract.trim().isEmpty()) return null;
            return shorten(extract);
        } catch (Exception e) {
            return null;
        }
    }

    private static String searchTitle(String raw) throws Exception {
        String q = cleanQuestion(raw);
        String url = "https://fa.wikipedia.org/w/api.php?action=query&list=search&srsearch="
                + URLEncoder.encode(q, "UTF-8") + "&srlimit=1&format=json&utf8=1";
        JSONObject root = new JSONObject(http(url));
        JSONArray arr = root.getJSONObject("query").getJSONArray("search");
        if (arr.length() == 0) return null;
        return arr.getJSONObject(0).optString("title", "");
    }

    private static String extract(String title) throws Exception {
        String url = "https://fa.wikipedia.org/w/api.php?action=query&prop=extracts&exintro=1"
                + "&explaintext=1&redirects=1&format=json&utf8=1&titles="
                + URLEncoder.encode(title, "UTF-8");
        JSONObject root = new JSONObject(http(url));
        JSONObject pages = root.getJSONObject("query").getJSONObject("pages");
        java.util.Iterator<String> it = pages.keys();
        while (it.hasNext()) {
            JSONObject page = pages.optJSONObject(it.next());
            if (page == null) continue;
            String ex = page.optString("extract", "");
            if (!ex.isEmpty()) return ex;
        }
        return null;
    }

    private static String http(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(3500);
        con.setReadTimeout(4500);
        con.setRequestProperty("User-Agent", "CR-Assistant/1.1 (Android)");
        con.setRequestProperty("Accept", "application/json");
        int code = con.getResponseCode();
        if (code < 200 || code >= 300) { con.disconnect(); throw new Exception("http " + code); }
        BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        con.disconnect();
        return sb.toString();
    }

    static String cleanQuestion(String raw) {
        String s = Commands.norm(raw);
        String[] strip = {"چیست", "چیه", "کیست", "کیه", "چرا", "چطور", "چگونه", "کدام",
                "معنی", "یعنی چی", "یعنی چه", "آیا", "بگو", "برای من", "به من", "چی هست",
                "?", "؟", "لطفا", "لطفاً"};
        for (String x : strip) s = s.replace(x, " ");
        return s.replaceAll("\\s+", " ").trim();
    }

    static String shorten(String text) {
        String t = text.replaceAll("\\s+", " ").trim();
        if (t.length() <= 420) return t;
        int cut = t.lastIndexOf('.', 420);
        if (cut > 120) t = t.substring(0, cut + 1);
        else t = t.substring(0, 420) + "…";
        return t;
    }
}
