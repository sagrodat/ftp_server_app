package com.example.ftpserverapp;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;

public class FtpWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "FtpWidgetProvider";
    // Action specifically for the widget button click
    public static final String ACTION_WIDGET_TOGGLE_FTP = "com.example.ftpserverapp.ACTION_WIDGET_TOGGLE_FTP";


    // Called when the widget is updated (e.g., added, interval elapsed)
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate called");
        // Update all instances of the widget
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, FtpService.getCurrentServerState(), FtpService.getCurrentIpAddress());
        }
    }

    // Called when a broadcast is received, including our custom action and state changes
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();
        Log.d(TAG, "onReceive action: " + action);

        if (ACTION_WIDGET_TOGGLE_FTP.equals(action)) {
            int currentServiceState = FtpService.getCurrentServerState();
            Intent serviceIntent = new Intent(context, FtpService.class);
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, FtpWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

            if (currentServiceState == Constants.SERVER_STATE_RUNNING) {
                // Stop Service
                serviceIntent.setAction(Constants.ACTION_STOP_FTP);
                Log.d(TAG, "Widget sending STOP intent");
                ContextCompat.startForegroundService(context, serviceIntent);
            } else {
                // Try to Start Service
                if (NetworkUtils.isWifiConnected(context)) {
                    // WiFi ON: Start service, set sync icon
                    serviceIntent.setAction(Constants.ACTION_START_FTP);
                    Log.d(TAG, "Widget sending START intent (WiFi connected)");
                    ContextCompat.startForegroundService(context, serviceIntent);
                    for (int appWidgetId : appWidgetIds) {
                        updateWidgetIcon(context, appWidgetManager, appWidgetId, R.drawable.ic_widget_sync);
                    }
                } else {
                    // WiFi OFF: Show ERROR icon explicitly
                    Log.w(TAG, "Widget start aborted (No WiFi) -> Setting widget to ERROR (Red)");
                    for (int appWidgetId : appWidgetIds) {
                        // Set the icon to RED to indicate failure condition
                        updateWidgetIcon(context, appWidgetManager, appWidgetId, R.drawable.ic_widget_error);
                    }
                    // DO NOT start the service
                }
            }
        } else if (Constants.BROADCAST_SERVER_STATE.equals(action)) {
            // Handle broadcast updates (as before)
            int state = intent.getIntExtra(Constants.EXTRA_SERVER_STATE, Constants.SERVER_STATE_STOPPED);
            String ipAddress = intent.getStringExtra(Constants.EXTRA_SERVER_IP);
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, FtpWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
            Log.d(TAG, "Received server state broadcast (" + state + "), updating " + appWidgetIds.length + " widgets.");
            for (int appWidgetId : appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, state, ipAddress);
            }
        } else if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)) {
            // Handle standard updates (as before)
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, FtpWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
            Log.d(TAG, "Received standard widget update action, updating " + appWidgetIds.length + " widgets.");
            for (int appWidgetId : appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, FtpService.getCurrentServerState(), FtpService.getCurrentIpAddress());
            }
        }
    } // end onReceive

    // updateAppWidget method (as before)
    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId, int serverState, String ipAddress) {
        Log.d(TAG, "Updating widget " + appWidgetId + " fully for service state: " + serverState);
        int iconResId;
        switch (serverState) {
            case Constants.SERVER_STATE_RUNNING: iconResId = R.drawable.ic_widget_stop; break;
            case Constants.SERVER_STATE_STARTING: iconResId = R.drawable.ic_widget_sync; break;
            case Constants.SERVER_STATE_ERROR: iconResId = R.drawable.ic_widget_error; break;
            case Constants.SERVER_STATE_STOPPED: default: iconResId = R.drawable.ic_widget_play; break;
        }
        updateWidgetIcon(context, appWidgetManager, appWidgetId, iconResId);
    }

    // updateWidgetIcon helper method (as before)
    static void updateWidgetIcon(Context context, AppWidgetManager appWidgetManager, int appWidgetId, int iconResId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.ftp_widget_layout);
        views.setImageViewResource(R.id.widget_toggle_button, iconResId);
        Intent intent = new Intent(context, FtpWidgetProvider.class);
        intent.setAction(ACTION_WIDGET_TOGGLE_FTP);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_toggle_button, pendingIntent);
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    // updateAllWidgets method (as before)
    public static void updateAllWidgets(Context context, int serverState, String ipAddress) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisAppWidget = new ComponentName(context.getPackageName(), FtpWidgetProvider.class.getName());
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);
        Log.d(TAG, "Updating all widgets (" + appWidgetIds.length + ") via updateAllWidgets call for state: " + serverState);
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, serverState, ipAddress);
        }
    }

    @Override
    public void onEnabled(Context context) {
        // Enter relevant functionality for when the first widget is created
        Log.d(TAG, "Widget enabled");
    }

    @Override
    public void onDisabled(Context context) {
        // Enter relevant functionality for when the last widget is disabled
        Log.d(TAG, "Widget disabled");
    }
}