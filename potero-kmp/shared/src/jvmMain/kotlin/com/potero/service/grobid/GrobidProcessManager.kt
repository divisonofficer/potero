package com.potero.service.grobid

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Manages GROBID server process lifecycle
 *
 * Downloads GROBID source code, builds it with Gradle, and runs as a local process.
 * This approach doesn't require Docker and works on all platforms.
 */
object GrobidProcessManager {
    private const val GROBID_VERSION = "0.8.2"
    private const val GROBID_SOURCE_URL = "https://github.com/kermitt2/grobid/archive/refs/tags/$GROBID_VERSION.zip"

    private const val GROBID_PORT = 8070
    private const val STARTUP_TIMEOUT_MS = 120_000L
    private const val HEALTH_CHECK_INTERVAL_MS = 1000L

    private val grobidInstallPath = "${System.getProperty("user.home")}/.potero/grobid"
    private val startMutex = Mutex()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var isRunning = false

    /**
     * Start GROBID server process
     * Downloads source if not present, builds it, then starts the server
     *
     * @return true if server started successfully
     */
    suspend fun start(): Boolean = startMutex.withLock {
        if (isRunning && isHealthy()) {
            println("[GROBID Process] Already running")
            return true
        }

        try {
            // Step 1: Ensure GROBID is built
            ensureGrobidBuilt()

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
            e.printStackTrace()
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
                connection.connectTimeout = 2000
                connection.readTimeout = 2000

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
     * Ensure GROBID is downloaded and built
     */
    private suspend fun ensureGrobidBuilt() {
        val installDir = File(grobidInstallPath)
        val sourceDir = File(installDir, "grobid-$GROBID_VERSION")

        // Check if onejar exists (indicator that build was completed)
        val onejarFile = File(sourceDir, "grobid-service/build/libs/grobid-service-$GROBID_VERSION-onejar.jar")

        if (onejarFile.exists()) {
            println("[GROBID Process] Server already built at: ${sourceDir.absolutePath}")
            println("[GROBID Process] OneJar found: ${onejarFile.absolutePath}")
            // Ensure pdfalto binaries are executable (might be missing if built before this fix)
            makePdfaltoBinariesExecutable(sourceDir)
            return
        }

        println("[GROBID Process] Downloading GROBID source from: $GROBID_SOURCE_URL")
        println("[GROBID Process] This may take a few minutes...")

        withContext(Dispatchers.IO) {
            try {
                installDir.mkdirs()

                // Download source ZIP
                val tempZip = File.createTempFile("grobid-source", ".zip")
                tempZip.deleteOnExit()

                println("[GROBID Process] Downloading...")
                downloadFile(GROBID_SOURCE_URL, tempZip)

                // Verify downloaded file
                if (!tempZip.exists() || tempZip.length() < 1024) {
                    throw GrobidException("Downloaded file is invalid or empty")
                }

                println("[GROBID Process] Extracting...")
                extractZip(tempZip, installDir)

                tempZip.delete()

                // Verify extracted directory
                if (!sourceDir.exists()) {
                    throw GrobidException("Source directory not found after extraction: ${sourceDir.absolutePath}")
                }

                println("[GROBID Process] Building with Gradle (this may take 5-10 minutes)...")
                buildGrobid(sourceDir)

                // Verify onejar was created
                val onejarFile = File(sourceDir, "grobid-service/build/libs/grobid-service-$GROBID_VERSION-onejar.jar")
                if (!onejarFile.exists()) {
                    throw GrobidException("OneJar not found after build. Expected: ${onejarFile.absolutePath}")
                }

                // Make pdfalto binaries executable (required for PDF processing)
                makePdfaltoBinariesExecutable(sourceDir)

                println("[GROBID Process] Build complete")

            } catch (e: GrobidException) {
                println("[GROBID Process] Setup failed: ${e.message}")
                throw e
            } catch (e: Exception) {
                println("[GROBID Process] Setup failed: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
                throw GrobidException("Failed to setup GROBID: ${e.message}", e)
            }
        }
    }

    /**
     * Build GROBID with Gradle and create OneJar
     */
    private fun buildGrobid(sourceDir: File) {
        val gradlew = if (System.getProperty("os.name").lowercase().contains("windows")) {
            File(sourceDir, "gradlew.bat")
        } else {
            File(sourceDir, "gradlew")
        }

        // Make gradlew executable on Unix systems
        if (!System.getProperty("os.name").lowercase().contains("windows")) {
            gradlew.setExecutable(true)
        }

        if (!gradlew.exists()) {
            throw GrobidException("Gradle wrapper not found: ${gradlew.absolutePath}")
        }

        println("[GROBID Process] Running: ${gradlew.absolutePath} :grobid-service:shadowJar")

        val processBuilder = ProcessBuilder(
            gradlew.absolutePath,
            ":grobid-service:shadowJar",
            "-x", "test"  // Skip tests to speed up build
        )
        processBuilder.directory(sourceDir)
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()

        // Stream build output
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                // Only print important lines to avoid spam
                line?.let {
                    if (it.contains("BUILD") || it.contains("FAILED") || it.contains("SUCCESS") ||
                        it.contains("Download") || it.contains("Task :")) {
                        println("[GROBID Build] $it")
                    }
                }
            }
        }

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GrobidException("Gradle build failed with exit code: $exitCode")
        }
    }

