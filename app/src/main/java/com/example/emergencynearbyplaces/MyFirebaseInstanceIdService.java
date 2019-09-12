package com.example.emergencynearbyplaces;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;

public class MyFirebaseInstanceIdService extends FirebaseInstanceIdService {
    private static final String TAG = "MyFirebaseIdService";
    private static final String TOPIC_GLOBAL = "global";
    JSONObject data;
    TelephonyManager telephonyManager;

    @Override
    public void onTokenRefresh() {
        // Get updated InstanceID token.
        String refreshedToken = FirebaseInstanceId.getInstance().getToken();
        Log.d(TAG, "Refreshed token: " + refreshedToken);

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // Instance ID token to your app server.

        sendRegistrationToServer(refreshedToken);
    }

    /**
     * Persist token to third-party servers.
     * <p>
     * Modify this method to associate the user's FCM InstanceID token with any server-side account
     * maintained by your application.
     *
     * @param token The new token.
     */
    private void sendRegistrationToServer(String token) {
        // TODO: Implement this method to send token to your app server.

        submitData(token);
    }

    private void submitData(String token) {

        telephonyManager = (TelephonyManager) getSystemService(Context.
                TELEPHONY_SERVICE);

        String deviceid="";
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            if (telephonyManager.getDeviceId() != null) {
                deviceid = telephonyManager.getDeviceId();
            } else {
                deviceid = Settings.Secure.getString(getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);
            }
        }

        try {
            data = new JSONObject();
            data.put("token", token);
            data.put("device", deviceid);

            String URL = "http://192.168.1.71:8888/emergencynearbyplaces/token.php";

            SaveToken saveToken = new SaveToken();
            saveToken.execute(URL);
            Log.d("Data", String.valueOf(data));

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private class SaveToken extends AsyncTask<String, Void, String> {
        private Exception exception = null;

        @Override
        protected void onPreExecute() {
            // put ui changes here
        }

        @Override
        protected String doInBackground(String... params) {
            URL url;
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;
            String JsonDATA = data.toString();
            String JsonResponse = null;

            try {

                Log.e(TAG, "Started Connecting to " + params[0]);
                url = new URL(params[0]);

                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setDoOutput(true);
                urlConnection.setRequestMethod("POST");
                urlConnection.setRequestProperty("Content-Type", "application/json");
                urlConnection.setRequestProperty("Accept", "application/json");

                Writer writer = new BufferedWriter(new OutputStreamWriter(urlConnection.getOutputStream(), "UTF-8"));
                writer.write(JsonDATA);
                writer.close();

                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String inputLine;
                while ((inputLine = reader.readLine()) != null)
                    buffer.append(inputLine + "\n");
                if (buffer.length() == 0) {
                    // Stream was empty. No point in parsing.
                    return null;
                }
                JsonResponse = buffer.toString();


                Log.i(TAG, JsonResponse);
                return JsonResponse;

            } catch (IOException e) {
                exception = e;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(TAG, "Error closing stream", e);
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) {

            if(!(exception == null)){
                Toast.makeText(getApplicationContext(),"I am here , Try again! ", Toast.LENGTH_LONG).show();

                Log.e(TAG, "Exception at login " + exception.getMessage());
            }else{
                //success
            }

        }

    }
}