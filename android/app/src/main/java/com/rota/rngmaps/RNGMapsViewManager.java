
package com.rota.rngmaps;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
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
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
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
    private float zoomLevel = -1;
    private ArrayList<Marker> mapMarkers = new ArrayList<Marker>();
    private HashMap<String,String> markerIdMap = new HashMap<String, String>();
    private int mapPaddingLeft = 0;
    private int mapPaddingRight = 0;
    private int mapPaddingTop = 0;
    private int mapPaddingBottom = 0;

    private boolean centerNextLocationFix = false;
    private boolean zoomNeedsUpdate = false;
    private boolean showMyLocationButton = false;

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
    protected MapView createViewInstance(final ThemedReactContext context) {
        reactContext = context;

        final GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        final int resultCode = apiAvailability.isGooglePlayServicesAvailable(context);
        if (resultCode != ConnectionResult.SUCCESS) {
            Context c = context;
            while (c != null
                && c instanceof ContextWrapper
                && !(c instanceof Activity)) {
                c = ((ContextWrapper)c).getBaseContext();
            }
            final Dialog dialog = apiAvailability.getErrorDialog((Activity)c,resultCode, 69);
            if (dialog != null) {
                dialog.show();
            }
        }
        locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
        final Criteria criteria = new Criteria();
        locationProvider = locationManager.getBestProvider(criteria, false);
        if (locationManager != null && locationProvider != null) locationManager.requestLocationUpdates(locationProvider, 400, 1, this);

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
        if (locationManager != null && locationProvider != null) locationManager.requestLocationUpdates(locationProvider, 400, 1, this);
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
            try {
                MapsInitializer.initialize(reactContext.getApplicationContext());
                final LatLng loc = new LatLng(34.0625,-118.2448);
                final CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(loc,10);
                map.moveCamera(cameraUpdate);
                final UiSettings uiSettings = map.getUiSettings();
                uiSettings.setRotateGesturesEnabled(false);
                uiSettings.setMapToolbarEnabled(false);
                uiSettings.setTiltGesturesEnabled(false);
                uiSettings.setMyLocationButtonEnabled(this.showMyLocationButton);

                map.setOnCameraChangeListener(this);
                map.setMyLocationEnabled(true);
                map.setOnInfoWindowClickListener(this);
            } catch (Exception e) {
                e.printStackTrace();
                sendMapError("Map initialize error", "map_init_error");
            }
            updateMapPadding();
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
    public void onCameraChange(final CameraPosition position) {
        WritableMap params = Arguments.createMap();
        params.putDouble("latitude", position.target.latitude);
        params.putDouble("longitude", position.target.longitude);
        params.putDouble("zoomLevel", position.zoom);

        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("mapChange", params);
    }

    @ReactProp(name = "center")
    public void setCenter(final MapView view,ReadableMap region) {
        Log.i("Map", "setCenter: region:" + region);
        if (region != null) {
            try {
                final Double lng = region.getDouble("longitude");
                final Double lat = region.getDouble("latitude");
                CameraUpdate update;
                if (region.hasKey("zoomLevel")) {
                    final Double zoomLevel = region.getDouble("zoomLevel");
                    update = CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), zoomLevel.floatValue());
                } else {
                    update = CameraUpdateFactory.newLatLng(new LatLng(lat, lng));
                }
                Log.i("Map", "setCenter: animateCamera:" + update);
                map.animateCamera(update);
            } catch (Exception e) {
                Log.e("Map", "Failed to update center: " + e);
            }
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
        markerIdMap.put(marker.getId(), config.getString("id"));
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
                .emit("markerClick", params);
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
                if (location == null && locationProvider != null) {
                    location = locationManager.getLastKnownLocation(locationProvider);
                }
            } else {
                location = null;
            }
            if (location != null && this.zoomNeedsUpdate) {
                final LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());
                final CameraUpdate update = CameraUpdateFactory.newLatLngZoom(latLng, zoomLevel);
                Log.i("Map", "updateCamera: animateCamera:" + update);
                map.animateCamera(update);
                this.centerNextLocationFix = false;
            } else if (location != null) {
                final LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());
                final CameraUpdate update = CameraUpdateFactory.newLatLngZoom(latLng, zoomLevel);
                Log.i("Map", "updateCamera: animateCamera:" + update);
                map.animateCamera(update);
                this.centerNextLocationFix = false;
            } else if (this.zoomNeedsUpdate) {
                final CameraUpdate update = CameraUpdateFactory.zoomTo(zoomLevel);
                Log.i("Map", "updateCamera: moveCamera:" + update);
                map.moveCamera(update);
            }
            this.zoomNeedsUpdate = false;
        }

    }

    @ReactProp(name = "zoomLevel")
    public void setZoomLevel(final MapView view,final Integer zoom) {
        Log.i("Map","setZoomLevel:" + zoom);
        if (zoom != null) {
            zoomLevel = zoom;
            zoomNeedsUpdate = true;
            updateCamera(null);
        }
    }
    @ReactProp(name = "mapPadding")
    public void setMapPadding(final MapView view,final ReadableMap padding) {
        mapPaddingLeft = 0;
        mapPaddingRight = 0;
        mapPaddingTop = 0;
        mapPaddingBottom = 0;
        if (padding != null) {
            if (padding.hasKey("left")) {
                mapPaddingLeft = padding.getInt("left");
            }
            if (padding.hasKey("right")) {
                mapPaddingRight = padding.getInt("right");
            }
            if (padding.hasKey("top")) {
                mapPaddingTop = padding.getInt("top");
            }
            if (padding.hasKey("bottom")) {
                mapPaddingBottom = padding.getInt("bottom");
            }
            updateMapPadding();
        }
    }
    private void updateMapPadding() {
        if (map != null) {
            map.setPadding(mapPaddingLeft, mapPaddingTop, mapPaddingRight, mapPaddingBottom);
        }
    }
    @ReactProp(name = "showMyLocationButton")
    public void setShowMyLocationButton(final MapView view,final Boolean show) {
        if (show != null) {
            this.showMyLocationButton = show;
            if (map != null ) {
                map.getUiSettings().setMyLocationButtonEnabled(this.showMyLocationButton);
            }
        }
    }
}
