package com.potero.service.grobid

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Manages GROBID server process lifecycle
 *
 * Downloads GROBID standalone server on first use, starts it as a separate process,
 * and manages its lifecycle (start/stop/health check).
 */
object GrobidProcessManager {
    private const val GROBID_VERSION = "0.8.2"
    private const val GROBID_SERVER_URL =
        "https://github.com/kermitt2/grobid/releases/download/v$GROBID_VERSION/grobid-service-$GROBID_VERSION.zip"

    private const val GROBID_PORT = 8070
    private const val STARTUP_TIMEOUT_MS = 60_000L
    private const val HEALTH_CHECK_INTERVAL_MS = 500L

    private val grobidInstallPath = "${System.getProperty("user.home")}/.potero/grobid"
    private val startMutex = Mutex()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var isRunning = false

    /**
     * Start GROBID server process
     * Downloads server if not present, then starts it
     *
     * @return true if server started successfully
     */
    suspend fun start(): Boolean = startMutex.withLock {
        if (isRunning && isHealthy()) {
            println("[GROBID Process] Already running")
            return true
        }

        try {
            // Step 1: Ensure GROBID server is installed
            ensureGrobidInstalled()

            // Step 2: Start GROBID server process
            println("[GROBID Process] Starting server on port $GROBID_PORT...")
            startProcess()

            // Step 3: Wait for server to be healthy
            val started = waitForHealthy()
            if (started) {
                println("[GROBID Process] Server started successfully")
                isRunning = true
            } else {
                println("[GROBID Process] Server failed to start within timeout")
                stop()
            }

            return started

        } catch (e: Exception) {
            println("[GROBID Process] Failed to start: ${e.message}")
            stop()
            return false
        }
    }

    /**
     * Stop GROBID server process
     */
    fun stop() {
        process?.let {
            println("[GROBID Process] Stopping server...")
            it.destroy()
            it.waitFor()
            process = null
            isRunning = false
            println("[GROBID Process] Server stopped")
        }
    }

    /**
     * Check if GROBID server is running and healthy
     */
    suspend fun isHealthy(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("http://localhost:$GROBID_PORT/api/isalive")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 1000
                connection.readTimeout = 1000

                val responseCode = connection.responseCode
                connection.disconnect()

                responseCode == 200
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Get GROBID server URL
     */
    fun getServerUrl(): String {
        return "http://localhost:$GROBID_PORT"
    }

    /**
     * Ensure GROBID server is installed
     * Downloads and extracts if not present
     */
    private suspend fun ensureGrobidInstalled() {
        val installDir = File(grobidInstallPath)
        val jarFile = File(installDir, "grobid-service-$GROBID_VERSION-onejar.jar")

        if (jarFile.exists()) {
            println("[GROBID Process] Server already installed at: $grobidInstallPath")
            return
        }

        println("[GROBID Process] Downloading GROBID server from: $GROBID_SERVER_URL")
        println("[GROBID Process] This may take a few minutes (~200MB)...")

        withContext(Dispatchers.IO) {
            try {
                installDir.mkdirs()

                // Download ZIP
                val tempZip = File.createTempFile("grobid-service", ".zip")
                tempZip.deleteOnExit()

                println("[GROBID Process] Downloading...")
                downloadFile(GROBID_SERVER_URL, tempZip)

                println("[GROBID Process] Extracting...")
                extractZip(tempZip, installDir)

                tempZip.delete()

                println("[GROBID Process] Installation complete")

            } catch (e: Exception) {
                println("[GROBID Process] Installation failed: ${e.message}")
                throw GrobidException("Failed to download GROBID server", e)
            }
        }
    }

    /**
     * Start GROBID server process using ProcessBuilder
     */
    private fun startProcess() {
        val jarFile = File(grobidInstallPath, "grobid-service-$GROBID_VERSION-onejar.jar")
        if (!jarFile.exists()) {
            throw GrobidException("GROBID JAR not found: ${jarFile.absolutePath}")
        }

        val processBuilder = ProcessBuilder(
            "java",
            "-Xmx2048m",
            "-jar",
            jarFile.absolutePath,
            "--port",
            GROBID_PORT.toString()
        )

        processBuilder.directory(File(grobidInstallPath))
        processBuilder.redirectErrorStream(true)

        // Redirect output to log file
        val logFile = File(grobidInstallPath, "grobid.log")
        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))

        process = processBuilder.start()
    }

    /**
     * Wait for GROBID server to become healthy
     */
    private suspend fun waitForHealthy(): Boolean {
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < STARTUP_TIMEOUT_MS) {
            if (isHealthy()) {
                return true
            }

            // Check if process is still alive
            if (process?.isAlive == false) {
                println("[GROBID Process] Process died unexpectedly")
                return false
            }

            delay(HEALTH_CHECK_INTERVAL_MS)
        }

        return false
    }

    /**
     * Download file from URL
     */
    private fun downloadFile(url: String, destination: File) {
        URL(url).openStream().use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytes = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead

                    // Print progress every 10MB
                    if (totalBytes % (10 * 1024 * 1024) == 0L) {
                        println("[GROBID Process] Downloaded ${totalBytes / (1024 * 1024)} MB...")
                    }
                }
            }
        }
    }

    /**
     * Extract ZIP file
     */
    private fun extractZip(zipFile: File, destination: File) {
        ZipInputStream(zipFile.inputStream()).use { zipInput ->
            var entry = zipInput.nextEntry
            while (entry != null) {
                val filePath = File(destination, entry.name)

                if (entry.isDirectory) {
                    filePath.mkdirs()
                } else {
                    filePath.parentFile?.mkdirs()
                    FileOutputStream(filePath).use { output ->
                        zipInput.copyTo(output)
                    }
                }

                zipInput.closeEntry()
                entry = zipInput.nextEntry
            }
        }
    }
}

/**
 * Shutdown hook to stop GROBID server on JVM exit
 */
class GrobidShutdownHook : Thread() {
    override fun run() {
        GrobidProcessManager.stop()
    }
}
