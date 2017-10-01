package com.example.tapsi.geodoor;

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
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class MyService extends Service implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    String TAG = "tapsi_Service";

    // Google GPS API
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;

    // File data stuff
    private SharedPreferences settingsData;

    boolean autoMode = true;
    boolean positionLock = false;
    Location homeLocation;
    float radius;

    // Servicebinder stuff
    private final IBinder binder = new MyLocalBinder();

    // Timer stuff
    private final static int INTERVAL = 1000;
    Handler mHandler = new Handler();
    private long timeFromStart = 0;
    private final long BLOCKTIME = 6000;

    // Event Handling
    private ServiceListener listener;

    // Interface declaring the Event
    interface ServiceListener {
        void onTimeUpdate(String time);

        void onLocationUpdate(List<String> data);

        void onOpenGate();
    }

    // Constructor
    public MyService() {
        this.listener = null;
    }

    public boolean isPositionLock() {
        return positionLock;
    }

    public void setPositionLock(boolean positionLock) {
        this.positionLock = positionLock;
    }

    // Gps Google API
    public synchronized void buildGoogleApiClient() {
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

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;
        float distance = mLastLocation.distanceTo(homeLocation);

        List<String> list = new ArrayList<String>();
        list.add(getStringValue(distance, 0));
        list.add(getStringValue(location.getSpeed(), 1));
        list.add(getStringValue(location.getAccuracy(), 0));

        listener.onLocationUpdate(list);
        if (distance < radius) {
            if (location.getAccuracy() <= 20.00) {
                if (!positionLock) {
                    positionLock = true;
                    listener.onOpenGate();
                }
            }
        }
        if (distance > radius) {
            if (location.getAccuracy() <= 20.00) {
                positionLock = false;
                listener.onTimeUpdate("00:00:00");
            }
        }
    }

    public void startGPS() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, (com.google.android.gms.location.LocationListener) this);
        }
    }

    public void stopGPS() {
        //stop location updates
        if (mGoogleApiClient != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, (com.google.android.gms.location.LocationListener) this);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // Binder stuff to get the parent class (the actual service class)
    class MyLocalBinder extends Binder {
        MyService getService() {
            return MyService.this;
        }
    }

    // Definition of the Timer
    Runnable mHandlerTask = new Runnable() {
        @Override
        public void run() {
            //updateTime();
            mHandler.postDelayed(mHandlerTask, INTERVAL);
            String time = getCurrentTime();

            if (Objects.equals(time, "end")) {
                listener.onTimeUpdate("00:00:00");
                mHandler.removeCallbacks(mHandlerTask);
            } else
                listener.onTimeUpdate(getCurrentTime());
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

            SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss", Locale.GERMANY);
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

    // Needed for the class to call a the listener for the events
    public void setCustomObjectListener(ServiceListener listener) {
        this.listener = listener;
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

        String strMode = settingsData.getString("Mode", "");
        String strHomeLat = settingsData.getString("HomeLat", "");
        String strHomeLong = settingsData.getString("HomeLong", "");
        String strHomeAlt = settingsData.getString("HomeAlt", "");
        String strRadius = settingsData.getString("Radius", "");

        if (Objects.equals(strMode, "Manual"))
            autoMode = false;

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

    public void killMe() {
        android.os.Process.killProcess(android.os.Process.myPid());
    }

}
