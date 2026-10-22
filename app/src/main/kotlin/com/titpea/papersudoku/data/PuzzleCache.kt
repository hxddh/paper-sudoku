package com.titpea.papersudoku.data

import android.util.Log
import com.titpea.papersudoku.engine.Difficulty
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

/**
 * Generated pages kept on disk, so a page that was prepared ahead (tomorrow's daily, the next
 * practice page) opens without waiting. Generation is deterministic, so a cleared cache only costs
 * time. Lives in the cache directory and is never backed up.
 */
class CachedPuzzleSource(
    private val directory: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PuzzleSource {
    private val lock = Mutex()

    override suspend fun daily(epochDay: Long): PuzzleSpec =
        cached(Catalog.dailyKey(epochDay)) { Catalog.daily(epochDay) }

    override suspend fun classic(difficulty: Difficulty, number: Int): PuzzleSpec =
        cached(Catalog.classicKey(difficulty, number)) { Catalog.classic(difficulty, number) }

    private suspend fun cached(key: String, generate: () -> PuzzleSpec): PuzzleSpec = withContext(dispatcher) {
        // One generation at a time: a prefetch and an open of the same page share the work.
        lock.withLock {
            val file = File(directory, "$key-v${Catalog.GENERATOR_VERSION}.json")
            read(file) ?: generate().also { write(file, it) }
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
    }
}
