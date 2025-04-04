package com.example.ftpserverapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;


import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FtpService extends Service {

    private static final String TAG = "FtpService";

    private FtpServer mFtpServer;
    private ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
    private static int currentServerState = Constants.SERVER_STATE_STOPPED; // Track state statically
    private static String currentIpAddress = null;


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service Created");
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand received");
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case Constants.ACTION_START_FTP:
                    Log.d(TAG, "Action START received");
                    startFtpServer();
                    break;
                case Constants.ACTION_STOP_FTP:
                    Log.d(TAG, "Action STOP received");
                    stopFtpServer();
                    break;
            }
        }
        // If service is killed, restart it with the last intent
        return START_REDELIVER_INTENT;
    }

    private void startFtpServer() {
        if (mFtpServer != null && !mFtpServer.isStopped()) {
            Log.w(TAG, "Server already running");
            Toast.makeText(this, "Server is already running", Toast.LENGTH_SHORT).show();
            updateState(Constants.SERVER_STATE_RUNNING, currentIpAddress); // Re-broadcast state
            return;
        }

        // Perform network operations on a background thread
        mExecutorService.submit(() -> {
            updateState(Constants.SERVER_STATE_STARTING, null);
            try {
                FtpServerFactory serverFactory = new FtpServerFactory();
                ListenerFactory listenerFactory = new ListenerFactory();

                // Set the port
                listenerFactory.setPort(Constants.FTP_PORT);
                serverFactory.addListener("default", listenerFactory.createListener());

                // Setup User Manager
                PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();

                // !!! IMPORTANT: Using external storage root. Ensure Permissions are granted !!!
                File externalStorageDir = Environment.getExternalStorageDirectory();
                if (!externalStorageDir.exists()) {
                    Log.e(TAG, "External storage directory not found!");
                    if (!externalStorageDir.mkdirs()) {
                        Log.e(TAG, "Failed to create external storage directory!");
                        updateState(Constants.SERVER_STATE_ERROR, null);
                        return;
                    }
                }
                if (!externalStorageDir.canRead() || !externalStorageDir.canWrite()) {
                    Log.e(TAG, "Missing Read/Write permission for: " + externalStorageDir.getPath());
                    // Consider prompting user via notification if needed
                }

                String homeDirectory = externalStorageDir.getAbsolutePath();
                Log.d(TAG, "Setting home directory to: " + homeDirectory);

                // Create user
                BaseUser user = new BaseUser();
                user.setName(Constants.FTP_USER);
                user.setPassword(Constants.FTP_PASS);
                user.setHomeDirectory(homeDirectory);

                // Grant write permission
                List<Authority> authorities = new ArrayList<>();
                authorities.add(new WritePermission());
                user.setAuthorities(authorities);

                serverFactory.getUserManager().save(user); // Save the user

                serverFactory.setUserManager(userManagerFactory.createUserManager());

                // Start the server
                mFtpServer = serverFactory.createServer();
                mFtpServer.start();
                Log.i(TAG, "FTP Server started on port " + Constants.FTP_PORT);

                currentIpAddress = NetworkUtils.getWifiIpAddress(this);
                if (currentIpAddress == null || currentIpAddress.equals("0.0.0.0")) {
                    currentIpAddress = NetworkUtils.getIPAddress(true); // Fallback
                }
                Log.i(TAG, "Server IP Address: " + currentIpAddress);

                startForeground(Constants.NOTIFICATION_ID, createNotification(currentIpAddress));
                updateState(Constants.SERVER_STATE_RUNNING, currentIpAddress);

            } catch (FtpException e) {
                Log.e(TAG, "Error starting FTP server", e);
                updateState(Constants.SERVER_STATE_ERROR, null);
                stopSelf(); // Stop service if server fails to start
            } catch (Exception e) { // Catch other potential exceptions like file system issues
                Log.e(TAG, "Unexpected error starting FTP server", e);
                updateState(Constants.SERVER_STATE_ERROR, null);
                stopSelf();
            }
        });
    }

    private void stopFtpServer() {
        mExecutorService.submit(() -> {
            if (mFtpServer != null) {
                try {
                    if (!mFtpServer.isStopped()) {
                        mFtpServer.stop();
                        Log.i(TAG, "FTP Server stopped.");
                    }
                } catch (Exception e) { // FtpServer.stop() doesn't declare checked exceptions
                    Log.e(TAG, "Error stopping FTP server", e);
                    // Even if stopping fails, proceed to clean up state
                } finally {
                    mFtpServer = null; // Release reference
                    stopForeground(true); // Remove notification
                    updateState(Constants.SERVER_STATE_STOPPED, null);
                    stopSelf(); // Stop the service itself
                }

            } else {
                Log.w(TAG, "Server already stopped or null");
                updateState(Constants.SERVER_STATE_STOPPED, null); // Ensure state is correct
                stopForeground(true); // Ensure notification is removed if somehow stuck
                stopSelf();
            }
        });
    }

    // Broadcasts the current state to Activity and Widget
    private void updateState(int state, String ipAddress) {
        currentServerState = state;
        currentIpAddress = ipAddress;

        // Also update the widget directly (important if widget is added after service start)
        FtpWidgetProvider.updateAllWidgets(this, state, ipAddress);

        // Update notification content if running
        if (state == Constants.SERVER_STATE_RUNNING && mFtpServer != null && !mFtpServer.isStopped()) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(Constants.NOTIFICATION_ID, createNotification(ipAddress));
            }
        }
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW // Low importance for foreground service
            );
            serviceChannel.setDescription(getString(R.string.notification_channel_desc));

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
                Log.d(TAG, "Notification channel created.");
            }
        }
    }

    private Notification createNotification(String ipAddress) {
        // Intent to open MainActivity when notification is tapped
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        String contentText = getString(R.string.address_placeholder); // Default
        if (ipAddress != null && !ipAddress.isEmpty() && !ipAddress.equals("0.0.0.0")) {
            contentText = "ftp://" + ipAddress + ":" + Constants.FTP_PORT;
        } else {
            contentText = "IP Address not available";
        }


        return new NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.ftp_service_notification_title))
                .setContentText(contentText)
                .setSmallIcon(R.mipmap.ic_launcher) // Replace with a proper icon
                .setContentIntent(pendingIntent)
                .setOngoing(true) // Makes it non-dismissible
                .setOnlyAlertOnce(true) // Don't alert repeatedly on updates
                .build();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service Destroyed");
        // Ensure server is stopped cleanly if service is destroyed unexpectedly
        stopFtpServer();
        mExecutorService.shutdown();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    // Static method to get current state (useful for Activity/Widget initial setup)
    public static int getCurrentServerState() {
        return currentServerState;
    }
    public static String getCurrentIpAddress() {
        return currentIpAddress;
    }
}