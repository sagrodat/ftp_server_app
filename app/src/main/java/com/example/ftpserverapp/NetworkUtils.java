package com.example.ftpserverapp;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.util.Log;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class NetworkUtils {

    private static final String TAG = "NetworkUtils";

    // Get the current WiFi IP Address
    public static String getWifiIpAddress(Context context) {
        WifiManager wifiMgr = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiMgr != null && wifiMgr.isWifiEnabled()) {
            int ipAddress = wifiMgr.getConnectionInfo().getIpAddress();
            // Convert little-endian to big-endian if needed
            // Formatter.formatIpAddress already handles this.
            return Formatter.formatIpAddress(ipAddress);
        } else {
            // Fallback for hotspot or other network types (less reliable for typical use case)
            return getIPAddress(true);
        }
    }

    public static boolean isWifiConnected(Context context) {
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMgr == null) {
            Log.e(TAG, "ConnectivityManager not available");
            return false; // Cannot determine state
        }

        Network activeNetwork = connMgr.getActiveNetwork();
        if (activeNetwork == null) {
            Log.d(TAG, "No active network connection.");
            return false; // No network connection at all
        }

        NetworkCapabilities capabilities = connMgr.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) {
            Log.d(TAG, "Cannot get network capabilities.");
            return false; // Cannot determine capabilities
        }

        // Check if the active network transport is WiFi
        boolean isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        Log.d(TAG, "Active network is WiFi: " + isWifi);
        return isWifi;
    }


    // Alternative method trying to find non-loopback IPv4 address
    public static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':')<0;

                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                return delim<0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "IP Address lookup failed", ex);
        }
        return null; // Or return "0.0.0.0" or similar placeholder
    }
}