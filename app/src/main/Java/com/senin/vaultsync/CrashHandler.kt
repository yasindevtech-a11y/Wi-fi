package com.senin.vaultsync

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Uygulama çökerse (uncaught exception), hatayı dosyaya yazar.
 * MainActivity açılışta bu dosyayı kontrol edip varsa hatayı ekranda gösterir.
 * Böylece "uygulama açılmıyor" durumunda gerçek sebebi görebiliriz.
 */
object CrashHandler {

    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                File(appContext.filesDir, FILE_NAME).writeText(sw.toString())
            } catch (_: Exception) {
                // yazma başarısız olsa bile orijinal çökmeyi engellemeyelim
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun readLastCrash(context: Context): String? {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        return if (file.exists()) file.readText() else null
    }

    fun clear(context: Context) {
        File(context.applicationContext.filesDir, FILE_NAME).delete()
    }
}
