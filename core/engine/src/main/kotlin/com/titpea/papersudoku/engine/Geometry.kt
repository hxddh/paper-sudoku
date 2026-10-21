package com.titpea.papersudoku.engine

enum class UnitKind { Row, Column, Box }

/** A row, column, or box, numbered from 0 in reading order. */
data class UnitRef(val kind: UnitKind, val index: Int)

/**
 * Board shape: cell indexing, units, and peers for a [size]×[size] grid of [boxRows]×[boxCols]
 * boxes. Instances are cached and immutable, so they can be shared freely between threads.
 */
class Geometry private constructor(
    val size: Int,
    val boxRows: Int,
    val boxCols: Int,
) {
    val cellCount: Int = size * size

    /** Bits 1..size set; bit `d` stands for digit `d`. */
    val allDigits: Int = (1 shl (size + 1)) - 2

    val boxesPerRow: Int = size / boxCols

    val rows: List<IntArray> = List(size) { r -> IntArray(size) { c -> r * size + c } }
    val columns: List<IntArray> = List(size) { c -> IntArray(size) { r -> r * size + c } }
    val boxes: List<IntArray> = List(size) { b ->
        val top = (b / boxesPerRow) * boxRows
        val left = (b % boxesPerRow) * boxCols
        IntArray(size) { k -> (top + k / boxCols) * size + left + k % boxCols }
    }

    /** Boxes first: a box is the easiest unit to scan on paper, so hints prefer it. */
    val units: List<Pair<UnitRef, IntArray>> =
        boxes.mapIndexed { i, cells -> UnitRef(UnitKind.Box, i) to cells } +
            rows.mapIndexed { i, cells -> UnitRef(UnitKind.Row, i) to cells } +
            columns.mapIndexed { i, cells -> UnitRef(UnitKind.Column, i) to cells }

    val peers: List<IntArray> = List(cellCount) { index ->
        val set = sortedSetOf<Int>()
        rows[row(index)].forEach(set::add)
        columns[column(index)].forEach(set::add)
        boxes[box(index)].forEach(set::add)
        set.remove(index)
        set.toIntArray()
    }

    fun row(index: Int): Int = index / size
    fun column(index: Int): Int = index % size
    fun box(index: Int): Int = (row(index) / boxRows) * boxesPerRow + column(index) / boxCols

    fun cells(unit: UnitRef): IntArray = when (unit.kind) {
        UnitKind.Row -> rows[unit.index]
        UnitKind.Column -> columns[unit.index]
        UnitKind.Box -> boxes[unit.index]
    }

    fun sees(a: Int, b: Int): Boolean =
        a != b && (row(a) == row(b) || column(a) == column(b) || box(a) == box(b))

    companion object {
        val Mini: Geometry = Geometry(6, 2, 3)
        val Classic: Geometry = Geometry(9, 3, 3)

        fun of(size: Int, boxRows: Int, boxCols: Int): Geometry = when {
            size == 6 && boxRows == 2 && boxCols == 3 -> Mini
            size == 9 && boxRows == 3 && boxCols == 3 -> Classic
            else -> {
                require(size in 4..9 && boxRows * boxCols == size) { "unsupported geometry" }
                Geometry(size, boxRows, boxCols)
            }
        }
    }
}

internal fun Int.hasDigit(digit: Int): Boolean = this and (1 shl digit) != 0
internal fun Int.digitCount(): Int = Integer.bitCount(this)
internal fun Int.firstDigit(): Int = Integer.numberOfTrailingZeros(this)
internal fun Int.digits(): List<Int> {
    val out = ArrayList<Int>(digitCount())
    var mask = this
    while (mask != 0) {
        val d = Integer.numberOfTrailingZeros(mask)
        out += d
        mask = mask and (mask - 1)
    }
    return out
}

/** Public helpers for callers that keep candidates as bitmasks (notes, for example). */
object DigitMask {
    fun of(digits: Iterable<Int>): Int = digits.fold(0) { acc, d -> acc or (1 shl d) }
    fun contains(mask: Int, digit: Int): Boolean = mask.hasDigit(digit)
    fun toggle(mask: Int, digit: Int): Int = mask xor (1 shl digit)
    fun remove(mask: Int, digit: Int): Int = mask and (1 shl digit).inv()
    fun digits(mask: Int): List<Int> = mask.digits()
    fun count(mask: Int): Int = mask.digitCount()
}
