package com.example.loper;

// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.Task;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.PendingResult;
import com.google.maps.internal.PolylineEncoding;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.TravelMode;

import java.util.ArrayList;
import java.util.List;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private String TAG = "MapActivity";
    // Het Google Maps object
    private GoogleMap mMap;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    // Laat de API toe om wifi of 4G te gebruiken om de locatie van de device te leren.
    // Dit is minder precies
    String COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    // Laat de API toe om een precise locatie te vinden via lokale providers.
    String FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    String[] Permissions = {COARSE_LOCATION, FINE_LOCATION};
    private int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private Boolean mLocationPermissionGranted = false;
    private float Default_Zoom = 17f;
    private double Latitude;
    private double Longitude;
    private float Distance; // distance in km
    private float Time; // time in minutes
    private int RunSpeed = 12; // speed in km/h
    private boolean GPSLocation;
    private Location currenLocation;
    private LatLng destination;
    private GeoApiContext mGeoApiContext = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        // Neemt de meegegeven locatie op
        Intent intent = getIntent();
        GPSLocation = intent.getBooleanExtra("GPSLocation", false);
        Latitude = intent.getDoubleExtra("Latitude", 0);
        Longitude = intent.getDoubleExtra("Longitude", 0);
        Distance = intent.getFloatExtra("Distance", 0);
        Time = intent.getFloatExtra("Time", 0);
        if (Time != 0 && Distance == 0){
            Distance = (Time / 60) * RunSpeed;
        }

        getLocationPermission();
    }

    private void getLocationPermission() {
        // De methode die om locatie permities vraagt
        // if (kijkt of hij de permisies heeft) else {vraagt permisies}
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                    COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mLocationPermissionGranted = true;
                InitializeMap();
            } else {
                ActivityCompat.requestPermissions(this, Permissions,
                        LOCATION_PERMISSION_REQUEST_CODE);
            }
        } else {
            ActivityCompat.requestPermissions(this, Permissions,
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    // Kijk na of het programma werkt zonder deze methode
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0) {
                for (int i = 0; i < grantResults.length; i++) {
                    // Als er een enkele permisie niet wordt toegelaten,
                    // dant gaat de kaart niet initializeren.
                    if (grantResults[i] != PackageManager.PERMISSION_DENIED) {
                        mLocationPermissionGranted = false;
                        return;
                    }
                    mLocationPermissionGranted = true;
                    InitializeMap();
                }
            }
        }
    }

    private void InitializeMap() {
        // Gaat de kaart met het Fragment object verbinden
        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(MapActivity.this);

        // Instantieer de directions calculator
        if (mGeoApiContext == null){
            mGeoApiContext= new GeoApiContext.Builder()
                    .apiKey(getString(R.string.Maps_API_key))
                    .build();
        }
    }

    public void onMapReady(GoogleMap googleMap) {
        Toast.makeText(this, "Map staat klaar", Toast.LENGTH_SHORT).show();
        // Geeft het Google Maps object een waarde
        mMap = googleMap;
        // Gaat nakijken of alle locatie permissies toegestaan zijn
        if (mLocationPermissionGranted) {
            // Gaat de locatie plaatsen in het Google Maps object afhankelijk van de huidige coördinaten
            getDeviceLocation();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            // Gaat een blauwe stip op onze locatie zetten
            mMap.setMyLocationEnabled(true);
            // Gaat de default show loation knop weghalen
            // Dit is omdat je de default knop niet kunt verplaatsen
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
            //InitializeSearch();
        }
    }

    public void getDeviceLocation() {
        // Deze methode gaat de gevraagde locatie in toepassing brengen
        Log.d(TAG, "getDeviceLocation: getting the current devices current location");
        // FusedLocationProviderClient gaat inwerken op de locatie
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // Gaat eerst checken of alle permisies gegeven zijn en pas als alle permisies er zijn
        // dan gaat hij de laatst opgenomen locatie meegeven aan de onComplete methode
        try {
            if (mLocationPermissionGranted) {
                Task location = mFusedLocationProviderClient.getLastLocation();
                location.addOnCompleteListener(this::onComplete);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "getDeviceLocation: SecurityException: " + e.getMessage());
        }
    }

    private void onComplete(Task task) {
        // Als er een locatie is meegegeven dan gaat hij de camera inzoemen met de meegegeven zoom
        // en gaat hij het center van de camera op de meegegeven coördinaten zeten
        if (task.isSuccessful()) {
            Log.d(TAG, "onComplete: found location");
            currenLocation = (Location) task.getResult();
            // deze dingen gebeuren in de moveCamera methode
            moveCamera(new LatLng(currenLocation.getLatitude(),
                    currenLocation.getLongitude()), Default_Zoom, "My Location");
            if (GPSLocation) {
                destination = new LatLng(currenLocation.getLatitude(), currenLocation.getLongitude());
                calculateDirections(destination);
                moveCamera(destination, Default_Zoom, "My Destination");
            } else if (Latitude != 0 && Longitude != 0) {
                destination = new LatLng(Latitude, Longitude);
                calculateDirections(destination);
                moveCamera(destination, Default_Zoom, "My Destination");
            } else {
                Toast.makeText(MapActivity.this, "No destination given", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.d(TAG, "onComplete: current location is null");
            Toast.makeText(MapActivity.this, "Location not given",
                    Toast.LENGTH_SHORT).show();
        }
        if (Distance != 0){
            Toast.makeText(MapActivity.this, "Distance: " + Distance + " km", Toast.LENGTH_SHORT).show();
        }
        if (Time != 0){
            Toast.makeText(MapActivity.this, "Time: " + Time + " minutes", Toast.LENGTH_SHORT).show();
        }
    }

    private void calculateDirections(LatLng destination) {
        DirectionsApiRequest directions = new DirectionsApiRequest(mGeoApiContext);

        // Geeft verschillende routes weer
        // directions.alternatives(true);
        // Gaat het start en eindpunt instellen
        // Gaat alleen met een "com.google.maps.model.LatLng" niet met een gewone Latlng
        directions.origin(new com.google.maps.model.LatLng(currenLocation.getLatitude(), currenLocation.getLongitude()));
        directions.destination(new com.google.maps.model.LatLng(destination.latitude, destination.longitude));
        directions.mode(TravelMode.WALKING);
        directions.setCallback(new PendingResult.Callback<DirectionsResult>() {
            @Override
            public void onResult(DirectionsResult result) {
                Log.d(TAG, "calculateDirections: onResult: routes: " + result.routes[0].toString());
                Log.d(TAG, "calculateDirections: onResult: duration: " + result.routes[0].legs[0].duration);
                Log.d(TAG, "calculateDirections: onResult: distance: " + result.routes[0].legs[0].distance);
                Log.d(TAG, "calculateDirections: onResult: geocodedWaypoints: " + result.geocodedWaypoints[0].toString());
                addPolylines(result);
            }

            @Override
            public void onFailure(Throwable e) {
                Log.d(TAG, "calculateDirections: onFailure: Failed to get diretions: " + e.getMessage());
            }
        });
    }

    private void addPolylines(DirectionsResult result) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Gaat de route decoderen
                DirectionsRoute route[] = result.routes;
                Log.d(TAG, "run: leg: " + route[0].legs[0].toString());
                List<com.google.maps.model.LatLng> decodedPath = PolylineEncoding.decode(route[0].overviewPolyline.getEncodedPath());
                List<LatLng> newDecodedPath = new ArrayList<>();
                for (com.google.maps.model.LatLng latlng: decodedPath) {
                    newDecodedPath.add(new LatLng(latlng.lat, latlng.lng));
                }
                // Fout zit hier bij polylines aanmaken
                Polyline polyline = mMap.addPolyline(new PolylineOptions().addAll(newDecodedPath));
                // polyline.setColor(ContextCompat.getColor("Activity", R.color.colorPrimaryDark));
            }
        });
    }

    private void moveCamera(LatLng latLng, float zoom, String title) {
        // Gaat een zekere zoom initalizeren op de coördinaten van de huidige positie
        Log.d(TAG, "moveCamera: moving the camera to lat: " + latLng.latitude +
                ", lng: " + latLng.longitude);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));

        if (!title.equals("My Location")){
            // Marker zetten op de meegegeven plaats
            MarkerOptions options = new MarkerOptions()
                    .position(latLng)
                    .title(title);
            mMap.addMarker(options);
        }
    }
}