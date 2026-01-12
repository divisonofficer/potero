package com.potero.service.grobid

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * GROBID engine implementation using REST API
 *
 * Supports two modes:
 * 1. Cloud mode: Uses external GROBID service (via GROBID_CLOUD_URL env var)
 * 2. Local mode: Uses bundled or downloaded GROBID server
 *
 * Thread-safe singleton with lazy initialization.
 */
class GrobidRestEngine(
    private val httpClient: HttpClient
) : GrobidEngine {

    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    @Volatile
    private var serverUrl: String? = null

    @Volatile
    private var isCloudMode = false

    /**
     * Get the GROBID server URL (cloud or local)
     */
    private fun getServerUrl(): String {
        return serverUrl ?: throw GrobidException("GROBID not initialized")
    }

    /**
     * Initialize GROBID (lazy)
     * Checks for cloud URL env var first, otherwise starts local server
     */
    private suspend fun initialize() = initMutex.withLock {
        if (initialized) return

        try {
            println("[GROBID REST] Initializing...")

            // Check for cloud URL via environment variable
            val cloudUrl = System.getenv("GROBID_CLOUD_URL")?.takeIf { it.isNotBlank() }

            if (cloudUrl != null) {
                // Cloud mode - use external GROBID service
                println("[GROBID REST] Using cloud GROBID: $cloudUrl")
                serverUrl = cloudUrl.trimEnd('/')
                isCloudMode = true

                // Verify cloud service is reachable
                val isAlive = verifyCloudService(serverUrl!!)
                if (!isAlive) {
                    println("[GROBID REST] Cloud GROBID not reachable, falling back to local...")
                    startLocalGrobid()
                } else {
                    initialized = true
                    println("[GROBID REST] Cloud GROBID ready at: $serverUrl")
                }
            } else {
                // Local mode - start GROBID server process
                startLocalGrobid()
            }

        } catch (e: Exception) {
            println("[GROBID REST] Initialization failed: ${e.message}")
            throw GrobidException("Failed to initialize GROBID REST engine", e)
        }
    }

    private suspend fun startLocalGrobid() {
        println("[GROBID REST] Starting local GROBID server...")

        val started = GrobidProcessManager.start()
        if (!started) {
            throw GrobidException("Failed to start GROBID server")
        }

        serverUrl = GrobidProcessManager.getServerUrl()
        isCloudMode = false
        initialized = true
        println("[GROBID REST] Local GROBID ready at: $serverUrl")
    }

    /**
     * Verify cloud GROBID service is reachable
     */
    private suspend fun verifyCloudService(url: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.get("$url/api/isalive") {
                    timeout {
                        requestTimeoutMillis = 10_000
                        connectTimeoutMillis = 5_000
                    }
                }
                response.status.isSuccess()
            } catch (e: Exception) {
                println("[GROBID REST] Cloud service check failed: ${e.message}")
                false
            }
        }
    }

    override suspend fun processFulltext(pdfPath: String): TEIDocument {
        if (!initialized) {
            initialize()
        }

        val pdfFile = File(pdfPath)
        if (!pdfFile.exists()) {
            throw GrobidException("PDF file not found: $pdfPath")
        }

        return withContext(Dispatchers.IO) {
            try {
                println("[GROBID REST] Processing full text: ${pdfFile.name}")

                val url = getServerUrl()
                val response = httpClient.post("$url/api/processFulltextDocument") {
                    // Accept TEI XML response
                    header(HttpHeaders.Accept, "application/xml")

                    // GROBID PDF processing can take 2-3 minutes for large/complex PDFs
                    // Cloud services may be slower, use longer timeout
                    timeout {
                        requestTimeoutMillis = if (isCloudMode) 300_000 else 180_000  // 5 min for cloud, 3 min for local
                        socketTimeoutMillis = if (isCloudMode) 300_000 else 180_000
                    }

                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("input", pdfFile.readBytes(), Headers.build {
                                    append(HttpHeaders.ContentType, "application/pdf")
                                    append(HttpHeaders.ContentDisposition, "filename=\"${pdfFile.name}\"")
                                })
                                append("consolidateHeader", "1")
                                append("consolidateCitations", "1")
                                append("includeRawCitations", "1")
                                append("includeRawAffiliations", "0")
                                // Use multi-append for teiCoordinates (more reliable than comma-separated)
                                listOf("persName", "figure", "ref", "biblStruct", "formula").forEach {
                                    append("teiCoordinates", it)
                                }
                            }
                        )
                    )
                }

                if (!response.status.isSuccess()) {
                    val errorBody = try {
                        response.bodyAsText()
                    } catch (e: Exception) {
                        "Unable to read error body: ${e.message}"
                    }
                    println("[GROBID REST] Error response (${response.status}): $errorBody")

                    // Provide user-friendly error message
                    val friendlyMessage = when {
                        errorBody.contains("BAD_INPUT_DATA") ->
                            "PDF format not supported by GROBID (will use direct PDF extraction instead)"
                        errorBody.contains("timeout") || errorBody.contains("TIMEOUT") ->
                            "GROBID processing timeout (PDF may be too large or complex)"
                        else ->
                            "GROBID API error: ${response.status}"
                    }

                    throw GrobidException(friendlyMessage)
                }

                val teiXml = response.bodyAsText()
                println("[GROBID REST] Received TEI XML (${teiXml.length} chars)")

                // Parse TEI XML to domain entities
                TEIParser.parse(teiXml)

            } catch (e: Exception) {
                println("[GROBID REST] Processing failed: ${e.message}")
                throw GrobidException("Failed to process PDF with GROBID", e)
            }
        }
    }

    override suspend fun processHeader(pdfPath: String): TEIDocument {
        if (!initialized) {
            initialize()
        }

        val pdfFile = File(pdfPath)
        if (!pdfFile.exists()) {
            throw GrobidException("PDF file not found: $pdfPath")
        }

        return withContext(Dispatchers.IO) {
            try {
                println("[GROBID REST] Processing header: ${pdfFile.name}")

                val url = getServerUrl()
                val response = httpClient.post("$url/api/processHeaderDocument") {
                    timeout {
                        requestTimeoutMillis = if (isCloudMode) 120_000 else 60_000
                        socketTimeoutMillis = if (isCloudMode) 120_000 else 60_000
                    }

                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("input", pdfFile.readBytes(), Headers.build {
                                    append(HttpHeaders.ContentType, "application/pdf")
                                    append(HttpHeaders.ContentDisposition, "filename=\"${pdfFile.name}\"")
                                })
                                append("consolidateHeader", "1")
                            }
                        )
                    )
                }

                if (!response.status.isSuccess()) {
                    val errorBody = try {
                        response.bodyAsText()
                    } catch (e: Exception) {
                        "Unable to read error body: ${e.message}"
                    }
                    println("[GROBID REST] Error response (${response.status}): $errorBody")

                    // Provide user-friendly error message
                    val friendlyMessage = when {
                        errorBody.contains("BAD_INPUT_DATA") ->
                            "PDF format not supported by GROBID (will use direct PDF extraction instead)"
                        errorBody.contains("timeout") || errorBody.contains("TIMEOUT") ->
                            "GROBID processing timeout (PDF may be too large or complex)"
                        else ->
                            "GROBID API error: ${response.status}"
                    }

                    throw GrobidException(friendlyMessage)
                }

                val teiXml = response.bodyAsText()
                println("[GROBID REST] Received TEI XML (${teiXml.length} chars)")

                // Parse TEI XML to domain entities
                TEIParser.parse(teiXml)

            } catch (e: Exception) {
                println("[GROBID REST] Processing failed: ${e.message}")
                throw GrobidException("Failed to process PDF header with GROBID", e)
            }
        }
    }

    override fun isAvailable(): Boolean {
        return initialized
    }

    override fun getInfo(): GrobidEngineInfo {
        return GrobidEngineInfo(
            version = if (isCloudMode) "Cloud API" else "0.8.2 (REST API)",
            grobidHomePath = serverUrl ?: "Not initialized",
            isInitialized = initialized,
            modelsDownloaded = true
        )
    }

    /**
     * Stop GROBID server (only for local mode)
     */
    fun shutdown() {
        if (!isCloudMode) {
            GrobidProcessManager.stop()
        }
    }
}
