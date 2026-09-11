package com.vorht.capture.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Pill toggle per DESIGN.md: fully rounded, ~10% black surface, 1px border, no elevation. */
@Composable
fun StatusChip(text: String, color: Color) {
    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = VorhtColors.PillSurface,
        border = BorderStroke(1.dp, color),
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun EmptyState(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = VorhtColors.Whiteout)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = VorhtColors.Whiteout.copy(alpha = 0.6f))
    }
}
