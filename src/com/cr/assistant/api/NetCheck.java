package com.cr.assistant.api;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * Requires android.permission.ACCESS_NETWORK_STATE. Without it every
 * ConnectivityManager call throws SecurityException, so the app would treat a
 * perfectly good connection as offline and refuse Wikipedia / web search.
 */
public class NetCheck {
    /** True when the device has validated internet. */
    public static boolean isOnline(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network n = cm.getActiveNetwork();
            if (n == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(n);
            if (caps == null) return false;
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false;
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
        } catch (Exception e) {
            return false;
        }
    }
}
