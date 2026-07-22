package com.leafdash.can

/**
 * Latest known vehicle values. Fields are null until the matching frame has
 * been seen. Immutable; [CanDecoder] returns an updated copy per frame.
 */
data class LeafState(
    val socPercent: Double? = null,
    val gids: Int? = null,
    val packVolts: Double? = null,
    val packAmps: Double? = null,      // + = discharge, - = regen/charge
    val batteryTempC: Double? = null,
    val ambientTempC: Double? = null,
    val aux12V: Double? = null,        // 12V battery, measured by the ELM327 (ATRV)
    val speedKmh: Double? = null,
    // from active LBC group polling
    val sohPercent: Double? = null,
    val ahCapacity: Double? = null,
    val hx: Double? = null,
    val batteryTempsC: List<Double> = emptyList(),
) {
    /** Instant DC power at the pack, kW. + discharge, - charge/regen. */
    val powerKw: Double?
        get() = if (packVolts != null && packAmps != null) packVolts * packAmps / 1000.0 else null

    /**
     * Usable energy remaining, kWh = SOC × capacity × NOMINAL voltage. Uses a
     * fixed nominal voltage (not live pack V) so it tracks SOC cleanly — live V
     * sags under load and would inject phantom consumption into economy.
     */
    val kwhRemaining: Double?
        get() = when {
            socPercent != null && ahCapacity != null ->
                socPercent / 100.0 * ahCapacity * NOMINAL_V / 1000.0
            gids != null -> gids * GID_WH / 1000.0
            else -> null
        }

    /** gids: broadcast value if present, else derived from kWh remaining. */
    val gidsValue: Int?
        get() = gids ?: kwhRemaining?.let { Math.round(it * 1000.0 / GID_WH).toInt() }

    companion object {
        /** Wh per gid (LeafSpy default 77.5). */
        const val GID_WH = 77.5

        /** Nominal AZE0 pack voltage for stable energy estimation. */
        const val NOMINAL_V = 360.0
    }
}
