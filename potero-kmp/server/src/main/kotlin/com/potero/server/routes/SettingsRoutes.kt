package com.potero.server.routes

import com.potero.domain.repository.SettingsKeys
import com.potero.server.di.ServiceLocator
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class SettingsDto(
    val llmApiKey: String? = null,
    val llmProvider: String = "gpt",
    val pdfStoragePath: String? = null,
    val theme: String = "system",
    val semanticScholarApiKey: String? = null,
    // SSO Authentication for POSTECH GenAI file upload
    val ssoConfigured: Boolean = false,
    val ssoTokenExpiresAt: Long? = null,
    val ssoSiteName: String = "robi-gpt-dev",
    // PDF Download options
    val enableSciHub: Boolean = false
)

@Serializable
data class UpdateSettingsRequest(
    val llmApiKey: String? = null,
    val llmProvider: String? = null,
    val pdfStoragePath: String? = null,
    val theme: String? = null,
    val semanticScholarApiKey: String? = null,
    // SSO Authentication fields
    val ssoAccessToken: String? = null,
    val ssoTokenExpiry: Long? = null,
    val ssoSiteName: String? = null,
    // PDF Download options
    val enableSciHub: Boolean? = null
)

fun Route.settingsRoutes() {
    val settingsRepository = ServiceLocator.settingsRepository

    route("/settings") {
        // GET /api/settings - Get all settings
        get {
            val result = settingsRepository.getAll()
            result.fold(
                onSuccess = { settings ->
                    val ssoToken = settings[SettingsKeys.SSO_ACCESS_TOKEN]
                    val ssoExpiry = settings[SettingsKeys.SSO_TOKEN_EXPIRY]?.toLongOrNull()
                    val currentTime = System.currentTimeMillis()
                    val ssoConfigured = ssoToken != null && (ssoExpiry == null || ssoExpiry > currentTime)

                    val dto = SettingsDto(
                        llmApiKey = settings[SettingsKeys.LLM_API_KEY]?.let {
                            // Mask API key for security
                            if (it.length > 8) "${it.take(4)}****${it.takeLast(4)}" else "****"
                        },
                        llmProvider = settings[SettingsKeys.LLM_PROVIDER] ?: "gpt",
                        pdfStoragePath = settings[SettingsKeys.PDF_STORAGE_PATH],
                        theme = settings[SettingsKeys.THEME] ?: "system",
                        semanticScholarApiKey = settings[SettingsKeys.SEMANTIC_SCHOLAR_API_KEY]?.let {
                            // Mask API key for security
                            if (it.length > 8) "${it.take(4)}****${it.takeLast(4)}" else "****"
                        },
                        ssoConfigured = ssoConfigured,
                        ssoTokenExpiresAt = ssoExpiry,
                        ssoSiteName = settings[SettingsKeys.SSO_SITE_NAME] ?: "robi-gpt-dev",
                        enableSciHub = settings["scihub.enabled"]?.toBoolean() ?: false
                    )
                    call.respond(ApiResponse(data = dto))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<SettingsDto>(
                            success = false,
                            error = error.message ?: "Failed to get settings"
                        )
                    )
                }
            )
        }

        // PUT /api/settings - Update settings
        put {
            val request = call.receive<UpdateSettingsRequest>()

            try {
                // Update each setting if provided
                request.llmApiKey?.let {
                    settingsRepository.set(SettingsKeys.LLM_API_KEY, it)
                }
                request.llmProvider?.let {
                    settingsRepository.set(SettingsKeys.LLM_PROVIDER, it)
                }
                request.pdfStoragePath?.let {
                    settingsRepository.set(SettingsKeys.PDF_STORAGE_PATH, it)
                }
                request.theme?.let {
                    settingsRepository.set(SettingsKeys.THEME, it)
                }
                request.semanticScholarApiKey?.let {
                    settingsRepository.set(SettingsKeys.SEMANTIC_SCHOLAR_API_KEY, it)
                }

                // SSO fields
                request.ssoAccessToken?.let {
                    settingsRepository.set(SettingsKeys.SSO_ACCESS_TOKEN, it)
                }
                request.ssoTokenExpiry?.let {
                    settingsRepository.set(SettingsKeys.SSO_TOKEN_EXPIRY, it.toString())
                }
                request.ssoSiteName?.let {
                    settingsRepository.set(SettingsKeys.SSO_SITE_NAME, it)
                }

                // PDF Download options
                request.enableSciHub?.let {
                    settingsRepository.set("scihub.enabled", it.toString())
                }

                // Return updated settings
                val allSettings = settingsRepository.getAll().getOrDefault(emptyMap())
                val ssoToken = allSettings[SettingsKeys.SSO_ACCESS_TOKEN]
                val ssoExpiry = allSettings[SettingsKeys.SSO_TOKEN_EXPIRY]?.toLongOrNull()
                val currentTime = System.currentTimeMillis()
                val ssoConfigured = ssoToken != null && (ssoExpiry == null || ssoExpiry > currentTime)

                val dto = SettingsDto(
                    llmApiKey = allSettings[SettingsKeys.LLM_API_KEY]?.let {
                        if (it.length > 8) "${it.take(4)}****${it.takeLast(4)}" else "****"
                    },
                    llmProvider = allSettings[SettingsKeys.LLM_PROVIDER] ?: "gpt",
                    pdfStoragePath = allSettings[SettingsKeys.PDF_STORAGE_PATH],
                    theme = allSettings[SettingsKeys.THEME] ?: "system",
                    semanticScholarApiKey = allSettings[SettingsKeys.SEMANTIC_SCHOLAR_API_KEY]?.let {
                        if (it.length > 8) "${it.take(4)}****${it.takeLast(4)}" else "****"
                    },
                    ssoConfigured = ssoConfigured,
                    ssoTokenExpiresAt = ssoExpiry,
                    ssoSiteName = allSettings[SettingsKeys.SSO_SITE_NAME] ?: "robi-gpt-dev",
                    enableSciHub = allSettings["scihub.enabled"]?.toBoolean() ?: false
                )
                call.respond(ApiResponse(data = dto))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<SettingsDto>(
                        success = false,
                        error = e.message ?: "Failed to update settings"
                    )
                )
            }
        }

        // GET /api/settings/{key} - Get specific setting
        get("/{key}") {
            val key = call.parameters["key"]
                ?: throw IllegalArgumentException("Missing key")

            val result = settingsRepository.get(key)
            result.fold(
                onSuccess = { value ->
                    call.respond(ApiResponse(data = mapOf("key" to key, "value" to value)))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Map<String, String?>>(
                            success = false,
                            error = error.message ?: "Failed to get setting"
                        )
                    )
                }
            )
        }

        // PUT /api/settings/{key} - Set specific setting
        put("/{key}") {
            val key = call.parameters["key"]
                ?: throw IllegalArgumentException("Missing key")

            @Serializable
            data class SetValueRequest(val value: String)

            val request = call.receive<SetValueRequest>()
            val result = settingsRepository.set(key, request.value)

            result.fold(
                onSuccess = {
                    call.respond(ApiResponse(data = mapOf("key" to key, "value" to request.value)))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Map<String, String>>(
                            success = false,
                            error = error.message ?: "Failed to set setting"
                        )
                    )
                }
            )
        }
    }
}
