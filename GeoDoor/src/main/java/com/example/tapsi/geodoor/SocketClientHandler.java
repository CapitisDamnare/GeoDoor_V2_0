package com.example.tapsi.geodoor;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Objects;

public class SocketClientHandler extends Service {

    String TAG = "tapsi_Socket";

    // ClientThread SocketHandler
    private Socket socket;
    private ClientThread client;
    Thread t = null;

    // File data stuff
    private SharedPreferences settingsData;
    private SharedPreferences.Editor fileEditor;

    private String strName;
    private int ServerPort;
    private String ServerIPAddress;
    private int ipPort;

    private boolean close = true;
    public PrintWriter outputStream = null;

    // Notification stuff
    NotificationCompat.Builder builder;
    NotificationManager nm;

    private final IBinder binder = new SocketBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, new IntentFilter("onUpdateData"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.i(TAG, "Do nothing ");
        } else if (intent.getAction().equals(Constants.ACTION.SOCKET_START)) {
            if (!close) {
                Log.i(TAG, "Received Start Foreground Intent ");

                Intent notificationIntent = new Intent(this, MainActivity.class);
                notificationIntent.setAction(Constants.ACTION.SOCKET_MAIN);
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                        notificationIntent, 0);

                Intent stopIntent = new Intent(this, SocketClientHandler.class);
                stopIntent.setAction(Constants.ACTION.SOCKET_STOP);
                PendingIntent sstopIntent = PendingIntent.getService(this, 0,
                        stopIntent, 0);

                Notification notification = new NotificationCompat.Builder(this)
                        .setSmallIcon(android.R.drawable.btn_star)
                        .setContentTitle("Got ping")
                        .setContentText("Click to return")
                        .setTicker("Ticker Text")
                        .addAction(android.R.drawable.ic_media_next, "Stop", sstopIntent)
                        .setPriority(Notification.PRIORITY_MAX)
                        .setContentIntent(pendingIntent)
                        .setWhen(0)
                        .setOngoing(true).build();

                startForeground(Constants.NOTIFICATION_ID.SOCKET_SERVICE_FOREGROUND, notification);

                buildNotification();
                startThread();
            }
        } else if (intent.getAction().equals(Constants.ACTION.SOCKET_STOP)) {
            Log.i(TAG, "Received Stop Foreground Intent");
            stopThread();
            stopForeground(true);
            stopSelf();
            LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        }
        return Service.START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // Binder stuff to get the parent class (the actual service class)
    class SocketBinder extends Binder {
        SocketClientHandler getService() {
            return SocketClientHandler.this;
        }
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra("message")) {
                final String message = intent.getStringExtra("message");
                Thread thread = new Thread((new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG, "Thread Broadcast:" + message);
                    }
                }));
                thread.start();
            }
        }
    };

    public void sendOutBroadcast(String string) {
        Intent intent = new Intent("onMain");
        // You can also include some extra data.
        intent.putExtra("message", string);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    // Todo: Check when to update data
    public void updateValues() {
        settingsData = PreferenceManager.getDefaultSharedPreferences(this);

        strName = settingsData.getString("Name", "");
        ServerIPAddress = settingsData.getString("IpAddr", "");
        String strPort = settingsData.getString("IpPort", "");

        ServerPort = Integer.parseInt(strPort);
    }

    private void buildNotification() {
        // Setup a notification
        builder = new NotificationCompat.Builder(this);
        builder.setAutoCancel(true);

        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setWhen(System.currentTimeMillis());
        builder.setContentTitle("Got ping");
        builder.setContentText("Click to return");
        builder.setPriority(Notification.PRIORITY_LOW);
        builder.setWhen(1);

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);
        nm = (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);
    }

    public void gotMessage(String msg) {
        Log.i(TAG, "onMessage: " + msg);
        String messageTemp = msg;
        final String command = messageTemp.substring(0, messageTemp.indexOf(":"));
        msg = messageTemp.replace(command + ":", "");
        final String finalMsg = msg;
        if (Objects.equals(command, "answer")) {
            switch (finalMsg) {
                case "not yet allowed":
                    Log.i(TAG, "onMessage: " + "not yet allowed");
                    break;
                case "allowed":
                    Log.i(TAG, "onMessage: " + "allowed");
                    break;
                case "registered ... waiting for permission":
                    Log.i(TAG, "onMessage: " + "registered ... waiting for permission");
                    break;
                case "ping":
                    Log.i(TAG, "onMessage: " + "ping");
                    sendMessage("pong:pong");
                    nm.notify(Constants.NOTIFICATION_ID.SOCKET_SERVICE_TEMP, builder.build());
                    sendOutBroadcast("got ping ... sending pong");
                    break;
                case "door1 open":
                    Log.i(TAG, "onMessage: " + "door1 open");
                    break;
                case "door1 close":
                    Log.i(TAG, "onMessage: " + "door1 close");
                    break;
            }
        }
    }

    public void startThread() {
        close = true;
        client = new ClientThread();
        t = new Thread(client);
        t.start();
    }

    public void stopThread() {
        close = false;
        if (client != null) {
            client.cancelRead();
        }
    }

    // Sending name and a unique Phone identifier
    @SuppressLint("MissingPermission")
    public void sendMessage(String msg) {
        try {
            if (socket == null)
                throw new Exception("Couldn't send message to server. No connection?!");
            outputStream = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream())), true);
            final TelephonyManager tm = (TelephonyManager) getBaseContext().getSystemService(getApplicationContext().TELEPHONY_SERVICE);
            outputStream.println(msg + "-" + tm.getSimSerialNumber());
            outputStream.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void checkName() {
        sendMessage("register:" + strName);
    }

    public void onOpenGate() {
        if (!(atHome && myService.isPositionLock())) {
            atHome = true;
            myService.setPositionLock(true);
            nm.notify(uniqueID, notification.build());
            sSocketservice.sendMessage("output:Gate1 open auto");
            myService.startRepeatingTask();
        }
    }

    private class ClientThread implements Runnable {

        BufferedReader inputStream = null;
        String response = null;

        @Override
        public void run() {
            // Try to connect to server and setup stream reader
            try {
                Log.i(TAG, "start socket!");
                InetAddress serverAddr = InetAddress.getByName(ServerIPAddress);
                socket = new Socket(serverAddr, ServerPort);
                if (socket == null)
                    throw new Exception("Couldn't connect to server!");

                inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (Exception e) {
                e.printStackTrace();
            }

            while (close) {
                try {
                    response = inputStream.readLine();
                    gotMessage(response);
                } catch (Exception e) {
                    e.printStackTrace();
                    if (close)
                        startThread();
                    return;
                }
            }
        }

        void cancelRead() {
            try {
                if (!close) {
                    socket.close();
                    inputStream.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
