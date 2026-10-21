package com.titpea.papersudoku.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {

    @Test
    fun `mini boxes are two rows by three columns`() {
        val g = Geometry.Mini
        assertEquals(listOf(0, 1, 2, 6, 7, 8), g.boxes[0].toList())
        assertEquals(listOf(3, 4, 5, 9, 10, 11), g.boxes[1].toList())
        assertEquals(listOf(24, 25, 26, 30, 31, 32), g.boxes[4].toList())
        assertEquals(5, g.box(27))
    }

    @Test
    fun `peers have the textbook counts`() {
        assertTrue(Geometry.Classic.peers.all { it.size == 20 })
        assertTrue(Geometry.Mini.peers.all { it.size == 12 })
    }

    @Test
    fun `every unit holds each cell exactly once per kind`() {
        for (g in listOf(Geometry.Mini, Geometry.Classic)) {
            for (kind in UnitKind.entries) {
                val cells = g.units.filter { it.first.kind == kind }.flatMap { it.second.toList() }
                assertEquals((0 until g.cellCount).toList(), cells.sorted())
            }
        }
    }
}
