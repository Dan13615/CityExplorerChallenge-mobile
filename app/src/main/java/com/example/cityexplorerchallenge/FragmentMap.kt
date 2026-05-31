package com.example.cityexplorerchallenge

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.graphics.createBitmap

class MapFragment : Fragment(R.layout.fragment_map) {

    private val GRAPH_HOPPER_API_KEY = "e4f4c30a-d9fc-4e98-84ef-69540acbbcc6"

    private lateinit var mapView: MapView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvDistance: TextView
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var roadPolyline: Polyline? = null
    private var locationManager: LocationManager? = null

    private val targetPoint = GeoPoint(50.0617, 19.9378)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            enableUserLocationTracking()
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val userPoint = GeoPoint(location.latitude, location.longitude)
            getWalkingRoute(userPoint, targetPoint)
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = requireContext().applicationContext
        Configuration.getInstance().load(context, context.getSharedPreferences("osm_pref", Context.MODE_PRIVATE))
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.map_view)
        progressBar = view.findViewById(R.id.progress_bar)
        tvDistance = view.findViewById(R.id.tv_map_distance)

        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(16.5)
        mapView.controller.setCenter(targetPoint)

        val targetMarker = Marker(mapView)
        targetMarker.position = targetPoint
        targetMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        targetMarker.title = "Target Location"
        mapView.overlays.add(targetMarker)

        view.findViewById<Button>(R.id.btn_center_location).setOnClickListener {
            myLocationOverlay?.myLocation?.let { geoPoint ->
                mapView.controller.animateTo(geoPoint)
                myLocationOverlay?.enableFollowLocation()
            }
        }

        view.findViewById<Button>(R.id.btn_check_completion).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<Button>(R.id.btn_go_to_main).setOnClickListener {
            findNavController().navigateUp()
        }

        checkAndRequestLocationPermissions()
    }

    private fun checkAndRequestLocationPermissions() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineLocationGranted) {
            enableUserLocationTracking()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun enableUserLocationTracking() {
        val provider = GpsMyLocationProvider(requireContext())
        myLocationOverlay = MyLocationNewOverlay(provider, mapView).apply {
            enableMyLocation()
            enableFollowLocation()
            getBitmapFromVector(requireContext(), R.drawable.ic_red_location_arrow)?.let { redArrowBitmap ->
                setDirectionArrow(redArrowBitmap, redArrowBitmap)
                setPersonIcon(redArrowBitmap)
            }
        }
        mapView.overlays.add(myLocationOverlay)

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 5f, locationListener)
            locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                getWalkingRoute(GeoPoint(it.latitude, it.longitude), targetPoint)
            }
        }
    }

    private fun getWalkingRoute(start: GeoPoint, stop: GeoPoint) {
        if (start.latitude == 0.0 && start.longitude == 0.0) {
            tvDistance.text = "Waiting for valid GPS fix..."
            return
        }

        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val urlString = "https://graphhopper.com/api/1/route?point=${start.latitude},${start.longitude}&point=${stop.latitude},${stop.longitude}&profile=foot&points_encoded=false&key=$GRAPH_HOPPER_API_KEY"
            val routePoints = ArrayList<GeoPoint>()
            var distanceText = "Distance: Error calculating"

            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)
                    val paths = jsonResponse.getJSONArray("paths")

                    if (paths.length() > 0) {
                        val pathObject = paths.getJSONObject(0)
                        val distanceInMeters = pathObject.getDouble("distance")

                        distanceText = if (distanceInMeters >= 1000) {
                            String.format("Distance: %.2f km", distanceInMeters / 1000.0)
                        } else {
                            String.format("Distance: %d m", distanceInMeters.toInt())
                        }

                        // Parse the GraphHopper coordinates [[lon, lat], ...]
                        val pointsObj = pathObject.getJSONObject("points")
                        val coordinates = pointsObj.getJSONArray("coordinates")

                        for (i in 0 until coordinates.length()) {
                            val coord = coordinates.getJSONArray(i)
                            val lon = coord.getDouble(0)
                            val lat = coord.getDouble(1)
                            routePoints.add(GeoPoint(lat, lon))
                        }
                    } else {
                        distanceText = "No walking path found"
                    }
                } else {
                    distanceText = "Server Error ($responseCode)"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                distanceText = "Network unreachable"
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                tvDistance.text = distanceText

                if (routePoints.isNotEmpty()) {
                    roadPolyline?.let { mapView.overlays.remove(it) }

                    roadPolyline = Polyline(mapView).apply {
                        setPoints(routePoints)
                        outlinePaint.color = Color.RED
                        outlinePaint.strokeWidth = 8f
                    }
                    mapView.overlays.add(roadPolyline)
                    mapView.invalidate() // Triggers immediate view update
                }
            }
        }
    }

    private fun getBitmapFromVector(context: Context, vectorResId: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, vectorResId) ?: return null
        val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        myLocationOverlay?.enableMyLocation()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        myLocationOverlay?.disableMyLocation()
        locationManager?.removeUpdates(locationListener)
    }
}