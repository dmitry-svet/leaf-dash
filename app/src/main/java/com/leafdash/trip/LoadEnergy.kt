package com.leafdash.trip

/**
 * Energy per load component since the last trip reset, kWh: motor traction,
 * motor regen (positive number), climate, aux + other; [batteryKwh] = net
 * pack V x A (regen negative), to check the components and the SOC-based trip kWh.
 */
data class LoadEnergy(
    val tractionKwh: Double = 0.0,
    val regenKwh: Double = 0.0,
    val climateKwh: Double = 0.0,
    val otherKwh: Double = 0.0,
    val batteryKwh: Double = 0.0,
)
