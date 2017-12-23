package tapsi.geodoor;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.Objects;

import org.acra.ACRA;


public class SocketService extends Service {

    String TAG = "tapsi_Socket";

    // Thread for periodic Gate checks
    private ClientThread client;
    Thread t = null;
    private boolean gateStatusTimer = true;

    // Socket Connection values
    private SocketConnector sConnector = null;
    private int serverPort;
    private String serverIPAddress;

    // File data stuff
    private SharedPreferences settingsData;

    private String strName;

    private boolean close = true;

    // Notification stuff
    NotificationCompat.Builder builder;
    NotificationManager nm;

    GPSService GPSService;
    boolean isGPSServiceBound = false;

    private final IBinder binder = new SocketBinder();

    public GPSService getGPSService() {
        return GPSService;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, new IntentFilter(Constants.BROADCAST.EVENT_TOSOCKET));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        updateValues();
        if (intent.getAction().equals(Constants.ACTION.SOCKET_START)) {
            checkName();
            if (close) {
                close = false;

                Intent startGPSIntent = new Intent(SocketService.this, GPSService.class);
                startGPSIntent.setAction(Constants.ACTION.GPS_START);
                startService(startGPSIntent);
                bindService(startGPSIntent, myServiceConnection, Context.BIND_AUTO_CREATE);

                Intent notificationIntent = new Intent(this, MainActivity.class);
                notificationIntent.setAction(Constants.ACTION.SOCKET_MAIN);
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                        notificationIntent, 0);

                Intent stopIntent = new Intent(this, SocketService.class);
                stopIntent.setAction(Constants.ACTION.SOCKET_STOP);
                PendingIntent sstopIntent = PendingIntent.getService(this, 0,
                        stopIntent, 0);

                Notification notification = new NotificationCompat.Builder(this)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("GeoDoor GPS & Socket Service activated")
                        .setContentText("Click to launch App")
                        .setTicker("GeoDoor")
                        .addAction(android.R.drawable.ic_media_next, "Stop Service", sstopIntent)
                        .setPriority(Notification.PRIORITY_MAX)
                        .setContentIntent(pendingIntent)
                        .setWhen(0)
                        .setOngoing(true).build();

                startForeground(Constants.NOTIFICATION_ID.SOCKET_SERVICE_FOREGROUND, notification);

                buildNotification();
                startGateStatusTimer();
            }
        } else if (intent.getAction().equals(Constants.ACTION.SOCKET_STOP)) {
            stopGateStatusTimer();
            if (!close) {
                close = true;
                stopForegroundService();
            }
        }
        return Service.START_NOT_STICKY;
    }

    public void stopForegroundService() {
        sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_SOCKETDISONNECTED, "true");

        Intent stopGPSIntent = new Intent(SocketService.this, GPSService.class);
        stopGPSIntent.setAction(Constants.ACTION.GPS_STOP);
        startService(stopGPSIntent);
        if (isGPSServiceBound)
            unbindService(myServiceConnection);
        else
            Log.i(TAG, "GPSService wasn't bound");

        stopForeground(true);
        stopSelf();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // Binder stuff to get the parent class (the actual service class)
    class SocketBinder extends Binder {
        SocketService getService() {
            return SocketService.this;
        }
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra(Constants.BROADCAST.NAME_OPENGATE)) {
                onOpenGate();
            }
        }
    };

    //GPS Service
    private ServiceConnection myServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            GPSService.MyLocalBinder binder = (GPSService.MyLocalBinder) service;
            GPSService = binder.getService();
            isGPSServiceBound = true;
            sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_GPSCONNECTED, "true");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_GPSDISCONNECTED, "true");
            isGPSServiceBound = false;
            GPSService = null;
        }
    };

    public void sendOutBroadcast(String event, String name, String value) {
        Intent intent = new Intent(event);
        intent.putExtra(name, value);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public void updateValues() {
        settingsData = PreferenceManager.getDefaultSharedPreferences(this);

        strName = settingsData.getString("Name", "");
        serverIPAddress = settingsData.getString("IpAddr", "");
        String strPort = settingsData.getString("IpPort", "");

        serverPort = Integer.parseInt(strPort);
    }

    private void buildNotification() {
        // Setup a notification
        builder = new NotificationCompat.Builder(this);
        builder.setAutoCancel(true);

        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setWhen(System.currentTimeMillis());
        builder.setContentTitle("Opened door automatically");
        builder.setContentText("Click to launch App");
        builder.setPriority(Notification.PRIORITY_LOW);
        builder.setWhen(1);

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);
        nm = (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);
    }

    public void gotMessage(String msg) {
        if (Objects.equals(msg, "quit"))
            return;
        String messageTemp = msg;
        final String command = messageTemp.substring(0, messageTemp.indexOf(":"));
        msg = messageTemp.replace(command + ":", "");
        final String finalMsg = msg;
        if (Objects.equals(command, "answer")) {
            switch (finalMsg) {
                case "not yet allowed":
                    Log.i(TAG, "onMessage: " + "not yet allowed");
                    sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_NOTYETALLOWED, "true");
                    break;
                case "allowed":
                    Log.i(TAG, "onMessage: " + "allowed");
                    sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_ALLOWED, "true");
                    break;
                case "registered ... waiting for permission":
                    Log.i(TAG, "onMessage: " + "registered ... waiting for permission");
                    sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_REGISTERED, "true");
                    break;
                case "door1 open":
                    Log.i(TAG, "onMessage: " + "door1 open");
                    sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_DOOR1OPEN, "true");
                    break;
                case "door1 closed":
                    Log.i(TAG, "onMessage: " + "door1 closed");
                    sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_DOOR1CLOSE, "true");
                    break;
            }
        }
    }

    @SuppressLint("MissingPermission")
    public void sendMessage(String msg) {
        final String message = msg;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sConnector = new SocketConnector(serverPort, serverIPAddress);
                    final TelephonyManager tm = (TelephonyManager) getBaseContext().getSystemService(getApplicationContext().TELEPHONY_SERVICE);
                    String answer = sConnector.sendMessage(message, tm.getSimSerialNumber());
                    if (answer != null) {
                        gotMessage(answer);
                        sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_SOCKETCONNECTED, "true");
                    } else {
                        sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_SOCKETDISONNECTED, "true");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void checkName() {
        sendMessage("register:" + strName);
    }

    public void onOpenGate() {
        nm.notify(Constants.NOTIFICATION_ID.SOCKET_SERVICE_TEMP, builder.build());
        sendMessage("output:Gate1 open auto");
    }

    public void startGateStatusTimer() {
        gateStatusTimer = true;
        client = new ClientThread();
        t = new Thread(client);
        t.start();
    }

    public void stopGateStatusTimer() {
        gateStatusTimer = false;
    }

    private class ClientThread implements Runnable {

        @Override
        public void run() {
            while (gateStatusTimer) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                sendMessage("output:Gate1 status");
            }
        }
    }
}
