package com.example.cityexplorerchallenge

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class HistoryFragment : Fragment(R.layout.fragment_history) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.btn_go_to_main).setOnClickListener {
            findNavController().navigateUp()
        }
    }
}