package com.example.tapsi.geodoor;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SettingsActivity extends AppCompatActivity {

    String TAG = "tapsi_Settings";

    private EditText editText1;
    private EditText editText2;
    private EditText editText3;
    private EditText editText4;
    private EditText editText5;

    private TextView editNum1;
    private TextView editNum2;
    private TextView editNum3;
    private TextView editNum4;
    private TextView editNum5;

    private TextView viewError;

    private String strName;
    private String strIpandPort;
    private String strIpAdd;
    private String strPort;

    private String strHomeLocation;
    private String strHomeLat;
    private String strHomeLong;
    private String strHomeAlt;
    private String strRadius;

    private SharedPreferences settingsData;
    private SharedPreferences.Editor fileEditor;

    // Is settings called via a first startup or via the menu
    boolean onStart = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // hasExtra Intent example for later use
        Bundle data = getIntent().getExtras();
        if (data != null)
            onStart = data.getBoolean("onStart");

        // Todo: Create a Map Activity to save a new HomePosition

        editText1 = (EditText) findViewById(R.id.set_edit1);
        editText2 = (EditText) findViewById(R.id.set_edit2);
        editText3 = (EditText) findViewById(R.id.set_edit3);
        editText4 = (EditText) findViewById(R.id.set_edit4);
        editText5 = (EditText) findViewById(R.id.set_edit5);

        editNum1 = (TextView) findViewById(R.id.set_edit_num1);
        editNum2 = (TextView) findViewById(R.id.set_edit_num2);
        editNum3 = (TextView) findViewById(R.id.set_edit_num3);
        editNum4 = (TextView) findViewById(R.id.set_edit_num4);
        editNum5 = (TextView) findViewById(R.id.set_edit_num5);

        viewError = (TextView) findViewById(R.id.set_view_error);

        settingsData = PreferenceManager.getDefaultSharedPreferences(this);
        fileEditor = settingsData.edit();

        if (!Objects.equals(settingsData.getString(editNum1.getText().toString(), ""), ""))
            readData();
    }

    public void sendOutBroadcast(String event, String string) {
        Intent intent = new Intent(event);
        intent.putExtra("valueUpdate", string);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public void onClick_home(View view) {

        // Hide Virtual Keayboard when Home is pressed
        InputMethodManager inputManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);

        inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
                InputMethodManager.HIDE_NOT_ALWAYS);

        // If values are incorrect abort
        if (!checkValues())
            return;

        saveData();

        if (onStart) {
            fileEditor.putString("Correct", "true");
            fileEditor.apply();

            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        }
        else {
            sendOutBroadcast("toMain","true");
            finish();
        }
    }

    public void saveData() {
        fileEditor.putString("Name", strName);
        fileEditor.putString("IpAddr", strIpAdd);
        fileEditor.putString("IpPort", strPort);
        fileEditor.putString("HomeLat", strHomeLat);
        fileEditor.putString("HomeLong", strHomeLong);
        fileEditor.putString("HomeAlt", strHomeAlt);
        fileEditor.putString("Radius", strRadius);
        fileEditor.putString("Debug", editNum5.getText().toString());
        fileEditor.apply();
    }

    public void readData() {
        editText1.setText(settingsData.getString("Name", ""));

        String ipAddrTemp = settingsData.getString("IpAddr", "");
        String ipPortTemp = settingsData.getString("IpPort", "");
        editText2.setText(ipAddrTemp + ":" + ipPortTemp);

        String homeLatTemp = settingsData.getString("HomeLat", "");
        String homeLongTemp = settingsData.getString("HomeLong", "");
        String homeAltTemp = settingsData.getString("HomeAlt", "");
        String homLocationTemp = "lat: " + homeLatTemp + "\nlong: " + homeLongTemp + "\nalt: " + homeAltTemp;
        editText3.setText(homLocationTemp);

        editText4.setText(settingsData.getString("Radius", "") + " m");
        editText5.setText(settingsData.getString("Debug", ""));
    }

    // Check all values
    private boolean checkValues() {
        viewError.setText("");

        boolean bcheckName = false;
        boolean bcheckIpAddress = false;
        boolean bcheckHomeLocation = false;
        boolean bcheckRadius = false;

        if (checkName())
            bcheckName = true;

        if (checkIpAddress())
            bcheckIpAddress = true;

        if (checkHomeLocation())
            bcheckHomeLocation = true;

        if (checkRadius())
            bcheckRadius = true;

        return bcheckName && bcheckIpAddress && bcheckHomeLocation && bcheckRadius;
    }

    private boolean checkName() {
        strName = editText1.getText().toString();

        if (strName.length() > 20) {
            viewError.append("Name to long!\n");
            return false;
        }
        return true;
    }

    private boolean checkIpAddress() {
        strIpandPort = editText2.getText().toString();

        try {
            strIpAdd = strIpandPort.substring(0, strIpandPort.indexOf(":"));
            String strIpandPortTemp = strIpandPort.replace(strIpAdd + ":", "");
            if (Objects.equals(strIpandPort, strIpandPortTemp))
                throw new Exception(": Missing colon?\n");
            strPort = strIpandPortTemp;

        } catch (Exception e) {
            viewError.append(e.getMessage() + "\nExample format:\n 11.22.333.44:1234\n");
            return false;
        }
        Pattern p = Pattern.compile("\\b((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(\\.|$)){4}\\b");
        Matcher m = p.matcher(strIpAdd);
        if (m.find()) {
            return true;
        } else {
            viewError.append("IP Address is wrong!");
            return false;
        }
    }

    private boolean checkHomeLocation() {

        try {
            strHomeLocation = editText3.getText().toString();
            strHomeLat = strHomeLocation.substring(0, strHomeLocation.indexOf("\n"));

            String strHomeLocationTemp = strHomeLocation.replace(strHomeLat + "\n", "");
            if (Objects.equals(strHomeLocation, strHomeLocationTemp))
                throw new Exception("Missing Enter?\n");
            strHomeLocation = strHomeLocationTemp;
            strHomeLong = strHomeLocation.substring(0, strHomeLocation.indexOf("\n"));

            strHomeLocationTemp = strHomeLocation.replace(strHomeLong + "\n", "");
            if (Objects.equals(strHomeLocation, strHomeLocationTemp))
                throw new Exception("Missing Enter?\n");
            strHomeAlt = strHomeLocationTemp;

            String strHomeLatTemp = strHomeLat.replace("lat: ", "");
            if (Objects.equals(strHomeLat, strHomeLatTemp))
                throw new Exception("Missing a space?\n");
            strHomeLat = strHomeLatTemp;

            String strHomeLongTemp = strHomeLong.replace("long: ", "");
            if (Objects.equals(strHomeLong, strHomeLongTemp))
                throw new Exception("Missing a space?\n");
            strHomeLong = strHomeLongTemp;

            String strHomeAltTemp = strHomeAlt.replace("alt: ", "");
            if (Objects.equals(strHomeAlt, strHomeAltTemp))
                throw new Exception("Missing a space?\n");
            strHomeAlt = strHomeAltTemp;

            float fLatitude = Float.parseFloat(strHomeLat);
            float fLongitude = Float.parseFloat(strHomeLong);
            float fAltitude = Float.parseFloat(strHomeAlt);

        } catch (Exception e) {
            viewError.append(e.getMessage() + "\nExample format:\nlat: 44.044444444\nlong: 11.01111111\nalt: 123");
            return false;
        }

        Pattern p = Pattern.compile("^[-+]?([1-8]?\\d(\\.\\d+)?|90(\\.0+)?),\\s*[-+]?(180(\\.0+)?|((1[0-7]\\d)|([1-9]?\\d))(\\.\\d+)?)$");
        Matcher m = p.matcher(strHomeLat + ", " + strHomeLong);
        if (m.find())
            return true;
        else {
            viewError.append("Coordinates are wrong!");
            return false;
        }
    }

    private boolean checkRadius() {

        strRadius = editText4.getText().toString();

        if (strRadius.contains("m")) {
            strRadius = strRadius.replace("m", "");
            strRadius = strRadius.replace(" ", "");

            Pattern p = Pattern.compile("[0-9]+");
            Matcher m = p.matcher(strRadius);

            try {
                float fRadius = Float.parseFloat(strRadius);
            } catch (Exception e) {
                viewError.append(e.getMessage() + "\nExample format:\n200 m");
                return false;
            }

            if (m.find())
                return true;
            else {
                viewError.append("Wrong number!\n");
                return false;
            }
        } else if (strRadius.contains("km")) {
            strRadius = strRadius.replace("m", "");
            strRadius = strRadius.replace(" ", "");
            strRadius += "000";

            Pattern p = Pattern.compile("[0-9]+");
            Matcher m = p.matcher(strRadius);

            try {
                float  fRadius = Float.parseFloat(strRadius);
            } catch (Exception e) {
                viewError.append(e.getMessage() + "\nExample format:\n200 m");
                return false;
            }

            if (m.find())
                return true;
            else {
                viewError.append("Wrong number!\n");
                return false;
            }
        } else {
            viewError.append("Missing unit!(m or km)\n");
            return false;
        }
    }
}
