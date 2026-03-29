package com.zerotreedelta.jpi.upload;

import android.os.Handler;
import android.os.Looper;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class S3UploadManager {

    private RequestQueue queue;
    private String token;
    private String savedSavvyAircraftId;

    private TextView consoleView;
    private ScrollView scrollView;
    private File jpiFile;
    private String fileName;

    public S3UploadManager(RequestQueue queue, TextView consoleView, ScrollView scrollView, String token, String savedSavvyAircraftId, File file) {
        this.queue = queue;
        this.token = token;
        this.savedSavvyAircraftId = savedSavvyAircraftId;
        this.consoleView = consoleView;
        this.jpiFile = file;
        this.fileName = file.getName();
    }

    // STEP 1: Get Aircraft ID
    public void startUploadProcess() {
        String getAircraftUrl = "https://apps.savvyaviation.com/get-aircraft/";
        updateConsoleStatus("* Verifying aircraft from Savvy\n");
        StringRequest aircraftRequest = new StringRequest(Request.Method.POST, getAircraftUrl,
                response -> {
                    try {
                        JSONArray arr = new JSONArray(response);
                        String savvyAircraftId = null;
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject item = arr.getJSONObject(i);
                            if (item.getString("id").equals(savedSavvyAircraftId)) {
                                savvyAircraftId = item.getString("id");
                                break;
                            }
                        }

                        if (savvyAircraftId != null) {
                            getUploadUrl(savvyAircraftId); // Trigger Step 2
                        } else {
                            updateConsoleStatus("No matching aircraft found from settings\n");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        updateConsoleStatus(e.getMessage());
                    }
                },
                error -> error.printStackTrace()) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("token", token);
                return params;
            }
        };

        queue.add(aircraftRequest);
    }

    // STEP 2: Get Upload URL & S3 Credentials
    private void getUploadUrl(String savvyAircraftId) {
        String getUploadUrl = "https://apps.savvyaviation.com/request_upload_url/" + savvyAircraftId + "/";
        updateConsoleStatus("* Getting temporary upload url\n");
        StringRequest uploadUrlReq = new StringRequest(Request.Method.POST, getUploadUrl,
                response -> {
                    try {
                        JSONObject uploadUrlJson = new JSONObject(response);
                        JSONObject fields = uploadUrlJson.getJSONObject("fields");

                        Map<String, String> s3Params = new HashMap<>();
                        s3Params.put("key", fields.getString("key"));
                        s3Params.put("AWSAccessKeyId", fields.getString("AWSAccessKeyId"));
                        s3Params.put("policy", fields.getString("policy"));
                        s3Params.put("signature", fields.getString("signature"));
                        s3Params.put("x-amz-security-token", fields.getString("x-amz-security-token"));

                        // Your original code used a hardcoded string as the file.
                        // Converting it to bytes for the upload.

                        uploadToS3(s3Params, fullyReadFileToBytes(jpiFile), fileName);// Trigger Step 3

                        String fileId = uploadUrlJson.getString("id");

                        updateConsoleStatus("* Checking upload status: \n");
                        checkUploadStatus(queue, token, fileId, 1);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                },
                error -> error.printStackTrace()) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("token", token);
                params.put("filename", fileName);
                return params;
            }
        };

        queue.add(uploadUrlReq);
    }

    // STEP 3: Upload to S3 using Custom Volley Request
    private void uploadToS3(Map<String, String> s3Params, byte[] fileData, String fileName) {
        String s3Url = "https://savvyanalysis.s3.amazonaws.com/";

        updateConsoleStatus("* Uploading to Savvy \n");
        VolleyMultipartRequest multipartRequest = new VolleyMultipartRequest(Request.Method.POST, s3Url,
                response -> {
                    String resultResponse = new String(response.data);
                },
                error -> error.printStackTrace()) {

            @Override
            protected Map<String, String> getParams() {
                return s3Params; // Pass the S3 credentials as form parts
            }

            @Override
            protected Map<String, DataPart> getByteData() {
                Map<String, DataPart> params = new HashMap<>();
                // Form field name is "file", filename is passed here
                params.put("file", new DataPart(fileName, fileData, "application/octet-stream"));
                return params;
            }
        };

        queue.add(multipartRequest);
    }






    public void checkUploadStatus(final RequestQueue requestQueue, final String token, final String fileId, final int attemptCount) {
        // Replicate the standard loop condition (i < 6)
        if (attemptCount >= 10) {
            updateConsoleStatus("\nMax attempts reached. Stopping polling.");
            return;
        }

        String getStatusUrl = "https://apps.savvyaviation.com/upload_status/" + fileId + "/";

        StringRequest stringRequest = new StringRequest(Request.Method.GET, getStatusUrl,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        System.out.println(response);
                        try {
                            JSONObject statusJson = new JSONObject(response);
                            String currentStatus = statusJson.getString("status");
                            updateConsoleStatus(" > "+ currentStatus + "\n");
                            List<String> nonFinalStatuses = Arrays.asList("init", "waiting_for_file_upload", "compute_hash_digest", "parsing");

                            if (nonFinalStatuses.contains(currentStatus)) {
                                // Status is not final. Wait 5 seconds and try again.
                                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        checkUploadStatus(requestQueue, token, fileId,attemptCount + 1);
                                    }
                                }, 5000);
                            } else {
                                // Final status reached, break the "loop" equivalent
                                updateConsoleStatus("\nSavvy final status: " + currentStatus);
                                // Do your success handling here
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        System.out.println("Network request failed: " + error.getMessage());
                    }
                }) {
            /**
             * This is where you set your Headers
             */
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                // Standard Authorization header format
                headers.put("Authorization", "Bearer " + token);
                // If your API doesn't use "Bearer", just use: headers.put("Authorization", token);
                return headers;
            }
        };

        // Add the request to the RequestQueue.
        requestQueue.add(stringRequest);
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




    private void updateConsoleStatus(String data) {
        consoleView.append(data);
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }


}