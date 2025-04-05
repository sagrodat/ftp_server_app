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
        super.onReceive(context, intent); // Important!

        String action = intent.getAction();
        Log.d(TAG, "onReceive action: " + action);

        if (ACTION_WIDGET_TOGGLE_FTP.equals(action)) {
            // Toggle button was clicked - decide whether to start or stop
            int currentState = FtpService.getCurrentServerState();
            Intent serviceIntent = new Intent(context, FtpService.class);
            if (currentState == Constants.SERVER_STATE_RUNNING) {
                serviceIntent.setAction(Constants.ACTION_STOP_FTP);
                Log.d(TAG,"Widget sending STOP intent");
            } else {
                // Check permissions before starting from widget? Ideally, yes, but complex.
                // Assume permissions are okay for simplicity, rely on Service/Activity checks.
                serviceIntent.setAction(Constants.ACTION_START_FTP);
                Log.d(TAG,"Widget sending START intent");
            }
            ContextCompat.startForegroundService(context, serviceIntent);
        } else if (Constants.BROADCAST_SERVER_STATE.equals(action)) {
            // Service state changed, update all widget instances
            int state = intent.getIntExtra(Constants.EXTRA_SERVER_STATE, Constants.SERVER_STATE_STOPPED);
            String ipAddress = intent.getStringExtra(Constants.EXTRA_SERVER_IP);
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, FtpWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
            for (int appWidgetId : appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, state, ipAddress);
            }
        } else if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)) {
            // Handle standard updates if necessary (already covered by onUpdate)
        }
    }


    // Method to update a single widget instance
    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId, int serverState, String ipAddress) {
        Log.d(TAG, "Updating widget " + appWidgetId + " for state: " + serverState);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.ftp_widget_layout);

        // Ustaw odpowiednią ikonę na ImageButton w zależności od stanu
        int iconResId; // Zmienna na ID zasobu ikony
        switch (serverState) {
            case Constants.SERVER_STATE_RUNNING:
                iconResId = R.drawable.ic_widget_stop; // Ikona Stop
                break;
            case Constants.SERVER_STATE_STARTING:
                iconResId = R.drawable.ic_widget_sync; // Ikona ładowania/synchronizacji
                break;
            case Constants.SERVER_STATE_ERROR:
                iconResId = R.drawable.ic_widget_error; // Ikona błędu
                break;
            case Constants.SERVER_STATE_STOPPED:
            default:
                iconResId = R.drawable.ic_widget_play; // Ikona Play
                break;
        }
        // Ustaw obrazek dla ImageButton o ID widget_toggle_button
        views.setImageViewResource(R.id.widget_toggle_button, iconResId);

        // Ustawienie PendingIntent dla kliknięcia - bez zmian, teraz dla ImageButton
        Intent intent = new Intent(context, FtpWidgetProvider.class);
        intent.setAction(ACTION_WIDGET_TOGGLE_FTP);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE); // Użyj appWidgetId w requestCode dla unikalności
        views.setOnClickPendingIntent(R.id.widget_toggle_button, pendingIntent);

        // Zaktualizuj widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    // Static helper method to trigger updates for all widgets (called from Service)
    public static void updateAllWidgets(Context context, int serverState, String ipAddress) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisAppWidget = new ComponentName(context.getPackageName(), FtpWidgetProvider.class.getName());
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);
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