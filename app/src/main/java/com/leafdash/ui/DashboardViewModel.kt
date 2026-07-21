package com.leafdash.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leafdash.poll.DashState
import com.leafdash.poll.LeafPoller
import com.leafdash.transport.Transport
import com.leafdash.trip.TripStore
import com.leafdash.trip.TripTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the session lifecycle and folds the vehicle poll (battery kWh + CAN
 * odometer) into the economy windows (TripTracker).
 */
class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val tripStore = TripStore(app)

    private val _state = MutableStateFlow(DashState())
    val state: StateFlow<DashState> = _state.asStateFlow()

    private var poller: LeafPoller? = null
    private var tracker: TripTracker? = null
    private var sessionJob: Job? = null
    private var collectJob: Job? = null
    private var samples = 0
    private var lastLeaf = com.leafdash.can.LeafState()   // retained across reconnects

    @Volatile private var unitsMiles = false

    /** Whether to auto-reconnect (disabled by a manual disconnect). */
    @Volatile var autoReconnect = true
        private set

    private val _lastDevice = MutableStateFlow<String?>(null)
    val lastDevice: StateFlow<String?> = _lastDevice.asStateFlow()

    init {
        viewModelScope.launch {
            unitsMiles = tripStore.loadUnitsMiles()
            _state.value = _state.value.copy(odoMiles = unitsMiles)
            _lastDevice.value = tripStore.loadLastDevice()
        }
    }

    /** Remember the Bluetooth device for auto-reconnect next launch. */
    fun rememberDevice(address: String) {
        _lastDevice.value = address
        viewModelScope.launch { tripStore.saveLastDevice(address) }
    }

    /** Set car odometer units (km / mi); persisted. */
    fun setUnits(miles: Boolean) {
        if (unitsMiles == miles) return
        unitsMiles = miles
        poller?.setUnitsMiles(miles)
        _state.value = _state.value.copy(odoMiles = miles)
        viewModelScope.launch { tripStore.saveUnitsMiles(miles) }
    }

    fun connect(transport: Transport, active: Boolean) {
        if (sessionJob?.isActive == true) return
        autoReconnect = true
        // assigned before this returns, so a second connect() (double-tap, or the
        // auto-reconnect loop racing a manual connect) hits the guard above
        sessionJob = viewModelScope.launch {
            val t = TripTracker(tripStore.load())
            t.onSessionStart()
            tracker = t
            val p = LeafPoller(transport, active = active)
            p.setUnitsMiles(unitsMiles)
            poller = p

            collectJob?.cancel()
            collectJob = viewModelScope.launch {
                p.state.collect { ps ->
                    ps.odometerKm?.let { km ->     // already km + smoothed by poller
                        t.onSample(ps.leaf.kwhRemaining, km, ps.leaf.socPercent, ps.leaf.speedKmh)
                        if (++samples % 20 == 0) tripStore.save(t.snapshot())
                    }
                    // keep last known values through connecting/reconnect (empty leaf)
                    val leaf = if (ps.leaf == com.leafdash.can.LeafState()) lastLeaf else ps.leaf
                    lastLeaf = leaf
                    _state.value = ps.copy(
                        leaf = leaf,
                        lastCharge = t.lastCharge.copy(),
                        carOn = t.carOn.copy(),
                        trip = t.trip.copy(),
                        lifetime = t.lifetime.copy(),
                        odoMiles = unitsMiles,
                        avgKwhPer100 = t.avgKwhPer100,
                    )
                }
            }
            withContext(Dispatchers.IO) { p.runBlocking() }
        }
    }

    fun disconnect() {
        autoReconnect = false      // manual disconnect stops auto-retry
        poller?.stop()
        collectJob?.cancel()
        // keep last data on screen, but reflect disconnected status
        _state.value = _state.value.copy(connected = false, connecting = false)
        tracker?.let { t -> viewModelScope.launch { tripStore.save(t.snapshot()) } }
    }

    fun resetTrip() = tracker?.resetTrip()

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }
}
