package com.potero.service.genai

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

/**
 * Service for uploading files to POSTECH GenAI API using SSO token.
 *
 * File upload flow:
 * 1. Get SSO bearer token from settings
 * 2. Upload file to /v2/athena/chats/m1/files with multipart/form-data
 * 3. Receive file ID and name from server
 * 4. Build file URL for attaching to chat messages
 */
class GenAIFileUploadService(
    private val httpClient: HttpClient,
    private val tokenProvider: suspend () -> String?,
    private val siteNameProvider: suspend () -> String
) {
    companion object {
        private const val BASE_URL = "https://genai.postech.ac.kr"
        private const val UPLOAD_ENDPOINT = "/v2/athena/chats/m1/files"
    }

    /**
     * Upload a file to GenAI and get file attachment info.
     *
     * @param fileName The original filename
     * @param fileBytes The file content as byte array
     * @param contentType MIME type of the file
     * @return Result containing the upload response with file ID and name
     * @throws GenAIException if SSO token not configured or upload fails
     */
    suspend fun uploadFile(
        fileName: String,
        fileBytes: ByteArray,
        contentType: String = "application/octet-stream"
    ): Result<GenAIFileResponse> = runCatching {
        val token = tokenProvider()
            ?: throw GenAIException("SSO token not configured. Please authenticate in Settings.")

        val siteName = siteNameProvider()
        val uploadUrl = "$BASE_URL$UPLOAD_ENDPOINT?site_name=$siteName"

        println("[GenAI] Uploading file: $fileName (${fileBytes.size} bytes) to $uploadUrl")

        val response = httpClient.submitFormWithBinaryData(
            url = uploadUrl,
            formData = formData {
                append("files", fileBytes, Headers.build {
                    append(HttpHeaders.ContentType, contentType)
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                })
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.body<String>()
            throw GenAIException("File upload failed: ${response.status}. $errorBody")
        }

        val uploadResponse = response.body<GenAIFileResponse>()
        println("[GenAI] File uploaded successfully: ${uploadResponse.files.size} file(s)")

        uploadResponse
    }

    /**
     * Build the file URL for attaching to chat messages.
     * This URL is used in the LLM API request.
     *
     * @param fileId The file ID returned from upload
     * @return The full URL for the uploaded file
     */
    suspend fun buildFileUrl(fileId: String): String {
        val siteName = siteNameProvider()
        return "$BASE_URL/v2/athena/chats/m1/files/$fileId?site_name=$siteName"
    }
}

/**
 * Response from GenAI file upload endpoint.
 */
@Serializable
data class GenAIFileResponse(
    val files: List<GenAIFile>
)

/**
 * File information from GenAI upload.
 */
@Serializable
data class GenAIFile(
    val id: String,
    val name: String
)

/**
 * Exception thrown when GenAI file operations fail.
 */
class GenAIException(message: String, cause: Throwable? = null) : Exception(message, cause)
