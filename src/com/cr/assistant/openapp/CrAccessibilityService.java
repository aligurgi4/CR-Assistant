package com.cr.assistant.openapp;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.regex.Pattern;

/** سرویس دسترسی برای کار داخل برنامه‌های دیگه (مثلاً کلیک دکمهٔ تماس در اپ تلفن). */
public class CrAccessibilityService extends AccessibilityService {
    static volatile CrAccessibilityService instance;
    static volatile String nextClickRegex;
    static volatile long nextClickDeadline;

    @Override
    protected void onServiceConnected() {
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        String regex = nextClickRegex;
        long deadline = nextClickDeadline;
        if (regex == null || System.currentTimeMillis() > deadline) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            AccessibilityNodeInfo n = find(root, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
            if (n != null) {
                if (!n.isClickable() && n.getParent() != null) n = n.getParent();
                if (n.isClickable()) {
                    n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    nextClickRegex = null;
                    return;
                }
            }
        }
    }

    AccessibilityNodeInfo find(AccessibilityNodeInfo node, Pattern p) {
        if (node == null) return null;
        CharSequence t = node.getText();
        CharSequence d = node.getContentDescription();
        if ((t != null && p.matcher(t.toString()).find()) || (d != null && p.matcher(d.toString()).find())) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = find(node.getChild(i), p);
            if (r != null) return r;
        }
        return null;
    }

    @Override
    public void onInterrupt() {}

    /** بعد از چند ثانیه نزدیک‌ترین گرهٔ مطابق regex رو کلیک می‌کنه (یک‌بار). */
    public static void clickSoon(String regex, int seconds) {
        nextClickRegex = regex;
        nextClickDeadline = System.currentTimeMillis() + seconds * 1000L;
    }

    public static boolean isEnabled() {
        return instance != null;
    }
}
