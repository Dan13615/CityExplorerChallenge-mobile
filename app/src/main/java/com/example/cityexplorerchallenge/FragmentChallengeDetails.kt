package com.example.cityexplorerchallenge

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController

class ChallengeDetailsFragment : Fragment(R.layout.fragment_challenge_details) {

    private val viewModel: ChallengeViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvChallenge = view.findViewById<TextView>(R.id.tv_details_challenge)
        val tvReasons = view.findViewById<TextView>(R.id.tv_details_reasons)
        val tvPlace = view.findViewById<TextView>(R.id.tv_details_place)

        viewModel.activeChallenge.observe(viewLifecycleOwner) { challenge ->
            if (challenge != null && challenge.isActive) {
                tvChallenge.text = "Challenge: Visit ${challenge.name}"
                
                // Construct dynamic reasons based on category
                val reasons = StringBuilder()
                reasons.append("• Nearby ${challenge.category.lowercase()} detected\n")
                reasons.append("• Target location verified\n")
                reasons.append("• Category adventure waiting")
                tvReasons.text = reasons.toString()

                tvPlace.text = "Name: ${challenge.name}\nCategory: ${challenge.category}\nTarget: ${String.format("%.4f, %.4f", challenge.targetPoint.latitude, challenge.targetPoint.longitude)}"
            } else {
                tvChallenge.text = "No active challenge"
                tvReasons.text = "Start a new challenge from the home screen."
                tvPlace.text = "---"
            }
        }

        view.findViewById<Button>(R.id.btn_go_to_main).setOnClickListener {
            findNavController().navigateUp()
        }
    }
}
