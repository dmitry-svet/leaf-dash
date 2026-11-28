package com.leafdash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leafdash.can.TireCheck
import com.leafdash.poll.DashState

/** Red banner when a tire is under the Settings threshold or the tires differ by 0.2 bar+; nothing otherwise. */
@Composable
fun TireAlertBanner(state: DashState) {
    val a = TireCheck.of(state.leaf.tiresPsi, state.tireMinBar) ?: return
    // poller order: FL, FR, RR, RL
    val names = listOf(t("FL", "ПЛ"), t("FR", "ПП"), t("RR", "ЗП"), t("RL", "ЗЛ"))
    val parts = buildList {
        if (a.low.isNotEmpty()) add(
            t("low ", "низький ") + a.low.sorted().joinToString(", ") { i ->
                names[i] + " " + "%.2f".format(state.leaf.tiresPsi[i]!! * TireCheck.BAR_PER_PSI)
            },
        )
        if (a.uneven) add(t("difference ", "різниця ") + "%.2f".format(a.spreadBar) + t(" bar", " бар"))
    }
    Text(
        t("Tire pressure: ", "Тиск у шинах: ") + parts.joinToString(" · "),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.fillMaxWidth()
            .background(AppTheme.pal.danger, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
