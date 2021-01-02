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

// MapFragment & Marker: (CodingWithMich)
// Google Maps & Google Places Android Course (Epsiode 1 - 7)
// https://www.youtube.com/playlist?list=PLgCYzUzKIBE-vInwQhGSdnbyJ62nixHCt

// Polylines: (CodingWithMich)
// Google Maps and Google Directions API (Epsiode 18 - 20)
// https://www.youtube.com/playlist?list=PLgCYzUzKIBE-SZUrVOsbYMzH7tPigT3gi

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
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
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.maps.model.DirectionsLeg;
import com.google.maps.model.DirectionsStep;

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

    private boolean mLocationPermissionGranted = false;
    private float mDefaultZoom = 15f;

    // Meegegeven Bestemming
    private double mLatitude;
    private double mLongitude;

    // Afstands Variabelen
    private float mDistance; // distance in km
    private float mTime; // time in minutes
    private float mRunSpeed = 0.2f; // speed in km/min (12 km/h / 60 = 0.2 km/min)

    private RouteCalculator mCalculator;

    // Maps Variabelen
    private Location mCurrentLocation;
    private LatLng mDestination = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        // Neemt de meegegeven locatie op
        Intent intent = getIntent();
        mLatitude = intent.getDoubleExtra("Latitude", 0);
        mLongitude = intent.getDoubleExtra("Longitude", 0);
        mDistance = intent.getFloatExtra("Distance", 0);
        // BUG: Tijd wordt niet meegegeven
        mTime = intent.getFloatExtra("Time", 0);
        if (mTime != 0 && mDistance == 0) mDistance = mTime * mRunSpeed;

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
            // Gaat de default show location knop weghalen
            // Dit is omdat je de default knop niet kunt verplaatsen
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
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
                location.addOnCompleteListener(new OnCompleteListener(){
                    public void onComplete(Task task) {
                        // Als er een locatie is meegegeven dan gaat hij de camera inzoemen met de meegegeven zoom
                        // en gaat hij het center van de camera op de meegegeven coördinaten zeten
                        if (task.isSuccessful()) {
                            Log.d(TAG, "onComplete: found location");
                            mCurrentLocation = (Location) task.getResult();
                            if (mCurrentLocation != null){
                                if (mLatitude != 0 || mLongitude != 0) {
                                    mDestination = new LatLng(mLatitude, mLongitude);
                                } else {
                                    mDestination = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
                                }
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                        new LatLng(mCurrentLocation.getLatitude(),
                                                mCurrentLocation.getLongitude()), mDefaultZoom));
                            }
                        } else {
                            Log.d(TAG, "onComplete: current location is null");
                        }
                    }
                });
            }
        } catch (SecurityException e) {
            Log.e(TAG, "getDeviceLocation: SecurityException: " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        update();
    }

    private Runnable updater;
    private boolean stop = false;
    private int delay = 5000; // 3 seconden
    private float mRunTime;
    private long mStartTime;
    // Deze variabele wordt gebruikt om de update te tonen,
    // indien u de route niet wilt aflopen kan u de deelFactor gelijk zetten aan 2.
    private int deelFactor = 1;
    final Handler timerHandler = new Handler();

    private void update(){
        updater = new Runnable() {
            @Override
            public void run() {
                if (mStartTime != 0){
                    LatLng temp = new LatLng(mMap.getMyLocation().getLatitude(), mMap.getMyLocation().getLongitude());
                    mRunTime = (float) ((SystemClock.elapsedRealtime() - mStartTime) / (60 * 1000));
                    Log.d(TAG, "run: Time Running = " + mRunTime + "min");
                    float runningDistance = 0;
                    boolean onTime = true;
                    for (DirectionsLeg leg: mCalculator.bestRoute.legs){
                        for (DirectionsStep step: leg.steps) {
                            String[] stringArray;

                            // Voegt de afstand van elke polyline/route toe aan tempDistance
                            if(step.distance.toString().contains(" km")) {
                                stringArray = step.distance.toString().split(" km");
                                runningDistance += Float.parseFloat(stringArray[0]);
                            }
                            else if(step.distance.toString().contains(" m")) {
                                stringArray = step.distance.toString().split(" m");
                                runningDistance += Float.parseFloat(stringArray[0]) / 1000;
                            }
                            else if(step.distance.toString().contains(" mi")) {
                                stringArray = step.distance.toString().split(" mi");
                                runningDistance += Float.parseFloat(stringArray[0]) / 0.62137;
                            }
                            else if(step.distance.toString().contains(" ft")) {
                                stringArray = step.distance.toString().split(" ft");
                                runningDistance += Float.parseFloat(stringArray[0]) / 3280.8;
                            }

                            Log.d(TAG, "run: Time = " + mRunTime + "min & Distance = " + runningDistance + "km");
                            // Kijkt naar de juiste route
                            if(isOnRoute(new com.google.maps.model.LatLng(temp.latitude, temp.longitude),
                                    step.startLocation, step.endLocation)){
                                Log.d(TAG, "run: You're on Route");
                                // kijkt of de gebruiker nog steeds op tijd is voor de huidige route
                                if (!isOnTime(runningDistance, mRunTime)){
                                    Log.d(TAG, "run: You're not on Time");
                                    // Deze lijn is om te tonen dat de update daadwerkelijk werkt,
                                    // anders moet u de route volgen, om een degelijk verschil te zien in de route.
                                    mDistance /= deelFactor;
                                    onTime = false;
                                    break;
                                }
                            }
                        }
                        if (!onTime) break;
                    }
                    if (!onTime){
                        Toast.makeText(MapActivity.this, mDistance + "km to go", Toast.LENGTH_SHORT).show();
                        mDistance -= runningDistance;
                        mCalculator.StopTask();
                        mCalculator.CalculateTask(mDistance, temp);
                    }
                } else if (mDestination != null){
                    try {
                        mCalculator = new RouteCalculator(mDestination, mMap, getString(R.string.Maps_API_key), MapActivity.this);
                        mCalculator.CalculateTask(mDistance, new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()));
                        delay = 60000; // 1 minuut
                        mStartTime = SystemClock.elapsedRealtime();
                        Log.d(TAG, "run: Time Since Start = " + mStartTime + "ms");
                    } catch (Exception e) {
                        Log.d(TAG, "run: AsyncTest " + e.toString());
                    }
                }
                // Zet de timer
                if(!stop) timerHandler.postDelayed(updater, delay);
            }

            private boolean isOnRoute(com.google.maps.model.LatLng playerLocation,
                                      com.google.maps.model.LatLng startLocation,
                                      com.google.maps.model.LatLng endLocation){
                // Ziet of de huidige locatie op de route staat voor latitude
                if ((startLocation.lat - 0.001 < playerLocation.lat && playerLocation.lat < endLocation.lat + 0.001)
                        || (startLocation.lat + 0.001> playerLocation.lat && playerLocation.lat > endLocation.lat - 0.001)){

                    // Ziet of de huidige locatie op de route staat voor longitude
                    return (startLocation.lng - 0.001 < playerLocation.lng && playerLocation.lng < endLocation.lng + 0.001)
                            || (startLocation.lng + 0.001 > playerLocation.lng && playerLocation.lng > endLocation.lng - 0.001);
                }
                return false;
            }

            private boolean isOnTime(float runningdistance, float timeSinceCreate){
                // Mijn snelheid * De gemiddelde loopsnelheid = De afstand die ik afgelegd heb
                // Indien deze afstand tussen de afstanden van de route ligt,
                // dan ben ik nog op schema
                return runningdistance + 0.5 > timeSinceCreate * mRunSpeed;
            }
        };
        timerHandler.post(updater);
    }

    @Override
    protected void onDestroy() {
        stop = true;
        mCalculator.StopTask();
        timerHandler.removeCallbacksAndMessages(null);
        SystemClock.sleep(1000);
        super.onDestroy();
    }
}

/*
    LogCat Searches:
    RouteCalculator:
    run:
    AsyncTest
 */
