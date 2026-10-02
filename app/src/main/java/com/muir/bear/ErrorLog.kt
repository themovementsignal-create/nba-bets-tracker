package com.muir.bear

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Keeps recent crashes and errors in a small text file so they can be viewed and copied in-app.
 * Crashes are written synchronously before the process dies.
 */
object ErrorLog {
    private const val MAX_BYTES = 200_000
    private lateinit var file: File
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun init(context: Context) {
        file = File(context.filesDir, "errors.log")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { write("CRASH", "Uncaught on ${thread.name}", e) }
            previous?.uncaughtException(thread, e)
        }
    }

    fun log(tag: String, message: String, e: Throwable? = null) {
        Log.w("Training", "$tag: $message", e)
        runCatching { write(tag, message, e) }
    }

    @Synchronized
    private fun write(tag: String, message: String, e: Throwable?) {
        if (!::file.isInitialized) return
        val trace = e?.let { t -> StringWriter().also { t.printStackTrace(PrintWriter(it)) }.toString() }.orEmpty()
        val entry = buildString {
            append("=== ").append(LocalDateTime.now().format(stamp)).append(" [").append(tag).append("] ")
            append("build ").append(BuildConfig.VERSION_CODE).append('\n')
            append(message).append('\n')
            if (trace.isNotEmpty()) append(trace)
            append('\n')
        }
        file.appendText(entry)
        if (file.length() > MAX_BYTES) {
            val text = file.readText()
            file.writeText(text.takeLast(MAX_BYTES / 2))
        }
    }

    fun read(): String = if (::file.isInitialized && file.exists()) file.readText() else ""

    @Synchronized
    fun clear() {
        if (::file.isInitialized) file.delete()
    }
}
