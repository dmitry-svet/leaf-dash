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
            lcKm = p[LC_KM] ?: 0.0,
            lcKwh = p[LC_KWH] ?: 0.0,
            tripKm = p[TR_KM] ?: 0.0,
            tripKwh = p[TR_KWH] ?: 0.0,
            chargeMinSoc = p[CHARGE_MIN_SOC],
            emaEff = p[EMA_EFF] ?: 15.0,
            lifetimeKm = p[LIFETIME_KM] ?: 0.0,
            lifetimeKwh = p[LIFETIME_KWH] ?: 0.0,
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
            p[LC_KM] = snap.lcKm
            p[LC_KWH] = snap.lcKwh
            p[TR_KM] = snap.tripKm
            p[TR_KWH] = snap.tripKwh
            snap.chargeMinSoc?.let { p[CHARGE_MIN_SOC] = it } ?: p.remove(CHARGE_MIN_SOC)
            p[EMA_EFF] = snap.emaEff
            p[LIFETIME_KM] = snap.lifetimeKm
            p[LIFETIME_KWH] = snap.lifetimeKwh
        }
    }

    private companion object {
        val LC_KM = doublePreferencesKey("lc_km")
        val LC_KWH = doublePreferencesKey("lc_kwh")
        val TR_KM = doublePreferencesKey("tr_km")
        val TR_KWH = doublePreferencesKey("tr_kwh")
        val CHARGE_MIN_SOC = doublePreferencesKey("charge_min_soc")
        val EMA_EFF = doublePreferencesKey("ema_eff")
        val LIFETIME_KM = doublePreferencesKey("lifetime_km")
        val LIFETIME_KWH = doublePreferencesKey("lifetime_kwh")
        val UNITS_MILES = booleanPreferencesKey("units_miles")
        val LAST_DEVICE = stringPreferencesKey("last_device")
    }
}
