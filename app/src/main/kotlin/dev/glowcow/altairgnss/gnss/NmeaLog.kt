package dev.glowcow.altairgnss.gnss

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedWriter
import java.io.File

/**
 * What the receiver says in NMEA: the latest sentences for the screen and, on request, all of them
 * into a file. Used from the main thread only.
 */
class NmeaLog(private val dir: File) {
    private val latest = ArrayDeque<String>()
    private val latestState = MutableStateFlow<List<String>>(emptyList())
    private var writer: BufferedWriter? = null
    private val writtenState = MutableStateFlow<Int?>(null)
    private val savedState = MutableStateFlow<Int?>(null)

    /** The latest sentences, the newest last. */
    val lines: StateFlow<List<String>> = latestState

    /** Sentences written to the file so far; null when nothing is being recorded. */
    val recording: StateFlow<Int?> = writtenState

    /** Sentences in the finished file; null when there is none. */
    val saved: StateFlow<Int?> = savedState

    val file: File get() = File(dir, "nmea.txt")

    fun add(sentence: String) {
        val line = sentence.trim()
        if (line.isEmpty()) return
        if (latest.size == SHOWN) latest.removeFirst()
        latest.addLast(line)
        latestState.value = latest.toList()
        writer?.let {
            try {
                it.write(line)
                it.newLine()
                writtenState.value = (writtenState.value ?: 0) + 1
            } catch (_: java.io.IOException) {
                stop()
            }
        }
    }

    /** Starts a new file, replacing the one recorded before. */
    fun start() {
        stop()
        dir.mkdirs()
        writer = try {
            file.bufferedWriter()
        } catch (_: java.io.IOException) {
            return
        }
        savedState.value = null
        writtenState.value = 0
    }

    fun stop() {
        val open = writer ?: return
        writer = null
        runCatching { open.close() }
        savedState.value = writtenState.value?.takeIf { it > 0 }
        writtenState.value = null
    }

    private companion object {
        const val SHOWN = 40
    }
}
