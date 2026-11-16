package com.radardisco.android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radardisco.android.model.ItemType
import com.radardisco.android.model.ScanItemSnapshot
import com.radardisco.android.model.ScanSnapshot
import com.radardisco.android.ui.theme.RadarColors
import com.radardisco.android.ui.theme.RadarFormatters
import com.radardisco.android.ui.viewmodel.ItemActionTarget

@Composable
fun FolderExplorerList(
    snapshot: ScanSnapshot,
    selectedPath: String?,
    onSelectPath: (String?) -> Unit,
    onFocusFolder: (String) -> Unit,
    onOpenActions: (ItemActionTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredItems = remember(snapshot.items, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            snapshot.items
        } else {
            snapshot.items.filter { it.name.contains(q, ignoreCase = true) }
        }
    }

    val parentBreadcrumb = if (snapshot.breadcrumbs.size >= 2) {
        snapshot.breadcrumbs[snapshot.breadcrumbs.size - 2]
    } else {
        null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RadarColors.BgSurface)
    ) {
        // Barra de Breadcrumbs + botão Subir
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RadarColors.BgSurfaceAlt)
                .border(1.dp, RadarColors.BorderSubtle)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (parentBreadcrumb != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(RadarColors.BgRoot)
                        .border(1.dp, RadarColors.BorderMuted, RoundedCornerShape(7.dp))
                        .clickable { onFocusFolder(parentBreadcrumb.path) }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = "Subir nível",
                        tint = RadarColors.Sky300,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Subir",
                        color = RadarColors.Sky300,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                snapshot.breadcrumbs.forEachIndexed { index, crumb ->
                    if (index > 0) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = RadarColors.TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    val isLast = index == snapshot.breadcrumbs.lastIndex
                    Text(
                        text = crumb.name,
                        color = if (isLast) RadarColors.TextPrimary else RadarColors.Sky300,
                        fontSize = 12.sp,
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = !isLast) { onFocusFolder(crumb.path) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = RadarFormatters.formatBytes(snapshot.focusSize),
                color = RadarColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Filtro rápido
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Filtrar pastas e arquivos nesta pasta...",
                    fontSize = 12.sp,
                    color = RadarColors.TextMuted
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = RadarColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Limpar filtro",
                            tint = RadarColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = RadarColors.BgRoot,
                unfocusedContainerColor = RadarColors.BgRoot,
                focusedBorderColor = RadarColors.Sky500,
                unfocusedBorderColor = RadarColors.BorderSubtle,
                focusedTextColor = RadarColors.TextPrimary,
                unfocusedTextColor = RadarColors.TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )

        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) {
                        "Nenhum item corresponde ao filtro \"$searchQuery\"."
                    } else {
                        "Esta pasta está vazia ou ainda está sendo lida."
                    },
                    color = RadarColors.TextMuted,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredItems, key = { it.path }) { item ->
                    FolderItemRow(
                        item = item,
                        isSelected = item.path == selectedPath,
                        onTap = {
                            onSelectPath(item.path)
                            if (item.type == ItemType.FOLDER) {
                                onFocusFolder(item.path)
                            } else {
                                onOpenActions(
                                    ItemActionTarget(
                                        name = item.name,
                                        path = item.path,
                                        size = item.size,
                                        type = item.type
                                    )
                                )
                            }
                        },
                        onMore = {
                            onSelectPath(item.path)
                            onOpenActions(
                                ItemActionTarget(
                                    name = item.name,
                                    path = item.path,
                                    size = item.size,
                                    type = item.type
                                )
                            )
                        }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderItemRow(
    item: ScanItemSnapshot,
    isSelected: Boolean,
    onTap: () -> Unit,
    onMore: () -> Unit
) {
    val meta = RadarFormatters.metaFor(item.category)
    val bgColor = if (isSelected) RadarColors.BgSurfaceHover else RadarColors.BgRoot
    val borderColor = if (isSelected) RadarColors.Sky400 else RadarColors.BorderSubtle

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = onMore
            )
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(meta.accent)
            )
            Icon(
                imageVector = if (item.type == ItemType.FOLDER) {
                    Icons.Rounded.Folder
                } else {
                    Icons.AutoMirrored.Rounded.InsertDriveFile
                },
                contentDescription = null,
                tint = if (item.type == ItemType.FOLDER) meta.accent else RadarColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.name,
                        color = RadarColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(11.dp),
                            strokeWidth = 1.5.dp,
                            color = RadarColors.Sky400
                        )
                    }
                    if (item.accessDenied) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Restrito",
                            tint = RadarColors.Amber400,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val subtitle = if (item.type == ItemType.FOLDER) {
                        "${RadarFormatters.formatNumber(item.fileCount)} arq • ${RadarFormatters.formatNumber(item.folderCount)} pastas"
                    } else {
                        meta.shortLabel
                    }
                    Text(
                        text = subtitle,
                        color = RadarColors.TextMuted,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                    if (item.accessDenied) {
                        Text(
                            text = "Restrito Android 11+",
                            color = RadarColors.Amber300,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(RadarColors.Amber500.copy(alpha = 0.16f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    if (item.specialBadge != null) {
                        Text(
                            text = item.specialBadge,
                            color = RadarColors.Amber300,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(RadarColors.Amber500.copy(alpha = 0.16f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = RadarFormatters.formatBytes(item.size),
                    color = RadarColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = String.format("%.1f%%", item.percentOfParent),
                    color = RadarColors.TextSecondary,
                    fontSize = 10.sp
                )
            }

            IconButton(
                onClick = onMore,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Ações do item",
                    tint = RadarColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Barra proporcional ao pai
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 6.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(RadarColors.BgSurfaceAlt)
        ) {
            val fraction = (item.percentOfParent / 100f).coerceIn(0f, 1f)
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(RoundedCornerShape(99.dp))
                        .background(meta.accent)
                )
            }
        }
    }
}
