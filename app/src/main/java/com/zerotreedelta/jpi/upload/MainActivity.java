package com.zerotreedelta.jpi.upload;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Environment;
import androidx.preference.ListPreference;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.android.volley.DefaultRetryPolicy;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {


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
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                TextView consoleView = (TextView) findViewById(R.id.consoleText);
                                consoleView.append("File saved to : \n"+outputFile.getAbsolutePath()+"\n");
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
        consoleView.append(data);
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //visibletxt = findViewById(R.id.visible_text);
//        colortxt = "red";//findViewById(R.id.color_text);
        //sizetxt = findViewById(R.id.size_text);

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
//        try{
//            submitToSavvy();
//        } catch(IOException e){
//
//        }
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
        consoleView.setText("Starting load...\n");




        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        final Boolean isFast = sharedPreferences.getBoolean(getString(R.string.pref_jpi_speed_key), false);

        int baud = 9600;
        if(isFast){
            baud = 19200;
        }
        consoleView.append("\nUsing speed of : "+baud+"\n");
        consoleView.append("NOTE : JPI must be set to Fast?="+(isFast?"Y":"N")+"\n\n");


// Find all available drivers from attached devices.
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager, consoleView);
        if(availableDrivers==null) {
            consoleView.append("No USB-serial adapters connected, exiting.\n");
            final ToggleButton button = findViewById(R.id.start_stop);
            button.setChecked(false);
            return;
        } else if (availableDrivers.isEmpty()) {
            consoleView.append("No compatible USB-serial adapters, exiting.\n");
            final ToggleButton button = findViewById(R.id.start_stop);
            button.setChecked(false);
            return;
        } else {
            consoleView.append("Adapter found.\n");
        }

// Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);


        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            consoleView.append("Error connecting\n");
//            // You probably need to call UsbManager.requestPermission(driver.getDevice(), ..)
            final ToggleButton button = findViewById(R.id.start_stop);
            button.setChecked(false);
            return;
        }

// Read some data! Most have just one port (port 0).
        UsbSerialPort port = driver.getPorts().get(0);
        try{
            port.open(connection);
            port.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            consoleView.append("Adapter connected.\n");

            Date current = new Date();
            SimpleDateFormat dateFileName = new SimpleDateFormat("yyyymmdd-HHmmss");

            outputFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), dateFileName.format(current)+".JPI");

            mSerialIoManager = new SerialInputOutputManager(port, mListener, new FileOutputStream(outputFile));
            mExecutor.submit(mSerialIoManager);

            consoleView.append("\nListening for JPI\nPress STEP now to begin transfer \n");


        } catch (IOException e) {
            consoleView.append("Connection error\n");
            consoleView.append(e.getMessage());
            final ToggleButton button = findViewById(R.id.start_stop);
            button.setChecked(false);
            // Deal with error.
        } finally {

//            try {
//                consoleView.append("close port");
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




    private void submitToSavvy() throws IOException{
        final byte[] bytes = fullyReadFileToBytes(outputFile);


//        final Toast success = Toast.makeText(getActivity(), "API Key verified", Toast.LENGTH_LONG);
//        final Toast noAircraft = Toast.makeText(getActivity(), "No aircraft on account", Toast.LENGTH_LONG);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        final String token = sharedPreferences.getString(getString(R.string.pref_savvy_api_token_key), null);
        String aircraft = sharedPreferences.getString(getString(R.string.pref_savvy_aircraft_key), null);

        if(token==null || aircraft == null){
            updateConsoleStatus("\nSavvy not configured, skipping upload");

            return;
        }
        updateConsoleStatus("\nSavvy upload starting\n");

        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
        String url ="https://apps.savvyaviation.com/upload_files_api/"+aircraft+"/";

        VolleyMultipartRequest multipartRequest = new VolleyMultipartRequest(Request.Method.POST, url, new Response.Listener<NetworkResponse>() {



            @Override
            public void onResponse(NetworkResponse response) {
                String resultResponse = new String(response.data);
                // parse success output
                try {
                    JSONObject resp = new JSONObject(resultResponse);
                    updateConsoleStatus("\nSavvy upload : "+ resp.get("status"));

                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
            }
        }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("token", token);
                return params;
            }

            @Override
            protected Map<String, DataPart> getByteData() {
                Map<String, DataPart> params = new HashMap<>();
                // file name could found file base or direct access from real path
                // for now just get bitmap data from ImageView
                params.put("file", new DataPart(outputFile.getName(), bytes));

                return params;
            }
        };

        multipartRequest.setRetryPolicy(new DefaultRetryPolicy(
                30000,
                0,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        queue.add(multipartRequest);

    }

    private byte[] fullyReadFileToBytes(File f) throws IOException {
        int size = (int) f.length();
        byte bytes[] = new byte[size];
        byte tmpBuff[] = new byte[size];
        FileInputStream fis= new FileInputStream(f);
        try {

            int read = fis.read(bytes, 0, size);
            if (read < size) {
                int remain = size - read;
                while (remain > 0) {
                    read = fis.read(tmpBuff, 0, remain);
                    System.arraycopy(tmpBuff, 0, bytes, size - remain, read);
                    remain -= read;
                }
            }
        }  catch (IOException e){
            throw e;
        } finally {
            fis.close();
        }

        return bytes;
    }

}
