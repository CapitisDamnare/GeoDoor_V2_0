package tapsi.geodoor;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.icu.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Objects;

public class GPSService extends Service implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    String TAG = "tapsi_Service";

    // Google GPS API
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;
    FusedLocationProviderClient mFusedLocationClient;

    // File data stuff
    private SharedPreferences settingsData;
    private SharedPreferences.Editor fileEditor;

    boolean autoMode;
    boolean positionLock = false;
    Location homeLocation;
    float radius;

    // Servicebinder stuff
    private final IBinder binder = new MyLocalBinder();

    // Timer stuff
    private final static int INTERVAL = 1000;
    Handler mHandler = new Handler();
    private long timeFromStart = 0;
    private final long BLOCKTIME = 600000;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
        } else if (intent.getAction().equals(Constants.ACTION.GPS_START)) {
            updateValues();
            if (getAPIClient() == null) {
                buildGoogleApiClient();
            }
            else {
                if (autoMode) {
                    startGPS();
                }
            }
        } else if (intent.getAction().equals(Constants.ACTION.GPS_STOP)) {
            stopGPS();
            saveSharedFile();
            stopRepeatingTask();
        }
        return Service.START_NOT_STICKY;
    }

    public boolean isPositionLock() {
        return positionLock;
    }

    public void setPositionLock(boolean positionLock) {
        this.positionLock = positionLock;
        saveSharedFile();
        sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_OPENGATE, "true");
    }

    // Gps Google API
    public synchronized void buildGoogleApiClient() {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    public GoogleApiClient getAPIClient() {
        return mGoogleApiClient;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(300);
        mLocationRequest.setFastestInterval(300);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        updateValues();
        if (autoMode) {
            startGPS();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "onConnectionSuspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i(TAG, "onConnectionFailed");
    }

    @Override
    public void onLocationChanged(Location location) {
    }

    public void sendOutBroadcast(String event, String name, String value) {
        Intent intent = new Intent(event);
        intent.putExtra(name, value);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public void sendOutBroadcast(String event, String name, ArrayList<String> value) {
        Intent intent = new Intent(event);
        Bundle bundle = new Bundle();
        bundle.putStringArrayList(name, value);
        intent.putExtras(bundle);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public void startGPS() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
        }
    }

    LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            for (Location location : locationResult.getLocations()) {
                mLastLocation = location;
                float distance = mLastLocation.distanceTo(homeLocation);

                ArrayList<String> list = new ArrayList<String>();
                list.add(getStringValue(distance, 0));
                list.add(getStringValue(location.getSpeed(), 1));
                list.add(getStringValue(location.getAccuracy(), 0));

                sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_LOCATIONUPDATE, list);
                if (distance < radius) {
                    if (location.getAccuracy() <= 20.00) {
                        if (!positionLock) {
                            setPositionLock(true);
                            sendOutBroadcast(Constants.BROADCAST.EVENT_TOSOCKET, Constants.BROADCAST.NAME_OPENGATE, "true");
                            sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_OPENGATE, "true");
                            startRepeatingTask();
                        }
                    }
                }
                if (distance > radius) {
                    if (location.getAccuracy() <= 20.00) {
                        setPositionLock(false);
                        sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_TIMEUPDATE, "00:00:00");
                    }
                }
            }
        }
    };

    public void stopGPS() {
        //stop location updates
        if (mGoogleApiClient != null) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // Binder stuff to get the parent class (the actual service class)
    class MyLocalBinder extends Binder {
        GPSService getService() {
            return GPSService.this;
        }
    }

    // Definition of the Timer
    Runnable mHandlerTask = new Runnable() {
        @Override
        public void run() {
            mHandler.postDelayed(mHandlerTask, INTERVAL);
            String time = getCurrentTime();

            if (Objects.equals(time, "end")) {
                sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_TIMEUPDATE, "00:00:00");
                setPositionLock(false);
                mHandler.removeCallbacks(mHandlerTask);
            } else {
                sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_TIMEUPDATE, getCurrentTime());
            }

        }
    };

    // Block Timer
    public String getCurrentTime() {
        String timeString = "";
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            long currentTime = Calendar.getInstance().getTimeInMillis();
            long countDown = timeFromStart - currentTime;
            if (countDown <= 0)
                return "end";

            SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");
            timeString = df.format(countDown);
        }

        return timeString;
    }

    // start the Handler
    public void startRepeatingTask() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            timeFromStart = Calendar.getInstance().getTimeInMillis() + BLOCKTIME;
        }

        mHandlerTask.run();
    }

    // stop the Handler
    public void stopRepeatingTask() {
        mHandler.removeCallbacks(mHandlerTask);
    }

    public float getLastDistance() {
        return mLastLocation.distanceTo(homeLocation);
    }

    // Format the values given to the Activity
    private String getStringValue(float number, int mode) {
        // Mode 0: get formated value in m or km
        // Mode 1: get formated value in km/h
        if (mode == 1)
            number *= 3.6;

        boolean km = false;
        if (number > 999.99) {
            number = number / 1000;
            km = true;
        }

        DecimalFormat df = new DecimalFormat("#.##");
        df.setRoundingMode(RoundingMode.CEILING);

        String str_num = String.valueOf(df.format(number));

        switch (mode) {
            case 0:
                if (km)
                    str_num += " km";
                else
                    str_num += " m";
                break;
            case 1:
                str_num += " km/h";

        }
        return str_num;
    }

    // Update important values
    public void updateValues() {
        settingsData = PreferenceManager.getDefaultSharedPreferences(this);
        fileEditor = settingsData.edit();

        if (Objects.equals(settingsData.getString("Mode", ""), "Manual")) {
            autoMode = false;
        }
        else
            autoMode = true;

        if (Objects.equals(settingsData.getString("atHome", ""), "true")) {
            positionLock = true;
        }

        String strHomeLat = settingsData.getString("HomeLat", "");
        String strHomeLong = settingsData.getString("HomeLong", "");
        String strHomeAlt = settingsData.getString("HomeAlt", "");
        String strRadius = settingsData.getString("Radius", "");

        float fLatitude = Float.parseFloat(strHomeLat);
        float fLongitude = Float.parseFloat(strHomeLong);
        float fAltitude = Float.parseFloat(strHomeAlt);
        float fRadius = Float.parseFloat(strRadius);

        homeLocation = new Location("tapsi");
        homeLocation.setLatitude(fLatitude);
        homeLocation.setLongitude(fLongitude);
        homeLocation.setAltitude(fAltitude);
        radius = fRadius;
    }

    private void saveSharedFile() {
        fileEditor.putString("atHome", String.valueOf(positionLock));
        fileEditor.apply();
    }
}
