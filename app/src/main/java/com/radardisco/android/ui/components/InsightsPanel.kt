package com.radardisco.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radardisco.android.model.AppStorageEntry
import com.radardisco.android.model.ItemType
import com.radardisco.android.model.ScanSnapshot
import com.radardisco.android.ui.theme.RadarColors
import com.radardisco.android.ui.theme.RadarFormatters
import com.radardisco.android.ui.viewmodel.ItemActionTarget

@Composable
fun InsightsPanel(
    snapshot: ScanSnapshot,
    appStorageEntries: List<AppStorageEntry>,
    hasUsageStatsPermission: Boolean,
    loadingApps: Boolean,
    selectedSubTab: Int = 0,
    onSelectSubTab: (Int) -> Unit = {},
    onOpenActions: (ItemActionTarget) -> Unit,
    onRequestUsageStatsPermission: () -> Unit,
    onOpenAppSettings: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val subTab = selectedSubTab.coerceIn(0, 2)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RadarColors.BgSurface)
    ) {
        // Sub-abas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RadarColors.BgSurfaceAlt)
                .border(1.dp, RadarColors.BorderSubtle)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SubTabChip(
                label = "Top Arquivos (${snapshot.topFiles.size})",
                selected = subTab == 0,
                onClick = { onSelectSubTab(0) },
                modifier = Modifier.weight(1f)
            )
            SubTabChip(
                label = "Vilões >10 MB (${snapshot.spaceHogs.size})",
                selected = subTab == 1,
                onClick = { onSelectSubTab(1) },
                modifier = Modifier.weight(1f)
            )
            SubTabChip(
                label = "Apps (${appStorageEntries.size})",
                selected = subTab == 2,
                onClick = { onSelectSubTab(2) },
                modifier = Modifier.weight(1f)
            )
        }

        when (subTab) {
            0 -> {
                if (snapshot.topFiles.isEmpty()) {
                    EmptyInsightPlaceholder("Escaneie uma pasta para listar os maiores arquivos individuais.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(snapshot.topFiles, key = { _, it -> it.path }) { idx, file ->
                            val meta = RadarFormatters.metaFor(file.category)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(RadarColors.BgRoot)
                                    .border(1.dp, RadarColors.BorderSubtle, RoundedCornerShape(10.dp))
                                    .clickable {
                                        onOpenActions(
                                            ItemActionTarget(
                                                name = file.name,
                                                path = file.path,
                                                size = file.size,
                                                type = ItemType.FILE
                                            )
                                        )
                                    }
                                    .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "#${idx + 1}",
                                    color = RadarColors.Sky300,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(meta.accent)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        color = RadarColors.TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = file.parentPath,
                                        color = RadarColors.TextMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = RadarFormatters.formatBytes(file.size),
                                    color = RadarColors.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = {
                                        onOpenActions(
                                            ItemActionTarget(
                                                name = file.name,
                                                path = file.path,
                                                size = file.size,
                                                type = ItemType.FILE
                                            )
                                        )
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "Ações",
                                        tint = RadarColors.TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                if (snapshot.spaceHogs.isEmpty()) {
                    EmptyInsightPlaceholder("Nenhum vilão (>10 MB) detectado até o momento.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(snapshot.spaceHogs, key = { _, it -> it.path }) { _, hog ->
                            val meta = RadarFormatters.metaFor(hog.category)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(RadarColors.BgRoot)
                                    .border(1.dp, RadarColors.BorderSubtle, RoundedCornerShape(10.dp))
                                    .clickable {
                                        onOpenActions(
                                            ItemActionTarget(
                                                name = hog.name,
                                                path = hog.path,
                                                size = hog.size,
                                                type = ItemType.FOLDER
                                            )
                                        )
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(meta.accent)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = hog.name,
                                            color = RadarColors.TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Text(
                                            text = hog.kind,
                                            color = RadarColors.Amber300,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(RadarColors.Amber500.copy(alpha = 0.16f))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                    Text(
                                        text = "${RadarFormatters.formatNumber(hog.fileCount)} arquivos • ${hog.path}",
                                        color = RadarColors.TextMuted,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = RadarFormatters.formatBytes(hog.size),
                                    color = RadarColors.Sky300,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                if (!hasUsageStatsPermission) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = RadarColors.Amber400,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Tamanho de Apps, Dados & Cache",
                            color = RadarColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Conceda Acesso de Uso (Usage Access) para o RadarDisco consultar o StorageStatsManager e mostrar quanto cada app consome em APK, Dados e Cache.",
                            color = RadarColors.TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onRequestUsageStatsPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RadarColors.Sky500,
                                contentColor = RadarColors.BgRoot
                            )
                        ) {
                            Text("Conceder Acesso de Uso", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                } else if (loadingApps) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = RadarColors.Sky400)
                    }
                } else if (appStorageEntries.isEmpty()) {
                    EmptyInsightPlaceholder("Nenhum aplicativo com uso reportado foi encontrado.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(appStorageEntries, key = { _, it -> it.packageName }) { _, app ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(RadarColors.BgRoot)
                                    .border(1.dp, RadarColors.BorderSubtle, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Android,
                                        contentDescription = null,
                                        tint = RadarColors.Emerald400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.appName,
                                            color = RadarColors.TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = app.packageName,
                                            color = RadarColors.TextMuted,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = RadarFormatters.formatBytes(app.totalBytes),
                                        color = RadarColors.Sky300,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "App: ${RadarFormatters.formatBytes(app.appBytes)} • Dados: ${RadarFormatters.formatBytes(app.dataBytes)} • Cache: ${RadarFormatters.formatBytes(app.cacheBytes)}",
                                        color = RadarColors.TextSecondary,
                                        fontSize = 10.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(RadarColors.BgSurfaceAlt)
                                            .border(1.dp, RadarColors.BorderMuted, RoundedCornerShape(6.dp))
                                            .clickable { onOpenAppSettings(app.packageName) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Settings,
                                            contentDescription = null,
                                            tint = RadarColors.TextSecondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Config",
                                            color = RadarColors.TextPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) RadarColors.Sky500.copy(alpha = 0.2f) else RadarColors.BgRoot
            )
            .border(
                1.dp,
                if (selected) RadarColors.Sky400 else RadarColors.BorderSubtle,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) RadarColors.Sky300 else RadarColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyInsightPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = RadarColors.TextMuted,
            fontSize = 13.sp
        )
    }
}
