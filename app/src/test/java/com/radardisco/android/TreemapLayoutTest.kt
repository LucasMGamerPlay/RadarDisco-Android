package com.radardisco.android

import com.radardisco.android.core.scanner.TreemapInput
import com.radardisco.android.core.scanner.computeSquarifiedTreemap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreemapLayoutTest {

    @Test
    fun preservesTotalAreaAndOrdersDescending() {
        val inputs = listOf(
            TreemapInput("pequeno", 10.0),
            TreemapInput("grande", 60.0),
            TreemapInput("medio", 30.0)
        )

        val rects = computeSquarifiedTreemap(inputs, totalWidth = 800f, totalHeight = 400f)
        assertEquals(3, rects.size)
        assertEquals("grande", rects.first().data)

        val totalRectArea = rects.sumOf { (it.width * it.height).toDouble() }
        assertEquals(800.0 * 400.0, totalRectArea, 5.0)

        rects.forEach { rect ->
            assertTrue(rect.width > 0f)
            assertTrue(rect.height > 0f)
            assertTrue(rect.x >= -0.01f)
            assertTrue(rect.y >= -0.01f)
            assertTrue(rect.x + rect.width <= 800.05f)
            assertTrue(rect.y + rect.height <= 400.05f)
        }
    }

    @Test
    fun filtersZeroAndNegativeItems() {
        val inputs = listOf(
            TreemapInput("zero", 0.0),
            TreemapInput("negativo", -50.0),
            TreemapInput("unico", 100.0)
        )

        val rects = computeSquarifiedTreemap(inputs, totalWidth = 300f, totalHeight = 200f)
        assertEquals(1, rects.size)
        assertEquals("unico", rects.first().data)
        assertEquals(300f, rects.first().width, 0.1f)
        assertEquals(200f, rects.first().height, 0.1f)
    }

    @Test
    fun handlesZeroCanvasBoundsGracefully() {
        val rects = computeSquarifiedTreemap(
            items = listOf(TreemapInput("a", 50.0)),
            totalWidth = 0f,
            totalHeight = 200f
        )
        assertTrue(rects.isEmpty())
    }
}
