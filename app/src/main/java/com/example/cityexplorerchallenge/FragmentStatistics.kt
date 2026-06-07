package com.example.cityexplorerchallenge

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController

class StatisticsFragment : Fragment(R.layout.fragment_statistics) {

    private val viewModel: ChallengeViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTotal = view.findViewById<TextView>(R.id.tv_stats_total)
        val tvDistance = view.findViewById<TextView>(R.id.tv_stats_distance)
        val tvCategories = view.findViewById<TextView>(R.id.tv_stats_categories)
        val tvMostExplored = view.findViewById<TextView>(R.id.tv_stats_most_explored)

        viewModel.completedChallenges.observe(viewLifecycleOwner) { history ->
            if (history.isNullOrEmpty()) {
                tvTotal.text = "Total completed challenges: 0"
                tvDistance.text = "Start exploring to see stats!"
                tvCategories.text = "No categories yet"
                tvMostExplored.text = "Most explored category: None"
                return@observe
            }

            val totalCount = history.size
            tvTotal.text = "Total completed challenges: $totalCount"
            tvDistance.text = "You have explored many places!"

            val categoryCounts = history.groupingBy { it.category }.eachCount()
            val categoriesText = categoryCounts.entries.joinToString("\n") { "${it.key}: ${it.value}" }
            tvCategories.text = categoriesText

            val mostExplored = categoryCounts.maxByOrNull { it.value }?.key ?: "None"
            tvMostExplored.text = "Most explored category: $mostExplored"
        }

        viewModel.loadHistory(requireContext())

        view.findViewById<Button>(R.id.btn_go_to_main).setOnClickListener {
            findNavController().navigateUp()
        }
    }
}
