package com.leafdash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leafdash.poll.DashState
import com.leafdash.power.PowerTest
import kotlinx.coroutines.delay

/**
 * Guided full-throttle step test. Drives [PowerTest] from the poll stream
 * (one sample per [DashState.cycle]) and a 1 s ticker; the poller runs in
 * fast mode while this screen is open.
 */
@Composable
fun PowerTestScreen(state: DashState, onBack: () -> Unit, setFast: (Boolean) -> Unit) {
    val test = remember { PowerTest() }
    var rev by remember { mutableIntStateOf(0) }     // bump to recompose after mutations

    DisposableEffect(Unit) {
        setFast(true)
        onDispose { setFast(false) }
    }
    LaunchedEffect(state.cycle) {
        val a = state.leaf.packAmps
        val v = state.leaf.packVolts
        if (state.connected && a != null && v != null) { test.onSample(a, v, state.leaf.cellsMv); rev++ }
    }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); test.onTick(); rev++ }
    }

    @Suppress("UNUSED_EXPRESSION") rev
    val danger = Color(0xFFC62828)
    val big = test.phase == PowerTest.Phase.COUNTDOWN || test.phase == PowerTest.Phase.FLOOR ||
        test.phase == PowerTest.Phase.RELEASE
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Power test", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Live("A", state.leaf.packAmps?.let { "%.0f".format(it) } ?: "--")
            Live("V", state.leaf.packVolts?.let { "%.1f".format(it) } ?: "--")
            Live("km/h", state.leaf.speedKmh?.let { "%.0f".format(it) } ?: "--")
            Live("cell min", state.leaf.cellMinV?.let { "%.3f".format(it) } ?: "--")
        }
        if (!state.connected) {
            Text("Not connected", color = danger, style = MaterialTheme.typography.titleMedium)
        }
        Column(
            Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                test.message,
                fontSize = if (big) 72.sp else 22.sp,
                lineHeight = if (big) 80.sp else 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = when {
                    test.phase == PowerTest.Phase.FLOOR -> danger
                    test.phase == PowerTest.Phase.RELEASE -> Color(0xFF2E7D32)
                    test.phase == PowerTest.Phase.RESULT && !test.ok -> danger
                    else -> Color.Unspecified
                },
            )
        }
        Button(onClick = { test.restart(); rev++ }, modifier = Modifier.fillMaxWidth()) {
            Text("Restart")
        }
    }
}

@Composable
private fun Live(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}
