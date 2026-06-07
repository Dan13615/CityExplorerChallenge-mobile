package com.example.cityexplorerchallenge

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment(R.layout.fragment_history) {

    private val viewModel: ChallengeViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val historyContainer = view.findViewById<LinearLayout>(R.id.history_container)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        viewModel.completedChallenges.observe(viewLifecycleOwner) { history ->
            historyContainer.removeAllViews()
            val inflater = LayoutInflater.from(context)

            if (history.isEmpty()) {
                val emptyTv = TextView(context).apply {
                    text = "No completed challenges yet."
                    setTextColor(resources.getColor(R.color.secondaryFontColor, null))
                    setPadding(0, 32, 0, 0)
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                }
                historyContainer.addView(emptyTv)
            } else {
                for (item in history) {
                    val card = inflater.inflate(R.layout.item_history_card, historyContainer, false)
                    card.findViewById<TextView>(R.id.tv_history_name).text = "[Completed] ${item.name}"
                    card.findViewById<TextView>(R.id.tv_history_details).text =
                        "Category: ${item.category}\nCompleted: ${dateFormat.format(Date(item.timestamp))}"
                    historyContainer.addView(card)
                }
            }
        }

        viewModel.loadHistory(requireContext())

        view.findViewById<Button>(R.id.btn_go_to_main).setOnClickListener {
            findNavController().navigateUp()
        }
    }
}
