package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Technique

/**
 * Where the generator's search ends, worked out ahead for the pages most players open: the
 * dailies of [CatalogTableData.DAILY_FIRST] onwards, the first practice pages of every level and
 * the first path pages of every level and technique. With an entry a page is one carve; without,
 * the search runs as before. Either way the page is the same, which `CatalogTableTest` checks.
 *
 * A daily's entry is its retry and the draws of its SplitMix64 stream before the carve that
 * became the page; a practice page's is the draws; a path page's is the try that needed the
 * technique, or [MISS] when none of its tries did (the plain page is dealt instead). Entries are
 * base-36 tokens in [CatalogTableData], parsed once, on first use. Written by
 * `CatalogTableWriter` (see its documentation to write it again after a generator change).
 */
internal object CatalogTable {
    const val MISS = -1
    val MISS_ENTRY = TableEntry(MISS.toLong(), 0)

    /** A table written for another generator would deal other pages, so it is not used at all. */
    val current: Boolean get() = CatalogTableData.GENERATOR_VERSION == Catalog.GENERATOR_VERSION

    private val dailies: LongArray by lazy { parse(CatalogTableData.DAILY) }

    private val classics: List<LongArray> by lazy {
        listOf(
            CatalogTableData.CLASSIC_GENTLE,
            CatalogTableData.CLASSIC_STEADY,
            CatalogTableData.CLASSIC_TRICKY,
            CatalogTableData.CLASSIC_DEEP,
        ).map(::parse)
    }

    /** By "Level:Technique". */
    private val paths: Map<String, LongArray> by lazy {
        CatalogTableData.PATH.split('|').associate { line ->
            val (level, technique, tokens) = line.split(':')
            "$level:$technique" to parse(tokens)
        }
    }

    fun daily(epochDay: Long): TableEntry? {
        if (!current) return null
        val i = epochDay - CatalogTableData.DAILY_FIRST
        if (i < 0 || i >= dailies.size) return null
        val token = dailies[i.toInt()]
        return TableEntry(attempt = token and 0xF, draws = token ushr 4)
    }

    fun classic(difficulty: Difficulty, number: Int): TableEntry? {
        if (!current) return null
        val row = classics[difficulty.ordinal]
        return if (number in row.indices) TableEntry(0, row[number]) else null
    }

    /** The try that deals path page [number], [MISS], or null when the page is not in the table. */
    fun path(difficulty: Difficulty, target: Technique, number: Int): Int? {
        if (!current) return null
        val row = paths["${difficulty.name}:${target.name}"] ?: return null
        return if (number in row.indices) row[number].toInt() else null
    }

    /** Sizes, for the tests. */
    val dailyCount: Int get() = dailies.size
    fun classicCount(difficulty: Difficulty): Int = classics[difficulty.ordinal].size
    fun pathCount(difficulty: Difficulty, target: Technique): Int = paths["${difficulty.name}:${target.name}"]?.size ?: 0

    private fun parse(tokens: String): LongArray =
        if (tokens.isEmpty()) LongArray(0) else tokens.split(' ').map { if (it == "-") MISS.toLong() else it.toLong(36) }.toLongArray()
}
