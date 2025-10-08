package com.example.taller2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.taller2.databinding.ActivityMapasBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.gms.tasks.CancellationTokenSource
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class Mapas : AppCompatActivity(), OnMapReadyCallback {

    // --- UI / Map
    private lateinit var binding: ActivityMapasBinding
    private var mMap: GoogleMap? = null

    // --- Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var lastLoggedLocation: Location? = null
    private var currentMarker: Marker? = null

    // --- Destino (para puntos 6 y 7)
    private var destinationMarker: Marker? = null

    // --- Sensor de luz (punto 5)
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private lateinit var lightListener: SensorEventListener

    // --- JSON (formato de las diapositivas)
    private var localizaciones: JSONArray = JSONArray()
    private val fileName = "locations.json"

    // --- Permisos
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                getCurrentLocationGoogle()
            } else {
                Toast.makeText(this, "Permiso de ubicación denegado ❌", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1) SENSOR LUZ
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        lightListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (mMap != null) {
                    if (event.values[0] < 5000) {
                        Log.i("MAPS", "DARK MAP ${event.values[0]}")
                        mMap!!.setMapStyle(MapStyleOptions.loadRawResourceStyle(baseContext, R.raw.dark))
                    } else {
                        Log.i("MAPS", "LIGHT MAP ${event.values[0]}")
                        mMap!!.setMapStyle(MapStyleOptions.loadRawResourceStyle(baseContext, R.raw.retro))
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        // 2) MAPA
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // 3) LOCATION
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L // cada 5 segundos
        ).setMinUpdateDistanceMeters(5f) // cada 5 metros
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                handleLocationUpdate(loc)
            }
        }

        // 4) BUSCADOR (punto 6)
        binding.texto.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val q = binding.texto.text?.toString()?.trim().orEmpty()
                if (q.isBlank()) {
                    Toast.makeText(this, "La dirección está vacía", Toast.LENGTH_SHORT).show()
                    return@setOnEditorActionListener true
                }
                geocodeByTextAndPin(q)
                true
            } else false
        }

        // 5) Cargar historial si ya existe (como en las slides)
        localizaciones = readJSONArrayFromFile(fileName)
    }

    override fun onResume() {
        super.onResume()
        lightSensor?.let { sensorManager.registerListener(lightListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        if (hasFineLocation()) startLocationUpdates() else askFineLocation()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(lightListener)
        stopLocationUpdates()
    }

    // ===================== Mapa listo =====================

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap!!.uiSettings.isZoomGesturesEnabled = true
        mMap!!.uiSettings.isScrollGesturesEnabled = true
        mMap!!.uiSettings.isRotateGesturesEnabled = true
        mMap!!.uiSettings.isTiltGesturesEnabled = true

        mMap!!.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.retro))

        // LongClick (punto 7)
        mMap!!.setOnMapLongClickListener { latLng ->
            val address = reverseGeocode(latLng)
            setDestinationMarker(latLng, address ?: "Posición seleccionada")
            moveCamera(latLng, 15f)
            // Distancia a destino (punto 8)
            showDistanceFromCurrentTo(latLng)
        }

        // Permitir botón "mi ubicación" si hay permiso
        if (hasFineLocation()) {
            try {
                mMap?.isMyLocationEnabled = true
                mMap?.uiSettings?.isMyLocationButtonEnabled = true
                // 🔹 Forzar un fix actual para evitar 'lastLocation' viejo (California)
                getCurrentLocationGoogle()
            } catch (se: SecurityException) {
                Log.w("MAPS", "No se pudo habilitar MyLocation: ${se.message}")
            }
        } else {
            askFineLocation()
        }
    }

    // ===================== Localización y logs =====================

    private fun handleLocationUpdate(loc: Location) {
        // Primer fix
        if (currentMarker == null) {
            currentMarker = mMap?.addMarker(
                MarkerOptions()
                    .position(LatLng(loc.latitude, loc.longitude))
                    .title("Mi ubicación")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
            moveCamera(LatLng(loc.latitude, loc.longitude), 16f)
            lastLoggedLocation = Location(loc)

            // ➜ Guardar en JSONArray (formato slides)
            localizaciones.put(MyLocation(Date(System.currentTimeMillis()), loc.latitude, loc.longitude).toJSON())
            writeJSONArray()
            return
        }

        // Movimiento > 30 m
        val last = lastLoggedLocation
        if (last == null || last.distanceTo(loc) >= 30f) {
            currentMarker?.position = LatLng(loc.latitude, loc.longitude)
            moveCamera(LatLng(loc.latitude, loc.longitude), mMap?.cameraPosition?.zoom ?: 16f)

            localizaciones.put(MyLocation(Date(System.currentTimeMillis()), loc.latitude, loc.longitude).toJSON())
            writeJSONArray()
            lastLoggedLocation = Location(loc)
        }
    }

    // ===================== Geocoder (texto -> punto) =====================

    private fun geocodeByTextAndPin(query: String) {
        try {
            val geocoder = Geocoder(this)
            val res = geocoder.getFromLocationName(query, 1)
            if (!res.isNullOrEmpty()) {
                val addr = res[0]
                val p = LatLng(addr.latitude, addr.longitude)
                val title = query
                val snippet = addr.getAddressLine(0) ?: ""
                setDestinationMarker(p, title, snippet)
                moveCamera(p, 16f)
                showDistanceFromCurrentTo(p)
            } else {
                Toast.makeText(this, "Dirección no encontrada", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Toast.makeText(this, "Error al buscar dirección", Toast.LENGTH_SHORT).show()
            Log.e("MAPS", "Geocoder error: ${e.message}")
        }
    }

    // ===================== Geocoder inverso (punto -> texto) =====================

    private fun reverseGeocode(p: LatLng): String? {
        return try {
            val geocoder = Geocoder(this)
            val res = geocoder.getFromLocation(p.latitude, p.longitude, 1)
            if (!res.isNullOrEmpty()) res[0].getAddressLine(0) else null
        } catch (e: IOException) {
            Log.e("MAPS", "Geocoder inverso error: ${e.message}")
            null
        }
    }

    // ===================== Marcadores destino y cámara =====================

    private fun setDestinationMarker(position: LatLng, title: String, snippet: String? = null) {
        destinationMarker?.remove()
        destinationMarker = mMap?.addMarker(
            MarkerOptions()
                .position(position)
                .title(title)
                .apply { if (!snippet.isNullOrBlank()) snippet(snippet) }
                .icon(bitmapDescriptorFromVector(this, R.drawable.baseline_add_location_alt_24))
        )
        destinationMarker?.showInfoWindow()
    }

    private fun moveCamera(target: LatLng, zoom: Float) {
        mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(target, zoom))
    }

    // ===================== Distancia (punto 8) =====================

    private fun showDistanceFromCurrentTo(target: LatLng) {
        val cur = currentMarker?.position ?: return
        val results = FloatArray(1)
        Location.distanceBetween(cur.latitude, cur.longitude, target.latitude, target.longitude, results)
        val meters = results[0]
        val text = if (meters >= 1000) {
            val km = ((meters / 1000f) * 100).roundToInt() / 100f
            "$km km"
        } else {
            "${meters.roundToInt()} m"
        }
        Toast.makeText(this, "Distancia a marcador: $text", Toast.LENGTH_LONG).show()
    }

    // ===================== Utils =====================

    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor {
        val vectorDrawable: Drawable? = ContextCompat.getDrawable(context, vectorResId)
        vectorDrawable?.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val bitmap = Bitmap.createBitmap(
            vectorDrawable!!.intrinsicWidth,
            vectorDrawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun askFineLocation() {
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            askFineLocation()
            return
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        mMap?.let {
            try {
                it.isMyLocationEnabled = true
                it.uiSettings.isMyLocationButtonEnabled = true
            } catch (_: SecurityException) { }
        }
        // 🔹 Intenta obtener un fix inmediato por si el callback aún no llegó
        seedCurrentLocationOnce()
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // ===================== Obtener ubicación actual (Google) =====================

    private fun getCurrentLocationGoogle() {
        // Verifica permisos (tu método hasFineLocation() ya existe)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        // Crea LatLng de Google Maps
                        val latLng = LatLng(location.latitude, location.longitude)

                        // Si aún no hay marcador, lo agregamos; si ya existe, solo actualizamos su posición
                        if (currentMarker == null) {
                            currentMarker = mMap?.addMarker(
                                MarkerOptions()
                                    .position(latLng)
                                    .title("Mi ubicación")
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                            )
                        } else {
                            currentMarker?.position = latLng
                        }

                        // Centrar cámara (usa tu helper moveCamera)
                        moveCamera(latLng, 16f)

                        // Inicia actualizaciones continuas (ya tienes startLocationUpdates())
                        startLocationUpdates()
                    } else {
                        // lastLocation puede ser null (por ejemplo recién encendido)
                        Log.e("MapsApp", "lastLocation es null — solicitando actualización")
                        // Pedimos una ubicación más fresca
                        requestFreshLocation()
                    }
                }
                .addOnFailureListener { ex ->
                    Log.e("MapsApp", "Error al obtener lastLocation", ex)
                    Toast.makeText(this, "Error al obtener ubicación", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Pide permiso (tu askFineLocation/request launcher ya están implementados)
            askFineLocation()
        }
    }

    private fun requestFreshLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            askFineLocation()
            return
        }

        val cts = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    if (currentMarker == null) {
                        currentMarker = mMap?.addMarker(
                            MarkerOptions()
                                .position(latLng)
                                .title("Mi ubicación (fresca)")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                        )
                    } else {
                        currentMarker?.position = latLng
                    }
                    moveCamera(latLng, 16f)
                    startLocationUpdates()
                } else {
                    Log.e("MapsApp", "getCurrentLocation devolvió null")
                    Toast.makeText(this, "No se pudo obtener ubicación actual", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { ex ->
                Log.e("MapsApp", "Falla al pedir ubicación fresca", ex)
            }
    }

    // ===================== JSON helpers  =====================

    private fun writeJSONArray() {
        try {
            val file = File(baseContext.getExternalFilesDir(null), fileName)
            Log.i("LOCATION", "Ubicacion de archivo: $file")
            file.writeText(localizaciones.toString())
            Toast.makeText(applicationContext, "Location saved", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            // Log de error
        }
    }

    private fun readJSONArrayFromFile(fileName: String): JSONArray {
        val file = File(baseContext.getExternalFilesDir(null), fileName)
        if (!file.exists()) {
            Log.i("LOCATION", "Ubicacion de archivo: $file no encontrado")
            return JSONArray()
        }
        val jsonString = file.readText()
        return try {
            JSONArray(jsonString)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    // ===================== Ubicación actual =====================

    private fun seedCurrentLocationOnce() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        val cts = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    handleLocationUpdate(loc)  // marcador, cámara y JSON usando tu lógica
                }
            }
            .addOnFailureListener { e ->
                Log.w("MAPS", "getCurrentLocation falló: ${e.message}")
            }
    }

    // ===================== Modelo JSON =====================

    class MyLocation(var fecha: Date, var latitud: Double, var longitud: Double) {
        fun toJSON(): JSONObject {
            val obj = JSONObject()
            try {
                obj.put("latitud", latitud)
                obj.put("longitud", longitud)
                obj.put("dateMillis", fecha.time)
                val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(fecha)
                obj.put("fecha_hora", iso)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            return obj
        }
    }


}