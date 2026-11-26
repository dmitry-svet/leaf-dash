package com.leafdash.trip

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.tripDataStore: DataStore<Preferences> by preferencesDataStore("trip")

/** Persists the [TripSnapshot] (lastCharge + trip windows) across app kills. */
class TripStore(private val context: Context) {

    suspend fun load(): TripSnapshot {
        val p = context.tripDataStore.data.first()
        // schema changed to app-tracked accumulation; drop older inconsistent data
        if ((p[SCHEMA_VER] ?: 0) < SCHEMA_CURRENT) return TripSnapshot()
        return TripSnapshot(
            lcKm = p[LC_KM] ?: 0.0,
            lcKwh = p[LC_KWH] ?: 0.0,
            tripKm = p[TR_KM] ?: 0.0,
            tripKwh = p[TR_KWH] ?: 0.0,
            chargeMinSoc = p[CHARGE_MIN_SOC],
            emaEff = p[EMA_EFF] ?: 15.0,
            lifetimeKm = p[LIFETIME_KM] ?: 0.0,
            lifetimeKwh = p[LIFETIME_KWH] ?: 0.0,
            tripRegenKwh = p[TR_REGEN] ?: 0.0,
        )
    }

    suspend fun loadUnitsMiles(): Boolean = context.tripDataStore.data.first()[UNITS_MILES] ?: false

    suspend fun saveUnitsMiles(miles: Boolean) {
        context.tripDataStore.edit { it[UNITS_MILES] = miles }
    }

    suspend fun loadLogEnabled(): Boolean = context.tripDataStore.data.first()[LOG_ENABLED] ?: false

    suspend fun saveLogEnabled(on: Boolean) {
        context.tripDataStore.edit { it[LOG_ENABLED] = on }
    }

    suspend fun loadLogUrl(): String =
        context.tripDataStore.data.first()[LOG_URL] ?: com.leafdash.log.LogStreamer.DEFAULT_URL

    suspend fun saveLogUrl(url: String) {
        context.tripDataStore.edit { it[LOG_URL] = url }
    }

    suspend fun loadReserveKwh(): Double =
        context.tripDataStore.data.first()[RESERVE_KWH] ?: DEFAULT_RESERVE_KWH

    suspend fun saveReserveKwh(kwh: Double) {
        context.tripDataStore.edit { it[RESERVE_KWH] = kwh }
    }

    suspend fun loadDataLogEnabled(): Boolean =
        context.tripDataStore.data.first()[DATA_LOG_ENABLED] ?: true

    suspend fun saveDataLogEnabled(on: Boolean) {
        context.tripDataStore.edit { it[DATA_LOG_ENABLED] = on }
    }

    suspend fun loadStreamEnabled(): Boolean =
        context.tripDataStore.data.first()[STREAM_ENABLED] ?: false

    suspend fun saveStreamEnabled(on: Boolean) {
        context.tripDataStore.edit { it[STREAM_ENABLED] = on }
    }

    suspend fun loadLangUk(): Boolean = context.tripDataStore.data.first()[LANG_UK] ?: false

    suspend fun saveLangUk(uk: Boolean) {
        context.tripDataStore.edit { it[LANG_UK] = uk }
    }

    suspend fun loadStatsView(): com.leafdash.stats.StatsView {
        val p = context.tripDataStore.data.first()
        val d = com.leafdash.stats.StatsView()
        return com.leafdash.stats.StatsView(
            barsKwh = p[STATS_BARS_KWH] ?: d.barsKwh,
            perKwh = p[STATS_PER_KWH] ?: d.perKwh,
            consumption = p[STATS_CONSUMPTION] ?: d.consumption,
            soh = p[STATS_SOH] ?: d.soh,
            hx = p[STATS_HX] ?: d.hx,
        )
    }

    suspend fun saveStatsView(v: com.leafdash.stats.StatsView) {
        context.tripDataStore.edit {
            it[STATS_BARS_KWH] = v.barsKwh
            it[STATS_PER_KWH] = v.perKwh
            it[STATS_CONSUMPTION] = v.consumption
            it[STATS_SOH] = v.soh
            it[STATS_HX] = v.hx
        }
    }

    /** Load screen style: true = bars, false = dials. */
    suspend fun loadLoadBars(): Boolean = context.tripDataStore.data.first()[LOAD_BARS] ?: false

    suspend fun saveLoadBars(bars: Boolean) {
        context.tripDataStore.edit { it[LOAD_BARS] = bars }
    }

    /** Last successful update check: time and the newest tag seen then. */
    suspend fun loadUpdateCheck(): Pair<Long?, String?> {
        val p = context.tripDataStore.data.first()
        return p[UPDATE_CHECKED_MS] to p[UPDATE_NEWEST]
    }

    suspend fun saveUpdateCheck(ms: Long, newest: String) {
        context.tripDataStore.edit {
            it[UPDATE_CHECKED_MS] = ms
            it[UPDATE_NEWEST] = newest
        }
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
            p[TR_REGEN] = snap.tripRegenKwh
            p[SCHEMA_VER] = SCHEMA_CURRENT
        }
    }

    private companion object {
        val LC_KM = doublePreferencesKey("lc_km")
        val TR_REGEN = doublePreferencesKey("trip_regen_kwh")
        val LC_KWH = doublePreferencesKey("lc_kwh")
        val TR_KM = doublePreferencesKey("tr_km")
        val TR_KWH = doublePreferencesKey("tr_kwh")
        val CHARGE_MIN_SOC = doublePreferencesKey("charge_min_soc")
        val EMA_EFF = doublePreferencesKey("ema_eff")
        val LIFETIME_KM = doublePreferencesKey("lifetime_km")
        val LIFETIME_KWH = doublePreferencesKey("lifetime_kwh")
        val UNITS_MILES = booleanPreferencesKey("units_miles")
        val LOG_ENABLED = booleanPreferencesKey("log_enabled")
        val LOG_URL = stringPreferencesKey("log_url")
        val STREAM_ENABLED = booleanPreferencesKey("stream_enabled")
        val RESERVE_KWH = doublePreferencesKey("reserve_kwh")
        val DATA_LOG_ENABLED = booleanPreferencesKey("data_log_enabled")
        val LAST_DEVICE = stringPreferencesKey("last_device")
        val LANG_UK = booleanPreferencesKey("lang_uk")
        val UPDATE_CHECKED_MS = longPreferencesKey("update_checked_ms")
        val UPDATE_NEWEST = stringPreferencesKey("update_newest")
        val STATS_BARS_KWH = booleanPreferencesKey("stats_bars_kwh")
        val STATS_PER_KWH = booleanPreferencesKey("stats_per_kwh")
        val STATS_CONSUMPTION = booleanPreferencesKey("stats_consumption")
        val STATS_SOH = booleanPreferencesKey("stats_soh")
        val STATS_HX = booleanPreferencesKey("stats_hx")
        val LOAD_BARS = booleanPreferencesKey("load_bars")
        val SCHEMA_VER = intPreferencesKey("schema_ver")
        const val SCHEMA_CURRENT = 2
        const val DEFAULT_RESERVE_KWH = 2.0
    }
}
