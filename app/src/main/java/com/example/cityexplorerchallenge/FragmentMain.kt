package com.example.cityexplorerchallenge

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class MainScreenFragment : Fragment(R.layout.fragment_main) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
}