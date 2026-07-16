package com.pranavkd.instadown.presentation.downloads

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pranavkd.instadown.domain.model.DownloadItem
import com.pranavkd.instadown.domain.model.DownloadStatus

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var deleteTarget by remember { mutableStateOf<DownloadItem?>(null) }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete download?") },
            text = { Text("This will remove the file from your device and the downloads list.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(item.id)
                    deleteTarget = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (downloads.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                DownloadsHeader()
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            "\uE2C4",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Your archived media will appear here.\nTreat every download with respect.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { DownloadsHeader() }
                items(downloads, key = { it.id }) { item ->
                    DownloadCard(
                        item = item,
                        onPause = { viewModel.pause(item.id) },
                        onResume = { viewModel.resume(item.id) },
                        onDelete = { deleteTarget = item },
                        onRetry = { viewModel.retry(item.id) },
                        onOpen = {
                            item.mediaStoreUri?.let { uri ->
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(uri), item.outputMimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                if (intent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(intent)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadsHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 8.dp)
    ) {
        Text(
            text = "Downloads",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Your archived media collection",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DownloadCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit
) {
    val isIndeterminate = item.status == DownloadStatus.MUXING
    val progress = if (item.totalBytes > 0) {
        (item.downloadedBytes.toFloat() / item.totalBytes).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progressPercent = (progress * 100).toInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (item.thumbnailUrl != null) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title ?: item.outputFileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(status = item.status)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when {
                                item.status == DownloadStatus.COMPLETED -> formatSize(item.totalBytes)
                                item.totalBytes > 0 -> "${formatSize(item.downloadedBytes)} / ${formatSize(item.totalBytes)}"
                                else -> formatSize(item.downloadedBytes)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Progress Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusLabel = when (item.status) {
                    DownloadStatus.DOWNLOADING -> "Status: Processing..."
                    DownloadStatus.MUXING -> "Status: Finalizing..."
                    DownloadStatus.PAUSED -> "Status: Paused"
                    DownloadStatus.COMPLETED -> "Ready to view"
                    DownloadStatus.FAILED -> "Status: Failed"
                    DownloadStatus.QUEUED -> "Status: Queued"
                    DownloadStatus.CANCELLED -> "Status: Cancelled"
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isIndeterminate) {
                    Text(
                        text = "$progressPercent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            val progressColor = when (item.status) {
                DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.primary
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (isIndeterminate) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(progressColor)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(progressColor)
                    )
                }
            }

            if (item.status == DownloadStatus.FAILED && item.errorMessage != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (item.status) {
                    DownloadStatus.DOWNLOADING -> {
                        ActionButton(
                            text = "PAUSE",
                            icon = "\uE034",
                            onClick = onPause,
                            modifier = Modifier.weight(1f),
                            filled = true
                        )
                        ActionButton(
                            text = "DELETE",
                            icon = "\uE872",
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                            destructive = true
                        )
                    }
                    DownloadStatus.PAUSED -> {
                        ActionButton(
                            text = "RESUME",
                            icon = "\uE037",
                            onClick = onResume,
                            modifier = Modifier.weight(1f),
                            filled = true
                        )
                        ActionButton(
                            text = "DELETE",
                            icon = "\uE872",
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                            destructive = true
                        )
                    }
                    DownloadStatus.MUXING -> {
                        ActionButton(
                            text = "DELETE",
                            icon = "\uE872",
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                            destructive = true
                        )
                    }
                    DownloadStatus.FAILED -> {
                        ActionButton(
                            text = "RETRY",
                            icon = "\uE037",
                            onClick = onRetry,
                            modifier = Modifier.weight(1f),
                            filled = true
                        )
                        ActionButton(
                            text = "DELETE",
                            icon = "\uE872",
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                            destructive = true
                        )
                    }
                    DownloadStatus.COMPLETED -> {
                        if (item.mediaStoreUri != null) {
                            ActionButton(
                                text = "OPEN",
                                icon = "\uE037",
                                onClick = onOpen,
                                modifier = Modifier.weight(1f),
                                filled = true,
                                useSecondary = true
                            )
                        }
                        ActionButton(
                            text = "DELETE",
                            icon = "\uE872",
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                            destructive = true
                        )
                    }
                    else -> {
                        ActionButton(
                            text = "DELETE",
                            icon = "\uE872",
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                            destructive = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: DownloadStatus) {
    data class ChipStyle(val label: String, val bgColor: androidx.compose.ui.graphics.Color, val textColor: androidx.compose.ui.graphics.Color)
    val style = when (status) {
        DownloadStatus.QUEUED -> ChipStyle("QUEUED",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant)
        DownloadStatus.DOWNLOADING -> ChipStyle("DOWNLOADING",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer)
        DownloadStatus.PAUSED -> ChipStyle("PAUSED",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer)
        DownloadStatus.MUXING -> ChipStyle("FINALIZING",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer)
        DownloadStatus.COMPLETED -> ChipStyle("COMPLETED",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer)
        DownloadStatus.FAILED -> ChipStyle("FAILED",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer)
        DownloadStatus.CANCELLED -> ChipStyle("CANCELLED",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = style.bgColor
    ) {
        Text(
            text = style.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = style.textColor
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    destructive: Boolean = false,
    useSecondary: Boolean = false
) {
    val containerColor = when {
        destructive -> MaterialTheme.colorScheme.surface
        useSecondary -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    val contentColor = when {
        destructive -> MaterialTheme.colorScheme.error
        useSecondary -> MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.onPrimary
    }
    val borderColor = when {
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    if (filled) {
        Button(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor
            )
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelSmall
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = contentColor
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
        else -> "%.2f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
    }
}
