package com.radardisco.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radardisco.android.core.scanner.TreemapInput
import com.radardisco.android.core.scanner.TreemapRect
import com.radardisco.android.core.scanner.computeSquarifiedTreemap
import com.radardisco.android.model.FileCategory
import com.radardisco.android.model.ItemType
import com.radardisco.android.model.ScanItemSnapshot
import com.radardisco.android.model.ScanSnapshot
import com.radardisco.android.ui.theme.RadarColors
import com.radardisco.android.ui.theme.RadarFormatters
import com.radardisco.android.ui.viewmodel.ItemActionTarget
import kotlin.math.max

@Composable
fun TreemapCanvas(
    snapshot: ScanSnapshot,
    selectedPath: String?,
    onSelectPath: (String?) -> Unit,
    onFocusFolder: (String) -> Unit,
    onOpenActions: (ItemActionTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleItems = remember(snapshot.items) {
        snapshot.items
            .filter { it.size > 0L }
            .take(48)
    }

    val selectedItem = remember(visibleItems, selectedPath) {
        visibleItems.firstOrNull { it.path == selectedPath }
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val rects: List<TreemapRect<ScanItemSnapshot>> = remember(visibleItems, canvasSize) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0 || visibleItems.isEmpty()) {
            emptyList()
        } else {
            computeSquarifiedTreemap(
                items = visibleItems.map { TreemapInput(it, it.size.toDouble()) },
                totalWidth = canvasSize.width.toFloat(),
                totalHeight = canvasSize.height.toFloat()
            )
        }
    }

    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RadarColors.BgRoot)
    ) {
        // Barra contextual do item selecionado / foco
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RadarColors.BgSurfaceAlt)
                .border(1.dp, RadarColors.BorderSubtle)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (selectedItem != null) {
                    Text(
                        text = "${selectedItem.name} (${RadarFormatters.formatBytes(selectedItem.size)})",
                        color = RadarColors.Sky300,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = String.format(
                            "%.1f%% da pasta atual • Toque 2x ou em Abrir para explorar",
                            selectedItem.percentOfParent
                        ),
                        color = RadarColors.TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = "Mapa de Ocupação (${visibleItems.size} maiores itens)",
                        color = RadarColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Toque: selecionar • 2x toque: abrir pasta • Segure: ações",
                        color = RadarColors.TextMuted,
                        fontSize = 10.sp
                    )
                }
            }

            if (selectedItem != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (selectedItem.type == ItemType.FOLDER) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(RadarColors.Sky500.copy(alpha = 0.2f))
                                .border(1.dp, RadarColors.Sky400.copy(alpha = 0.5f), RoundedCornerShape(7.dp))
                                .clickable { onFocusFolder(selectedItem.path) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                tint = RadarColors.Sky300,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Abrir",
                                color = RadarColors.Sky300,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(RadarColors.BgRoot)
                            .border(1.dp, RadarColors.BorderMuted, RoundedCornerShape(7.dp))
                            .clickable {
                                onOpenActions(
                                    ItemActionTarget(
                                        name = selectedItem.name,
                                        path = selectedItem.path,
                                        size = selectedItem.size,
                                        type = selectedItem.type
                                    )
                                )
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = null,
                            tint = RadarColors.TextPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Ações",
                            color = RadarColors.TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Área do Canvas Treemap
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            if (visibleItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sem dados positivos nesta pasta para desenhar o mapa.",
                        color = RadarColors.TextMuted,
                        fontSize = 13.sp
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(rects) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val hit = rects.firstOrNull { r ->
                                        offset.x >= r.x && offset.x <= r.x + r.width &&
                                            offset.y >= r.y && offset.y <= r.y + r.height
                                    }?.data
                                    if (hit != null) {
                                        if (hit.path == selectedPath && hit.type == ItemType.FOLDER) {
                                            onFocusFolder(hit.path)
                                        } else {
                                            onSelectPath(hit.path)
                                        }
                                    }
                                },
                                onDoubleTap = { offset ->
                                    val hit = rects.firstOrNull { r ->
                                        offset.x >= r.x && offset.x <= r.x + r.width &&
                                            offset.y >= r.y && offset.y <= r.y + r.height
                                    }?.data
                                    if (hit != null) {
                                        if (hit.type == ItemType.FOLDER) {
                                            onFocusFolder(hit.path)
                                        } else {
                                            onOpenActions(
                                                ItemActionTarget(
                                                    name = hit.name,
                                                    path = hit.path,
                                                    size = hit.size,
                                                    type = hit.type
                                                )
                                            )
                                        }
                                    }
                                },
                                onLongPress = { offset ->
                                    val hit = rects.firstOrNull { r ->
                                        offset.x >= r.x && offset.x <= r.x + r.width &&
                                            offset.y >= r.y && offset.y <= r.y + r.height
                                    }?.data
                                    if (hit != null) {
                                        onSelectPath(hit.path)
                                        onOpenActions(
                                            ItemActionTarget(
                                                name = hit.name,
                                                path = hit.path,
                                                size = hit.size,
                                                type = hit.type
                                            )
                                        )
                                    }
                                }
                            )
                        }
                ) {
                    val gapPx = 2.dp.toPx()
                    val titlePadPx = 5.dp.toPx()

                    rects.forEach { rect ->
                        val item = rect.data
                        val meta = RadarFormatters.metaFor(item.category)
                        val isSelected = item.path == selectedPath

                        val x = rect.x + gapPx * 0.5f
                        val y = rect.y + gapPx * 0.5f
                        val w = max(1f, rect.width - gapPx)
                        val h = max(1f, rect.height - gapPx)

                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(meta.treemapStart, meta.treemapEnd),
                                startY = y,
                                endY = y + h
                            ),
                            topLeft = Offset(x, y),
                            size = Size(w, h)
                        )

                        drawRect(
                            color = if (isSelected) RadarColors.Sky300 else meta.treemapBorder,
                            topLeft = Offset(x, y),
                            size = Size(w, h),
                            style = Stroke(width = if (isSelected) 2.2.dp.toPx() else 1.dp.toPx())
                        )

                        if (w >= 46.dp.toPx() && h >= 26.dp.toPx()) {
                            val maxTextWidth = max(4, (w - titlePadPx * 2f).toInt())
                            val nameLayout = textMeasurer.measure(
                                text = item.name,
                                style = TextStyle(
                                    color = RadarColors.TextPrimary,
                                    fontSize = if (w >= 110.dp.toPx() && h >= 58.dp.toPx()) 12.sp else 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxTextWidth)
                            )

                            drawText(
                                textLayoutResult = nameLayout,
                                topLeft = Offset(x + titlePadPx, y + titlePadPx)
                            )

                            val sizeTop = y + titlePadPx + nameLayout.size.height + 1.dp.toPx()
                            if (h >= nameLayout.size.height + 24.dp.toPx()) {
                                val sizeLayout = textMeasurer.measure(
                                    text = RadarFormatters.formatBytes(item.size),
                                    style = TextStyle(
                                        color = RadarColors.TextPrimary.copy(alpha = 0.88f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxTextWidth)
                                )
                                drawText(
                                    textLayoutResult = sizeLayout,
                                    topLeft = Offset(x + titlePadPx, sizeTop)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Legenda de cores das categorias
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RadarColors.BgSurface)
                .border(1.dp, RadarColors.BorderSubtle)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileCategory.entries.forEach { cat ->
                val meta = RadarFormatters.metaFor(cat)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(meta.accent)
                    )
                    Text(
                        text = meta.shortLabel,
                        color = RadarColors.TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
