package com.calendo.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun WeekPlaceholderScreen(modifier: Modifier = Modifier) {
    PlaceholderCopy(
        title = "周视图",
        body = "下一版会提供类似 Time Blocks 的周时间轴与跨日块拖动。",
        modifier = modifier,
    )
}

@Composable
fun MonthPlaceholderScreen(modifier: Modifier = Modifier) {
    PlaceholderCopy(
        title = "月视图",
        body = "下一版会提供月历密度展示与多点分布提示。",
        modifier = modifier,
    )
}

@Composable
private fun PlaceholderCopy(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
