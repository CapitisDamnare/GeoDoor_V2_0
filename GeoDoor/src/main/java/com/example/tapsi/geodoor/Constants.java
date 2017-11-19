package com.example.tapsi.geodoor;

public class Constants {
    public interface ACTION {
        public static String SOCKET_MAIN = "com.example.tapsi.testsleep.socket.main";
        public static String SOCKET_START = "com.example.tapsi.testsleep.socket.start";
        public static String SOCKET_STOP = "com.example.tapsi.testsleep.socket.stop";
        public static String GPS_MAIN = "com.example.tapsi.testsleep.gps.main";
        public static String GPS_START = "com.example.tapsi.testsleep.gps.start";
        public static String GPS_STOP = "com.example.tapsi.testsleep.gps.stop";
    }

    public interface NOTIFICATION_ID {
        public static int SOCKET_SERVICE_FOREGROUND = 101;
        public static int SOCKET_SERVICE_TEMP = 102;
        public static int GPS_SERVICE = 111;

    }
}
