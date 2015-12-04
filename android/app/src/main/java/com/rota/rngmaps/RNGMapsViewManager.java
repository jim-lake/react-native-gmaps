
package com.rota.rngmaps;

import android.content.Context;
import android.location.Criteria;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.location.Location;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.uimanager.ReactProp;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.UIProp;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by Henry on 08/10/2015.
 */

public class RNGMapsViewManager extends SimpleViewManager<MapView>
        implements OnMapReadyCallback, LifecycleEventListener, LocationListener,
        GoogleMap.OnCameraChangeListener, GoogleMap.OnInfoWindowClickListener {
    public static final String REACT_CLASS = "RNGMapsViewManager";

    private LocationManager locationManager;
    private String locationProvider;
    private MapView mView;
    private GoogleMap map;
    private ReactContext reactContext;
    private int zoomLevel = -1;
    private ArrayList<Marker> mapMarkers = new ArrayList<Marker>();
    private HashMap<String,String> markerIdMap = new HashMap<String, String>();

    private boolean centerNextLocationFix = false;
    private boolean zoomNeedsUpdate = false;

    @UIProp(UIProp.Type.MAP)
    public static final String PROP_CENTER = "center";

    @UIProp(UIProp.Type.NUMBER)
    public static final String PROP_ZOOM_LEVEL = "zoomLevel";

    @UIProp(UIProp.Type.ARRAY)
    public static final String PROP_MARKERS = "markers";

    @UIProp(UIProp.Type.BOOLEAN)
    public static final String PROP_ZOOM_ON_MARKERS = "zoomOnMarkers";

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    public GoogleMap getMap() {
        return map;
    }
    @Override
    protected MapView createViewInstance(ThemedReactContext context) {
        reactContext = context;
        locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
        final Criteria criteria = new Criteria();
        locationProvider = locationManager.getBestProvider(criteria, false);
        locationManager.requestLocationUpdates(locationProvider,400,1,this);

        context.addLifecycleEventListener(this);

        mView = new MapView(context);
        mView.onCreate(null);
        mView.onResume();
        mView.getMapAsync(this);
        return mView;
    }
    @Override
    public void onHostResume() {
        if (map != null) map.setMyLocationEnabled(true);
        if (locationManager != null) locationManager.requestLocationUpdates(locationProvider, 400, 1, this);
    }
    @Override
    public void onHostPause() {
        if (map != null) map.setMyLocationEnabled(false);
        if (locationManager != null) locationManager.removeUpdates(this);
    }
    @Override
    public void onHostDestroy() {
    }
    @Override
    public void onLocationChanged(final Location location) {
        updateCamera(location);
    }
    @Override
    public void onStatusChanged(final String provider, final int status, final Bundle extras) {
    }
    @Override
    public void onProviderEnabled(final String provider) {
    }
    @Override
    public void onProviderDisabled(final String provider) {
    }

    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        if (map == null) {
            sendMapError("Map is null", "map_null");
        } else {
            map.getUiSettings().setMyLocationButtonEnabled(false);

            try {
                MapsInitializer.initialize(reactContext.getApplicationContext());
                map.setOnCameraChangeListener(this);
            } catch (Exception e) {
                e.printStackTrace();
                sendMapError("Map initialize error", "map_init_error");
            }

            map.setMyLocationEnabled(true);
            map.setOnInfoWindowClickListener(this);
            updateCamera(null);
            Log.i("Foo","Map Ready");
        }
    }

    private void sendMapError(String message, String type) {
      WritableMap error = Arguments.createMap();
      error.putString("message", message);
      error.putString("type", type);

      reactContext
              .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
              .emit("mapError", error);
    }

    @Override
    public void onCameraChange(CameraPosition position) {
        WritableMap params = Arguments.createMap();
        WritableMap latLng = Arguments.createMap();
        latLng.putDouble("latitude", position.target.latitude);
        latLng.putDouble("longitude", position.target.longitude);

        params.putMap("latLng", latLng);
        params.putDouble("zoomLevel", position.zoom);

        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("mapChange", params);
    }

    @ReactProp(name = "center")
    public void setCenter(final MapView view,ReadableMap region) {
        try {
            Double lng = region.getDouble("longitude");
            Double lat = region.getDouble("latitude");

            map.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(lat,lng)));
        } catch (Exception e) {
        }
    }

    @ReactProp(name = "markers")
    public void setMarkers(MapView view,ReadableArray markers) {
        try {
            // First clear all markers from the map
            for (Marker marker: mapMarkers) {
                marker.remove();
            }
            mapMarkers.clear();

            // All markers to map
            for (int i = 0; i < markers.size(); i++) {
                final ReadableMap config = markers.getMap(i);
                mapMarkers.add(addMarker(config));
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
    public Marker addMarker(final ReadableMap config) {
        final MarkerOptions options = new MarkerOptions();
        options.position(new LatLng(config.getDouble("latitude"), config.getDouble("longitude")));
        if(config.hasKey("title")) {
            options.title(config.getString("title"));
        }
        if (config.hasKey("icon")) {
            options.icon(BitmapDescriptorFactory.fromAsset(config.getString("icon")));
        }
        if (config.hasKey("anchor")) {
            ReadableArray anchor = config.getArray("anchor");
            options.anchor((float)anchor.getDouble(0), (float)anchor.getDouble(1));
        }

        final Marker marker = map.addMarker(options);
        markerIdMap.put(marker.getId(),config.getString("id"));
        return marker;
    }
    @Override
    public void onInfoWindowClick(final Marker marker) {
        final String id = markerIdMap.get(marker.getId());
        if (id != null) {
            WritableMap params = Arguments.createMap();
            params.putString("id",id);
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("markerClick",params);
        }
    }

    @ReactProp(name = "centerNextLocationFix")
    public void setCenterNextFix(final MapView view,final boolean center) {
        this.centerNextLocationFix = center;
        updateCamera(null);
    }

    private void updateCamera(Location location) {
        if (map != null && (this.centerNextLocationFix || this.zoomNeedsUpdate)) {
            if (this.centerNextLocationFix) {
                if (location == null) {
                    try {
                        location = map.getMyLocation();
                    } catch(java.lang.IllegalStateException e) {
                        // Ignore
                    }
                }
                if (location == null) {
                    location = locationManager.getLastKnownLocation(locationProvider);
                }
            } else {
                location = null;
            }
            if (location != null && this.zoomNeedsUpdate) {
                final LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoomLevel));
                this.centerNextLocationFix = false;
            } else if (location != null) {
                final LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());
                map.animateCamera(CameraUpdateFactory.newLatLng(latLng));
                this.centerNextLocationFix = false;
            } else if (this.zoomNeedsUpdate) {
                map.moveCamera(CameraUpdateFactory.zoomTo(zoomLevel));
            }
            this.zoomNeedsUpdate = false;
        }

    }

    @ReactProp(name = "zoomLevel")
    public void setZoomLevel(final MapView view,final int zoom) {
        zoomLevel = zoom;
        zoomNeedsUpdate = true;
        updateCamera(null);
    }
}
