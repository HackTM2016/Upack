package ro.netex.upack;

import android.app.ListActivity;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.SyncStateContract;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

public class MapActivity extends AppCompatActivity
        implements OnMapReadyCallback {

    private GoogleMap mMap;
    public Toolbar toolbar;
    public NavigationView navigationView;
    public DrawerLayout drawer;
    public MenuActivity navigationMenu;
    public Menu menu;
    int currentItemId;
    AppController appController;
    JSONObject suppliers;
    UPackApi api;
    Context context;
    Location currentLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        RouteDrawer.MapContext = this;

        SupportMapFragment mapFragment = (SupportMapFragment) this.getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        this.context = this;

        // Application controller calls get_suppliers;
        UPackApi.activity = this.context;
        appController = new AppController(this);
        appController.getSuppliers();
        addNavigationToolbar();

    }

    public void addNavigationToolbar() {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        navigationView = (NavigationView) findViewById(R.id.nav_view);
        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
//        currentItemId = R.id.supplier;
        navigationMenu = new MenuActivity();
        navigationMenu.setContext(this);
        navigationMenu.setNavigationToolbar(navigationView, menu, toolbar, drawer, currentItemId);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.map, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        appController.getSuppliers();
        mMap = googleMap;
        mMap.getUiSettings().setMapToolbarEnabled(false);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
            zoomMapToCurrentLocation();
            getCurrentUserLocation();
        }


        mMap.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
            @Override
            public void onInfoWindowClick(Marker marker) {
                Intent intent = new Intent(MapActivity.this, PackageActivity.class);
                intent.putExtra("STATUS", "AVAILABLE");
                intent.putExtra("SUPPLIER_ID", marker.getSnippet());
                startActivity(intent);
            }
        });

    }

    public void populateMapWithSuppliers(JSONArray suppliers) {

        // parse suppliers list
        for (int i = 0; i < suppliers.length(); i++) {
            JSONObject jsonobject = null;
            JSONObject address = null;
            try {
                jsonobject = suppliers.getJSONObject(i);
                String name = jsonobject.getString("name");
                String id = jsonobject.getString("id");
                // get supplier's lat and lng
                address = jsonobject.getJSONObject("address");
                Double lat = address.getDouble("lat");
                Double lng = address.getDouble("lng");
                // Add a marker supplier coordinates on map
                LatLng supplierLat = new LatLng(lat, lng);
                mMap.addMarker(new MarkerOptions().position(supplierLat).title(name).snippet(id));
//                mMap.moveCamera(CameraUpdateFactory.newLatLng(supplierLat));

            } catch (JSONException e) {
                e.printStackTrace();
            }

        }
    }

    // set zoom to current user location
    public void zoomMapToCurrentLocation() {

        if (checkGPSStatus()) {
            JSONObject coordinates = getCurrentUserLocation();
            LatLng latLng = null;
            try {
                latLng = new LatLng(coordinates.getDouble("lat"), coordinates.getDouble("lng"));
                CameraUpdate center = CameraUpdateFactory.newLatLng(latLng);
                CameraUpdate zoom = CameraUpdateFactory.zoomTo(15);
                mMap.moveCamera(center);
                mMap.animateCamera(zoom);
            } catch (JSONException e) {
                e.printStackTrace();
            }

        } else {
            buildAlertMessageNoGps();
        }
    }

    // check if GPS is enable
    public boolean checkGPSStatus() {
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return false;
        }
        return true;


    }

    // show dialog where GPS is not enabled
    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        finish();
                        startActivity(getIntent());
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    // get user coordinates
    public JSONObject getCurrentUserLocation() {

        JSONObject coordinates = new JSONObject();

        LocationManager locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            double lat = location.getLatitude();
            double lng = location.getLongitude();
            try {
                coordinates.put("lat", lat);
                coordinates.put("lng", lng);

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return coordinates;
    }

    // set navigation route to map

    public void drawRootNavigation(int package_id) {
        RouteDrawer draw = new RouteDrawer(package_id);
        draw.setUserCurrentLocation(getCurrentUserLocation());
        draw.setGoogleMap(mMap);
        draw.drawRoute();

    }


}
