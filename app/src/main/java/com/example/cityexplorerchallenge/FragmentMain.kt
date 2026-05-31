package com.example.cityexplorerchallenge

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import org.osmdroid.util.GeoPoint

class MainScreenFragment : Fragment(R.layout.fragment_main) {

    private val viewModel: ChallengeViewModel by activityViewModels()
    private var locationManager: LocationManager? = null
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            triggerChallengeCreationWorkflow()
        } else {
            Toast.makeText(context, "Location permission is required to start a challenge!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvChallengeTitle = view.findViewById<TextView>(R.id.tv_challenge_title)
        val tvChallengeCategory = view.findViewById<TextView>(R.id.tv_challenge_category)
        val tvChallengeDistance = view.findViewById<TextView>(R.id.tv_challenge_distance)
        val tvChallengeStatus = view.findViewById<TextView>(R.id.tv_challenge_status)

        viewModel.activeChallenge.observe(viewLifecycleOwner) { challenge ->
            if (challenge != null && challenge.isActive) {
                tvChallengeTitle.text = challenge.name
                tvChallengeCategory.text = "Category: ${challenge.category}"
                tvChallengeDistance.text = challenge.distanceText
                tvChallengeStatus.text = "Status: Active"
                tvChallengeStatus.setTextColor(resources.getColor(R.color.successColor, null))
            } else {
                tvChallengeTitle.text = "No active challenge yet"
                tvChallengeCategory.text = "Category: --"
                tvChallengeDistance.text = "Distance: --"
                tvChallengeStatus.text = "Status: Inactive"
                tvChallengeStatus.setTextColor(resources.getColor(android.R.color.darker_gray, null))
            }
        }

        view.findViewById<Button>(R.id.btn_new_challenge).setOnClickListener {
            checkPermissionsAndStartChallenge()
        }

        view.findViewById<Button>(R.id.btn_open_map).setOnClickListener {
            findNavController().navigate(R.id.action_main_to_map)
        }

        view.findViewById<Button>(R.id.btn_challenge_details).setOnClickListener {
            findNavController().navigate(R.id.action_main_to_details)
        }

        view.findViewById<Button>(R.id.btn_nav_history).setOnClickListener {
            findNavController().navigate(R.id.action_main_to_history)
        }

        view.findViewById<Button>(R.id.btn_nav_statistics).setOnClickListener {
            findNavController().navigate(R.id.action_main_to_statistics)
        }
    }

    private fun checkPermissionsAndStartChallenge() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation) {
            triggerChallengeCreationWorkflow()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun triggerChallengeCreationWorkflow() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val lastKnownGpsLocation = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastKnownNetworkLocation = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            val bestLocation = lastKnownGpsLocation ?: lastKnownNetworkLocation

            if (bestLocation != null) {
                val realUserGpsFix = GeoPoint(bestLocation.latitude, bestLocation.longitude)
                showNewChallengeDialog(realUserGpsFix)
            } else {
                Toast.makeText(context, "No GPS fix found yet. Using default fallback location.", Toast.LENGTH_SHORT).show()
                showNewChallengeDialog(GeoPoint(50.0617, 19.9378))
            }
        }
    }

    private fun showNewChallengeDialog(userPoint: GeoPoint) {
        val categories = arrayOf("Historical Places", "Restaurants", "Cafes")
        val checkedItems = booleanArrayOf(false, false, false)

        AlertDialog.Builder(requireContext())
            .setTitle("Generate New Adventure Location")
            .setMultiChoiceItems(categories, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Explore!") { dialog, _ ->
                val selectedTags = ArrayList<String>()
                if (checkedItems[0]) selectedTags.add("historic")
                if (checkedItems[1]) selectedTags.add("amenity=restaurant")
                if (checkedItems[2]) selectedTags.add("amenity=cafe")

                if (selectedTags.isEmpty()) {
                    Toast.makeText(context, "Please choose a filter configuration!", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.createDynamicChallenge(userPoint.latitude, userPoint.longitude, selectedTags)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}