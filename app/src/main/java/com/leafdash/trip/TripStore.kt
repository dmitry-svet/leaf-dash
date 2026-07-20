package com.leafdash.trip

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.tripDataStore: DataStore<Preferences> by preferencesDataStore("trip")

/** Persists the [TripSnapshot] (lastCharge + trip windows) across app kills. */
class TripStore(private val context: Context) {

    suspend fun load(): TripSnapshot {
        val p = context.tripDataStore.data.first()
        return TripSnapshot(
            lcStartOdo = p[LC_START_ODO],
            lcKwh = p[LC_KWH] ?: 0.0,
            tripStartOdo = p[TR_START_ODO],
            tripKwh = p[TR_KWH] ?: 0.0,
            chargeMinSoc = p[CHARGE_MIN_SOC],
            emaEff = p[EMA_EFF] ?: 15.0,
        )
    }

    suspend fun loadUnitsMiles(): Boolean = context.tripDataStore.data.first()[UNITS_MILES] ?: false

    suspend fun saveUnitsMiles(miles: Boolean) {
        context.tripDataStore.edit { it[UNITS_MILES] = miles }
    }

    suspend fun loadLastDevice(): String? = context.tripDataStore.data.first()[LAST_DEVICE]

    suspend fun saveLastDevice(address: String) {
        context.tripDataStore.edit { it[LAST_DEVICE] = address }
    }

    suspend fun save(snap: TripSnapshot) {
        context.tripDataStore.edit { p ->
            snap.lcStartOdo?.let { p[LC_START_ODO] = it } ?: p.remove(LC_START_ODO)
            p[LC_KWH] = snap.lcKwh
            snap.tripStartOdo?.let { p[TR_START_ODO] = it } ?: p.remove(TR_START_ODO)
            p[TR_KWH] = snap.tripKwh
            snap.chargeMinSoc?.let { p[CHARGE_MIN_SOC] = it } ?: p.remove(CHARGE_MIN_SOC)
            p[EMA_EFF] = snap.emaEff
        }
    }

    private companion object {
        val LC_START_ODO = doublePreferencesKey("lc_start_odo")
        val LC_KWH = doublePreferencesKey("lc_kwh")
        val TR_START_ODO = doublePreferencesKey("tr_start_odo")
        val TR_KWH = doublePreferencesKey("tr_kwh")
        val CHARGE_MIN_SOC = doublePreferencesKey("charge_min_soc")
        val EMA_EFF = doublePreferencesKey("ema_eff")
        val UNITS_MILES = booleanPreferencesKey("units_miles")
        val LAST_DEVICE = stringPreferencesKey("last_device")
    }
}
