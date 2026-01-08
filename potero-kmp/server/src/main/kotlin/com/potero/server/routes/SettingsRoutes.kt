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
    val theme: String = "system"
)

@Serializable
data class UpdateSettingsRequest(
    val llmApiKey: String? = null,
    val llmProvider: String? = null,
    val pdfStoragePath: String? = null,
    val theme: String? = null
)

fun Route.settingsRoutes() {
    val settingsRepository = ServiceLocator.settingsRepository

    route("/settings") {
        // GET /api/settings - Get all settings
        get {
            val result = settingsRepository.getAll()
            result.fold(
                onSuccess = { settings ->
                    val dto = SettingsDto(
                        llmApiKey = settings[SettingsKeys.LLM_API_KEY]?.let {
                            // Mask API key for security
                            if (it.length > 8) "${it.take(4)}****${it.takeLast(4)}" else "****"
                        },
                        llmProvider = settings[SettingsKeys.LLM_PROVIDER] ?: "gpt",
                        pdfStoragePath = settings[SettingsKeys.PDF_STORAGE_PATH],
                        theme = settings[SettingsKeys.THEME] ?: "system"
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

                // Return updated settings
                val allSettings = settingsRepository.getAll().getOrDefault(emptyMap())
                val dto = SettingsDto(
                    llmApiKey = allSettings[SettingsKeys.LLM_API_KEY]?.let {
                        if (it.length > 8) "${it.take(4)}****${it.takeLast(4)}" else "****"
                    },
                    llmProvider = allSettings[SettingsKeys.LLM_PROVIDER] ?: "gpt",
                    pdfStoragePath = allSettings[SettingsKeys.PDF_STORAGE_PATH],
                    theme = allSettings[SettingsKeys.THEME] ?: "system"
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
