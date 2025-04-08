package com.example.ftpserverapp;

// ** ADD/CHECK THESE IMPORTS **
import android.Manifest;
import android.app.AlertDialog; // Import AlertDialog
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri; // Import Uri
import android.os.Build;
import android.os.Bundle;
import android.os.Environment; // Import Environment
import android.provider.Settings; // Import Settings
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
// import androidx.annotation.NonNull; // No longer needed directly maybe
import androidx.appcompat.app.AppCompatActivity;
// import androidx.core.app.ActivityCompat; // No longer needed directly maybe
import androidx.core.content.ContextCompat;

// Keep other imports like List, ArrayList, Map

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private TextView tvStatus, tvAddress, tvCredentials;
    private Button btnStart, btnStop, btnGrantPermissions;

    private BroadcastReceiver serverStateReceiver;


    // Launcher for Notification permission (and potentially others NOT requiring settings)
    private ActivityResultLauncher<String[]> requestPermissionLauncher;
    // Launcher specifically for the MANAGE_EXTERNAL_STORAGE settings screen
    private ActivityResultLauncher<Intent> manageStorageLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvAddress = findViewById(R.id.tvAddress);
        tvCredentials = findViewById(R.id.tvCredentials);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnGrantPermissions = findViewById(R.id.btnGrantPermissions);

        setupPermissionLaunchers(); // Register launchers

        // Initialize the receiver
        serverStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Constants.BROADCAST_SERVER_STATE.equals(intent.getAction())) {
                    int state = intent.getIntExtra(Constants.EXTRA_SERVER_STATE, Constants.SERVER_STATE_STOPPED);
                    String ipAddress = intent.getStringExtra(Constants.EXTRA_SERVER_IP);
                    Log.d(TAG, "Received broadcast - State: " + state + ", IP: " + ipAddress);
                    updateUI(state, ipAddress); // Update UI based on broadcast
                }
            }
        };


        // --- BUTTON CLICK LISTENERS ---
        btnStart.setOnClickListener(v -> {

            if (!NetworkUtils.isWifiConnected(this)) {
                Toast.makeText(this, R.string.wifi_required_error, Toast.LENGTH_LONG).show();
                return; // Stop if not connected to WiFi
            }

            // Check permissions one last time before starting
            if (checkPermissionsGranted()) {
                Log.d(TAG, "Start button clicked - sending start intent");
                Intent serviceIntent = new Intent(this, FtpService.class);
                serviceIntent.setAction(Constants.ACTION_START_FTP);
                ContextCompat.startForegroundService(this, serviceIntent);
            } else {
                // This shouldn't happen if button is enabled correctly, but as a fallback:
                Toast.makeText(this, "Permissions still required!", Toast.LENGTH_SHORT).show();
                // Trigger the request flow again if needed
                requestNeededPermissions();
            }
        });

        btnStop.setOnClickListener(v -> {
            Log.d(TAG, "Stop button clicked - sending stop intent");
            Intent serviceIntent = new Intent(this, FtpService.class);
            serviceIntent.setAction(Constants.ACTION_STOP_FTP);
            ContextCompat.startForegroundService(this, serviceIntent);
        });

        btnGrantPermissions.setOnClickListener(v -> requestNeededPermissions()); // Grant button triggers request flow

        // Initial UI Update based on current state and permissions
        updateUI(FtpService.getCurrentServerState(), FtpService.getCurrentIpAddress());
    }

    private void setupPermissionLaunchers() {
        // Launcher for standard permissions like Notifications
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    boolean notificationsGranted = true;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationsGranted = result.getOrDefault(Manifest.permission.POST_NOTIFICATIONS, false);
                    }

                    if (notificationsGranted) {
                        Log.d(TAG, "Notification permission granted or not needed.");
                        // Now check/request Manage Storage if needed
                        requestManageStoragePermissionIfNotGranted();
                    } else {
                        Log.w(TAG, "Notification permission denied.");
                        // Show rationale or guide to settings for Notifications if needed (similar logic as before)
                        Toast.makeText(this, "Notification permission denied. Functionality may be limited.", Toast.LENGTH_SHORT).show();
                        showPermissionsRequiredUI(); // Ensure grant button is visible
                    }
                    // Update UI after handling results
                    updateUI(FtpService.getCurrentServerState(), FtpService.getCurrentIpAddress());
                });

        // Launcher for the result of the MANAGE_EXTERNAL_STORAGE settings screen
        manageStorageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // User has returned from the settings screen. Check the permission again.
                    Log.d(TAG, "Returned from Manage Storage settings screen.");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        if (Environment.isExternalStorageManager()) {
                            Log.d(TAG, "MANAGE_EXTERNAL_STORAGE granted after returning from settings.");
                            Toast.makeText(this, "All files access granted!", Toast.LENGTH_SHORT).show();
                            // Check if notifications are also granted now
                            if (checkNotificationPermissionGranted()) {
                                btnGrantPermissions.setVisibility(View.GONE);
                            } else {
                                // Still need notifications
                                requestNotificationPermission();
                            }

                        } else {
                            Log.w(TAG, "MANAGE_EXTERNAL_STORAGE still denied after returning from settings.");
                            Toast.makeText(this, "All files access is required for FTP server.", Toast.LENGTH_LONG).show();
                            showPermissionsRequiredUI();
                        }
                    }
                    // Update UI based on the new permission state
                    updateUI(FtpService.getCurrentServerState(), FtpService.getCurrentIpAddress());
                });
    }

    // Renamed method: Checks ALL needed permissions and returns true if all granted
    private boolean checkPermissionsGranted() {
        boolean storageGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager(); // Check MANAGE_EXTERNAL_STORAGE on R+
        boolean notificationsGranted = checkNotificationPermissionGranted();

        Log.d(TAG, "Permissions Check: Storage=" + storageGranted + ", Notifications=" + notificationsGranted);
        return storageGranted && notificationsGranted;
    }

    // Helper specifically for Notification permission check
    private boolean checkNotificationPermissionGranted() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }


    // New method to orchestrate requesting necessary permissions
    private void requestNeededPermissions() {
        // 1. Check/Request Notification permission first (if applicable)
        if (!checkNotificationPermissionGranted()) {
            requestNotificationPermission();
            // Storage check will happen after notification result if needed
        } else {
            // 2. If Notifications are okay, check/request Storage
            requestManageStoragePermissionIfNotGranted();
        }
        // Update UI to reflect current state (buttons might disable during request)
        updateUI(FtpService.getCurrentServerState(), FtpService.getCurrentIpAddress());
    }

    // Request only Notification permission
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "Requesting Notification permission...");
            requestPermissionLauncher.launch(new String[]{Manifest.permission.POST_NOTIFICATIONS});
        } else {
            Log.d(TAG, "Notification permission not required before Android 13.");
        }
    }

    // Check and redirect to settings for MANAGE_EXTERNAL_STORAGE if needed
    private void requestManageStoragePermissionIfNotGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Requires Android 11+
            if (!Environment.isExternalStorageManager()) {
                Log.w(TAG, "MANAGE_EXTERNAL_STORAGE permission needed. Redirecting to settings.");
                // Show a dialog explaining WHY we need this permission before sending user to settings
                new AlertDialog.Builder(this)
                        .setTitle("All Files Access Required")
                        .setMessage("To allow the FTP server to access all files and folders on your shared storage, please enable 'All files access' for this app on the next screen.")
                        .setPositiveButton("Go to Settings", (dialog, which) -> {
                            launchManageStorageSettings(); // Launch the settings intent
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> {
                            dialog.dismiss();
                            Toast.makeText(this, "Server cannot access files without permission.", Toast.LENGTH_SHORT).show();
                            showPermissionsRequiredUI();
                            updateUI(FtpService.getCurrentServerState(), FtpService.getCurrentIpAddress()); // Refresh UI
                        })
                        .show();
            } else {
                Log.d(TAG, "MANAGE_EXTERNAL_STORAGE already granted.");
                // If storage is granted, ensure Grant button is hidden and update UI
                btnGrantPermissions.setVisibility(View.GONE);
                updateUI(FtpService.getCurrentServerState(), FtpService.getCurrentIpAddress());
            }
        } else {
            // On Android 10 (API 29) and below, MANAGE_EXTERNAL_STORAGE doesn't exist.
            // READ/WRITE should have been requested (though maybe not applicable anymore)
            // For simplicity now, assume older versions might work or have different issues.
            Log.d(TAG, "MANAGE_EXTERNAL_STORAGE not applicable before Android 11.");
            btnGrantPermissions.setVisibility(View.GONE); // Hide grant button if not applicable
            updateUI(FtpService.getCurrentServerState(), FtpService.getCurrentIpAddress());
        }
    }

    // Helper to launch the specific settings intent
    private void launchManageStorageSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                Log.d(TAG, "Launching MANAGE_EXTERNAL_STORAGE settings.");
                manageStorageLauncher.launch(intent); // Use the specific launcher
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch MANAGE_EXTERNAL_STORAGE settings", e);
                // Fallback: Try generic intent (might not work)
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    manageStorageLauncher.launch(intent);
                } catch (Exception e2) {
                    Log.e(TAG, "Failed to launch generic MANAGE_ALL_FILES settings", e2);
                    Toast.makeText(this, "Could not open All Files Access settings. Please grant manually.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    // Shows the Grant button and disables Start
    private void showPermissionsRequiredUI() {
        btnGrantPermissions.setVisibility(View.VISIBLE);
        btnStart.setEnabled(false); // Disable start if permissions missing
    }


    @Override
    protected void onResume() {
        super.onResume();
        // Register receiver for immediate updates
        IntentFilter filter = new IntentFilter(Constants.BROADCAST_SERVER_STATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(serverStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        }

        Log.d(TAG,"Broadcast receiver registered");

        // Also refresh UI on resume in case state changed while paused
        updateUI(FtpService.getCurrentServerState(), FtpService.getCurrentIpAddress());
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister receiver to prevent leaks
        unregisterReceiver(serverStateReceiver);
        Log.d(TAG,"Broadcast receiver unregistered");
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // No receiver to unregister anymore
    }

    // Updates UI based on service state AND permission status
    private void updateUI(int state, String ipAddress) {
        Log.d(TAG, "Updating UI for state: " + state + ", IP: " + ipAddress);

        // First, determine overall permission status
        boolean allPermissionsGranted = checkPermissionsGranted();

        // Update Status Text and Address
        switch (state) {
            case Constants.SERVER_STATE_RUNNING:
                tvStatus.setText(R.string.server_running);
                if (ipAddress != null && !ipAddress.isEmpty() && !ipAddress.equals("0.0.0.0")) {
                    tvAddress.setText("ftp://" + ipAddress + ":" + Constants.FTP_PORT);
                } else {
                    tvAddress.setText("ftp://(Getting IP...):" + Constants.FTP_PORT);
                }
                break;
            case Constants.SERVER_STATE_STARTING:
                tvStatus.setText(R.string.server_starting);
                tvAddress.setText(R.string.address_placeholder);
                break;
            case Constants.SERVER_STATE_ERROR:
                tvStatus.setText(R.string.server_error);
                tvAddress.setText(R.string.address_placeholder);
                break;
            case Constants.SERVER_STATE_STOPPED:
            default:
                tvStatus.setText(R.string.server_stopped);
                tvAddress.setText(R.string.address_placeholder);
                break;
        }

        // Update Button States based on permissions AND server state
        if (allPermissionsGranted) {
            btnGrantPermissions.setVisibility(View.GONE); // Hide grant button if all granted
            if (state == Constants.SERVER_STATE_RUNNING || state == Constants.SERVER_STATE_STARTING) {
                btnStart.setEnabled(false);
                btnStop.setEnabled(state == Constants.SERVER_STATE_RUNNING); // Only enable Stop if fully running
            } else { // Stopped or Error
                btnStart.setEnabled(true);
                btnStop.setEnabled(false);
            }
        } else {
            // Permissions are missing
            showPermissionsRequiredUI(); // Show Grant button, disable Start
            btnStop.setEnabled(false); // Stop should also be disabled
        }
    }
}