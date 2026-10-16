package com.cr.assistant;

import android.content.Context;

import com.cr.assistant.api.GoogleApi;
import com.cr.assistant.api.KnowledgeApi;
import com.cr.assistant.api.LocalApi;
import com.cr.assistant.api.NetCheck;
import com.cr.assistant.openapp.Actions;

/**
 * Central pipeline for every spoken sentence:
 * 1) offline command intents, 2) local knowledge facts, 3) Persian Wikipedia,
 * 4) the user's default browser as the last fallback.
 */
public class CrCore {
    public static void process(Context c, String text, Object tts) {
        if (text == null || text.trim().isEmpty()) return;
        if (LocalApi.handle(c, text, tts)) return;

        String fact = KnowledgeApi.localAnswer(c, text);
        if (fact != null) { Actions.say(c, tts, fact); return; }

        if (!KnowledgeApi.looksLikeQuestion(text)) {
            Actions.say(c, tts, "این دستور رو نمی‌شناسم. بگو «راهنما» تا لیست دستورها رو ببینی");
            return;
        }

        if (NetCheck.isOnline(c)) {
            String wiki = KnowledgeApi.wikipedia(c, text);
            if (wiki != null) { Actions.say(c, tts, wiki); return; }
            GoogleApi.searchDefaultBrowser(c, text, tts);
        } else {
            Actions.say(c, tts, "آفلاینم و جواب این سوال رو ندارم. به اینترنت وصل شو تا از ویکی‌پدیا یا مرورگر کمکت کنم");
        }
    }
}
