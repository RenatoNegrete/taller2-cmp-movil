package com.example.taller2

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.taller2.databinding.ActivityMapsBinding
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MapStyleOptions
import org.json.JSONArray
import java.io.IOException
import java.util.logging.Logger

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        private val TAG = MapsActivity::class.java.name
        const val REQUEST_CHECK_SETTINGS = 201
    }

    private val logger = Logger.getLogger(TAG)

    private lateinit var binding: ActivityMapsBinding
    private lateinit var mMap: GoogleMap
    private lateinit var mGeocoder: Geocoder

    lateinit var sensorManager: SensorManager
    lateinit var lightSensor: Sensor
    lateinit var lightSensorListener: SensorEventListener
    lateinit var mAddress: EditText

    private val LOCATION_PERMISSION_ID = 103
    private var mFusedLocationClient: FusedLocationProviderClient? = null
    private var mLocationRequest: LocationRequest? = null
    private val mLocationCallback: LocationCallback? = null
    var mCurrentLocation: Location? = null
    var locationPerm = Manifest.permission.ACCESS_FINE_LOCATION

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        //mLocationRequest = createLocationRequest()
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        requestPermission(this, locationPerm, "Permissions to use the location", LOCATION_PERMISSION_ID)


        mAddress = binding.textDirection
        mAddress.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val addressText = mAddress.text.toString()
                if (addressText.isNotEmpty()) {
                    findAddress()
                } else {
                    Toast.makeText(this@MapsActivity, "Enter a direction", Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                false
            }
        }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)!!

        lightSensorListener = object:SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (mMap != null) {
                    if (event.values[0] < 5000) {
                        Log.i("MAPS", "DARK MAP" + event.values[0])
                        mMap.setMapStyle(
                            MapStyleOptions.loadRawResourceStyle(
                                this@MapsActivity,
                                R.raw.style_night
                            )
                        )
                    } else {
                        Log.i("MAPS", "LIGHT MAP" + event.values[0])
                        mMap.setMapStyle(
                            MapStyleOptions.loadRawResourceStyle(
                                this@MapsActivity,
                                R.raw.style_light
                            )
                        )
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, i: Int) { }
        }

        mGeocoder = Geocoder(baseContext)
    }

    private fun createLocationRequest(): LocationRequest {
        return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).apply {
            setMinUpdateDistanceMeters(5f)
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            setWaitForAccurateLocation(true)
        }.build()
    }

    private fun requestPermission(context: Activity, permission: String, justification: String, id: Int) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_DENIED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(context, permission)) {
                Toast.makeText(context, justification, Toast.LENGTH_SHORT).show()
            }
            ActivityCompat.requestPermissions(context, arrayOf(permission), id)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_ID) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                logger.info("Permission granted.")
                // Aquí llamas a tu código para obtener la ubicación
                getLastKnownLocation()
            } else {
                logger.warning("Permission denied.")
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getLastKnownLocation() {
        if (ContextCompat.checkSelfPermission(this, locationPerm) != PackageManager.PERMISSION_GRANTED) {
            logger.warning("Failed to get location permission.")
            requestPermission(this, locationPerm, "We need location permission to show your location", LOCATION_PERMISSION_ID)
        } else {
            logger.info("Permission granted. Getting location.")
            mFusedLocationClient?.lastLocation?.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    logger.info("Location found: Lat=${location.latitude}, Long=${location.longitude}")
                    val position = LatLng(location.latitude, location.longitude)
                    mMap.addMarker(MarkerOptions().position(position).title("Your current location"))
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 12f))
                } else {
                    logger.warning("Last known location is null.")
                    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                        .setWaitForAccurateLocation(true)
                        .setGranularity(Granularity.GRANULARITY_FINE)
                        .build()

                    mFusedLocationClient!!.requestLocationUpdates(locationRequest, object : LocationCallback() {
                        override fun onLocationResult(locationResult: LocationResult) {
                            val currentLocation = locationResult.lastLocation
                            if (currentLocation != null) {
                                logger.info("Current location found: Lat=${currentLocation.latitude}, Long=${currentLocation.longitude}")
                                val position = LatLng(currentLocation.latitude, currentLocation.longitude)
                                mMap.addMarker(MarkerOptions().position(position).title("Your current location"))
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 12f))
                            } else {
                                logger.warning("Current location is null.")
                                Toast.makeText(this@MapsActivity, "Unable to find your location", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }, Looper.getMainLooper())
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(lightSensorListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        //startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(lightSensorListener)
        //stopLocationUpdates()
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        getLastKnownLocation()

        mMap.uiSettings.setAllGesturesEnabled(true)
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isMapToolbarEnabled = true
        mMap.uiSettings.isCompassEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true

        setLongClickListener()
    }

    private fun findAddress() {
        val addressString = mAddress.text.toString()
        if (addressString.isNotEmpty()) {
            try {
                val addresses = mGeocoder.getFromLocationName(
                    addressString, 2
                )
                if (addresses != null && addresses.isNotEmpty()) {
                    val addressResult = addresses[0]
                    val position = LatLng(addressResult.latitude, addressResult.longitude)
                    mMap.addMarker(
                        MarkerOptions().position(position)
                            .title(addressResult.featureName)
                            .snippet(addressResult.getAddressLine(0))
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                    )
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 12f))
                } else {
                    Toast.makeText(
                        this@MapsActivity,
                        "Direction not found",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } else {
            Toast.makeText(this@MapsActivity, "The direction is empty", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun setLongClickListener() {
        mMap.setOnMapLongClickListener { latLng ->
            val addreses = mGeocoder.getFromLocation(latLng.latitude, latLng.longitude,1)
            if (addreses != null && addreses.isNotEmpty()) {
                val address = addreses[0]
                val name = address.getAddressLine(0)
                mMap.addMarker(
                    MarkerOptions().position(latLng)
                        .title(name)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                )
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 12f))

                Toast.makeText(this, "Marcador agregado en: $latLng", Toast.LENGTH_SHORT).show()
            }
        }
    }
}