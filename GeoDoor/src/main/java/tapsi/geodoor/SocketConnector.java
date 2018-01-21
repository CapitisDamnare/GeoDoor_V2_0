package tapsi.geodoor;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Objects;

public final class SocketConnector {

    private static int serverPort;
    private static String serverIPAddress;
    private static Context context;

    private static Socket socket = null;
    private static BufferedReader inputStream = null;
    private static PrintWriter outputStream = null;

    public SocketConnector(int serverPort, String serverIPAddress, Context context) {
        SocketConnector.serverPort = serverPort;
        SocketConnector.serverIPAddress = serverIPAddress;
        SocketConnector.context = context;
    }

    public static boolean initConnection() {
        try {
            InetAddress serverAddr = InetAddress.getByName(serverIPAddress);
            socket = new Socket();
            socket.connect(new InetSocketAddress(serverIPAddress,serverPort),1000);

        } catch (Exception e) {
            e.printStackTrace();
            sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_GPSDISCONNECTED, "true");
            return false;
        }
        return true;
    }

    public static String sendMessage(String msg, String serialNumber) {
        String response = "";

        if (!initConnection())
            return null;

        try {
            inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            outputStream = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream())), true);
            outputStream.println(msg + "-" + serialNumber);
            outputStream.flush();
        } catch (Exception e) {
            sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_GPSDISCONNECTED, "true");
            e.printStackTrace();
            return null;
        }

        try {
            for (int i = 0; i < 500; i += 1) {
                response += inputStream.readLine();
                if (!Objects.equals(response, ""))
                    break;
                Thread.sleep(10);
            }
        } catch (Exception e) {
            sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_GPSDISCONNECTED, "true");
            e.printStackTrace();
            return null;
        } finally {
            if (socket != null)
                try {
                    socket.close();
                } catch (IOException e) {
                    sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_GPSDISCONNECTED, "true");
                    e.printStackTrace();
                }
        }
        return response;
    }

    private static void sendOutBroadcast(String event, String name, String value) {
        Intent intent = new Intent(event);
        intent.putExtra(name, value);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}
