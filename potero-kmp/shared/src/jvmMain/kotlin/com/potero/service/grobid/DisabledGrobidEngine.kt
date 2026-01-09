package com.potero.service.grobid

/**
 * Disabled GROBID engine implementation (no-op)
 *
 * This implementation is used as a fallback when GROBID is not available
 * or when the user has disabled GROBID processing.
 *
 * All processing methods throw exceptions indicating GROBID is disabled.
 */
object DisabledGrobidEngine : GrobidEngine {

    override suspend fun processFulltext(pdfPath: String): TEIDocument {
        throw GrobidException("GROBID engine is disabled")
    }

    override suspend fun processHeader(pdfPath: String): TEIDocument {
        throw GrobidException("GROBID engine is disabled")
    }

    override fun isAvailable(): Boolean {
        return false
    }

    override fun getInfo(): GrobidEngineInfo {
        return GrobidEngineInfo(
            version = "N/A",
            grobidHomePath = "N/A",
            isInitialized = false,
            modelsDownloaded = false
        )
    }
}
