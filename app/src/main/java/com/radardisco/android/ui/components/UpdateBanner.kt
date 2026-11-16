package com.radardisco.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radardisco.android.model.UpdateState
import com.radardisco.android.model.UpdateStatus
import com.radardisco.android.ui.theme.RadarColors

@Composable
fun UpdateBanner(
    updateState: UpdateState,
    dismissed: Boolean,
    onOpenDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    if (dismissed || updateState.status != UpdateStatus.AVAILABLE) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(RadarColors.Emerald950.copy(alpha = 0.85f))
            .border(1.dp, RadarColors.Emerald500.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudDownload,
            contentDescription = null,
            tint = RadarColors.Emerald400,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Nova versão v${updateState.latestVersion} disponível",
                color = RadarColors.Emerald300,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Baixe o APK mais recente diretamente pelo GitHub Releases.",
                color = RadarColors.TextSecondary,
                fontSize = 11.sp
            )
        }
        Button(
            onClick = onOpenDownload,
            colors = ButtonDefaults.buttonColors(containerColor = RadarColors.Emerald500),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Baixar APK",
                color = RadarColors.BgRoot,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Fechar aviso",
                tint = RadarColors.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
