package com.zerotreedelta.jpi.upload;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;


public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener, Preference.OnPreferenceChangeListener {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings_pref);

        SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();
        PreferenceScreen prefScreen = getPreferenceScreen();

        int count = prefScreen.getPreferenceCount();

        // Go through all of the preferences, and set up their preference summary.
        for (int i = 0; i < count; i++) {
            Preference p = prefScreen.getPreference(i);
            // You don't need to set up preference summaries for checkbox preferences because
            // they are already set up in xml using summaryOff and summary On
            if (!(p instanceof CheckBoxPreference)) {
                String value = sharedPreferences.getString(p.getKey(), "");
                setPreferenceSummary(p, value);
            }
        }

        Preference tokenPref = findPreference(getString(R.string.pref_savvy_api_token_key));
        tokenPref.setOnPreferenceChangeListener(this);

        ListPreference aircraftPref = (ListPreference) findPreference(getString(R.string.pref_savvy_aircraft_key));
        aircraftPref.setOnPreferenceChangeListener(this);

        String apiToken = sharedPreferences.getString(getString(R.string.pref_savvy_api_token_key),null);

        if(apiToken!=null) {
            updateAircraftList(apiToken, aircraftPref);
        }
    }


    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        String apiTokenKey = getString(R.string.pref_savvy_api_token_key);
        if (preference.getKey().equals(apiTokenKey)) {
            String apiToken = (String) newValue;
            verifyApiToken(apiToken);
        }
//        String tailKey = getString(R.string.pref_savvy_tailnbr_key);
//        if (preference.getKey().equals(tailKey)) {
//            String tailNbr = (String) newValue;
//            if(!"N5503D".equals(tailNbr)){
//                Toast error = Toast.makeText(getActivity(), "Unable to verify Aircraft", Toast.LENGTH_LONG);
//                error.show();
//                return false;
//            }
//
//        }

        return true;
    }



    private void updateAircraftList(final String token, final ListPreference aircraft){


        RequestQueue queue = Volley.newRequestQueue(getContext().getApplicationContext());

        String url ="https://apps.savvyaviation.com/get-aircraft";

        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try{
                            JSONArray aircraftList = new JSONArray(response);
                            String[] entries = new String[aircraftList.length()];
                            String[] entryValues = new String[aircraftList.length()];

                            for(int i=0; i<aircraftList.length(); i++){
                                JSONObject aircraft = aircraftList.getJSONObject(i);
                                entries[i]=aircraft.getString("registration_no");
                                entryValues[i]=aircraft.getString("id");
                            }
                            SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();
                            String selectedAircraft = sharedPreferences.getString(getString(R.string.pref_savvy_aircraft_key),null);

                            aircraft.setEntries(entries);
                            aircraft.setEntryValues(entryValues);

                            aircraft.setValue(selectedAircraft);
//                            String name = (String) aircraft.getEntry();
//                            setPreferenceSummary(aircraft, "fpp");
                            onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_savvy_aircraft_key));
                        } catch (JSONException e){

                        }

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }


        }){
            @Override
            protected Map<String,String> getParams(){
                Map<String,String> params = new HashMap<>();
                params.put("token", token);
                return params;
            }

        };

        queue.add(stringRequest);

    }



    private void verifyApiToken(final String token){

        final Toast success = Toast.makeText(getActivity(), "API Key verified", Toast.LENGTH_LONG);
        final Toast noAircraft = Toast.makeText(getActivity(), "No aircraft on account", Toast.LENGTH_LONG);
        final Toast err = Toast.makeText(getActivity(), "Unable to verify API Key", Toast.LENGTH_LONG);


        RequestQueue queue = Volley.newRequestQueue(getContext().getApplicationContext());

        String url = "https://apps.savvyaviation.com/get-aircraft";

        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try{
                            JSONArray json = new JSONArray(response);
                            if(json.length()==0){
                                //empty list = no aircraft set
                                noAircraft.show();
                            }
                            ListPreference aircraftPref = (ListPreference) findPreference(getString(R.string.pref_savvy_aircraft_key));
                            updateAircraftList(token, aircraftPref);

                            success.show();
                        } catch (JSONException e){
                                err.show();
                        }

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                err.show();
            }


        }){
            @Override
            protected Map<String,String> getParams(){
                Map<String,String> params = new HashMap<>();
                params.put("token", token);
                return params;
            }

        };

        queue.add(stringRequest);

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Figure out which preference was changed
        Preference preference = findPreference(key);
        if (null != preference) {
            // Updates the summary for the preference
            if (!(preference instanceof CheckBoxPreference)) {
                String value = sharedPreferences.getString(preference.getKey(), "");
                setPreferenceSummary(preference, value);
            }
        }
    }

    private void setPreferenceSummary(Preference preference, String value) {
        if (preference instanceof ListPreference) {
            // For list preferences, figure out the label of the selected value
            ListPreference listPreference = (ListPreference) preference;
            int prefIndex = listPreference.findIndexOfValue(value);
            if (prefIndex >= 0) {
                // Set the summary to that label
                listPreference.setSummary(listPreference.getEntries()[prefIndex]);
            }
        } else if (preference instanceof EditTextPreference) {
            // For EditTextPreferences, set the summary to the value's simple string representation.
            preference.setSummary(value);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }
}
