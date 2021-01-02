package com.example.loper;

// Searchbar: (CodingWithMich)
// Google Maps & Google Places Android Course (Epsiode 8 - 9)
// https://www.youtube.com/playlist?list=PLgCYzUzKIBE-vInwQhGSdnbyJ62nixHCt

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.ViewPager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.Status;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.android.material.tabs.TabLayout;

import java.util.Arrays;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int ERROR_DIALOG_REQUEST = 9001;
    private Place mPlace;

    // Widgets
    private Button button_map;
    private AutocompleteSupportFragment autocompleteFragment;

    private ImageView mGPS;
    private boolean GPSLocation;

    private TabLayout tabLayout;
    private ViewPager viewPager;
    private DistanceTab distanceTab;
    private TimeTab timeTab;
    private float distance;
    private float time;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Componenten linken
        button_map = findViewById(R.id.button_map);
        CreateTabLayout();
        mGPS = findViewById(R.id.button_gps);
        autocompleteFragment = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.autocomplete_fragment);

        InitializePlaces();
    }

    private void CreateTabLayout() {
        // Tab bar maken en vullen
        tabLayout = findViewById(R.id.tab_layout);
        tabLayout.addTab(tabLayout.newTab().setText(R.string.distance_tab));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.time_tab));
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);
        // Waarom geeft dit een foutmelding
        tabLayout.setSelectedTabIndicatorColor(R.color.colorPrimaryDark);

        // Iedere tab een fragment toedienen
        viewPager = findViewById(R.id.pager);
        distanceTab = new DistanceTab();
        timeTab = new TimeTab();
        PagerAdapter adapter = new PagerAdapter(getSupportFragmentManager(), distanceTab, timeTab);
        viewPager.setAdapter(adapter);

        // Click listener toevoegen
        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }
            @Override
            public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    public void InitializeRoute(View view) {
        Intent intent = new Intent(MainActivity.this, MapActivity.class);
        intent.putExtra("GPSLocation", GPSLocation);
        GPSLocation = false;
        if (mPlace != null){
            intent.putExtra("Latitude", mPlace.getLatLng().latitude);
            intent.putExtra("Longitude", mPlace.getLatLng().longitude);
        }
        // Hoe dan ook, als het input veld leeg staat, dan krijg ik een foutmelding
        switch (viewPager.getCurrentItem()){
            case 0:
                try {
                    distance = distanceTab.getDistance();
                    Log.d(TAG, "Given distance in km: " + distance);
                    intent.putExtra("Distance", distance);
                } catch (Exception e) {
                    Log.d(TAG, "Foutmelding: " + e.toString());
                }
                break;
            case 1:
                try {
                    time = timeTab.getTime();
                    Log.d(TAG, "Given time in minutes: " + time);
                    intent.putExtra("Time", time);
                } catch (Exception e) {
                    Log.d(TAG, "Foutmelding: " + e.toString());
                }
                break;
        }
        try {
            if (IsServiceOK()){
                startActivity(intent);
            }
        }catch (Exception e){
            Log.e(TAG, "InitializeRoute: Exception ", e);
        }
    }

    private void InitializePlaces() {
        // Initialize Places.
        // Kan geen gebruik maken van "@string/Maps_API_key"
        Places.initialize(getApplicationContext(), "AIzaSyCxVkfBeDd-mBiG-WCzE8481RwBjVqKQPA");
        // Create a new Places client instance.
        PlacesClient placesClient = Places.createClient(getApplicationContext());

        // Gaat de Naam, ID en LAT-LNG van de ingegeven locatie meegeven
        autocompleteFragment.setPlaceFields(Arrays.asList(Place.Field.NAME,
                Place.Field.ID, Place.Field.LAT_LNG));

        // Set up a PlaceSelectionListener to handle the response.
        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(@NonNull Place place) {
                Log.d(TAG, "Place: " + place.getName() + ", " + place.getId()
                        + ", " + place.getLatLng().toString());
                mPlace = place;
            }

            @Override
            public void onError(@NonNull Status status) {
                Log.d(TAG, "An error occurred: " + status);
            }
        });
    }

    public void GetMyLocation(View view) {
        Log.d(TAG, "onClick: Clicked GPS icon");
        GPSLocation = true;
        Toast.makeText(this, "Huidige locatie is gekozen"
                , Toast.LENGTH_SHORT).show();
    }

    public boolean IsServiceOK(){
        Log.d(TAG, "isServicesOK: checking google services version");

        int available = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(MainActivity.this);

        if (available == ConnectionResult.SUCCESS){
            // Alles is in orde
            Log.d(TAG, "isServicesOK: Google Play Services is working");
            return true;
        } else if (GoogleApiAvailability.getInstance().isUserResolvableError(available)){
            // Er is iets mis maar het kan opgelost worden
            Log.d(TAG, "isServicesOK: an error occured but we can fix it");
            // De error wordt opgenomen vanuit google
            Dialog dialog = GoogleApiAvailability.getInstance().getErrorDialog(MainActivity.this, available, ERROR_DIALOG_REQUEST);
            // Een dialog wordt gegeven om de error op te lossen
            dialog.show();

        } else {
            Toast.makeText(this, "You can't make map requests", Toast.LENGTH_SHORT).show();
        }
        return false;
    }
}