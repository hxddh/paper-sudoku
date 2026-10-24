package com.titpea.papersudoku.data

import android.os.Trace
import android.util.Log
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.PuzzleSource
import com.titpea.papersudoku.game.model.PuzzleSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Generated pages kept on disk, so a page that was prepared ahead (tomorrow's daily, the next
 * practice page) opens without waiting. Generation is deterministic, so a cleared cache only costs
 * time. Lives in the cache directory and is never backed up.
 */
class CachedPuzzleSource(
    private val directory: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PuzzleSource {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val pruned = AtomicBoolean(false)

    override suspend fun daily(epochDay: Long): PuzzleSpec =
        cached(Catalog.dailyKey(epochDay)) { Catalog.daily(epochDay) }

    override suspend fun classic(difficulty: Difficulty, number: Int): PuzzleSpec =
        cached(Catalog.classicKey(difficulty, number)) { Catalog.classic(difficulty, number) }

    // Kept under the key asked for: a path page that falls back to the plain page is cached
    // under the path key too, so it is never searched for again.
    override suspend fun practice(difficulty: Difficulty, number: Int, target: Technique?): PuzzleSpec =
        if (target == null) {
            classic(difficulty, number)
        } else {
            cached(Catalog.practiceKey(difficulty, number, target)) { Catalog.practice(difficulty, number, target) }
        }

    private suspend fun cached(key: String, generate: () -> PuzzleSpec): PuzzleSpec = withContext(dispatcher) {
        if (pruned.compareAndSet(false, true)) prune()
        // One generation per page at a time: a prefetch and an open of the same page share the
        // work, while different pages do not wait for each other.
        locks.getOrPut(key) { Mutex() }.withLock {
            val file = File(directory, "$key$SUFFIX")
            read(file) ?: run {
                Trace.beginSection("ps.deal")
                try { generate() } finally { Trace.endSection() }
            }.also { write(file, it) }
        }
    }

    /** Drops pages from older generators, dailies already in the past, and 9.0's weekly and focus pages. */
    private fun prune() {
        val yesterday = LocalDate.now().toEpochDay() - 1
        directory.listFiles()?.forEach { file ->
            val name = file.name
            val stale = !name.endsWith(SUFFIX) ||
                (Catalog.dayOf(name.removeSuffix(SUFFIX))?.let { it < yesterday } ?: false) ||
                name.startsWith("weekly-") || name.startsWith("focus-")
            if (stale) file.delete()
        }
    }

    private fun read(file: File): PuzzleSpec? = runCatching {
        if (file.exists()) json.decodeFromString(PuzzleSpec.serializer(), file.readText()) else null
    }.getOrNull()

    private fun write(file: File, spec: PuzzleSpec) {
        runCatching {
            directory.mkdirs()
            val temp = File(directory, file.name + ".tmp")
            temp.writeText(json.encodeToString(PuzzleSpec.serializer(), spec))
            temp.renameTo(file)
        }.onFailure { Log.w("PuzzleCache", "Could not keep ${file.name}", it) }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val SUFFIX = "-v${Catalog.GENERATOR_VERSION}.${Catalog.REVISION}.json"
    }
}
