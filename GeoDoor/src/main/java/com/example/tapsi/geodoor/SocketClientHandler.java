package com.example.tapsi.geodoor;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
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

public class SocketClientHandler extends Service {

    String TAG = "tapsi_Socket";

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

    // Event Handling
    private SocketListener listener;

    // Wakelock! Keep the cpu alive!
    PowerManager.WakeLock wl;

    private final IBinder binder = new SocketBinder();

    interface SocketListener {
        void onMessage(String msg);

        void onConnected();

        void onDisconnected();

        void onError(Exception e);
    }

    public void setWl(PowerManager.WakeLock wl) {
        this.wl = wl;
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

    public void setCustomObjectListener(SocketListener listener) {
        this.listener = listener;
    }

    public void updateValues() {
        settingsData = PreferenceManager.getDefaultSharedPreferences(this);

        strName = settingsData.getString("Name", "");
        ServerIPAddress = settingsData.getString("IpAddr", "");
        String strPort = settingsData.getString("IpPort", "");

        ServerPort = Integer.parseInt(strPort);
    }

    public void startThread() {
        close = true;
        client = new ClientThread();
        t = new Thread(client);
        t.start();
        wl.acquire();
    }

    public void stopThread() {
        close = false;
        client.cancelRead();
        wl.release();
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
            listener.onError(e);
        }
    }

    public void checkName() {
        sendMessage("register:" + strName);
    }

    private class ClientThread implements Runnable{

        BufferedReader inputStream = null;
        String response = null;

        @Override
        public void run() {
            // Try to connect to server and setup stream reader
            try {
                InetAddress serverAddr = InetAddress.getByName(ServerIPAddress);
                socket = new Socket(serverAddr, ServerPort);
                if (socket == null)
                    throw new Exception("Couldn't connect to server!");

                inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (Exception e) {
                listener.onError(e);
                return;
            }
            listener.onConnected();

            while (close) {
                try {
                    response = inputStream.readLine();
                    listener.onMessage(response);
                } catch (Exception e) {
                    e.printStackTrace();
                    close = false;
                }
            }
            Log.i(TAG,"run ended");
            listener.onDisconnected();
        }

        void cancelRead() {
            try {
                if (!close) {
                    Log.i(TAG,"cancel read!");
                    socket.close();
                    inputStream.close();
                }
            } catch (Exception e) {
                listener.onError(e);
            }
        }
    }
}
