package com.leafdash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leafdash.trip.TripRecord

/** Columns shown in the table (the raw ms/dist columns are export-only). */
private const val SHOWN = 20

/** Trip log table, newest first, with CSV export. */
@Composable
fun TripLogScreen(
    trips: List<TripRecord>,
    onExport: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text(
                "Trip log",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onExport, enabled = trips.isNotEmpty()) { Text("Export CSV") }
        }
        if (trips.isEmpty()) {
            Text(
                "No trips yet. A trip is recorded from car on to car off " +
                    "(30 min without data ends it).",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }
        // one horizontal scroll state shared by header and rows keeps columns aligned
        val hScroll = rememberScrollState()
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                TableRow(
                    TripRecord.HEADER.take(SHOWN),
                    Modifier.horizontalScroll(hScroll)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    bold = true,
                )
            }
            val newestFirst = trips.asReversed()
            items(newestFirst.size) { i ->
                TableRow(
                    newestFirst[i].cells().take(SHOWN),
                    Modifier.horizontalScroll(hScroll).background(
                        if (i % 2 == 1) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, modifier: Modifier, bold: Boolean = false) {
    Row(modifier.padding(vertical = 8.dp)) {
        cells.forEachIndexed { i, c ->
            Text(
                c,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (bold) FontWeight.Bold else null,
                maxLines = if (bold) 2 else 1,
                modifier = Modifier.width(if (i == 0) 96.dp else 84.dp).padding(horizontal = 4.dp),
            )
        }
    }
}
