package com.leafdash.can

/**
 * Live power breakdown for the load screen, kW. Battery = pack V x A; motor,
 * climate and aux come from the VCM. Climate is one fast reading (1151, A/C
 * compressor + PTC heater); the split uses the VCM's slow A/C and heater
 * estimates (1261/1262) as a ratio, or the outside temperature when both are
 * zero/unknown. VCM motor power (1146) is traction only (reads 0 under
 * regen), so when the pack power left after climate and aux is negative,
 * that remainder is the motor's regen. Other = battery minus the components
 * (inverter/motor losses, DC/DC), floored at 0.
 * Parked (speed < 1 km/h) the motor cannot regen: the remainder is not
 * shown as regen, and a pack current within [PARKED_DEADBAND_A] reads 0
 * (the LBC sensor shows about -2 A with no real current, car ON not READY).
 */
data class Load(
    val batteryKw: Double?,
    val motorKw: Double?,
    val climateKw: Double?,
    val acKw: Double?,
    val heaterKw: Double?,
    val auxKw: Double?,
    val otherKw: Double?,
) {
    companion object {
        private const val AC_ABOVE_C = 15.0
        const val PARKED_DEADBAND_A = 3.0

        fun of(s: LeafState): Load {
            val parked = s.speedKmh?.let { it < 1.0 } ?: false
            val battery = if (parked && s.packAmps != null && Math.abs(s.packAmps) <= PARKED_DEADBAND_A) 0.0 else s.powerKw
            val climate = s.acPower250W?.let { it * 0.25 }
            val aux = s.auxPower100W?.let { it * 0.1 }
            val left = battery?.let { it - (climate ?: 0.0) - (aux ?: 0.0) }
            val motor = if (left != null && left < 0 && !parked) left else s.motorPowerW?.let { it / 1000.0 }
            val estAc = (s.estAcPower50W ?: 0) * 0.05
            val estHeat = (s.estHeaterPower250W ?: 0) * 0.25
            val acShare = when {
                estAc + estHeat > 0 -> estAc / (estAc + estHeat)
                (s.ambientTempC ?: 20.0) >= AC_ABOVE_C -> 1.0
                else -> 0.0
            }
            val other = if (battery != null && motor != null)
                (battery - motor - (climate ?: 0.0) - (aux ?: 0.0)).coerceAtLeast(0.0) else null
            return Load(
                batteryKw = battery,
                motorKw = motor,
                climateKw = climate,
                acKw = climate?.let { it * acShare },
                heaterKw = climate?.let { it * (1 - acShare) },
                auxKw = aux,
                otherKw = other,
            )
        }
    }
}
