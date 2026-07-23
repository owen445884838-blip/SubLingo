package com.sublingo.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sublingo.app.ui.components.PillButton
import com.sublingo.app.ui.components.SoftCard

@Composable
fun LibraryScreen(viewModel: LibraryViewModel, onOpenPlayer: (String) -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "媒体库", style = MaterialTheme.typography.headlineLarge)
        Text(text = uiState.status, style = MaterialTheme.typography.bodyLarge)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(uiState.items) { item ->
                SoftCard(modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPlayer(item.id) }) {
                    Text(text = item.title, style = MaterialTheme.typography.titleLarge)
                    Text(text = "${item.source} · ${item.progressLabel}", style = MaterialTheme.typography.bodyMedium)
                    item.filePath?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
                    PillButton(text = "打开播放器", onClick = { onOpenPlayer(item.id) })
                }
            }
        }

        uiState.selectedPlayerRoute?.let {
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "播放器路由", style = MaterialTheme.typography.titleLarge)
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
