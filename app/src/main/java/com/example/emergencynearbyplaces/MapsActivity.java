package com.example.emergencynearbyplaces;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
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
import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends FragmentActivity implements
        OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        GoogleMap.OnMyLocationClickListener,
        GoogleMap.OnMyLocationButtonClickListener {


    private GoogleMap mMap;
    private GoogleApiClient client;
    private LocationRequest locationRequest;
    private Location lastLocation;
    private Marker currentLocationMarker;
    public static final int REQUEST_LOCATION_CODE = 99;
    int PROXIMITY_RADIUS = 10000;//search location within the 10km radius from the current location
    double latitude, longitude;
    JSONObject data;
    TelephonyManager telephonyManager;
    private static final String TAG = "MapActivity";
    private static final int PERMISSIONS_REQUEST_READ_PHONE_STATE = 999;
    String  title= "", message = "", institution = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BASE) {
            checkLocationPermission();

        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE},
                        PERMISSIONS_REQUEST_READ_PHONE_STATE);
            } else {
                getDeviceImei();
            }
        }

        FirebaseInstanceId.getInstance().getInstanceId().addOnSuccessListener(MapsActivity.this, new OnSuccessListener<InstanceIdResult>() {
            @Override
            public void onSuccess(InstanceIdResult instanceIdResult) {
                String newToken = instanceIdResult.getToken();
                Log.e("newToken", newToken);
                submitData(newToken);
            }
        });
