package com.example.taller2

import android.app.UiModeManager
import android.content.Context
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.example.taller2.databinding.ActivityOmsBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.api.IGeoPoint
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Address
import android.location.Geocoder
import com.google.android.gms.location.LocationRequest
import android.os.Looper
import android.widget.EditText
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale


/*class Oms : AppCompatActivity() {
    private lateinit var binding: ActivityOmsBinding
    private lateinit var map: MapView
    private var longPressedMarker: Marker? = null
    private var searchMarker: Marker? = null
    private var roadOverlay: Polyline? = null
    private lateinit var roadManager: RoadManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastLocation: Location? = null
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private lateinit var addressEditText: EditText
    private val lightSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event != null && event.sensor.type == Sensor.TYPE_LIGHT) {
                val lightLevel = event.values[0]
                if (lightLevel < 10) {
                    setDarkMode(true)
                } else {
                    setDarkMode(false)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation
            if (location != null) {
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                addMarker(geoPoint, "Ubicación Actualizada", false) // Marcamos la nueva ubicación
                map.controller.setCenter(geoPoint)
                map.controller.setZoom(18.0)
                handleNewLocation(location)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )

        binding = ActivityOmsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        map = binding.osmMap
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        binding.searchButton.setOnClickListener{
            val address = addressEditText.text.toString()
            getLocationFromAddress(address)
        }

        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        roadManager = OSRMRoadManager(this, "ANDROID")

        map.overlays.add(createOverlayEvents())


    }

    private fun getLocationFromAddress(address: String) {
        if (!Geocoder.isPresent()) {
            Log.e("Geocoder", "Geocoder no está disponible en este dispositivo.")
            return
        }

        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            val addresses: List<Address> = geocoder.getFromLocationName(address, 1) ?: emptyList()

            if (addresses.isNotEmpty()) {
                val location = addresses[0]
                val latitude = location.latitude
                val longitude = location.longitude

                val geoPoint = GeoPoint(latitude, longitude)
                addMarker(geoPoint)

                map.controller.setCenter(geoPoint)
                map.controller.setZoom(18.0)

                Log.d("Geocoder", "Ubicación encontrada: Lat: $latitude, Long: $longitude")
            } else {
                Log.e("Geocoder", "No se pudo encontrar la dirección: $address.")
            }
        } catch (e: IOException) {
            Log.e("Geocoder", "Error al obtener la ubicación: ${e.message}")
        } catch (e: Exception) {
            Log.e("Geocoder", "Error inesperado: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()

        setupLightSensor()

        lightSensor?.let {
            sensorManager.registerListener(lightSensorListener, it, SensorManager.SENSOR_DELAY_UI)
        }

        map.onResume()
        getCurrentLocation()
        startLocationUpdates()

        val uims = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uims.nightMode == UiModeManager.MODE_NIGHT_YES) {
            map.overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
        }
    }

    private fun setupLightSensor() {
        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        if (lightSensor != null) {
            sensorManager.registerListener(lightSensorListener, lightSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager.unregisterListener(lightSensorListener)

    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    if (!::map.isInitialized) {
                        Log.e("MapsApp", "MapView no está inicializado todavía")
                        return@addOnSuccessListener
                    }

                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    addMarker(geoPoint, "Mi Ubicación Actual", false)

                    map.controller.apply {
                        setCenter(geoPoint)
                        setZoom(18.0)
                    }

                    startLocationUpdates()
                } else {
                    Log.e("MapsApp", "No se pudo obtener la ubicación")
                }
            }.addOnFailureListener {
                Log.e("MapsApp", "Error al obtener ubicación", it)
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation() // ¡Ya tienes permiso, puedes pedir ubicación!
        } else {
            Log.e("MapsApp", "Permiso de ubicación denegado")
        }
    }

    fun createOverlayEvents(): MapEventsOverlay {
        return MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p != null) {
                    longPressOnMap(p)
                }
                return true
            }
        })
    }

    private fun longPressOnMap(p: GeoPoint) {
        if (longPressedMarker != null) {
            map.overlays.remove(longPressedMarker)
        }

        val geocoder = Geocoder(this, Locale.getDefault())
        var snippet = ""

        try {
            val addresses = geocoder.getFromLocation(p.latitude, p.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                snippet = address.getAddressLine(0) ?: ""
            } else {
                snippet = "Ubicación desconocida"
            }
        } catch (e: IOException) {
            Log.e("Geocoder", "Error al obtener dirección desde lat/long: ${e.message}")
            snippet = "Error obteniendo dirección"
        }

        addMarker(p, snippet, true)
    }


    fun addMarker(p: GeoPoint, snippet: String, longPressed: Boolean) {
        if (longPressed) {
            longPressedMarker =
                createMarker(p, "Nueva ubicación", snippet, R.drawable.baseline_add_location_alt_24)
            longPressedMarker?.let { map.overlays.add(it) }
        } else {
            searchMarker =
                createMarker(p, "Ubicación", snippet, R.drawable.baseline_add_location_alt_24)
            searchMarker?.let { map.overlays.add(it) }
        }
    }

    private fun addMarker(geoPoint: GeoPoint) {
        val marker = Marker(map)
        marker.position = geoPoint
        marker.title = "Ubicación encontrada"
        map.overlays.add(marker)
    }

    fun createMarker(p: GeoPoint, title: String, desc: String, iconID: Int): Marker {
        val marker = Marker(map)
        marker.title = title
        marker.subDescription = desc
        marker.icon = resources.getDrawable(iconID, theme)
        marker.position = p
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        return marker
    }

    fun drawRoute(start: GeoPoint, finish: GeoPoint) {
        var routePoints = ArrayList<GeoPoint>()
        routePoints.add(start)
        routePoints.add(finish)
        val road = roadManager.getRoad(routePoints)
        if (road != null) {
            Log.i("MapsApp", "Route length: " + road.mLength + " km")
            Log.i("MapsApp", "Duration: " + road.mDuration / 60 + " min")
            roadOverlay = RoadManager.buildRoadOverlay(road)
            roadOverlay!!.outlinePaint.color = Color.RED
            roadOverlay!!.outlinePaint.strokeWidth = 10f
            map.overlays.add(roadOverlay)
        } else {
            Log.e("MapsApp", "No road found")
        }

    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val locationRequest = LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 2000
            priority =
                Priority.PRIORITY_HIGH_ACCURACY
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }


    private fun handleNewLocation(newLocation: Location) {
        if (lastLocation == null) {
            lastLocation = newLocation
            saveLocation(newLocation)
        } else {
            val distance = lastLocation!!.distanceTo(newLocation)

            val latDiff = Math.abs(lastLocation!!.latitude - newLocation.latitude)
            val lonDiff = Math.abs(lastLocation!!.longitude - newLocation.longitude)

            if (distance > 30 || latDiff > 0.0001 || lonDiff > 0.0001) {
                lastLocation = newLocation
                saveLocation(newLocation)
            }
        }
    }


    private fun saveLocation(location: Location) {
        val currentTime =
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())

        val entry = LocationEntry(
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = currentTime
        )

        val jsonString = JSONObject()
        jsonString.put("latitude", entry.latitude)
        jsonString.put("longitude", entry.longitude)
        jsonString.put("timestamp", entry.timestamp)

        writeJsonToFile(jsonString)
    }

    private fun writeJsonToFile(jsonObject: JSONObject) {
        val filename = "locations.json"
        val file = File(filesDir, filename)

        if (!file.exists()) {
            file.createNewFile()
        }

        val existingData = if (file.readText().isNotEmpty()) {
            JSONArray(file.readText())
        } else {
            JSONArray()
        }

        existingData.put(jsonObject)

        file.writeText(existingData.toString())
    }


    private fun setDarkMode(enable: Boolean) {
        val uims = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (enable) {
            uims.nightMode = UiModeManager.MODE_NIGHT_YES
            map.overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
        } else {
            uims.nightMode = UiModeManager.MODE_NIGHT_NO
        }

        delegate.localNightMode = if (enable) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
    }

}*/

