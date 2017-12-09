package com.example.tapsi.geodoor;

// Foreground Branch

import android.Manifest;
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
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.provider.SyncStateContract;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.NotificationCompat;
import android.text.SpannableString;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

//Todo: add Exception Handling and safe messages in a file

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    String TAG = "tapsi_Main";

    // Save Instance
    public boolean onPause;
    public boolean onSaveInstance;

    // Will be set to false if some settings are wrong
    public boolean wrongSettings = true;

    // Time and Lock stuff
    //public boolean atHome = false;
    //public boolean doorStatus = false;

    // Save data stuff
    private SharedPreferences settingsData;
    private SharedPreferences.Editor fileEditor;

    // Permission stuff
    public static final int MY_PERMISSIONS_REQUESTS = 99;

    // Timer for permissions
    private final static int INTERVAL = 1000;
    Handler mHandler = new Handler();

    // Service stuff
    MyService myService = null;
    SocketClientHandler sSocketservice;

    // Animations and Buttons and Mode boolean
    private Animation doorAnimation1;
    private Animation doorAnimation2;
    private Animation doorAnimation3;
    private Animation doorAnimation4;
    private Animation doorAnimation5;
    private Animation doorAnimation6;

    private Button btn_first;
    private Button btn_second;
    private Button btn_mode;

    private boolean autoMode = true;

    // Timer to reconnect to the server
    //private int socketInterval = 7000; // 5 seconds by default, can be changed later
    //private Handler socketTimer = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Handler to save and load data
        settingsData = PreferenceManager.getDefaultSharedPreferences(this);
        fileEditor = settingsData.edit();

        // Set OnTouch with Animation and OnClickListener
        setupButtons();

        // load saved Data and set the UI
        loadSharedFile();

        // Firsts start of the app should guid to the settings
        if ((Objects.equals(settingsData.getString("Correct", ""), ""))) {

            Intent set_intent = new Intent(this, SettingsActivity.class);
            set_intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(set_intent);
            // first time save
            saveSharedFile();
            fileEditor.putString("Service", "closed");
            fileEditor.apply();
            finish();
            return;
        }

        // Create Toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        // Create Drawer
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);

        Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.main_home_icon, getTheme());
        toggle.setHomeAsUpIndicator(drawable);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        // Style Drawer Menu
        setupNavigationMenu();

        // Style Statusbar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(getResources().getColor(R.color.colorDrawer));
        }

        Intent startIntent = new Intent(MainActivity.this, SocketClientHandler.class);
        startIntent.setAction(Constants.ACTION.SOCKET_START);
        startService(startIntent);
        bindService(startIntent, socketServiceConnection, Context.BIND_AUTO_CREATE);

        Intent startGPSIntent = new Intent(MainActivity.this, MyService.class);
        startGPSIntent.setAction(Constants.ACTION.GPS_START);
        startService(startGPSIntent);
        bindService(startGPSIntent, myServiceConnection, Context.BIND_AUTO_CREATE);

        // Thread to wait for starting permissin requests
        mHandlerTask.run();

        // Thread to check socket connection and trigger reconnect
        //socketTask.run();

        // Setup Custom Broadcast Receiver with intentFilter
        LocalBroadcastManager.getInstance(this).registerReceiver(socketReceiver,
                new IntentFilter(Constants.BROADCAST.EVENT_TOMAIN));
    }

    //GPS Service
    private ServiceConnection myServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MyService.MyLocalBinder binder = (MyService.MyLocalBinder) service;
            myService = binder.getService();
            //sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_GPSCONNECTED, "true");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "onServiceDisconnected! ");
            //sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_GPSDISCONNECTED, "true");
            myService = null;
        }
    };

    // get Broadcasts
    private BroadcastReceiver socketReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            if (intent.hasExtra(Constants.BROADCAST.NAME_VALUEUPDATE)) {
                myService.updateValues();
                sSocketservice.stopThread();
                sSocketservice.updateValues();
                sSocketservice.startThread();
            }
            if (intent.hasExtra(Constants.BROADCAST.NAME_TIMEUPDATE)) {
                String time = intent.getStringExtra(Constants.BROADCAST.NAME_TIMEUPDATE);
                onTimeUpdate(time);
            }
            if (intent.hasExtra(Constants.BROADCAST.NAME_LOCATIONUPDATE)) {
                Bundle bundle = intent.getExtras();
                ArrayList<String> list = new ArrayList<String>();
                list = bundle.getStringArrayList(Constants.BROADCAST.NAME_LOCATIONUPDATE);
                onLocationUpdate(list);
            }
            if (intent.hasExtra(Constants.BROADCAST.NAME_ALLOWED)) {
                Toast.makeText(getApplication(), "allowed", Toast.LENGTH_LONG).show();
                setTextColor(true);

            }
            if (intent.hasExtra(Constants.BROADCAST.NAME_NOTYETALLOWED)) {
                Toast.makeText(getApplication(), "not yet allowed", Toast.LENGTH_LONG).show();
                setTextColor(true);
            }
            if (intent.hasExtra(Constants.BROADCAST.NAME_REGISTERED)) {
                Toast.makeText(getApplication(), "registered ... waiting for permission", Toast.LENGTH_LONG).show();
                setTextColor(true);
            }
            if (intent.hasExtra(Constants.BROADCAST.NAME_DOOR1OPEN)) {
                doorAnimationOpen();
            }
            if (intent.hasExtra(Constants.BROADCAST.NAME_DOOR1CLOSE)) {
                doorAnimationClose();
            }
            if (intent.hasExtra(Constants.BROADCAST.NAME_SOCKETCONNECTED)) {
                Log.i(TAG, "Broadcast onSocketConnected\n");
            }
            if (intent.hasExtra(Constants.BROADCAST.NAME_SOCKETDISONNECTED)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setTextColor(false);
                    }
                });
                Log.i(TAG, "onDisconnected\n");
            }
            if (intent.hasExtra(Constants.BROADCAST.NAME_GPSCONNECTED)) {
                //myService = sSocketservice.getMyService();
                Log.i(TAG, "Broadcast onGPSConnected\n");
            }

        }
    };

    public void onTimeUpdate(String time) {
        // GPS Updates
        final TextView view1 = (TextView) findViewById(R.id.txtView_timelock_val);
        String lockText = "";
        if (myService.isPositionLock())
            lockText = "lock ";

        String text = lockText + time;

        int whiteColor = getResources().getColor(R.color.colorWhite);
        int redColor = getResources().getColor(R.color.colorRed);

        view1.setText(text);
        view1.setTextColor(redColor);
        if (Objects.equals(time, "00:00:00")) {
            if (Objects.equals(lockText, "")) {
                view1.setText("OFF");
                view1.setTextColor(whiteColor);
            } else {
                view1.setText("lock");
            }
        }
    }

    public void onLocationUpdate(List<String> list) {
        final TextView view1 = (TextView) findViewById(R.id.txtView_distance_val);
        view1.setText(list.get(0));
        final TextView view = (TextView) findViewById(R.id.txtView_speed_val);
        view.setText(list.get(1));
        final TextView view3 = (TextView) findViewById(R.id.txtView_accuracy_val);
        view3.setText(list.get(2));
    }

    // User Handling with closing and suspending the app
    @Override
    protected void onPostResume() {
        super.onPostResume();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        saveSharedFile();
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(socketReceiver);
        super.onDestroy();
    }

    // Permissions
    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) + ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {

            // Asking user if explanation is needed
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(this,
                            Manifest.permission.READ_PHONE_STATE)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

                //Prompt the user once explanation has been shown

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE},
                        MY_PERMISSIONS_REQUESTS);


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE},
                        MY_PERMISSIONS_REQUESTS);


            }
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUESTS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted. Do the
                    // contacts-related task you need to do.
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION) + ContextCompat.checkSelfPermission(this,
                            Manifest.permission.READ_PHONE_STATE)
                            == PackageManager.PERMISSION_GRANTED) {

                        // Todo: Check if needed
//                        if (myService.getAPIClient() == null) {
//                            myService.buildGoogleApiClient();
//                        }
                    }

                } else {

                    // Permission denied, Disable the functionality that depends on this permission.
                    Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
                    fileEditor.putString("Service", "closed");
                    fileEditor.apply();
                    finish();
                }
            }
            // other 'case' lines to check for other permissions this app might request.
            // You can add here other case statements according to your requirement.
        }
    }


    // Todo Check first permission handling!
    // Wait for successful binding of the service
    Runnable mHandlerTask = new Runnable() {

        @Override
        public void run() {
            mHandler.postDelayed(mHandlerTask, INTERVAL);
            //if (isBound && socketIsBound) {
            // start Handler for periodic up
            // for some reason it doesn't work in onServiceConnected
            // so we created a timer function which waits until onServiceConnected was called

            // Permissions
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                checkLocationPermission();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) +
                        ContextCompat.checkSelfPermission(getApplicationContext(),
                                Manifest.permission.READ_PHONE_STATE)
                        == PackageManager.PERMISSION_GRANTED) {
                    //myService.buildGoogleApiClient();

                    // Todo: If permission isn't granted you have to start the app twice for a socket connection
                    //sSocketservice.updateValues();
                    //sSocketservice.startThread();
                }
            } else {
                // For earlier API Versions
                //myService.buildGoogleApiClient();
                //sSocketservice.updateValues();
                //sSocketservice.startThread();
            }

            //After that we stop the timer
            mHandler.removeCallbacks(mHandlerTask);
            //}
        }
    };

    // Socket Service
    private ServiceConnection socketServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //Log.i(TAG, "onSocketServiceConnected!");
            SocketClientHandler.SocketBinder sBinder = (SocketClientHandler.SocketBinder) service;
            sSocketservice = sBinder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            //Log.i(TAG, "onSocketServiceDisconnected! ");
        }
    };

    public void setTextColor(final boolean val) {
        TextView view = (TextView) this.findViewById(R.id.txtView_status_val);
        int greenColor = getResources().getColor(R.color.colorGreen);
        int redColor = getResources().getColor(R.color.colorRed);

        if (val) {
            view.setText("Connected");
            view.setTextColor(greenColor);
        } else {
            view.setText("Disconnected");
            view.setTextColor(redColor);
        }
    }

    // Drawer Methods
    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_back) {
            // Handle the camera action
        } else if (id == R.id.nav_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra("onStart", false);
            startActivity(intent);
        } else if (id == R.id.nav_info) {


        } else if (id == R.id.nav_exit) {
            Intent stopIntent = new Intent(MainActivity.this, SocketClientHandler.class);
            stopIntent.setAction(Constants.ACTION.SOCKET_STOP);
            startService(stopIntent);

            Intent stopGPSIntent = new Intent(MainActivity.this, MyService.class);
            stopGPSIntent.setAction(Constants.ACTION.GPS_STOP);
            startService(stopGPSIntent);
            saveSharedFile();
            this.finish();
            //moveTaskToBack(true);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    // Style Drawer Menu
    private void setupNavigationMenu() {
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);

        // Style Navigation Text
        Menu menu = navigationView.getMenu();
        MenuItem itemHeader = menu.findItem(R.id.menu_bar);
        SpannableString s_itemHeader = new SpannableString(itemHeader.getTitle());
        s_itemHeader.setSpan(new TextAppearanceSpan(this, R.style.TextAppearanceMenuHeader), 0, s_itemHeader.length(), 0);
        itemHeader.setTitle(s_itemHeader);

        MenuItem itemBack = menu.findItem(R.id.nav_back);
        SpannableString s_itemBack = new SpannableString(itemBack.getTitle());
        s_itemBack.setSpan(new TextAppearanceSpan(this, R.style.TextAppearanceItemWhite), 0, s_itemBack.length(), 0);
        itemBack.setTitle(s_itemBack);

        MenuItem itemSettings = menu.findItem(R.id.nav_settings);
        SpannableString s_itemSettings = new SpannableString(itemSettings.getTitle());
        s_itemSettings.setSpan(new TextAppearanceSpan(this, R.style.TextAppearanceItemBlue), 0, s_itemSettings.length(), 0);
        itemSettings.setTitle(s_itemSettings);

        MenuItem itemInfo = menu.findItem(R.id.nav_info);
        SpannableString s_itemInfo = new SpannableString(itemInfo.getTitle());
        s_itemInfo.setSpan(new TextAppearanceSpan(this, R.style.TextAppearanceItemWhite), 0, s_itemInfo.length(), 0);
        itemInfo.setTitle(s_itemInfo);

        MenuItem itemExit = menu.findItem(R.id.nav_exit);
        SpannableString s_itemExit = new SpannableString(itemExit.getTitle());
        s_itemExit.setSpan(new TextAppearanceSpan(this, R.style.TextAppearanceItemBlue), 0, s_itemExit.length(), 0);
        itemExit.setTitle(s_itemExit);

        // Style Navigation Icons
        int whiteColor = getResources().getColor(R.color.colorWhite);
        int blueColor = getResources().getColor(R.color.colorBlue);
        itemBack.getIcon().setColorFilter(whiteColor, PorterDuff.Mode.SRC_IN);
        itemSettings.getIcon().setColorFilter(blueColor, PorterDuff.Mode.SRC_IN);
        itemInfo.getIcon().setColorFilter(whiteColor, PorterDuff.Mode.SRC_IN);
        itemExit.getIcon().setColorFilter(blueColor, PorterDuff.Mode.SRC_IN);

        navigationView.setNavigationItemSelectedListener(this);
    }

    // Toolbar Methods
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra("onStart", false);
            startActivity(intent);

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // Door Animation (Open and Close)
    private void doorAnimationOpen() {
        doorAnimation1 = AnimationUtils.loadAnimation(this, R.anim.anim_translate_door_open);
        final ImageView v1 = (ImageView) this.findViewById(R.id.main_door6);
        v1.startAnimation(doorAnimation1);

        doorAnimation2 = AnimationUtils.loadAnimation(this, R.anim.anim_translate_door_open);
        final ImageView v2 = (ImageView) this.findViewById(R.id.main_door5);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        v2.startAnimation(doorAnimation2);
                    }
                });
            }
        }).start();

        doorAnimation3 = AnimationUtils.loadAnimation(this, R.anim.anim_translate_door_open);
        final ImageView v3 = (ImageView) this.findViewById(R.id.main_door4);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        v3.startAnimation(doorAnimation3);
                    }
                });
            }
        }).start();

        doorAnimation4 = AnimationUtils.loadAnimation(this, R.anim.anim_translate_door_open);
        final ImageView v4 = (ImageView) this.findViewById(R.id.main_door3);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(800);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        v4.startAnimation(doorAnimation4);
                    }
                });
            }
        }).start();

        doorAnimation5 = AnimationUtils.loadAnimation(this, R.anim.anim_translate_door_open);
        final ImageView v5 = (ImageView) this.findViewById(R.id.main_door2);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        v5.startAnimation(doorAnimation5);
                    }
                });
            }
        }).start();

        doorAnimation6 = AnimationUtils.loadAnimation(this, R.anim.anim_translate_door_open);
        final ImageView v6 = (ImageView) this.findViewById(R.id.main_door1);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        v6.startAnimation(doorAnimation6);
                    }
                });
            }
        }).start();
    }

    private void doorAnimationClose() {
        doorAnimation1 = AnimationUtils.loadAnimation(this, R.anim.anim_translate_door_close);
        final ImageView v1 = (ImageView) this.findViewById(R.id.main_door1);
        v1.startAnimation(doorAnimation1);

        doorAnimation2 = AnimationUtils.loadAnimation(this, R.anim.anim_translate_door_close);
        final ImageView v2 = (ImageView) this.findViewById(R.id.main_door2);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        v2.startAnimation(doorAnimation2);
                    }
                });
            }
        }).start();

        doorAnimation3 = AnimationUtils.loadAnimation(this, R.anim.anim_translate_door_close);
        final ImageView v3 = (ImageView) this.findViewById(R.id.main_door3);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        v3.startAnimation(doorAnimation3);
                    }
                });
            }
        }).start();

        doorAnimation4 = AnimationUtils.loadAnimation(this, R.anim.anim_translate_door_close);
        final ImageView v4 = (ImageView) this.findViewById(R.id.main_door4);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(800);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        v4.startAnimation(doorAnimation4);
                    }
                });
            }
        }).start();

        doorAnimation5 = AnimationUtils.loadAnimation(this, R.anim.anim_translate_door_close);
        final ImageView v5 = (ImageView) this.findViewById(R.id.main_door5);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        v5.startAnimation(doorAnimation5);
                    }
                });
            }
        }).start();

        doorAnimation6 = AnimationUtils.loadAnimation(this, R.anim.anim_translate_door_close);
        final ImageView v6 = (ImageView) this.findViewById(R.id.main_door6);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        v6.startAnimation(doorAnimation6);
                    }
                });
            }
        }).start();
    }

    // Setup Buttons
    private void setupButtons() {
        btn_first = (Button) this.findViewById(R.id.main_button1);
        btn_second = (Button) this.findViewById(R.id.main_button2);
        btn_mode = (Button) this.findViewById(R.id.main_btn_mode);

        btn_first.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN)
                    v.setBackground(getResources().getDrawable(R.drawable.main_btn_pushed));
                else if (event.getAction() == MotionEvent.ACTION_UP)
                    v.setBackground(getResources().getDrawable(R.drawable.main_btn2_bckgr));
                return false;
            }
        });

        btn_first.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickButton1();
            }
        });

        btn_second.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN)
                    v.setBackground(getResources().getDrawable(R.drawable.main_btn_pushed));
                else if (event.getAction() == MotionEvent.ACTION_UP)
                    v.setBackground(getResources().getDrawable(R.drawable.main_btn2_bckgr));
                return false;
            }
        });

        btn_second.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickButton2();
            }
        });

        btn_mode.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN)
                    v.setBackground(getResources().getDrawable(R.drawable.main_btn_pushed));
                else if (event.getAction() == MotionEvent.ACTION_UP)
                    btn_mode.setBackgroundColor(Color.TRANSPARENT);
                return false;
            }
        });

        btn_mode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickBtnMode();
            }
        });
    }

    // Open Gate
    private void onClickButton1() {
        if (Objects.equals(String.valueOf(btn_mode.getText()), "Automatic")) {
            sSocketservice.sendMessage("output:Gate1 open auto");
            myService.startRepeatingTask();
        } else
            sSocketservice.sendMessage("output:Gate1 open");
    }

    // Open door
    private void onClickButton2() {
        sSocketservice.sendMessage("output:Door1 open");
    }

    // Setting the mode and change Button Text
    private void onClickBtnMode() {
        if (autoMode) {
            autoMode = false;
            btn_mode.setText("Manual");
//            if (!isBound) {
//                Toast.makeText(this, "GPS not ready yet! - ", Toast.LENGTH_LONG).show();
//                return;
//            }
            myService.stopGPS();
        } else {

            // Workaround for to fast clicking!
//            if (!isBound) {
//                Toast.makeText(this, "GPS not ready yet! - ", Toast.LENGTH_LONG).show();
//                return;
//            }

            autoMode = true;
            btn_mode.setText("Automatic");
            myService.startGPS();
        }
    }

    public void loadSharedFile() {
        if (Objects.equals(settingsData.getString("Mode", ""), "Manual")) {
            btn_mode.setText(settingsData.getString("Mode", ""));
            autoMode = false;
        }

        if (Objects.equals(settingsData.getString("atHome", ""), "true")) {
            //atHome = true;
            //Log.i(TAG, "atHome: " + String.valueOf(atHome));

            final TextView view1 = (TextView) findViewById(R.id.txtView_timelock_val);
            String lockText = "lock ";
            int redColor = getResources().getColor(R.color.colorRed);
            view1.setText(lockText);
            view1.setTextColor(redColor);
        }

        if (!autoMode) {
            btn_mode.setText("Manual");
        }

        if (Objects.equals(settingsData.getString("doorStatus", ""), "true")) {
            //doorStatus = true;
        }

    }

    private void saveSharedFile() {
        fileEditor.putString("Mode", btn_mode.getText().toString());
        fileEditor.putString("Service", "closed");
        //fileEditor.putString("atHome", String.valueOf(atHome));
        //fileEditor.putString("doorStatus", String.valueOf(doorStatus));
        fileEditor.apply();
    }
}