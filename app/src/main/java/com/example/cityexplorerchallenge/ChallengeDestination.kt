package com.example.cityexplorerchallenge

import org.osmdroid.util.GeoPoint

data class ChallengeState(
    val name: String,
    val category: String,
    val targetPoint: GeoPoint,
    val startPoint: GeoPoint,
    val distanceText: String = "--",
    val isActive: Boolean = false,
    val selectionReason: String = ""
)