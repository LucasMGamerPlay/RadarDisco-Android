package com.radardisco.android.core.scanner

import kotlin.math.max
import kotlin.math.min

data class TreemapInput<T>(
    val data: T,
    val value: Double
)

data class TreemapRect<T>(
    val data: T,
    val value: Double,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

private data class NormalizedNode<T>(
    val data: T,
    val value: Double,
    val area: Double
)

private fun worstAspectRatio(row: List<NormalizedNode<*>>, sideLength: Double): Double {
    if (row.isEmpty() || sideLength <= 0.0) return Double.POSITIVE_INFINITY
    var sumArea = 0.0
    var maxArea = 0.0
    var minArea = Double.POSITIVE_INFINITY

    for (item in row) {
        sumArea += item.area
        if (item.area > maxArea) maxArea = item.area
        if (item.area < minArea) minArea = item.area
    }

    if (sumArea <= 0.0 || minArea <= 0.0) return Double.POSITIVE_INFINITY
    val s2 = sumArea * sumArea
    val w2 = sideLength * sideLength
    return max((w2 * maxArea) / s2, s2 / (w2 * minArea))
}

fun <T> computeSquarifiedTreemap(
    items: List<TreemapInput<T>>,
    totalWidth: Float,
    totalHeight: Float
): List<TreemapRect<T>> {
    if (totalWidth <= 0f || totalHeight <= 0f || items.isEmpty()) return emptyList()

    val positive = items
        .filter { it.value.isFinite() && it.value > 0.0 }
        .sortedByDescending { it.value }
    if (positive.isEmpty()) return emptyList()

    val totalValue = positive.sumOf { it.value }
    if (totalValue <= 0.0) return emptyList()

    val totalArea = totalWidth.toDouble() * totalHeight.toDouble()
    val nodes = positive.map { item ->
        NormalizedNode(
            data = item.data,
            value = item.value,
            area = (item.value / totalValue) * totalArea
        )
    }

    val results = ArrayList<TreemapRect<T>>(nodes.size)
    var x = 0.0
    var y = 0.0
    var w = totalWidth.toDouble()
    var h = totalHeight.toDouble()

    fun layoutRow(row: List<NormalizedNode<T>>) {
        val rowArea = row.sumOf { it.area }
        if (rowArea <= 0.0 || w <= 0.0 || h <= 0.0) return

        val horizontal = w >= h
        if (horizontal) {
            val stripWidth = min(w, rowArea / h)
            var currY = y
            for (i in row.indices) {
                val node = row[i]
                val rectHeight = if (i == row.lastIndex) {
                    max(0.0, y + h - currY)
                } else {
                    node.area / stripWidth
                }
                results.add(
                    TreemapRect(
                        data = node.data,
                        value = node.value,
                        x = x.toFloat(),
                        y = currY.toFloat(),
                        width = max(0.0, stripWidth).toFloat(),
                        height = max(0.0, rectHeight).toFloat()
                    )
                )
                currY += rectHeight
            }
            x += stripWidth
            w = max(0.0, w - stripWidth)
        } else {
            val stripHeight = min(h, rowArea / w)
            var currX = x
            for (i in row.indices) {
                val node = row[i]
                val rectWidth = if (i == row.lastIndex) {
                    max(0.0, x + w - currX)
                } else {
                    node.area / stripHeight
                }
                results.add(
                    TreemapRect(
                        data = node.data,
                        value = node.value,
                        x = currX.toFloat(),
                        y = y.toFloat(),
                        width = max(0.0, rectWidth).toFloat(),
                        height = max(0.0, stripHeight).toFloat()
                    )
                )
                currX += rectWidth
            }
            y += stripHeight
            h = max(0.0, h - stripHeight)
        }
    }

    val currentRow = mutableListOf<NormalizedNode<T>>()
    for (node in nodes) {
        val shortSide = min(w, h)
        if (shortSide <= 0.0) break

        if (currentRow.isEmpty()) {
            currentRow.add(node)
            continue
        }

        val currentWorst = worstAspectRatio(currentRow, shortSide)
        val nextWorst = worstAspectRatio(currentRow + node, shortSide)

        if (nextWorst <= currentWorst) {
            currentRow.add(node)
        } else {
            layoutRow(currentRow.toList())
            currentRow.clear()
            currentRow.add(node)
        }
    }

    if (currentRow.isNotEmpty()) {
        layoutRow(currentRow)
    }

    return results
}
