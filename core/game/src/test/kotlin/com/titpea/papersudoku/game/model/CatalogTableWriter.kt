package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Technique
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Writes [CatalogTableData] by running every tabled page's search. Skipped unless asked for:
 *
 *     PAPER_SUDOKU_WRITE_TABLE=1 ./gradlew :core:game:test --tests '*CatalogTableWriter*' --rerun
 *
 * Run it after any change to what the generator deals (and bump [Catalog.GENERATOR_VERSION]
 * then, so an old table is never used). It takes some minutes on four cores.
 */
class CatalogTableWriter {

    @Test
    fun `write the catalog table`() {
        assumeTrue(System.getenv("PAPER_SUDOKU_WRITE_TABLE") != null)
        val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        val started = System.nanoTime()
        fun <T> all(tasks: List<() -> T>): List<T> = pool.invokeAll(tasks.map { Callable(it) }).map { it.get() }

        val days = (DAILY_FIRST.toEpochDay()..DAILY_LAST.toEpochDay()).toList()
        val dailies = all(days.map { day -> { Catalog.dealDaily(day, table = false) } })
        report("dailies", dailies)
        val classics = Difficulty.entries.associateWith { level ->
            all((0 until CLASSIC_PAGES).map { n -> { Catalog.dealClassic(level, n, table = false) } }).also { report("classic $level", it) }
        }
        val paths = Difficulty.entries.flatMap { level -> Technique.entries.filter { it.tier in 2..level.maxTier }.map { level to it } }
            .associateWith { (level, technique) ->
                all((0 until PATH_PAGES).map { n -> { Catalog.dealPractice(level, n, technique, table = false) } })
                    .also { report("path $level $technique", it) }
            }
        pool.shutdown()

        val out = StringBuilder()
        out.append("package com.titpea.papersudoku.game.model\n\n")
        out.append("// Written by CatalogTableWriter; do not edit. See CatalogTable for what the entries are.\n")
        out.append("internal object CatalogTableData {\n")
        out.append("    const val GENERATOR_VERSION: Long = ${Catalog.GENERATOR_VERSION}\n")
        out.append("    const val DAILY_FIRST: Long = ${DAILY_FIRST.toEpochDay()} // $DAILY_FIRST, through $DAILY_LAST\n")
        constant(out, "DAILY", dailies.map { (it.entry.draws * 16 + it.entry.attempt).toString(36) })
        for ((level, row) in classics) constant(out, "CLASSIC_" + level.name.uppercase(), row.map { it.entry.draws.toString(36) })
        val pathTokens = paths.entries.map { (key, row) ->
            "${key.first.name}:${key.second.name}:" + row.joinToString(" ") { d ->
                if (d.entry == CatalogTable.MISS_ENTRY) "-" else d.entry.attempt.toString(36)
            }
        }
        constant(out, "PATH", pathTokens, separator = "|")
        out.append("}\n")
        File(OUTPUT).writeText(out.toString())
        println("wrote $OUTPUT (${out.length} chars) in ${(System.nanoTime() - started) / 1_000_000} ms")
    }

    private fun report(label: String, dealt: List<Dealt>) {
        val carves = dealt.map { it.carves }.sorted()
        println("$label: ${dealt.size} pages, carves median ${carves[carves.size / 2]}, max ${carves.last()}, total ${carves.sum()}, misses ${dealt.count { it.entry == CatalogTable.MISS_ENTRY }}")
    }

    /** One string constant, wrapped; each stays far below the class file's 64 KB per constant. */
    private fun constant(out: StringBuilder, name: String, tokens: List<String>, separator: String = " ") {
        val text = tokens.joinToString(separator)
        check(text.length < 60_000) { "$name is too long for one constant" }
        out.append("    const val $name: String =\n")
        val lines = text.chunked(100)
        lines.forEachIndexed { i, line ->
            out.append("        \"").append(line).append('"').append(if (i < lines.lastIndex) " +\n" else "\n")
        }
        if (lines.isEmpty()) out.append("        \"\"\n")
    }

    private companion object {
        val DAILY_FIRST: LocalDate = LocalDate.of(2026, 1, 1)
        val DAILY_LAST: LocalDate = LocalDate.of(2029, 12, 31)
        const val CLASSIC_PAGES = 300
        val PATH_PAGES = System.getenv("PAPER_SUDOKU_PATH_PAGES")?.toInt() ?: 100
        const val OUTPUT = "src/main/kotlin/com/titpea/papersudoku/game/model/CatalogTableData.kt"
    }
}
