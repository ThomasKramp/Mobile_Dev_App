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
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.Task;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.PendingResult;
import com.google.maps.internal.PolylineEncoding;
import com.google.maps.model.DirectionsLeg;
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

    private boolean mLocationPermissionGranted = false;
    private float mDefaultZoom = 15f;
    private double mLatitude;
    private double mLongitude;
    private float mDistance; // distance in km
    private float mTime; // time in minutes
    private float mRunSpeed = 12 / 60; // speed in km/min (km/h / 60 = km/min)

    private long mStartTime;
    private float mRunTime;

    private Location mCurrenLocation;
    private LatLng mDestination;
    private GeoApiContext mGeoApiContext = null;

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
            // Gaat de default show location knop weghalen
            // Dit is omdat je de default knop niet kunt verplaatsen
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
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
            mCurrenLocation = (Location) task.getResult();
            if (mLatitude != 0 || mLongitude != 0) {
                mDestination = new LatLng(mLatitude, mLongitude);
            } else {
                mDestination = new LatLng(mCurrenLocation.getLatitude(), mCurrenLocation.getLongitude());
            }
            moveCamera(mDestination, mDefaultZoom, "My Destination");
            calculateDirections(mDestination);
            mStartTime = SystemClock.elapsedRealtime();
        } else {
            Log.d(TAG, "onComplete: current location is null");
        }
    }

    private void moveCamera(LatLng latLng, float zoom, String title) {
        // Gaat een zekere zoom initalizeren op de coördinaten van de huidige positie
        Log.d(TAG, "moveCamera: moving the camera to lat: " + latLng.latitude +
                ", lng: " + latLng.longitude);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));
        // Marker zetten op de meegegeven plaats
        MarkerOptions options = new MarkerOptions()
                .position(latLng)
                .title(title);
        mMap.addMarker(options);
    }

    private void calculateDirections(LatLng destination) {
        DirectionsApiRequest directions = new DirectionsApiRequest(mGeoApiContext);

        generateBestDirection(destination);

        com.google.maps.model.LatLng[] wayPoints = new com.google.maps.model.LatLng[3];
        wayPoints[0] = calculateWaypoint(mCurrenLocation.getLatitude(), mCurrenLocation.getLongitude(),
                bestDirections[0]);
        wayPoints[1] = calculateWaypoint(wayPoints[0].lat, wayPoints[0].lng, bestDirections[1]);
        wayPoints[2] = calculateWaypoint(wayPoints[1].lat, wayPoints[1].lng, bestDirections[2]);

        // StartPunt toevoegen
        directions.origin(new com.google.maps.model.LatLng(mCurrenLocation.getLatitude(), mCurrenLocation.getLongitude()));
        // Middelpunten toevoegen
        directions.waypoints(wayPoints);
        // EindPunt toevoegen
        directions.destination(new com.google.maps.model.LatLng(destination.latitude, destination.longitude));
        directions.optimizeWaypoints(true);
        directions.mode(TravelMode.WALKING);
        directions.setCallback(new PendingResult.Callback<DirectionsResult>() {
            @Override
            public void onResult(DirectionsResult result) {
                Log.d(TAG, "calculateDirections: onResult: routes: " + result.routes[0].legs[0].toString());
                bestRoute = result.routes[0];
                for (DirectionsLeg leg: result.routes[0].legs) {
                    Log.d(TAG, "calculateDirections: onResult: duration: " + leg.duration);
                    Log.d(TAG, "calculateDirections: onResult: distance: " + leg.distance);
                }
                addPolylines(result);
            }

            @Override
            public void onFailure(Throwable e) {
                Log.d(TAG, "calculateDirections: onFailure: Failed to get directions: " + e.getMessage());
            }
        });
    }

    private float bestDistance = 0;
    private WindDirections[] bestDirections = new WindDirections[3];
    private boolean gotResult = false;
    private com.google.maps.model.DirectionsRoute bestRoute = null;

    private void generateBestDirection(LatLng destination) {
        List<WindDirections[]> directionsList = generateDirections();
        bestDistance -= mDistance;

        for (WindDirections[] windDirections: directionsList){
            DirectionsApiRequest directions = new DirectionsApiRequest(mGeoApiContext);
            com.google.maps.model.LatLng[] wayPoints = new com.google.maps.model.LatLng[3];
            gotResult = false;

            wayPoints[0] = calculateWaypoint(mCurrenLocation.getLatitude(), mCurrenLocation.getLongitude(),
                    windDirections[0]);
            wayPoints[1] = calculateWaypoint(wayPoints[0].lat, wayPoints[0].lng, windDirections[1]);
            wayPoints[2] = calculateWaypoint(wayPoints[1].lat, wayPoints[1].lng, windDirections[2]);

            directions.origin(new com.google.maps.model.LatLng(mCurrenLocation.getLatitude(), mCurrenLocation.getLongitude()));
            directions.waypoints(wayPoints);
            directions.destination(new com.google.maps.model.LatLng(destination.latitude, destination.longitude));
            directions.optimizeWaypoints(true);
            directions.mode(TravelMode.WALKING);
            directions.setCallback(new PendingResult.Callback<DirectionsResult>() {
                @Override
                public void onResult(DirectionsResult result) {
                    float runningDistance = 0;
                    for (DirectionsLeg leg: result.routes[0].legs) {
                        String[] stringArray = new String[2];
                        if(leg.distance.toString().contains(" mi")) stringArray = leg.distance.toString().split(" mi");
                        if(leg.distance.toString().contains(" km")) stringArray = leg.distance.toString().split(" km");
                        runningDistance += Float.parseFloat(stringArray[0]);
                    }
                    Log.d(TAG, "calculateDirections: onResult: Total distance: " + runningDistance);
                    if(Math.abs(mDistance - runningDistance) < Math.abs(mDistance - bestDistance)){
                        bestDirections = windDirections;
                        bestDistance = runningDistance;
                    }
                    gotResult = true;
                }

                @Override
                public void onFailure(Throwable e) {
                    Log.d(TAG, "calculateDirections: onFailure: Failed to get directions: " + e.getMessage());
                    gotResult = true;
                }
            });
            while (!gotResult);
        }
    }

    private List<WindDirections[]> generateDirections(){
        List<WindDirections[]> directionsList = new ArrayList<>();
        // Elke wind richting mag 1 keer voorkomen
        // De wind richting mag niet gevolgd worden door zijn tegengestelde
        for (WindDirections firstValue: WindDirections.values()){

            for (WindDirections secondValue: WindDirections.values()){
                if (firstValue != secondValue && !AreOppositeDirections(firstValue, secondValue)){

                    for (WindDirections thirdValue: WindDirections.values()){
                        if (firstValue != thirdValue && secondValue != thirdValue
                                && !AreOppositeDirections(secondValue, thirdValue)){

                            directionsList.add(new WindDirections[]
                                    {firstValue, secondValue, thirdValue});
                        }
                    }
                }
            }
        }
        return directionsList;
    }

    private boolean AreOppositeDirections(WindDirections directionsOne, WindDirections directionsTwo){
        return (directionsOne == WindDirections.North && directionsTwo == WindDirections.South)
                || (directionsOne == WindDirections.East && directionsTwo == WindDirections.West)
                || (directionsOne == WindDirections.South && directionsTwo == WindDirections.North)
                || (directionsOne == WindDirections.West && directionsTwo == WindDirections.East);
    }

    private com.google.maps.model.LatLng calculateWaypoint(Double Latitude, Double Longitude,
                                                           WindDirections direction){
        /*/
        North & South = Latitude
        Latitude: 1 deg = 110.574 km
        East & West = Longitude
        Longitude: 1 deg = 111.320*cos(latitude) km
        /*/
        double calcLatitude = 0;
        double calcLongitude = 0;
        switch (direction) {
            case North:
                calcLatitude = mDistance / (4 * 110.574);
                break;
            case East:
                calcLongitude = mDistance / (4 * 111.320 * Math.cos(calcLatitude));
                break;
            case South:
                calcLatitude = (mDistance / (4 * 110.574)) * -1;
                break;
            case West:
                calcLongitude = (mDistance / (4 * 111.320 * Math.cos(calcLatitude))) * -1;
                break;
            /*/
            case North_East:
                calcLatitude = Distance / (4 * 110.574);
                calcLongitude = Distance / (4 * 111.320 * Math.cos(calcLatitude));
                break;
            case South_East:
                calcLatitude = (Distance / (4 * 110.574)) * -1;
                calcLongitude = Distance / (4 * 111.320 * Math.cos(calcLatitude));
                break;
            case South_West:
                calcLatitude = (Distance / (4 * 110.574)) * -1;
                calcLongitude = (Distance / (4 * 111.320 * Math.cos(calcLatitude))) * -1;
                break;
            case North_West:
                calcLatitude = Distance / (4 * 110.574);
                calcLongitude = (Distance / (4 * 111.320 * Math.cos(calcLatitude))) * -1;
                break;
                /*/
        }
        return new com.google.maps.model.LatLng(Latitude + calcLatitude,
                Longitude + calcLongitude);
    }

    private void addPolylines(DirectionsResult result) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Gaat de route decoderen
                DirectionsRoute[] route = result.routes;
                Log.d(TAG, "run: leg: " + route[0].legs[0].toString());
                List<com.google.maps.model.LatLng> decodedPath = PolylineEncoding.decode(route[0].overviewPolyline.getEncodedPath());
                List<LatLng> newDecodedPath = new ArrayList<>();
                for (com.google.maps.model.LatLng latlng: decodedPath) {
                    newDecodedPath.add(new LatLng(latlng.lat, latlng.lng));
                }
                Polyline polyline = mMap.addPolyline(new PolylineOptions().addAll(newDecodedPath));
                // polyline.setColor(ContextCompat.getColor("Activity", R.color.colorPrimaryDark));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        //update();
    }

    private Runnable updater;
    private void update(){
        if (mStartTime != 0){
            final Handler timerHandler = new Handler();
            Log.d(TAG, "onComplete: Time Since Start = " + mStartTime + "ms");

            updater = new Runnable() {
                @Override
                public void run() {
                    int runningDistance = 0;
                    mRunTime = (float) ((SystemClock.elapsedRealtime() - mStartTime) / (60 * 1000));
                    Log.d(TAG, "onComplete: Time Since Start = " + mRunTime + "min");

                    for (DirectionsLeg leg: bestRoute.legs) {
                        String[] stringArray = new String[2];
                        if(leg.distance.toString().contains(" mi")) stringArray = leg.distance.toString().split(" mi");
                        if(leg.distance.toString().contains(" km")) stringArray = leg.distance.toString().split(" km");

                        Log.d(TAG, "onResult: RouteStart: " + leg.startLocation);
                        Log.d(TAG, "onResult: RouteEnd: " + leg.endLocation);

                        // Kijkt tussen welke 2 punten dat de user staat
                        if (isOnRoute(new com.google.maps.model.LatLng(mCurrenLocation.getLatitude(),
                                mCurrenLocation.getLongitude()), leg.startLocation, leg.endLocation)){
                            // Kijkt of de gebruiker nog op tijd is
                            if (!isOnTime(runningDistance, runningDistance
                                    + Float.parseFloat(stringArray[0]), mRunTime)){
                                // Indien de gebruiker niet op tijd is zal de applicatie
                                // een nieuwe route maken
                                calculateDirections(mDestination);
                                mStartTime = SystemClock.elapsedRealtime();
                            }
                        }
                        runningDistance += Float.parseFloat(stringArray[0]);
                    }
                    // Elke minuut wordt dit nagekeken
                    timerHandler.postDelayed(updater,60 * 1000);
                }
                private boolean isOnRoute(com.google.maps.model.LatLng playerLocation,
                                          com.google.maps.model.LatLng startLocation,
                                          com.google.maps.model.LatLng endLocation){
                    // Ziet of de huidige locatie op de route staat voor latitude
                    if ((startLocation.lat < playerLocation.lat && playerLocation.lat < endLocation.lat)
                            || (startLocation.lat > playerLocation.lat && playerLocation.lat > endLocation.lat)){

                        // Ziet of de huidige locatie op de route staat voor longitude
                        return (startLocation.lng < playerLocation.lng && playerLocation.lng < endLocation.lng)
                                || (startLocation.lng > playerLocation.lng && playerLocation.lng > endLocation.lng);
                    }
                    return false;
                }

                private boolean isOnTime(float beginDistanceRoute, float endDistanceRoute,
                                         float timeSinceCreate){
                    // Mijn snelheid * De gemiddelde loopsnelheid = De afstand die ik afgelegd heb
                    // Indien deze afstand tussen de afstanden van de route ligt,
                    // dan ben ik nog op schema
                    return beginDistanceRoute <= timeSinceCreate * mRunSpeed
                            && timeSinceCreate * mRunSpeed <= endDistanceRoute;
                }
            };
            timerHandler.post(updater);
        }
    }
}