//
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcast_handle, new IntentFilter("com.example.emergencynearbyplaces_FCM_MESSAGE"));

        if (getIntent().getExtras() != null){
            for (String key : getIntent().getExtras().keySet()){
                if (key.equals("title")){
                    title = getIntent().getExtras().getString("title");
                }
                if (key.equals("message")){
                    message = getIntent().getExtras().getString("message");
                }
                displayAlertNotification(title, message);
            }
        }
    }

    private void displayAlertNotification(String title, String message) {
        Toast.makeText(getApplicationContext(), "Title: "+title,Toast.LENGTH_LONG).show();
        Toast.makeText(getApplicationContext(), "Message: "+message,Toast.LENGTH_LONG).show();
    }

   private BroadcastReceiver broadcast_handle = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String title = intent.getStringExtra("title");
            String message = intent.getStringExtra("message");

            title =title;
            message = message;

            displayAlertNotification(title, message);
        }
    };

    @Override
    protected void onPause() {
        super.onPause();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcast_handle);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_LOCATION_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        if (client == null) {
                            buildGoogleApiClient();
                        }
                        mMap.setMyLocationEnabled(true);
                    }
                } else {
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_LONG).show();

                }
            case PERMISSIONS_REQUEST_READ_PHONE_STATE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getDeviceImei();
                }
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            buildGoogleApiClient();
            mMap.setMyLocationEnabled(true);
            mMap.setOnMyLocationClickListener(this);
            // mMap.setOnMyLocationClickListener(this);
            mMap.setOnMyLocationButtonClickListener(this);

        }
    }


    protected synchronized void buildGoogleApiClient() {
        client = new GoogleApiClient.Builder(this).addConnectionCallbacks(this).addOnConnectionFailedListener(this).addApi(LocationServices.API).build();
        client.connect();

    }

    @Override
    public void onLocationChanged(Location location) {

        latitude = location.getLatitude();
        longitude = location.getLongitude();
        lastLocation = location;
        if (currentLocationMarker != null) {
            currentLocationMarker.remove();

        }

        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(latLng);
        markerOptions.title("Your Current Location");
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
        currentLocationMarker = mMap.addMarker(markerOptions);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomBy(12));

        if (client != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(client, this);
        }


        //calculate distance


        //if(d == 5km){
            //send a notifications
            getInstToken(institution);
        //}


    }

    public void onClick(View v) {
        Object dataTransfer[] = new Object[2];
        GetNearbyPlacesData getNearbyPlacesData = new GetNearbyPlacesData();

        switch (v.getId()) {
            case R.id.search_address:
                @SuppressLint("WrongViewCast") EditText locations = (EditText) findViewById(R.id.location_search);
                String location = locations.getText().toString();
                List<Address> addressList;


                if (!location.equals("")) {
                    Geocoder geocoder = new Geocoder(this);

                    try {
                        addressList = geocoder.getFromLocationName(location, 5);

                        if (addressList != null) {
                            for (int i = 0; i < addressList.size(); i++) {
                                LatLng latLng = new LatLng(addressList.get(i).getLatitude(), addressList.get(i).getLongitude());
                                MarkerOptions markerOptions = new MarkerOptions();
                                markerOptions.position(latLng);
                                markerOptions.title(location);
                                mMap.addMarker(markerOptions);
                                mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
                                mMap.animateCamera(CameraUpdateFactory.zoomTo(10));
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case R.id.hospital_near:
                mMap.clear();
                String hospital = "hospital";
                institution= "hospital";
                String url = getUrl(latitude, longitude, hospital);
                dataTransfer[0] = mMap;
                dataTransfer[1] = url;


                getNearbyPlacesData.execute(dataTransfer);
                Toast.makeText(MapsActivity.this, "Showing Nearby Hospitals", Toast.LENGTH_SHORT).show();
                break;


            case R.id.police_near:
                mMap.clear();
                String police = "police";
                institution= "police";
                url = getUrl(latitude, longitude, police);
                dataTransfer[0] = mMap;
                dataTransfer[1] = url;

                getNearbyPlacesData.execute(dataTransfer);
                Toast.makeText(MapsActivity.this, "Showing Nearby Police Stations", Toast.LENGTH_SHORT).show();
                break;
            case R.id.firestation_near:
                mMap.clear();
                String bank = "Bank";
                institution= "Bank";
                url = getUrl(latitude, longitude, bank);
                dataTransfer[0] = mMap;
                dataTransfer[1] = url;

                getNearbyPlacesData.execute(dataTransfer);
                Toast.makeText(MapsActivity.this, "Showing Nearby Banks & ATM Machine", Toast.LENGTH_SHORT).show();
                break;

            case R.id.garage_near:
                mMap.clear();
                String administrative_office = "Administrative";
                institution= "Administrative";
                url = getUrl(latitude, longitude, administrative_office);
                dataTransfer[0] = mMap;
                dataTransfer[1] = url;

                getNearbyPlacesData.execute(dataTransfer);
                Toast.makeText(MapsActivity.this, "Showing Nearby administrative office", Toast.LENGTH_SHORT).show();
        }
    }


    private String getUrl(double latitude, double longitude, String nearbyPlace) {

        StringBuilder googlePlaceUrl = new StringBuilder("https://maps.googleapis.com/maps/api/place/nearbysearch/json?");
        googlePlaceUrl.append("location=" + latitude + "," + longitude);
        googlePlaceUrl.append("&radius=" + PROXIMITY_RADIUS);
        googlePlaceUrl.append("&type=" + nearbyPlace);
        googlePlaceUrl.append("&sensor=true");
        googlePlaceUrl.append("&key=" + "AIzaSyCnS9sn6KocUEotwer_AQdMKNQl9UjWD08");

        Log.d("MapsActivity", "url = " + googlePlaceUrl.toString());

        return googlePlaceUrl.toString();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

        locationRequest = new LocationRequest();
        locationRequest.setInterval(1100);
        locationRequest.setFastestInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(client, locationRequest, this);
        }
    }


    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_CODE);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION_CODE);
            }
            return false;

        } else
            return true;
    }


    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }

    @Override
    public void onMyLocationClick(@NonNull Location location) {
        Toast.makeText(this, "Current location:\n" + location, Toast.LENGTH_LONG).show();

    }

    @Override
    public boolean onMyLocationButtonClick() {
        Toast.makeText(this, "MyLocation button clicked", Toast.LENGTH_SHORT).show();

        return false;
    }

    private void submitData(String token) {

        try {
            data = new JSONObject();
            data.put("token", token);
            data.put("device", getDeviceImei());

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

            if (!(exception == null)) {
                Toast.makeText(getApplicationContext(), "I am here , Try again! ", Toast.LENGTH_LONG).show();

                Log.e(TAG, "Exception at login " + exception.getMessage());
            } else {
                //success
            }

        }

    }


    private void getInstToken(String institution) {

        try {
            data = new JSONObject();
            data.put("institution", institution);

            String URL = "http://192.168.1.71:8888/emergencynearbyplaces/getInstitutionToken.php";

            InstToken instToken = new InstToken();
            instToken.execute(URL);
            Log.d("Data", String.valueOf(data));

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private class InstToken extends AsyncTask<String, Void, String> {
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

            if (!(exception == null)) {
                Toast.makeText(getApplicationContext(), "I am here , Try again! ", Toast.LENGTH_LONG).show();

                Log.e(TAG, "Exception at login " + exception.getMessage());
            } else {
                //success
                Log.d("token_from_server", s);
                try {

                    JSONObject object = new JSONObject(s);
                    String token = object.getString("token");

                    sendNotification( token);

                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }

        }

    }

    //send a notification
    private void sendNotification(String token) {

        try {
            data = new JSONObject();
            data.put("token", token);

            String URL = "http://192.168.1.71:8888/emergencynearbyplaces/pushnotification.php";

            NotifyEmergency notifyEmergency = new NotifyEmergency();
            notifyEmergency.execute(URL);
            Log.d("Data", String.valueOf(data));

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private class NotifyEmergency extends AsyncTask<String, Void, String> {
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

            if (!(exception == null)) {
                Toast.makeText(getApplicationContext(), "I am here , Try again! ", Toast.LENGTH_LONG).show();

                Log.e(TAG, "Exception at login " + exception.getMessage());
            } else {
                //success
            }

        }

    }


    private String getDeviceImei() {

        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        String deviceid = "";

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            if (telephonyManager.getDeviceId() != null){
                deviceid = telephonyManager.getDeviceId();
            }else{
                deviceid = Settings.Secure.getString(getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);
            }
            Log.d("msg", "DeviceImei " + deviceid);
        }else{

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE},
                        PERMISSIONS_REQUEST_READ_PHONE_STATE);
            }

            if (telephonyManager.getDeviceId() != null){
                deviceid = telephonyManager.getDeviceId();
            }else{
                deviceid = Settings.Secure.getString(getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);
            }
        }

        return  deviceid;
    }


}

