package com.example.tapsi.geodoor;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
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

// Todo: Implement Name Handshake
// Todo: Implement Basic Alive Check and IP Check

public class SocketClientHandler extends Service {

    String TAG = "tapsi_Socket";

    private Socket socket;
    private ClientThread client;

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

    private final IBinder binder = new SocketBinder();

    interface SocketListener {
        void onMessage(String msg);

        void onConnected();

        void onDisconnected();

        void onError(Exception e);

        void onCheckName(boolean val);
    }

    // Constructor
    public SocketClientHandler() {
        this.listener = null;
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
        Thread t = new Thread(client);
        t.start();
    }

    public void stopThread() {
        close = false;
        client.cancelRead();
    }

    public void sendMessage(String msg) {
        try {
            if (socket == null)
                throw new Exception("Couldn't send message to server. No connection?!");
            outputStream = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream())), true);
            outputStream.println(msg);
            outputStream.flush();
        } catch (Exception e) {
            listener.onError(e);
        }
    }

    // Sending name and a unique Phone identifier
    public void checkName() {
        final TelephonyManager tm = (TelephonyManager) getBaseContext().getSystemService(getApplicationContext().TELEPHONY_SERVICE);
        sendMessage("register:" + strName + "-" + tm.getSimSerialNumber());
    }

    private class ClientThread implements Runnable {

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
                    Thread.sleep(200);

                    response = inputStream.readLine();

                    String tempStr = response;
                    if (tempStr.contains("cmnd-name:")) {
                        if (tempStr.compareTo("cmnd-name:true") == 0)
                            listener.onCheckName(true);
                        else if (tempStr.compareTo("cmnd-name:true") == 0)
                            listener.onCheckName(false);
                    }

                    listener.onMessage(response);
                } catch (Exception e) {
                    e.printStackTrace();
                    close = true;
                    listener.onDisconnected();
                    return;
                }
            }

            listener.onDisconnected();
        }

        void cancelRead() {
            try {
                if (!close) {
                    socket.close();
                    inputStream.close();

                }
            } catch (Exception e) {
                listener.onError(e);
            }
        }
    }
}
