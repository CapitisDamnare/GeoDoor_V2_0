package com.example.tapsi.geodoor;

public class Constants {
    public interface ACTION {
        public static String SOCKET_MAIN = "com.tapsi.geodoor.socket.main";
        public static String SOCKET_START = "com.tapsi.geodoor.socket.start";
        public static String SOCKET_STOP = "com.tapsi.geodoor.socket.stop";
        public static String GPS_MAIN = "com.tapsi.geodoor.gps.main";
        public static String GPS_START = "com.tapsi.geodoor.gps.start";
        public static String GPS_STOP = "com.tapsi.geodoor.gps.stop";
    }

    public interface NOTIFICATION_ID {
        public static int SOCKET_SERVICE_FOREGROUND = 101;
        public static int SOCKET_SERVICE_TEMP = 102;
        public static int GPS_SERVICE = 111;

    }

    public interface BROADCAST {
        public static String EVENT_TOMAIN = "com.tapsi.geodoor.toMain";
        public static String NAME_VALUEUPDATE = "com.tapsi.geodoor.valueUpdate";
        public static String NAME_TIMEUPDATE = "com.tapsi.geodoor.timeUpdate";
        public static String NAME_LOCATIONUPDATE = "com.tapsi.geodoor.timeUpdate";

        public static String EVENT_TOSOCKET = "com.tapsi.geodoor.toSocket";
        public static String EVENT_TOGPS = "com.tapsi.geodoor.toGPS";
    }
}
