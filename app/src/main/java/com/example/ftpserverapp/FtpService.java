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

// Upewnij się, że TE importy są obecne:
import org.apache.ftpserver.ftplet.UserManager; // Interfejs!
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory; // Fabryka
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor; // Proste szyfrowanie (lub inne jak Md5PasswordEncryptor)
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.UserManager; // Ważne dla interfejsu UserManager
import org.apache.ftpserver.ftplet.User; // Ważne dla BaseUser i metody save
import org.apache.ftpserver.ftplet.FtpException; // Dla obsługi błędów
import org.apache.ftpserver.usermanager.impl.BaseUser; // Dla tworzenia użytkownika
import org.apache.ftpserver.usermanager.impl.WritePermission; // Dla uprawnień
import org.apache.ftpserver.ftplet.Authority; // Dla uprawnień

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
        // Perform network operations on a background thread
        mExecutorService.submit(() -> {
            updateState(Constants.SERVER_STATE_STARTING, null);
            try { // Dodajemy try-catch wokół całego startu serwera

                FtpServerFactory serverFactory = new FtpServerFactory();
                ListenerFactory listenerFactory = new ListenerFactory();
                listenerFactory.setPort(Constants.FTP_PORT);

                // --- Poprawiona Konfiguracja Portów Pasywnych ---
                org.apache.ftpserver.DataConnectionConfigurationFactory dccf =
                        new org.apache.ftpserver.DataConnectionConfigurationFactory();

                // 1. Ustaw JEDEN konkretny port pasywny (np. 2300)
                dccf.setPassivePorts("2300");
                Log.d(TAG, "Set passive port to 2300");

                // 2. Ustaw adres zewnętrzny na localhost (tak jak było, logi pokazały, że serwer to wysyłał)
                //dccf.setPassiveExternalAddress("127.0.0.1");
                //Log.d(TAG, "Set passive external address to 127.0.0.1");


                // 3. Przypisz konfigurację połączenia danych do listenera (tak jak było)
                listenerFactory.setDataConnectionConfiguration(dccf.createDataConnectionConfiguration());
                // --- Koniec Poprawionej Konfiguracji ---

                serverFactory.addListener("default", listenerFactory.createListener());

                // --- Nowa Konfiguracja Menedżera Użytkowników ---
                PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();

                // Opcjonalnie: Ustawienie szyfrowania hasła. Bez tego może użyć domyślnego (MD5) lub rzucić błędem.
                // Dla prostoty użyjemy ClearText - hasło będzie przechowywane jako zwykły tekst.
                // Pamiętaj, żeby dodać import: import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor;
                userManagerFactory.setPasswordEncryptor(new org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor());

                // Utwórz instancję UserManager (będzie to PropertiesUserManager) z fabryki
                // Ważne: importuj interfejs UserManager! import org.apache.ftpserver.ftplet.UserManager;
                UserManager userManager = userManagerFactory.createUserManager();

                // Utwórz obiekt użytkownika BaseUser (tak jak poprzednio)
                BaseUser user = new BaseUser();
                user.setName(Constants.FTP_USER); // "android"
                // Ustaw hasło - jeśli ustawiłeś PasswordEncryptor, on zajmie się szyfrowaniem przy zapisie
                user.setPassword(Constants.FTP_PASS); // "android"

                // Ustaw katalog domowy (tak jak poprzednio)
                File externalStorageDir = Environment.getExternalStorageDirectory();
                if (!externalStorageDir.exists()) {
                    Log.e(TAG, "External storage directory not found! Creating...");
                    if (!externalStorageDir.mkdirs()) {
                        Log.e(TAG, "Failed to create external storage directory!");
                        updateState(Constants.SERVER_STATE_ERROR, null);
                        return; // Stop if cannot create home dir base
                    } else {
                        Log.d(TAG, "Created external storage directory.");
                    }
                } else if (!externalStorageDir.canRead() || !externalStorageDir.canWrite()) {
                    Log.e(TAG, "Missing Read/Write permission for external storage!");
                    // Można by tu rzucić wyjątkiem lub poinformować użytkownika
                }
                String homeDirectory = externalStorageDir.getAbsolutePath();
                user.setHomeDirectory(homeDirectory);
                Log.d(TAG, "Setting user home directory to: " + homeDirectory);


                // Nadaj uprawnienia (tak jak poprzednio)
                List<Authority> authorities = new ArrayList<>();
                authorities.add(new WritePermission()); // Importuj WritePermission
                user.setAuthorities(authorities);

                try {
                    // Zapisz użytkownika do UTWORZONEJ instancji menedżera
                    userManager.save(user);
                    Log.i(TAG, "User '" + Constants.FTP_USER + "' saved to UserManager instance.");
                } catch (FtpException e) {
                    Log.e(TAG, "Failed to save user to UserManager", e);
                    updateState(Constants.SERVER_STATE_ERROR, null);
                    return; // Zatrzymaj, jeśli nie można zapisać użytkownika
                } catch (Exception e) {
                    // Złap inne potencjalne błędy przy save
                    Log.e(TAG, "Unexpected error saving user", e);
                    updateState(Constants.SERVER_STATE_ERROR, null);
                    return;
                }


                // Ustaw TĘ skonfigurowaną instancję menedżera na fabryce serwera
                serverFactory.setUserManager(userManager);
                // --- Koniec Nowej Konfiguracji ---


                // Start serwera (tak jak poprzednio)
                Log.d(TAG, "Creating FtpServer instance...");
                mFtpServer = serverFactory.createServer();
                Log.d(TAG, "Starting FtpServer...");
                mFtpServer.start();
                Log.i(TAG, "FTP Server started successfully on port " + Constants.FTP_PORT);

                // Pobierz IP i zaktualizuj stan (tak jak poprzednio)
                currentIpAddress = NetworkUtils.getWifiIpAddress(this);
                if (currentIpAddress == null || currentIpAddress.equals("0.0.0.0")) {
                    currentIpAddress = NetworkUtils.getIPAddress(true); // Fallback
                }
                Log.i(TAG, "Server IP Address: " + currentIpAddress);

                startForeground(Constants.NOTIFICATION_ID, createNotification(currentIpAddress));
                updateState(Constants.SERVER_STATE_RUNNING, currentIpAddress);

                // Złap błędy startu serwera
            } catch (FtpException e) {
                Log.e(TAG, "FtpException starting FTP server", e);
                updateState(Constants.SERVER_STATE_ERROR, null);
                if (mFtpServer != null && !mFtpServer.isStopped()) mFtpServer.stop(); // Spróbuj zatrzymać jeśli częściowo ruszył
                mFtpServer = null;
                stopSelf(); // Zatrzymaj serwis
            } catch (Exception e) { // Złap inne potencjalne błędy (np. z uprawnień przy sprawdzaniu home dir)
                Log.e(TAG, "Unexpected error starting FTP server", e);
                updateState(Constants.SERVER_STATE_ERROR, null);
                if (mFtpServer != null && !mFtpServer.isStopped()) mFtpServer.stop();
                mFtpServer = null;
                stopSelf(); // Zatrzymaj serwis
            }
        }); // Koniec mExecutorService.submit
    } // Koniec metody startFtpServer

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

        // --- Use standard broadcast ---
        Intent intent = new Intent(Constants.BROADCAST_SERVER_STATE);
        intent.putExtra(Constants.EXTRA_SERVER_STATE, state);
        intent.putExtra(Constants.EXTRA_SERVER_IP, ipAddress);
        // Set package to make it explicit, slightly more secure than fully implicit
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
        Log.d(TAG,"Sent broadcast for state: " + state);
        // --- End standard broadcast ---


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