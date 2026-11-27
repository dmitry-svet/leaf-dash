package com.leafdash.trip

/**
 * Energy per load component since the last trip reset, kWh: motor traction,
 * motor regen (positive number), climate, aux + other.
 */
data class LoadEnergy(
    val tractionKwh: Double = 0.0,
    val regenKwh: Double = 0.0,
    val climateKwh: Double = 0.0,
    val otherKwh: Double = 0.0,
)