    /**
     * Start GROBID server process using OneJar (java -jar)
     * This is much lighter than gradle run and avoids OOM issues
     */
    private fun startProcess() {
        val sourceDir = File(grobidInstallPath, "grobid-$GROBID_VERSION")
        val onejarFile = File(sourceDir, "grobid-service/build/libs/grobid-service-$GROBID_VERSION-onejar.jar")
        val grobidHome = File(sourceDir, "grobid-home")
        val configFile = File(grobidHome, "config/grobid.yaml")

        if (!onejarFile.exists()) {
            throw GrobidException("OneJar not found: ${onejarFile.absolutePath}")
        }

        if (!grobidHome.exists()) {
            throw GrobidException("GROBID home directory not found: ${grobidHome.absolutePath}")
        }

        if (!configFile.exists()) {
            throw GrobidException("GROBID config file not found: ${configFile.absolutePath}")
        }

        println("[GROBID Process] Starting with OneJar: ${onejarFile.absolutePath}")

        val processBuilder = ProcessBuilder(
            "java",
            "-Xmx1g",  // 1GB heap for GROBID processing (reduced to prevent OOM)
            "-Xms256m", // Start with 256MB
            "-jar",
            onejarFile.absolutePath,
            "server",
            configFile.absolutePath  // Path to config YAML file
        )

        processBuilder.directory(sourceDir)
        processBuilder.redirectErrorStream(true)

        // Set GROBID_HOME environment variable
        val env = processBuilder.environment()
        env["GROBID_HOME"] = grobidHome.absolutePath

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
                // Show last lines of log
                val logFile = File(grobidInstallPath, "grobid.log")
                if (logFile.exists()) {
                    val lastLines = logFile.readLines().takeLast(20)
                    println("[GROBID Process] Last log lines:")
                    lastLines.forEach { println("  $it") }
                }
                return false
            }

            delay(HEALTH_CHECK_INTERVAL_MS)
        }

        return false
    }

    /**
     * Download file from URL with proper redirect handling
     */
    private fun downloadFile(url: String, destination: File) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            // Set user agent to avoid GitHub bot detection
            connection.setRequestProperty("User-Agent", "Potero-App/1.0")

            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                throw GrobidException("HTTP $responseCode: ${connection.responseMessage}")
            }

            val contentLength = connection.contentLengthLong
            println("[GROBID Process] File size: ${contentLength / (1024 * 1024)} MB")

            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead

                        // Print progress every 10MB
                        if (totalBytes % (10 * 1024 * 1024) < 8192) {
                            val progress = if (contentLength > 0) {
                                " (${(totalBytes * 100 / contentLength)}%)"
                            } else {
                                ""
                            }
                            println("[GROBID Process] Downloaded ${totalBytes / (1024 * 1024)} MB$progress")
                        }
                    }

                    println("[GROBID Process] Download complete: ${totalBytes / (1024 * 1024)} MB")
                }
            }
        } catch (e: Exception) {
            throw GrobidException("Failed to download from $url: ${e.message}", e)
        } finally {
            connection?.disconnect()
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


    /**
     * Make pdfalto binaries executable (required for PDF processing).
     * GROBID uses pdfalto to convert PDF to XML, but the binaries are not executable after extraction.
     */
    private fun makePdfaltoBinariesExecutable(sourceDir: File) {
        val pdfaltoBinDir = File(sourceDir, "grobid-home/pdfalto/lin-64")
        if (!pdfaltoBinDir.exists()) {
            println("[GROBID Process] Warning: pdfalto binary directory not found: ${pdfaltoBinDir.absolutePath}")
            return
        }

        val binaries = listOf("pdfalto", "pdfalto_server")
        for (binaryName in binaries) {
            val binaryFile = File(pdfaltoBinDir, binaryName)
            if (binaryFile.exists()) {
                val wasExecutable = binaryFile.canExecute()
                binaryFile.setExecutable(true)
                if (!wasExecutable) {
                    println("[GROBID Process] Made $binaryName executable")
                }
            } else {
                println("[GROBID Process] Warning: Binary not found: ${binaryFile.absolutePath}")
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
