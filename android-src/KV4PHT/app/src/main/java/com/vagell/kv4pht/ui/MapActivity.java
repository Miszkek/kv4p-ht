package com.vagell.kv4pht.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.vagell.kv4pht.R;
import com.vagell.kv4pht.utils.BeaconLocationStore;
import com.vagell.kv4pht.utils.MapPrefs;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;

/**
 * MVP: Offline map screen using osmdroid.
 * - Shows user's current location.
 * - Displays last N received beacon locations and draws a track (polyline).
 * - Optional overlay: aprs_weather.xml (toggle).
 */
public class MapActivity extends AppCompatActivity {

    private static final int REQ_LOCATION = 2401;

    private MapView mapView;
    private MyLocationNewOverlay myLocationOverlay;
    private Marker myMarker;

    private BeaconLocationStore beaconStore;

    private final List<Marker> beaconMarkers = new ArrayList<>();
    private Polyline beaconTrack;

    private Spinner trailSpinner;
    private Spinner colorSpinner;
    private SwitchMaterial weatherSwitch;
    private View weatherOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // osmdroid requires this BEFORE MapView creation
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_map);

        setupBottomNav(); // Wywołaj nową metodę
        beaconStore = new BeaconLocationStore(this);

        // Keep the bottom nav consistent with MainActivity (voice/chat/map)
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.btnMap);
            bottomNav.setOnNavigationItemSelectedListener(item -> {
                int id = item.getItemId();

                if (id == R.id.btnMap) {
                    return true;
                }

                if (id == R.id.voice_mode || id == R.id.text_chat_mode) {
                    Intent intent = new Intent(MapActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                    return true;
                }

                return false;
            });
        }

        beaconStore = new BeaconLocationStore(this);

        mapView = findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);

        weatherOverlay = findViewById(R.id.aprsWeatherOverlay);
        weatherSwitch = findViewById(R.id.switchWeather);
        trailSpinner = findViewById(R.id.spinnerTrail);
        colorSpinner = findViewById(R.id.spinnerMyColor);

        setupSpinnersAndToggles();
        setupMyLocationOverlay();

        // Initial render
        applyWeatherVisibility();
        renderBeacons();


    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        if (bottomNav == null) return;

        bottomNav.setSelectedItemId(R.id.btnMap);

        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.btnMap) {
                return true;
            }

            if (id == R.id.voice_mode || id == R.id.text_chat_mode) {
                Intent intent = new Intent(MapActivity.this, MainActivity.class);
                // Jeśli chcesz otworzyć konkretny ekran, użyj prostego flagowania lub usuń parametry
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
                return true;
            }

            return false;
        });
    }

    private void setupSpinnersAndToggles() {
        // Trail count
        ArrayAdapter<CharSequence> trailAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.map_trail_counts,
                android.R.layout.simple_spinner_item
        );
        trailAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        trailSpinner.setAdapter(trailAdapter);

        int savedTrail = MapPrefs.getTrailCount(this);
        int trailIndex = MapPrefs.indexOfTrailCount(getResources().getStringArray(R.array.map_trail_counts), savedTrail);
        trailSpinner.setSelection(Math.max(trailIndex, 0));
        trailSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String v = parent.getItemAtPosition(position).toString();
                int n;
                try {
                    n = Integer.parseInt(v);
                } catch (Exception e) {
                    n = 10;
                }
                MapPrefs.setTrailCount(MapActivity.this, n);
                renderBeacons();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Marker color
        ArrayAdapter<CharSequence> colorAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.map_marker_colors,
                android.R.layout.simple_spinner_item
        );
        colorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        colorSpinner.setAdapter(colorAdapter);

        String savedColor = MapPrefs.getMyMarkerColor(this);
        int colorIndex = MapPrefs.indexOfString(getResources().getStringArray(R.array.map_marker_colors), savedColor);
        colorSpinner.setSelection(Math.max(colorIndex, 0));
        colorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String color = parent.getItemAtPosition(position).toString();
                MapPrefs.setMyMarkerColor(MapActivity.this, color);
                updateMyMarkerAppearance();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Weather overlay toggle
        weatherSwitch.setChecked(MapPrefs.getShowWeather(this));
        weatherSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            MapPrefs.setShowWeather(MapActivity.this, isChecked);
            applyWeatherVisibility();
        });
    }

    private void applyWeatherVisibility() {
        boolean show = MapPrefs.getShowWeather(this);
        weatherOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setupMyLocationOverlay() {
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        myLocationOverlay.enableMyLocation();
        mapView.getOverlays().add(myLocationOverlay);

        // Our own marker (for colored pin). We'll keep it updated when GPS changes.
        myMarker = new Marker(mapView);
        myMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        myMarker.setTitle(getString(R.string.map_me));
        mapView.getOverlays().add(myMarker);
        updateMyMarkerAppearance();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            return;
        }

        // Center map when we get first fix
        myLocationOverlay.runOnFirstFix(() -> runOnUiThread(() -> {
            GeoPoint me = myLocationOverlay.getMyLocation();
            if (me != null) {
                mapView.getController().setZoom(15.5);
                mapView.getController().setCenter(me);
                myMarker.setPosition(me);
                mapView.invalidate();
            }
        }));

        // Keep updating marker position
        myLocationOverlay.enableFollowLocation();
    }

    private void updateMyMarkerAppearance() {
        // Use a simple colored circle drawable.
        String colorName = MapPrefs.getMyMarkerColor(this);
        int drawableRes = MapPrefs.resolveMarkerDrawable(colorName);
        myMarker.setIcon(ContextCompat.getDrawable(this, drawableRes));
        mapView.invalidate();
    }

    private void clearBeaconOverlays() {
        for (Marker m : beaconMarkers) {
            mapView.getOverlays().remove(m);
        }
        beaconMarkers.clear();

        if (beaconTrack != null) {
            mapView.getOverlays().remove(beaconTrack);
            beaconTrack = null;
        }
    }

    private void renderBeacons() {
        if (mapView == null) return;

        clearBeaconOverlays();

        int limit = MapPrefs.getTrailCount(this);
        List<BeaconLocationStore.BeaconPoint> pts = beaconStore.getLastPoints(limit);

        if (pts.isEmpty()) {
            mapView.invalidate();
            return;
        }

        // Markers
        for (BeaconLocationStore.BeaconPoint p : pts) {
            Marker m = new Marker(mapView);
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setPosition(new GeoPoint(p.lat, p.lon));
            m.setTitle(p.sender == null ? getString(R.string.map_beacon) : p.sender);
            m.setSubDescription(p.receivedAtString());
            m.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_beacon_pin));
            beaconMarkers.add(m);
            mapView.getOverlays().add(m);
        }

        // Track polyline (in time order)
        beaconTrack = new Polyline();
        List<GeoPoint> geoPoints = new ArrayList<>();
        for (BeaconLocationStore.BeaconPoint p : pts) {
            geoPoints.add(new GeoPoint(p.lat, p.lon));
        }
        beaconTrack.setPoints(geoPoints);
        mapView.getOverlays().add(beaconTrack);

        // Center on newest point
        BeaconLocationStore.BeaconPoint last = pts.get(0); // store returns newest-first
        mapView.getController().setZoom(14.5);
        mapView.getController().setCenter(new GeoPoint(last.lat, last.lon));

        mapView.invalidate();
    }

    private void openMain(String startScreen) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        // Jeśli MainActivity nie ma obsługi EXTRA_START_SCREEN, możesz to na razie zakomentować
        // intent.putExtra("EXTRA_START_SCREEN", startScreen);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
        renderBeacons();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupMyLocationOverlay();
            }
        }
    }
}
