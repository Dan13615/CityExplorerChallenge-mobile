package com.example.cityexplorerchallenge

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController

class ProposalsFragment : Fragment(R.layout.fragment_proposals) {

    private val viewModel: ChallengeViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val proposalsContainer = view.findViewById<LinearLayout>(R.id.proposals_container)

        viewModel.challengeProposals.observe(viewLifecycleOwner) { proposals ->
            proposalsContainer.removeAllViews()
            val inflater = LayoutInflater.from(context)

            if (proposals.isNullOrEmpty()) {
                val emptyTv = TextView(context).apply {
                    text = "No proposals found. Try again!"
                    setTextColor(resources.getColor(R.color.secondaryFontColor, null))
                    setPadding(0, 32, 0, 0)
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                }
                proposalsContainer.addView(emptyTv)
            } else {
                for (proposal in proposals) {
                    val card = inflater.inflate(R.layout.item_proposal_card, proposalsContainer, false)
                    card.findViewById<TextView>(R.id.tv_proposal_name).text = proposal.name
                    card.findViewById<TextView>(R.id.tv_proposal_category).text = "Category: ${proposal.category}"
                    card.findViewById<TextView>(R.id.tv_proposal_distance).text = "Distance: ${proposal.distanceText}"
                    card.findViewById<TextView>(R.id.tv_proposal_reason).text = "Reason: ${proposal.selectionReason}"
                    
                    card.findViewById<Button>(R.id.btn_select_proposal).setOnClickListener {
                        viewModel.setActiveChallenge(requireContext(), proposal.copy(isActive = true))
                        Toast.makeText(context, "Challenge started: ${proposal.name}", Toast.LENGTH_SHORT).show()
                        findNavController().navigate(R.id.action_proposals_to_main)
                    }
                    
                    proposalsContainer.addView(card)
                }
            }
        }

        view.findViewById<Button>(R.id.btn_go_to_main).setOnClickListener {
            viewModel.clearProposals()
            findNavController().navigateUp()
        }
    }
}
