package com.leafdash.log

import com.leafdash.can.LeafState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Per-sample data log in LeafSpy's published CSV layout (LeafSpy Help,
 * "Log File Format": columns A..EU, same labels and order) so tools that read
 * LeafSpy logs read ours. Values LeafDash cannot read (GPS, gids, pack amps,
 * tire pressures, power by consumer, charge counters...) are left blank.
 */
object LeafSpyLog {

    val HEADER: List<String> = listOf(
        "Date/Time", "Lat", "Long", "Elv", "Speed", "Gids", "SOC", "AHr",
        "Pack Volts", "Pack Amps", "Max CP mV", "Min CP mV", "Avg CP mV",
        "CP mV Diff", "Judgement",
        "Pack T1 F", "Pack T1 C", "Pack T2 F", "Pack T2 C",
        "Pack T3 F", "Pack T3 C", "Pack T4 F", "Pack T4 C",
    ) + (1..96).map { "CP$it" } + listOf(
        "12v Bat Amps", "VIN", "Hx", "12v Bat Volts", "Odo(km)", "QC", "L1/L2",
        "TP-FL", "TP-FR", "TP-RR", "TP-RL", "Ambient", "SOH", "RegenWh",
        "BLevel", "epoch time", "Motor Pwr(w)", "Aux Pwr(100w)",
        "A/C Pwr(250w)", "A/C Comp(0.1MPa)", "Est Pwr A/C(50w)",
        "Est Pwr Htr(250w)", "Plug State", "Charge Mode", "Chrg Pwr", "Gear",
        "HVolt1", "HVolt2", "GPS Status", "Power SW", "BMS", "OBC",
    )

    fun row(tMs: Long, leaf: LeafState, odoKm: Double?, phoneBattery: Int?): List<String> {
        val cells = leaf.cellsMv
        val valid = cells.filter { it in 1000..4500 }        // raw cells may hold junk reads
        val max = valid.maxOrNull()
        val min = valid.minOrNull()
        val avg = if (valid.isEmpty()) null else valid.average()
        // AZE0 has no sensor 3 (0xFF): three readings are sensors 1, 2 and 4
        val t = leaf.batteryTempsC
        val (t1, t2, t4) = if (t.size == 3) Triple(t[0], t[1], t[2])
            else Triple(t.getOrNull(0), t.getOrNull(1), t.getOrNull(2))
        return listOf(
            SimpleDateFormat("MM/dd/yyyy H:mm:ss", Locale.US).format(Date(tMs)),
            "", "", "",                                        // Lat, Long, Elv (no GPS)
            f(leaf.speedKmh, 0),                               // car speed (not GPS)
            "",                                                // Gids: not readable
            leaf.socPercent?.let { Math.round(it * 10000).toString() } ?: "",
            leaf.ahCapacity?.let { Math.round(it * 10000).toString() } ?: "",
            f(avg?.let { it * 96 / 1000.0 } ?: leaf.packVolts, 2),
            "",                                                // Pack Amps
            max?.toString() ?: "", min?.toString() ?: "",
            avg?.let { Math.round(it).toString() } ?: "",
            if (max != null && min != null) (max - min).toString() else "",
            "",                                                // Judgement
            f(t1?.let(::toF), 1), f(t1, 1),
            f(t2?.let(::toF), 1), f(t2, 1),
            "na", "na",
            f(t4?.let(::toF), 1), f(t4, 1),
        ) + (0 until 96).map { cells.getOrNull(it)?.toString() ?: "" } + listOf(
            "na",                                              // 12v Bat Amps (2011/12 only)
            "",                                                // VIN
            f(leaf.hx, 2),
            leaf.aux12V?.let { f(it, 2) + "V" } ?: "",
            f(odoKm, 0),
            "", "",                                            // QC, L1/L2
            "", "", "", "",                                    // tire pressures
            f(leaf.ambientTempC?.let(::toF), 0),
            f(leaf.sohPercent, 2),
            "",                                                // RegenWh
            phoneBattery?.toString() ?: "",
            (tMs / 1000).toString(),
            "", "", "", "", "", "",                            // power by consumer
            "", "", "", "",                                    // plug, charge mode/power, gear
            f(leaf.packVolts, 2), "",                          // HVolt1 (LBC), HVolt2
            "", "",                                            // GPS Status, Power SW
            "1", "0",                                          // BMS read, OBC not read
        )
    }

    private fun toF(c: Double) = c * 1.8 + 32.0

    private fun f(v: Double?, digits: Int): String =
        if (v == null) "" else String.format(Locale.US, "%.${digits}f", v)
}
