package com.leafdash.poll

import com.leafdash.can.LeafState
import com.leafdash.trip.TripWindow

/** Everything the dashboard renders. Immutable snapshot for the UI. */
data class DashState(
    val leaf: LeafState = LeafState(),
    val lastCharge: TripWindow = TripWindow(),
    val carOn: TripWindow = TripWindow(),
    val trip: TripWindow = TripWindow(),
    val lifetime: TripWindow = TripWindow(),
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val connectMsg: String? = null,
    val error: String? = null,
    /** Diagnostic: raw ELM327 response text per polled group (active mode). */
    val raw: Map<String, String> = emptyMap(),
    /** Diagnostic: connection/debug lines (ELM id, protocol, group status). */
    val debug: List<String> = emptyList(),
    /** Raw odometer count from 0x5C5 (km or mi per car). Feeds economy distance. */
    val odometerKm: Double? = null,
    /** Whether the odometer raw count is in miles (user setting). */
    val odoMiles: Boolean = false,
    /** Odometer converted to km for display. */
    val odoKm: Double? = null,
    /** Smoothed lifetime efficiency (kWh/100 km) for stable range. */
    val avgKwhPer100: Double = 15.0,
)
