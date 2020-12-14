package com.example.loper;

import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.PendingResult;
import com.google.maps.model.DirectionsLeg;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.TravelMode;

import java.util.ArrayList;
import java.util.List;

public class RouteCalculator {

    private Location mCurrentLocation;
    private LatLng mDestination = null;
    private GeoApiContext mGeoApiContext = null;
    private GoogleMap mMap;

    public RouteCalculator(){

    }

    public void CalculateTask(float mDistance){
        DirectionsTask directionsTask = new DirectionsTask();
        directionsTask.execute(mDistance);
    }

    private String TAG = "MapActivity";
    private float bestDistance = 0;
    private WindDirections[] bestDirections = new WindDirections[3];
    private boolean gotResult = false;
    private com.google.maps.model.DirectionsRoute bestRoute = null;

    private void calculateDirections(Float distance) {
        DirectionsApiRequest directions = new DirectionsApiRequest(mGeoApiContext);
        com.google.maps.model.LatLng[] wayPoints = new com.google.maps.model.LatLng[3];
        wayPoints[0] = calculateWaypoint(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude(),
                bestDirections[0], distance);
        wayPoints[1] = calculateWaypoint(wayPoints[0].lat, wayPoints[0].lng, bestDirections[1], distance);
        wayPoints[2] = calculateWaypoint(wayPoints[1].lat, wayPoints[1].lng, bestDirections[2], distance);

        // StartPunt toevoegen
        directions.origin(new com.google.maps.model.LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()));
        // Middelpunten toevoegen
        directions.waypoints(wayPoints);
        // EindPunt toevoegen
        directions.destination(new com.google.maps.model.LatLng(mDestination.latitude, mDestination.longitude));
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
                //addPolylines(result);
            }

            @Override
            public void onFailure(Throwable e) {
                Log.d(TAG, "calculateDirections: onFailure: Failed to get directions: " + e.getMessage());
            }
        });
    }

    private List<WindDirections[]> generateDirections(float distance){
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
                                                           WindDirections direction, Float distance){
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
                calcLatitude = distance / (4 * 110.574);
                break;
            case East:
                calcLongitude = distance / (4 * 111.320 * Math.cos(calcLatitude));
                break;
            case South:
                calcLatitude = (distance / (4 * 110.574)) * -1;
                break;
            case West:
                calcLongitude = (distance / (4 * 111.320 * Math.cos(calcLatitude))) * -1;
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
    /*/
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
    /*/
    private class DirectionsTask extends AsyncTask<Float, Void, Void>{

        private float distance;
        @Override
        protected Void doInBackground(Float... floats) {
            distance = floats[0];
            List<WindDirections[]> directionsList = generateDirections(distance);
            bestDistance -= distance;

            for (WindDirections[] windDirections: directionsList){
                DirectionsApiRequest directions = new DirectionsApiRequest(mGeoApiContext);
                com.google.maps.model.LatLng[] wayPoints = new com.google.maps.model.LatLng[3];
                gotResult = false;

                wayPoints[0] = calculateWaypoint(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude(),
                        windDirections[0], distance);
                wayPoints[1] = calculateWaypoint(wayPoints[0].lat, wayPoints[0].lng, windDirections[1], distance);
                wayPoints[2] = calculateWaypoint(wayPoints[1].lat, wayPoints[1].lng, windDirections[2], distance);

                directions.origin(new com.google.maps.model.LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()));
                directions.waypoints(wayPoints);
                directions.destination(new com.google.maps.model.LatLng(mDestination.latitude, mDestination.longitude));
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
                        if(Math.abs(distance - runningDistance) < Math.abs(distance - bestDistance)){
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
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            generateDirections(distance);
        }
    }
}
