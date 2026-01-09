package com.potero.service.grobid

import io.ktor.client.*
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
 * This implementation starts a local GROBID server process and communicates
 * via HTTP REST API. This approach is ideal for Electron deployment as it
 * doesn't require Docker and can be bundled with the application.
 *
 * Thread-safe singleton with lazy initialization.
 */
class GrobidRestEngine(
    private val httpClient: HttpClient
) : GrobidEngine {

    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    /**
     * Initialize GROBID server (lazy)
     * Starts the server process on first use
     */
    private suspend fun initialize() = initMutex.withLock {
        if (initialized) return

        try {
            println("[GROBID REST] Initializing server...")

            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(GrobidShutdownHook())

            // Start GROBID server process
            val started = GrobidProcessManager.start()
            if (!started) {
                throw GrobidException("Failed to start GROBID server")
            }

            initialized = true
            println("[GROBID REST] Server ready at: ${GrobidProcessManager.getServerUrl()}")

        } catch (e: Exception) {
            println("[GROBID REST] Initialization failed: ${e.message}")
            throw GrobidException("Failed to initialize GROBID REST engine", e)
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

                val serverUrl = GrobidProcessManager.getServerUrl()
                val response = httpClient.post("$serverUrl/api/processFulltextDocument") {
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
                                append("teiCoordinates", "persName,figure,ref,biblStruct,formula")
                            }
                        )
                    )
                }

                if (!response.status.isSuccess()) {
                    throw GrobidException("GROBID API error: ${response.status}")
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

                val serverUrl = GrobidProcessManager.getServerUrl()
                val response = httpClient.post("$serverUrl/api/processHeaderDocument") {
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
                    throw GrobidException("GROBID API error: ${response.status}")
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
        // Note: GrobidProcessManager.isHealthy() is suspend, so we can't call it here
        // In practice, if initialized=true, server should be running
        return initialized
    }

    override fun getInfo(): GrobidEngineInfo {
        return GrobidEngineInfo(
            version = "0.8.2 (REST API)",
            grobidHomePath = GrobidProcessManager.getServerUrl(),
            isInitialized = initialized,
            modelsDownloaded = true  // Server includes models
        )
    }

    /**
     * Stop GROBID server
     * Call this on application shutdown
     */
    fun shutdown() {
        GrobidProcessManager.stop()
    }
}
