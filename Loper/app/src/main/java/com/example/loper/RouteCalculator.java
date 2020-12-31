package com.example.loper;

// AsyncTask: https://www.youtube.com/watch?v=uKx0FuVriqA
// Solution?: https://stackoverflow.com/questions/4080808/asynctask-doinbackground-does-not-run

import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.PendingResult;
import com.google.maps.internal.PolylineEncoding;
import com.google.maps.model.DirectionsLeg;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.TravelMode;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class RouteCalculator {

    // Constructor variabelen
    private Location mCurrentLocation;
    private LatLng mDestination;
    private GoogleMap mMap;
    private GeoApiContext mGeoApiContext;

    public RouteCalculator(Location currentLocation, LatLng destination, GoogleMap map,
                           String ApiKey){
        mCurrentLocation = currentLocation;
        mDestination = destination;
        mMap = map;
        // Instantieer de directions calculator
        mGeoApiContext = new GeoApiContext.Builder()
                .apiKey(ApiKey)
                .build();
        directionsList = generateDirections();
    }

    // Task Variabelen
    private String TAG = "RouteCalculator";
    public DirectionsRoute bestRoute = null;
    DirectionsTask directionsTask;
    private List<WindDirections[]> directionsList;

    public void CalculateTask(float distance){
        directionsTask = new DirectionsTask(this);
        directionsTask.execute(distance);
    }

    public void StopTask(){
        directionsTask.cancel(true);
        Log.d(TAG, "StopTask: Task is stopped");
    }

    private static class DirectionsTask extends AsyncTask<Float, Void, DirectionsRoute>{

        WeakReference<RouteCalculator> calculatorWeakReference;

        public DirectionsTask(RouteCalculator calculator){
            calculatorWeakReference = new WeakReference<RouteCalculator>(calculator);
        }

        float bestDistance = 0;
        boolean gotResult = false;
        boolean gotBestRoute = false;
        float distance;

        @Override
        protected DirectionsRoute doInBackground(Float... floats) {
            RouteCalculator calculator = calculatorWeakReference.get();
            distance = floats[0];
            Log.d(calculator.TAG, "doInBackground: Total distance: " + distance);
            bestDistance -= distance;
            Log.d(calculator.TAG, "doInBackground: Async starts");

            for (WindDirections[] windDirections: calculator.directionsList){
                Log.d(calculator.TAG, "doInBackgroundTest: Async working " + calculator.directionsList.indexOf(windDirections));
                if (isCancelled()) return null;
                DirectionsApiRequest directions = new DirectionsApiRequest(calculator.mGeoApiContext);
                com.google.maps.model.LatLng[] wayPoints = new com.google.maps.model.LatLng[3];
                gotResult = false;

                wayPoints[0] = calculator.calculateWaypoint(calculator.mCurrentLocation.getLatitude(),
                        calculator.mCurrentLocation.getLongitude(), windDirections[0], distance);
                wayPoints[1] = calculator.calculateWaypoint(wayPoints[0].lat, wayPoints[0].lng,
                        windDirections[1], distance);
                wayPoints[2] = calculator.calculateWaypoint(wayPoints[1].lat, wayPoints[1].lng,
                        windDirections[2], distance);

                directions.origin(new com.google.maps.model.LatLng(calculator.mCurrentLocation.getLatitude(),
                        calculator.mCurrentLocation.getLongitude()));
                directions.waypoints(wayPoints);
                directions.destination(new com.google.maps.model.LatLng(calculator.mDestination.latitude,
                        calculator.mDestination.longitude));
                directions.optimizeWaypoints(true);
                directions.mode(TravelMode.WALKING);
                directions.setCallback(new PendingResult.Callback<DirectionsResult>() {
                    @Override
                    public void onResult(DirectionsResult result) {
                        Log.d(calculator.TAG, "doInBackgroundTest: Async finished " + calculator.directionsList.indexOf(windDirections));
                        if (gotBestRoute) return;
                        float runningDistance = 0;
                        for (DirectionsLeg leg: result.routes[0].legs) {
                            String[] stringArray;
                            if(leg.distance.toString().contains(" km")) {
                                stringArray = leg.distance.toString().split(" km");
                                runningDistance += Float.parseFloat(stringArray[0]);
                            }
                            else if(leg.distance.toString().contains(" m")) {
                                stringArray = leg.distance.toString().split(" m");
                                runningDistance += Float.parseFloat(stringArray[0]) / 1000;
                            }
                            else if(leg.distance.toString().contains(" mi")) {
                                stringArray = leg.distance.toString().split(" mi");
                                runningDistance += Float.parseFloat(stringArray[0]) / 0.62137;
                            }
                            else if(leg.distance.toString().contains(" ft")) {
                                stringArray = leg.distance.toString().split(" ft");
                                runningDistance += Float.parseFloat(stringArray[0]) / 3280.8;
                            }
                        }
                        Log.d(calculator.TAG, "onResult: Total distance: " + runningDistance);
                        if(Math.abs(distance - runningDistance) < Math.abs(distance - bestDistance)){
                            calculator.bestRoute = result.routes[0];
                            bestDistance = runningDistance;
                            if (distance == runningDistance) gotBestRoute = true;
                        }
                        gotResult = true;
                    }

                    @Override
                    public void onFailure(Throwable e) {
                        Log.d(calculator.TAG, "doInBackgroundTest: Async failed " + calculator.directionsList.indexOf(windDirections));
                        Log.d(calculator.TAG, "onFailure: Failed to get directions: " + e.getMessage());
                        gotResult = true;
                    }
                });
                if (gotBestRoute)
                    return calculator.bestRoute;
                while (!gotResult);
            }
            return calculator.bestRoute;
        }

        @Override
        protected void onPostExecute(DirectionsRoute directionsRoute) {
            super.onPostExecute(directionsRoute);
            if (isCancelled()) return;
            RouteCalculator calculator = calculatorWeakReference.get();
            Log.d(calculator.TAG, "doInBackgroundTest: Async completed");

            // Gaat de route decoderen
            calculator.mMap.clear();
            calculator.addMarker(calculator.mDestination);

            Log.d(calculator.TAG, "onPostExecute: leg: " + directionsRoute.legs[0].toString());
            List<com.google.maps.model.LatLng> decodedPath =
                    PolylineEncoding.decode(directionsRoute.overviewPolyline.getEncodedPath());
            List<LatLng> newDecodedPath = new ArrayList<>();
            for (com.google.maps.model.LatLng latlng: decodedPath) {
                newDecodedPath.add(new LatLng(latlng.lat, latlng.lng));
            }

            Polyline polyline = calculator.mMap.addPolyline(new PolylineOptions().addAll(newDecodedPath));
            // polyline.setColor(ContextCompat.getColor("Activity", R.color.colorPrimaryDark));
            this.cancel(true);
        }
    }

    private void addMarker(LatLng latLng) {
        // Gaat een zekere zoom initalizeren op de coördinaten van de huidige positie
        Log.d(TAG, "moveCamera: moving the camera to lat: " + latLng.latitude +
                ", lng: " + latLng.longitude);
        // Marker zetten op de meegegeven plaats
        MarkerOptions options = new MarkerOptions()
                .position(latLng)
                .title("Destination");
        mMap.addMarker(options);
    }

    // Extra methodes
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
        }
        return new com.google.maps.model.LatLng(Latitude + calcLatitude,
                Longitude + calcLongitude);
            /*/ Voor enige extensies
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
}
