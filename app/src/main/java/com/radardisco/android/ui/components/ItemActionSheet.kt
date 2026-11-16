package com.radardisco.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radardisco.android.model.ItemType
import com.radardisco.android.ui.theme.RadarColors
import com.radardisco.android.ui.theme.RadarFormatters
import com.radardisco.android.ui.viewmodel.ItemActionTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemActionSheet(
    target: ItemActionTarget?,
    onDismiss: () -> Unit,
    onFocusFolder: (String) -> Unit,
    onOpenInFiles: (String, ItemType) -> Unit,
    onSearchWeb: (String, String, ItemType) -> Unit,
    onCopyPath: (String) -> Unit,
    onRequestDelete: (ItemActionTarget) -> Unit
) {
    if (target == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RadarColors.BgDialog,
        contentColor = RadarColors.TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (target.type == ItemType.FOLDER) {
                        Icons.Rounded.Folder
                    } else {
                        Icons.AutoMirrored.Rounded.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = if (target.type == ItemType.FOLDER) RadarColors.Sky400 else RadarColors.TextSecondary,
                    modifier = Modifier.size(26.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = target.name,
                        color = RadarColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${if (target.type == ItemType.FOLDER) "Pasta" else "Arquivo"} • ${RadarFormatters.formatBytes(target.size)}",
                        color = RadarColors.Sky300,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Text(
                text = target.path,
                color = RadarColors.TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(RadarColors.BgHeader)
                    .border(1.dp, RadarColors.BorderSubtle, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )

            HorizontalDivider(color = RadarColors.BorderSubtle)

            if (target.type == ItemType.FOLDER) {
                SheetActionRow(
                    icon = Icons.Rounded.FolderOpen,
                    title = "Abrir e explorar no RadarDisco",
                    tint = RadarColors.Sky300,
                    onClick = {
                        onFocusFolder(target.path)
                        onDismiss()
                    }
                )
            }

            SheetActionRow(
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                title = "Abrir no gerenciador de arquivos",
                tint = RadarColors.TextPrimary,
                onClick = {
                    onOpenInFiles(target.path, target.type)
                    onDismiss()
                }
            )

            SheetActionRow(
                icon = Icons.Rounded.TravelExplore,
                title = "Pesquisar o que é na Web",
                tint = RadarColors.Indigo400,
                onClick = {
                    onSearchWeb(target.name, target.path, target.type)
                    onDismiss()
                }
            )

            SheetActionRow(
                icon = Icons.Rounded.ContentCopy,
                title = "Copiar caminho completo",
                tint = RadarColors.TextPrimary,
                onClick = {
                    onCopyPath(target.path)
                    onDismiss()
                }
            )

            HorizontalDivider(color = RadarColors.BorderSubtle)

            SheetActionRow(
                icon = Icons.Rounded.DeleteForever,
                title = "Excluir permanentemente",
                tint = RadarColors.Rose400,
                containerColor = RadarColors.Rose950.copy(alpha = 0.45f),
                onClick = {
                    onRequestDelete(target)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    title: String,
    tint: Color,
    containerColor: Color = RadarColors.BgSurfaceAlt,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .border(1.dp, RadarColors.BorderSubtle, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            color = tint,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
