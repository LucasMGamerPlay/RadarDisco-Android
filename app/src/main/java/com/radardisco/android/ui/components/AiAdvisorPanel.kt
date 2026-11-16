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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radardisco.android.model.AiAnalysisReport
import com.radardisco.android.model.AiRecommendationItem
import com.radardisco.android.model.AiRiskLevel
import com.radardisco.android.model.AiSettings
import com.radardisco.android.model.ItemType
import com.radardisco.android.ui.theme.RadarColors
import com.radardisco.android.ui.theme.RadarFormatters
import com.radardisco.android.ui.viewmodel.ItemActionTarget

@Composable
fun AiAdvisorPanel(
    aiSettings: AiSettings,
    report: AiAnalysisReport?,
    analyzing: Boolean,
    error: String?,
    hasScannedData: Boolean,
    onRunAnalysis: () -> Unit,
    onOpenSettings: () -> Unit,
    onFocusFolder: (String) -> Unit,
    onSearchWeb: (String, String, ItemType) -> Unit,
    onRequestDelete: (ItemActionTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedRiskFilter by rememberSaveable { mutableStateOf("all") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RadarColors.BgSurface)
    ) {
        if (analyzing) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = RadarColors.Fuchsia400,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Consultando ${aiSettings.provider.displayName}...",
                    color = RadarColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Modelo ${aiSettings.model} está avaliando as maiores pastas, arquivos e apps via API na nuvem.",
                    color = RadarColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
            return@Column
        }

        if (report == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(RadarColors.BgSurfaceAlt)
                        .border(1.dp, RadarColors.Fuchsia500.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = RadarColors.Fuchsia400,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Consultor de Limpeza IA (API na Nuvem)",
                            color = RadarColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Envie apenas os metadados dos maiores arquivos, pastas e caches para um modelo de nuvem (Google Gemini, OpenAI, OpenRouter, Groq ou outra API HTTPS) e receba um plano seguro de liberação de espaço.",
                        color = RadarColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Text(
                        text = "Provedor ativo: ${aiSettings.provider.displayName} • ${aiSettings.model.ifBlank { "Não definido" }}",
                        color = RadarColors.Fuchsia200,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    if (!error.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(RadarColors.Rose950.copy(alpha = 0.65f))
                                .border(1.dp, RadarColors.Rose500.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = RadarColors.Rose300,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = error,
                                color = RadarColors.Rose200,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Configurar API", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = onRunAnalysis,
                            enabled = hasScannedData,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RadarColors.Fuchsia600,
                                contentColor = RadarColors.TextPrimary
                            ),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Analisar Agora", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            return@Column
        }

        val safeCount = remember(report) { report.recommendations.count { it.riskLevel == AiRiskLevel.SAFE } }
        val cautionCount = remember(report) { report.recommendations.count { it.riskLevel == AiRiskLevel.CAUTION } }
        val dangerCount = remember(report) { report.recommendations.count { it.riskLevel == AiRiskLevel.DANGER } }

        val filteredRecs = remember(report, selectedRiskFilter) {
            when (selectedRiskFilter) {
                AiRiskLevel.SAFE.wireValue -> report.recommendations.filter { it.riskLevel == AiRiskLevel.SAFE }
                AiRiskLevel.CAUTION.wireValue -> report.recommendations.filter { it.riskLevel == AiRiskLevel.CAUTION }
                AiRiskLevel.DANGER.wireValue -> report.recommendations.filter { it.riskLevel == AiRiskLevel.DANGER }
                else -> report.recommendations
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    RadarColors.Fuchsia600.copy(alpha = 0.22f),
                                    RadarColors.Indigo600.copy(alpha = 0.22f)
                                )
                            )
                        )
                        .border(1.dp, RadarColors.Fuchsia500.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Relatório do Consultor IA",
                                color = RadarColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${report.providerUsed} • ${report.modelUsed}",
                                color = RadarColors.Fuchsia200,
                                fontSize = 11.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onRunAnalysis,
                                enabled = hasScannedData,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Reanalisar",
                                    tint = RadarColors.Fuchsia200,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = "Configurar API",
                                    tint = RadarColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = report.summary,
                        color = RadarColors.TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    if (report.estimatedReclaimableBytes > 0L) {
                        Text(
                            text = "Potencial recuperável: ${RadarFormatters.formatBytes(report.estimatedReclaimableBytes)}",
                            color = RadarColors.Emerald300,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RiskFilterChip(
                        label = "Todos (${report.recommendations.size})",
                        selected = selectedRiskFilter == "all",
                        accent = RadarColors.Sky400,
                        onClick = { selectedRiskFilter = "all" }
                    )
                    RiskFilterChip(
                        label = "Seguro ($safeCount)",
                        selected = selectedRiskFilter == AiRiskLevel.SAFE.wireValue,
                        accent = RadarColors.Emerald400,
                        onClick = { selectedRiskFilter = AiRiskLevel.SAFE.wireValue }
                    )
                    RiskFilterChip(
                        label = "Cuidado ($cautionCount)",
                        selected = selectedRiskFilter == AiRiskLevel.CAUTION.wireValue,
                        accent = RadarColors.Amber400,
                        onClick = { selectedRiskFilter = AiRiskLevel.CAUTION.wireValue }
                    )
                    RiskFilterChip(
                        label = "Não Apagar ($dangerCount)",
                        selected = selectedRiskFilter == AiRiskLevel.DANGER.wireValue,
                        accent = RadarColors.Rose400,
                        onClick = { selectedRiskFilter = AiRiskLevel.DANGER.wireValue }
                    )
                }
            }

            itemsIndexed(filteredRecs, key = { idx, item -> "${item.path}_$idx" }) { _, rec ->
                AiRecommendationCard(
                    rec = rec,
                    onFocusFolder = onFocusFolder,
                    onSearchWeb = onSearchWeb,
                    onRequestDelete = onRequestDelete
                )
            }
        }
    }
}

@Composable
private fun RiskFilterChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Text(
        text = label,
        color = if (selected) accent else RadarColors.TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else RadarColors.BgRoot)
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.6f) else RadarColors.BorderSubtle,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun AiRecommendationCard(
    rec: AiRecommendationItem,
    onFocusFolder: (String) -> Unit,
    onSearchWeb: (String, String, ItemType) -> Unit,
    onRequestDelete: (ItemActionTarget) -> Unit
) {
    val (riskLabel, riskColor, riskBg) = when (rec.riskLevel) {
        AiRiskLevel.SAFE -> Triple("Seguro Apagar", RadarColors.Emerald300, RadarColors.Emerald950.copy(alpha = 0.65f))
        AiRiskLevel.CAUTION -> Triple("Avaliar com Cuidado", RadarColors.Amber300, RadarColors.Amber500.copy(alpha = 0.14f))
        AiRiskLevel.DANGER -> Triple("Não Recomendado Apagar", RadarColors.Rose300, RadarColors.Rose950.copy(alpha = 0.65f))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RadarColors.BgRoot)
            .border(1.dp, riskColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(riskBg)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = riskColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = riskLabel,
                        color = riskColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = if (rec.itemType == ItemType.FOLDER) "Pasta" else "Arquivo",
                    color = RadarColors.TextMuted,
                    fontSize = 10.sp
                )
            }

            if (rec.sizeBytes > 0L) {
                Text(
                    text = RadarFormatters.formatBytes(rec.sizeBytes),
                    color = RadarColors.Sky300,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = rec.name,
            color = RadarColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = rec.path,
            color = RadarColors.TextMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "O que é: ${rec.whatIsIt}",
            color = RadarColors.TextSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )

        Text(
            text = "Impacto: ${rec.whyAndImpact}",
            color = RadarColors.TextPrimary,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(RadarColors.BgSurfaceAlt)
                    .border(1.dp, RadarColors.BorderSubtle, RoundedCornerShape(7.dp))
                    .clickable { onSearchWeb(rec.name, rec.path, rec.itemType) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.TravelExplore,
                    contentDescription = null,
                    tint = RadarColors.Indigo400,
                    modifier = Modifier.size(13.dp)
                )
                Text("Web", color = RadarColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }

            if (rec.itemType == ItemType.FOLDER) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(RadarColors.BgSurfaceAlt)
                        .border(1.dp, RadarColors.BorderSubtle, RoundedCornerShape(7.dp))
                        .clickable { onFocusFolder(rec.path) }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        tint = RadarColors.Sky300,
                        modifier = Modifier.size(13.dp)
                    )
                    Text("Abrir", color = RadarColors.Sky300, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }

            if (rec.riskLevel != AiRiskLevel.DANGER) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(RadarColors.Rose950.copy(alpha = 0.55f))
                        .border(1.dp, RadarColors.Rose500.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
                        .clickable {
                            onRequestDelete(
                                ItemActionTarget(
                                    name = rec.name,
                                    path = rec.path,
                                    size = rec.sizeBytes,
                                    type = rec.itemType
                                )
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteForever,
                        contentDescription = null,
                        tint = RadarColors.Rose300,
                        modifier = Modifier.size(13.dp)
                    )
                    Text("Excluir", color = RadarColors.Rose300, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
