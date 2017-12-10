package tapsi.geodoor;

public class Constants {
    public interface ACTION {
        public static String SOCKET_MAIN = "com.tapsi.tapsi.geodoor.socket.main";
        public static String SOCKET_START = "com.tapsi.tapsi.geodoor.socket.start";
        public static String SOCKET_STOP = "com.tapsi.tapsi.geodoor.socket.stop";
        public static String GPS_MAIN = "com.tapsi.tapsi.geodoor.gps.main";
        public static String GPS_START = "com.tapsi.tapsi.geodoor.gps.start";
        public static String GPS_STOP = "com.tapsi.tapsi.geodoor.gps.stop";
    }

    public interface NOTIFICATION_ID {
        public static int SOCKET_SERVICE_FOREGROUND = 101;
        public static int SOCKET_SERVICE_TEMP = 102;
    }

    public interface BROADCAST {
        public static String EVENT_TOMAIN = "com.tapsi.tapsi.geodoor.toMain";
        public static String EVENT_TOSOCKET = "com.tapsi.tapsi.geodoor.toSocket";
        public static String EVENT_TOGPS = "com.tapsi.tapsi.geodoor.toGPS";

        public static String NAME_VALUEUPDATE = "com.tapsi.tapsi.geodoor.valueUpdate";
        public static String NAME_TIMEUPDATE = "com.tapsi.tapsi.geodoor.timeUpdate";
        public static String NAME_LOCATIONUPDATE = "com.tapsi.tapsi.geodoor.locationUpdate";
        public static String NAME_OPENGATE = "com.tapsi.tapsi.geodoor.openGate";
        public static String NAME_NOTYETALLOWED = "com.tapsi.tapsi.geodoor.notYetAllowed";
        public static String NAME_ALLOWED = "com.tapsi.tapsi.geodoor.allowed";
        public static String NAME_REGISTERED = "com.tapsi.tapsi.geodoor.registered";
        public static String NAME_PING = "com.tapsi.tapsi.geodoor.ping";
        public static String NAME_DOOR1OPEN = "com.tapsi.tapsi.geodoor.door1Open";
        public static String NAME_DOOR1CLOSE = "com.tapsi.tapsi.geodoor.door1Close";
        public static String NAME_SOCKETCONNECTED = "com.tapsi.tapsi.geodoor.socketConnected";
        public static String NAME_SOCKETDISONNECTED = "com.tapsi.tapsi.geodoor.docketDisconnected";
        public static String NAME_GPSCONNECTED = "com.tapsi.tapsi.geodoor.gpsConnected";
        public static String NAME_GPSDISCONNECTED = "com.tapsi.tapsi.geodoor.gpsDisconnected";
        public static String NAME_POSITIONLOCK = "com.tapsi.tapsi.geodoor.positionLock";
    }
}
