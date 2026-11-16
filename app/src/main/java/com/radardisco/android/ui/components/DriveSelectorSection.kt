package com.radardisco.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radardisco.android.model.QuickScanTarget
import com.radardisco.android.model.ScanStatus
import com.radardisco.android.model.StorageVolumeInfo
import com.radardisco.android.ui.theme.RadarColors
import com.radardisco.android.ui.theme.RadarFormatters

@Composable
fun DriveSelectorSection(
    volumes: List<StorageVolumeInfo>,
    quickTargets: List<QuickScanTarget>,
    activeRootPath: String,
    scanStatus: ScanStatus,
    hasScannedData: Boolean,
    aiAnalyzing: Boolean,
    loadingVolumes: Boolean,
    onSelectPath: (String) -> Unit,
    onPickSafFolder: () -> Unit,
    onAnalyzeWithAi: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onRefreshVolumes: () -> Unit,
    onCollapse: (() -> Unit)? = null
) {
    val isScanning = scanStatus === ScanStatus.SCANNING
    val canAnalyze = !isScanning && hasScannedData && !aiAnalyzing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RadarColors.BgSurface.copy(alpha = 0.92f))
            .border(1.dp, RadarColors.BorderSubtle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Linha 1: Título "Disco ou pasta" sem quebra + botão opcional de recolher painel
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storage,
                    contentDescription = null,
                    tint = RadarColors.Sky400,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Disco ou pasta",
                    color = RadarColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            if (onCollapse != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(RadarColors.BgSurfaceAlt)
                        .border(1.dp, RadarColors.BorderMuted, RoundedCornerShape(8.dp))
                        .clickable(onClick = onCollapse)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Recolher painel",
                        tint = RadarColors.TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Recolher",
                        color = RadarColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }

        // Linha 2: Ações principais alinhadas (Analisar com IA | Pasta | Recarregar)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Botão Analisar com IA + Engrenagem
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (canAnalyze) RadarColors.Fuchsia600.copy(alpha = 0.28f)
                        else RadarColors.BgSurfaceAlt
                    )
                    .border(
                        1.dp,
                        if (canAnalyze) RadarColors.Fuchsia500.copy(alpha = 0.55f)
                        else RadarColors.BorderSubtle,
                        RoundedCornerShape(10.dp)
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(enabled = canAnalyze) { onAnalyzeWithAi() }
                        .padding(horizontal = 10.dp)
                ) {
                    if (aiAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 1.8.dp,
                            color = RadarColors.Fuchsia300
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = if (canAnalyze) RadarColors.Fuchsia300 else RadarColors.TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            aiAnalyzing -> "Analisando..."
                            isScanning -> "IA (aguardando)"
                            else -> "Analisar com IA"
                        },
                        color = if (canAnalyze) RadarColors.Fuchsia200 else RadarColors.TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(RadarColors.BorderSubtle)
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = 34.dp, height = 34.dp)
                        .clickable(onClick = onOpenAiSettings)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Configurar API de IA",
                        tint = RadarColors.TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Escolher Pasta (SAF)
            Button(
                onClick = onPickSafFolder,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RadarColors.Sky500.copy(alpha = 0.16f)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(34.dp)
                    .border(1.dp, RadarColors.Sky400.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = RadarColors.Sky300,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Pasta",
                    color = RadarColors.Sky300,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            // Botão Recarregar com exatamente o mesmo tamanho e alinhamento
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(RadarColors.BgSurfaceAlt)
                    .border(1.dp, RadarColors.BorderSubtle, RoundedCornerShape(10.dp))
                    .clickable(enabled = !loadingVolumes, onClick = onRefreshVolumes)
            ) {
                if (loadingVolumes) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.8.dp,
                        color = RadarColors.Sky400
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Atualizar volumes",
                        tint = RadarColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Cartões de Volumes (Armazenamento Interno, Cartão SD)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            volumes.forEach { vol ->
                val isSelected = activeRootPath == vol.mountPath
                val isCritical = vol.usedPercent >= 90f
                val isWarning = vol.usedPercent >= 78f && !isCritical
                val barColor = when {
                    isCritical -> RadarColors.Rose500
                    isWarning -> RadarColors.Amber500
                    else -> RadarColors.Sky400
                }

                Column(
                    modifier = Modifier
                        .width(215.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) RadarColors.Sky500.copy(alpha = 0.14f)
                            else RadarColors.BgSurfaceAlt
                        )
                        .border(
                            1.dp,
                            if (isSelected) RadarColors.Sky400.copy(alpha = 0.7f)
                            else RadarColors.BorderSubtle,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectPath(vol.mountPath) }
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = vol.label,
                            color = RadarColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isCritical) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(RadarColors.Rose500.copy(alpha = 0.2f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = RadarColors.Rose300,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "${vol.usedPercent.toInt()}%",
                                    color = RadarColors.Rose300,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                text = "${vol.usedPercent.toInt()}%",
                                color = RadarColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(RadarColors.BgRoot)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((vol.usedPercent / 100f).coerceIn(0.02f, 1f))
                                .clip(RoundedCornerShape(99.dp))
                                .background(barColor)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${RadarFormatters.formatBytes(vol.freeBytes)} livres",
                            color = if (isCritical) RadarColors.Rose300 else RadarColors.TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "de ${RadarFormatters.formatBytes(vol.totalBytes, 0)}",
                                color = RadarColors.TextMuted,
                                fontSize = 10.sp
                            )
                            if (!isSelected) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Escanear",
                                    tint = RadarColors.Sky400,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Atalhos rápidos de pastas comuns no Android
        if (quickTargets.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickTargets.forEach { target ->
                    val selected = activeRootPath == target.path
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) RadarColors.Sky500.copy(alpha = 0.2f)
                                else RadarColors.BgRoot
                            )
                            .border(
                                1.dp,
                                if (selected) RadarColors.Sky400.copy(alpha = 0.55f)
                                else RadarColors.BorderSubtle,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectPath(target.path) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = target.name,
                            color = if (selected) RadarColors.Sky300 else RadarColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
