package com.radardisco.android.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radardisco.android.R
import com.radardisco.android.model.UpdateState
import com.radardisco.android.model.UpdateStatus
import com.radardisco.android.ui.theme.RadarColors

@Composable
fun TopHeaderBar(
    updateState: UpdateState,
    onCheckUpdates: () -> Unit,
    onOpenAiSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RadarColors.BgHeader)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_radardisco_logo),
                contentDescription = "RadarDisco",
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "RadarDisco",
                        color = RadarColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val isAvailable = updateState.status == UpdateStatus.AVAILABLE
                    val isChecking = updateState.status == UpdateStatus.CHECKING
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (isAvailable) RadarColors.Emerald500.copy(alpha = 0.2f)
                                else RadarColors.BgSurfaceAlt
                            )
                            .border(
                                1.dp,
                                if (isAvailable) RadarColors.Emerald500.copy(alpha = 0.45f)
                                else RadarColors.BorderSubtle,
                                CircleShape
                            )
                            .clickable(enabled = !isChecking) { onCheckUpdates() }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = RadarColors.Sky400
                            )
                        } else {
                            Icon(
                                imageVector = if (isAvailable) Icons.Rounded.CloudDownload else Icons.Rounded.Refresh,
                                contentDescription = "Verificar atualização",
                                tint = if (isAvailable) RadarColors.Emerald400 else RadarColors.TextSecondary,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "v${updateState.currentVersion}",
                            color = if (isAvailable) RadarColors.Emerald300 else RadarColors.TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Text(
                    text = "Analisador de Espaço em Tempo Real",
                    color = RadarColors.TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(RadarColors.BgSurfaceAlt)
                .border(1.dp, RadarColors.BorderSubtle, RoundedCornerShape(10.dp))
        ) {
            IconButton(
                onClick = onOpenAiSettings,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Configurar API de IA",
                    tint = RadarColors.Fuchsia300,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
