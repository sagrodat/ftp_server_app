package com.example.ftpserverapp;

public class Constants {
    public static final String ACTION_START_FTP = "com.example.ftpserverapp.ACTION_START_FTP";
    public static final String ACTION_STOP_FTP = "com.example.ftpserverapp.ACTION_STOP_FTP";
    public static final String BROADCAST_SERVER_STATE = "com.example.ftpserverapp.BROADCAST_SERVER_STATE";
    public static final String EXTRA_SERVER_STATE = "com.example.ftpserverapp.EXTRA_SERVER_STATE";
    public static final String EXTRA_SERVER_IP = "com.example.ftpserverapp.EXTRA_SERVER_IP";

    public static final int SERVER_STATE_STOPPED = 0;
    public static final int SERVER_STATE_STARTING = 1;
    public static final int SERVER_STATE_RUNNING = 2;
    public static final int SERVER_STATE_ERROR = 3;

    public static final String NOTIFICATION_CHANNEL_ID = "FtpServerChannel";
    public static final int NOTIFICATION_ID = 1;

    public static final int FTP_PORT = 2221;
    public static final String FTP_USER = "android";
    public static final String FTP_PASS = "android";
}