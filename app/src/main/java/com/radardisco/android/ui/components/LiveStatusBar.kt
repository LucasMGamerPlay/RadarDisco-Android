package com.radardisco.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radardisco.android.model.FileCategory
import com.radardisco.android.model.ScanSnapshot
import com.radardisco.android.model.ScanStatus
import com.radardisco.android.model.StorageVolumeInfo
import com.radardisco.android.ui.theme.RadarColors
import com.radardisco.android.ui.theme.RadarFormatters
import kotlin.math.max

@Composable
fun LiveStatusBar(
    snapshot: ScanSnapshot,
    activeVolume: StorageVolumeInfo? = null,
    hasUsageStatsPermission: Boolean = false,
    appsTotalBytes: Long = 0L,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRescan: () -> Unit,
    onCollapse: (() -> Unit)? = null,
    onOpenAppsTab: () -> Unit = {}
) {
    if (snapshot.status == ScanStatus.IDLE || snapshot.rootPath.isBlank()) return

    val statusText: String
    val statusColor: Color
    when (snapshot.status) {
        ScanStatus.SCANNING -> {
            statusText = "AO VIVO"
            statusColor = RadarColors.Emerald400
        }
        ScanStatus.PAUSED -> {
            statusText = "PAUSADO"
            statusColor = RadarColors.Amber400
        }
        ScanStatus.COMPLETED -> {
            statusText = "CONCLUÍDO"
            statusColor = RadarColors.Sky400
        }
        ScanStatus.CANCELLED -> {
            statusText = "PARADO"
            statusColor = RadarColors.TextSecondary
        }
        else -> {
            statusText = "ERRO"
            statusColor = RadarColors.Rose400
        }
    }

    val orderedCategories = FileCategory.entries.filter { (snapshot.categoryTotals[it] ?: 0L) > 0L }
    val totalCatBytes = max(1L, orderedCategories.sumOf { snapshot.categoryTotals[it] ?: 0L })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RadarColors.BgSurface)
            .border(1.dp, RadarColors.BorderSubtle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.16f))
                        .border(1.dp, statusColor.copy(alpha = 0.35f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    if (snapshot.status == ScanStatus.COMPLETED) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                    } else if (snapshot.status == ScanStatus.SCANNING) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = snapshot.breadcrumbs.firstOrNull()?.name ?: snapshot.rootPath,
                        color = RadarColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (snapshot.status == ScanStatus.SCANNING && snapshot.stats.currentPath.isNotBlank()) {
                        Text(
                            text = "Lendo: ${snapshot.stats.currentPath}",
                            color = RadarColors.TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Controles Pausar / Continuar / Parar / Reescanear
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (snapshot.status == ScanStatus.SCANNING) {
                    StatusActionChip("Pausar", Icons.Rounded.Pause, RadarColors.Amber300, onPause)
                }
                if (snapshot.status == ScanStatus.PAUSED) {
                    StatusActionChip("Continuar", Icons.Rounded.PlayArrow, RadarColors.Emerald300, onResume)
                }
                if (snapshot.status == ScanStatus.SCANNING || snapshot.status == ScanStatus.PAUSED) {
                    StatusActionChip("Parar", Icons.Rounded.Stop, RadarColors.Rose300, onCancel)
                }
                if (snapshot.status == ScanStatus.COMPLETED || snapshot.status == ScanStatus.CANCELLED || snapshot.status == ScanStatus.ERROR) {
                    StatusActionChip("Reescanear", Icons.Rounded.Refresh, RadarColors.Sky300, onRescan)
                }
                if (onCollapse != null && snapshot.status != ScanStatus.SCANNING) {
                    StatusActionChip("Encolher", Icons.Rounded.KeyboardArrowUp, RadarColors.TextSecondary, onCollapse)
                }
            }
        }

        // Métricas ao vivo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricStat("Mapeado", RadarFormatters.formatBytes(snapshot.stats.totalBytes), RadarColors.Sky300)
            MetricStat("Arquivos", RadarFormatters.formatNumber(snapshot.stats.scannedFiles), RadarColors.TextPrimary)
            MetricStat("Pastas", RadarFormatters.formatNumber(snapshot.stats.scannedFolders), RadarColors.TextPrimary)
            MetricStat("Velocidade", "${RadarFormatters.formatNumber(snapshot.stats.filesPerSec)} arq/s", RadarColors.Emerald300)
            MetricStat("Tempo", RadarFormatters.formatDuration(snapshot.stats.elapsedMs), RadarColors.TextSecondary)
            if (snapshot.stats.skippedCount > 0) {
                MetricStat("Restritos", RadarFormatters.formatNumber(snapshot.stats.skippedCount), RadarColors.Amber300)
            }
        }

        // Nota transparente explicando a diferença entre arquivos acessíveis vs Apps/Android/data/Sistema
        val unmappedVolumeBytes = if (
            snapshot.status == ScanStatus.COMPLETED &&
            activeVolume != null &&
            snapshot.rootPath.trimEnd('/') == activeVolume.mountPath.trimEnd('/')
        ) {
            max(0L, activeVolume.usedBytes - snapshot.stats.totalBytes)
        } else {
            0L
        }

        if (unmappedVolumeBytes > 1_000_000_000L) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(RadarColors.BgSurfaceAlt)
                    .border(1.dp, RadarColors.BorderSubtle, RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenAppsTab)
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = RadarColors.Amber400,
                    modifier = Modifier.size(14.dp)
                )
                val infoText = if (hasUsageStatsPermission && appsTotalBytes > 0L) {
                    "+${RadarFormatters.formatBytes(unmappedVolumeBytes)} do disco: Apps (${RadarFormatters.formatBytes(appsTotalBytes)}) + Android/data restrito e Sistema"
                } else {
                    "+${RadarFormatters.formatBytes(unmappedVolumeBytes)} em Apps, Android/data restrito e Sistema • Ver Apps"
                }
                Text(
                    text = infoText,
                    color = RadarColors.TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = RadarColors.Sky300,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Barra empilhada por categoria
        if (orderedCategories.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(RadarColors.BgRoot)
                    .padding(1.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                orderedCategories.forEach { cat ->
                    val bytes = snapshot.categoryTotals[cat] ?: 0L
                    val fraction = (bytes.toFloat() / totalCatBytes.toFloat())
                    if (fraction >= 0.005f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(fraction)
                                .background(RadarFormatters.metaFor(cat).accent)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                orderedCategories.forEach { cat ->
                    val meta = RadarFormatters.metaFor(cat)
                    val bytes = snapshot.categoryTotals[cat] ?: 0L
                    val pct = ((bytes.toDouble() / totalCatBytes.toDouble()) * 100.0).toInt()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(meta.accent)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${meta.shortLabel}: ${RadarFormatters.formatBytes(bytes)} ($pct%)",
                            color = RadarColors.TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricStat(label: String, value: String, valueColor: Color) {
    Column {
        Text(
            text = label.uppercase(),
            color = RadarColors.TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatusActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun CollapsedScanSummaryBar(
    snapshot: ScanSnapshot,
    onExpand: () -> Unit,
    onRescan: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RadarColors.BgSurface)
            .border(1.dp, RadarColors.BorderSubtle)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = if (snapshot.status == ScanStatus.COMPLETED) RadarColors.Emerald400 else RadarColors.Sky400,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = snapshot.breadcrumbs.firstOrNull()?.name ?: snapshot.rootPath.ifBlank { "Disco" },
                color = RadarColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (snapshot.stats.totalBytes > 0L) {
                Text(
                    text = "• ${RadarFormatters.formatBytes(snapshot.stats.totalBytes)}",
                    color = RadarColors.Sky300,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (snapshot.status != ScanStatus.IDLE && snapshot.status != ScanStatus.SCANNING) {
                StatusActionChip("Reescanear", Icons.Rounded.Refresh, RadarColors.Sky300, onRescan)
            }
            StatusActionChip("Expandir", Icons.Rounded.KeyboardArrowDown, RadarColors.TextPrimary, onExpand)
        }
    }
}
