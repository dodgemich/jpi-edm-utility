package com.zerotreedelta.jpi.upload;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.telephony.IccOpenLogicalChannelResponse;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private enum UsbPermission { Unknown, Requested, Granted, Denied }
    // private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private static final String INTENT_ACTION_GRANT_USB = "com.zerotreedelta.jpi.upload.GRANT_USB";


    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private SerialInputOutputManager mSerialIoManager;

    private File outputFile;


    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onRunError(final Exception e) {
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.this.updateConsoleStatus("onRunError");
                            MainActivity.this.updateConsoleStatus(e.getMessage());
                        }
                    });
                }

                @Override
                public void onStatusMessage(final String data) {
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.this.updateConsoleStatus(data);
                        }
                    });
                }

                @Override
                public void onCompletion(){
                    TextView consoleView = (TextView) findViewById(R.id.consoleText);
                    consoleView.append("ohai");
                    try{Thread.sleep(5000);} catch (Exception e){}
                    MainActivity.this.updateConsoleStatus("ohai2");

                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                TextView consoleView = (TextView) findViewById(R.id.consoleText);
                                consoleView.append("onComplete");
                                try{Thread.sleep(5000);} catch (Exception e){}
                                // updateConsoleStatus("File saved to : \n"+outputFile.getAbsolutePath()+"\n");
                                // try{Thread.sleep(5000);} catch (Exception e){}

                                MainActivity.this.submitToSavvy();
                            } catch(IOException e){

                            }
                            final ToggleButton button = findViewById(R.id.start_stop);
                            button.setChecked(false);
                        }
                    });
                }
            };

    private void updateConsoleStatus(String data) {
        TextView consoleView = (TextView) findViewById(R.id.consoleText);
        // ScrollView scrollView = (ScrollView) findViewById(R.id.scroll);
        consoleView.append(data);
        // scrollView.post(new Runnable() {
        //     @Override
        //     public void run() {
        //         scrollView.fullScroll(View.FOCUS_DOWN);
        //     }
        // });


    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final ToggleButton button = findViewById(R.id.start_stop);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ToggleButton b = (ToggleButton) v;
                boolean isChecked = ((ToggleButton) v).isChecked();
                if(isChecked) {
                    serialFun();
                } else {
                    if(mSerialIoManager!=null) {
                        mSerialIoManager.stop();
                    }
                }
                // Code here executes on main thread after user presses button
            }
        });
        setupSharedPreferences();

    }

    private void setupSharedPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }



    // Method to pass value from SharedPreferences
    private void loadColorFromPreference(SharedPreferences sharedPreferences) {
//        Log.d("Parzival",sharedPreferences.getString(getString(R.string.pref_color_key),getString(R.string.pref_color_red_value)));
//        changeTextColor(sharedPreferences.getString(getString(R.string.pref_color_key),getString(R.string.pref_color_red_value)));
    }

    private void loadSizeFromPreference(SharedPreferences sharedPreferences) {
//        float minSize = Float.parseFloat(sharedPreferences.getString(getString(R.string.pref_size_key), "16.0"));
//        changeTextSize(minSize);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

//        if (key.equals("display_text")) {
//            setTextVisible(sharedPreferences.getBoolean("display_text",true));
//        } else if (key.equals("color")) {
//            loadColorFromPreference(sharedPreferences);
//        } else if (key.equals("size"))  {
//            loadSizeFromPreference(sharedPreferences);
//        }
    }






    private void serialFun(){
        TextView consoleView = (TextView) findViewById(R.id.consoleText);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        final Boolean isFast = sharedPreferences.getBoolean(getString(R.string.pref_jpi_speed_key), false);

        int baud = 9600;
        if(isFast){
            baud = 19200;
        }
        updateConsoleStatus("Using speed of : "+baud+"\n");
        updateConsoleStatus("NOTE : JPI must be set to Fast?="+(isFast?"Y":"N")+"\n\n");

        // Find all available drivers from attached devices.
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if(availableDrivers==null) {
            updateConsoleStatus("No USB-serial adapters connected, exiting.\n");
            final ToggleButton button = findViewById(R.id.start_stop);
            button.setChecked(false);
            return;
        } else if (availableDrivers.isEmpty()) {
            updateConsoleStatus("No compatible USB-serial adapters, exiting.\n");
            final ToggleButton button = findViewById(R.id.start_stop);
            button.setChecked(false);
            return;
        } else {
            updateConsoleStatus("Adapter found.\n");
        }

// Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        updateConsoleStatus("Adapter Info: \n");
        updateConsoleStatus("* Manufacturer: "+driver.getDevice().getManufacturerName() +"\n");
        updateConsoleStatus("* Name: "+driver.getDevice().getDeviceName() +"\n");
        updateConsoleStatus("* Id: "+driver.getDevice().getProductId() +"\n");

        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
//        if (connection == null) {
//            updateConsoleStatus("Error connecting\n");
//            manager.requestPermission(driver.getDevice(), null);
////            // You probably need to call UsbManager.requestPermission(driver.getDevice(), ..)
//            final ToggleButton button = findViewById(R.id.start_stop);
//            button.setChecked(false);
//            return;
//        }

        if(connection == null && !manager.hasPermission(driver.getDevice())) {
            UsbPermission usbPermission = UsbPermission.Requested;
            int flags = PendingIntent.FLAG_MUTABLE;
            Intent intent = new Intent(INTENT_ACTION_GRANT_USB);
            intent.setPackage(this.getPackageName());
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this, 0, intent, flags);
            updateConsoleStatus("usb permissions requested");
            manager.requestPermission(driver.getDevice(), usbPermissionIntent);
//            return;
        }
        if(connection == null) {
            if (!manager.hasPermission(driver.getDevice()))
                updateConsoleStatus("connection failed: permission denied");
            else
                updateConsoleStatus("connection failed: open failed");
            return;
        }


// Read some data! Most have just one port (port 0).
        UsbSerialPort port = driver.getPorts().get(0);
        try{
            port.open(connection);
            port.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            updateConsoleStatus("Adapter connected.\n");

            Date current = new Date();
            SimpleDateFormat dateFileName = new SimpleDateFormat("yyyy-MM-dd_HHmmss");

            outputFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), dateFileName.format(current)+".JPI");

            mSerialIoManager = new SerialInputOutputManager(port, mListener, new FileOutputStream(outputFile));
            mExecutor.submit(mSerialIoManager);

            updateConsoleStatus("\nListening for JPI\nPress STEP now to begin transfer \n");


        } catch (IOException e) {
            updateConsoleStatus("Connection error\n");
            updateConsoleStatus(e.getMessage());
            final ToggleButton button = findViewById(R.id.start_stop);
            button.setChecked(false);
            // Deal with error.
        } finally {

//            try {
//                updateConsoleStatus("close port");
//                port.close();
//            } catch (IOException e){
//                //nothing to see here
//            }
        }

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
    }










    private void submitToSavvy() throws IOException {

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String token = sharedPreferences.getString(getString(R.string.pref_savvy_api_token_key), null);
        String aircraft = sharedPreferences.getString(getString(R.string.pref_savvy_aircraft_key), null);

        if (token == null || aircraft == null) {
            updateConsoleStatus("\nSavvy not configured, skipping upload");

            return;
        }
        updateConsoleStatus("\nSavvy upload starting\n");

        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
        S3UploadManager s3UploadManager = new S3UploadManager(queue, (TextView) findViewById(R.id.consoleText), (ScrollView) findViewById(R.id.scroll), token, aircraft, outputFile);
        s3UploadManager.startUploadProcess();
    }


}
