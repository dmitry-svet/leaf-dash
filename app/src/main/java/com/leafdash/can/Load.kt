package com.leafdash.can

/**
 * Live power breakdown for the load screen, kW. Battery = pack V x A; motor,
 * climate and aux come from the VCM. Climate is one fast reading (1151, A/C
 * compressor + PTC heater); the split uses the VCM's slow A/C and heater
 * estimates (1261/1262) as a ratio, or the outside temperature when both are
 * zero/unknown. Other = battery minus the components (inverter/motor losses,
 * DC/DC), floored at 0.
 */
data class Load(
    val batteryKw: Double?,
    val motorKw: Double?,
    val climateKw: Double?,
    val acKw: Double?,
    val heaterKw: Double?,
    val auxKw: Double?,
    val otherKw: Double?,
    /** Instant consumption, kWh/100 km; null below [MIN_SPEED_KMH]. */
    val kwhPer100: Double?,
) {
    companion object {
        const val MIN_SPEED_KMH = 5.0
        private const val AC_ABOVE_C = 15.0

        fun of(s: LeafState): Load {
            val battery = s.powerKw
            val motor = s.motorPowerW?.let { it / 1000.0 }
            val climate = s.acPower250W?.let { it * 0.25 }
            val aux = s.auxPower100W?.let { it * 0.1 }
            val estAc = (s.estAcPower50W ?: 0) * 0.05
            val estHeat = (s.estHeaterPower250W ?: 0) * 0.25
            val acShare = when {
                estAc + estHeat > 0 -> estAc / (estAc + estHeat)
                (s.ambientTempC ?: 20.0) >= AC_ABOVE_C -> 1.0
                else -> 0.0
            }
            val other = if (battery != null && motor != null)
                (battery - motor - (climate ?: 0.0) - (aux ?: 0.0)).coerceAtLeast(0.0) else null
            val speed = s.speedKmh
            return Load(
                batteryKw = battery,
                motorKw = motor,
                climateKw = climate,
                acKw = climate?.let { it * acShare },
                heaterKw = climate?.let { it * (1 - acShare) },
                auxKw = aux,
                otherKw = other,
                kwhPer100 = if (battery != null && speed != null && speed >= MIN_SPEED_KMH) battery / speed * 100.0 else null,
            )
        }
    }
}
